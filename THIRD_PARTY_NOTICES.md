# 第三方组件

ColorOS录音助手使用以下 libxposed 组件：

- `io.github.libxposed:api:102.0.0`：仅编译依赖，由 LSPosed 在运行时提供。
- `io.github.libxposed:service:102.0.0`：配置页与 LSPosed 的官方通信库，包含所需的 interface 传递依赖。

上游项目：https://github.com/libxposed/service

libxposed service 采用 Apache License 2.0；许可全文随源码和 APK 保存在
`app/src/main/assets/licenses/libxposed-service-LICENSE.txt`。本项目的 MIT 许可证不替代第三方组件的许可证。
