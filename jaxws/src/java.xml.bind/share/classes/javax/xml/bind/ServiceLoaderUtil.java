/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared ServiceLoader/FactoryFinder Utils shared among SAAJ, JAXB and JAXWS
 * - this class must be duplicated to all those projects, but it's
 * basically generic code and we want to have it everywhere same.
 *
 * @author Miroslav.Kos@oracle.com
 */
class ServiceLoaderUtil {

    private static final String OSGI_SERVICE_LOADER_CLASS_NAME = "com.sun.org.glassfish.hk2.osgiresourcelocator.ServiceLoader";
    private static final String OSGI_SERVICE_LOADER_METHOD_NAME = "lookupProviderClasses";

    static <P> P firstByServiceLoader(Class<P> spiClass, Logger logger) {
        // service discovery
        ServiceLoader<P> serviceLoader = ServiceLoader.load(spiClass);
        for (P impl : serviceLoader) {
            logger.fine("ServiceProvider loading Facility used; returning object [" + impl.getClass().getName() + "]");
            return impl;
        }
        return null;
    }

    static boolean isOsgi(Logger logger) {
        try {
            Class.forName(OSGI_SERVICE_LOADER_CLASS_NAME);
            return true;
        } catch (ClassNotFoundException ignored) {
            logger.log(Level.FINE, "OSGi classes not found, OSGi not available.", ignored);
        }
        return false;
    }

    static Object lookupUsingOSGiServiceLoader(String factoryId, Logger logger) {
        try {
            // Use reflection to avoid having any dependendcy on ServiceLoader class
            Class serviceClass = Class.forName(factoryId);
            Class target = Class.forName(OSGI_SERVICE_LOADER_CLASS_NAME);
            Method m = target.getMethod(OSGI_SERVICE_LOADER_METHOD_NAME, Class.class);
            Iterator iter = ((Iterable) m.invoke(null, serviceClass)).iterator();
            if (iter.hasNext()) {
                Object next = iter.next();
                logger.fine("Found implementation using OSGi facility; returning object [" + next.getClass().getName() + "].");
                return next;
            } else {
                return null;
            }
        } catch (Exception ignored) {
            logger.log(Level.FINE, "Unable to find from OSGi: [" + factoryId + "]", ignored);
            return null;
        }
    }

    static String propertyFileLookup(final String configFullPath, final String factoryId) throws IOException {
        File f = new File(configFullPath);
        String factoryClassName = null;
        if (f.exists()) {
            Properties props = new Properties();
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(f);
                props.load(stream);
                factoryClassName = props.getProperty(factoryId);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return factoryClassName;
    }

    static void checkPackageAccess(String className) {
        // make sure that the current thread has an access to the package of the given name.
        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            int i = className.lastIndexOf('.');
            if (i != -1) {
                s.checkPackageAccess(className.substring(0, i));
            }
        }
    }

    static Class nullSafeLoadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (classLoader == null) {
            return Class.forName(className);
        } else {
            return classLoader.loadClass(className);
        }
    }

    /**
     * Returns instance of required class. It checks package access (security) unless it is defaultClassname. It means if you
     * are trying to instantiate default implementation (fallback), pass the class name to both first and second parameter.
     *
     * @param className          class to be instantiated
     * @param isDefaultClassname says whether default implementation class
     * @param handler            exception handler - necessary for wrapping exceptions and logging
     * @param <T>                Type of exception being thrown (necessary to distinguish between Runtime and checked exceptions)
     * @return instantiated object or throws Runtime/checked exception, depending on ExceptionHandler's type
     * @throws T
     */
    static <T extends Exception> Object newInstance(String className, String defaultImplClassName, final ExceptionHandler<T> handler) throws T {
        try {
            return safeLoadClass(className, defaultImplClassName, contextClassLoader(handler)).newInstance();
        } catch (ClassNotFoundException x) {
            throw handler.createException(x, "Provider " + className + " not found");
        } catch (Exception x) {
            throw handler.createException(x, "Provider " + className + " could not be instantiated: " + x);
        }
    }

    static Class safeLoadClass(String className, String defaultImplClassName, ClassLoader classLoader) throws ClassNotFoundException {
        try {
            checkPackageAccess(className);
        } catch (SecurityException se) {
            // anyone can access the platform default factory class without permission
            if (defaultImplClassName != null && defaultImplClassName.equals(className)) {
                return Class.forName(className);
            }
            // not platform default implementation ...
            throw se;
        }
        return nullSafeLoadClass(className, classLoader);
    }

    static String getJavaHomeLibConfigPath(String filename) {
        String javah = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty("java.home");
            }
        });
        return javah + File.separator + "lib" + File.separator + filename;
    }

    static ClassLoader contextClassLoader(ExceptionHandler exceptionHandler) throws Exception {
        try {
            return Thread.currentThread().getContextClassLoader();
        } catch (Exception x) {
            throw exceptionHandler.createException(x, x.toString());
        }
    }

    static abstract class ExceptionHandler<T extends Exception> {

        public abstract T createException(Throwable throwable, String message);

    }

}
