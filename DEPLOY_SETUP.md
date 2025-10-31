# Windows Server 2022 部署配置指南

## 📋 一次性配置（首次部署前）

### 1️⃣ Windows Server 启用 OpenSSH

**以管理员身份运行 PowerShell：**

```powershell
# 安装 OpenSSH Server
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0

# 启动 SSH 服务
Start-Service sshd

# 设置为自动启动
Set-Service -Name sshd -StartupType 'Automatic'

# 配置防火墙规则（如果尚未配置）
New-NetFirewallRule -Name sshd -DisplayName 'OpenSSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22

# 验证服务状态
Get-Service sshd
```

### 2️⃣ 配置 SSH 密钥认证（推荐，避免每次输入密码）

**在你的 Mac 上：**

```bash
# 生成 SSH 密钥（如果还没有）
ssh-keygen -t rsa -b 4096 -C "dehzi@macbook"

# 上传公钥到 Windows Server
scp ~/.ssh/id_rsa.pub Administrator@172.93.167.110:temp_pubkey.txt

# 在 Windows Server 上配置 authorized_keys（对于 Administrator 账户）
ssh Administrator@172.93.167.110 "powershell -Command \"
    # Administrator 账户使用特殊的 authorized_keys 位置
    Copy-Item -Path temp_pubkey.txt -Destination 'C:\ProgramData\ssh\administrators_authorized_keys' -Force;

    # 设置正确的权限
    icacls 'C:\ProgramData\ssh\administrators_authorized_keys' /inheritance:r;
    icacls 'C:\ProgramData\ssh\administrators_authorized_keys' /grant 'BUILTIN\Administrators:(F)';
    icacls 'C:\ProgramData\ssh\administrators_authorized_keys' /grant 'NT AUTHORITY\SYSTEM:(F)';

    # 重启 SSH 服务
    Restart-Service sshd;

    # 清理临时文件
    Remove-Item temp_pubkey.txt;

    Write-Host 'SSH 密钥配置完成'
\""
```

**重要提示**：Windows 对 Administrator 账户有特殊要求，必须使用 `C:\ProgramData\ssh\administrators_authorized_keys` 而不是用户目录的 `.ssh\authorized_keys`。

**验证无密码登录：**

```bash
# 测试密钥认证（不使用密码）
ssh -o PreferredAuthentications=publickey -o PasswordAuthentication=no Administrator@172.93.167.110 "echo 'SSH 密钥认证成功！'"
```

### 3️⃣ 确保 Traccar 已安装并注册为 Windows 服务

**验证服务存在：**

```powershell
Get-Service -Name traccar
```

**如果服务不存在，手动注册：**

```powershell
cd "C:\Program Files\Traccar"
java -jar tracker-server.jar --install conf\traccar.xml
```

---

## 🚀 使用部署脚本

### 基本用法

```bash
# 进入项目目录
cd /Users/dezhi/Documents/Myproject/Aftermiles-trac-server-java

# 完整部署（构建 + 部署 + 重启）
./deploy.sh

# 跳过构建，只部署已有的 JAR
./deploy.sh --skip-build

# 带备份的部署（推荐）
./deploy.sh --backup

# 部署但不重启服务（维护窗口使用）
./deploy.sh --no-restart

# 查看帮助
./deploy.sh --help
```

### 典型场景

**场景1: 日常开发部署**
```bash
# 修改代码后，快速部署测试
./deploy.sh
```

**场景2: 紧急修复**
```bash
# 修复 bug 后，带备份部署
./deploy.sh --backup
```

**场景3: 数据库迁移**
```bash
# 新增数据库表后，完整部署
./deploy.sh --backup
# Liquibase 会自动执行迁移
```

**场景4: 只更新 JAR（已经构建好）**
```bash
# 如果已经运行过 ./gradlew build
./deploy.sh --skip-build
```

---

## 🔍 部署流程说明

脚本执行的步骤：

1. **检查 SSH 连接** - 确保能连接到 Windows Server
2. **本地构建** - 运行 `./gradlew build -x test`
3. **停止服务** - `Stop-Service traccar`
4. **备份旧版本**（可选） - 备份服务器上的 JAR
5. **上传文件** - 使用 SCP 上传 JAR 和 schema
6. **启动服务** - `Start-Service traccar`
7. **验证部署** - 检查服务状态和 API 可访问性

---

## 🛠️ 故障排查

### 问题1: SSH 连接失败

**症状**：`无法连接到 Windows Server`

