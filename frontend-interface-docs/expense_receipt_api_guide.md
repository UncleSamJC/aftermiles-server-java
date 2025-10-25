# Expense Receipt Image API Guide

## 概述
本文档说明费用收据图片的上传、存储和获取机制，供前端开发者参考。

---

## 1. 收据图片存储位置

### 文件系统路径结构
```
{media.path配置路径}/{设备UniqueId}/{年份}/{月份}/receipt_{时间戳}.{扩展名}
```

**示例：**
```
./media/device123456/2025/10/receipt_1234567890123.jpg
./media/device123456/2025/11/receipt_1234567890124.jpg
./media/device789012/2024/12/receipt_1234567890125.jpg
```

**说明：**
- 年份和月份基于费用日期（`expenseDate`），非上传时间
- 月份为两位数格式（01-12）
- 此分层结构优化了大量文件的存储性能

### 配置路径
- **默认路径**：`./media`（相对于项目根目录）
- **可配置**：在 `debug.xml` 或 `traccar.xml` 中修改
  ```xml
  <entry key='media.path'>./target/media</entry>
  ```

---

## 2. 图片存储流程

### 后端处理步骤
1. 前端上传图片到 `POST /api/expenses`
2. 后端使用 `MediaManager` 自动创建目录结构：
   - 目录：`{media.path}/{device.uniqueId}/`
   - 文件名：`receipt_{当前时间戳毫秒}.{扩展名}`
3. 相对路径保存到数据库的 `receiptImagePath` 字段
4. 返回访问 URL：`/api/expenses/{id}/receipt`

### 数据库存储示例
```sql
tc_expenses 表:
+-----+----------+--------------------------------------------------+
| id  | deviceId | receiptImagePath                                 |
+-----+----------+--------------------------------------------------+
| 456 | 123      | device123456/2025/10/receipt_1700000000000.jpg   |
+-----+----------+--------------------------------------------------+
```

---

## 3. 前端获取图片（重要）

### 方式1：使用 API 响应中的 receiptUrl（推荐）✅

#### 创建费用后获取
```javascript
// POST /api/expenses 响应
{
  "success": true,
  "data": {
    "id": 456,
    "deviceId": 123,
    "category": "fuel",
    "amount": 65.50,
    "receiptUrl": "/api/expenses/456/receipt",  // ← 直接使用此 URL
    ...
  }
}
```

#### 在 HTML 中使用
```html
<!-- 直接作为图片 src -->
<img src="/api/expenses/456/receipt" alt="Receipt" />
```

#### 在 JavaScript 中使用
```javascript
// 方式1：直接设置 src
document.getElementById('receipt-img').src = data.data.receiptUrl;

// 方式2：通过 Blob 下载
fetch('/api/expenses/456/receipt')
  .then(response => response.blob())
  .then(blob => {
    const imageUrl = URL.createObjectURL(blob);
    document.getElementById('receipt').src = imageUrl;
  });
```

### 方式2：查询费用列表时获取
```javascript
// GET /api/expenses?deviceId=123
fetch('/api/expenses?deviceId=123')
  .then(response => response.json())
  .then(expenses => {
    expenses.forEach(expense => {
      // 构造图片 URL
      const receiptUrl = `/api/expenses/${expense.id}/receipt`;

      const img = document.createElement('img');
      img.src = receiptUrl;
      img.alt = `Receipt for ${expense.merchant}`;

      document.getElementById('receipt-list').appendChild(img);
    });
  });
```

---

## 4. 完整的前端示例代码

