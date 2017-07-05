/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.ComponentEx;
import com.sun.xml.internal.ws.api.server.ContainerResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;


/**
 * A simple service-provider lookup mechanism.  A <i>service</i> is a
 * well-known set of interfaces and (usually abstract) classes.  A <i>service
 * provider</i> is a specific implementation of a service.  The classes in a
 * provider typically implement the interfaces and subclass the classes defined
 * in the service itself.  Service providers may be installed in an
 * implementation of the Java platform in the form of extensions, that is, jar
 * files placed into any of the usual extension directories.  Providers may
 * also be made available by adding them to the applet or application class
 * path or by some other platform-specific means.
 * <p/>
 * <p> In this lookup mechanism a service is represented by an interface or an
 * abstract class.  (A concrete class may be used, but this is not
 * recommended.)  A provider of a given service contains one or more concrete
 * classes that extend this <i>service class</i> with data and code specific to
 * the provider.  This <i>provider class</i> will typically not be the entire
 * provider itself but rather a proxy that contains enough information to
 * decide whether the provider is able to satisfy a particular request together
 * with code that can create the actual provider on demand.  The details of
 * provider classes tend to be highly service-specific; no single class or
 * interface could possibly unify them, so no such class has been defined.  The
 * only requirement enforced here is that provider classes must have a
 * zero-argument constructor so that they may be instantiated during lookup.
 * <p/>
 * <p> A service provider identifies itself by placing a provider-configuration
 * file in the resource directory <tt>META-INF/services</tt>.  The file's name
 * should consist of the fully-qualified name of the abstract service class.
 * The file should contain a list of fully-qualified concrete provider-class
 * names, one per line.  Space and tab characters surrounding each name, as
 * well as blank lines, are ignored.  The comment character is <tt>'#'</tt>
 * (<tt>0x23</tt>); on each line all characters following the first comment
 * character are ignored.  The file must be encoded in UTF-8.
 * <p/>
 * <p> If a particular concrete provider class is named in more than one
 * configuration file, or is named in the same configuration file more than
 * once, then the duplicates will be ignored.  The configuration file naming a
 * particular provider need not be in the same jar file or other distribution
 * unit as the provider itself.  The provider must be accessible from the same
 * class loader that was initially queried to locate the configuration file;
 * note that this is not necessarily the class loader that found the file.
 * <p/>
 * <p> <b>Example:</b> Suppose we have a service class named
 * <tt>java.io.spi.CharCodec</tt>.  It has two abstract methods:
 * <p/>
 * <pre>
 *   public abstract CharEncoder getEncoder(String encodingName);
 *   public abstract CharDecoder getDecoder(String encodingName);
 * </pre>
 * <p/>
 * Each method returns an appropriate object or <tt>null</tt> if it cannot
 * translate the given encoding.  Typical <tt>CharCodec</tt> providers will
 * support more than one encoding.
 * <p/>
 * <p> If <tt>sun.io.StandardCodec</tt> is a provider of the <tt>CharCodec</tt>
 * service then its jar file would contain the file
 * <tt>META-INF/services/java.io.spi.CharCodec</tt>.  This file would contain
 * the single line:
 * <p/>
 * <pre>
 *   sun.io.StandardCodec    # Standard codecs for the platform
 * </pre>
 * <p/>
 * To locate an codec for a given encoding name, the internal I/O code would
 * do something like this:
 * <p/>
 * <pre>
 *   CharEncoder getEncoder(String encodingName) {
 *       for( CharCodec cc : ServiceFinder.find(CharCodec.class) ) {
 *           CharEncoder ce = cc.getEncoder(encodingName);
 *           if (ce != null)
 *               return ce;
 *       }
 *       return null;
 *   }
 * </pre>
 * <p/>
 * The provider-lookup mechanism always executes in the security context of the
 * caller.  Trusted system code should typically invoke the methods in this
 * class from within a privileged security context.
 *
 * @author Mark Reinhold
 * @version 1.11, 03/12/19
 * @since 1.3
 */
public final class ServiceFinder<T> implements Iterable<T> {

    private static final String prefix = "META-INF/services/";

    private static WeakHashMap<ClassLoader, ConcurrentHashMap<String, ServiceName[]>> serviceNameCache
             = new WeakHashMap<ClassLoader, ConcurrentHashMap<String, ServiceName[]>>();

    private final Class<T> serviceClass;
    private final @Nullable ClassLoader classLoader;
    private final @Nullable ComponentEx component;

    private static class ServiceName {
        final String className;
        final URL config;
        public ServiceName(String className, URL config) {
            this.className = className;
            this.config = config;
        }
    }

    public static <T> ServiceFinder<T> find(@NotNull Class<T> service, @Nullable ClassLoader loader, Component component) {
        return new ServiceFinder<T>(service, loader, component);
    }

    public static <T> ServiceFinder<T> find(@NotNull Class<T> service, Component component) {
        return find(service,Thread.currentThread().getContextClassLoader(),component);
    }

