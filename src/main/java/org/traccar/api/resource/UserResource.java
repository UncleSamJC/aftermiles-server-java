/*
 * Copyright 2015 - 2025 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import org.traccar.api.BaseObjectResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.ReceiptQuotaManager;
import org.traccar.helper.LogAction;
import org.traccar.helper.SessionHelper;
import org.traccar.helper.model.UserUtil;
import org.traccar.mail.MailManager;
import org.traccar.model.Device;
import org.traccar.model.ManagedUser;
import org.traccar.model.Permission;
import org.traccar.model.User;
import org.traccar.model.UserReceiptQuota;
import org.traccar.model.UserType;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;

@Path("users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource extends BaseObjectResource<User> {

    @Inject
    private Config config;

    @Inject
    private LogAction actionLogger;

    @Inject
    private ReceiptQuotaManager receiptQuotaManager;

    @Inject
    private MailManager mailManager;

    @Context
    private HttpServletRequest request;

    public UserResource() {
        super(User.class);
    }

    @GET
    public Stream<User> get(
            @QueryParam("userId") long userId, @QueryParam("deviceId") long deviceId,
            @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {
        var conditions = new LinkedList<Condition>();
        if (userId > 0) {
            permissionsService.checkUser(getUserId(), userId);
            conditions.add(new Condition.Permission(User.class, userId, ManagedUser.class).excludeGroups());
        } else if (permissionsService.notAdmin(getUserId())) {
            conditions.add(new Condition.Permission(User.class, getUserId(), ManagedUser.class).excludeGroups());
        }
        if (deviceId > 0) {
            permissionsService.checkManager(getUserId());
            conditions.add(new Condition.Permission(User.class, Device.class, deviceId).excludeGroups());
        }
        Columns columns = excludeAttributes ? new Columns.Exclude("attributes") : new Columns.All();
        return storage.getObjectsStream(baseClass, new Request(
                columns, Condition.merge(conditions), new Order("name")));
    }

    @Override
    @PermitAll
    @POST
    public Response add(User entity) throws StorageException {
        User currentUser = getUserId() > 0 ? permissionsService.getUser(getUserId()) : null;
        if (currentUser == null || !currentUser.getAdministrator()) {
            permissionsService.checkUserUpdate(getUserId(), new User(), entity);
            if (currentUser != null && currentUser.getUserLimit() != 0) {
                int userLimit = currentUser.getUserLimit();
                if (userLimit > 0) {
                    int userCount = storage.getObjects(baseClass, new Request(
                            new Columns.All(),
                            new Condition.Permission(User.class, getUserId(), ManagedUser.class).excludeGroups()))
                            .size();
                    if (userCount >= userLimit) {
                        throw new SecurityException("Manager user limit reached");
                    }
                }
            } else {
                if (UserUtil.isEmpty(storage)) {
                    entity.setAdministrator(true);
                } else if (!permissionsService.getServer().getRegistration()) {
                    throw new SecurityException("Registration disabled");
                }
                if (permissionsService.getServer().getBoolean(Keys.WEB_TOTP_FORCE.getKey())
                        && entity.getTotpKey() == null) {
                    throw new SecurityException("One-time password key is required");
                }
                UserUtil.setUserDefaults(entity, config);
            }
        }

        entity.setId(storage.addObject(entity, new Request(new Columns.Exclude("id"))));
        storage.updateObject(entity, new Request(
                new Columns.Include("hashedPassword", "salt"),
                new Condition.Equals("id", entity.getId())));

        actionLogger.create(request, getUserId(), entity);

        if (currentUser != null && currentUser.getUserLimit() != 0) {
            storage.addPermission(new Permission(User.class, getUserId(), ManagedUser.class, entity.getId()));
            actionLogger.link(request, getUserId(), User.class, getUserId(), ManagedUser.class, entity.getId());
        }
        return Response.ok(entity).build();
    }

    @Path("{id}")
    @DELETE
    public Response remove(@PathParam("id") long id) throws Exception {
        Response response = super.remove(id);
        if (getUserId() == id) {
            request.getSession().removeAttribute(SessionHelper.USER_ID_KEY);
        }
        return response;
    }

    @Path("totp")
    @PermitAll
    @POST
    public String generateTotpKey() throws StorageException {
        if (!permissionsService.getServer().getBoolean(Keys.WEB_TOTP_ENABLE.getKey())) {
            throw new SecurityException("One-time password is disabled");
        }
        return new GoogleAuthenticator().createCredentials().getKey();
    }

    // ==================== Receipt Quota Management APIs ====================

    /**
     * 试用用户注册 API
     * 允许未登录用户自行注册试用账号（7天有效期，50张扫描限额）
     */
    @Path("trial-registration")
    @PermitAll
    @POST
    public Response createTrialUser(TrialRegistrationRequest request) throws StorageException {
        // 1. 验证邮箱是否已存在
        User existingUser = storage.getObject(User.class, new org.traccar.storage.query.Request(
            new Columns.All(),
            new Condition.Equals("email", request.getEmail())
        ));

        if (existingUser != null) {
            throw new SecurityException("Email already registered");
        }

        // 2. 创建用户实体
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        // 设置试用期到期时间
        UserType userType = UserType.TRIAL_2025;
        user.setExpirationTime(userType.getExpirationDate());
        user.setTemporary(true); // 标记为临时用户

        // 应用默认配置
        UserUtil.setUserDefaults(user, config);

        // 3. 保存用户
        user.setId(storage.addObject(user, new org.traccar.storage.query.Request(new Columns.Exclude("id"))));
        storage.updateObject(user, new org.traccar.storage.query.Request(
            new Columns.Include("hashedPassword", "salt"),
            new Condition.Equals("id", user.getId())
        ));

        // 4. 初始化扫描配额
        UserReceiptQuota quota = receiptQuotaManager.initializeQuota(user.getId(), userType);

        // 5. 记录操作日志
        actionLogger.create(this.request, 0, user);

        // 6. 发送欢迎邮件（异步，失败不影响注册）
        try {
            sendTrialWelcomeEmail(user, userType, quota);
        } catch (Exception e) {
            // 邮件发送失败不影响注册流程
            // 日志已在 MailManager 中记录
        }

        // 7. 返回用户信息和配额信息
        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("quota", quota);
        response.put("message", "Trial account created successfully. You have "
            + quota.getMaxLimit() + " receipt scans for " + userType.getValidityDays() + " days.");

        return Response.ok(response).build();
    }

    /**
     * 升级用户类型 API（管理员操作，线下现金支付）
     * 管理员验证收款后，手动升级用户类型
     */
    @Path("{id}/upgrade")
    @POST
    public Response upgradeUser(
            @PathParam("id") long userId,
            @QueryParam("userType") String userTypeCode,
            @QueryParam("remark") String remark) throws StorageException {

        // 1. 权限检查：只有管理员可以操作
        permissionsService.checkAdmin(getUserId());

        // 2. 解析新的用户类型
        UserType newUserType;
        try {
            newUserType = UserType.fromCode(userTypeCode);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid user type: " + userTypeCode))
                    .build()
            );
        }

        // 3. 获取当前用户信息
        User user = storage.getObject(User.class, new org.traccar.storage.query.Request(
            new Columns.All(), new Condition.Equals("id", userId)
        ));

        if (user == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // 4. 升级用户类型（会更新 expirationTime 和 quota）
        receiptQuotaManager.upgradeUserType(userId, newUserType);

        // 5. 记录操作信息到 attributes
        user.getAttributes().put("lastUpgradeTime", new Date());
        user.set("lastUpgradeUserType", newUserType.getCode());
        user.set("upgradeOperator", getUserId()); // 记录操作的管理员ID

        if (remark != null && !remark.isEmpty()) {
            user.set("upgradeRemark", remark); // 备注信息（如：现金付款99元）
        }

        storage.updateObject(user, new org.traccar.storage.query.Request(
            new Columns.Include("attributes"),
            new Condition.Equals("id", userId)
        ));

        // 6. 记录操作日志
        actionLogger.create(this.request, getUserId(), user);

        // 7. 发送升级成功邮件给用户（异步，失败不影响升级）
        try {
            sendUpgradeSuccessEmail(user, newUserType);
        } catch (Exception e) {
            // 邮件发送失败不影响升级流程
        }

        // 8. 返回更新后的用户和配额信息
        UserReceiptQuota quota = receiptQuotaManager.getCurrentQuota(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", user);
        response.put("quota", quota);
        response.put("newUserType", newUserType.getCode());
        response.put("newUserTypeName", newUserType.getDisplayName());
        response.put("expirationTime", user.getExpirationTime());
        response.put("maxLimit", quota.getMaxLimit());
        response.put("currentUsage", quota.getCurrentUsage());
        response.put("remainingQuota", quota.getRemainingQuota());

        return Response.ok(response).build();
    }

    /**
     * 查询用户收据扫描配额 API
     * 用户可以查询自己的配额，管理员可以查询所有用户的配额
     */
    @Path("{id}/receipt-quota")
    @GET
    public Response getReceiptQuota(@PathParam("id") long userId) throws StorageException {
        // 权限检查：用户只能查询自己的配额，管理员可查询所有
        permissionsService.checkUser(getUserId(), userId);

        UserReceiptQuota quota = receiptQuotaManager.getCurrentQuota(userId);

        if (quota == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Quota not found for user"))
                .build();
        }

        // 返回配额信息
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("year", quota.getYear());
        response.put("userType", quota.getUserType());
        response.put("userTypeName", quota.getUserTypeEnum().getDisplayName());
        response.put("maxLimit", quota.getMaxLimit());
        response.put("currentUsage", quota.getCurrentUsage());
        response.put("remainingQuota", quota.getRemainingQuota());
        response.put("hasQuota", quota.hasQuota());

        // 获取用户到期时间
        User user = storage.getObject(User.class, new org.traccar.storage.query.Request(
            new Columns.Include("expirationTime"),
            new Condition.Equals("id", userId)
        ));
        response.put("expirationTime", user.getExpirationTime());

        return Response.ok(response).build();
    }

    // ==================== Helper Methods ====================

    /**
     * 发送试用用户欢迎邮件
     */
    private void sendTrialWelcomeEmail(User user, UserType userType, UserReceiptQuota quota) {
        try {
            String subject = "Welcome to AfterMiles - Trial Account Activated";
            String body = String.format(
                "Dear %s,\n\n" +
                "Welcome to AfterMiles! Your trial account has been successfully created.\n\n" +
                "Account Details:\n" +
                "- User Type: %s\n" +
                "- Validity: %d days (expires on %tF)\n" +
                "- Receipt Scan Quota: %d scans\n\n" +
                "You can now start using AfterMiles to manage your receipts and expenses.\n\n" +
                "To upgrade to a paid plan for more features, please contact our support team.\n\n" +
                "Best regards,\n" +
                "AfterMiles Team",
                user.getName(),
                userType.getDisplayName(),
                userType.getValidityDays(),
                user.getExpirationTime(),
                quota.getMaxLimit()
            );

            mailManager.sendMessage(user, true, subject, body, null);
        } catch (Exception e) {
            // Log but don't throw - email failure shouldn't block registration
            // Logger is already in MailManager
        }
    }

    /**
     * 发送升级成功邮件
     */
    private void sendUpgradeSuccessEmail(User user, UserType newUserType) {
        try {
            String subject = "Account Upgraded - " + newUserType.getDisplayName();
            String body = String.format(
                "Dear %s,\n\n" +
                "Your AfterMiles account has been successfully upgraded!\n\n" +
                "New Account Details:\n" +
                "- User Type: %s\n" +
                "- Expires on: %tF\n" +
                "- Receipt Scan Quota: %d scans per year\n\n" +
                "Thank you for choosing AfterMiles!\n\n" +
                "Best regards,\n" +
                "AfterMiles Team",
                user.getName(),
                newUserType.getDisplayName(),
                user.getExpirationTime(),
                newUserType.getScanQuota()
            );

            mailManager.sendMessage(user, false, subject, body, null);
        } catch (Exception e) {
            // Log but don't throw
        }
    }

    // ==================== DTO Classes ====================

    /**
     * 试用用户注册请求 DTO
     */
    public static class TrialRegistrationRequest {
        private String name;
        private String email;
        private String password;
        private String phone;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

}
