# 验收脚本：三条章程主题，依赖后端 http://localhost:8080 已启动且已配置 DEEPSEEK / TAVILY / SILICONFLOW
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$ExternalLimit = 15
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

$topics = @(
    "人工智能在医疗中的应用",
    "新能源汽车发展现状",
    "碳中和政策与企业实践"
)

Write-Host "=== EIF 验收：三条主题 ===" -ForegroundColor Cyan

foreach ($topic in $topics) {
    Write-Host "`n--- $topic ---" -ForegroundColor Yellow

    $topicPayload = @{ topic = $topic } | ConvertTo-Json -Compress
    $outline = Invoke-RestMethod -Uri "$BaseUrl/api/projects/topic" -Method Post -Body $topicPayload -ContentType "application/json; charset=utf-8" -TimeoutSec 360
    $pid = $outline.projectId
    Write-Host "projectId=$pid"

    $loadPayload = @{
        query     = $topic
        projectId = $pid
        limit     = $ExternalLimit
    } | ConvertTo-Json -Compress
    $loaded = Invoke-RestMethod -Uri "$BaseUrl/api/external-sources/load" -Method Post -Body $loadPayload -ContentType "application/json; charset=utf-8"
    Write-Host "external segments indexed (reported count): $($loaded.loadedCount)"

    $genPayload = @{
        inputType    = "topic"
        inputContent = $topic
    } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$BaseUrl/api/projects/$pid/slides/generate" -Method Post -Body $genPayload `
        -ContentType "application/json; charset=utf-8" -TimeoutSec 1900 | Out-Null
    Write-Host "slides generated"

    $evalPayload = @{
        outlineLogicScore         = 80
        factualAccuracyScore    = 80
        infoDensityScore          = 80
        languageExpressionScore   = 80
    } | ConvertTo-Json -Compress
    $reportId = Invoke-RestMethod -Uri "$BaseUrl/api/projects/$pid/evaluations" -Method Post -Body $evalPayload -ContentType "application/json; charset=utf-8"
    Write-Host "evaluation report id: $reportId"

    $reports = Invoke-RestMethod -Uri "$BaseUrl/api/projects/$pid/evaluations" -Method Get
    $last = $reports[0]
    $rate = $last.factVerificationRate
    $auto = $last.autoFactualAccuracyScore
    Write-Host "factVerificationRate=$rate autoFactualAccuracyScore=$auto"

    if ($null -ne $rate -and $rate -le 1 -and $rate -lt 0.92) {
        Write-Host "低于阈值 0.92，建议检查 Tavily / SiliconFlow 配置或调整 TAVILY_* 策略。" -ForegroundColor Red
    }
}

Write-Host "`n=== 完成 ===" -ForegroundColor Cyan
