# Kimi Agent 智能 PPT 生成站（本地运行指南）

本项目是一个基于 `Vite + React + TypeScript` 的前端应用，代码目录为 `web`。

## 运行环境

- Node.js: 建议 `20.x`
- npm: 建议 `9+` 或 `10+`
- 操作系统: Windows / macOS / Linux（以下示例以 Windows PowerShell 为主）

## 本地启动

1. 进入项目目录

```powershell
cd "C:\Users\wenwe\Downloads\Kimi_Agent_智能PPT生成站\web"
```

2. 检查 Node 版本

```powershell
node -v
```

3. 安装依赖

```powershell
npm install
```

4. 启动开发服务器

```powershell
npm run dev
```

5. 打开终端输出中的本地地址（通常是）

- http://localhost:5173

## 构建与预览

### 生产构建

```powershell
npm run build
```

### 本地预览构建结果

```powershell
npm run preview
```

## 常见说明

- 当前项目未发现必须配置的 `.env` 文件即可启动页面。
- 页面中的“导出 PPTX / 导出 PDF”功能目前仍为占位逻辑（后续实现）。
- 目前可用导出为 Markdown。

## 常见问题排查

- 端口被占用：关闭占用 `5173` 端口的进程，或重启后按 Vite 提示使用新端口。
- 依赖安装失败：可先删除 `node_modules` 后重新执行 `npm install`。
- Node 版本过低：升级到 Node.js `20.x` 后重试。
