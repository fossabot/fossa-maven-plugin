package io.fossa.maven;

public class Analysis {
  public String Name;
  public String Type;
  public String Manifest;
  public Build Build;

  public static class Build {
    public String Artifact;
    public String Context;
    public Boolean Succeeded;
    public String[] Imports;
    public Dependency[] Dependencies;
  }

  public static class Dependency {
    public String locator;
    public String[] imports;
  }
}
