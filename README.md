# 🧠 MindFlow - AI 驱动的灵感流笔记 (AI Voice Task Manager)

> 一个基于大语言模型 (LLM) 的智能待办事项管理 App，支持语音输入、意图识别、自动分类与多模态交互。

![Android](https://img.shields.io/badge/Platform-Android-green.svg) ![Python](https://img.shields.io/badge/Backend-FastAPI-blue.svg) ![AI](https://img.shields.io/badge/AI-Qwen%20Turbo-orange.svg)

## 📖 项目简介 (Introduction)

MindFlow 旨在通过 AI 技术重构传统的 To-Do List。用户只需按住说话（例如：“下周五提醒我去机场接人”），App 即可通过后端 LLM 自动分析语义，提取 **时间、地点、任务内容**，并自动生成结构化的任务卡片。

本项目采用 **Client-Server** 架构：Android 端负责交互与采集，Python 后端负责 AI 推理与业务逻辑。

## ✨ 核心功能 (Features)

* **🗣️ 语音驱动 (Voice First)**: 集成 Android MediaRecorder 与 FFmpeg，支持全格式语音上传。
* **🧠 智能解析 (AI Analysis)**:
    * 基于 **Qwen-Turbo** 大模型进行 NLP 分析。
    * 自动剥离闲聊内容，提取核心 Task。
    * 支持复杂任务自动拆解 (Sub-tasks Breakdown)。
    * 智能推算相对日期 (如 "下周三" -> "202x-xx-xx")。
* **🔊 语音交互 (TTS)**: 支持智能总结朗读，模拟助理汇报。
* **⏰ 强力提醒 (Power Alarm)**: 集成 Android Full Screen Intent，支持锁屏状态下的全屏闹钟唤醒。
* **🌊 动态视觉**: 录音时配备实时声波可视化效果。

## 🛠️ 技术栈 (Tech Stack)

### 📱 Android Client (Java)
* **Network**: OkHttp3 (Multipart upload)
* **Audio**: MediaRecorder (AAC/AMR), TextToSpeech (TTS)
* **UI**: Custom Views (VoiceLineView), Material Design
* **System**: AlarmManager, BroadcastReceiver, Full-Screen Intent

### 🖥️ Python Server (FastAPI)
* **Framework**: FastAPI (Async/Await)
* **AI Models**: 
    * ASR: Alibaba SenseVoice / Paraformer (Speech-to-Text)
    * LLM: Qwen-Turbo (Prompt Engineering)
* **Tools**: FFmpeg (Audio transcoding), Uvicorn

## 📸 项目演示 (Screenshots)

| 首页 (Home) | 录音中 (Recording) | 智能分析 (AI Analysis) | 强力闹钟 (Alarm) |
|:---:|:---:|:---:|:---:|
| ![Home](screenshots/home.jpg) | ![Record](screenshots/record.jpg) | ![Result](screenshots/result.jpg) | ![Alarm](screenshots/alarm.jpg) |
| *任务列表与分类* | *实时声波反馈* | *AI 自动提取要素* | *锁屏唤醒界面* |

<img width="1080" height="2287" alt="image" src="https://github.com/user-attachments/assets/b98875e6-5bb3-4ff1-bc74-034342b59a86" />

## 🚀 快速开始 (How to Run)

### 1. 后端 (Backend)
需要安装 FFmpeg 并配置环境变量。

```bash
cd MindFlow_Server
pip install -r requirements.txt
# 配置 main.py 中的 API Key
python main.py
