# Traccar Server 部署更新指南

## 概述
本文档说明如何将 Traccar 服务器从开发环境（Mac）部署到生产环境（Windows Server 2012）。

**适用场景：**
- 新功能开发完成，需要部署到生产服务器
- Bug 修复后的更新
- 常规版本升级

**生产环境信息：**
- 操作系统：Windows Server 2012
- 安装位置：`C:\Program Files\Traccar`
- 服务名称：traccar

---

## 部署前准备清单

### ⚠️ 重要注意事项

- [ ] 已在本地完成充分测试
- [ ] 已通知相关人员计划停机时间
- [ ] 选择低流量时段进行部署
- [ ] 准备好回滚方案
- [ ] 确认有服务器管理员权限
- [ ] 确认服务器磁盘空间充足（至少 2GB 剩余）

---

## 第一部分：在 Mac 上准备部署包

### 1. 清理并构建项目

```bash
cd ~/Documents/Myproject/Aftermiles-trac-server-java

# 清理之前的构建
./gradlew clean

# 重新构建（包含测试）
./gradlew build

# 或跳过测试快速构建
./gradlew build -x test
```

### 2. 验证构建结果

```bash
# 检查 JAR 文件是否生成
ls -lh target/tracker-server.jar

# 检查依赖库数量（应该约 300 个）
ls target/lib/ | wc -l

# 检查数据库迁移脚本
ls schema/changelog-6.11.0.xml
```

### 3. 创建部署包

```bash
# 删除旧的部署目录（如果存在）
rm -rf deploy

# 创建部署目录
mkdir deploy

# 复制主程序
cp target/tracker-server.jar deploy/

# 复制依赖库
cp -r target/lib deploy/

# 复制数据库迁移脚本（重要！）
cp -r schema deploy/

# 可选：复制配置文件（如果有更新）
# cp traccar.xml deploy/
```

### 4. 打包成压缩文件

```bash
# 使用 zip 格式（Windows 友好）
zip -r deploy.zip deploy/

# 或使用 tar.gz 格式
# tar -czf deploy.tar.gz deploy/

# 验证压缩包大小（应该约 50-100 MB）
ls -lh deploy.zip
```

### 5. 最终检查

```bash
# 列出压缩包内容
unzip -l deploy.zip | head -20

# 确认包含以下内容：
# ✓ deploy/tracker-server.jar
# ✓ deploy/lib/ (约 300 个 jar 文件)
# ✓ deploy/schema/ (所有 changelog 文件)
```

---

## 第二部分：上传到 Windows Server

### 方式1：通过远程桌面（RDP）

1. 使用远程桌面连接到 Windows Server 2012
2. 在本地 Mac 上选择 `deploy.zip` 文件
3. 复制（Cmd+C）
4. 在远程桌面中打开 `C:\temp\` 目录
5. 粘贴（Ctrl+V）

### 方式2：通过 SCP（需要服务器安装 OpenSSH）

```bash
# 在 Mac 上执行
scp deploy.zip administrator@server-ip:C:/temp/
```

### 方式3：通过 FTP/SFTP

使用 FileZilla 或其他 FTP 客户端：
1. 连接到服务器
2. 导航到 `C:\temp\`
3. 上传 `deploy.zip`

---

## 第三部分：在 Windows Server 上部署

### 1. 解压部署包

以**管理员身份**打开 PowerShell：

```powershell
# 进入临时目录
cd C:\temp

# 解压部署包
Expand-Archive -Path deploy.zip -DestinationPath C:\temp\ -Force

# 验证解压结果
dir deploy
```

### 2. 创建自动化部署脚本

在 `C:\temp\` 目录创建 `deploy.ps1` 文件，内容如下：

```powershell
# ==========================================
# Traccar 自动化部署脚本
# ==========================================

# 配置路径
$deployPath = "C:\temp\deploy"                      # 上传的文件位置
$traccarPath = "C:\Program Files\Traccar"           # Traccar 安装目录
$backupPath = "C:\backup\$(Get-Date -Format 'yyyyMMdd_HHmmss')"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Traccar Deployment Script" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# ==========================================
# 1. 验证文件
# ==========================================
Write-Host "[1/6] Validating files..." -ForegroundColor Yellow

