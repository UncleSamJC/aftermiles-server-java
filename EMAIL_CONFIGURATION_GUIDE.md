# Traccar 邮件发送配置指南

## 当前状态

✅ **Traccar 已内置完整的邮件发送功能**

**当前模式**: `mail.debug=true` (仅打印日志，不真实发送)

## 启用真实邮件发送

### 1. 通过配置文件

在 `debug.xml` 或 `setup/traccar.xml` 中修改/添加以下配置：

```xml
<!-- 关闭调试模式 -->
<entry key='mail.debug'>false</entry>

<!-- SMTP 服务器配置 -->
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.example.com</entry>
<entry key='mail.smtp.port'>587</entry>
<entry key='mail.smtp.starttls.enable'>true</entry>
<entry key='mail.smtp.starttls.required'>true</entry>

<!-- SMTP 认证 -->
<entry key='mail.smtp.username'>your-email@example.com</entry>
<entry key='mail.smtp.password'>your-password</entry>

<!-- 发件人 -->
<entry key='mail.smtp.from'>noreply@aftermiles.com</entry>
<entry key='mail.smtp.fromName'>Aftermiles Receipt System</entry>
```

## 常用邮件服务商配置

### Gmail

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.gmail.com</entry>
<entry key='mail.smtp.port'>587</entry>
<entry key='mail.smtp.starttls.enable'>true</entry>
<entry key='mail.smtp.starttls.required'>true</entry>
<entry key='mail.smtp.ssl.trust'>smtp.gmail.com</entry>
<entry key='mail.smtp.username'>your-email@gmail.com</entry>
<entry key='mail.smtp.password'>your-app-password</entry>  <!-- 使用应用专用密码 -->
<entry key='mail.smtp.from'>your-email@gmail.com</entry>
<entry key='mail.smtp.fromName'>Aftermiles</entry>
```

**注意**: Gmail 需要生成"应用专用密码"，不能直接使用账户密码
- 前往: https://myaccount.google.com/apppasswords
- 生成应用专用密码并使用

### 腾讯企业邮箱

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.exmail.qq.com</entry>
<entry key='mail.smtp.port'>587</entry>
<entry key='mail.smtp.starttls.enable'>true</entry>
<entry key='mail.smtp.username'>noreply@yourcompany.com</entry>
<entry key='mail.smtp.password'>your-password</entry>
<entry key='mail.smtp.from'>noreply@yourcompany.com</entry>
<entry key='mail.smtp.fromName'>报税助手</entry>
```

### 阿里云企业邮箱

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.qiye.aliyun.com</entry>
<entry key='mail.smtp.port'>465</entry>
<entry key='mail.smtp.ssl.enable'>true</entry>
<entry key='mail.smtp.ssl.trust'>smtp.qiye.aliyun.com</entry>
<entry key='mail.smtp.username'>noreply@yourcompany.com</entry>
<entry key='mail.smtp.password'>your-password</entry>
<entry key='mail.smtp.from'>noreply@yourcompany.com</entry>
<entry key='mail.smtp.fromName'>Aftermiles</entry>
```

### 163 邮箱

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.163.com</entry>
<entry key='mail.smtp.port'>465</entry>
<entry key='mail.smtp.ssl.enable'>true</entry>
<entry key='mail.smtp.username'>your-email@163.com</entry>
<entry key='mail.smtp.password'>your-auth-code</entry>  <!-- 使用授权码，非密码 -->
<entry key='mail.smtp.from'>your-email@163.com</entry>
<entry key='mail.smtp.fromName'>报税助手</entry>
```

