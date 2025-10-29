# Receipt Info Upload API

## 接口概述
此接口用于上传各类费用信息及小票照片，支持记录车辆的燃油、保险、维修、其他费用等。

## 接口详情

### 请求信息
- **接口路径**: `/api/expenses`
- **请求方法**: `POST`
- **Content-Type**: `multipart/form-data`

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| deviceId | number | 是 | 设备ID（车辆ID） |
| category | string | 是 | 费用分类：`fuel`（燃油）, `insurance`（保险）, `maintenance`（维修）, `parking`（停车）, `toll`（过路费）, `carwash`（洗车）, `mobile`（手机通讯）, `legal`（法律费用）, `supplies`（用品）等 |
| amount | number | 是 | 金额（单位：元，保留2位小数） |
| currency | string | 是 | 货币种类 （例如：CAD，USD等） |
| merchant | string | 是 | 商家名称 |
| date | string | 是 | 日期（格式：YYYY-MM-DD） |
| receipt | file | 是 | 小票照片（JPEG格式） |
| notes | string | 否 | 备注信息 |
| tags | string | 否 | 标签（多个标签用逗号分隔） |

### 响应格式

#### 成功响应
```json
{
  "success": true,
  "data": {
    "id": 123,
    "deviceId": 1,
    "category": "fuel",
    "amount": 65.50,
    "currency": ”CAD“,
    "merchant": "Shell Gas Station",
    "date": "2024-01-15",
    "receiptUrl": "https://backend.aftermiles.ca/uploads/receipts/123.jpg",
    "notes": "Regular gasoline, full tank",
    "tags": "business,highway",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "message": "Expense record created successfully"
}
```

#### 失败响应
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input parameters",
    "details": {
      "amount": "Amount must be a positive number"
    }
  }
}
```

## 测试数据

### 测试数据 1 - 燃油费用（Fuel）
```
deviceId: 1
category: fuel
amount: 65.50
currency: CAD
merchant: Shell Gas Station
date: 2024-01-15
notes: Regular gasoline, full tank
tags: business,highway
receipt: [JPEG file - receipt-fuel-001.jpg]
```

### 测试数据 2 - 停车费用（Parking）
```
deviceId: 2
category: parking
amount: 12.00
currency: CAD
merchant: Downtown Parking Lot
date: 2024-01-16
notes: 3 hours parking
tags: business,client-meeting
receipt: [JPEG file - receipt-parking-002.jpg]
```

### 测试数据 3 - 洗车费用（Carwash）
```
deviceId: 1
category: carwash
amount: 25.00
currency: USD
merchant: Sparkle Car Wash
date: 2024-01-17
notes: Full service wash and wax
tags: maintenance
receipt: [JPEG file - receipt-carwash-003.jpg]
```

## 注意事项

1. **图片格式**: 小票照片仅支持JPEG格式，建议压缩质量为85%
2. **文件大小**: 单个小票照片不超过5MB
3. **日期范围**: 日期不能晚于当前日期
4. **金额精度**: 金额最多保留2位小数
5. **设备验证**: deviceId必须是当前用户有权限访问的设备
6. **分类验证**: category必须是系统支持的费用类型
7. **字符限制**:
   - merchant: 最多100个字符
   - notes: 最多500个字符
   - tags: 最多200个字符

## 支持的费用分类（Category）

| 分类值 | 中文说明 | 使用场景 |
|--------|----------|----------|
| fuel | 燃油 | 加油站加油 |
| insurance | 保险 | 车辆保险费用 |
| maintenance | 维修保养 | 车辆维修、保养 |
| parking | 停车 | 停车场费用 |
| toll | 过路费 | 高速公路、桥梁通行费 |
| carwash | 洗车 | 洗车服务 |
| mobile | 手机通讯 | 车载通讯费用 |
| legal | 法律费用 | 罚单、法律咨询等 |
| supplies | 用品 | 车辆用品购买 |
| others | 其他 | 其他无法归类的费用 |

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| VALIDATION_ERROR | 参数验证失败 |
| DEVICE_NOT_FOUND | 设备不存在 |
| UNAUTHORIZED | 无权限访问该设备 |
| INVALID_CATEGORY | 不支持的费用分类 |
| FILE_TOO_LARGE | 文件大小超过限制 |
| INVALID_FILE_TYPE | 文件类型不支持 |
| FUTURE_DATE_ERROR | 日期不能晚于当前日期 |






## 补充修改 - 2025-10-25

### 查询费用列表接口增强

**需求背景**：前端需要在 Overview 页面显示最近10条费用记录，需要后端支持限制返回条数和排序功能。

#### 接口修改详情

**接口路径**: `GET /api/expenses`

**新增查询参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| limit | number | 否 | 限制返回的记录条数，默认返回所有记录 |
| offset | number | 否 | 分页偏移量，默认为0（为未来分页功能预留）|
| deviceId | number | 否 | 按设备ID筛选（现有参数，保持不变）|
| from | string | 否 | 起始日期 YYYY-MM-DD（现有参数，保持不变）|
| to | string | 否 | 结束日期 YYYY-MM-DD（现有参数，保持不变）|
| category | string | 否 | 按分类筛选（现有参数，保持不变）|

**排序要求**:
- 默认按 `date` 字段降序排列（最新的记录在前）
- 如果日期相同，按 `id` 降序排列

#### 请求示例

```bash
# 获取最近10条费用记录
GET /api/expenses?limit=10

# 获取某设备的最近5条记录
GET /api/expenses?deviceId=123&limit=5

# 获取最近20条fuel类型的记录
GET /api/expenses?category=fuel&limit=20
```

#### 响应格式

响应应该是一个**数组**，而不是包装在对象中：

```json
[
  {
    "id": 456,
    "deviceId": 1,
    "category": "fuel",
    "amount": 65.50,
    "currency": "CAD",
    "merchant": "Shell Gas Station",
    "date": "2024-01-20",
    "receiptImagePath": "device123456/receipt_1700000000000.jpg",
    "notes": "Regular gasoline, full tank",
    "tags": "business,highway",
    "createdAt": "2024-01-20T10:30:00Z"
  },
  {
    "id": 455,
    "deviceId": 2,
    "category": "parking",
    "amount": 12.00,
    "currency": "CAD",
    "merchant": "Downtown Parking",
    "date": "2024-01-19",
    "receiptImagePath": "device789/receipt_1699999999999.jpg",
    "notes": "3 hours",
    "tags": "business",
    "createdAt": "2024-01-19T15:20:00Z"
  }
]
```

**注意**：
- 不要包含 `receiptUrl` 字段，前端会自己构造
- `receiptImagePath` 用于后端内部文件访问
- 如果没有数据，返回空数组 `[]`


#### SQL示例（参考）

```sql
SELECT * FROM tc_expenses
WHERE userId = ?
  AND (deviceId = ? OR ? IS NULL)
  AND (date >= ? OR ? IS NULL)
  AND (date <= ? OR ? IS NULL)
  AND (category = ? OR ? IS NULL)
ORDER BY date DESC, id DESC
LIMIT ?
OFFSET ?;
```

#### 测试场景

1. **无参数查询**：返回所有记录，按日期降序
2. **limit=10**：只返回最近10条记录
3. **limit=10&deviceId=123**：返回设备123的最近10条记录
4. **limit=5&category=fuel**：返回最近5条fuel类型记录
5. **空数据**：返回空数组 `[]`
6. **权限验证**：用户只能看到自己有权限的设备的费用记录
