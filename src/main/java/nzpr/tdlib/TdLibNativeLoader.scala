package nzpr.tdlib

import java.io.{File, FileOutputStream, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.ZipInputStream
import scala.util.{Try, Using}

object TdlibNativeLoader {
  private var loaded = false
  private val GITHUB_REPO = "nzpr/tdlib"
  private val CACHE_DIR =
    Paths.get(System.getProperty("user.home"), ".tdlib", "native")

  private def isLibraryAvailable(libName: String): Boolean = {
    try {
      System.loadLibrary(
        libName.stripPrefix("lib").stripSuffix(".so").stripSuffix(".dylib")
      )
      true
    } catch {
      case _: UnsatisfiedLinkError => false
    }
  }

  def tryLoad(): Unit = synchronized {
    if (loaded) return

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
    val platform = s"$osFolder-$archFolder"

    val libName =
      osFolder match {
        case "linux"   => "libtdjni.so"
        case "macos"   => "libtdjni.dylib"
        case "windows" => "tdjni.dll"
      }

    if (isLibraryAvailable(libName)) {
      loaded = true
      return
    }
    loaded = true

    // Try to load from resources first (for local development)
    val resourcePath = s"/native/$platform/$libName"
    val resourceStream = Option(getClass.getResourceAsStream(resourcePath))

    resourceStream match {
      case Some(is) =>
        // Load from resources (local development)
        val tempFile = Files.createTempFile("tdjni", libName).toFile
        tempFile.deleteOnExit()
        Files.copy(is, tempFile.toPath, StandardCopyOption.REPLACE_EXISTING)
        System.load(tempFile.getAbsolutePath)
        is.close()

      case None =>
        // Download from GitHub releases
        val cachedLib = CACHE_DIR.resolve(platform).resolve(libName)

        if (!Files.exists(cachedLib)) {
          downloadNativeLibrary(platform, cachedLib)
        }

        System.load(cachedLib.toAbsolutePath.toString)
    }
  }

  private def downloadNativeLibrary(
      platform: String,
      targetPath: Path
  ): Unit = {
    println(s"Downloading native library for $platform...")

    // Get latest release version
    val version = getLatestVersion()
    val downloadUrl =
      s"https://github.com/$GITHUB_REPO/releases/download/$version/native-$platform.zip"

    // Download and extract
    Files.createDirectories(targetPath.getParent)

    Using.resource(new URL(downloadUrl).openStream()) { inputStream =>
      Using.resource(new ZipInputStream(inputStream)) { zipStream =>
        var entry = zipStream.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            val fileName = Paths.get(entry.getName).getFileName.toString
            val outputPath = targetPath.getParent.resolve(fileName)

            Using.resource(new FileOutputStream(outputPath.toFile)) {
              outputStream =>
                val buffer = new Array[Byte](8192)
                var len = zipStream.read(buffer)
                while (len > 0) {
                  outputStream.write(buffer, 0, len)
                  len = zipStream.read(buffer)
                }
            }
          }
          zipStream.closeEntry()
          entry = zipStream.getNextEntry
        }
      }
    }

    println(
      s"Downloaded and extracted native library to ${targetPath.getParent}"
    )
  }

  private def getLatestVersion(): String = {
    val apiUrl = s"https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    Try {
      val connection =
        new URL(apiUrl).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setRequestProperty("Accept", "application/json")

      Using.resource(
        scala.io.Source.fromInputStream(connection.getInputStream)
      ) { source =>
        val json = source.mkString
        // Simple JSON parsing to extract tag_name
        val tagPattern = """"tag_name"\s*:\s*"([^"]+)"""".r
        tagPattern.findFirstMatchIn(json) match {
          case Some(m) => m.group(1)
          case None =>
            throw new RuntimeException(
              "Could not parse release version from GitHub API"
            )
        }
      }
    }.getOrElse {
      // Fallback: try to read version from system property or use latest
      System.getProperty("tdlib.version", "latest")
    }
  }
}
