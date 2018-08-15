package io.fossa.maven;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.IOUtil;

import oshi.PlatformEnum;
import oshi.SystemInfo;

/**
 * Says "Hi" to the user.
 */
@Mojo(name = "analyze")
public class FossaMavenPlugin extends AbstractMojo {
  // public enum CliSource {
  //   INSTALL,
  //   LOCAL
  // }

  // @Parameter(property = "analyze.cli", defaultValue = "INSTALL")
  // private CliSource cliSource;

  // @Parameter(property = "analyze.localCliPath")
  // private File localCliPath;

  // @Parameter(property = "analyze.cliVersion")
  // private String cliVersion;

  // @Parameter(property = "analyze.configurationFile", defaultValue = ".fossa.yml")
  // private File configurationFile;

  public void execute() throws MojoExecutionException, MojoFailureException {
    Log log = getLog();

    // Detect OS.
    String os;
    String format = "tar.gz";
    PlatformEnum platform = SystemInfo.getCurrentPlatformEnum();
    switch (platform) {
    case WINDOWS:
      os = "windows";
      format = "zip";
      break;

    case MACOSX:
      os = "darwin";
      break;

    case LINUX:
      os = "linux";
      break;

    default:
      throw new MojoExecutionException("unsupported OS: " + platform.toString());
    }
    log.debug("Detected OS: " + os);
    log.debug("Detected archive format: " + format);

    // Detect architecture.
    String arch;
    if ((new SystemInfo()).getHardware().getProcessor().isCpu64bit()) {
      arch = "amd64";
    } else {
      throw new MojoExecutionException("unsupported architecture: must be amd64");
    }
    log.debug("Detected architecture: " + arch);

    // Construct download URL.
    String version = "v0.7.3-1";
    String url = "https://github.com/fossas/fossa-cli/releases/download/" + version + "/fossa-cli_"
        + version.substring(1) + "_" + os + "_" + arch + "." + format;
    log.debug("Download URL: " + url);

    // Download CLI.
    InputStream download;
    try {
      log.debug("Downloading CLI");
      download = (new URL(url)).openConnection().getInputStream();
    } catch (IOException e) {
      throw new MojoExecutionException("error while downloading CLI", e);
    }

    // Install CLI.
    String tmpdir = System.getProperty("java.io.tmpdir");
    Path cli = Paths.get(tmpdir, "fossa");
    log.debug("Installation path: " + cli.toString());
    try {
      log.debug("Unpacking CLI");
      CompressorInputStream decompressed = new CompressorStreamFactory()
          .createCompressorInputStream(new BufferedInputStream(download));
      ArchiveInputStream unarchived = new ArchiveStreamFactory()
          .createArchiveInputStream(new BufferedInputStream(decompressed));
      ArchiveEntry entry;
      while ((entry = unarchived.getNextEntry()) != null) {
        log.debug("Archive entry: " + entry.getName());
        if (entry.getName().trim().equals("fossa")) {
          log.debug("Installing CLI");
          File fossa = new File(cli.toString());
          OutputStream tmpStream = new FileOutputStream(fossa);
          IOUtil.copy(unarchived, tmpStream);
          fossa.setExecutable(true);
          unarchived.close();
          tmpStream.close();
          break;
        }
      }
      if (entry == null) {
        throw new MojoExecutionException("could not install fossa");
      }
    } catch (CompressorException e) {
      throw new MojoExecutionException("error while decompressing CLI", e);
    } catch (ArchiveException e) {
      throw new MojoExecutionException("error while unpacking CLI", e);
    } catch (IOException e) {
      throw new MojoExecutionException("error while installing CLI", e);
    }

    // Execute CLI in current project.
    try {
      ProcessBuilder pb = new ProcessBuilder(cli.toString());
      pb.redirectErrorStream(true);
      log.debug("API key: " + System.getenv("FOSSA_API_KEY"));
      log.debug("Running CLI");
      Process proc = pb.start();
      BufferedReader output = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      String line;
      while ((line = output.readLine()) != null) {
        log.info(line);
      }
      int exitCode = proc.waitFor();
      log.debug("Exit code: " + exitCode);
    } catch (IOException e) {
      throw new MojoFailureException("failed to run analysis", e);
    } catch (InterruptedException e) {
      throw new MojoFailureException("caught interrupt", e);
    }
  }
}
