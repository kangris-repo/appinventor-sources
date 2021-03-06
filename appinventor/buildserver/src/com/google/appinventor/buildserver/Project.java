// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0
package com.google.appinventor.buildserver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This class gives access to Young Android project files.
 *
 * <p>A Young Android project file is essentially a Java properties file.
 *
 * @author markf@google.com (Mark Friedman)
 */
public final class Project {

  /**
   * Representation of a source file containing its name and file location.
   */
  public static class SourceDescriptor {

    // Qualified name of the class defined by the source file
    private final String qualifiedName;

    // File descriptor for the source
    private final File file;

    // Source type
    public enum SourceType {
      FORM,
      TASK
    }

    private final SourceType sourceType;

    private SourceDescriptor(String qualifiedName, File file, SourceType sourceType) {
      this.qualifiedName = qualifiedName;
      this.file = file;
      this.sourceType = sourceType;
    }

    /**
     * Returns the qualified name of the class defined by the source file.
     *
     * @return  class name of source file
     */
    public String getQualifiedName() {
      return qualifiedName;
    }

    /**
     * Returns a file descriptor for the source file
     *
     * @return  file descriptor
     */
    public File getFile() {
      return file;
    }

    /**
     * Returns the source type of the file
     *
     * @return
     */
    public SourceType getSourceType() {
      return sourceType;
    }
  }

  /*
   * Property tags defined in the project file:
   *
   *    main - qualified name of main form class
   *    name - application name
   *    icon - application icon
   *    versioncode - version code
   *    versionname - version name
   *    source - comma separated list of source root directories
   *    assets - assets directory (for image and data files bundled with the application)
   *    build - output directory for the compiler
   */
  private static final String MAINTAG = "main";
  private static final String NAMETAG = "name";
  private static final String ICONTAG = "icon";
  private static final String SOURCETAG = "source";
  private static final String VCODETAG = "versioncode";
  private static final String VNAMETAG = "versionname";
  private static final String ASSETSTAG = "assets";
  private static final String BUILDTAG = "build";
  private static final String USESLOCATIONTAG = "useslocation";
  private static final String ANAMETAG = "aname";

  // Table containing project properties
  private Properties properties;

  // Project directory. This directory contains the project.properties file.
  private String projectDir;

  // Build output directory override, or null.
  private String buildDirOverride;

  // List of source files
  private List<SourceDescriptor> sources;

  // List of screen file names
  private List<String> screens;

  // List of task file names
  private List<String> tasks;

  // List of yail file names;
  private Map<String, File> yails;

  // Logging support
  private static final Logger LOG = Logger.getLogger(Project.class.getName());

  /**
   * Creates a new Young Android project descriptor.
   *
   * @param projectFile  path to project file
   */
  public Project(String projectFile) {
    this(new File(projectFile));
  }

  /**
   * Creates a new Young Android project descriptor.
   *
   * @param projectFile  path to project file
   * @param buildDirOverride  build output directory override, or null
   */
  public Project(String projectFile, String buildDirOverride) {
    this(new File(projectFile));
    this.buildDirOverride = buildDirOverride;
  }

