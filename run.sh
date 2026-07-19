#!/bin/bash
#
# crskin-all - Minecraft 认证服务器一体化 JAR 启动脚本
# 支持作为 Javaagent (-javaagent) 或独立服务器运行
#
# 用法: ./run.sh [start|stop|restart|status|console|agent]
#
# 无参数时进入交互式菜单
#

set -uo pipefail

# ============================================================
# 配置
# ============================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="crskin-all"
JAR_FILE="${SCRIPT_DIR}/${APP_NAME}-1.0.0.jar"
PID_FILE="${SCRIPT_DIR}/${APP_NAME}.pid"
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/${APP_NAME}.log"

# 服务器配置
CRSKIN_HOST="${CRSKIN_HOST:-127.0.0.1}"
CRSKIN_PORT="${CRSKIN_PORT:-25578}"
CRSKIN_DB="${CRSKIN_DB:-crskin.db}"

# JVM 参数
JVM_OPTS="-server -XX:+UseG1GC -Xms256m -Xmx512m"
JVM_OPTS="${JVM_OPTS} -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
JVM_OPTS="${JVM_OPTS} -Dcrskin.host=${CRSKIN_HOST}"
JVM_OPTS="${JVM_OPTS} -Dcrskin.port=${CRSKIN_PORT}"
JVM_OPTS="${JVM_OPTS} -Dcrskin.db=${CRSKIN_DB}"

# ============================================================
# 颜色输出
# ============================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_title() { echo -e "${BLUE}$*${NC}"; }

# ============================================================
# Java 检测
# ============================================================
find_java() {
    # 1. JAVA_HOME
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        echo "${JAVA_HOME}/bin/java"
        return 0
    fi

    # 2. PATH
    if command -v java &>/dev/null; then
        echo "java"
        return 0
    fi

    # 3. 常见路径
    local paths=(
        "/usr/lib/jvm/default/bin/java"
        "/usr/java/default/bin/java"
        "/opt/java/openjdk/bin/java"
        "/usr/local/openjdk-*/bin/java"
        "/snap/java/current/bin/java"
        "/usr/lib/jvm/java-*/bin/java"
        "/usr/lib/jvm/jre-*/bin/java"
    )

    for pattern in "${paths[@]}"; do
        for p in $pattern; do
            if [[ -x "$p" ]]; then
                echo "$p"
                return 0
            fi
        done
    done

    return 1
}

# ============================================================
# 预检
# ============================================================
preflight() {
    if [[ ! -f "${JAR_FILE}" ]]; then
        log_error "未找到 JAR 文件: ${JAR_FILE}"
        log_error "请先构建项目或下载 crskin-all-1.0.0.jar"
        exit 1
    fi

    JAVA_CMD="$(find_java)" || {
        log_error "未找到 Java，请安装 JDK 17+ 或设置 JAVA_HOME"
        log_error "Debian/Ubuntu: apt install openjdk-17-jre"
        log_error "CentOS/RHEL: yum install java-17-openjdk"
        exit 1
    }

    mkdir -p "${LOG_DIR}"
}

