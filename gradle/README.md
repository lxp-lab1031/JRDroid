# 初回のGradle Wrapper生成

この土台にはWrapperの実行スクリプト・JARを同梱していません。
WindowsでJDK 17と公式Gradle 8.9のbin配布を用意し、GradleのbinをPATHへ追加します。
配布元: https://gradle.org/releases/ （8.9の過去リリース）
ダウンロードした配布物は公式SHA-256で照合してください。

ルートでPowerShellから実行:

```powershell
gradle --version
# Gradle 8.9 / JVM 17であることを確認
gradle wrapper --gradle-version 8.9 --distribution-type bin
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Android SDK Platform 35とネットワーク接続も必要です。
生成された`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、
`gradle/wrapper/gradle-wrapper.properties`をまとめてコミットしてください。
Wrapper JARを空ファイルや代替スクリプトで置き換えないでください。
