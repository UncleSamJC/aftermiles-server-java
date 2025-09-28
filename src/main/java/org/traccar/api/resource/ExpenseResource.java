/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
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

import org.traccar.api.BaseObjectResource;
import org.traccar.model.Expense;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Date;
import java.util.LinkedList;
import java.util.stream.Stream;

@Path("expenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExpenseResource extends BaseObjectResource<Expense> {

    public ExpenseResource() {
        super(Expense.class);
    }

    @GET
    public Stream<Expense> get(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("type") String type) throws StorageException {

        var conditions = new LinkedList<Condition>();

        // 权限控制：非管理员只能查看自己创建的费用记录
        if (permissionsService.notAdmin(getUserId())) {
            conditions.add(new Condition.Equals("createdByUserId", getUserId()));
        }

        // 设备过滤
        if (deviceId > 0) {
            conditions.add(new Condition.Equals("deviceId", deviceId));
        }

        // 时间范围过滤
        if (from != null && to != null) {
            conditions.add(new Condition.Between("expenseDate", from, to));
        }

        // 费用类型过滤
        if (type != null && !type.isEmpty()) {
            conditions.add(new Condition.Equals("type", type));
        }

        return storage.getObjectsStream(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("expenseDate", true, 0))); // 按费用日期降序排列
    }

    @POST
    public Response add(Expense entity) throws Exception {
        // 权限检查
        permissionsService.checkEdit(getUserId(), entity, true, false);

        // 设置创建信息
        entity.setCreatedByUserId(getUserId());
        entity.setCreatedTime(new Date());
        entity.setModifiedTime(new Date());

        // 直接添加到数据库，不创建权限关联表记录
        entity.setId(storage.addObject(entity, new Request(new Columns.Exclude("id"))));

        return Response.ok(entity).build();
    }

    @Override
    public Response update(Expense entity) throws Exception {
        // 权限检查：只能更新自己创建的费用记录
        if (permissionsService.notAdmin(getUserId())) {
            Expense existing = storage.getObject(baseClass, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", entity.getId())));
            if (existing == null || existing.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        // 设置修改时间，但不修改创建时间和创建者
        entity.setModifiedTime(new Date());

        storage.updateObject(entity, new Request(
                new Columns.Exclude("id", "createdTime", "createdByUserId"),
                new Condition.Equals("id", entity.getId())));

        return Response.ok(entity).build();
    }

    @Override
    public Response remove(long id) throws Exception {
        // 权限检查：只能删除自己创建的费用记录
        if (permissionsService.notAdmin(getUserId())) {
            Expense existing = storage.getObject(baseClass, new Request(
                    new Columns.Include("createdByUserId"),
                    new Condition.Equals("id", id)));
            if (existing == null || existing.getCreatedByUserId() != getUserId()) {
                throw new SecurityException("Expense access denied");
            }
        }

        return super.remove(id);
    }

}