if (!(Test-Path "$deployPath\tracker-server.jar")) {
    Write-Host "✗ Error: Deploy files not found at $deployPath" -ForegroundColor Red
    Write-Host "Please ensure deploy.zip is extracted to C:\temp\" -ForegroundColor Red
    exit 1
}

if (!(Test-Path $traccarPath)) {
    Write-Host "✗ Error: Traccar not found at $traccarPath" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Files validated successfully`n" -ForegroundColor Green

# ==========================================
# 2. 停止服务
# ==========================================
Write-Host "[2/6] Stopping Traccar service..." -ForegroundColor Yellow

$service = Get-Service traccar -ErrorAction SilentlyContinue
if ($service) {
    if ($service.Status -eq "Running") {
        Stop-Service traccar -Force
        Start-Sleep -Seconds 5
        Write-Host "✓ Service stopped`n" -ForegroundColor Green
    } else {
        Write-Host "✓ Service already stopped`n" -ForegroundColor Green
    }
} else {
    Write-Host "⚠ Service not found, continuing anyway...`n" -ForegroundColor Yellow
}

# ==========================================
# 3. 创建备份
# ==========================================
Write-Host "[3/6] Creating backup..." -ForegroundColor Yellow
Write-Host "Backup location: $backupPath" -ForegroundColor Gray

New-Item -ItemType Directory -Force -Path $backupPath | Out-Null

# 备份主程序
if (Test-Path "$traccarPath\tracker-server.jar") {
    Copy-Item "$traccarPath\tracker-server.jar" "$backupPath\" -Force
    Write-Host "  ✓ Backed up tracker-server.jar" -ForegroundColor Gray
}

# 备份数据库
if (Test-Path "$traccarPath\data") {
    Copy-Item "$traccarPath\data" "$backupPath\data" -Recurse -Force
    Write-Host "  ✓ Backed up database" -ForegroundColor Gray
}

# 备份配置文件
if (Test-Path "$traccarPath\conf\traccar.xml") {
    Copy-Item "$traccarPath\conf\traccar.xml" "$backupPath\" -Force
    Write-Host "  ✓ Backed up traccar.xml" -ForegroundColor Gray
} elseif (Test-Path "$traccarPath\traccar.xml") {
    Copy-Item "$traccarPath\traccar.xml" "$backupPath\" -Force
    Write-Host "  ✓ Backed up traccar.xml" -ForegroundColor Gray
}

# 备份媒体文件（收据图片等）
if (Test-Path "$traccarPath\media") {
    Copy-Item "$traccarPath\media" "$backupPath\media" -Recurse -Force
    Write-Host "  ✓ Backed up media files" -ForegroundColor Gray
}

Write-Host "✓ Backup completed`n" -ForegroundColor Green

# ==========================================
# 4. 部署新文件
# ==========================================
Write-Host "[4/6] Deploying new files..." -ForegroundColor Yellow

# 部署主程序
Copy-Item "$deployPath\tracker-server.jar" "$traccarPath\" -Force
Write-Host "  ✓ Deployed tracker-server.jar" -ForegroundColor Gray

# 部署依赖库
$libCount = (Get-ChildItem "$deployPath\lib\*.jar").Count
Copy-Item "$deployPath\lib\*" "$traccarPath\lib\" -Force
Write-Host "  ✓ Deployed $libCount library files" -ForegroundColor Gray

# 部署数据库迁移脚本（重要！）
Copy-Item "$deployPath\schema\*" "$traccarPath\schema\" -Force -Recurse
Write-Host "  ✓ Deployed database migration scripts" -ForegroundColor Gray

Write-Host "✓ Files deployed successfully`n" -ForegroundColor Green

# ==========================================
# 5. 启动服务
# ==========================================
Write-Host "[5/6] Starting Traccar service..." -ForegroundColor Yellow

Start-Service traccar
Start-Sleep -Seconds 10

$service = Get-Service traccar -ErrorAction SilentlyContinue

if ($service -and $service.Status -eq "Running") {
    Write-Host "✓ Service started successfully`n" -ForegroundColor Green
} else {
    Write-Host "✗ Service failed to start`n" -ForegroundColor Red
}

# ==========================================
# 6. 验证部署
# ==========================================
Write-Host "[6/6] Verifying deployment..." -ForegroundColor Yellow

# 等待服务完全启动
Start-Sleep -Seconds 5

