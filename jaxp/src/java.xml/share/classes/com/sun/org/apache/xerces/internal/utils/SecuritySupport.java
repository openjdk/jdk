/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xerces.internal.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * This class is duplicated for each subpackage so keep it in sync.
 * It is package private and therefore is not exposed as part of any API.
 *
 * @xerces.internal
 */
public final class SecuritySupport {

    private static final SecuritySupport securitySupport = new SecuritySupport();

    /**
     * Return an instance of this class.
     * @return an instance of this class
     */
    public static SecuritySupport getInstance() {
        return securitySupport;
    }

    static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader cl = null;
            try {
                cl = Thread.currentThread().getContextClassLoader();
            } catch (SecurityException ex) { }
            return cl;
        });
    }

    static ClassLoader getSystemClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader cl = null;
            try {
                cl = ClassLoader.getSystemClassLoader();
            } catch (SecurityException ex) {}
            return cl;
        });
    }

    static ClassLoader getParentClassLoader(final ClassLoader cl) {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader parent = null;
            try {
                parent = cl.getParent();
            } catch (SecurityException ex) {}

            // eliminate loops in case of the boot
            // ClassLoader returning itself as a parent
            return (parent == cl) ? null : parent;
        });
    }

    public static String getSystemProperty(final String propName) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () ->
                System.getProperty(propName));
    }

    static FileInputStream getFileInputStream(final File file)
    throws FileNotFoundException
    {
        try {
            return AccessController.doPrivileged(
                    (PrivilegedExceptionAction<FileInputStream>)() ->
                    new FileInputStream(file));
        } catch (PrivilegedActionException e) {
            throw (FileNotFoundException)e.getException();
        }
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
        return AccessController.doPrivileged((PrivilegedAction<ResourceBundle>) () -> {
            try {
                return PropertyResourceBundle.getBundle(bundle, locale);
            } catch (MissingResourceException e) {
                try {
                    return PropertyResourceBundle.getBundle(bundle, new Locale("en", "US"));
                } catch (MissingResourceException e2) {
                    throw new MissingResourceException(
                            "Could not load any resource bundle by " + bundle, bundle, "");
                }
            }
        });
    }

    static boolean getFileExists(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
                f.exists()));
    }

    static long getLastModified(final File f) {
        return (AccessController.doPrivileged((PrivilegedAction<Long>) () ->
                f.lastModified()));
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
        return uri;
    }

    /**
     * Check the protocol used in the systemId against allowed protocols
     *
     * @param systemId the Id of the URI
     * @param allowedProtocols a list of allowed protocols separated by comma
     * @param accessAny keyword to indicate allowing any protocol
     * @return the name of the protocol if rejected, null otherwise
     * @throws java.io.IOException
     */
    public static String checkAccess(String systemId, String allowedProtocols, String accessAny) throws IOException {
        if (systemId == null || (allowedProtocols != null &&
                allowedProtocols.equalsIgnoreCase(accessAny))) {
            return null;
        }

        String protocol;
        if (!systemId.contains(":")) {
            protocol = "file";
        } else {
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

    /**
     * Read JAXP system property in this order: system property,
     * $java.home/conf/jaxp.properties if the system property is not specified
     *
     * @param sysPropertyId the Id of the property
     * @return the value of the property
     */
    public static String getJAXPSystemProperty(String sysPropertyId) {
        String value = getSystemProperty(sysPropertyId);
        if (value == null) {
            value = readJAXPProperty(sysPropertyId);
        }
        return value;
    }

     /**
     * Read from $java.home/conf/jaxp.properties for the specified property
     * The program
     *
     * @param propertyId the Id of the property
     * @return the value of the property
     */
    static String readJAXPProperty(String propertyId) {
        String value = null;
        InputStream is = null;
        try {
            if (firstTime) {
                synchronized (cacheProps) {
                    if (firstTime) {
                        String configFile = getSystemProperty("java.home") + File.separator +
                            "conf" + File.separator + "jaxp.properties";
                        File f = new File(configFile);
                        if (getFileExists(f)) {
                            is = getFileInputStream(f);
                            cacheProps.load(is);
                        }
                        firstTime = false;
                    }
                }
            }
            value = cacheProps.getProperty(propertyId);

        }
        catch (IOException ex) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {}
            }
        }

        return value;
    }

   /**
     * Cache for properties in java.home/conf/jaxp.properties
     */
    static final Properties cacheProps = new Properties();

    /**
     * Flag indicating if the program has tried reading java.home/conf/jaxp.properties
     */
    static volatile boolean firstTime = true;

    private SecuritySupport () {}
}
