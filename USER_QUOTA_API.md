# User Quota Management APIs

## 1. Trial User Registration

**Endpoint**: `POST /api/users/trial-registration`
**Auth**: No auth required

**Request Body**:
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123",
  "phone": "1234567890"  // optional
}
```

**Response**:
```json
{
  "user": {
    "id": 123,
    "name": "John Doe",
    "email": "john@example.com",
    "expirationTime": "2025-01-24T00:00:00Z",
    "temporary": true
  },
  "quota": {
    "userId": 123,
    "year": 2025,
    "userType": "TRIAL_2025",
    "maxLimit": 50,
    "currentUsage": 0,
    "remainingQuota": 50
  },
  "message": "Trial account created successfully. You have 50 receipt scans for 7 days."
}
```

## 2. Upgrade User (Admin Only)

**Endpoint**: `POST /api/users/{id}/upgrade?userType={type}&remark={remark}`
**Auth**: Admin only

**Query Parameters**:
- `userType`: `TRIAL_2025` | `TAX_SEASON_2025` | `TAX_SEASON_2026` | `ANNUAL_USER`
- `remark`: Optional remark (e.g., "Cash payment $99")

**Response**:
```json
{
  "success": true,
  "user": { ... },
  "quota": {
    "maxLimit": 500,
    "currentUsage": 0,
    "remainingQuota": 500
  },
  "newUserType": "TAX_SEASON_2025",
  "newUserTypeName": "2025报税季用户",
  "expirationTime": "2026-05-01T00:00:00Z"
}
```

## 3. Get User Quota

**Endpoint**: `GET /api/users/{id}/receipt-quota`
**Auth**: User can query own quota, admin can query all

**Response**:
```json
{
  "userId": 123,
  "year": 2025,
  "userType": "TAX_SEASON_2025",
  "userTypeName": "2025报税季用户",
  "maxLimit": 500,
  "currentUsage": 25,
  "remainingQuota": 475,
  "hasQuota": true,
  "expirationTime": "2026-05-01T00:00:00Z"
}
```

## User Types

| Type | Validity | Scan Quota | Expiration |
|------|----------|------------|------------|
| TRIAL_2025 | 7 days | 50 | 7 days from registration |
| TAX_SEASON_2025 | ~1 year | 500 | 2026-05-01 |
| TAX_SEASON_2026 | ~1 year | 500 | 2027-05-01 |
| ANNUAL_USER | 365 days | 1000 | 365 days from upgrade |

## Error Responses

```json
{
  "error": "Email already registered"
}
```

```json
{
  "error": "Invalid user type: INVALID_TYPE"
}
```

```json
{
  "error": "Quota not found for user"
}
```
