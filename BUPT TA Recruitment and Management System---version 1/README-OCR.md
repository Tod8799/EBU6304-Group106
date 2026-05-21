# 便携 OCR 方案（新电脑尽量零手动安装）

本项目 PDF 简历已改为 **纯视觉 OCR**：

1. **PDFBox**：把 PDF 每一页渲染成图片（200 DPI）  
2. **Tess4J + Tesseract**：对图片做 OCR，提取中英文文字  

不再解析 PDF 内嵌文本流，扫描件/纯图片 PDF 也可识别。

## 新电脑第一次运行

只需安装 **JDK 17+**，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

`run.ps1` 会自动：

- 下载 `lib/*.jar`（PDFBox、Tess4J 等，Maven 中央仓库）
- 下载 `vendor/tesseract/tessdata` 语言包
- 若本机没有 Tesseract，会尝试用 **winget** 安装一次并复制到 `vendor/tesseract/`（无需你手动点安装向导）
- 编译并启动 `WebServer`

**你不需要**自己再去装 Poppler、配置环境变量；跑通一次 `run.ps1` 即可。

## 手动仅安装 OCR 依赖（可选）

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-portable-ocr.ps1
```

## 目录结构

```
lib/                    # Java 依赖 JAR（脚本自动下载）
vendor/tesseract/       # 便携 tesseract.exe + tessdata/
src/ResumePdfOcr.java   # OCR 实现
```

## 说明

- 默认识别前 **3 页**，分辨率 **200 DPI**
- 语言：若存在 `chi_sim.traineddata` 与 `eng.traineddata` 则使用 `chi_sim+eng`
- 纯扫描件 PDF 走 OCR；不再解析 PDF 内嵌文本流
