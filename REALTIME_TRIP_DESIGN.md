# Real-Time Trip Detection System

## Architecture

```
H02 Device → Protocol Decoder → Processing Pipeline → DatabaseHandler → RealtimeTripDetectionHandler
                                                                              ↓
                                                                    RealtimeTripStateManager (内存)
                                                                              ↓
                                                                    tcaf_realtime_trips (数据库)
```

## Core Components

| 组件 | 路径 | 职责 |
|------|------|------|
| `RealtimeTripDetectionHandler` | `org.traccar.handler` | Pipeline 中接收 Position，检测 Trip 开始/结束 |
| `RealtimeTripStateManager` | `org.traccar.session.state` | 管理设备状态（内存），Trip 检测算法 |
| `RealtimeTripState` | `org.traccar.session.state` | 单个设备的状态：currentTrip, lastIgnition, lastStopTime |
| `AFTrip` | `org.traccar.model` | Trip 数据模型，映射 `tcaf_realtime_trips` 表 |
| `AFTripResource` | `org.traccar.api.resource` | REST API: `/api/realtimetrips` |

## Trip Detection Rules

### Trip 开始条件

1. **冷启动**：首次数据或服务器重启后，如果 `ignition=true` 且 `motion=true`，立即开始
2. **状态转换**：`ignition: false → true` 或 `motion: false → true`

### Trip 结束条件

1. **熄火**：`ignition: true → false`，**立即结束**
2. **怠速超时**：`ignition=true` 但 `distance=0` 持续超过 3 分钟，结束

## Configuration

```properties
realtimeTrip.minStopDuration=180000   # 怠速超时时间 (ms)，默认 3 分钟
realtimeTrip.minDistance=100          # 最小有效 Trip 距离 (m)
realtimeTrip.minDuration=60000        # 最小有效 Trip 时长 (ms)
realtimeTrip.ignitionRequired=true    # 是否需要 ignition 信号
```

## Database Table

```sql
tcaf_realtime_trips (
  id, deviceId, userId,
  startTime, endTime,
  startPositionId, endPositionId,
  startOdometer, distance, duration,
  startAddress, endAddress, attributes
)
```

## API

- `GET /api/realtimetrips?from=2025-01-01T00:00&to=2025-01-01T23:59&deviceId=123`
- `GET /api/realtimetrips/{id}`

详见 `REALTIME_TRIPS_API.md`

## Key Files

```
src/main/java/org/traccar/
├── handler/RealtimeTripDetectionHandler.java
├── session/state/RealtimeTripStateManager.java
├── session/state/RealtimeTripState.java
├── model/AFTrip.java
├── api/resource/AFTripResource.java
├── config/Keys.java (REALTIME_TRIP_* 配置项)
└── ProcessingHandler.java (Handler 注册)

schema/changelog-6.14.0.xml (数据库表定义)
```

## Notes

- Trip 状态保存在内存中，服务器重启后通过冷启动检测恢复
- Trip 数据只在结束时写入数据库，进行中的 Trip 存在内存
- 不满足最小距离/时长要求的 Trip 会被丢弃