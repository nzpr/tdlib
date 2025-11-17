package nzpr.tdlib
import fs2.Stream
import org.drinkless.tdlib.TdApi
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Queue
import org.drinkless.tdlib.Client.ResultHandler
import cats.effect.Sync
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.Client.ExceptionHandler
import cats.effect.std.Dispatcher
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.drinkless.tdlib.Client.LogMessageHandler
import cats.syntax.all.*
import cats.effect.Deferred

final case class TdLib[F[_]](
    updates: Stream[F, TdApi.Object],
    send: TdApi.Function[?] => F[TdApi.Object]
)

object TdLib {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  case class TdlibParameters(
      useTestDc: Boolean = false,
      databaseDirectory: String = "",
      filesDirectory: String = "",
      databaseEncryptionKey: Array[Byte] = Array.empty,
      useFileDatabase: Boolean = false,
      useChatInfoDatabase: Boolean = false,
      useMessageDatabase: Boolean = false,
      useSecretChats: Boolean = false,
      apiId: Int = 0,
      apiHash: String = "",
      systemLanguageCode: String = "",
      deviceModel: String = "",
      systemVersion: String = "",
      applicationVersion: String = ""
  )

  def loadJni[F[_]: Sync]: F[Unit] = Sync[F].delay {
    nzpr.tdlib.TdlibNativeLoader.tryLoad();
  }
  def apply[F[_]: Async](verbosityLevel: Int = 0): Resource[F, TdLib[F]] = for {
    queue <- Resource.eval(Queue.unbounded[F, TdApi.Object])
    closed <- Resource.eval(Deferred[F, Unit])
    _ <- Resource.eval(loadJni[F])
    disp <- Dispatcher.sequential
    lmh = new LogMessageHandler {
      override def onLogMessage(verbosityLevel: Int, message: String): Unit =
        verbosityLevel match {
          case 0 => disp.unsafeRunAndForget(info"$message")
          case _ => disp.unsafeRunAndForget(debug"$message")
        }
    }
    _ = Client.setLogMessageHandler(verbosityLevel, lmh)
    client <- Resource.make {
      Async[F].delay {
        Client.create(
          { (obj: TdApi.Object) =>
            val effect = obj match {
              case u: TdApi.UpdateAuthorizationState =>
                u.authorizationState match {
                  case _: TdApi.AuthorizationStateClosed =>
                    closed.complete(()).void
                  case _ => queue.offer(obj)
                }
              case _ => queue.offer(obj)
            }
            disp.unsafeRunAndForget(effect)
          },
          (e: Throwable) => disp.unsafeRunAndForget(error"$e"),
          (e: Throwable) => disp.unsafeRunAndForget(error"$e")
        )
      }
    } { c =>
      Async[F].delay(c.send(TdApi.Close(), null, null)) >> closed.get
    }
  } yield TdLib[F](
    updates = Stream
      .fromQueueUnterminated(queue)
      .evalFilter {
        case x: TdApi.UpdateOption =>
          val valOneliner = s"${x.value}".replace("\n", "")
          info"UpdateOption: ${x.name} => $valOneliner".as(false)
        case _ => true.pure[F]
      },
    send = (f: TdApi.Function[?]) =>
      Async[F].async_ { cb =>
        client.send(
          f,
          new ResultHandler {
            def onResult(obj: TdApi.Object): Unit =
              cb(Right(obj))
          },
          new ExceptionHandler {
            def onException(e: Throwable): Unit =
              cb(Left(new Exception(e.getLocalizedMessage())))
          }
        )
      }
  )
}
