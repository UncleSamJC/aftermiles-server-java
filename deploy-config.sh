#!/bin/bash

#####################################################################
# 部署配置文件
#
# 复制此文件为 deploy-config.local.sh 并修改配置
# deploy-config.local.sh 已在 .gitignore 中，不会被提交
#####################################################################

# Windows Server 配置
export WIN_SERVER="172.93.167.110"
export WIN_USER="Administrator"  # 修改为你的用户名

# 可选：SSH 密钥路径（如果不是默认的 ~/.ssh/id_rsa）
# export SSH_KEY="~/.ssh/windows_server_key"

# 可选：自定义安装目录
# export WIN_INSTALL_DIR="C:\\Program Files\\Traccar"

# 可选：自定义服务名
# export SERVICE_NAME="traccar"