### SendGrid (推荐用于大量发送)

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>smtp.sendgrid.net</entry>
<entry key='mail.smtp.port'>587</entry>
<entry key='mail.smtp.starttls.enable'>true</entry>
<entry key='mail.smtp.username'>apikey</entry>  <!-- 固定值 -->
<entry key='mail.smtp.password'>your-sendgrid-api-key</entry>
<entry key='mail.smtp.from'>noreply@aftermiles.com</entry>
<entry key='mail.smtp.fromName'>Aftermiles Receipt System</entry>
```

### AWS SES (Amazon Simple Email Service)

```xml
<entry key='mail.debug'>false</entry>
<entry key='mail.transport.protocol'>smtp</entry>
<entry key='mail.smtp.host'>email-smtp.us-east-1.amazonaws.com</entry>
<entry key='mail.smtp.port'>587</entry>
<entry key='mail.smtp.starttls.enable'>true</entry>
<entry key='mail.smtp.username'>your-smtp-username</entry>
<entry key='mail.smtp.password'>your-smtp-password</entry>
<entry key='mail.smtp.from'>verified-email@yourcompany.com</entry>
<entry key='mail.smtp.fromName'>Aftermiles</entry>
```

## 如何在代码中发送邮件

### 方式1: 通过依赖注入 (推荐)

```java
@Inject
private MailManager mailManager;

public void sendWelcomeEmail(User user) throws MessagingException {
    String subject = "欢迎使用Aftermiles报税助手";
    String body = """
        <html>
        <body>
            <h2>欢迎 %s！</h2>
            <p>您的试用账户已创建成功。</p>
            <p>试用期：7天</p>
            <p>扫描额度：50张收据</p>
            <p><a href="https://aftermiles.com/login">立即登录</a></p>
        </body>
        </html>
        """.formatted(user.getName());

    mailManager.sendMessage(user, true, subject, body);
}
```

### 方式2: 带附件发送

```java
@Inject
private MailManager mailManager;

public void sendReceiptReport(User user, File reportPdf) throws MessagingException {
    String subject = "您的收据报告已生成";
    String body = """
        <html>
        <body>
            <p>尊敬的 %s，</p>
            <p>您的月度收据报告已生成，请查看附件。</p>
        </body>
        </html>
        """.formatted(user.getName());

    // 创建附件
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.attachFile(reportPdf);
    attachment.setFileName("receipt_report.pdf");

    mailManager.sendMessage(user, true, subject, body, attachment);
}
```

### 方式3: 检查邮件功能是否启用

```java
@Inject
private MailManager mailManager;