# 检查日志文件
$logPath = "$traccarPath\logs\tracker-server.log"
if (Test-Path $logPath) {
    Write-Host "`nRecent log entries:" -ForegroundColor Cyan
    Write-Host "----------------------------------------" -ForegroundColor Gray
    Get-Content $logPath -Tail 15
    Write-Host "----------------------------------------" -ForegroundColor Gray

    # 检查是否有错误
    $errors = Get-Content $logPath | Select-String "ERROR" -Tail 10
    if ($errors) {
        Write-Host "`n⚠ Found errors in log:" -ForegroundColor Yellow
        $errors | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
    }

    # 检查数据库迁移
    $migration = Get-Content $logPath | Select-String "changelog-6.11.0"
    if ($migration) {
        Write-Host "`n✓ Database migration executed" -ForegroundColor Green
    }
}

# ==========================================
# 部署总结
# ==========================================
Write-Host "`n========================================" -ForegroundColor Cyan
if ($service -and $service.Status -eq "Running") {
    Write-Host "✓ DEPLOYMENT SUCCESSFUL" -ForegroundColor Green
} else {
    Write-Host "✗ DEPLOYMENT FAILED" -ForegroundColor Red
}
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nBackup location: $backupPath" -ForegroundColor Gray
Write-Host "Log file: $logPath" -ForegroundColor Gray
Write-Host "`nPress any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
```

### 3. 执行部署脚本

```powershell
# 确保在 C:\temp 目录
cd C:\temp

# 允许脚本执行（仅当前会话）
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process

# 运行部署脚本
.\deploy.ps1
```

### 4. 观察部署过程

脚本会自动执行以下步骤：
1. ✓ 验证文件
2. ✓ 停止服务
3. ✓ 创建备份
4. ✓ 部署新文件
5. ✓ 启动服务
6. ✓ 验证部署

---

## 第四部分：手动部署步骤（备选方案）

如果自动化脚本失败，可以使用以下手动步骤：

### 1. 停止服务

```powershell
# 以管理员身份运行
net stop traccar

# 等待服务完全停止
Start-Sleep -Seconds 5
```

### 2. 手动备份

```powershell
# 创建备份目录
$backupDir = "C:\backup\manual_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Path $backupDir

# 备份主程序
Copy-Item "C:\Program Files\Traccar\tracker-server.jar" $backupDir

# 备份数据库
Copy-Item "C:\Program Files\Traccar\data" "$backupDir\data" -Recurse

# 备份配置
Copy-Item "C:\Program Files\Traccar\conf\traccar.xml" $backupDir

# 备份媒体文件
Copy-Item "C:\Program Files\Traccar\media" "$backupDir\media" -Recurse
```

### 3. 替换文件

```powershell
# 替换主程序
Copy-Item "C:\temp\deploy\tracker-server.jar" "C:\Program Files\Traccar\" -Force

# 替换依赖库
Copy-Item "C:\temp\deploy\lib\*" "C:\Program Files\Traccar\lib\" -Force

# 更新数据库迁移脚本
Copy-Item "C:\temp\deploy\schema\*" "C:\Program Files\Traccar\schema\" -Force -Recurse
```

### 4. 启动服务

```powershell
# 启动服务
net start traccar

# 等待服务启动
Start-Sleep -Seconds 10

# 检查服务状态
Get-Service traccar
```

---

## 第五部分：部署后验证

### 1. 检查服务状态

```powershell
# 方式1：使用 PowerShell
Get-Service traccar

# 方式2：使用命令行
sc query traccar

# 预期输出：STATE: 4 RUNNING
```

### 2. 检查日志文件

```powershell
# 查看最新日志
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Tail 50

# 查找错误
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" | Select-String "ERROR"

# 查找数据库迁移记录
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" | Select-String "changelog-6.11.0"
```

**预期看到：**
```
INFO: Successfully executed changeset changelog-6.11.0
```

### 3. 测试 API 端点

#### 测试服务器基本信息
```powershell
Invoke-WebRequest -Uri "http://localhost:8082/api/server" -Method GET
```

#### 测试新增的费用接口
```powershell
# 需要先登录获取 session，这里仅测试端点是否响应
Invoke-WebRequest -Uri "http://localhost:8082/api/expenses" -Method GET
```

### 4. 在浏览器中验证

打开浏览器访问：
```
http://localhost:8082
```

或服务器外网地址：
```
http://your-server-ip:8082
```

### 5. 测试新功能

- 登录系统
- 创建一条费用记录
- 上传收据图片
- 验证图片能正常显示

---

## 第六部分：回滚步骤

如果部署后发现问题，立即回滚：

### 自动回滚脚本

创建 `C:\temp\rollback.ps1`：

```powershell
# 配置
$traccarPath = "C:\Program Files\Traccar"
$backupPath = "C:\backup\20241022_153045"  # 👈 替换为实际备份目录

