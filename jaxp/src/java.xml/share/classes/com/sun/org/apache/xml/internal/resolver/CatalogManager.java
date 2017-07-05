/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// CatalogManager.java - Access CatalogManager.properties

/*
 * Copyright 2001-2004 The Apache Software Foundation or its licensors,
 * as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.resolver;

import com.sun.org.apache.xerces.internal.utils.SecuritySupport;
import com.sun.org.apache.xml.internal.resolver.helpers.BootstrapResolver;
import com.sun.org.apache.xml.internal.resolver.helpers.Debug;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.Vector;
import sun.reflect.misc.ReflectUtil;

/**
 * CatalogManager provides an interface to the catalog properties.
 *
 * <p>Properties can come from two places: from system properties or
 * from a <i>CatalogManager.properties</i> file. This class provides a transparent
 * interface to both, with system properties preferred over property file values.</p>
 *
 * <p>The following table summarizes the properties:</p>
 *
 * <table border="1">
 * <thead>
 * <tr>
 * <td>System Property</td>
 * <td>CatalogManager.properties<br/>Property</td>
 * <td>Description</td>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>xml.catalog.ignoreMissing</td>
 * <td>&#160;</td>
 * <td>If true, a missing <i>CatalogManager.properties</i> file or missing properties
 * within that file will not generate warning messages. See also the
 * <i>ignoreMissingProperties</i> method.</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.files</td>
 * <td>catalogs</td>
 * <td>The <emph>semicolon-delimited</emph> list of catalog files.</td>
 * </tr>
 *
 * <tr>
 * <td>&#160;</td>
 * <td>relative-catalogs</td>
 * <td>If false, relative catalog URIs are made absolute with respect to the base URI of
 * the <i>CatalogManager.properties</i> file. This setting only applies to catalog
 * URIs obtained from the <i>catalogs</i> property <emph>in the</emph>
 * <i>CatalogManager.properties</i> file</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.verbosity</td>
 * <td>verbosity</td>
 * <td>If non-zero, the Catalog classes will print informative and debugging messages.
 * The higher the number, the more messages.</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.prefer</td>
 * <td>prefer</td>
 * <td>Which identifier is preferred, "public" or "system"?</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.staticCatalog</td>
 * <td>static-catalog</td>
 * <td>Should a single catalog be constructed for all parsing, or should a different
 * catalog be created for each parser?</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.allowPI</td>
 * <td>allow-oasis-xml-catalog-pi</td>
 * <td>If the source document contains "oasis-xml-catalog" processing instructions,
 * should they be used?</td>
 * </tr>
 *
 * <tr>
 * <td>xml.catalog.className</td>
 * <td>catalog-class-name</td>
 * <td>If you're using the convenience classes
 * <tt>com.sun.org.apache.xml.internal.resolver.tools.*</tt>), this setting
 * allows you to specify an alternate class name to use for the underlying
 * catalog.</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 * @version 1.0
 */

public class CatalogManager {
  private static String pFiles         = "xml.catalog.files";
  private static String pVerbosity     = "xml.catalog.verbosity";
  private static String pPrefer        = "xml.catalog.prefer";
  private static String pStatic        = "xml.catalog.staticCatalog";
  private static String pAllowPI       = "xml.catalog.allowPI";
  private static String pClassname     = "xml.catalog.className";
  private static String pIgnoreMissing = "xml.catalog.ignoreMissing";

  /** A static CatalogManager instance for sharing */
  private static CatalogManager staticManager = new CatalogManager();

  /** The bootstrap resolver to use when loading XML Catalogs. */
  private BootstrapResolver bResolver = new BootstrapResolver();

  /** Flag to ignore missing property files and/or properties */
  private boolean ignoreMissingProperties
    = (SecuritySupport.getSystemProperty(pIgnoreMissing) != null
       || SecuritySupport.getSystemProperty(pFiles) != null);

  /** Holds the resources after they are loaded from the file. */
  private ResourceBundle resources;

  /** The name of the CatalogManager properties file. */
  private String propertyFile = "CatalogManager.properties";

  /** The location of the propertyFile */
  private URL propertyFileURI = null;

  /** Default catalog files list. */
  private String defaultCatalogFiles = "./xcatalog";

  /** Current catalog files list. */
  private String catalogFiles = null;

  /** Did the catalogFiles come from the properties file? */
  private boolean fromPropertiesFile = false;

  /** Default verbosity level if there is no property setting for it. */
  private int defaultVerbosity = 1;

