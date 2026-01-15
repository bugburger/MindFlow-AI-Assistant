import dashscope
from dashscope.audio.asr import Recognition 
from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
import uvicorn
import os
import json
import time
import shutil
import subprocess 
import traceback
from http import HTTPStatus
from datetime import datetime

# ================= 配置区域 =================
MY_API_KEY = "YOUR_DASHSCOPE_API_KEY" 
dashscope.api_key = MY_API_KEY
# ===========================================

app = FastAPI()

# 启动时检查 FFmpeg
if not shutil.which("ffmpeg"):
    print("\n❌❌❌ 严重警告：未检测到 FFmpeg！转码功能将失效！❌❌❌\n")

class TextRequest(BaseModel):
    text: str

@app.post("/api/v1/meeting/analyze")
async def analyze_audio(audio_file: UploadFile = File(...)):
    print(f"\n======== 收到【语音】分析请求 ========")
    
    # 1. 定义文件名
    timestamp = int(time.time())
    m4a_filename = f"temp_{timestamp}.m4a"  # 手机传来的原始文件
    wav_filename = f"temp_{timestamp}.wav"  # 转码后的纯净文件
    
    try:
        # 2. 保存原始 m4a
        with open(m4a_filename, "wb") as f:
            f.write(await audio_file.read())
        
        file_size = os.path.getsize(m4a_filename)
        print(f"👉 原始文件已保存: {m4a_filename} (大小: {file_size})")
        
        if file_size < 1000:
            return {"code": 500, "message": "录音时间太短"}

        print("🔄 正在进行格式清洗 (m4a -> wav)...")
        cmd = f'ffmpeg -y -i "{os.path.abspath(m4a_filename)}" -ar 16000 -ac 1 "{os.path.abspath(wav_filename)}"'
        
        subprocess.run(cmd, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        if not os.path.exists(wav_filename):
            print("❌ 转码失败，wav 文件未生成！")
            return {"code": 500, "message": "服务器转码失败"}
            
        print(f"✅ 转码成功！准备识别: {wav_filename}")

        print("📞 正在调用阿里云识别...")
        recognition = Recognition(
            model='paraformer-realtime-v1',
            format='wav', 
            sample_rate=16000,
            callback=None
        )

        result = recognition.call(os.path.abspath(wav_filename))

        if result.status_code == HTTPStatus.OK:
            full_text = ""
            if hasattr(result, 'output') and 'sentence' in result.output:
                for sent in result.output['sentence']:
                    full_text += sent['text']
            
            if not full_text: 
                print("⚠️ 识别结果为空 (可能是声音太小)")
                full_text = "（未听清）"
            else:
                print(f"🎉🎉🎉 识别成功: {full_text}")
                
            return call_ai_analysis(full_text)
        else:
            print(f"❌ API报错: {result.message}")
            return {"code": 500, "message": "识别服务异常"}

    except subprocess.CalledProcessError:
        print("❌ FFmpeg 执行出错，请检查环境变量！")
        return {"code": 500, "message": "音频转码失败"}
    except Exception as e:
        traceback.print_exc()
        return {"code": 500, "message": str(e)}
    finally:

        try:
            if os.path.exists(m4a_filename): os.remove(m4a_filename)
            if os.path.exists(wav_filename): os.remove(wav_filename)
        except: pass

@app.post("/api/v1/meeting/analyze_text")
async def analyze_text(request: TextRequest):
    print(f"\n======== 收到【文字】分析请求 ========")
    return call_ai_analysis(request.text)

def call_ai_analysis(content_text):
    print(f"5. 正在调用大模型分析: {content_text}")

    if "未听清" in content_text or "未检测到" in content_text:
        return {
            "code": 200, 
            "message": "Success", 
            "data": {
                "smart_summary": "抱歉，没听清，请离麦克风近一点。", 
                "action_items": []
            }
        }
    
    current_time_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    weekday_str = datetime.now().strftime("%A")

    prompt = f"""
    你是一个拥有项目管理思维的智能秘书。
    【当前系统时间】：{current_time_str} ({weekday_str})。
    【用户输入内容】：{content_text}
    
    请按以下步骤思考，并生成包含 "smart_summary" 和 "action_items" 两个核心字段的 JSON：
    
    1. 【smart_summary (智能总结)】：
       - 请用一句简短、温暖、人性化的话总结用户意图。
       - 例子1（任务）：用户说“明天去福州”，总结：“已为您安排了明天的福州行程。”
       - 例子2（闲聊）：用户说“你好”，总结：“你好呀，随时待命。”
       - 例子3（生活）：用户说“想吃火锅”，总结：“听起来很棒，已记下吃火锅的计划。”
    
    2. 【action_items (任务清单)】：
       - 第一步【过滤】：如果是纯闲聊/情绪宣泄，返回空数组 []。只有含待办意图才生成。
       - 第二步【提取】：
         * task: 核心动作（如“飞往福州”）。
         * location: 地点（如“咸阳机场”）。无则填 "本地"。
         * sub_tasks: 复杂任务（如旅游、装修）拆解为2-4个步骤；简单任务返回 []。
         * time: "MM月DD日 HH:mm"。
         * timestamp: "YYYY-MM-DD HH:MM:SS"。
         * sys_date: "YYYY年M月"。
       - 第三步【分类】：
         * category: 严格从以下选择：["工作", "学习", "生活" (含吃饭/娱乐/运动), "紧急", "其他"]

    
    请直接返回纯JSON数据。
    """
    
    try:
        llm_response = dashscope.Generation.call(
            model=dashscope.Generation.Models.qwen_turbo,
            prompt=prompt,
            result_format='message'
        )
        if llm_response.status_code == HTTPStatus.OK:
            ai_content = llm_response.output.choices[0].message.content
            clean_json = ai_content.replace("```json", "").replace("```", "").strip()
            try:
                data_obj = json.loads(clean_json)
                if "smart_summary" not in data_obj:
                    if len(data_obj.get("action_items", [])) > 0:
                        data_obj["smart_summary"] = "已为您生成任务清单。"
                    else:
                        data_obj["smart_summary"] = "没有检测到具体待办事项。"
                        
            except:
                data_obj = {"smart_summary": ai_content, "action_items": []}
                
            return {"code": 200, "message": "Success", "data": data_obj}
        else:
            return {"code": 500, "message": "AI调用失败"}
    except Exception as e:
        return {"code": 500, "message": str(e)}
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)