### 上传费用和收据图片
```javascript
// HTML 表单
<form id="expense-form">
  <input type="number" name="deviceId" required />
  <select name="category" required>
    <option value="fuel">Fuel</option>
    <option value="parking">Parking</option>
    <!-- 其他选项 -->
  </select>
  <input type="number" name="amount" step="0.01" required />
  <input type="text" name="currency" value="CAD" required />
  <input type="text" name="merchant" required />
  <input type="date" name="date" required />
  <input type="file" name="receipt" accept="image/jpeg,image/jpg" required />
  <textarea name="notes"></textarea>
  <input type="text" name="tags" placeholder="business,highway" />
  <button type="submit">Submit</button>
</form>

<div id="result">
  <img id="receipt-preview" style="display:none; max-width: 300px;" />
</div>

<script>
document.getElementById('expense-form').addEventListener('submit', async (e) => {
  e.preventDefault();

  const formData = new FormData(e.target);

  try {
    const response = await fetch('/api/expenses', {
      method: 'POST',
      body: formData,
      credentials: 'include'  // 包含认证信息
    });

    const result = await response.json();

    if (result.success) {
      console.log('Expense created:', result.data);

      // 显示上传的收据图片
      const receiptImg = document.getElementById('receipt-preview');
      receiptImg.src = result.data.receiptUrl;  // /api/expenses/456/receipt
      receiptImg.style.display = 'block';

      alert('费用记录创建成功！ID: ' + result.data.id);
    } else {
      alert('错误: ' + result.error.message);
      console.error('Error details:', result.error);
    }
  } catch (error) {
    console.error('Request failed:', error);
    alert('请求失败，请检查网络连接');
  }
});
</script>
```

### 展示费用列表和收据图片
```javascript
async function loadExpenses(deviceId) {
  try {
    const response = await fetch(`/api/expenses?deviceId=${deviceId}`, {
      credentials: 'include'
    });

    const expenses = await response.json();

    const container = document.getElementById('expenses-list');
    container.innerHTML = '';

    expenses.forEach(expense => {
      const expenseCard = document.createElement('div');
      expenseCard.className = 'expense-card';
      expenseCard.innerHTML = `
        <h3>${expense.merchant}</h3>
        <p>金额: ${expense.amount} ${expense.currency}</p>
        <p>类别: ${expense.category}</p>
        <p>日期: ${expense.date}</p>
        <p>备注: ${expense.notes || 'N/A'}</p>
        <img src="/api/expenses/${expense.id}/receipt"
             alt="Receipt"
             style="max-width: 200px; cursor: pointer;"
             onclick="window.open(this.src, '_blank')" />
      `;
      container.appendChild(expenseCard);
    });

  } catch (error) {
    console.error('Failed to load expenses:', error);
  }
}

// 调用
loadExpenses(123);
```

### 下载收据图片
```javascript
async function downloadReceipt(expenseId) {
  try {
    const response = await fetch(`/api/expenses/${expenseId}/receipt`, {
      credentials: 'include'
    });

    if (!response.ok) {
      throw new Error('Receipt not found');
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);

    // 创建下载链接
    const a = document.createElement('a');
    a.href = url;
    a.download = `receipt_${expenseId}.jpg`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);

    // 释放 URL
    URL.revokeObjectURL(url);

  } catch (error) {
    console.error('Download failed:', error);
    alert('下载失败');
  }
}
```

---

## 5. API 端点说明

### 创建费用（含图片上传）
```
POST /api/expenses
Content-Type: multipart/form-data

参数：见 receipt_info_upload.md
```

### 获取收据图片
```
GET /api/expenses/{expenseId}/receipt

响应：
- Content-Type: image/jpeg 或 image/png
- Content-Disposition: inline; filename="receipt_{expenseId}"
- Body: 图片二进制数据
```

### 查询费用列表
```
GET /api/expenses?deviceId={deviceId}&from={date}&to={date}&type={category}

响应：JSON 数组
注意：响应中不直接包含 receiptUrl，需要前端自己构造：
/api/expenses/{expense.id}/receipt
```

---

## 6. 安全性说明

### 权限控制
- ✅ **自动验证**：只有创建者或管理员才能访问收据图片
- ✅ **设备权限**：用户必须对设备有访问权限才能上传/查看收据
- ❌ **无需额外认证**：使用现有的 session 认证机制

### 访问控制流程
```
前端请求 → 后端检查 session → 验证用户是否为费用创建者 → 返回图片
```

如果权限不足，返回：
```
HTTP 403 Forbidden
{
  "message": "Access denied"
}
```