  /** Current verbosity level. */
  private Integer verbosity = null;

  /** Default preference setting. */
  private boolean defaultPreferPublic = true;

  /** Current preference setting. */
  private Boolean preferPublic = null;

  /** Default setting of the static catalog flag. */
  private boolean defaultUseStaticCatalog = true;

  /** Current setting of the static catalog flag. */
  private Boolean useStaticCatalog = null;

  /** The static catalog used by this manager. */
  private static Catalog staticCatalog = null;

  /** Default setting of the oasisXMLCatalogPI flag. */
  private boolean defaultOasisXMLCatalogPI = true;

  /** Current setting of the oasisXMLCatalogPI flag. */
  private Boolean oasisXMLCatalogPI = null;

  /** Default setting of the relativeCatalogs flag. */
  private boolean defaultRelativeCatalogs = true;

  /** Current setting of the relativeCatalogs flag. */
  private Boolean relativeCatalogs = null;

  /** Current catalog class name. */
  private String catalogClassName = null;
    /**
     * Indicates whether implementation parts should use
     *   service loader (or similar).
     * Note the default value (false) is the safe option..
     */
    private boolean useServicesMechanism;

  /** The manager's debug object. Used for printing debugging messages.
   *
   * <p>This field is public so that objects that have access to this
   * CatalogManager can use this debug object.</p>
   */
  public Debug debug = null;

  /** Constructor. */
  public CatalogManager() {
    init();
  }

  /** Constructor that specifies an explicit property file. */
  public CatalogManager(String propertyFile) {
    this.propertyFile = propertyFile;
    init();
  }

  private void init() {
    debug = new Debug();
    // Note that we don't setDebug() here; we do that lazily. Either the
    // user will set it explicitly, or we'll do it automagically if they
    // read from the propertyFile for some other reason. That way, there's
    // no attempt to read from the file before the caller has had a chance
    // to avoid it.
    if (System.getSecurityManager() == null) {
        useServicesMechanism = true;
    }
  }
  /** Set the bootstrap resolver.*/
  public void setBootstrapResolver(BootstrapResolver resolver) {
    bResolver = resolver;
  }

  /** Get the bootstrap resolver.*/
  public BootstrapResolver getBootstrapResolver() {
    return bResolver;
  }

  /**
   * Load the properties from the propertyFile and build the
   * resources from it.
   */
  private synchronized void readProperties() {
    try {
      propertyFileURI = CatalogManager.class.getResource("/"+propertyFile);
      InputStream in =
        CatalogManager.class.getResourceAsStream("/"+propertyFile);
      if (in==null) {
        if (!ignoreMissingProperties) {
          System.err.println("Cannot find "+propertyFile);
          // there's no reason to give this warning more than once
          ignoreMissingProperties = true;
        }
        return;
      }
      resources = new PropertyResourceBundle(in);
    } catch (MissingResourceException mre) {
      if (!ignoreMissingProperties) {
        System.err.println("Cannot read "+propertyFile);
      }
    } catch (java.io.IOException e) {
      if (!ignoreMissingProperties) {
        System.err.println("Failure trying to read "+propertyFile);
      }
    }

    // This is a bit of a hack. After we've successfully read the properties,
    // use them to set the default debug level, if the user hasn't already set
    // the default debug level.
    if (verbosity == null) {
      try {
        String verbStr = resources.getString("verbosity");
        int verb = Integer.parseInt(verbStr.trim());
        debug.setDebug(verb);
        verbosity = new Integer(verb);
      } catch (Exception e) {
        // nop
      }
    }
  }

  /**
   * Allow access to the static CatalogManager
   */
  public static CatalogManager getStaticManager() {
    return staticManager;
  }

  /**
   * How are missing properties handled?
   *
   * <p>If true, missing or unreadable property files will
   * not be reported. Otherwise, a message will be sent to System.err.
   * </p>
   */
  public boolean getIgnoreMissingProperties() {
    return ignoreMissingProperties;
  }

  /**
   * How should missing properties be handled?
   *
   * <p>If ignore is true, missing or unreadable property files will
   * not be reported. Otherwise, a message will be sent to System.err.
   * </p>
   */
  public void setIgnoreMissingProperties(boolean ignore) {
    ignoreMissingProperties = ignore;
  }

