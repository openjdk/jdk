/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.server.RMIClassLoaderSpi;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

/**
 * Derived from <code>LoaderHandler</code> to provides the implementation of the
 * static methods of the <code>java.rmi.server.RMIClassLoader</code> class.
 *
 * WARNING: this loader will load classes from codebase annotations received over the
 * wire, without performing any security checks. It is intended only for testing.
 *
 * @author      Ann Wollrath
 * @author      Peter Jones
 * @author      Laird Dornin
 */
@SuppressWarnings("deprecation")
public final class TestLoaderHandler extends RMIClassLoaderSpi {

    /**
     * value of "java.rmi.server.codebase" property, as cached at class
     * initialization time.  It may be null or contain malformed URLs.
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
    private static final Set<ClassLoader> codebaseLoaders =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>(5)));
    static {
        for (ClassLoader codebaseLoader = ClassLoader.getSystemClassLoader();
             codebaseLoader != null;
             codebaseLoader = codebaseLoader.getParent())
        {
            codebaseLoaders.add(codebaseLoader);
        }
    }

    /**
     * table mapping codebase URL path and context class loader pairs
     * to class loader instances.  Entries hold class loaders with weak
     * references, so this table does not prevent loaders from being
     * garbage collected.
     */
    private static final HashMap<LoaderKey, LoaderEntry> loaderTable
        = new HashMap<>(5);

    /** reference queue for cleared class loader entries */
    private static final ReferenceQueue<Loader> refQueue = new ReferenceQueue<>();