Write-Host "Rolling back to: $backupPath" -ForegroundColor Yellow

# 停止服务
Write-Host "Stopping service..." -ForegroundColor Yellow
Stop-Service traccar -Force
Start-Sleep -Seconds 5

# 恢复备份
Write-Host "Restoring backup..." -ForegroundColor Yellow
Copy-Item "$backupPath\tracker-server.jar" "$traccarPath\" -Force
Copy-Item "$backupPath\data\*" "$traccarPath\data\" -Recurse -Force
Copy-Item "$backupPath\traccar.xml" "$traccarPath\conf\" -Force

# 启动服务
Write-Host "Starting service..." -ForegroundColor Yellow
Start-Service traccar
Start-Sleep -Seconds 10

$service = Get-Service traccar
if ($service.Status -eq "Running") {
    Write-Host "`n✓ Rollback successful!" -ForegroundColor Green
} else {
    Write-Host "`n✗ Rollback failed!" -ForegroundColor Red
}
```

### 手动回滚

```powershell
# 1. 停止服务
net stop traccar

# 2. 找到最新备份
dir C:\backup\ | Sort-Object LastWriteTime -Descending | Select-Object -First 1

# 3. 恢复备份（替换时间戳）
$backup = "C:\backup\20241022_153045"
Copy-Item "$backup\tracker-server.jar" "C:\Program Files\Traccar\" -Force
Copy-Item "$backup\data\*" "C:\Program Files\Traccar\data\" -Recurse -Force

# 4. 启动服务
net start traccar

# 5. 验证
Get-Service traccar
```

---

## 第七部分：常见问题排查

### 问题1：服务启动失败

**症状：**
```
Error: Service failed to start
```

**解决方案：**
```powershell
# 检查日志
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Tail 100

# 常见原因：
# 1. 端口被占用（8082）
netstat -ano | findstr 8082

# 2. 数据库文件损坏
# 恢复备份的数据库文件

# 3. Java 版本不匹配
java -version  # 应该是 Java 17
```

### 问题2：数据库迁移失败

**症状：**
日志中出现 Liquibase 错误

**解决方案：**
```powershell
# 1. 检查 schema 目录是否完整
dir "C:\Program Files\Traccar\schema"

# 2. 确认包含 changelog-6.11.0.xml
dir "C:\Program Files\Traccar\schema\changelog-6.11.0.xml"

# 3. 如果缺失，重新复制
Copy-Item "C:\temp\deploy\schema\*" "C:\Program Files\Traccar\schema\" -Force -Recurse

# 4. 重启服务
net stop traccar
net start traccar
```

### 问题3：收据图片无法访问

**症状：**
`GET /api/expenses/{id}/receipt` 返回 404

**解决方案：**
```powershell
# 1. 检查媒体目录权限
icacls "C:\Program Files\Traccar\media"

# 2. 确保服务账户有读写权限
icacls "C:\Program Files\Traccar\media" /grant "NETWORK SERVICE:(OI)(CI)F"

# 3. 检查配置文件中的 media.path
type "C:\Program Files\Traccar\conf\traccar.xml" | findstr media

# 4. 重启服务
net stop traccar
net start traccar
```

### 问题4：权限错误

**症状：**
```
Access denied / Permission denied
```

**解决方案：**
```powershell
# 1. 确保以管理员身份运行 PowerShell

# 2. 检查文件夹权限
icacls "C:\Program Files\Traccar"

# 3. 授予完全控制权限
icacls "C:\Program Files\Traccar" /grant Administrators:F /T
```

---

## 第八部分：性能监控

### 部署后监控建议

```powershell
# 1. 监控服务状态（运行几小时）
while ($true) {
    $status = (Get-Service traccar).Status
    $time = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "$time - Service status: $status"
    Start-Sleep -Seconds 60
}

# 2. 监控日志错误
Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Wait | Select-String "ERROR"

# 3. 检查磁盘空间
Get-PSDrive C | Select-Object Used,Free

