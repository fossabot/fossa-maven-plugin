[![CircleCI](https://circleci.com/gh/fossas/fossa-maven-plugin.svg?style=svg)](https://circleci.com/gh/fossas/fossa-maven-plugin)

# FOSSA Maven plugin

This plugin integrates into Maven to report Maven dependencies in your build. It
is intended for use cases where you need to roll out across a large number
of Maven projects.

Most projects should prefer the [FOSSA CLI](https://github.com/fossas/fossa-cli),
which provides additional integrations and functionality. The advantage of the
Maven plugin is easy integration with pre-existing Maven projects. If you have
many Maven projects that share a single parent POM, you can integrate this
plugin once with the parent POM to integrate with all of its children.

## Usage

```xml
<project>
  <!-- Other project elements... -->
  <build>
    <!-- Other build elements... -->
    <plugins>
      <!-- Other plugin elements... -->
      <plugin>
        <groupId>io.fossa</groupId>
        <artifactId>fossa-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <configuration>
          <apiKey>your-key-here</apiKey>
        </configuration>
        <!-- Include this section to automatically analyze on installation. -->
        <executions>
          <execution>
            <phase>install</phase>
            <goals>
              <goal>analyze</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```
