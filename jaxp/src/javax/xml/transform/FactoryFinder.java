/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.transform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * <p>Implements pluggable Datatypes.</p>
 *
 * <p>This class is duplicated for each JAXP subpackage so keep it in
 * sync.  It is package private for secure class loading.</p>
 *
 * @author Santiago.PericasGeertsen@sun.com
 * @author Huizhe.Wang@oracle.com
 */
class FactoryFinder {

    /**
     * Internal debug flag.
     */
    private static boolean debug = false;

    /**
     * Cache for properties in java.home/lib/jaxp.properties
     */
    static Properties cacheProps = new Properties();

    /**
     * Flag indicating if properties from java.home/lib/jaxp.properties
     * have been cached.
     */
    static volatile boolean firstTime = true;

    /**
     * Security support class use to check access control before
     * getting certain system resources.
     */
    static SecuritySupport ss = new SecuritySupport();

    // Define system property "jaxp.debug" to get output
    static {
        // Use try/catch block to support applets, which throws
        // SecurityException out of this code.
        try {
            String val = ss.getSystemProperty("jaxp.debug");
            // Allow simply setting the prop to turn on debug
            debug = val != null && !"false".equals(val);
        }
        catch (SecurityException se) {
            debug = false;
        }
    }

    private static void dPrint(String msg) {
        if (debug) {
            System.err.println("JAXP: " + msg);
        }
    }

    /**
     * Attempt to load a class using the class loader supplied. If that fails
     * and fall back is enabled, the current (i.e. bootstrap) class loader is
     * tried.
     *
     * If the class loader supplied is <code>null</code>, first try using the
     * context class loader followed by the current (i.e. bootstrap) class
     * loader.
     *
     * Use bootstrap classLoader if cl = null and useBSClsLoader is true
     */
    static private Class getProviderClass(String className, ClassLoader cl,
            boolean doFallback, boolean useBSClsLoader) throws ClassNotFoundException
    {
        try {
            if (cl == null) {
                if (useBSClsLoader) {
                    return Class.forName(className, true, FactoryFinder.class.getClassLoader());
                } else {
                    cl = ss.getContextClassLoader();
                    if (cl == null) {
                        throw new ClassNotFoundException();
                    }
                    else {
                        return cl.loadClass(className);
                    }
                }
            }
            else {
                return cl.loadClass(className);
            }
        }
        catch (ClassNotFoundException e1) {
            if (doFallback) {
                // Use current class loader - should always be bootstrap CL
                return Class.forName(className, true, FactoryFinder.class.getClassLoader());
            }
            else {
                throw e1;
            }
        }
    }

    /**
     * Create an instance of a class. Delegates to method
     * <code>getProviderClass()</code> in order to load the class.
     *
     * @param className Name of the concrete class corresponding to the
     * service provider
     *
     * @param cl <code>ClassLoader</code> used to load the factory class. If <code>null</code>
     * current <code>Thread</code>'s context classLoader is used to load the factory class.
     *
     * @param doFallback True if the current ClassLoader should be tried as
     * a fallback if the class is not found using cl
     */
    static Object newInstance(String className, ClassLoader cl, boolean doFallback)
        throws ConfigurationError
    {
        return newInstance(className, cl, doFallback, false, false);
    }

