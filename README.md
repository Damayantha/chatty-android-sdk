# chatty-sdk (Android)

Native Jetpack Compose chat widget SDK for Chatty — talks to the same `/api/widget/*` backend as the web widget, rendered with real Compose UI (no WebView).

> **Note:** this module was written in an environment without the Android SDK/Gradle toolchain installed, so it has not been compiled or run on a device/emulator. Review and build it (`./gradlew :chatty-sdk:assembleDebug`) before shipping.

## Install

Maven Central publishing is configured (`com.vanniktech.maven.publish`, see `chatty-sdk/build.gradle.kts`)
but hasn't been run yet — it needs a verified Sonatype account for the `com.personaliai` namespace.
A JitPack build was also attempted and failed (recorded as `"1.0.0": "Error"` at
`jitpack.io/api/builds/com.github.Damayantha/chatty-android-sdk`) — likely the `mavenPublishing {}`
block trying to configure Sonatype publishing without credentials present breaks the build in
JitPack's environment; worth trying `publishToMavenCentral()` behind a conditional (only when
Sonatype credentials are actually set) if you want JitPack to work as an interim distribution
channel.

Until one of those is sorted out, include it as a local module:

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