  /**
   * How are missing properties handled?
   *
   * <p>If ignore is true, missing or unreadable property files will
   * not be reported. Otherwise, a message will be sent to System.err.
   * </p>
   *
   * @deprecated No longer static; use get/set methods.
   */
  public void ignoreMissingProperties(boolean ignore) {
    setIgnoreMissingProperties(ignore);
  }

  /**
   * Obtain the verbosity setting from the properties.
   *
   * @return The verbosity level from the propertyFile or the
   * defaultVerbosity.
   */
  private int queryVerbosity () {
    String defaultVerbStr = Integer.toString(defaultVerbosity);

    String verbStr = SecuritySupport.getSystemProperty(pVerbosity);

    if (verbStr == null) {
      if (resources==null) readProperties();
      if (resources != null) {
        try {
          verbStr = resources.getString("verbosity");
        } catch (MissingResourceException e) {
          verbStr = defaultVerbStr;
        }
      } else {
        verbStr = defaultVerbStr;
      }
    }

    int verb = defaultVerbosity;

    try {
      verb = Integer.parseInt(verbStr.trim());
    } catch (Exception e) {
      System.err.println("Cannot parse verbosity: \"" + verbStr + "\"");
    }

    // This is a bit of a hack. After we've successfully got the verbosity,
    // we have to use it to set the default debug level,
    // if the user hasn't already set the default debug level.
    if (verbosity == null) {
      debug.setDebug(verb);
      verbosity = new Integer(verb);
    }

    return verb;
  }

  /**
   * What is the current verbosity?
   */
  public int getVerbosity() {
    if (verbosity == null) {
      verbosity = new Integer(queryVerbosity());
    }

    return verbosity.intValue();
  }

  /**
   * Set the current verbosity.
   */
  public void setVerbosity (int verbosity) {
    this.verbosity = new Integer(verbosity);
    debug.setDebug(verbosity);
  }

  /**
   * What is the current verbosity?
   *
   * @deprecated No longer static; use get/set methods.
   */
  public int verbosity () {
    return getVerbosity();
  }

  /**
   * Obtain the relativeCatalogs setting from the properties.
   *
   * @return The relativeCatalogs setting from the propertyFile or the
   * defaultRelativeCatalogs.
   */
  private boolean queryRelativeCatalogs () {
    if (resources==null) readProperties();

    if (resources==null) return defaultRelativeCatalogs;

    try {
      String allow = resources.getString("relative-catalogs");
      return (allow.equalsIgnoreCase("true")
              || allow.equalsIgnoreCase("yes")
              || allow.equalsIgnoreCase("1"));
    } catch (MissingResourceException e) {
      return defaultRelativeCatalogs;
    }
  }

  /**
   * Get the relativeCatalogs setting.
   *
   * <p>This property is used when the catalogFiles property is
   * interrogated. If true, then relative catalog entry file names
   * are returned. If false, relative catalog entry file names are
   * made absolute with respect to the properties file before returning
   * them.</p>
   *
   * <p>This property <emph>only applies</emph> when the catalog files
   * come from a properties file. If they come from a system property or
   * the default list, they are never considered relative. (What would
   * they be relative to?)</p>
   *
   * <p>In the properties, a value of 'yes', 'true', or '1' is considered
   * true, anything else is false.</p>
   *
   * @return The relativeCatalogs setting from the propertyFile or the
   * defaultRelativeCatalogs.
   */
  public boolean getRelativeCatalogs () {
    if (relativeCatalogs == null) {
      relativeCatalogs = new Boolean(queryRelativeCatalogs());
    }

    return relativeCatalogs.booleanValue();
  }

  /**
   * Set the relativeCatalogs setting.
   *
   * @see #getRelativeCatalogs()
   */
  public void setRelativeCatalogs (boolean relative) {
    relativeCatalogs = new Boolean(relative);
  }

  /**
   * Get the relativeCatalogs setting.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public boolean relativeCatalogs () {
    return getRelativeCatalogs();
  }

  /**
   * Obtain the list of catalog files from the properties.
   *
   * @return A semicolon delimited list of catlog file URIs
   */
  private String queryCatalogFiles () {
    String catalogList = SecuritySupport.getSystemProperty(pFiles);
    fromPropertiesFile = false;

    if (catalogList == null) {
      if (resources == null) readProperties();
      if (resources != null) {
        try {
          catalogList = resources.getString("catalogs");
          fromPropertiesFile = true;
        } catch (MissingResourceException e) {
          System.err.println(propertyFile + ": catalogs not found.");
          catalogList = null;
        }
      }
    }

    if (catalogList == null) {
      catalogList = defaultCatalogFiles;
    }

    return catalogList;
  }

