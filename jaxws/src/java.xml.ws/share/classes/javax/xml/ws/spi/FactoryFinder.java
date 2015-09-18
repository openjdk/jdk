/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.spi;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.ws.WebServiceException;

class FactoryFinder {

    private static final Logger logger = Logger.getLogger("javax.xml.ws");

    private static final ServiceLoaderUtil.ExceptionHandler<WebServiceException> EXCEPTION_HANDLER =
            new ServiceLoaderUtil.ExceptionHandler<WebServiceException>() {
                @Override
                public WebServiceException createException(Throwable throwable, String message) {
                    return new WebServiceException(message, throwable);
                }
            };

    /**
     * Finds the implementation {@code Class} object for the given
     * factory name, or if that fails, finds the {@code Class} object
     * for the given fallback class name. The arguments supplied MUST be
     * used in order. If using the first argument is successful, the second
     * one will not be used.
     * <P>
     * This method is package private so that this code can be shared.
     *
     * @return the {@code Class} object of the specified message factory;
     *         may not be {@code null}
     *
     * @param factoryClass          the name of the factory to find, which is
     *                              a system property
     * @param fallbackClassName     the implementation class name, which is
     *                              to be used only if nothing else
     *                              is found; {@code null} to indicate that
     *                              there is no fallback class name
     * @exception WebServiceException if there is an error
     */
    @SuppressWarnings("unchecked")
    static <T> T find(Class<T> factoryClass, String fallbackClassName) {
        ClassLoader classLoader = ServiceLoaderUtil.contextClassLoader(EXCEPTION_HANDLER);

        T provider = ServiceLoaderUtil.firstByServiceLoader(factoryClass, logger, EXCEPTION_HANDLER);
        if (provider != null) return provider;

        String factoryId = factoryClass.getName();

        // try to read from $java.home/lib/jaxws.properties
        provider = (T) fromJDKProperties(factoryId, fallbackClassName, classLoader);
        if (provider != null) return provider;

        // Use the system property
        provider = (T) fromSystemProperty(factoryId, fallbackClassName, classLoader);
        if (provider != null) return provider;

        // handling Glassfish (platform specific default)
        if (isOsgi()) {
            return (T) lookupUsingOSGiServiceLoader(factoryId);
        }

        if (fallbackClassName == null) {
            throw new WebServiceException(
                "Provider for " + factoryId + " cannot be found", null);
        }

        return (T) ServiceLoaderUtil.newInstance(fallbackClassName,
                fallbackClassName, classLoader, EXCEPTION_HANDLER);
    }

    private static Object fromSystemProperty(String factoryId,
                                             String fallbackClassName,
                                             ClassLoader classLoader) {
        try {
            String systemProp = System.getProperty(factoryId);
            if (systemProp != null) {
                return ServiceLoaderUtil.newInstance(systemProp,
                        fallbackClassName, classLoader, EXCEPTION_HANDLER);
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private static Object fromJDKProperties(String factoryId,
                                            String fallbackClassName,
                                            ClassLoader classLoader) {
        Path path = null;
        try {
            String JAVA_HOME = System.getProperty("java.home");
            path = Paths.get(JAVA_HOME, "conf", "jaxws.properties");

            // to ensure backwards compatibility
            if (!Files.exists(path)) {
                path = Paths.get(JAVA_HOME, "lib", "jaxws.properties");
            }

            if (!Files.exists(path)) {
                Properties props = new Properties();
                try (InputStream inStream = Files.newInputStream(path)) {
                    props.load(inStream);
                }
                String factoryClassName = props.getProperty(factoryId);
                return ServiceLoaderUtil.newInstance(factoryClassName,
                        fallbackClassName, classLoader, EXCEPTION_HANDLER);
            }
        } catch (Exception ignored) {
            logger.log(Level.SEVERE, "Error reading JAX-WS configuration from ["  + path +
                    "] file. Check it is accessible and has correct format.", ignored);
        }
        return null;
    }

    private static final String OSGI_SERVICE_LOADER_CLASS_NAME = "com.sun.org.glassfish.hk2.osgiresourcelocator.ServiceLoader";

    private static boolean isOsgi() {
        try {
            Class.forName(OSGI_SERVICE_LOADER_CLASS_NAME);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    private static Object lookupUsingOSGiServiceLoader(String factoryId) {
        try {
            // Use reflection to avoid having any dependendcy on ServiceLoader class
            Class serviceClass = Class.forName(factoryId);
            Class[] args = new Class[]{serviceClass};
            Class target = Class.forName(OSGI_SERVICE_LOADER_CLASS_NAME);
            java.lang.reflect.Method m = target.getMethod("lookupProviderInstances", Class.class);
            java.util.Iterator iter = ((Iterable) m.invoke(null, (Object[]) args)).iterator();
            return iter.hasNext() ? iter.next() : null;
        } catch (Exception ignored) {
            // log and continue
            return null;
        }
    }

}
