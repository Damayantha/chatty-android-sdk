# chatty-sdk (Android)

Native Jetpack Compose chat widget SDK for Chatty — talks to the same `/api/widget/*` backend as the web widget, rendered with real Compose UI (no WebView).

> **Note:** the `1.0.0`/`1.0.1` tags predate a JitPack build fix and never compiled anywhere
> (`mavenPublishing {}` unconditionally required Sonatype credentials at Gradle configuration
> time, breaking the build before any Kotlin got compiled — even for a plain `assembleDebug`).
> Fixed in `v1.0.2`, confirmed via a real successful JitPack build
> (`jitpack.io/api/builds/com.github.Damayantha/chatty-android-sdk` → `"v1.0.2": "ok"`). Use
> `v1.0.2` or later.

## Install

**Via JitPack (works today, no account needed):**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.Damayantha:chatty-android-sdk:v1.0.2")
}
```

**Via Maven Central:** configured (`com.vanniktech.maven.publish`, see `chatty-sdk/build.gradle.kts`)
but not actually published yet — needs a verified Sonatype account for the `com.personaliai`
namespace. Once that's done: `implementation("com.personaliai:chatty-android-sdk:1.0.1")`.

**As a local module**, if you'd rather build from source directly:

```kotlin
// settings.gradle.kts
include(":chatty-sdk")
project(":chatty-sdk").projectDir = file("../chatty-mobile-sdks/android/chatty-sdk")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":chatty-sdk"))
}
```

## Usage — floating launcher (recommended)

```kotlin
@Composable
fun AppRoot() {
    Box(Modifier.fillMaxSize()) {
        // ...your app content...
        ChattyLauncher(botId = "YOUR_BOT_ID")
    }
}
```

## Usage — embedded full-screen chat

```kotlin
@Composable
fun SupportScreen() {
    ChattyChatScreen(botId = "YOUR_BOT_ID", modifier = Modifier.fillMaxSize())
}
```

## Notes

- If the bot has `allowed_domains` configured in the dashboard, pass a matching `host` value — native apps don't send an `Origin`/`Referer` header, so without a matching `host`, requests are rejected with 403. Leave `allowed_domains` empty for mobile-only bots to skip this.
- Lead capture and meeting booking happen conversationally (the assistant decides to ask/act) — there's no separate REST call to trigger them from the SDK.
- Polling for human-agent takeover messages runs every 4s while the chat screen is composed, matching the web widget.
- Requires `minSdk 24+`. Uses OkHttp, Coil (image loading), and Jetpack Compose Material3.