  /**
   * Creates a new Young Android project descriptor.
   *
   * @param file  project file
   */
  public Project(File file) {
    try {
      File parentFile = Preconditions.checkNotNull(file.getParentFile());
      projectDir = parentFile.getAbsolutePath();

      // Load project file
      properties = new Properties();
      FileInputStream in = new FileInputStream(file);
      try {
        properties.load(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns the name of the main form class
   *
   * @return  main form class name
   */
  public String getMainClass() {
    return properties.getProperty(MAINTAG);
  }

  /**
   * Sets the name of the main form class.
   *
   * @param main  main form class name
   */
  public void setMainClass(String main) {
    properties.setProperty(MAINTAG, main);
  }

  /**
   * Returns the name of the project (application).
   *
   * @return  project name
   */
  public String getProjectName() {
    return properties.getProperty(NAMETAG);
  }

  /**
   * Sets the name of the project (application)
   *
   * @param name  project name
   */
  public void setProjectName(String name) {
    properties.setProperty(NAMETAG, name);
  }

  /**
   * Returns the name of the icon
   *
   * @return  icon name
   */
  public String getIcon() {
    return properties.getProperty(ICONTAG);
  }

  /**
   * Sets the name of the icon
   *
   * @param icon  icon name
   */
  public void setIcon(String icon) {
    properties.setProperty(ICONTAG, icon);
  }

  /**
   * Returns the version code.
   *
   * @return  version code
   */
  public String getVCode() {
    return properties.getProperty(VCODETAG);
  }

  /**
   * Sets the version code.
   *
   * @param vcode  version code
   */
  public void setVCode(String vcode) {
    properties.setProperty(VCODETAG, vcode);
  }

  /**
   * Returns the version name.
   *
   * @return  version name
   */
  public String getVName() {
    return properties.getProperty(VNAMETAG);
  }

  /**
   * Sets the version name.
   *
   * @param vname  version name
   */
  public void setVName(String vname) {
    properties.setProperty(VNAMETAG, vname);
  }

  /**
   * gets the useslocation property
   *
   * @return useslocation property
   */
  public String getUsesLocation() {
    String retval = properties.getProperty(USESLOCATIONTAG);
    if (retval == null)         // Older Projects won't have this
      retval = "False";
    return retval;
  }

  /**
   * Returns the app name.
   *
   * @return  app name
   */
  public String getAName() {
    //The non-English character set can't be shown properly and need special encoding.
    String appName = properties.getProperty(ANAMETAG);
    try {
      appName = new String(appName.getBytes("ISO-8859-1"), "UTF-8");
    } catch (UnsupportedEncodingException e) {
    } catch (NullPointerException e) {
    }
    return appName;
  }

  /**
   * Sets the app name.
   *
   * @param aname  app name
   */
  public void setAName(String aname) {
    properties.setProperty(ANAMETAG, aname);
  }

  /**
   * Returns the project directory. This directory contains the project.properties file.
   *
   * @return  project directory
   */
  public String getProjectDir() {
    return projectDir;
  }

  /**
   * Returns the location of the assets directory.
   *
   * @return  assets directory
   */
  public File getAssetsDirectory() {
    return new File(projectDir, properties.getProperty(ASSETSTAG));
  }

  /**
   * Returns the location of the build output directory.
   *
   * @return  build output directory
   */
  public File getBuildDirectory() {
    if (buildDirOverride != null) {
      return new File(buildDirOverride);
    }
    return new File(projectDir, properties.getProperty(BUILDTAG));
  }

  /*
   * Recursively visits source directories and adds found Young Android source files to the list of
   * source files.
   */
  private void visitSourceDirectories(String root, File file) {
    if (file.isDirectory()) {
      // Recursively visit nested directories.
      for (String child : file.list()) {
        visitSourceDirectories(root, new File(file, child));
      }
    } else {
      // Add Young Android source files to the source file list
      if (file.getName().endsWith(YoungAndroidConstants.FORM_PROPERTIES_EXTENSION)) {
        String absName = file.getAbsolutePath();
        String name = absName.substring(root.length() + 1, absName.length() -
            YoungAndroidConstants.FORM_PROPERTIES_EXTENSION.length());
        screens.add(name.replace(File.separatorChar, '.'));
      } else if (file.getName().endsWith(YoungAndroidConstants.TASK_PROPERTIES_EXTENSION)) {
        String absName = file.getAbsolutePath();
        String name = absName.substring(root.length() + 1, absName.length() -
            YoungAndroidConstants.TASK_PROPERTIES_EXTENSION.length());
        tasks.add(name.replace(File.separatorChar, '.'));
      } else if (file.getName().endsWith(YoungAndroidConstants.YAIL_EXTENSION)) {
        String absName = file.getAbsolutePath();
        String name = absName.substring(root.length() + 1, absName.length() -
            YoungAndroidConstants.YAIL_EXTENSION.length());
        yails.put(name.replace(File.separatorChar, '.'), file);
      }
    }
  }

  /**
   * Returns a list of Yail files in the project.
   *
   * @return  list of source files
   */
  public List<SourceDescriptor> getSources() {
    // Lazily discover source files
    if (sources == null) {
      sources = Lists.newArrayList();
      screens = Lists.newArrayList();
      tasks = Lists.newArrayList();
      yails = Maps.newHashMap();
      String sourceTag = properties.getProperty(SOURCETAG);
      for (String sourceDir : sourceTag.split(",")) {
        File dir = new File(projectDir + File.separatorChar + sourceDir);
        visitSourceDirectories(dir.getAbsolutePath(), dir);
      }
      for (String name : yails.keySet()) {
        if (screens.contains(name)) {
          sources.add(new SourceDescriptor(name, yails.get(name), SourceDescriptor.SourceType.FORM));
        } else if (tasks.contains(name)) {
          sources.add(new SourceDescriptor(name, yails.get(name), SourceDescriptor.SourceType.TASK));
        }
      }
    }
    return sources;
  }
}
