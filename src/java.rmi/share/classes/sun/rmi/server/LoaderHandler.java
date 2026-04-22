/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.server;

import java.lang.ref.SoftReference;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.server.LogStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import sun.rmi.runtime.Log;

/**
 * <code>LoaderHandler</code> provides the implementation of the static
 * methods of the <code>java.rmi.server.RMIClassLoader</code> class.
 *
 * @author      Ann Wollrath
 * @author      Peter Jones
 * @author      Laird Dornin
 */
@SuppressWarnings("deprecation")
public final class LoaderHandler {

    /** RMI class loader log level */
    static final int logLevel = LogStream.parseLevel(System.getProperty("sun.rmi.loader.logLevel"));

    /* loader system log */
    static final Log loaderLog =
        Log.getLog("sun.rmi.loader", "loader", LoaderHandler.logLevel);

    /**
     * value of "java.rmi.server.codebase" property, as cached at class
     * initialization time.  It may contain malformed URLs.
     */
    private static String codebaseProperty = null;
    static {
        String prop = System.getProperty("java.rmi.server.codebase");
        if (prop != null && prop.trim().length() > 0) {
            codebaseProperty = prop;
        }
    }

    /** list of URLs represented by the codebase property, if valid */
    private static URL[] codebaseURLs = null;

    /** table of class loaders that use codebase property for annotation */
    private static final Map<ClassLoader, Void> codebaseLoaders =
        Collections.synchronizedMap(new IdentityHashMap<ClassLoader, Void>(5));
    static {
        for (ClassLoader codebaseLoader = ClassLoader.getSystemClassLoader();
             codebaseLoader != null;
             codebaseLoader = codebaseLoader.getParent())
        {
            codebaseLoaders.put(codebaseLoader, null);
        }
    }

    /*
     * Disallow anyone from creating one of these.
     */
    private LoaderHandler() {}

    /**
     * Returns an array of URLs initialized with the value of the
     * java.rmi.server.codebase property as the URL path.
     */
    private static synchronized URL[] getDefaultCodebaseURLs()
        throws MalformedURLException
    {
        /*
         * If it hasn't already been done, convert the codebase property
         * into an array of URLs; this may throw a MalformedURLException.
         */
        if (codebaseURLs == null) {
            if (codebaseProperty != null) {
                codebaseURLs = pathToURLs(codebaseProperty);
            } else {
                codebaseURLs = new URL[0];
            }
        }
        return codebaseURLs;
    }

    /**
     * Load a class from a network location (one or more URLs),
     * but first try to resolve the named class through the given
     * "default loader".
     */
    public static Class<?> loadClass(String codebase, String name,
                                     ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        if (loaderLog.isLoggable(Log.BRIEF)) {
            loaderLog.log(Log.BRIEF,
                "name = \"" + name + "\", " +
                "codebase = \"" + (codebase != null ? codebase : "") + "\"" +
                (defaultLoader != null ?
                 ", defaultLoader = " + defaultLoader : ""));
        }

        URL[] urls;
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        if (defaultLoader != null) {
            try {
                Class<?> c = Class.forName(name, false, defaultLoader);
                if (loaderLog.isLoggable(Log.VERBOSE)) {
                    loaderLog.log(Log.VERBOSE,
                        "class \"" + name + "\" found via defaultLoader, " +
                        "defined by " + c.getClassLoader());
                }
                return c;
            } catch (ClassNotFoundException e) {
            }
        }

        return loadClass(urls, name);
    }