    /**
     * Create an instance of a class. Delegates to method
     * <code>getProviderClass()</code> in order to load the class.
     *
     * @param className Name of the concrete class corresponding to the
     * service provider
     *
     * @param cl <code>ClassLoader</code> used to load the factory class. If <code>null</code>
     * current <code>Thread</code>'s context classLoader is used to load the factory class.
     *
     * @param doFallback True if the current ClassLoader should be tried as
     * a fallback if the class is not found using cl
     *
     * @param useBSClsLoader True if cl=null actually meant bootstrap classLoader. This parameter
     * is needed since DocumentBuilderFactory/SAXParserFactory defined null as context classLoader.
     *
     * @param useServicesMechanism True use services mechanism
     */
    static Object newInstance(String className, ClassLoader cl, boolean doFallback, boolean useBSClsLoader, boolean useServicesMechanism)
        throws ConfigurationError
    {
        try {
            Class providerClass = getProviderClass(className, cl, doFallback, useBSClsLoader);
            Object instance = null;
            if (!useServicesMechanism) {
                instance = newInstanceNoServiceLoader(providerClass);
            }
            if (instance == null) {
                instance = providerClass.newInstance();
            }
            if (debug) {    // Extra check to avoid computing cl strings
                dPrint("created new instance of " + providerClass +
                       " using ClassLoader: " + cl);
            }
            return instance;
        }
        catch (ClassNotFoundException x) {
            throw new ConfigurationError(
                "Provider " + className + " not found", x);
        }
        catch (Exception x) {
            throw new ConfigurationError(
                "Provider " + className + " could not be instantiated: " + x,
                x);
        }
    }
    /**
     * Try to construct using newTransformerFactoryNoServiceLoader
     *   method if available.
     */
    private static Object newInstanceNoServiceLoader(
         Class<?> providerClass
    ) {
        // Retain maximum compatibility if no security manager.
        if (System.getSecurityManager() == null) {
            return null;
        }
        try {
            Method creationMethod =
                providerClass.getDeclaredMethod(
                    "newTransformerFactoryNoServiceLoader"
                );
                return creationMethod.invoke(null, null);
            } catch (NoSuchMethodException exc) {
                return null;
            } catch (Exception exc) {
                return null;
        }
    }
    /**
     * Finds the implementation Class object in the specified order.  Main
     * entry point.
     * @return Class object of factory, never null
     *
     * @param factoryId             Name of the factory to find, same as
     *                              a property name
     * @param fallbackClassName     Implementation class name, if nothing else
     *                              is found.  Use null to mean no fallback.
     *
     * Package private so this code can be shared.
     */
    static Object find(String factoryId, String fallbackClassName)
        throws ConfigurationError
    {
        dPrint("find factoryId =" + factoryId);
        // Use the system property first
        try {
            String systemProp = ss.getSystemProperty(factoryId);
            if (systemProp != null) {
                dPrint("found system property, value=" + systemProp);
                return newInstance(systemProp, null, true, false, true);
            }
        }
        catch (SecurityException se) {
            if (debug) se.printStackTrace();
        }

        // try to read from $java.home/lib/jaxp.properties
        try {
            String factoryClassName = null;
            if (firstTime) {
                synchronized (cacheProps) {
                    if (firstTime) {
                        String configFile = ss.getSystemProperty("java.home") + File.separator +
                            "lib" + File.separator + "jaxp.properties";
                        File f = new File(configFile);
                        firstTime = false;
                        if (ss.doesFileExist(f)) {
                            dPrint("Read properties file "+f);
                            cacheProps.load(ss.getFileInputStream(f));
                        }
                    }
                }
            }
            factoryClassName = cacheProps.getProperty(factoryId);

            if (factoryClassName != null) {
                dPrint("found in $java.home/jaxp.properties, value=" + factoryClassName);
                return newInstance(factoryClassName, null, true, false, true);
            }
        }
        catch (Exception ex) {
            if (debug) ex.printStackTrace();
        }

        // Try Jar Service Provider Mechanism
        Object provider = findJarServiceProvider(factoryId);
        if (provider != null) {
            return provider;
        }
        if (fallbackClassName == null) {
            throw new ConfigurationError(
                "Provider for " + factoryId + " cannot be found", null);
        }

        dPrint("loaded from fallback value: " + fallbackClassName);
        return newInstance(fallbackClassName, null, true, false, true);
    }

    /*
     * Try to find provider using Jar Service Provider Mechanism
     *
     * @return instance of provider class if found or null
     */
    private static Object findJarServiceProvider(String factoryId)
        throws ConfigurationError
    {
        String serviceId = "META-INF/services/" + factoryId;
        InputStream is = null;

        // First try the Context ClassLoader
        ClassLoader cl = ss.getContextClassLoader();
        boolean useBSClsLoader = false;
        if (cl != null) {
            is = ss.getResourceAsStream(cl, serviceId);

            // If no provider found then try the current ClassLoader
            if (is == null) {
                cl = FactoryFinder.class.getClassLoader();
                is = ss.getResourceAsStream(cl, serviceId);
                useBSClsLoader = true;
           }
        } else {
            // No Context ClassLoader, try the current ClassLoader
            cl = FactoryFinder.class.getClassLoader();
            is = ss.getResourceAsStream(cl, serviceId);
            useBSClsLoader = true;
        }

        if (is == null) {
            // No provider found
            return null;
        }

        if (debug) {    // Extra check to avoid computing cl strings
            dPrint("found jar resource=" + serviceId + " using ClassLoader: " + cl);
        }

        BufferedReader rd;
        try {
            rd = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        }
        catch (java.io.UnsupportedEncodingException e) {
            rd = new BufferedReader(new InputStreamReader(is));
        }

        String factoryClassName = null;
        try {
            // XXX Does not handle all possible input as specified by the
            // Jar Service Provider specification
            factoryClassName = rd.readLine();
            rd.close();
        } catch (IOException x) {
            // No provider found
            return null;
        }

        if (factoryClassName != null && !"".equals(factoryClassName)) {
            dPrint("found in resource, value=" + factoryClassName);

            // Note: here we do not want to fall back to the current
            // ClassLoader because we want to avoid the case where the
            // resource file was found using one ClassLoader and the
            // provider class was instantiated using a different one.
            return newInstance(factoryClassName, cl, false, useBSClsLoader, true);
        }

        // No provider found
        return null;
    }

    static class ConfigurationError extends Error {
        private Exception exception;

        /**
         * Construct a new instance with the specified detail string and
         * exception.
         */
        ConfigurationError(String msg, Exception x) {
            super(msg);
            this.exception = x;
        }

        Exception getException() {
            return exception;
        }
        /**
        * use the exception chaining mechanism of JDK1.4
        */
        @Override
        public Throwable getCause() {
            return exception;
        }
    }

}
