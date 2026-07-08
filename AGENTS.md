Ensure all warnings and errors are resolved and the build passes before proceeding. After every successful build, provide a concise Git commit message wrapped in a markdown code block.

Prefer idiomatic Kotlin property assignment in scope functions when the platform API exposes a Kotlin property wrapper. For example, inside `Intent.apply { ... }`, use `data = Uri.fromParts(...)` instead of `setData(...)`.

Known local ADB path for device/log checks: `C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

Do not upgrade the Android Gradle Plugin to `9.3.0-rc01` in this project; the local Android Studio version does not support it yet. Keep using the supported AGP line unless the IDE/toolchain is upgraded first.