  /**
   * Return the current list of catalog files.
   *
   * @return A vector of the catalog file names or null if no catalogs
   * are available in the properties.
   */
  public Vector getCatalogFiles() {
    if (catalogFiles == null) {
      catalogFiles = queryCatalogFiles();
    }

    StringTokenizer files = new StringTokenizer(catalogFiles, ";");
    Vector catalogs = new Vector();
    while (files.hasMoreTokens()) {
      String catalogFile = files.nextToken();
      URL absURI = null;

      if (fromPropertiesFile && !relativeCatalogs()) {
        try {
          absURI = new URL(propertyFileURI, catalogFile);
          catalogFile = absURI.toString();
        } catch (MalformedURLException mue) {
          absURI = null;
        }
      }

      catalogs.add(catalogFile);
    }

    return catalogs;
  }

  /**
   * Set the list of catalog files.
   */
  public void setCatalogFiles(String fileList) {
    catalogFiles = fileList;
    fromPropertiesFile = false;
  }

  /**
   * Return the current list of catalog files.
   *
   * @return A vector of the catalog file names or null if no catalogs
   * are available in the properties.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public Vector catalogFiles() {
    return getCatalogFiles();
  }

  /**
   * Obtain the preferPublic setting from the properties.
   *
   * <p>In the properties, a value of 'public' is true,
   * anything else is false.</p>
   *
   * @return True if prefer is public or the
   * defaultPreferSetting.
   */
  private boolean queryPreferPublic () {
    String prefer = SecuritySupport.getSystemProperty(pPrefer);

    if (prefer == null) {
      if (resources==null) readProperties();
      if (resources==null) return defaultPreferPublic;
      try {
        prefer = resources.getString("prefer");
      } catch (MissingResourceException e) {
        return defaultPreferPublic;
      }
    }

    if (prefer == null) {
      return defaultPreferPublic;
    }

    return (prefer.equalsIgnoreCase("public"));
  }

  /**
   * Return the current prefer public setting.
   *
   * @return True if public identifiers are preferred.
   */
  public boolean getPreferPublic () {
    if (preferPublic == null) {
      preferPublic = new Boolean(queryPreferPublic());
    }
    return preferPublic.booleanValue();
  }

  /**
   * Set the prefer public setting.
   */
  public void setPreferPublic (boolean preferPublic) {
    this.preferPublic = new Boolean(preferPublic);
  }

  /**
   * Return the current prefer public setting.
   *
   * @return True if public identifiers are preferred.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public boolean preferPublic () {
    return getPreferPublic();
  }

  /**
   * Obtain the static-catalog setting from the properties.
   *
   * <p>In the properties, a value of 'yes', 'true', or '1' is considered
   * true, anything else is false.</p>
   *
   * @return The static-catalog setting from the propertyFile or the
   * defaultUseStaticCatalog.
   */
  private boolean queryUseStaticCatalog () {
    String staticCatalog = SecuritySupport.getSystemProperty(pStatic);

    if (staticCatalog == null) {
      if (resources==null) readProperties();
      if (resources==null) return defaultUseStaticCatalog;
      try {
        staticCatalog = resources.getString("static-catalog");
      } catch (MissingResourceException e) {
        return defaultUseStaticCatalog;
      }
    }

    if (staticCatalog == null) {
      return defaultUseStaticCatalog;
    }

    return (staticCatalog.equalsIgnoreCase("true")
            || staticCatalog.equalsIgnoreCase("yes")
            || staticCatalog.equalsIgnoreCase("1"));
  }

  /**
   * Get the current use static catalog setting.
   */
  public boolean getUseStaticCatalog() {
    if (useStaticCatalog == null) {
      useStaticCatalog = new Boolean(queryUseStaticCatalog());
    }

    return useStaticCatalog.booleanValue();
  }

  /**
   * Set the use static catalog setting.
   */
  public void setUseStaticCatalog(boolean useStatic) {
    useStaticCatalog = new Boolean(useStatic);
  }