**解决方法**：
```bash
# 测试 SSH 连接
ssh Administrator@172.93.167.110

# 如果提示输入密码，说明密钥认证未配置
# 重新执行 ssh-copy-id

# 检查 Windows Server 上的 SSH 服务
ssh Administrator@172.93.167.110 "powershell -Command \"Get-Service sshd\""
```

### 问题2: 服务停止失败

**症状**：`服务可能未完全停止`

**解决方法**：
```bash
# 手动停止服务
ssh Administrator@172.93.167.110 "powershell -Command \"Stop-Service -Name traccar -Force\""

# 检查进程
ssh Administrator@172.93.167.110 "powershell -Command \"Get-Process -Name java -ErrorAction SilentlyContinue\""
```

### 问题3: 上传文件权限错误

**症状**：`Move-Item: Access is denied`

**解决方法**：
```bash
# 确保以管理员身份运行 SSH
# 或者手动移动文件
ssh Administrator@172.93.167.110
cd "C:\Program Files\Traccar"
# 手动替换文件
```

### 问题4: 服务启动失败

**症状**：`服务启动失败`

**解决方法**：
```bash
# 查看服务日志
ssh Administrator@172.93.167.110 "powershell -Command \"Get-Content 'C:\Program Files\Traccar\logs\tracker-server.log' -Tail 50\""

# 手动启动服务查看错误
ssh Administrator@172.93.167.110 "powershell -Command \"cd 'C:\Program Files\Traccar'; java -jar tracker-server.jar conf\traccar.xml\""
```

### 问题5: 数据库迁移失败

**症状**：服务启动后 API 返回错误

**解决方法**：
```bash
# 查看 Liquibase 日志
ssh Administrator@172.93.167.110 "powershell -Command \"
    Get-Content 'C:\Program Files\Traccar\logs\tracker-server.log' | Select-String 'liquibase'
\""

# 手动检查数据库
# 连接到数据库，查看 databasechangelog 表
```

---

## 📊 部署成功验证清单

- [ ] 服务状态为 Running
- [ ] API 可访问 (http://172.93.167.110:8082/api/session)
- [ ] 数据库迁移已执行（查看 databasechangelog 表）
- [ ] 新功能可正常使用
- [ ] 旧数据未丢失

---

## 💡 高级用法

### 创建快捷别名

在 `~/.zshrc` 或 `~/.bash_profile` 中添加：

```bash
# Traccar 部署快捷命令
alias deploy-traccar='cd /Users/dezhi/Documents/Myproject/Aftermiles-trac-server-java && ./deploy.sh'
alias deploy-traccar-quick='cd /Users/dezhi/Documents/Myproject/Aftermiles-trac-server-java && ./deploy.sh --skip-build'
alias deploy-traccar-safe='cd /Users/dezhi/Documents/Myproject/Aftermiles-trac-server-java && ./deploy.sh --backup'
```

然后直接使用：
```bash
deploy-traccar         # 完整部署
deploy-traccar-quick   # 快速部署
deploy-traccar-safe    # 安全部署（带备份）
```


### 监控部署日志

```bash
# 实时查看服务器日志
ssh Administrator@172.93.167.110 "powershell -Command \"
    Get-Content 'C:\Program Files\Traccar\logs\tracker-server.log' -Wait
\""
```

### 回滚到备份版本

```bash
# 列出备份文件
ssh Administrator@172.93.167.110 "powershell -Command \"
    Get-ChildItem 'C:\Program Files\Traccar\*.backup_*' | Sort-Object LastWriteTime -Descending
\""

# 回滚到指定备份（替换时间戳为实际的备份文件名）
ssh Administrator@172.93.167.110 "powershell -Command \"
    Stop-Service traccar;
    Copy-Item 'C:\Program Files\Traccar\tracker-server.jar.backup_YYYYMMDD_HHMMSS' 'C:\Program Files\Traccar\tracker-server.jar' -Force;
    Start-Service traccar
\""
```

---

## 🔐 安全建议

1. **使用密钥认证** - 禁用密码登录
2. **限制 SSH 访问** - 配置防火墙只允许你的 IP
3. **定期备份** - 数据库和配置文件
4. **监控日志** - 设置日志告警
5. **权限最小化** - 不要用 Administrator，创建专用部署账号

---

## 📞 需要帮助？

如果遇到问题：
1. 查看脚本输出的错误信息
2. 检查 Windows Server 日志
3. 验证 SSH 连接和权限
4. 参考上面的故障排查步骤
