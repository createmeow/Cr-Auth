@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem crskin-all - Minecraft 认证服务器一体化 JAR 启动脚本
rem 支持作为 Javaagent (-javaagent) 或独立服务器运行
rem ============================================================

cd /d "%~dp0"

rem ===== 配置 =====
set "CRSKIN_HOST=127.0.0.1"
set "CRSKIN_PORT=25578"
set "CRSKIN_DB=crskin.db"
set "JAR_FILE=crskin-all-1.0.0.jar"
set "PID_FILE=crskin-all.pid"
set "LOG_DIR=logs"
set "LOG_FILE=%LOG_DIR%\crskin-all.log"

rem ===== 颜色代码 (Windows 10+) =====
for /f "tokens=2 delims==" %%a in ('wmic OS get localdatetime /value 2^>nul') do set "dt=%%a"
set "YY=%dt:~2,2%" & set "YYYY=%dt:~0,4%" & set "MM=%dt:~4,2%" & set "DD=%dt:~6,2%"
set "HH=%dt:~8,2%" & set "Min=%dt:~10,2%" & set "Sec=%dt:~12,2%"

rem ===== 标题 =====
title crskin-all - Minecraft Auth Server

echo ============================================================
echo  crskin-all - 一体化认证服务器
echo  支持 Javaagent 模式和独立服务器模式
echo ============================================================
echo.

rem ===== 检查 JAR 文件 =====
if not exist "%JAR_FILE%" (
    echo [错误] 未找到 %JAR_FILE%
    echo 请先构建项目或下载 JAR 文件
    pause
    exit /b 1
)

rem ===== Java 环境检测 =====
set "JAVA_CMD="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        echo [信息] 使用 JAVA_HOME: %JAVA_HOME%
    )
)

if not defined JAVA_CMD (
    where java >nul 2>&1
    if !errorlevel! equ 0 (
        set "JAVA_CMD=java"
        echo [信息] 使用系统 PATH 中的 Java
    )
)

if not defined JAVA_CMD (
    rem 检查常见 Minecraft 自带 Java 路径
    for %%d in (
        "%APPDATA%\.minecraft\runtime\java-runtime-epsilon\bin\java.exe"
        "%APPDATA%\.minecraft\runtime\java-runtime-gamma\bin\java.exe"
        "%APPDATA%\.minecraft\runtime\java-runtime-beta\bin\java.exe"
        "%APPDATA%\.minecraft\runtime\jre-legacy\bin\java.exe"
        "C:\Program Files\Eclipse Adoptium\jdk*\bin\java.exe"
        "C:\Program Files\Java\jdk*\bin\java.exe"
        "C:\Program Files (x86)\Java\jdk*\bin\java.exe"
    ) do (
        if exist %%d (
            set "JAVA_CMD=%%~d"
            echo [信息] 使用 Minecraft 自带 Java: %%~d
            goto :java_found
        )
    )
)

:java_found

if not defined JAVA_CMD (
    echo [错误] 未找到 Java，请安装 JDK 17+ 或设置 JAVA_HOME
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

rem ===== 检查 Java 版本 =====
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%v"
)
set "JAVA_VER=!JAVA_VER:"=!"
for /f "tokens=1 delims=." %%m in ("!JAVA_VER!") do set "JAVA_MAJOR=%%m"

if !JAVA_MAJOR! LSS 17 (
    echo [警告] Java 版本可能过低 (!JAVA_VER!)，建议 JDK 17+
    echo 按任意键继续尝试启动...
    pause >nul
)

rem ===== 检查配置文件 =====
if exist "config.json" (
    echo [信息] 使用配置文件: config.json
) else (
    echo [警告] 未找到 config.json
    echo         将使用默认配置
)

rem ===== 创建日志目录 =====
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

rem ===== 选择运行模式 =====
echo.
echo 请选择运行模式:
echo   1. 独立服务器模式 (crskin 认证服务器)
echo   2. Javaagent 模式 (-javaagent:crskin-all.jar)
echo   3. 仅测试启动 (启动后自动退出)
echo.
set /p MODE="请输入选项 (1/2/3) [默认 1]: "

if "!MODE!"=="" set "MODE=1"

rem ===== 启动参数 =====
set "JVM_OPTS=-server -XX:+UseG1GC -Xms256m -Xmx512m"
set "JVM_OPTS=!JVM_OPTS! -Dfile.encoding=UTF-8"
set "JVM_OPTS=!JVM_OPTS! -Dcrskin.host=%CRSKIN_HOST%"
set "JVM_OPTS=!JVM_OPTS! -Dcrskin.port=%CRSKIN_PORT%"
set "JVM_OPTS=!JVM_OPTS! -Dcrskin.db=%CRSKIN_DB%"

echo.
echo [信息] 启动参数:
echo   Java: %JAVA_CMD%
echo   监听: http://%CRSKIN_HOST%:%CRSKIN_PORT%
echo   数据库: %CRSKIN_DB%
echo.

if "!MODE!"=="1" (
    rem ===== 独立服务器模式 =====
    echo [信息] 独立服务器模式启动中...
    echo.
    "%JAVA_CMD%" %JVM_OPTS% -jar "%JAR_FILE%"

) else if "!MODE!"=="2" (
    rem ===== Javaagent 模式 =====
    echo [信息] Javaagent 模式启动中...
    echo.
    echo 请将以下参数添加到 Minecraft 服务器启动脚本:
    echo   -javaagent:%cd%\%JAR_FILE%=http://%CRSKIN_HOST%:%CRSKIN_PORT%/
    pause

) else if "!MODE!"=="3" (
    rem ===== 测试启动 =====
    echo [信息] 测试启动中...
    "%JAVA_CMD%" %JVM_OPTS% -jar "%JAR_FILE%"
    echo.
    echo [信息] 测试完成
    pause
) else (
    echo [错误] 无效选项
    pause
    exit /b 1
)
