package io.fossa.maven;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import io.fossa.maven.Analysis.Dependency;
import io.fossa.maven.FossaMavenPlugin.Result;

public class FossaMavenPluginTest extends AbstractMojoTestCase {
  public void testExecution() throws Exception {
    // Load POM fixture.
    File basedir = getTestFile("src/test/resources/unit/child");
    assertNotNull(basedir);
    assertTrue(basedir.exists());

    // Initialize Mojo.
    MojoRule r = new MojoRule(this);
    FossaMavenPlugin fossa = (FossaMavenPlugin) r.lookupConfiguredMojo(basedir, "analyze");
    assertNotNull(fossa);

    // Test execution.
    fossa.setLog(new DefaultLog(new ConsoleLogger(Logger.LEVEL_DEBUG, "test-logger")));
    fossa.execute();

    // Check result.
    Result result = fossa.getResult();
    assertEquals(1, result.output.size());
    Analysis analysis = result.output.iterator().next();
    assertEquals("io.fossa.test:fossa-maven-plugin-test-child", analysis.Name);

    // Check imports.
    String[] expectedImports = new String[] {
      "mvn+com.github.oshi:oshi-core$3.7.2",
      "mvn+junit:junit$4.12",
      "mvn+org.apache.commons:commons-compress$1.2",
    };
    Arrays.sort(analysis.Build.Imports);
    assertArrayEquals(expectedImports, analysis.Build.Imports);

    // Check dependencies.
    Map<String, String[]> expectedLocators = new HashMap<String, String[]>();
    expectedLocators.put("mvn+com.github.oshi:oshi-core$3.7.2", new String[]{
      "mvn+net.java.dev.jna:jna-platform$4.5.2",
      "mvn+org.slf4j:slf4j-api$1.7.25",
    });
    expectedLocators.put("mvn+net.java.dev.jna:jna-platform$4.5.2", new String[]{
      "mvn+net.java.dev.jna:jna$4.5.2",
    });
    expectedLocators.put("mvn+junit:junit$4.12", new String[]{
      "mvn+org.hamcrest:hamcrest-core$1.3",
    });
    expectedLocators.put("mvn+org.apache.commons:commons-compress$1.2", null);
    for (Dependency dep : analysis.Build.Dependencies) {
      assertTrue(expectedLocators.containsKey(dep.locator));
      String[] expected = expectedLocators.get(dep.locator);

      if (dep.imports == null) {
        assertNull(expected);
      } else {
        Arrays.sort(dep.imports);
        assertArrayEquals(expected, dep.imports);
      }
    }
  }
}
