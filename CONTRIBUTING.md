# Contributing to share-it

Thank you for your help. Bug reports, ideas and pull requests are welcome.

## Report a bug

Open an issue and include this information:

- The phone model and the Android version.
- The computer and the app that you used (for example, macOS 26 Finder, or Windows 11 WinSCP).
- The build number at the bottom of the share-it screen.
- What you did, what you expected, and what happened.

Do not put passwords, private file names or private IP addresses in an issue.

## Make a change

1. Fork the repo and make a branch from `main`.
2. Set up the tools in the [Build](README.md#build) section of the README.
3. Make your change. Keep each pull request to 1 topic.
4. Run the tests:
   ```sh
   go test ./server
   ```
5. Build the APK and test your change on a phone:
   ```sh
   gomobile bind -target=android/arm64,android/amd64 -androidapi 30 -javapkg com.shareit \
     -o android/app/libs/server.aar ./server
   cd android && ./gradlew assembleDebug
   ```
6. Open a pull request to `main`. Tell us what you changed, why, and how you tested it.

The maintainer reviews each pull request and merges it.

## Code rules

- Keep the app small. Do not add a dependency for a task that a few lines of code can do.
- The server is in Go (`server/`). Add a test in `server/server_test.go` for each change in server behavior.
- The app is in Kotlin and does not use AndroidX (`android/`).
- Do not send data to the internet. share-it works only on the local network.

## Releases

The maintainer makes each release on GitHub. When a release is published, GitHub Actions builds the APK and attaches it to the release. Commits and merges do not make a release.

## License

By contributing, you agree that your contribution uses the [MIT License](LICENSE) of this project.
