#!/bin/bash

#####################################################################
# Aftermiles Traccar Server - Auto Deploy Script
#
# 自动编译、构建并部署到 Windows Server 2022
#
# 用法: ./deploy.sh [options]
# 选项:
#   --skip-build    跳过构建，只部署已有的 JAR
#   --backup        部署前备份服务器上的旧版本
#   --help          显示帮助信息
#####################################################################

set -e  # 遇到错误立即退出

# 配置变量
WIN_SERVER="172.93.167.110"
WIN_USER="Administrator"  # 修改为你的 Windows 用户名
WIN_INSTALL_DIR="C:\\Program Files\\Traccar"
SERVICE_NAME="traccar"
LOCAL_JAR="target/tracker-server.jar"
LOCAL_SCHEMA="schema"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
Aftermiles Traccar Server - 自动部署脚本

用法: ./deploy.sh [选项]

选项:
  --skip-build    跳过本地构建，直接部署已有的 JAR 文件
  --backup        部署前备份服务器上的旧版本
  --no-restart    部署后不重启服务（用于维护窗口）
  --help          显示此帮助信息

示例:
  ./deploy.sh                     # 完整部署流程
  ./deploy.sh --skip-build        # 只部署，不构建
  ./deploy.sh --backup            # 带备份的部署

环境变量:
  WIN_SERVER     目标服务器IP (默认: 172.93.167.110)
  WIN_USER       Windows 用户名 (默认: Administrator)

EOF
    exit 0
}

# 解析命令行参数
SKIP_BUILD=false
DO_BACKUP=false
NO_RESTART=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --backup)
            DO_BACKUP=true
            shift
            ;;
        --no-restart)
            NO_RESTART=true
            shift
            ;;
        --help)
            show_help
            ;;
        *)
            log_error "未知选项: $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# 检查 SSH 连接
check_ssh_connection() {
    log_info "检查 SSH 连接: ${WIN_USER}@${WIN_SERVER}..."
    if ! ssh -o ConnectTimeout=5 "${WIN_USER}@${WIN_SERVER}" "echo Connected" >/dev/null 2>&1; then
        log_error "无法连接到 Windows Server"
        log_warn "请确保:"
        log_warn "  1. Windows Server 已启动 OpenSSH 服务"
        log_warn "  2. 已配置 SSH 密钥认证（或输入密码）"
        log_warn "  3. 防火墙允许 SSH 连接（端口 22）"
        exit 1
    fi
    log_success "SSH 连接正常"
}

# 本地构建
build_project() {
    if [ "$SKIP_BUILD" = true ]; then
        log_info "跳过构建步骤..."
        if [ ! -f "$LOCAL_JAR" ]; then
            log_error "找不到 JAR 文件: $LOCAL_JAR"
            log_warn "请先运行构建，或移除 --skip-build 参数"
            exit 1
        fi
        return
    fi

    log_info "开始构建项目..."

    # 清理旧的构建
    log_info "清理旧构建..."
    ./gradlew clean

    # 编译和打包（跳过测试加快速度）
    log_info "编译和打包..."
    if ./gradlew build -x test; then
        log_success "构建成功"
    else
        log_error "构建失败"
        exit 1
    fi

    # 验证 JAR 文件
    if [ ! -f "$LOCAL_JAR" ]; then
        log_error "构建后找不到 JAR 文件: $LOCAL_JAR"
        exit 1
    fi

    local jar_size=$(du -h "$LOCAL_JAR" | cut -f1)
    log_info "JAR 文件大小: ${jar_size}"
}

# 停止 Windows 服务
stop_service() {
    log_info "停止 Windows 服务: ${SERVICE_NAME}..."

    # 使用 PowerShell 停止服务
    ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"Stop-Service -Name ${SERVICE_NAME} -ErrorAction SilentlyContinue; Start-Sleep -Seconds 3\"" || true

    # 验证服务已停止
    local status=$(ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"(Get-Service -Name ${SERVICE_NAME}).Status\"")
    if [[ "$status" == *"Stopped"* ]]; then
        log_success "服务已停止"
    else
        log_warn "服务可能未完全停止，状态: $status"
    fi
}

# 备份服务器上的旧版本
backup_remote_jar() {
    if [ "$DO_BACKUP" = false ]; then
        return
    fi

    log_info "备份服务器上的旧版本..."

    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_name="tracker-server.jar.backup_${timestamp}"

    ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"Copy-Item '${WIN_INSTALL_DIR}\\tracker-server.jar' '${WIN_INSTALL_DIR}\\${backup_name}' -ErrorAction SilentlyContinue\"" || true

    log_success "备份完成: ${backup_name}"
}

