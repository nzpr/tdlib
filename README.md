# tdlib

A functional Scala 3 wrapper for [TDLib](https://github.com/tdlib/td) (Telegram Database Library), providing a type-safe, effect-based API for building Telegram clients.

## Overview

This library provides a Cats Effect and FS2-based interface to TDLib, offering:

- **Functional API**: Built on Cats Effect for composable, resource-safe operations
- **Streaming Updates**: Real-time event stream via FS2
- **Type Safety**: Leverages Scala 3's type system
- **Cross-Platform**: Native library support for Linux, macOS, and Windows (x86_64 and aarch64)
- **Resource Management**: Automatic lifecycle management with proper cleanup

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "nzpr" %% "tdlib" % "0.1.0-SNAPSHOT"
```

**Requirements:**
- Scala 3.7.3 or higher
- Java 17 or higher
- TDLib native libraries for your platform (included in the artifact)

## Usage

### Basic Example

```scala
import nzpr.tdlib.TdLib
import cats.effect.{IO, IOApp}
import org.drinkless.tdlib.TdApi

object MyTelegramBot extends IOApp.Simple {
  def run: IO[Unit] = {
    TdLib[IO]().use { case TdLib(updates, send) =>
      // Handle incoming updates via FS2 stream
      val handleUpdates = updates
        .evalMap {
          case update: TdApi.UpdateNewMessage =>
            IO.println(s"New message: ${update.message}")
          case update: TdApi.UpdateAuthorizationState =>
            handleAuth(update.authorizationState, send)
          case _ => IO.unit
        }
        .compile
        .drain

      handleUpdates
    }
  }

  def handleAuth(state: TdApi.AuthorizationState, send: TdApi.Function[?] => IO[TdApi.Object]): IO[Unit] = {
    state match {
      case _: TdApi.AuthorizationStateWaitTdlibParameters =>
        send(TdApi.SetTdlibParameters(/* ... */)).void
      case _: TdApi.AuthorizationStateWaitPhoneNumber =>
        send(TdApi.SetAuthenticationPhoneNumber(/* ... */)).void
      // Handle other states...
      case _ => IO.unit
    }
  }
}
```

### API

The `TdLib` case class provides two main components:

```scala
final case class TdLib[F[_]](
  updates: Stream[F, TdApi.Object],  // Stream of incoming updates
  send: TdApi.Function[?] => F[TdApi.Object]  // Send requests to Telegram
)
```

- **`updates`**: An FS2 stream that emits TDLib updates (`TdApi.Object` instances)
- **`send`**: Function to send requests to Telegram and receive responses asynchronously

### Creating a TdLib Instance

```scala
// With default verbosity (0 = fatal errors only)
TdLib[IO]().use { tdlib => /* ... */ }

// With custom verbosity level (for debugging)
TdLib[IO](verbosityLevel = 5).use { tdlib => /* ... */ }
```

The instance is created as a `Resource`, ensuring proper cleanup when done.

## Publishing

This library is published to Maven Central. Releases are automatically published when a new GitHub release is created.

### For Maintainers

**Prerequisites:**
1. Sonatype account with access to the `nzpr` organization
2. GPG key for signing artifacts
3. GitHub repository secrets configured:
   - `SONATYPE_USERNAME`: Sonatype username
   - `SONATYPE_PASSWORD`: Sonatype password
   - `PGP_SECRET`: Base64-encoded GPG private key
   - `PGP_PASSPHRASE`: Passphrase for the GPG key

**Release Process:**

1. Update version in `build.sbt` if needed (or let the CI update it from the tag)
2. Create and push a git tag:
   ```bash
   git tag -a v0.1.0 -m "Release v0.1.0"
   git push origin v0.1.0
   ```
3. Create a GitHub release from the tag
4. The release workflow will automatically:
   - Build native libraries for all platforms
   - Sign artifacts with GPG
   - Publish to Sonatype staging
   - Release to Maven Central

**Manual Publishing (if needed):**

```bash
# Publish locally
sbt publishLocal

# Publish to Sonatype staging
sbt publishSigned

# Release to Maven Central
sbt sonatypeBundleRelease
```

## License

This project wraps TDLib, which is licensed under the Boost Software License 1.0. See TDLib's [LICENSE_1_0.txt](td/LICENSE_1_0.txt) for details.

## References

- [TDLib Documentation](https://core.telegram.org/tdlib)
- [TDLib GitHub Repository](https://github.com/tdlib/td)
- [Cats Effect Documentation](https://typelevel.org/cats-effect/)
- [FS2 Documentation](https://fs2.io/)


THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
