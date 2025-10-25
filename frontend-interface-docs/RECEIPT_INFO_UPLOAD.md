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
