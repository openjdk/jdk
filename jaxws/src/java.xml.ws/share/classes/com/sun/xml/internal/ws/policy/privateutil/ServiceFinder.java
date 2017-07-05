/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.privateutil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;


/**
 *
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
 * file in the resource directory {@code META-INF/services}.  The file's name
 * should consist of the fully-qualified name of the abstract service class.
 * The file should contain a list of fully-qualified concrete provider-class
 * names, one per line.  Space and tab characters surrounding each name, as
 * well as blank lines, are ignored.  The comment character is {@code '#'}
 * ({@code 0x23}); on each line all characters following the first comment
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
 * {@code java.io.spi.CharCodec}.  It has two abstract methods:
 * <p/>
 * <pre>
 *   public abstract CharEncoder getEncoder(String encodingName);
 *   public abstract CharDecoder getDecoder(String encodingName);
 * </pre>
 * <p/>
 * Each method returns an appropriate object or {@code null} if it cannot
 * translate the given encoding.  Typical {@code CharCodec} providers will
 * support more than one encoding.
 * <p/>
 * <p> If {@code sun.io.StandardCodec} is a provider of the {@code CharCodec}
 * service then its jar file would contain the file
 * {@code META-INF/services/java.io.spi.CharCodec}.  This file would contain
 * the single line:
 * <p/>
 * <pre>
 *   sun.io.StandardCodec    # Standard codecs for the platform
 * </pre>
 * <p/>
 * To locate an encoder for a given encoding name, the internal I/O code would
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
final class ServiceFinder<T> implements Iterable<T> {
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(ServiceFinder.class);

    private static final String prefix = "META-INF/services/";

    private final Class<T> serviceClass;
    private final ClassLoader classLoader;

    /**
     * Locates and incrementally instantiates the available providers of a
     * given service using the given class loader.
     * <p/>
     * <p> This method transforms the name of the given service class into a
     * provider-configuration filename as described above and then uses the
     * {@code getResources} method of the given class loader to find all
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
     *                and instantiate provider classes, or {@code null} if the system
     *                class loader (or, failing that the bootstrap class loader) is to
     *                be used
     * @throws ServiceConfigurationError If a provider-configuration file violates the specified format
     *                                   or names a provider class that cannot be found and instantiated
     * @see #find(Class)
     */
    static <T> ServiceFinder<T> find(final Class<T> service, final ClassLoader loader) {
        if (null==service) {
            throw LOGGER.logSevereException(new NullPointerException(LocalizationMessages.WSP_0032_SERVICE_CAN_NOT_BE_NULL()));
        }
        return new ServiceFinder<T>(service,loader);
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
    public static <T> ServiceFinder<T> find(final Class<T> service) {
        return find(service,Thread.currentThread().getContextClassLoader());
    }

    private ServiceFinder(Class<T> service, ClassLoader loader) {
        this.serviceClass = service;
        this.classLoader = loader;
    }

    /**
     * Returns discovered objects incrementally.
     *
     * @return An {@code Iterator} that yields provider objects for the given
     *         service, in some arbitrary order.  The iterator will throw a
     *         {@code ServiceConfigurationError} if a provider-configuration
     *         file violates the specified format or if a provider class cannot
     *         be found and instantiated.
     */
    public Iterator<T> iterator() {
        return new LazyIterator<T>(serviceClass,classLoader);
    }

    /**
     * Returns discovered objects all at once.
     *
     * @return
     *      can be empty but never null.
     *
     * @throws ServiceConfigurationError
     */
    @SuppressWarnings({"unchecked"})
    public T[] toArray() {
        List<T> result = new ArrayList<T>();
        for (T t : this) {
            result.add(t);
        }
        return result.toArray((T[])Array.newInstance(serviceClass,result.size()));
    }

    private static void fail(final Class service, final String msg, final Throwable cause)
        throws ServiceConfigurationError {
        final ServiceConfigurationError sce
            = new ServiceConfigurationError(LocalizationMessages.WSP_0025_SPI_FAIL_SERVICE_MSG(service.getName(), msg));
        if (null != cause) {
            sce.initCause(cause);
        }

        throw LOGGER.logSevereException(sce);
    }

/*    private static void fail(Class service, String msg)
        throws ServiceConfigurationError {
        throw new ServiceConfigurationError(LocalizationMessages.WSP_0025_SPI_FAIL_SERVICE_MSG(service.getName(), msg));
    }*/

    private static void fail(final Class service, final URL u, final int line, final String msg, final Throwable cause)
        throws ServiceConfigurationError {
        fail(service, LocalizationMessages.WSP_0024_SPI_FAIL_SERVICE_URL_LINE_MSG(u , line, msg), cause);
    }

    /**
     * Parse a single line from the given configuration file, adding the name
     * on the line to both the names list and the returned set iff the name is
     * not already a member of the returned set.
     */
    private static int parseLine(final Class service, final URL u, final BufferedReader r, final int lc,
                                 final List<String> names, final Set<String> returned)
        throws IOException, ServiceConfigurationError {
        String ln = r.readLine();
        if (ln == null) {
            return -1;
        }
        final int ci = ln.indexOf('#');
        if (ci >= 0) ln = ln.substring(0, ci);
        ln = ln.trim();
        final int n = ln.length();
        if (n != 0) {
            if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0))
                fail(service, u, lc, LocalizationMessages.WSP_0067_ILLEGAL_CFG_FILE_SYNTAX(), null);
            int cp = ln.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                fail(service, u, lc, LocalizationMessages.WSP_0066_ILLEGAL_PROVIDER_CLASSNAME(ln), null);
            for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                cp = ln.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp) && (cp != '.'))
                    fail(service, u, lc, LocalizationMessages.WSP_0066_ILLEGAL_PROVIDER_CLASSNAME(ln), null);
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
     *                 that will be yielded from the returned {@code Iterator}.
     * @return A (possibly empty) {@code Iterator} that will yield the
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
            fail(service, ": " + x, x);
        } finally {
            try {
                if (r != null) r.close();
                if (in != null) in.close();
            } catch (IOException y) {
                fail(service, ": " + y, y);
            }
        }
        return names.iterator();
    }


    /**
     * Private inner class implementing fully-lazy provider lookup
     */
    private static class LazyIterator<T> implements Iterator<T> {
        Class<T> service;
        ClassLoader loader;
        Enumeration<URL> configs = null;
        Iterator<String> pending = null;
        Set<String> returned = new TreeSet<String>();
        String nextName = null;

        private LazyIterator(Class<T> service, ClassLoader loader) {
            this.service = service;
            this.loader = loader;
        }

        public boolean hasNext() throws ServiceConfigurationError {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    final String fullName = prefix + service.getName();
                    if (loader == null)
                        configs = ClassLoader.getSystemResources(fullName);
                    else
                        configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, ": " + x, x);
                }
            }
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                pending = parse(service, configs.nextElement(), returned);
            }
            nextName = pending.next();
            return true;
        }

        public T next() throws ServiceConfigurationError {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final String cn = nextName;
            nextName = null;
            try {
                return service.cast(Class.forName(cn, true, loader).newInstance());
            } catch (ClassNotFoundException x) {
                fail(service, LocalizationMessages.WSP_0027_SERVICE_PROVIDER_NOT_FOUND(cn), x);
            } catch (Exception x) {
                fail(service, LocalizationMessages.WSP_0028_SERVICE_PROVIDER_COULD_NOT_BE_INSTANTIATED(cn), x);
            }
            return null;    /* This cannot happen */
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
