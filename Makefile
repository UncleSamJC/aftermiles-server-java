.PHONY: help build deploy deploy-quick deploy-safe clean test

# 默认目标
help:
	@echo "Aftermiles Traccar Server - 快捷命令"
	@echo ""
	@echo "开发命令:"
	@echo "  make build         - 构建项目（跳过测试）"
	@echo "  make test          - 运行测试"
	@echo "  make clean         - 清理构建文件"
	@echo ""
	@echo "部署命令:"
	@echo "  make deploy        - 完整部署（构建 + 上传 + 重启）"
	@echo "  make deploy-quick  - 快速部署（跳过构建）"
	@echo "  make deploy-safe   - 安全部署（带备份）"
	@echo ""
	@echo "本地运行:"
	@echo "  make run           - 本地运行服务器（debug模式）"
	@echo ""

# 构建项目
build:
	@echo "📦 开始构建..."
	./gradlew build -x test
	@echo "✅ 构建完成"

# 运行测试
test:
	@echo "🧪 运行测试..."
	./gradlew test

# 清理构建
clean:
	@echo "🧹 清理构建文件..."
	./gradlew clean
	@echo "✅ 清理完成"

# 完整部署
deploy:
	@echo "🚀 开始完整部署..."
	./deploy.sh

# 快速部署（跳过构建）
deploy-quick:
	@echo "⚡ 快速部署（跳过构建）..."
	./deploy.sh --skip-build

# 安全部署（带备份）
deploy-safe:
	@echo "🛡️ 安全部署（带备份）..."
	./deploy.sh --backup

# 本地运行
run:
	@echo "🏃 启动本地服务器..."
	@if [ -f "debug.xml" ]; then \
		java -jar target/tracker-server.jar debug.xml; \
	else \
		echo "❌ debug.xml 不存在，请先创建配置文件"; \
		echo "   参考: debug.xml.example"; \
		exit 1; \
	fi