# ============================================================
# 进程管理
# ============================================================
is_running() {
    if [[ -f "${PID_FILE}" ]]; then
        local pid
        pid=$(cat "${PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            return 0
        fi
        rm -f "${PID_FILE}"
    fi
    return 1
}

get_pid() {
    if [[ -f "${PID_FILE}" ]]; then
        cat "${PID_FILE}"
    fi
}

# ============================================================
# 启动 (独立服务器模式)
# ============================================================
do_start() {
    if is_running; then
        local pid
        pid=$(get_pid)
        log_warn "服务已在运行 (PID: ${pid})"
        return 0
    fi

    preflight

    log_title "============================================================"
    log_title " crskin-all - 一体化认证服务器"
    log_title " Linux 快速启动脚本"
    log_title "============================================================"
    echo
    log_info "Java: ${JAVA_CMD} $("${JAVA_CMD}" -version 2>&1 | head -1)"
    log_info "监听: http://${CRSKIN_HOST}:${CRSKIN_PORT}"
    log_info "数据库: ${CRSKIN_DB}"
    echo

    # 检查配置
    if [[ -f "config.json" ]]; then
        log_info "配置文件: config.json"
    else
        log_warn "未找到 config.json"
        log_warn "将使用默认配置"
    fi
    echo

    log_info "启动中..."
    nohup "${JAVA_CMD}" ${JVM_OPTS} -jar "${JAR_FILE}" \
        >>"${LOG_FILE}" 2>&1 &

    local pid=$!
    echo "${pid}" > "${PID_FILE}"
    log_info "服务已启动 (PID: ${pid})"
    log_info "日志文件: ${LOG_FILE}"
    echo

    # 等待就绪
    log_info "等待服务器就绪..."
    local count=0
    while (( count < 30 )); do
        if command -v curl &>/dev/null; then
            if curl -sf --max-time 2 "http://127.0.0.1:${CRSKIN_PORT}/" &>/dev/null; then
                log_info "服务器就绪! http://127.0.0.1:${CRSKIN_PORT}/"
                return 0
            fi
        elif command -v wget &>/dev/null; then
            if wget -q --timeout=2 -O /dev/null "http://127.0.0.1:${CRSKIN_PORT}/" 2>/dev/null; then
                log_info "服务器就绪! http://127.0.0.1:${CRSKIN_PORT}/"
                return 0
            fi
        else
            log_warn "未找到 curl/wget，跳过就绪检查"
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done

    log_error "服务器未在 30 秒内就绪，请检查日志: ${LOG_FILE}"
    return 1
}

# ============================================================
# 停止
# ============================================================
do_stop() {
    if ! is_running; then
        log_warn "服务未运行"
        return 0
    fi

    local pid
    pid=$(get_pid)
    log_info "停止服务 (PID: ${pid})..."

    kill "${pid}" 2>/dev/null || true

    local wait=0
    while kill -0 "${pid}" 2>/dev/null && (( wait < 30 )); do
        sleep 1
        wait=$((wait + 1))
    done

    if kill -0 "${pid}" 2>/dev/null; then
        log_warn "服务未响应，强制停止..."
        kill -9 "${pid}" 2>/dev/null || true
        sleep 1
    fi

    rm -f "${PID_FILE}"
    log_info "服务已停止"
}

# ============================================================
# 重启
# ============================================================
do_restart() {
    log_info "重启服务..."
    do_stop
    sleep 2
    do_start
}

# ============================================================
# 状态
# ============================================================
do_status() {
    if is_running; then
        local pid
        pid=$(get_pid)
        local uptime=""
        if command -v ps &>/dev/null; then
            uptime="$(ps -o etime= -p "${pid}" 2>/dev/null || echo "未知")"
        fi
        log_info "服务运行中 (PID: ${pid}, 运行时长: ${uptime})"

        # 健康检查
        if command -v curl &>/dev/null; then
            if curl -sf --max-time 2 "http://127.0.0.1:${CRSKIN_PORT}/" &>/dev/null; then
                log_info "健康检查: 正常"
            else
                log_warn "健康检查: 失败 (可能正在启动)"
            fi
        fi
    else
        log_info "服务未运行"
    fi
}

# ============================================================
# 控制台模式 (前台运行)
# ============================================================
do_console() {
    if is_running; then
        local pid
        pid=$(get_pid)
        log_warn "服务已在后台运行 (PID: ${pid})"
        log_info "如需前台运行，请先停止服务: $0 stop"
        return 1
    fi

    preflight

    log_title "============================================================"
    log_title " crskin-all - 一体化认证服务器"
    log_title " 控制台模式 (按 Ctrl+C 停止)"
    log_title "============================================================"
    echo

    exec "${JAVA_CMD}" ${JVM_OPTS} -jar "${JAR_FILE}"
}

# ============================================================
# Javaagent 模式 (打印启动参数)
# ============================================================
do_agent() {
    log_info "crskin-all 作为 Javaagent 使用"
    echo
    log_info "请将以下参数添加到 Minecraft 服务器启动脚本:"
    echo "  -javaagent:${SCRIPT_DIR}/${JAR_FILE}=http://${CRSKIN_HOST}:${CRSKIN_PORT}/"
    echo
    log_info "或者使用系统属性覆盖配置:"
    echo "  -Dcrskin.host=${CRSKIN_HOST}"
    echo "  -Dcrskin.port=${CRSKIN_PORT}"
}

# ============================================================
# 查看日志
# ============================================================
do_logs() {
    if [[ ! -f "${LOG_FILE}" ]]; then
        log_warn "日志文件不存在: ${LOG_FILE}"
        return 1
    fi

    local lines="${1:-100}"
    log_info "显示最近 ${lines} 行日志..."
    echo "---"
    tail -n "${lines}" "${LOG_FILE}"
}

# ============================================================
# 交互式菜单
# ============================================================
show_menu() {
    while true; do
        echo
        log_title "============================================================"
        log_title " crskin-all - 管理菜单"
        log_title "============================================================"
        echo
        echo "  1. 启动服务 (独立服务器模式)"
        echo "  2. 停止服务"
        echo "  3. 重启服务"
        echo "  4. 查看状态"
        echo "  5. 前台运行 (控制台)"
        echo "  6. 查看日志"
        echo "  7. 打印 Javaagent 启动参数"
        echo "  0. 退出"
        echo

        if is_running; then
            log_info "当前状态: 运行中 (PID: $(get_pid))"
        else
            log_warn "当前状态: 已停止"
        fi
        echo

        read -rp "请选择操作 [0-7]: " choice

        case "$choice" in
            1) do_start ;;
            2) do_stop ;;
            3) do_restart ;;
            4) do_status ;;
            5) do_console; break ;;
            6)
                read -rp "显示行数 [默认 100]: " lines
                do_logs "${lines:-100}"
                ;;
            7) do_agent ;;
            0)
                log_info "退出"
                exit 0
                ;;
            *)
                log_error "无效选项"
                ;;
        esac

        echo
        read -rp "按回车键继续..."
    done
}

# ============================================================
# 主入口
# ============================================================
usage() {
    cat <<EOF
用法: $(basename "$0") [命令]

命令:
  start      启动服务 (独立服务器模式)
  stop       停止服务
  restart    重启服务
  status     查看服务状态
  console    前台运行 (控制台模式)
  logs [N]   查看最近 N 行日志 (默认 100)
  agent      打印 Javaagent 启动参数
  menu       显示交互式菜单
  help       显示此帮助信息

无参数时进入交互式菜单

示例:
  $(basename "$0")           # 交互式菜单
  $(basename "$0") start     # 启动服务
  $(basename "$0") stop      # 停止服务
  $(basename "$0") console   # 前台运行
  $(basename "$0") logs 50   # 查看最近 50 行日志
EOF
}

case "${1:-menu}" in
    start)   do_start ;;
    stop)    do_stop ;;
    restart) do_restart ;;
    status)  do_status ;;
    console) do_console ;;
    logs)    do_logs "${2:-100}" ;;
    agent)   do_agent ;;
    menu)    show_menu ;;
    help|-h|--help) usage ;;
    *)
        log_error "未知命令: $1"
        usage
        exit 1
        ;;
esac