    /**
     * Returns the class annotation (representing the location for
     * a class) that RMI will use to annotate the call stream when
     * marshalling objects of the given class.
     */
    public static String getClassAnnotation(Class<?> cl) {
        String name = cl.getName();

        /*
         * Class objects for arrays of primitive types never need an
         * annotation, because they never need to be (or can be) downloaded.
         *
         * REMIND: should we (not) be annotating classes that are in
         * "java.*" packages?
         */
        int nameLength = name.length();
        if (nameLength > 0 && name.charAt(0) == '[') {
            // skip past all '[' characters (see bugid 4211906)
            int i = 1;
            while (nameLength > i && name.charAt(i) == '[') {
                i++;
            }
            if (nameLength > i && name.charAt(i) != 'L') {
                return null;
            }
        }

        /*
         * Get the class's class loader.  If it is null, the system class
         * loader, an ancestor of the base class loader (such as the loader
         * for installed extensions), return the value of the
         * "java.rmi.server.codebase" property.
         */
        ClassLoader loader = cl.getClassLoader();
        if (loader == null || codebaseLoaders.containsKey(loader)) {
            return codebaseProperty;
        }

        /*
         * Get the codebase URL path for the class loader, if it supports
         * such a notion (i.e., if it is a URLClassLoader or subclass).
         */
        String annotation = null;
        if (loader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) loader).getURLs();
            if (urls != null) {
                annotation = urlsToPath(urls);
            }
        }

        if (annotation != null) {
            return annotation;
        } else {
            return codebaseProperty;    // REMIND: does this make sense??
        }
    }

    /**
     * Returns the thread context classloader. The codebase argument is ignored.
     */
    public static ClassLoader getClassLoader(String codebase)
        throws MalformedURLException
    {
        URL[] urls; // ignored, used only for URL syntax checking
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Register a class loader as one whose classes should always be
     * annotated with the value of the "java.rmi.server.codebase" property.
     */
    public static void registerCodebaseLoader(ClassLoader loader) {
        codebaseLoaders.put(loader, null);
    }

    /**
     * Load a class from the RMI class loader corresponding to the given
     * codebase URL path in the current execution context.
     */
    private static Class<?> loadClass(URL[] urls, String name)
        throws ClassNotFoundException
    {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (loaderLog.isLoggable(Log.VERBOSE)) {
            loaderLog.log(Log.VERBOSE,
                "(thread context class loader: " + parent + ")");
        }

        /*
         * There is no security manager, so disable access to RMI class
         * loaders and simply delegate request to the parent loader
         * (see bugid 4140511).
         */
        try {
            Class<?> c = Class.forName(name, false, parent);
            if (loaderLog.isLoggable(Log.VERBOSE)) {
                loaderLog.log(Log.VERBOSE,
                    "class \"" + name + "\" found via " +
                    "thread context class loader " +
                    "(no security manager: codebase disabled), " +
                    "defined by " + c.getClassLoader());
            }
            return c;
        } catch (ClassNotFoundException e) {
            if (loaderLog.isLoggable(Log.BRIEF)) {
                loaderLog.log(Log.BRIEF,
                    "class \"" + name + "\" not found via " +
                    "thread context class loader " +
                    "(no security manager: codebase disabled)", e);
            }
            throw new ClassNotFoundException(e.getMessage() +
                " (no security manager: RMI class loader disabled)",
                e.getException());
        }
    }

    /**
     * Define and return a dynamic proxy class in a class loader with
     * URLs supplied in the given location.  The proxy class will
     * implement interface classes named by the given array of
     * interface names.
     */
    public static Class<?> loadProxyClass(String codebase, String[] interfaces,
                                          ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        if (loaderLog.isLoggable(Log.BRIEF)) {
            loaderLog.log(Log.BRIEF,
                "interfaces = " + Arrays.asList(interfaces) + ", " +
                "codebase = \"" + (codebase != null ? codebase : "") + "\"" +
                (defaultLoader != null ?
                 ", defaultLoader = " + defaultLoader : ""));
        }

        /*
         * This method uses a fairly complex algorithm to load the
         * proxy class and its interface classes in order to maximize
         * the likelihood that the proxy's codebase annotation will be
         * preserved.  The algorithm is (assuming that all of the
         * proxy interface classes are public):
         *
         * If the default loader is not null, try to load the proxy
         * interfaces through that loader. If the interfaces can be
         * loaded in that loader, try to define the proxy class in an
         * RMI class loader (child of the context class loader) before
         * trying to define the proxy in the default loader.  If the
         * attempt to define the proxy class succeeds, the codebase
         * annotation is preserved.  If the attempt fails, try to
         * define the proxy class in the default loader.
         *
         * If the interface classes can not be loaded from the default
         * loader or the default loader is null, try to load them from
         * the RMI class loader.  Then try to define the proxy class
         * in the RMI class loader.
         *
         * Additionally, if any of the proxy interface classes are not
         * public, all of the non-public interfaces must reside in the
         * same class loader or it will be impossible to define the
         * proxy class (an IllegalAccessError will be thrown).  An
         * attempt to load the interfaces from the default loader is
         * made.  If the attempt fails, a second attempt will be made
         * to load the interfaces from the RMI loader. If all of the
         * non-public interfaces classes do reside in the same class
         * loader, then we attempt to define the proxy class in the
         * class loader of the non-public interfaces.  No other
         * attempt to define the proxy class will be made.
         */
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (loaderLog.isLoggable(Log.VERBOSE)) {
            loaderLog.log(Log.VERBOSE,
                "(thread context class loader: " + parent + ")");
        }

        URL[] urls;
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        /*
         * There is no security manager, so disable access to RMI class
         * loaders and use the would-be parent instead.
         */
        try {
            Class<?> c = loadProxyClass(interfaces, defaultLoader, parent,
                                     false);
            if (loaderLog.isLoggable(Log.VERBOSE)) {
                loaderLog.log(Log.VERBOSE,
                    "(no security manager: codebase disabled) " +
                    "proxy class defined by " + c.getClassLoader());
            }
            return c;
        } catch (ClassNotFoundException e) {
            if (loaderLog.isLoggable(Log.BRIEF)) {
                loaderLog.log(Log.BRIEF,
                    "(no security manager: codebase disabled) " +
                    "proxy class resolution failed", e);
            }
            throw new ClassNotFoundException(e.getMessage() +
                " (no security manager: RMI class loader disabled)",
                e.getException());
        }
    }

    /**
     * Define a proxy class in the default loader if appropriate.
     * Define the class in an RMI class loader otherwise.  The proxy
     * class will implement classes which are named in the supplied
     * interfaceNames.
     */
    private static Class<?> loadProxyClass(String[] interfaceNames,
                                           ClassLoader defaultLoader,
                                           ClassLoader codebaseLoader,
                                           boolean preferCodebase)
        throws ClassNotFoundException
    {
        ClassLoader proxyLoader = null;
        Class<?>[] classObjs = new Class<?>[interfaceNames.length];
        boolean[] nonpublic = { false };

      defaultLoaderCase:
        if (defaultLoader != null) {
            try {
                proxyLoader =
                    loadProxyInterfaces(interfaceNames, defaultLoader,
                                        classObjs, nonpublic);
                if (loaderLog.isLoggable(Log.VERBOSE)) {
                    ClassLoader[] definingLoaders =
                        new ClassLoader[classObjs.length];
                    for (int i = 0; i < definingLoaders.length; i++) {
                        definingLoaders[i] = classObjs[i].getClassLoader();
                    }
                    loaderLog.log(Log.VERBOSE,
                        "proxy interfaces found via defaultLoader, " +
                        "defined by " + Arrays.asList(definingLoaders));
                }
            } catch (ClassNotFoundException e) {
                break defaultLoaderCase;
            }
            if (!nonpublic[0]) {
                if (preferCodebase) {
                    try {
                        return Proxy.getProxyClass(codebaseLoader, classObjs);
                    } catch (IllegalArgumentException e) {
                    }
                }
                proxyLoader = defaultLoader;
            }
            return loadProxyClass(proxyLoader, classObjs);
        }

        nonpublic[0] = false;
        proxyLoader = loadProxyInterfaces(interfaceNames, codebaseLoader,
                                          classObjs, nonpublic);
        if (loaderLog.isLoggable(Log.VERBOSE)) {
            ClassLoader[] definingLoaders = new ClassLoader[classObjs.length];
            for (int i = 0; i < definingLoaders.length; i++) {
                definingLoaders[i] = classObjs[i].getClassLoader();
            }
            loaderLog.log(Log.VERBOSE,
                "proxy interfaces found via codebase, " +
                "defined by " + Arrays.asList(definingLoaders));
        }
        if (!nonpublic[0]) {
            proxyLoader = codebaseLoader;
        }
        return loadProxyClass(proxyLoader, classObjs);
    }

    /**
     * Define a proxy class in the given class loader.  The proxy
     * class will implement the given interfaces Classes.
     */
    private static Class<?> loadProxyClass(ClassLoader loader, Class<?>[] interfaces)
        throws ClassNotFoundException
    {
        try {
            return Proxy.getProxyClass(loader, interfaces);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(
                "error creating dynamic proxy class", e);
        }
    }

    /*
     * Load Class objects for the names in the interfaces array from
     * the given class loader.
     *
     * We pass classObjs and nonpublic arrays to avoid needing a
     * multi-element return value.  nonpublic is an array to enable
     * the method to take a boolean argument by reference.
     *
     * nonpublic array is needed to signal when the return value of
     * this method should be used as the proxy class loader.  Because
     * null represents a valid class loader, that value is
     * insufficient to signal that the return value should not be used
     * as the proxy class loader.
     */
    private static ClassLoader loadProxyInterfaces(String[] interfaces,
                                                   ClassLoader loader,
                                                   Class<?>[] classObjs,
                                                   boolean[] nonpublic)
        throws ClassNotFoundException
    {
        /* loader of a non-public interface class */
        ClassLoader nonpublicLoader = null;

        for (int i = 0; i < interfaces.length; i++) {
            Class<?> cl =
                (classObjs[i] = Class.forName(interfaces[i], false, loader));

            if (!Modifier.isPublic(cl.getModifiers())) {
                ClassLoader current = cl.getClassLoader();
                if (loaderLog.isLoggable(Log.VERBOSE)) {
                    loaderLog.log(Log.VERBOSE,
                        "non-public interface \"" + interfaces[i] +
                        "\" defined by " + current);
                }
                if (!nonpublic[0]) {
                    nonpublicLoader = current;
                    nonpublic[0] = true;
                } else if (current != nonpublicLoader) {
                    throw new IllegalAccessError(
                        "non-public interfaces defined in different " +
                        "class loaders");
                }
            }
        }
        return nonpublicLoader;
    }

    /**
     * Convert a string containing a space-separated list of URLs into a
     * corresponding array of URL objects, throwing a MalformedURLException
     * if any of the URLs are invalid.
     */
    private static URL[] pathToURLs(String path)
        throws MalformedURLException
    {
        synchronized (pathToURLsCache) {
            Object[] v = pathToURLsCache.get(path);
            if (v != null) {
                return ((URL[])v[0]);
            }
        }
        StringTokenizer st = new StringTokenizer(path); // divide by spaces
        URL[] urls = new URL[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            @SuppressWarnings("deprecation")
            var url = new URL(st.nextToken());
            urls[i] = url;
        }
        synchronized (pathToURLsCache) {
            pathToURLsCache.put(path,
                                new Object[] {urls, new SoftReference<String>(path)});
        }
        return urls;
    }

    /** map from weak(key=string) to [URL[], soft(key)] */
    private static final Map<String, Object[]> pathToURLsCache
        = new WeakHashMap<>(5);

    /**
     * Convert an array of URL objects into a corresponding string
     * containing a space-separated list of URLs.
     *
     * Note that if the array has zero elements, the return value is
     * null, not the empty string.
     */
    private static String urlsToPath(URL[] urls) {
        if (urls.length == 0) {
            return null;
        } else if (urls.length == 1) {
            return urls[0].toExternalForm();
        } else {
            StringBuilder path = new StringBuilder(urls[0].toExternalForm());
            for (int i = 1; i < urls.length; i++) {
                path.append(' ');
                path.append(urls[i].toExternalForm());
            }
            return path.toString();
        }
    }
}
