package io.fossa.maven;

import java.io.File;

import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

public class FossaMavenPluginTest extends AbstractMojoTestCase {
  public void testExecution() throws Exception {
    File basedir = getTestFile("src/test/resources/unit/child");
    assertNotNull(basedir);
    assertTrue(basedir.exists());

    MojoRule r = new MojoRule(this);
    FossaMavenPlugin fossa = (FossaMavenPlugin) r.lookupConfiguredMojo(basedir, "analyze");

    fossa.setLog(new DefaultLog(new ConsoleLogger(Logger.LEVEL_DEBUG, "test-logger")));
    assertNotNull(fossa);
    fossa.execute();

    System.out.println("OK");
  }
}