# 4. 检查内存使用
Get-Process -Name java | Select-Object ProcessName,@{Name="Memory(MB)";Expression={$_.WS / 1MB}}
```

---

## 第九部分：最佳实践

### 部署前

1. ✅ 在开发环境完整测试所有功能
2. ✅ 编写详细的发布说明（Release Notes）
3. ✅ 与团队沟通部署时间窗口
4. ✅ 准备回滚计划

### 部署中

1. ✅ 选择业务低峰期（如凌晨或周末）
2. ✅ 全程记录部署过程和时间点
3. ✅ 保持与团队沟通
4. ✅ 密切监控日志输出

### 部署后

1. ✅ 持续监控 1-2 小时
2. ✅ 验证关键功能正常工作
3. ✅ 检查错误日志
4. ✅ 收集用户反馈
5. ✅ 记录部署结果和遇到的问题

---

## 第十部分：部署检查清单

### 部署前检查

- [ ] 本地构建成功（`./gradlew build`）
- [ ] 所有测试通过
- [ ] JAR 文件生成（`target/tracker-server.jar`）
- [ ] 依赖库完整（约 300 个）
- [ ] 数据库迁移脚本存在（`schema/changelog-6.11.0.xml`）
- [ ] 部署包已打包（`deploy.zip`）
- [ ] 已上传到服务器（`C:\temp\deploy.zip`）

### 部署中检查

- [ ] 服务已停止
- [ ] 备份已创建
- [ ] 主程序已替换
- [ ] 依赖库已更新
- [ ] 数据库脚本已更新
- [ ] 服务已启动
- [ ] 服务状态为 Running

### 部署后检查

- [ ] 日志无严重错误
- [ ] 数据库迁移成功（日志中有 changelog-6.11.0）
- [ ] API 端点响应正常（`/api/server`）
- [ ] Web 界面可访问
- [ ] 能登录系统
- [ ] 能创建费用记录
- [ ] 能上传收据图片
- [ ] 收据图片能正常显示
- [ ] 已通知团队部署完成

---

## 附录A：目录结构参考

### Mac 开发环境
```
~/Documents/Myproject/Aftermiles-trac-server-java/
├── src/
├── target/
│   ├── tracker-server.jar
│   └── lib/
├── schema/
│   ├── changelog-master.xml
│   ├── changelog-6.10.0.xml
│   └── changelog-6.11.0.xml  ← 新增
├── deploy/                     ← 部署包
│   ├── tracker-server.jar
│   ├── lib/
│   └── schema/
└── deploy.zip                  ← 上传到服务器
```

### Windows Server 生产环境
```
C:\
├── temp\
│   ├── deploy.zip              ← 上传的文件
│   ├── deploy\                 ← 解压后
│   │   ├── tracker-server.jar
│   │   ├── lib\
│   │   └── schema\
│   ├── deploy.ps1              ← 部署脚本
│   └── rollback.ps1            ← 回滚脚本
│
├── Program Files\
│   └── Traccar\
│       ├── tracker-server.jar  ← 主程序
│       ├── lib\                ← 依赖库
│       ├── schema\             ← 数据库迁移
│       ├── conf\
│       │   └── traccar.xml
│       ├── data\               ← 数据库
│       │   └── database.mv.db
│       ├── media\              ← 媒体文件
│       └── logs\               ← 日志
│           └── tracker-server.log
│
└── backup\                     ← 备份
    └── 20241022_153045\
        ├── tracker-server.jar
        ├── traccar.xml
        ├── data\
        └── media\
```

---

## 附录B：联系支持

### 遇到问题时

1. **查看日志**
   ```powershell
   Get-Content "C:\Program Files\Traccar\logs\tracker-server.log" -Tail 100
   ```

2. **收集系统信息**
   ```powershell
   systeminfo | findstr /B /C:"OS Name" /C:"OS Version"
   java -version
   Get-Service traccar
   ```

3. **联系开发团队**
   - 提供详细的错误信息
   - 附上日志文件
   - 说明部署步骤和时间点

---

## 文档版本

- **版本**: 1.0
- **日期**: 2024-10-22
- **适用版本**: Traccar 6.11.0
- **作者**: 开发团队

---

## 更新记录

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2024-10-22 | 1.0 | 初始版本，包含完整部署流程 |

---

**祝部署顺利！** 🚀