    /**
     * Locates and incrementally instantiates the available providers of a
     * given service using the given class loader.
     * <p/>
     * <p> This method transforms the name of the given service class into a
     * provider-configuration filename as described above and then uses the
     * <tt>getResources</tt> method of the given class loader to find all
     * available files with that name.  These files are then read and parsed to
     * produce a list of provider-class names.  The iterator that is returned
     * uses the given class loader to lookup and then instantiate each element
     * of the list.
     * <p/>
     * <p> Because it is possible for extensions to be installed into a running
     * Java virtual machine, this method may return different results each time
     * it is invoked. <p>
     *
     * @param service The service's abstract service class
     * @param loader  The class loader to be used to load provider-configuration files
     *                and instantiate provider classes, or <tt>null</tt> if the system
     *                class loader (or, failing that the bootstrap class loader) is to
     *                be used
     * @throws ServiceConfigurationError If a provider-configuration file violates the specified format
     *                                   or names a provider class that cannot be found and instantiated
     * @see #find(Class)
     */
    public static <T> ServiceFinder<T> find(@NotNull Class<T> service, @Nullable ClassLoader loader) {
        return find(service, loader, ContainerResolver.getInstance().getContainer());
    }

    /**
     * Locates and incrementally instantiates the available providers of a
     * given service using the context class loader.  This convenience method
     * is equivalent to
     * <p/>
     * <pre>
     *   ClassLoader cl = Thread.currentThread().getContextClassLoader();
     *   return Service.providers(service, cl);
     * </pre>
     *
     * @param service The service's abstract service class
     *
     * @throws ServiceConfigurationError If a provider-configuration file violates the specified format
     *                                   or names a provider class that cannot be found and instantiated
     * @see #find(Class, ClassLoader)
     */
    public static <T> ServiceFinder<T> find(Class<T> service) {
        return find(service,Thread.currentThread().getContextClassLoader());
    }

    private ServiceFinder(Class<T> service, ClassLoader loader, Component component) {
        this.serviceClass = service;
        this.classLoader = loader;
        this.component = getComponentEx(component);
    }

    private static ServiceName[] serviceClassNames(Class serviceClass, ClassLoader classLoader) {
        ArrayList<ServiceName> l = new ArrayList<ServiceName>();
        for (Iterator<ServiceName> it = new ServiceNameIterator(serviceClass,classLoader);it.hasNext();) l.add(it.next());
        return l.toArray(new ServiceName[l.size()]);
    }