---

## 7. 错误处理

### 常见错误码

| HTTP状态码 | 说明 | 处理建议 |
|-----------|------|----------|
| 400 Bad Request | 参数验证失败 | 检查表单数据是否完整 |
| 403 Forbidden | 权限不足 | 提示用户无权访问 |
| 404 Not Found | 费用记录或图片不存在 | 显示占位图或提示 |
| 413 Payload Too Large | 文件超过5MB | 提示用户压缩图片 |
| 500 Internal Server Error | 服务器错误 | 联系后端开发者 |

### 前端错误处理示例
```javascript
try {
  const response = await fetch('/api/expenses/456/receipt');

  if (response.status === 404) {
    // 图片不存在，显示占位图
    img.src = '/images/no-receipt-placeholder.png';
  } else if (response.status === 403) {
    // 权限不足
    alert('您没有权限查看此收据');
  } else if (response.ok) {
    const blob = await response.blob();
    img.src = URL.createObjectURL(blob);
  }
} catch (error) {
  console.error('Network error:', error);
  img.src = '/images/error-placeholder.png';
}
```

---

## 8. 最佳实践

### 图片优化建议
1. **压缩图片**：前端上传前压缩到 85% 质量
2. **限制尺寸**：建议不超过 1920x1080 像素
3. **格式选择**：优先使用 JPEG 格式（PNG 文件较大）

### 示例：前端图片压缩
```javascript
function compressImage(file, maxWidth = 1920, quality = 0.85) {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        let width = img.width;
        let height = img.height;

        if (width > maxWidth) {
          height *= maxWidth / width;
          width = maxWidth;
        }

        canvas.width = width;
        canvas.height = height;

        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, width, height);

        canvas.toBlob((blob) => {
          resolve(new File([blob], file.name, {
            type: 'image/jpeg',
            lastModified: Date.now()
          }));
        }, 'image/jpeg', quality);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  });
}

// 使用
const originalFile = fileInput.files[0];
const compressedFile = await compressImage(originalFile);
formData.append('receipt', compressedFile);
```

### 性能优化
1. **懒加载**：使用 Intersection Observer 延迟加载图片
2. **缩略图**：考虑让后端提供缩略图端点（未来优化）
3. **缓存**：浏览器会自动缓存图片，无需特殊处理

---

## 9. 调试技巧

### 检查图片是否成功上传
```javascript
// 创建费用后立即测试图片 URL
fetch(result.data.receiptUrl)
  .then(response => {
    if (response.ok) {
      console.log('✅ 图片上传成功');
    } else {
      console.error('❌ 图片访问失败:', response.status);
    }
  });
```

### 查看实际存储路径（仅开发环境）
```javascript
// 在浏览器控制台
fetch('/api/expenses/456')
  .then(r => r.json())
  .then(expense => {
    console.log('数据库路径:', expense.receiptImagePath);
    // 例如: device123456/receipt_1700000000000.jpg
  });
```

---

## 10. 常见问题 FAQ

**Q: 图片 URL 是否永久有效？**
A: 是的，只要费用记录存在，图片 URL 就永久有效。

**Q: 可以使用相对路径吗？**
A: 可以。`/api/expenses/456/receipt` 是相对路径，会自动解析为完整 URL。

**Q: 支持 PNG 格式吗？**
A: 是的，支持 JPEG、PNG、JPG 格式。

**Q: 图片文件名是什么？**
A: 后端自动生成：`receipt_{时间戳}.{扩展名}`，前端无需关心。

**Q: 如何判断图片是否存在？**
A: 发送 HEAD 请求检查：
```javascript
const response = await fetch('/api/expenses/456/receipt', { method: 'HEAD' });
const exists = response.ok;
```

**Q: 可以更新已上传的图片吗？**
A: 当前版本不支持单独更新图片，需要更新整个费用记录。

---

## 11. 联系支持

如有问题，请联系后端开发团队或查看：
- API 文档：`receipt_info_upload.md`
- 源码：`src/main/java/org/traccar/api/resource/ExpenseResource.java`
