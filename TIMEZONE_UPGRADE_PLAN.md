# PostgreSQL Timestamp 升级计划：timestamp → timestamptz

## 📊 影响范围分析

**发现 11 个表，22 个字段需要升级**：

### 高优先级表（你的业务）
1. **tc_expenses** (3 rows)
   - expensedate, createdtime, modifiedtime

2. **tc_ai_receipts_batches** (3 rows)
   - createdtime, startedtime, completedtime

3. **tc_ai_receipts_batch_items** (4 rows)
   - createdtime, processedtime

### 中等优先级表（核心功能）
4. **tc_positions** (196,380 rows) ⚠️ **数据量大**
   - devicetime, fixtime, servertime

5. **tc_events** (5,110 rows)
   - eventtime

6. **tc_actions** (510 rows)
   - actiontime

### 低优先级表（系统）
7. **tc_devices** (1 row)
   - expirationtime, lastupdate, motiontime, overspeedtime

8. **tc_maintenance_logs** (5 rows)
   - createdtime, date, modifiedtime

9. **tc_statistics** (61 rows)
   - capturetime

10. **tc_users** (2 rows)
    - expirationtime

---

## 🎯 执行策略

### 阶段 1：小表先行（立即）
升级你的业务表（数据量小，风险低）：
- tc_expenses
- tc_ai_receipts_batches
- tc_ai_receipts_batch_items
- tc_maintenance_logs
- tc_actions
- tc_users
- tc_devices
- tc_statistics
- tc_events

### 阶段 2：大表单独处理（可选）
- tc_positions (19万+行) - 可以晚点做或跳过

---

## ⚡ 迁移 SQL 脚本

```sql
-- 阶段 1：小表批量升级（安全、快速）
BEGIN;

-- tc_expenses (3 rows)
ALTER TABLE tc_expenses
  ALTER COLUMN expensedate TYPE timestamptz USING expensedate AT TIME ZONE 'UTC',
  ALTER COLUMN createdtime TYPE timestamptz USING createdtime AT TIME ZONE 'UTC',
  ALTER COLUMN modifiedtime TYPE timestamptz USING modifiedtime AT TIME ZONE 'UTC';

-- tc_ai_receipts_batches (3 rows)
ALTER TABLE tc_ai_receipts_batches
  ALTER COLUMN createdtime TYPE timestamptz USING createdtime AT TIME ZONE 'UTC',
  ALTER COLUMN startedtime TYPE timestamptz USING startedtime AT TIME ZONE 'UTC',
  ALTER COLUMN completedtime TYPE timestamptz USING completedtime AT TIME ZONE 'UTC';

-- tc_ai_receipts_batch_items (4 rows)
ALTER TABLE tc_ai_receipts_batch_items
  ALTER COLUMN createdtime TYPE timestamptz USING createdtime AT TIME ZONE 'UTC',
  ALTER COLUMN processedtime TYPE timestamptz USING processedtime AT TIME ZONE 'UTC';

-- tc_maintenance_logs (5 rows)
ALTER TABLE tc_maintenance_logs
  ALTER COLUMN createdtime TYPE timestamptz USING createdtime AT TIME ZONE 'UTC',
  ALTER COLUMN date TYPE timestamptz USING date AT TIME ZONE 'UTC',
  ALTER COLUMN modifiedtime TYPE timestamptz USING modifiedtime AT TIME ZONE 'UTC';

-- tc_actions (510 rows)
ALTER TABLE tc_actions
  ALTER COLUMN actiontime TYPE timestamptz USING actiontime AT TIME ZONE 'UTC';

-- tc_users (2 rows)
ALTER TABLE tc_users
  ALTER COLUMN expirationtime TYPE timestamptz USING expirationtime AT TIME ZONE 'UTC';

-- tc_devices (1 row)
ALTER TABLE tc_devices
  ALTER COLUMN expirationtime TYPE timestamptz USING expirationtime AT TIME ZONE 'UTC',
  ALTER COLUMN lastupdate TYPE timestamptz USING lastupdate AT TIME ZONE 'UTC',
  ALTER COLUMN motiontime TYPE timestamptz USING motiontime AT TIME ZONE 'UTC',
  ALTER COLUMN overspeedtime TYPE timestamptz USING overspeedtime AT TIME ZONE 'UTC';

-- tc_statistics (61 rows)
ALTER TABLE tc_statistics
  ALTER COLUMN capturetime TYPE timestamptz USING capturetime AT TIME ZONE 'UTC';

-- tc_events (5,110 rows)
ALTER TABLE tc_events
  ALTER COLUMN eventtime TYPE timestamptz USING eventtime AT TIME ZONE 'UTC';

COMMIT;

-- 验证升级结果
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('tc_expenses', 'tc_ai_receipts_batches', 'tc_ai_receipts_batch_items',
                     'tc_maintenance_logs', 'tc_actions', 'tc_users', 'tc_devices',
                     'tc_statistics', 'tc_events')
  AND (column_name LIKE '%time%' OR column_name LIKE '%date%')
ORDER BY table_name, column_name;
```

---

## 🔧 阶段 2：大表升级（可选，谨慎执行）

```sql
-- tc_positions (196,380 rows) - 单独执行，可能需要几分钟
ALTER TABLE tc_positions
  ALTER COLUMN devicetime TYPE timestamptz USING devicetime AT TIME ZONE 'UTC',
  ALTER COLUMN fixtime TYPE timestamptz USING fixtime AT TIME ZONE 'UTC',
  ALTER COLUMN servertime TYPE timestamptz USING servertime AT TIME ZONE 'UTC';
```

⚠️ **注意事项**：
- tc_positions 数据量大，建议在低峰期执行
- Aiven 支持在线 DDL，但仍建议观察
- 可以不升级此表，应用层统一处理也可以

---

## ✅ 安全保障

1. **事务包装** - 失败自动回滚
2. **USING 子句** - 明确指定数据从 UTC 转换
3. **Aiven 在线 DDL** - 不锁表，不影响应用
4. **可逆** - 如需回滚可以改回 timestamp

---

## 📝 后续配置（Java 应用）

升级后还需要配置 Java JDBC URL：

```xml
<!-- debug.xml -->
<entry key='database.url'>jdbc:postgresql://aftermiles-af-6b50.f.aivencloud.com:19431/aftraccar?ssl=require&amp;timezone=UTC</entry>

<!-- 生产配置 setup/traccar.xml -->
<entry key='database.url'>jdbc:postgresql://aftermiles-af-6b50.f.aivencloud.com:19431/aftraccar?ssl=require&amp;timezone=UTC</entry>
```

可选：在 Main.java 中设置 JVM 时区
```java
TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
```

---

## 📅 执行时间建议

- **阶段 1**：随时可以执行（数据量小，< 1 秒）
- **阶段 2**：建议在业务低峰期（可能需要 1-2 分钟）

---

## 🎯 预期结果

所有时间字段从 `timestamp without time zone` 升级为 `timestamp with time zone`：
- ✅ 明确存储时区信息（+00 表示 UTC）
- ✅ 跨时区查询更准确
- ✅ 符合 PostgreSQL 最佳实践
- ✅ 为未来多时区业务打好基础

---

**创建日期**: 2025-11-08
**创建人**: Claude Code
**状态**: 待执行