  /**
   * Get the current use static catalog setting.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public boolean staticCatalog() {
    return getUseStaticCatalog();
  }

  /**
   * Get a new catalog instance.
   *
   * This method always returns a new instance of the underlying catalog class.
   */
  public Catalog getPrivateCatalog() {
    Catalog catalog = staticCatalog;

    if (useStaticCatalog == null) {
      useStaticCatalog = new Boolean(getUseStaticCatalog());
    }

    if (catalog == null || !useStaticCatalog.booleanValue()) {

      try {
        String catalogClassName = getCatalogClassName();

        if (catalogClassName == null) {
          catalog = new Catalog();
        } else {
          try {
            catalog = (Catalog) ReflectUtil.forName(catalogClassName).newInstance();
          } catch (ClassNotFoundException cnfe) {
            debug.message(1,"Catalog class named '"
                          + catalogClassName
                          + "' could not be found. Using default.");
            catalog = new Catalog();
          } catch (ClassCastException cnfe) {
            debug.message(1,"Class named '"
                          + catalogClassName
                          + "' is not a Catalog. Using default.");
            catalog = new Catalog();
          }
        }

        catalog.setCatalogManager(this);
        catalog.setupReaders();
        catalog.loadSystemCatalogs();
      } catch (Exception ex) {
        ex.printStackTrace();
      }

      if (useStaticCatalog.booleanValue()) {
        staticCatalog = catalog;
      }
    }

    return catalog;
  }

  /**
   * Get a catalog instance.
   *
   * If this manager uses static catalogs, the same static catalog will
   * always be returned. Otherwise a new catalog will be returned.
   */
  public Catalog getCatalog() {
    Catalog catalog = staticCatalog;

    if (useStaticCatalog == null) {
      useStaticCatalog = new Boolean(getUseStaticCatalog());
    }

    if (catalog == null || !useStaticCatalog.booleanValue()) {
      catalog = getPrivateCatalog();
      if (useStaticCatalog.booleanValue()) {
        staticCatalog = catalog;
      }
    }

    return catalog;
  }

  /**
   * <p>Obtain the oasisXMLCatalogPI setting from the properties.</p>
   *
   * <p>In the properties, a value of 'yes', 'true', or '1' is considered
   * true, anything else is false.</p>
   *
   * @return The oasisXMLCatalogPI setting from the propertyFile or the
   * defaultOasisXMLCatalogPI.
   */
  public boolean queryAllowOasisXMLCatalogPI () {
    String allow = SecuritySupport.getSystemProperty(pAllowPI);

    if (allow == null) {
      if (resources==null) readProperties();
      if (resources==null) return defaultOasisXMLCatalogPI;
      try {
        allow = resources.getString("allow-oasis-xml-catalog-pi");
      } catch (MissingResourceException e) {
        return defaultOasisXMLCatalogPI;
      }
    }

    if (allow == null) {
      return defaultOasisXMLCatalogPI;
    }

    return (allow.equalsIgnoreCase("true")
            || allow.equalsIgnoreCase("yes")
            || allow.equalsIgnoreCase("1"));
  }

  /**
   * Get the current XML Catalog PI setting.
   */
  public boolean getAllowOasisXMLCatalogPI () {
    if (oasisXMLCatalogPI == null) {
      oasisXMLCatalogPI = new Boolean(queryAllowOasisXMLCatalogPI());
    }

    return oasisXMLCatalogPI.booleanValue();
  }

  public boolean useServicesMechanism() {
      return useServicesMechanism;
  }
  /**
   * Set the XML Catalog PI setting
   */
  public void setAllowOasisXMLCatalogPI(boolean allowPI) {
    oasisXMLCatalogPI = new Boolean(allowPI);
  }

  /**
   * Get the current XML Catalog PI setting.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public boolean allowOasisXMLCatalogPI() {
    return getAllowOasisXMLCatalogPI();
  }

  /**
   * Obtain the Catalog class name setting from the properties.
   *
   */
  public String queryCatalogClassName () {
    String className = SecuritySupport.getSystemProperty(pClassname);

    if (className == null) {
      if (resources==null) readProperties();
      if (resources==null) return null;
      try {
        return resources.getString("catalog-class-name");
      } catch (MissingResourceException e) {
        return null;
      }
    }

    return className;
  }

  /**
   * Get the current Catalog class name.
   */
  public String getCatalogClassName() {
    if (catalogClassName == null) {
      catalogClassName = queryCatalogClassName();
    }

    return catalogClassName;
  }

  /**
   * Set the Catalog class name.
   */
  public void setCatalogClassName(String className) {
    catalogClassName = className;
  }

  /**
   * Get the current Catalog class name.
   *
   * @deprecated No longer static; use get/set methods.
   */
  public String catalogClassName() {
    return getCatalogClassName();
  }
}
