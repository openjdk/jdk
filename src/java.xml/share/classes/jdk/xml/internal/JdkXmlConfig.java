/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.xml.internal;

import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Global configuration for JAXP components. A single instance of this class
 * holds the properties from the JAXP Configuration File, the JDK built-in
 * Catalog, a base XMLSecurityManager and XMLSecurityPropertyManager.
 */
public class JdkXmlConfig {
    public static final String JDKCATALOG_FILE = "/jdk/xml/internal/jdkcatalog/JDKCatalog.xml";
    private static final String JDKCATALOG_URL = "jrt:/java.xml/jdk/xml/internal/jdkcatalog/JDKCatalog.xml";

    // The JDK Configuration instance
    private static volatile JdkXmlConfig INSTANCE;
    // Represents properties set in JAXP Configuration File
    private final Properties jaxpConfig = new Properties();
    // The security manager initialized when the JdkXmlConfig instance is created
    private final XMLSecurityManager baseManager;
    private final XMLSecurityPropertyManager basePropertyMgr;
    private final JdkXmlFeatures baseFeatures;

    // The JDK built-in Catalog
    private static class CatalogHolder {
        private static final Catalog JDKCATALOG = CatalogManager.catalog(
                CatalogFeatures.defaults(), URI.create(JDKCATALOG_URL));
    }

    /**
     * Constructs an instance of this class.
     * @param stax a flag indicating whether the call is from StAX
     */
    private JdkXmlConfig(boolean stax) {
        loadConfig(stax);
        baseManager = new XMLSecurityManager(true);
        basePropertyMgr = new XMLSecurityPropertyManager();
        baseFeatures = new JdkXmlFeatures(true);
    }

    /**
     * Returns the singleton instance of this class.
     * @param stax a flag indicating whether the call is from StAX
     * @return the singleton instance of this class
     */
    public static JdkXmlConfig getInstance(boolean stax) {
        if (INSTANCE == null) {
            synchronized (JdkXmlConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = new JdkXmlConfig(stax);
                }
            }
        }
        return INSTANCE;
    }

    public Properties getJaxpConfig() {
        return jaxpConfig;
    }

    /**
     * Returns a copy of the base XMLSecurityManager.
     * @param update the flag indicating whether the copy should be updated
     * @return a copy of the base XMLSecurityManager.
     */
    public XMLSecurityManager getXMLSecurityManager(boolean update) {
        return update ? baseManager.cloneAndUpdate() : baseManager.clone();
    }

    /**
     * Returns a copy of the base XMLSecurityPropertyManager.
     * @param update the flag indicating whether the copy should be updated
     * @return a copy of the base XMLSecurityPropertyManager.
     */
    public XMLSecurityPropertyManager getXMLSecurityPropertyManager(boolean update) {
        return update ? basePropertyMgr.cloneAndUpdate() : basePropertyMgr.clone();
    }

    /**
     * Returns a copy of the base XMLSecurityPropertyManager.
     * @param update the flag indicating whether the copy should be updated
     * @return a copy of the base XMLSecurityPropertyManager.
     */
    public JdkXmlFeatures getXMLFeatures(boolean update) {
        return update ? baseFeatures.cloneAndUpdate() : baseFeatures.clone();
    }

    /**
     * Returns the JDK built-in Catalog.
     * @return the JDK built-in Catalog
     */
    public Catalog getJdkCatalog() {
        return CatalogHolder.JDKCATALOG;
    }

    /**
     * Loads the JAXP Configuration file.
     * The method reads the JDK default configuration that is typically located
     * at $java.home/conf/jaxp.properties. On top of the default, if the System
     * Property "java.xml.config.file" exists, the configuration file it points
     * to will also be read. Any settings in it will then override those in the
     * default.
     *
     * @param stax a flag indicating whether to read stax.properties
     * @return the value of the specified property, null if the property is not
     * found
     */
    private void loadConfig(boolean stax) {
        Properties properties = new Properties();
        // load the default configuration file
        boolean found = loadProperties(
                Paths.get(System.getProperty("java.home"),
                                "conf", "jaxp.properties")
                        .toAbsolutePath().normalize().toString());

        // attempts to find stax.properties only if jaxp.properties is not available
        if (stax && !found) {
            found = loadProperties(
                    Paths.get(System.getProperty("java.home"),
                                    "conf", "stax.properties")
                            .toAbsolutePath().normalize().toString()
            );
        }

        // load the custom configure on top of the default if any
        String configFile = System.getProperty(JdkConstants.CONFIG_FILE_PROPNAME);
        if (configFile != null) {
            loadProperties(configFile);
        }
    }

    /**
     * Loads the properties from the specified file into the cache.
     * @param file the specified file
     * @return true if success, false otherwise
     */
    private boolean loadProperties(String file) {
        File f = new File(file);
        if (SecuritySupport.doesFileExist(f)) {
            try (final InputStream in = SecuritySupport.getFileInputStream(f)) {
                jaxpConfig.load(in);
                return true;
            } catch (IOException e) {
                // shouldn't happen, but required by method getFileInputStream
            }
        }
        return false;
    }
}
