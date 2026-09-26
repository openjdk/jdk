/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

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
        String value = System.getProperty(propName);
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
        return JdkXmlConfig.getInstance(stax).getJaxpConfig().getProperty(propName);
    }

    /**
     * Tests whether the file denoted by this abstract pathname is a directory.
     * @param f the file to be tested
     * @return true if it is a directory, false otherwise
     */
    public static boolean isDirectory(final File f) {
        return f.isDirectory();
    }

    /**
     * Tests whether the file exists.
     *
     * @param f the file to be tested
     * @return true if the file exists, false otherwise
     */
    public static boolean isFileExists(final File f) {
        return f.exists();
    }

    /**
     * Tests whether the input is file.
     *
     * @param f the file to be tested
     * @return true if the input is file, false otherwise
     */
    public static boolean isFile(final File f) {
        return f.isFile();
    }

    /**
     * Creates and returns a new FileInputStream from a file.
     * @param file the specified file
     * @return the FileInputStream
     * @throws FileNotFoundException if the file is not found
     */
    public static FileInputStream getFileInputStream(final File file)
            throws FileNotFoundException {
        return new FileInputStream(file);
    }

    /**
     * Returns an InputStream from a URLConnection.
     * @param uc the URLConnection
     * @return the InputStream
     * @throws IOException if an I/O error occurs while creating the input stream
     */
    public static InputStream getInputStream(final URLConnection uc)
            throws IOException {
        return uc.getInputStream();
    }

    /**
     * Returns the resource as a stream.
     * @param name the resource name
     * @return the resource stream
     */
    public static InputStream getResourceAsStream(final String name) {
        return SecuritySupport.class.getResourceAsStream("/"+name);
    }

    /**
     * Returns the resource by the name.
     * @param name the resource name
     * @return the resource
     */
    public static URL getResource(final String name) {
        return SecuritySupport.class.getResource(name);
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
    public static ResourceBundle getResourceBundle(final String bundle, final Locale locale) {
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
    }

    /**
     * Checks whether the file exists.
     * @param f the specified file
     * @return true if the file exists, false otherwise
     */
    public static boolean doesFileExist(final File f) {
        return f.exists();
    }

    /**
     * Strips off path from a URI or file path.
     *
     * @param input a URI or file path
     * @return the file name only
     */
    public static String sanitizePath(String input) {
        if (input == null) {
            return "";
        }
        input = input.replace('\\', '/');
        int i = input.lastIndexOf('/');
        if (i > 0) {
            return input.substring(i+1);
        }
        return input;
    }

    /**
     * Checks whether access to resource represented by the systemId is permitted
     * by Resource Access and/or External Access Properties (EAP)
     * @param systemId the systemId
     * @param xsm the XMLSecurityManager
     * @param eap the External Access Property, e.g. ACCESS_EXTERNAL_DTD
     * @param allowedProtocols protocols allowed by the EAP
     * @return true if access is permitted, false otherwise
     * @throws IOException if the systemId is invalid
     */
    public static String checkAccess(String systemId, XMLSecurityManager xsm,
        String eap, String allowedProtocols) {
        String errMsg = null;
        if (xsm != null && !xsm.isAccessAllowed(systemId)) {
            errMsg = "Resource Access (jdk.xml.resource.access)";
        }

        if (!checkAccess(systemId, allowedProtocols)) {
            errMsg = (errMsg != null) ? errMsg + " and " + eap : eap;
        }
        return errMsg;
    }

    /**
     * Check the protocol used in the systemId against allowed protocols
     *
     * @param systemId the Id of the URI
     * @param allowedProtocols a list of allowed protocols separated by comma
     * @return true if access is permitted, false otherwise
     */
    public static boolean checkAccess(String systemId, String allowedProtocols) {
        if (Utils.isEmpty(allowedProtocols)) {
            return false;
        }
        if (systemId == null || (allowedProtocols != null &&
                allowedProtocols.equalsIgnoreCase(JdkConstants.ACCESS_EXTERNAL_ALL))) {
            return true;
        }

        URI uri = Utils.createURI(systemId);
        String scheme = uri.getScheme();
        if (scheme == null) {
            scheme = "file";
        } else {
            scheme = scheme.toLowerCase(Locale.ROOT);
            if ("jar".equals(scheme)) {
                String ssp = uri.getSchemeSpecificPart(); // e.g. file:/x.jar!/a.xml
                int sep = ssp.indexOf("!/");
                if (sep != -1) {
                    URI nested = Utils.createURI(ssp.substring(0, sep));
                    String nestedScheme = nested.getScheme();
                    if (nestedScheme != null) {
                        scheme = nestedScheme.toLowerCase(Locale.ROOT);
                    }
                }
            } else if ("jrt".equals(scheme)) {
                // allow access if it's "file"
                scheme = "file";
            }
        }

        Set<String> allowed = parseProtocols(allowedProtocols);
        return allowed.contains(scheme);
    }

    /**
     * Parses allowed protocols.
     * @param protocols the protocol setting
     * @return a set containing allowed protocols
     */
    private static Set<String> parseProtocols(String protocols) {
        Set<String> set = new HashSet<>();
        if (protocols == null || protocols.isEmpty()) {
            return set;
        }

        for (String p : protocols.split(",")) {
            String trimmed = p.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    public static ClassLoader getContextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = ClassLoader.getSystemClassLoader();
        return cl;
    }

    public static ClassLoader getSystemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    public static ClassLoader getParentClassLoader(final ClassLoader cl) {
        ClassLoader parent = cl.getParent();

        // eliminate loops in case of the boot
        // ClassLoader returning itself as a parent
        return (parent == cl) ? null : parent;
    }


    // Used for debugging purposes
    public static String getClassSource(Class<?> cls) {
        CodeSource cs = cls.getProtectionDomain().getCodeSource();
        if (cs != null) {
            URL loc = cs.getLocation();
            return loc != null ? loc.toString() : "(no location)";
        } else {
            return "(no code source)";
        }
    }

    // ----------------  For SAX ----------------------
    /**
     * Returns the current thread's context class loader, or the system class loader
     * if the context class loader is null.
     * @return the current thread's context class loader, or the system class loader
     */
    public static ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        return cl;
    }

    public static InputStream getResourceAsStream(final ClassLoader cl, final String name)
    {
        InputStream ris;
        if (cl == null) {
            ris = SecuritySupport.class.getResourceAsStream(name);
        } else {
            ris = cl.getResourceAsStream(name);
        }
        return ris;
    }
}