    public TestLoaderHandler() {
        System.err.println("*** TestLoaderHandler created ***");
    }

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
    public Class<?> loadClass(String codebase, String name,
                                     ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        URL[] urls;
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        if (defaultLoader != null) {
            try {
                return Class.forName(name, false, defaultLoader);
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
    public String getClassAnnotation(Class<?> cl) {
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
        if (loader == null || codebaseLoaders.contains(loader)) {
            return codebaseProperty;
        }

        /*
         * Get the codebase URL path for the class loader, if it supports
         * such a notion (i.e., if it is a URLClassLoader or subclass).
         */
        String annotation = null;
        if (loader instanceof Loader) {
            /*
             * If the class loader is one of our RMI class loaders, we have
             * already computed the class annotation string, and no
             * permissions are required to know the URLs.
             */
            annotation = ((Loader) loader).getClassAnnotation();

        } else if (loader instanceof URLClassLoader) {
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
     * Returns a classloader that loads classes from the given codebase URL
     * path.  The parent classloader of the returned classloader is the
     * context class loader.
     */
    public ClassLoader getClassLoader(String codebase)
        throws MalformedURLException
    {
        ClassLoader parent = getRMIContextClassLoader();

        URL[] urls;
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        return lookupLoader(urls, parent);
    }

    /**
     * Return the security context of the given class loader.
     */
    public static Object getSecurityContext(ClassLoader loader) {
        /*
         * REMIND: This is a bogus JDK1.1-compatible implementation.
         * This method should never be called by application code anyway
         * (hence the deprecation), but should it do something different
         * and perhaps more useful, like return a String or a URL[]?
         */
        if (loader instanceof Loader) {
            URL[] urls = ((Loader) loader).getURLs();
            if (urls.length > 0) {
                return urls[0];
            }
        }
        return null;
    }

    /**
     * Register a class loader as one whose classes should always be
     * annotated with the value of the "java.rmi.server.codebase" property.
     */
    public static void registerCodebaseLoader(ClassLoader loader) {
        codebaseLoaders.add(loader);
    }

    /**
     * Load a class from the RMI class loader corresponding to the given
     * codebase URL path in the current execution context.
     */
    private Class<?> loadClass(URL[] urls, String name)
        throws ClassNotFoundException
    {
        ClassLoader parent = getRMIContextClassLoader();

        /*
         * Get or create the RMI class loader for this codebase URL path
         * and parent class loader pair.
         */
        Loader loader = lookupLoader(urls, parent);

        return Class.forName(name, false, loader);
    }

    /**
     * Define and return a dynamic proxy class in a class loader with
     * URLs supplied in the given location.  The proxy class will
     * implement interface classes named by the given array of
     * interface names.
     */
    public Class<?> loadProxyClass(String codebase, String[] interfaces,
                                          ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
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
        ClassLoader parent = getRMIContextClassLoader();

        URL[] urls;
        if (codebase != null) {
            urls = pathToURLs(codebase);
        } else {
            urls = getDefaultCodebaseURLs();
        }

        /*
         * Get or create the RMI class loader for this codebase URL path
         * and parent class loader pair.
         */
        Loader loader = lookupLoader(urls, parent);

        return loadProxyClass(interfaces, defaultLoader, loader, true);
    }

    /**
     * Define a proxy class in the default loader if appropriate.
     * Define the class in an RMI class loader otherwise.  The proxy
     * class will implement classes which are named in the supplied
     * interfaceNames.
     */
    private Class<?> loadProxyClass(String[] interfaceNames,
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
        if (!nonpublic[0]) {
            proxyLoader = codebaseLoader;
        }
        return loadProxyClass(proxyLoader, classObjs);
    }

    /**
     * Define a proxy class in the given class loader.  The proxy
     * class will implement the given interfaces Classes.
     */
    private Class<?> loadProxyClass(ClassLoader loader, Class<?>[] interfaces)
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

    /**
     * Return the class loader to be used as the parent for an RMI class
     * loader used in the current execution context.
     */
    private static ClassLoader getRMIContextClassLoader() {
        /*
         * The current implementation simply uses the current thread's
         * context class loader.
         */
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Look up the RMI class loader for the given codebase URL path
     * and the given parent class loader.  A new class loader instance
     * will be created and returned if no match is found.
     */
    @SuppressWarnings("removal")
    private static Loader lookupLoader(final URL[] urls,
                                       final ClassLoader parent)
    {
        /*
         * If the requested codebase URL path is empty, the supplied
         * parent class loader will be sufficient.
         *
         * REMIND: To be conservative, this optimization is commented out
         * for now so that it does not open a security hole in the future
         * by providing untrusted code with direct access to the public
         * loadClass() method of a class loader instance that it cannot
         * get a reference to.  (It's an unlikely optimization anyway.)
         *
         * if (urls.length == 0) {
         *     return parent;
         * }
         */

        LoaderEntry entry;
        Loader loader;

        synchronized (TestLoaderHandler.class) {
            /*
             * Take this opportunity to remove from the table entries
             * whose weak references have been cleared.
             */
            while ((entry = (LoaderEntry) refQueue.poll()) != null) {
                if (!entry.removed) {   // ignore entries removed below
                    loaderTable.remove(entry.key);
                }
            }

            /*
             * Look up the codebase URL path and parent class loader pair
             * in the table of RMI class loaders.
             */
            LoaderKey key = new LoaderKey(urls, parent);
            entry = loaderTable.get(key);

            if (entry == null || (loader = entry.get()) == null) {
                /*
                 * If entry was in table but it's weak reference was cleared,
                 * remove it from the table and mark it as explicitly cleared,
                 * so that new matching entry that we put in the table will
                 * not be erroneously removed when this entry is processed
                 * from the weak reference queue.
                 */
                if (entry != null) {
                    loaderTable.remove(key);
                    entry.removed = true;
                }

                /*
                 * A matching loader was not found, so create a new class
                 * loader instance for the requested codebase URL path and
                 * parent class loader.  The instance is created within an
                 * access control context restricted to the permissions
                 * necessary to load classes from its codebase URL path.
                 */
                loader = new Loader(urls, parent);

                /*
                 * Finally, create an entry to hold the new loader with a
                 * weak reference and store it in the table with the key.
                 */
                entry = new LoaderEntry(key, loader);
                loaderTable.put(key, entry);
            }
        }

        return loader;
    }

    /**
     * LoaderKey holds a codebase URL path and parent class loader pair
     * used to look up RMI class loader instances in its class loader cache.
     */
    private static class LoaderKey {

        private URL[] urls;

        private ClassLoader parent;

        private int hashValue;

        public LoaderKey(URL[] urls, ClassLoader parent) {
            this.urls = urls;
            this.parent = parent;

            if (parent != null) {
                hashValue = parent.hashCode();
            }
            for (int i = 0; i < urls.length; i++) {
                hashValue ^= urls[i].hashCode();
            }
        }

        public int hashCode() {
            return hashValue;
        }

        public boolean equals(Object obj) {
            if (obj instanceof LoaderKey) {
                LoaderKey other = (LoaderKey) obj;
                if (parent != other.parent) {
                    return false;
                }
                if (urls == other.urls) {
                    return true;
                }
                if (urls.length != other.urls.length) {
                    return false;
                }
                for (int i = 0; i < urls.length; i++) {
                    if (!urls[i].equals(other.urls[i])) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * LoaderEntry contains a weak reference to an RMIClassLoader.  The
     * weak reference is registered with the private static "refQueue"
     * queue.  The entry contains the codebase URL path and parent class
     * loader key for the loader so that the mapping can be removed from
     * the table efficiently when the weak reference is cleared.
     */
    private static class LoaderEntry extends WeakReference<Loader> {

        public LoaderKey key;

        /**
         * set to true if the entry has been removed from the table
         * because it has been replaced, so it should not be attempted
         * to be removed again
         */
        public boolean removed = false;

        public LoaderEntry(LoaderKey key, Loader loader) {
            super(loader, refQueue);
            this.key = key;
        }
    }

    /**
     * Loader is the actual class of the RMI class loaders created
     * by the RMIClassLoader static methods.
     */
    private static class Loader extends URLClassLoader {

        /** string form of loader's codebase URL path, also an optimization */
        private String annotation;

        private Loader(URL[] urls, ClassLoader parent) {
            super(urls, parent);

            /*
             * Caching the value of class annotation string here assumes
             * that the protected method addURL() is never called on this
             * class loader.
             */
            annotation = urlsToPath(urls);
        }

        /**
         * Return the string to be annotated with all classes loaded from
         * this class loader.
         */
        public String getClassAnnotation() {
            return annotation;
        }

        /**
         * Return a string representation of this loader (useful for
         * debugging).
         */
        public String toString() {
            return super.toString() + "[\"" + annotation + "\"]";
        }
    }
}