public void sendEmailIfEnabled(User user) {
    if (mailManager.getEmailEnabled()) {
        try {
            mailManager.sendMessage(user, true, "Test", "Test email");
            System.out.println("邮件已发送");
        } catch (MessagingException e) {
            System.err.println("邮件发送失败: " + e.getMessage());
        }
    } else {
        System.out.println("邮件功能未启用");
    }
}
```

## 邮件模板示例

### 试用账户欢迎邮件

```java
public void sendTrialWelcomeEmail(User user) throws MessagingException {
    String subject = "🎉 欢迎加入Aftermiles报税助手 - 7天免费试用";
    String body = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background: #4CAF50; color: white; padding: 20px; text-align: center; }
                .content { padding: 20px; background: #f9f9f9; }
                .quota { font-size: 24px; font-weight: bold; color: #4CAF50; }
                .button { background: #4CAF50; color: white; padding: 12px 30px;
                          text-decoration: none; border-radius: 5px; display: inline-block; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>欢迎使用Aftermiles！</h1>
                </div>
                <div class="content">
                    <p>尊敬的 <strong>%s</strong>，</p>

                    <p>恭喜您成功注册Aftermiles报税助手！现在您可以开始使用我们的智能收据扫描功能。</p>

                    <h3>您的试用账户详情：</h3>
                    <ul>
                        <li>📅 试用期限：<strong>7天</strong></li>
                        <li>📸 扫描额度：<span class="quota">50张</span></li>
                        <li>⏰ 到期时间：<strong>%s</strong></li>
                    </ul>

                    <p>开始使用：</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="https://aftermiles.com/login" class="button">立即登录</a>
                    </p>

                    <p>试用期结束前，您可以随时升级为付费版本，享受更多扫描额度和功能！</p>

                    <hr>
                    <p style="font-size: 12px; color: #666;">
                        如有任何问题，请联系我们：support@aftermiles.com
                    </p>
                </div>
            </div>
        </body>
        </html>
        """.formatted(
            user.getName(),
            new SimpleDateFormat("yyyy-MM-dd HH:mm").format(user.getExpirationTime())
        );

    mailManager.sendMessage(user, true, subject, body);
}
```

### 付费升级成功邮件

```java
public void sendUpgradeSuccessEmail(User user) throws MessagingException {
    String subject = "✅ 升级成功！感谢您选择Aftermiles";
    String body = """
        <!DOCTYPE html>
        <html>
        <body style="font-family: Arial, sans-serif;">
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #4CAF50;">🎊 升级成功！</h2>

                <p>尊敬的 <strong>%s</strong>，</p>

                <p>感谢您升级为Aftermiles付费用户！</p>

                <div style="background: #f0f8ff; padding: 20px; border-radius: 10px; margin: 20px 0;">
                    <h3>您的新账户权益：</h3>
                    <ul>
                        <li>✅ 有效期至：<strong>2026年5月1日</strong></li>
                        <li>✅ 扫描额度：<strong style="font-size: 24px; color: #4CAF50;">500张</strong></li>
                        <li>✅ 优先客服支持</li>
                        <li>✅ 数据导出功能</li>
                    </ul>
                </div>

                <p>您已经扫描的 <strong>%d张</strong> 收据已自动保留，剩余额度 <strong>%d张</strong>。</p>

                <p style="text-align: center; margin: 30px 0;">
                    <a href="https://aftermiles.com/dashboard"
                       style="background: #4CAF50; color: white; padding: 12px 30px;
                              text-decoration: none; border-radius: 5px; display: inline-block;">
                        查看我的Dashboard
                    </a>
                </p>

                <p>祝您使用愉快！</p>

                <hr>
                <p style="font-size: 12px; color: #666;">
                    Aftermiles团队<br>
                    客服邮箱: support@aftermiles.com
                </p>
            </div>
        </body>
        </html>
        """.formatted(
            user.getName(),
            user.getInteger("scannedCount"),
            user.getDeviceLimit() - user.getInteger("scannedCount")
        );

    mailManager.sendMessage(user, true, subject, body);
}
```

### 试用期即将到期提醒

```java
public void sendExpirationWarningEmail(User user) throws MessagingException {
    String subject = "⏰ 您的试用期即将到期 - 升级享受更多权益";
    String body = """
        <!DOCTYPE html>
        <html>
        <body style="font-family: Arial, sans-serif;">
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #ff9800;">⏰ 试用期即将到期</h2>

                <p>尊敬的 <strong>%s</strong>，</p>

                <p>您的Aftermiles试用账户将于 <strong style="color: #ff5722;">明天</strong> 到期。</p>

                <div style="background: #fff3cd; border-left: 4px solid #ff9800; padding: 15px; margin: 20px 0;">
                    <p style="margin: 0;"><strong>到期时间：</strong>%s</p>
                    <p style="margin: 10px 0 0 0;"><strong>已使用：</strong>%d / 50 张</p>
                </div>

                <p>升级为付费版本，享受：</p>
                <ul>
                    <li>🚀 <strong>500张</strong> 扫描额度</li>
                    <li>📅 有效期至 <strong>2026年5月1日</strong></li>
                    <li>💎 优先技术支持</li>
                    <li>📊 高级数据分析</li>
                </ul>

                <p style="text-align: center; margin: 30px 0;">
                    <a href="https://aftermiles.com/upgrade"
                       style="background: #4CAF50; color: white; padding: 15px 40px;
                              text-decoration: none; border-radius: 5px; display: inline-block;
                              font-size: 18px; font-weight: bold;">
                        立即升级 🚀
                    </a>
                </p>

                <p style="font-size: 14px; color: #666;">
                    限时优惠：现在升级立减 20元！
                </p>
            </div>
        </body>
        </html>
        """.formatted(
            user.getName(),
            new SimpleDateFormat("yyyy-MM-dd HH:mm").format(user.getExpirationTime()),
            user.getInteger("scannedCount")
        );

    mailManager.sendMessage(user, true, subject, body);
}
```

### 扫描额度不足提醒

```java
public void sendQuotaWarningEmail(User user, int remaining) throws MessagingException {
    String subject = "📢 扫描额度即将用完";
    String body = """
        <!DOCTYPE html>
        <html>
        <body style="font-family: Arial, sans-serif;">
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #ff9800;">📢 扫描额度提醒</h2>

                <p>尊敬的 <strong>%s</strong>，</p>

                <p>您的扫描额度即将用完：</p>

                <div style="background: #fff3cd; padding: 20px; border-radius: 10px; text-align: center;">
                    <p style="font-size: 16px; margin: 0;">剩余额度</p>
                    <p style="font-size: 48px; font-weight: bold; color: #ff5722; margin: 10px 0;">
                        %d张
                    </p>
                    <p style="font-size: 14px; color: #666; margin: 0;">
                        已使用 %d / %d 张
                    </p>
                </div>

                <p style="margin-top: 30px;">为了不影响您的使用，建议您：</p>

                <p style="text-align: center; margin: 30px 0;">
                    <a href="https://aftermiles.com/upgrade"
                       style="background: #4CAF50; color: white; padding: 12px 30px;
                              text-decoration: none; border-radius: 5px; display: inline-block;">
                        升级为付费版本 (500张额度)
                    </a>
                </p>
            </div>
        </body>
        </html>
        """.formatted(
            user.getName(),
            remaining,
            user.getInteger("scannedCount"),
            user.getDeviceLimit()
        );

    mailManager.sendMessage(user, true, subject, body);
}
```

## 配置验证

### 测试邮件发送

创建一个测试API：

```java
@Path("test/email")
@POST
public Response testEmail(@QueryParam("to") String email) throws MessagingException {
    User testUser = new User();
    testUser.setEmail(email);
    testUser.setName("Test User");

    String subject = "Traccar邮件系统测试";
    String body = "<h1>测试成功！</h1><p>邮件系统配置正确。</p>";

    mailManager.sendMessage(testUser, true, subject, body);

    return Response.ok("邮件已发送到: " + email).build();
}
```

访问：`POST /api/test/email?to=your-email@example.com`

## 常见问题

### 1. Gmail "不够安全的应用"错误
**解决方案**: 使用应用专用密码
1. 启用两步验证
2. 生成应用专用密码：https://myaccount.google.com/apppasswords
3. 使用生成的密码替换配置中的密码

### 2. 邮件发送失败 - 连接超时
**可能原因**:
- 防火墙阻止SMTP端口 (25, 465, 587)
- SMTP服务器地址错误
- 网络限制

**解决方案**:
- 确认端口开放
- 使用 `telnet smtp.example.com 587` 测试连接
- 检查云服务器安全组规则

### 3. 认证失败
**可能原因**:
- 用户名/密码错误
- 需要授权码而非密码（163、QQ等）
- SSL/TLS配置不正确

**解决方案**:
- 检查用户名是否为完整邮箱地址
- 使用授权码代替密码
- 确认SSL/TLS端口配置正确

### 4. 邮件被标记为垃圾邮件
**解决方案**:
- 使用企业邮箱发送
- 配置SPF、DKIM、DMARC记录
- 避免使用敏感词汇
- 添加退订链接

## 推荐方案

### 开发/测试环境
```xml
<entry key='mail.debug'>true</entry>  <!-- 仅打印到日志 -->
```

### 生产环境（小规模）
**推荐**: 腾讯企业邮箱 / 阿里云企业邮箱
- 稳定可靠
- 发送配额充足
- 国内访问速度快

### 生产环境（大规模）
**推荐**: SendGrid / AWS SES / 阿里云邮件推送
- 专业邮件服务
- 高送达率
- 详细的发送统计
- API支持

## 下一步

1. ✅ 选择邮件服务商
2. ✅ 配置SMTP参数
3. ✅ 测试邮件发送
4. ✅ 编写邮件模板
5. ✅ 集成到业务逻辑
6. ✅ 监控发送成功率

---

**文档更新时间**: 2025-11-18