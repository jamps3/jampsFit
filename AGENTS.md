Ensure all warnings and errors are resolved and the build passes before proceeding. After every successful build, provide a concise Git commit message wrapped in a markdown code block.

Prefer idiomatic Kotlin property assignment in scope functions when the platform API exposes a Kotlin property wrapper. For example, inside `Intent.apply { ... }`, use `data = Uri.fromParts(...)` instead of `setData(...)`.