# 上传文件到 Windows Server
upload_files() {
    log_info "上传 JAR 文件到服务器..."

    # 使用 SCP 上传 JAR（Windows 路径需要转义）
    # 先上传到用户目录，再移动到安装目录（避免权限问题）
    if scp "$LOCAL_JAR" "${WIN_USER}@${WIN_SERVER}:tracker-server.jar"; then
        log_success "JAR 上传成功"
    else
        log_error "JAR 上传失败"
        exit 1
    fi

    # 移动文件到安装目录
    log_info "移动文件到安装目录..."
    ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"Move-Item -Path tracker-server.jar -Destination '${WIN_INSTALL_DIR}\\tracker-server.jar' -Force\""

    # 上传 schema 文件（如果有新的数据库迁移）
    log_info "上传数据库 schema 文件..."
    if [ -d "$LOCAL_SCHEMA" ]; then
        # 打包 schema 目录
        tar czf /tmp/schema.tar.gz -C . schema/

        # 上传并解压
        scp /tmp/schema.tar.gz "${WIN_USER}@${WIN_SERVER}:schema.tar.gz"
        ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"
            # 解压到临时目录
            tar -xzf schema.tar.gz
            # 覆盖到安装目录
            Copy-Item -Path schema\\* -Destination '${WIN_INSTALL_DIR}\\schema\\' -Recurse -Force
            # 清理
            Remove-Item schema.tar.gz
            Remove-Item -Recurse schema
        \""

        rm /tmp/schema.tar.gz
        log_success "Schema 文件上传成功"
    fi
}

# 启动 Windows 服务
start_service() {
    if [ "$NO_RESTART" = true ]; then
        log_warn "跳过服务启动（--no-restart）"
        return
    fi

    log_info "启动 Windows 服务: ${SERVICE_NAME}..."

    # 使用 PowerShell 启动服务
    ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"Start-Service -Name ${SERVICE_NAME}\""

    # 等待服务启动
    sleep 5

    # 验证服务状态
    local status=$(ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"(Get-Service -Name ${SERVICE_NAME}).Status\"")
    if [[ "$status" == *"Running"* ]]; then
        log_success "服务启动成功"
    else
        log_error "服务启动失败，状态: $status"
        exit 1
    fi
}

# 验证部署
verify_deployment() {
    log_info "验证部署..."

    # 检查 JAR 文件
    local remote_jar_exists=$(ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"Test-Path '${WIN_INSTALL_DIR}\\tracker-server.jar'\"")
    if [[ "$remote_jar_exists" == *"True"* ]]; then
        log_success "JAR 文件存在"
    else
        log_error "JAR 文件不存在"
        return 1
    fi

    # 检查服务状态
    local service_status=$(ssh "${WIN_USER}@${WIN_SERVER}" "powershell -Command \"(Get-Service -Name ${SERVICE_NAME}).Status\"")
    log_info "服务状态: ${service_status}"

    # 尝试访问 API（等待几秒让服务完全启动）
    log_info "等待服务完全启动..."
    sleep 10

    if curl -s -o /dev/null -w "%{http_code}" "http://${WIN_SERVER}:8082/api/session" | grep -q "200\|401"; then
        log_success "API 可访问，部署成功！"
        log_info "访问地址: http://${WIN_SERVER}:8082"
    else
        log_warn "API 可能尚未启动，请手动检查日志"
    fi
}

# 主流程
main() {
    echo ""
    echo "=========================================="
    echo "  Aftermiles Traccar 自动部署工具"
    echo "=========================================="
    echo ""

    log_info "目标服务器: ${WIN_SERVER}"
    log_info "安装目录: ${WIN_INSTALL_DIR}"
    log_info "服务名称: ${SERVICE_NAME}"
    echo ""

    # 1. 检查连接
    check_ssh_connection

    # 2. 本地构建
    build_project

    # 3. 停止服务
    stop_service

    # 4. 备份（可选）
    backup_remote_jar

    # 5. 上传文件
    upload_files

    # 6. 启动服务
    start_service

    # 7. 验证部署
    verify_deployment

    echo ""
    log_success "=========================================="
    log_success "  部署完成！"
    log_success "=========================================="
    echo ""
    log_info "下一步:"
    log_info "  1. 访问 http://${WIN_SERVER}:8082"
    log_info "  2. 测试新功能"
    log_info "  3. 查看日志: ${WIN_INSTALL_DIR}\\logs"
    echo ""
}

# 捕获 Ctrl+C
trap 'log_error "部署被中断"; exit 1' INT

# 执行主流程
main
