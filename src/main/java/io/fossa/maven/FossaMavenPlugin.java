package io.fossa.maven;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

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
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.IOUtil;

import oshi.PlatformEnum;
import oshi.SystemInfo;

/**
 * Runs a FOSSA analysis on the current project.
 */
@Mojo(name = "analyze")
public class FossaMavenPlugin extends AbstractMojo {
  public enum Source {
    INSTALL, LOCAL,
  }

  public enum Mode {
    OUTPUT, UPLOAD,
  }

  @Parameter(property = "analyze.source", defaultValue = "INSTALL")
  private Source source;

  @Parameter(property = "analyze.path")
  private File path;

  @Parameter(property = "analyze.version", defaultValue = "v0.7.3-2")
  private String version;

  @Parameter(property = "analyze.mode", defaultValue = "UPLOAD")
  private Mode mode;

  @Parameter(property = "analyze.apiKey")
  private String apiKey;

  @Parameter(property = "analyze.endpoint")
  private String endpoint;

  @Parameter(property = "analyze.configurationFile")
  private File configurationFile;

  public static class Result {
    public int exitCode;
    public Collection<Analysis> output;
    public String stdout;
    public String stderr;

    public Result() {
      stdout = "";
      stderr = "";
    }
  }

  private Result output;

  public Result getResult() {
    return output;
  }

  public void execute() throws MojoExecutionException, MojoFailureException {
    Log log = getLog();
    log.debug("parameters:");
    log.debug("source: " + source);
    log.debug("path: " + path);
    log.debug("version: " + version);
    log.debug("mode: " + mode);
    log.debug("apiKey: " + apiKey);
    log.debug("endpoint: " + endpoint);
    log.debug("configurationFile: " + configurationFile);
    log.debug("cwd: " + System.getProperty("user.dir"));

    // Load CLI.
    String cli;
    switch (source) {
    case INSTALL:
      cli = install(version).toString();
      break;
    case LOCAL:
      if (path == null) {
        throw new MojoFailureException("local CLI is specified, but no path is provided");
      }
      cli = path.getPath();
      break;
    default:
      throw new MojoExecutionException("unable to parse CLI source");
    }

    // Execute CLI in current project.
    try {
      log.debug("Building command");
      // Build command: add flags.
      List<String> args = new LinkedList<String>();
      args.add(cli.toString());
      if (configurationFile != null) {
        log.debug("Adding configuration file: " + configurationFile.getAbsolutePath());
        args.add("--config");
        args.add(configurationFile.getAbsolutePath());
      }
      if (endpoint != null) {
        log.debug("Adding endpoint: " + endpoint);
        args.add("--endpoint");
        args.add(endpoint);
      }
      switch (mode) {
      case OUTPUT:
        log.debug("Adding output flag");
        args.add("--output");
        break;
      case UPLOAD:
        break;
      default:
        throw new MojoExecutionException("unable to parse CLI mode");
      }
      log.debug("Done adding flags");

      // Build command: set environment.
      ProcessBuilder pb = new ProcessBuilder(args);
      Map<String, String> env = pb.environment();
      if (apiKey != null && apiKey.length() > 0) {
        env.put("FOSSA_API_KEY", apiKey);
      }

      // Run command.
      log.debug("Running CLI");
      Process proc = pb.start();

      // Read output.
      BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      boolean keepReading = true;
      Result result = new Result();
      do {
        if (stdout.ready()) {
          String line = stdout.readLine();
          log.info("[FOSSA] " + line);
          result.stdout = result.stdout.concat(line);
        }
        if (stderr.ready()) {
          String line = stderr.readLine();
          log.info("[FOSSA: STDERR] " + line);
          result.stderr = result.stderr.concat(line);
        }
        keepReading = stdout.ready() || stderr.ready() || proc.isAlive();
      } while (keepReading);
      result.exitCode = proc.waitFor();
      log.debug("Exit code: " + result.exitCode);

      // Parse output.
      Gson gson = new Gson();
      try {
        log.debug("Parsing output");
        Type analyses = new TypeToken<Collection<Analysis>>() {
        }.getType();
        result.output = gson.fromJson(result.stdout, analyses);
        if (result.output == null) {
          log.warn("Could not parse CLI output");
        } else {
          log.debug("Parsing output succeeded");
        }
      } catch (JsonParseException e) {
        log.error("Exception while parsing CLI output", e);
      }
      output = result;
    } catch (IOException e) {
      throw new MojoFailureException("failed to run analysis", e);
    } catch (InterruptedException e) {
      throw new MojoFailureException("caught interrupt", e);
    }
  }

  private Path install(String version) throws MojoExecutionException {
    Log log = getLog();
    log.info("Loading latest FOSSA CLI");

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
          if (fossa.exists()) {
            fossa.delete();
          }
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

    log.debug("CLI installed");
    return cli;
  }
}
