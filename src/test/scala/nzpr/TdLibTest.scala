import nzpr.tdlib.TdLib
import cats.Applicative
import nzpr.tdlib.TdLib
import org.drinkless.tdlib.TdApi
import cats.effect.Async
import cats.effect.std.Console
import fs2.Stream
import cats.syntax.all.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.IO
import scala.concurrent.duration._

class TdLibTest extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  "Tdlib" - {
    "initialises and asks for auth" in {
      TdLib[IO]().use { case TdLib(updates, send) =>
        updates
          .collect { case u: TdApi.UpdateAuthorizationState =>
            u.authorizationState
          }
          .collect { case _: TdApi.AuthorizationStateWaitTdlibParameters =>
          }
          .take(1)
          .timeout(3.seconds)
          .compile
          .lastOrError
      }
    }
  }
}