    /**
     * Returns discovered objects incrementally.
     *
     * @return An <tt>Iterator</tt> that yields provider objects for the given
     *         service, in some arbitrary order.  The iterator will throw a
     *         <tt>ServiceConfigurationError</tt> if a provider-configuration
     *         file violates the specified format or if a provider class cannot
     *         be found and instantiated.
     */
    @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
        Iterator<T> it = new LazyIterator<T>(serviceClass,classLoader);
        return component != null ?
                        new CompositeIterator<T>(
                                        component.getIterableSPI(serviceClass).iterator(),it) :
                        it;
    }

    /**
     * Returns discovered objects all at once.
     *
     * @return
     *      can be empty but never null.
     *
     * @throws ServiceConfigurationError
     */
    public T[] toArray() {
        List<T> result = new ArrayList<T>();
        for (T t : this) {
            result.add(t);
        }
        return result.toArray((T[])Array.newInstance(serviceClass,result.size()));
    }

    private static void fail(Class service, String msg, Throwable cause)
        throws ServiceConfigurationError {
        ServiceConfigurationError sce
            = new ServiceConfigurationError(service.getName() + ": " + msg);
        sce.initCause(cause);
        throw sce;
    }

    private static void fail(Class service, String msg)
        throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    private static void fail(Class service, URL u, int line, String msg)
        throws ServiceConfigurationError {
        fail(service, u + ":" + line + ": " + msg);
    }

    /**
     * Parse a single line from the given configuration file, adding the name
     * on the line to both the names list and the returned set iff the name is
     * not already a member of the returned set.
     */
    private static int parseLine(Class service, URL u, BufferedReader r, int lc,
                                 List<String> names, Set<String> returned)
        throws IOException, ServiceConfigurationError {
        String ln = r.readLine();
        if (ln == null) {
            return -1;
        }
        int ci = ln.indexOf('#');
        if (ci >= 0) ln = ln.substring(0, ci);
        ln = ln.trim();
        int n = ln.length();
        if (n != 0) {
            if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0))
                fail(service, u, lc, "Illegal configuration-file syntax");
            int cp = ln.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                fail(service, u, lc, "Illegal provider-class name: " + ln);
            for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                cp = ln.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp) && (cp != '.'))
                    fail(service, u, lc, "Illegal provider-class name: " + ln);
            }
            if (!returned.contains(ln)) {
                names.add(ln);
                returned.add(ln);
            }
        }
        return lc + 1;
    }

    /**
     * Parse the content of the given URL as a provider-configuration file.
     *
     * @param service  The service class for which providers are being sought;
     *                 used to construct error detail strings
     * @param u        The URL naming the configuration file to be parsed
     * @param returned A Set containing the names of provider classes that have already
     *                 been returned.  This set will be updated to contain the names
     *                 that will be yielded from the returned <tt>Iterator</tt>.
     * @return A (possibly empty) <tt>Iterator</tt> that will yield the
     *         provider-class names in the given configuration file that are
     *         not yet members of the returned set
     * @throws ServiceConfigurationError If an I/O error occurs while reading from the given URL, or
     *                                   if a configuration-file format error is detected
     */
    @SuppressWarnings({"StatementWithEmptyBody"})
    private static Iterator<String> parse(Class service, URL u, Set<String> returned)
        throws ServiceConfigurationError {
        InputStream in = null;
        BufferedReader r = null;
        ArrayList<String> names = new ArrayList<String>();
        try {
            in = u.openStream();
            r = new BufferedReader(new InputStreamReader(in, "utf-8"));
            int lc = 1;
            while ((lc = parseLine(service, u, r, lc, names, returned)) >= 0) ;
        } catch (IOException x) {
            fail(service, ": " + x);
        } finally {
            try {
                if (r != null) r.close();
                if (in != null) in.close();
            } catch (IOException y) {
                fail(service, ": " + y);
            }
        }
        return names.iterator();
    }

    private static ComponentEx getComponentEx(Component component) {
        if (component instanceof ComponentEx)
                return (ComponentEx) component;

        return component != null ? new ComponentExWrapper(component) : null;
    }

    private static class ComponentExWrapper implements ComponentEx {
        private final Component component;

        public ComponentExWrapper(Component component) {
                this.component = component;
        }

                public <S> S getSPI(Class<S> spiType) {
                        return component.getSPI(spiType);
                }

                public <S> Iterable<S> getIterableSPI(Class<S> spiType) {
                S item = getSPI(spiType);
                if (item != null) {
                        Collection<S> c = Collections.singletonList(item);
                        return c;
                }
                return Collections.emptySet();
                }
    }

    private static class CompositeIterator<T> implements Iterator<T> {
        private final Iterator<Iterator<T>> it;
        private Iterator<T> current = null;

        public CompositeIterator(Iterator<T>... iterators) {
                it = Arrays.asList(iterators).iterator();
        }

                public boolean hasNext() {
                        if (current != null && current.hasNext())
                                return true;

                        while (it.hasNext()) {
                                current = it.next();
                                if (current.hasNext())
                                        return true;

                        }

                        return false;
                }

                public T next() {
                        if (!hasNext())
                                throw new NoSuchElementException();

                        return current.next();
                }

                public void remove() {
            throw new UnsupportedOperationException();
                }
    }

    /**
     * Private inner class implementing fully-lazy provider lookup
     */
    private static class ServiceNameIterator implements Iterator<ServiceName> {
        Class service;
        @Nullable ClassLoader loader;
        Enumeration<URL> configs = null;
        Iterator<String> pending = null;
        Set<String> returned = new TreeSet<String>();
        String nextName = null;
        URL currentConfig = null;

        private ServiceNameIterator(Class service, ClassLoader loader) {
            this.service = service;
            this.loader = loader;
        }

        public boolean hasNext() throws ServiceConfigurationError {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    String fullName = prefix + service.getName();
                    if (loader == null)
                        configs = ClassLoader.getSystemResources(fullName);
                    else
                        configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, ": " + x);
                }
            }
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                currentConfig = configs.nextElement();
                pending = parse(service, currentConfig, returned);
            }
            nextName = pending.next();
            return true;
        }

        public ServiceName next() throws ServiceConfigurationError {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String cn = nextName;
            nextName = null;
            return new ServiceName(cn, currentConfig);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class LazyIterator<T> implements Iterator<T> {
        Class<T> service;
        @Nullable ClassLoader loader;
        ServiceName[] names;
        int index;

        private LazyIterator(Class<T> service, ClassLoader loader) {
            this.service = service;
            this.loader = loader;
            this.names = null;
            index = 0;
        }

        @Override
        public boolean hasNext() {
            if (names == null) {
                ConcurrentHashMap<String, ServiceName[]> nameMap = null;
                synchronized(serviceNameCache){ nameMap = serviceNameCache.get(loader); }
                names = (nameMap != null)? nameMap.get(service.getName()) : null;
                if (names == null) {
                    names = serviceClassNames(service, loader);
                    if (nameMap == null) nameMap = new ConcurrentHashMap<String, ServiceName[]>();
                    nameMap.put(service.getName(), names);
                    synchronized(serviceNameCache){ serviceNameCache.put(loader,nameMap); }
                }
            }
            return (index < names.length);
        }

        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            ServiceName sn = names[index++];
            String cn = sn.className;
            URL currentConfig = sn.config;
            try {
                return service.cast(Class.forName(cn, true, loader).newInstance());
            } catch (ClassNotFoundException x) {
                fail(service, "Provider " + cn + " is specified in "+currentConfig+" but not found");
            } catch (Exception x) {
              fail(service, "Provider " + cn + " is specified in "+currentConfig+"but could not be instantiated: " + x, x);
            }
            return null;    /* This cannot happen */
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
