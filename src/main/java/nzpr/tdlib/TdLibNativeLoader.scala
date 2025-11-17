package nzpr.tdlib

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

object TdlibNativeLoader {
  private var loaded = false

  def tryLoad(): Unit = synchronized {
    if (loaded) return
    loaded = true

    val os = System.getProperty("os.name").toLowerCase
    val arch = System.getProperty("os.arch").toLowerCase

    def detectOS: String =
      if (os.contains("linux")) "linux"
      else if (os.contains("mac") || os.contains("darwin")) "macos"
      else if (os.contains("win")) "windows"
      else throw new RuntimeException(s"Unsupported OS: $os")

    def detectArch: String =
      if (arch.contains("aarch64") || arch.contains("arm64")) "aarch64"
      else if (arch.contains("x86_64") || arch.contains("amd64")) "x86_64"
      else if (
        arch.contains("x86") || arch.contains("i386") || arch.contains("i686")
      ) "x86"
      else throw new RuntimeException(s"Unsupported architecture: $arch")

    val osFolder = detectOS
    val archFolder = detectArch

    val folder = s"$osFolder-$archFolder"

    val libName =
      osFolder match {
        case "linux"   => "libtdjni.so"
        case "macos"   => "libtdjni.dylib"
        case "windows" => "tdjni.dll"
      }

    val resourcePath = s"/native/$folder/$libName"
    val is = Option(getClass.getResourceAsStream(resourcePath))
      .getOrElse(
        throw new RuntimeException(s"Native library not found: $resourcePath")
      )

    val tempFile = Files.createTempFile("tdjni", libName).toFile
    tempFile.deleteOnExit()
    Files.copy(is, tempFile.toPath, StandardCopyOption.REPLACE_EXISTING)

    System.load(tempFile.getAbsolutePath)
  }
}
