/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * This class contains utility methods for reading resources in the JAXP packages
 */
public class SecuritySupport {
    public final static String NEWLINE = System.lineSeparator();

    /**
     * Cache for properties in java.home/conf/jaxp.properties
     */
    static final Properties cacheProps = new Properties();

    /**
     * Flag indicating whether java.home/conf/jaxp.properties has been read
     */
    static volatile boolean firstTime = true;

    private SecuritySupport() {}

    public static String getErrorMessage(Locale locale, String bundle, String key,
            Object[] arguments) {
        ResourceBundle rb;
        if (locale != null) {
            rb = ResourceBundle.getBundle(bundle,locale);
        } else {
            rb = ResourceBundle.getBundle(bundle);
        }

        String msg = rb.getString(key);
        if (arguments != null) {
            msg = MessageFormat.format(msg, arguments);
        }
        return msg;
    }

    /**
     * Reads a system property with privilege
     *
     * @param propName the name of the property
     * @return the value of the property
     */
    @SuppressWarnings("removal")
    public static String getSystemProperty(final String propName) {
        return
        AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty(propName));
    }

    /**
     * Reads a system property with privilege
     *
     * @param propName the name of the property
     * @return the value of the property
     */
    public static String getSystemProperty(final String propName, String defValue) {
        String value = getSystemProperty(propName);
        if (value == null) {
            return defValue;
        }
        return value;
    }

    /**
     * Reads a system property with specified type.
     *
     * @param <T> the type of the property value
     * @param type the type of the property value
     * @param propName the name of the property
     * @param defValue the default value
     * @return the value of the property, or the default value if no system
     * property is found
     */
    public static <T> T getSystemProperty(Class<T> type, String propName, String defValue) {
        String value = getSystemProperty(propName);
        if (value == null) {
            value = defValue;
        }
        if (Integer.class == type) {
            return type.cast(Integer.parseInt(value));
        } else if (Boolean.class == type) {
            return type.cast(Boolean.parseBoolean(value));
        }
        return type.cast(value);
    }

    /**
     * Reads JAXP system property in this order: system property,
     * $java.home/conf/jaxp.properties if the system property is not specified
     *
     * @param <T> the type of the property value
     * @param type the type of the property value
     * @param propName the name of the property
     * @param defValue the default value
     * @return the value of the property, or the default value if no system
     * property is found
     */
    public static <T> T getJAXPSystemProperty(Class<T> type, String propName, String defValue) {
        String value = getJAXPSystemProperty(propName);
        if (value == null) {
            value = defValue;
        }
        if (Integer.class.isAssignableFrom(type)) {
            return type.cast(Integer.parseInt(value));
        } else if (Boolean.class.isAssignableFrom(type)) {
            return type.cast(Boolean.parseBoolean(value));
        }
        return type.cast(value);
    }

    /**
     * Reads JAXP system property in this order: system property,
     * $java.home/conf/jaxp.properties if the system property is not specified
     *
     * @param propName the name of the property
     * @return the value of the property
     */
    public static String getJAXPSystemProperty(String propName) {
        String value = getSystemProperty(propName);
        if (value == null) {
            value = readConfig(propName);
        }
        return value;
    }

    /**
     * Returns the value of the specified property from the Configuration file.
     * The method reads the System Property "java.xml.config.file" for a custom
     * configuration file, if doesn't exist, falls back to the JDK default that
     * is typically located at $java.home/conf/jaxp.properties.
     *
     * @param propName the specified property
     * @return the value of the specified property, null if the property is not
     * found
     */
    public static String readConfig(String propName) {
        return readConfig(propName, false);
    }

    /**
     * Returns the value of the specified property from the Configuration file.
     * The method reads the JDK default configuration that is typically located
     * at $java.home/conf/jaxp.properties. On top of the default, if the System
     * Property "java.xml.config.file" exists, the configuration file it points
     * to will also be read. Any settings in it will then override those in the
     * default.
     *
     * @param propName the specified property
     * @param stax a flag indicating whether to read stax.properties
     * @return the value of the specified property, null if the property is not
     * found
     */
    public static String readConfig(String propName, boolean stax) {
        // always load the default configuration file
        if (firstTime) {
            synchronized (cacheProps) {
                if (firstTime) {
                    boolean found = loadProperties(
                            Paths.get(SecuritySupport.getSystemProperty("java.home"),
                                "conf", "jaxp.properties")
                                .toAbsolutePath().normalize().toString());

                    // attempts to find stax.properties only if jaxp.properties is not available
                    if (stax && !found) {
                        found = loadProperties(
                            Paths.get(SecuritySupport.getSystemProperty("java.home"),
                                    "conf", "stax.properties")
                                    .toAbsolutePath().normalize().toString()
                        );
                    }

                    // load the custom configure on top of the default if any
                    String configFile = SecuritySupport.getSystemProperty(JdkConstants.CONFIG_FILE_PROPNAME);
                    if (configFile != null) {
                        loadProperties(configFile);
                    }

                    firstTime = false;
                }
            }
        }

        return cacheProps.getProperty(propName);
    }

    /**
     * Loads the properties from the specified file into the cache.
     * @param file the specified file
     * @return true if success, false otherwise
     */
    private static boolean loadProperties(String file) {
        File f = new File(file);
        if (SecuritySupport.doesFileExist(f)) {
            try (final InputStream in = SecuritySupport.getFileInputStream(f)) {
                cacheProps.load(in);
                return true;
            } catch (IOException e) {
                // shouldn't happen, but required by method getFileInputStream
            }
        }
        return false;
    }

    /**
     * Tests whether the file denoted by this abstract pathname is a directory.
     * @param f the file to be tested
     * @return true if it is a directory, false otherwise
     */
    @SuppressWarnings("removal")
    public static boolean isDirectory(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Boolean>) ()
                -> f.isDirectory()));
    }

    /**
     * Tests whether the file exists.
     *
     * @param f the file to be tested
     * @return true if the file exists, false otherwise
     */
    @SuppressWarnings("removal")
    public static boolean isFileExists(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Boolean>) ()
                -> f.exists()));
    }

    /**
     * Creates and returns a new FileInputStream from a file.
     * @param file the specified file
     * @return the FileInputStream
     * @throws FileNotFoundException if the file is not found
     */
    @SuppressWarnings("removal")
    public static FileInputStream getFileInputStream(final File file)
            throws FileNotFoundException {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<FileInputStream>) ()
                    -> new FileInputStream(file));
        } catch (PrivilegedActionException e) {
            throw (FileNotFoundException) e.getException();
        }
    }

    /**
     * Returns the resource as a stream.
     * @param name the resource name
     * @return the resource stream
     */
    @SuppressWarnings("removal")
    public static InputStream getResourceAsStream(final String name) {
        return AccessController.doPrivileged((PrivilegedAction<InputStream>) () ->
                SecuritySupport.class.getResourceAsStream("/"+name));
    }

    /**
     * Gets a resource bundle using the specified base name, the default locale, and the caller's class loader.
     * @param bundle the base name of the resource bundle, a fully qualified class name
     * @return a resource bundle for the given base name and the default locale
     */
    public static ResourceBundle getResourceBundle(String bundle) {
        return getResourceBundle(bundle, Locale.getDefault());
    }

    /**
     * Gets a resource bundle using the specified base name and locale, and the caller's class loader.
     * @param bundle the base name of the resource bundle, a fully qualified class name
     * @param locale the locale for which a resource bundle is desired
     * @return a resource bundle for the given base name and locale
     */
    @SuppressWarnings("removal")
    public static ResourceBundle getResourceBundle(final String bundle, final Locale locale) {
        return AccessController.doPrivileged((PrivilegedAction<ResourceBundle>) () -> {
            try {
                return ResourceBundle.getBundle(bundle, locale);
            } catch (MissingResourceException e) {
                try {
                    return ResourceBundle.getBundle(bundle, Locale.US);
                } catch (MissingResourceException e2) {
                    throw new MissingResourceException(
                            "Could not load any resource bundle by " + bundle, bundle, "");
                }
            }
        });
    }

    /**
     * Checks whether the file exists.
     * @param f the specified file
     * @return true if the file exists, false otherwise
     */
    @SuppressWarnings("removal")
    public static boolean doesFileExist(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> f.exists()));
    }

    /**
     * Checks the LastModified attribute of a file.
     * @param f the specified file
     * @return the LastModified attribute
     */
    @SuppressWarnings("removal")
    static long getLastModified(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Long>) () -> f.lastModified()));
    }

    /**
     * Strip off path from an URI
     *
     * @param uri an URI with full path
     * @return the file name only
     */
    public static String sanitizePath(String uri) {
        if (uri == null) {
            return "";
        }
        int i = uri.lastIndexOf("/");
        if (i > 0) {
            return uri.substring(i+1, uri.length());
        }
        return "";
    }

    /**
     * Check the protocol used in the systemId against allowed protocols
     *
     * @param systemId the Id of the URI
     * @param allowedProtocols a list of allowed protocols separated by comma
     * @param accessAny keyword to indicate allowing any protocol
     * @return the name of the protocol if rejected, null otherwise
     */
    public static String checkAccess(String systemId, String allowedProtocols,
            String accessAny) throws IOException {
        if (systemId == null || (allowedProtocols != null &&
                allowedProtocols.equalsIgnoreCase(accessAny))) {
            return null;
        }

        String protocol;
        if (!systemId.contains(":")) {
            protocol = "file";
        } else {
            @SuppressWarnings("deprecation")
            URL url = new URL(systemId);
            protocol = url.getProtocol();
            if (protocol.equalsIgnoreCase("jar")) {
                String path = url.getPath();
                protocol = path.substring(0, path.indexOf(":"));
            } else if (protocol.equalsIgnoreCase("jrt")) {
                // if the systemId is "jrt" then allow access if "file" allowed
                protocol = "file";
            }
        }

        if (isProtocolAllowed(protocol, allowedProtocols)) {
            //access allowed
            return null;
        } else {
            return protocol;
        }
    }

    /**
     * Check if the protocol is in the allowed list of protocols. The check
     * is case-insensitive while ignoring whitespaces.
     *
     * @param protocol a protocol
     * @param allowedProtocols a list of allowed protocols
     * @return true if the protocol is in the list
     */
    private static boolean isProtocolAllowed(String protocol, String allowedProtocols) {
         if (allowedProtocols == null) {
             return false;
         }
         String temp[] = allowedProtocols.split(",");
         for (String t : temp) {
             t = t.trim();
             if (t.equalsIgnoreCase(protocol)) {
                 return true;
             }
         }
         return false;
     }

    @SuppressWarnings("removal")
    public static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null)
                cl = ClassLoader.getSystemClassLoader();
            return cl;
        });
    }


    @SuppressWarnings("removal")
    public static ClassLoader getSystemClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader cl = null;
            try {
                cl = ClassLoader.getSystemClassLoader();
            } catch (SecurityException ex) {
            }
            return cl;
        });
    }

    @SuppressWarnings("removal")
    public static ClassLoader getParentClassLoader(final ClassLoader cl) {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader parent = null;
            try {
                parent = cl.getParent();
            } catch (SecurityException ex) {
            }

            // eliminate loops in case of the boot
            // ClassLoader returning itself as a parent
            return (parent == cl) ? null : parent;
        });
    }


    // Used for debugging purposes
    @SuppressWarnings("removal")
    public static String getClassSource(Class<?> cls) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
            CodeSource cs = cls.getProtectionDomain().getCodeSource();
            if (cs != null) {
                URL loc = cs.getLocation();
                return loc != null ? loc.toString() : "(no location)";
            } else {
                return "(no code source)";
            }
        });
    }

    // ----------------  For SAX ----------------------
    /**
     * Returns the current thread's context class loader, or the system class loader
     * if the context class loader is null.
     * @return the current thread's context class loader, or the system class loader
     * @throws SecurityException
     */
    @SuppressWarnings("removal")
    public static ClassLoader getClassLoader() throws SecurityException{
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>)() -> {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

            return cl;
        });
    }

    @SuppressWarnings("removal")
    public static InputStream getResourceAsStream(final ClassLoader cl, final String name)
    {
        return AccessController.doPrivileged((PrivilegedAction<InputStream>) () -> {
            InputStream ris;
            if (cl == null) {
                ris = SecuritySupport.class.getResourceAsStream(name);
            } else {
                ris = cl.getResourceAsStream(name);
            }
            return ris;
        });
    }
}
