/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Layer;
import java.lang.reflect.Modifier;
import java.lang.reflect.Module;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.Loader;
import jdk.internal.loader.LoaderPool;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.module.ServicesCatalog;
import jdk.internal.module.ServicesCatalog.ServiceProvider;

import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;


/**
 * A simple service-provider loading facility.
 *
 * <p> A <i>service</i> is a well-known set of interfaces and (usually
 * abstract) classes.  A <i>service provider</i> is a specific implementation
 * of a service.  The classes in a provider typically implement the interfaces
 * and subclass the classes defined in the service itself.
 * Providers may be developed and deployed as modules and made available using
 * the application module path. Providers may alternatively be packaged as JAR
 * files and made available by adding them to the application class path. The
 * advantage of developing a provider as a module is that the provider can be
 * fully encapsulated to hide all details of its implementation.
 *
 * <p> For the purpose of loading, a service is represented by a single type,
 * that is, a single interface or abstract class.  (A concrete class can be
 * used, but this is not recommended.)  A provider of a given service contains
 * one or more concrete classes that extend this <i>service type</i> with data
 * and code specific to the provider.  The <i>provider class</i> is typically
 * not the entire provider itself but rather a proxy which contains enough
 * information to decide whether the provider is able to satisfy a particular
 * request together with code that can create the actual provider on demand.
 * The details of provider classes tend to be highly service-specific; no
 * single class or interface could possibly unify them, so no such type is
 * defined here. A requirement enforced by this facility is that each provider
 * class must have a {@code public} zero-argument constructor.
 *
 * <p> An application or library using this loading facility and developed
 * and deployed as a named module must have an appropriate <i>uses</i> clause
 * in its <i>module descriptor</i> to declare that the module uses
 * implementations of the service. A corresponding requirement is that a
 * provider deployed as a named modules must have an appropriate
 * <i>provides</i> clause in its module descriptor to declare that the module
 * provides an implementation of the service. The <i>uses</i> and
 * <i>provides</i> allow consumers of a service to be <i>linked</i> to
 * providers of the service. In the case of {@code load} methods that locate
 * service providers using a class loader, then provider modules defined to
 * that class loader, or a class loader <i>reachable</i> using {@link
 * ClassLoader#getParent() parent} delegation, will be located.
 *
 * <p> A service provider that is packaged as a JAR file for the class path is
 * identified by placing a <i>provider-configuration file</i> in the resource
 * directory <tt>META-INF/services</tt>. The file's name is the fully-qualified
 * <a href="../lang/ClassLoader.html#name">binary name</a> of the service's
 * type. The file contains a list of fully-qualified binary names of concrete
 * provider classes, one per line.  Space and tab characters surrounding each
 * name, as well as blank lines, are ignored.  The comment character is
 * <tt>'#'</tt> (<tt>'&#92;u0023'</tt>,
 * <font style="font-size:smaller;">NUMBER SIGN</font>); on
 * each line all characters following the first comment character are ignored.
 * The file must be encoded in UTF-8.
 * If a particular concrete provider class is named in more than one
 * configuration file, or is named in the same configuration file more than
 * once, then the duplicates are ignored.  The configuration file naming a
 * particular provider need not be in the same JAR file or other distribution
 * unit as the provider itself. The provider must be visible from the same
 * class loader that was initially queried to locate the configuration file;
 * note that this is not necessarily the class loader from which the file was
 * actually loaded.
 *
 * <p> Providers are located and instantiated lazily, that is, on demand.  A
 * service loader maintains a cache of the providers that have been loaded so
 * far.  Each invocation of the {@link #iterator iterator} method returns an
 * iterator that first yields all of the elements of the cache, in
 * instantiation order, and then lazily locates and instantiates any remaining
 * providers, adding each one to the cache in turn.  The cache can be cleared
 * via the {@link #reload reload} method.
 *
 * <p> Service loaders always execute in the security context of the caller
 * of the iterator methods and may also be restricted by the security
 * context of the caller that created the service loader.
 * Trusted system code should typically invoke the methods in this class, and
 * the methods of the iterators which they return, from within a privileged
 * security context.
 *
 * <p> Instances of this class are not safe for use by multiple concurrent
 * threads.
 *
 * <p> Unless otherwise specified, passing a <tt>null</tt> argument to any
 * method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * <p><span style="font-weight: bold; padding-right: 1em">Example</span>
 * Suppose we have a service type <tt>com.example.CodecSet</tt> which is
 * intended to represent sets of encoder/decoder pairs for some protocol.  In
 * this case it is an abstract class with two abstract methods:
 *
 * <blockquote><pre>
 * public abstract Encoder getEncoder(String encodingName);
 * public abstract Decoder getDecoder(String encodingName);</pre></blockquote>
 *
 * Each method returns an appropriate object or <tt>null</tt> if the provider
 * does not support the given encoding.  Typical providers support more than
 * one encoding.
 *
 * <p> The <tt>CodecSet</tt> class creates and saves a single service instance
 * at initialization:
 *
 * <pre>{@code
 * private static ServiceLoader<CodecSet> codecSetLoader
 *     = ServiceLoader.load(CodecSet.class);
 * }</pre>
 *
 * <p> To locate an encoder for a given encoding name it defines a static
 * factory method which iterates through the known and available providers,
 * returning only when it has located a suitable encoder or has run out of
 * providers.
 *
 * <pre>{@code
 * public static Encoder getEncoder(String encodingName) {
 *     for (CodecSet cp : codecSetLoader) {
 *         Encoder enc = cp.getEncoder(encodingName);
 *         if (enc != null)
 *             return enc;
 *     }
 *     return null;
 * }}</pre>
 *
 * <p> A {@code getDecoder} method is defined similarly.
 *
 * <p> If the code creating and using the service loader is developed as
 * a module then its module descriptor will declare the usage with:
 * <pre>{@code uses com.example.CodecSet;}</pre>
 *
 * <p> Now suppose that {@code com.example.impl.StandardCodecs} is an
 * implementation of the {@code CodecSet} service and developed as a module.
 * In that case then the module with the service provider module will declare
 * this in its module descriptor:
 * <pre>{@code provides com.example.CodecSet with com.example.impl.StandardCodecs;
 * }</pre>
 *
 * <p> On the other hand, suppose {@code com.example.impl.StandardCodecs} is
 * packaged in a JAR file for the class path then the JAR file will contain a
 * file named:
 * <pre>{@code META-INF/services/com.example.CodecSet}</pre>
 * that contains the single line:
 * <pre>{@code com.example.impl.StandardCodecs    # Standard codecs}</pre>
 *
 * <p><span style="font-weight: bold; padding-right: 1em">Usage Note</span> If
 * the class path of a class loader that is used for provider loading includes
 * remote network URLs then those URLs will be dereferenced in the process of
 * searching for provider-configuration files.
 *
 * <p> This activity is normal, although it may cause puzzling entries to be
 * created in web-server logs.  If a web server is not configured correctly,
 * however, then this activity may cause the provider-loading algorithm to fail
 * spuriously.
 *
 * <p> A web server should return an HTTP 404 (Not Found) response when a
 * requested resource does not exist.  Sometimes, however, web servers are
 * erroneously configured to return an HTTP 200 (OK) response along with a
 * helpful HTML error page in such cases.  This will cause a {@link
 * ServiceConfigurationError} to be thrown when this class attempts to parse
 * the HTML page as a provider-configuration file.  The best solution to this
 * problem is to fix the misconfigured web server to return the correct
 * response code (HTTP 404) along with the HTML error page.
 *
 * @param  <S>
 *         The type of the service to be loaded by this loader
 *
 * @author Mark Reinhold
 * @since 1.6
 */

public final class ServiceLoader<S>
    implements Iterable<S>
{
    private static final String PREFIX = "META-INF/services/";

    // The class or interface representing the service being loaded
    private final Class<S> service;

    // The module Layer used to locate providers; null when locating
    // providers using a class loader
    private final Layer layer;

    // The class loader used to locate, load, and instantiate providers;
    // null when locating provider using a module Layer
    private final ClassLoader loader;

    // The access control context taken when the ServiceLoader is created
    private final AccessControlContext acc;

    // Cached providers, in instantiation order
    private List<S> providers = new ArrayList<>();

    // The class names of the cached providers, only used when locating
    // service providers via a class loader
    private Set<String> providerNames = new HashSet<>();

    // Incremented when reload is called
    private int reloadCount;

    // the service iterator when locating services via a module layer
    private LayerLookupIterator layerLookupIterator;

    // The module services iterator when locating services in modules
    // defined to a class loader
    private ModuleServicesIterator moduleServicesIterator;

    // The current lazy-lookup iterator for locating legacy provider on the
    // class path via a class loader
    private LazyClassPathIterator lazyClassPathIterator;


    /**
     * Clear this loader's provider cache so that all providers will be
     * reloaded.
     *
     * <p> After invoking this method, subsequent invocations of the {@link
     * #iterator() iterator} method will lazily look up and instantiate
     * providers from scratch, just as is done by a newly-created loader.
     *
     * <p> This method is intended for use in situations in which new providers
     * can be installed into a running Java virtual machine.
     */
    public void reload() {
        providers.clear();

        assert layer == null || loader == null;
        if (layer != null) {
            layerLookupIterator = new LayerLookupIterator();
        } else {
            providerNames.clear();
            moduleServicesIterator = new ModuleServicesIterator();
            lazyClassPathIterator = new LazyClassPathIterator();
        }

        reloadCount++;
    }


    /**
     * Initializes a new instance of this class for locating service providers
     * in a module Layer.
     *
     * @throws ServiceConfigurationError
     *         If {@code svc} is not accessible to {@code caller} or that the
     *         caller's module does not declare that it uses the service type.
     */
    private ServiceLoader(Class<?> caller, Layer layer, Class<S> svc) {

        checkModule(caller.getModule(), svc);

        this.service = svc;
        this.layer = layer;
        this.loader = null;
        this.acc = (System.getSecurityManager() != null)
                ? AccessController.getContext()
                : null;

        reload();
    }

    /**
     * Initializes a new instance of this class for locating service providers
     * via a class loader.
     *
     * @throws ServiceConfigurationError
     *         If {@code svc} is not accessible to {@code caller} or that the
     *         caller's module does not declare that it uses the service type.
     */
    private ServiceLoader(Module callerModule, Class<S> svc, ClassLoader cl) {
        if (VM.isBooted()) {

            checkModule(callerModule, svc);

            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

        } else {

            // if we get here then it means that ServiceLoader is being used
            // before the VM initialization has completed. At this point then
            // only code in the java.base should be executing.
            Module base = Object.class.getModule();
            Module svcModule = svc.getModule();
            if (callerModule != base || svcModule != base) {
                fail(svc, "not accessible to " + callerModule + " during VM init");
            }

            // restricted to boot loader during startup
            cl = null;
        }

        this.service = svc;
        this.layer = null;
        this.loader = cl;
        this.acc = (System.getSecurityManager() != null)
                ? AccessController.getContext()
                : null;

        reload();
    }

    private ServiceLoader(Class<?> caller, Class<S> svc, ClassLoader cl) {
        this(caller.getModule(), svc, cl);
    }



    /**
     * Checks that the given service type is accessible to types in the given
     * module, and check that the module declare that it uses the service type.
     */
    private static void checkModule(Module module, Class<?> svc) {

        // Check that the service type is in a package that is
        // exported to the caller.
        if (!Reflection.verifyModuleAccess(module, svc)) {
            fail(svc, "not accessible to " + module);
        }

        // If the caller is in a named module then it should "uses" the
        // service type
        if (!module.canUse(svc)) {
            fail(svc, "use not declared in " + module);
        }

    }

    private static void fail(Class<?> service, String msg, Throwable cause)
        throws ServiceConfigurationError
    {
        throw new ServiceConfigurationError(service.getName() + ": " + msg,
                                            cause);
    }

    private static void fail(Class<?> service, String msg)
        throws ServiceConfigurationError
    {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    private static void fail(Class<?> service, URL u, int line, String msg)
        throws ServiceConfigurationError
    {
        fail(service, u + ":" + line + ": " + msg);
    }

    // Parse a single line from the given configuration file, adding the name
    // on the line to the names list.
    //
    private int parseLine(Class<?> service, URL u, BufferedReader r, int lc,
                          List<String> names)
        throws IOException, ServiceConfigurationError
    {
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
            if (!providerNames.contains(ln) && !names.contains(ln))
                names.add(ln);
        }
        return lc + 1;
    }

    /**
     * Parse the content of the given URL as a provider-configuration file.
     *
     * @param  service
     *         The service type for which providers are being sought;
     *         used to construct error detail strings
     *
     * @param  u
     *         The URL naming the configuration file to be parsed
     *
     * @return A (possibly empty) iterator that will yield the provider-class
     *         names in the given configuration file that are not yet members
     *         of the returned set
     *
     * @throws ServiceConfigurationError
     *         If an I/O error occurs while reading from the given URL, or
     *         if a configuration-file format error is detected
     *
     */
    private Iterator<String> parse(Class<?> service, URL u)
        throws ServiceConfigurationError
    {
        ArrayList<String> names = new ArrayList<>();
        try {
            URLConnection uc = u.openConnection();
            uc.setUseCaches(false);
            try (InputStream in = uc.getInputStream();
                 BufferedReader r
                     = new BufferedReader(new InputStreamReader(in, "utf-8")))
            {
                int lc = 1;
                while ((lc = parseLine(service, u, r, lc, names)) >= 0);
            }
        } catch (IOException x) {
            fail(service, "Error accessing configuration file", x);
        }
        return names.iterator();
    }

    /**
     * Returns the {@code Constructor} to instantiate the service provider.
     * The constructor has its accessible flag set so that the access check
     * is suppressed when instantiating the provider. This is necessary
     * because newInstance is a caller sensitive method and ServiceLoader
     * is instantiating the service provider on behalf of the service
     * consumer.
     */
    private static Constructor<?> checkAndGetConstructor(Class<?> c)
        throws NoSuchMethodException, IllegalAccessException
    {
        Constructor<?> ctor = c.getConstructor();

        // check class and no-arg constructor are public
        int modifiers = ctor.getModifiers();
        if (!Modifier.isPublic(Reflection.getClassAccessFlags(c) & modifiers)) {
            String cn = c.getName();
            throw new IllegalAccessException(cn + " is not public");
        }

        // return Constructor to create the service implementation
        PrivilegedAction<Void> action = new PrivilegedAction<Void>() {
            public Void run() { ctor.setAccessible(true); return null; }
        };
        AccessController.doPrivileged(action);
        return ctor;
    }

    /**
     * Uses Class.forName to load a class in a module.
     */
    private static Class<?> loadClassInModule(Module module, String cn) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return Class.forName(module, cn);
        } else {
            PrivilegedAction<Class<?>> pa = () -> Class.forName(module, cn);
            return AccessController.doPrivileged(pa);
        }
    }

    /**
     * An Iterator that runs the next and hasNext methods with permissions
     * restricted by the {@code AccessControlContext} obtained when the
     * ServiceLoader was created.
     */
    private abstract class RestrictedIterator<S>
        implements Iterator<S>
    {
        /**
         * Returns {@code true} if the iteration has more elements.
         */
        abstract boolean hasNextService();

        /**
         * Returns the next element in the iteration
         */
        abstract S nextService();

        public final boolean hasNext() {
            if (acc == null) {
                return hasNextService();
            } else {
                PrivilegedAction<Boolean> action = new PrivilegedAction<Boolean>() {
                    public Boolean run() { return hasNextService(); }
                };
                return AccessController.doPrivileged(action, acc);
            }
        }

        public final S next() {
            if (acc == null) {
                return nextService();
            } else {
                PrivilegedAction<S> action = new PrivilegedAction<S>() {
                    public S run() { return nextService(); }
                };
                return AccessController.doPrivileged(action, acc);
            }
        }
    }

    /**
     * Implements lazy service provider lookup of service providers that
     * are provided by modules in a module Layer.
     *
     * For now, this iterator examines all modules in each Layer. This will
     * be replaced once we decide on how the service-use graph is exposed
     * in the module API.
     */
    private class LayerLookupIterator
        extends RestrictedIterator<S>
    {
        final String serviceName;
        Layer currentLayer;
        Iterator<ModuleDescriptor> descriptorIterator;
        Iterator<String> providersIterator;

        Module nextModule;
        String nextProvider;

        LayerLookupIterator() {
            serviceName = service.getName();
            currentLayer = layer;

            // need to get us started
            descriptorIterator = descriptors(layer, serviceName);
        }

        Iterator<ModuleDescriptor> descriptors(Layer layer, String service) {
            return layer.modules().stream()
                    .map(Module::getDescriptor)
                    .filter(d -> d.provides().get(service) != null)
                    .iterator();
        }

        @Override
        boolean hasNextService() {

            // already have the next provider cached
            if (nextProvider != null)
                return true;

            while (true) {

                // next provider
                if (providersIterator != null && providersIterator.hasNext()) {
                    nextProvider = providersIterator.next();
                    return true;
                }

                // next descriptor
                if (descriptorIterator.hasNext()) {
                    ModuleDescriptor descriptor = descriptorIterator.next();

                    nextModule = currentLayer.findModule(descriptor.name()).get();

                    Provides provides = descriptor.provides().get(serviceName);
                    providersIterator = provides.providers().iterator();

                    continue;
                }

                // next layer
                Layer parent = currentLayer.parent().orElse(null);
                if (parent == null)
                    return false;

                currentLayer = parent;
                descriptorIterator = descriptors(currentLayer, serviceName);
            }
        }

        @Override
        S nextService() {
            if (!hasNextService())
                throw new NoSuchElementException();

            assert nextModule != null && nextProvider != null;

            String cn = nextProvider;
            nextProvider = null;

            // attempt to load the provider
            Class<?> c = loadClassInModule(nextModule, cn);
            if (c == null)
                fail(service, "Provider " + cn  + " not found");
            if (!service.isAssignableFrom(c))
                fail(service, "Provider " + cn  + " not a subtype");

            // instantiate the provider
            S p = null;
            try {
                Constructor<?> ctor = checkAndGetConstructor(c);
                p = service.cast(ctor.newInstance());
            } catch (Throwable x) {
                if (x instanceof InvocationTargetException)
                    x = x.getCause();
                fail(service,
                        "Provider " + cn + " could not be instantiated", x);
            }

            // add to cached provider list
            providers.add(p);

            return p;
        }
    }

    /**
     * Implements lazy service provider lookup of service providers that
     * are provided by modules defined to a class loader.
     */
    private class ModuleServicesIterator
        extends RestrictedIterator<S>
    {
        final JavaLangAccess langAccess = SharedSecrets.getJavaLangAccess();

        ClassLoader currentLoader;
        Iterator<ServiceProvider> iterator;
        ServiceProvider nextProvider;

        ModuleServicesIterator() {
            this.currentLoader = loader;
            this.iterator = iteratorFor(loader);
        }

        /**
         * Returns an iterator to iterate over the implementations of {@code
         * service} in modules defined to the given class loader.
         */
        private Iterator<ServiceProvider> iteratorFor(ClassLoader loader) {

            // if the class loader is in a loader pool then return an Iterator
            // that iterates over all service providers in the pool that provide
            // an implementation of the service
            if (currentLoader instanceof Loader) {
                LoaderPool pool = ((Loader) loader).pool();
                if (pool != null) {
                    return pool.loaders()
                            .map(l -> langAccess.getServicesCatalog(l))
                            .filter(sc -> sc != null)
                            .map(sc -> sc.findServices(service.getName()))
                            .flatMap(Set::stream)
                            .iterator();
                }
            }

            ServicesCatalog catalog;
            if (currentLoader == null) {
                catalog = BootLoader.getServicesCatalog();
            } else {
                catalog = langAccess.getServicesCatalog(currentLoader);
            }
            if (catalog == null) {
                return Collections.emptyIterator();
            } else {
                return catalog.findServices(service.getName()).iterator();
            }
        }

        @Override
        boolean hasNextService() {
            // already have the next provider cached
            if (nextProvider != null)
                return true;

            while (true) {
                if (iterator.hasNext()) {
                    nextProvider = iterator.next();
                    return true;
                }

                // move to the next class loader if possible
                if (currentLoader == null) {
                    return false;
                } else {
                    currentLoader = currentLoader.getParent();
                    iterator = iteratorFor(currentLoader);
                }
            }
        }

        @Override
        S nextService() {
            if (!hasNextService())
                throw new NoSuchElementException();

            ServiceProvider provider = nextProvider;
            nextProvider = null;

            // attempt to load the provider
            Module module = provider.module();
            String cn = provider.providerName();

            Class<?> c = loadClassInModule(module, cn);
            if (c == null) {
                fail(service,
                    "Provider " + cn + " not found in " + module.getName());
            }
            if (!service.isAssignableFrom(c)) {
                fail(service, "Provider " + cn  + " not a subtype");
            }

            // instantiate the provider
            S p = null;
            try {
                Constructor<?> ctor = checkAndGetConstructor(c);
                p = service.cast(ctor.newInstance());
            } catch (Throwable x) {
                if (x instanceof InvocationTargetException)
                    x = x.getCause();
                fail(service,
                    "Provider " + cn + " could not be instantiated", x);
            }

            // add to provider list
            providers.add(p);

            // record the class name of the service provider, this is
            // needed for cases where there a module has both a "uses"
            // and a services configuration file listing the same
            // provider
            providerNames.add(cn);

            return p;
        }
    }

    /**
     * Implements lazy service provider lookup where the service providers
     * are configured via service configuration files.
     */
    private class LazyClassPathIterator
        extends RestrictedIterator<S>
    {
        Enumeration<URL> configs;
        Iterator<String> pending;
        String nextName;

        @Override
        boolean hasNextService() {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    String fullName = PREFIX + service.getName();
                    if (loader == null)
                        configs = ClassLoader.getSystemResources(fullName);
                    else
                        configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, "Error locating configuration files", x);
                }
            }
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                pending = parse(service, configs.nextElement());
            }
            nextName = pending.next();
            return true;
        }

        @Override
        S nextService() {
            if (!hasNextService())
                throw new NoSuchElementException();
            String cn = nextName;
            nextName = null;
            Class<?> c = null;
            try {
                c = Class.forName(cn, false, loader);
            } catch (ClassNotFoundException x) {
                fail(service,
                     "Provider " + cn + " not found");
            }
            if (!service.isAssignableFrom(c)) {
                fail(service,
                     "Provider " + cn  + " not a subtype");
            }
            S p = null;
            try {
                p = service.cast(c.newInstance());
            } catch (Throwable x) {
                fail(service,
                     "Provider " + cn + " could not be instantiated",
                     x);
            }
            providers.add(p);
            providerNames.add(cn);
            return p;
        }
    }

    /**
     * Lazily loads the available providers of this loader's service.
     *
     * <p> The iterator returned by this method first yields all of the
     * elements of the provider cache, in instantiation order.  It then lazily
     * loads and instantiates any remaining providers, adding each one to the
     * cache in turn.
     *
     * <p> To achieve laziness the actual work of locating and instantiating
     * providers must be done by the iterator itself. Its {@link
     * java.util.Iterator#hasNext hasNext} and {@link java.util.Iterator#next
     * next} methods can therefore throw a {@link ServiceConfigurationError}
     * if a provider class cannot be loaded, doesn't have the appropriate
     * constructor, can't be assigned to the service type or if any other kind
     * of exception or error is thrown as the next provider is located and
     * instantiated. To write robust code it is only necessary to catch {@link
     * ServiceConfigurationError} when using a service iterator.
     *
     * <p> If such an error is thrown then subsequent invocations of the
     * iterator will make a best effort to locate and instantiate the next
     * available provider, but in general such recovery cannot be guaranteed.
     *
     * <blockquote style="font-size: smaller; line-height: 1.2"><span
     * style="padding-right: 1em; font-weight: bold">Design Note</span>
     * Throwing an error in these cases may seem extreme.  The rationale for
     * this behavior is that a malformed provider-configuration file, like a
     * malformed class file, indicates a serious problem with the way the Java
     * virtual machine is configured or is being used.  As such it is
     * preferable to throw an error rather than try to recover or, even worse,
     * fail silently.</blockquote>
     *
     * <p> If this loader's provider cache is cleared by invoking the {@link
     * #reload() reload} method then existing iterators for this service
     * loader should be discarded.
     * The {@link java.util.Iterator#hasNext() hasNext} and {@link
     * java.util.Iterator#next() next} methods of the iterator throw {@link
     * java.util.ConcurrentModificationException ConcurrentModificationException}
     * if used after the provider cache has been cleared.
     *
     * <p> The iterator returned by this method does not support removal.
     * Invoking its {@link java.util.Iterator#remove() remove} method will
     * cause an {@link UnsupportedOperationException} to be thrown.
     *
     * @implNote When adding providers to the cache, the {@link #iterator
     * Iterator} processes resources in the order that the {@link
     * java.lang.ClassLoader#getResources(java.lang.String)
     * ClassLoader.getResources(String)} method finds the service configuration
     * files.
     *
     * @return  An iterator that lazily loads providers for this loader's
     *          service
     */
    public Iterator<S> iterator() {
        return new Iterator<S>() {

            // record reload count
            final int expectedReloadCount = ServiceLoader.this.reloadCount;

            // index into the cached providers list
            int index;

            /**
             * Throws ConcurrentModificationException if the list of cached
             * providers has been cleared by reload.
             */
            private void checkReloadCount() {
                if (ServiceLoader.this.reloadCount != expectedReloadCount)
                    throw new ConcurrentModificationException();
            }

            public boolean hasNext() {
                checkReloadCount();
                if (index < providers.size())
                    return true;

                if (layerLookupIterator != null) {
                    return layerLookupIterator.hasNext();
                } else {
                    return moduleServicesIterator.hasNext() ||
                            lazyClassPathIterator.hasNext();
                }
            }

            public S next() {
                checkReloadCount();
                S next;
                if (index < providers.size()) {
                    next = providers.get(index);
                } else {
                    if (layerLookupIterator != null) {
                        next = layerLookupIterator.next();
                    } else {
                        if (moduleServicesIterator.hasNext()) {
                            next = moduleServicesIterator.next();
                        } else {
                            next = lazyClassPathIterator.next();
                        }
                    }
                }
                index++;
                return next;
            }

        };
    }

    /**
     * Creates a new service loader for the given service type, class
     * loader, and caller.
     *
     * @param  <S> the class of the service type
     *
     * @param  service
     *         The interface or abstract class representing the service
     *
     * @param  loader
     *         The class loader to be used to load provider-configuration files
     *         and provider classes, or <tt>null</tt> if the system class
     *         loader (or, failing that, the bootstrap class loader) is to be
     *         used
     *
     * @param  callerModule
     *         The caller's module for which a new service loader is created
     *
     * @return A new service loader
     */
    static <S> ServiceLoader<S> load(Class<S> service,
                                     ClassLoader loader,
                                     Module callerModule)
    {
        return new ServiceLoader<>(callerModule, service, loader);
    }

    /**
     * Creates a new service loader for the given service type and class
     * loader.
     *
     * @param  <S> the class of the service type
     *
     * @param  service
     *         The interface or abstract class representing the service
     *
     * @param  loader
     *         The class loader to be used to load provider-configuration files
     *         and provider classes, or {@code null} if the system class
     *         loader (or, failing that, the bootstrap class loader) is to be
     *         used
     *
     * @return A new service loader
     *
     * @throws ServiceConfigurationError
     *         if the service type is not accessible to the caller or the
     *         caller is in a named module and its module descriptor does
     *         not declare that it uses {@code service}
     */
    @CallerSensitive
    public static <S> ServiceLoader<S> load(Class<S> service,
                                            ClassLoader loader)
    {
        return new ServiceLoader<>(Reflection.getCallerClass(), service, loader);
    }

    /**
     * Creates a new service loader for the given service type, using the
     * current thread's {@linkplain java.lang.Thread#getContextClassLoader
     * context class loader}.
     *
     * <p> An invocation of this convenience method of the form
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>)</pre></blockquote>
     *
     * is equivalent to
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>,
     *                    Thread.currentThread().getContextClassLoader())</pre></blockquote>
     *
     * @param  <S> the class of the service type
     *
     * @param  service
     *         The interface or abstract class representing the service
     *
     * @return A new service loader
     *
     * @throws ServiceConfigurationError
     *         if the service type is not accessible to the caller or the
     *         caller is in a named module and its module descriptor does
     *         not declare that it uses {@code service}
     */
    @CallerSensitive
    public static <S> ServiceLoader<S> load(Class<S> service) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return new ServiceLoader<>(Reflection.getCallerClass(), service, cl);
    }

    /**
     * Creates a new service loader for the given service type, using the
     * {@linkplain ClassLoader#getPlatformClassLoader() platform class loader}.
     *
     * <p> This convenience method is equivalent to: </p>
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>, <i>ClassLoader.getPlatformClassLoader())</i>
     * </pre></blockquote>
     *
     * <p> This method is intended for use when only installed providers are
     * desired.  The resulting service will only find and load providers that
     * have been installed into the current Java virtual machine; providers on
     * the application's module path or class path will be ignored.
     *
     * @param  <S> the class of the service type
     *
     * @param  service
     *         The interface or abstract class representing the service
     *
     * @return A new service loader
     *
     * @throws ServiceConfigurationError
     *         if the service type is not accessible to the caller or the
     *         caller is in a named module and its module descriptor does
     *         not declare that it uses {@code service}
     */
    @CallerSensitive
    public static <S> ServiceLoader<S> loadInstalled(Class<S> service) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        ClassLoader prev = null;
        while (cl != null) {
            prev = cl;
            cl = cl.getParent();
        }
        return new ServiceLoader<>(Reflection.getCallerClass(), service, prev);
    }

    /**
     * Creates a new service loader for the given service type that loads
     * service providers from modules in the given {@code Layer} and its
     * ancestors.
     *
     * @apiNote Unlike the other load methods defined here, the service type
     * is the second parameter. The reason for this is to avoid source
     * compatibility issues for code that uses {@code load(S, null)}.
     *
     * @param  <S> the class of the service type
     *
     * @param  layer
     *         The module Layer
     *
     * @param  service
     *         The interface or abstract class representing the service
     *
     * @return A new service loader
     *
     * @throws ServiceConfigurationError
     *         if the service type is not accessible to the caller or the
     *         caller is in a named module and its module descriptor does
     *         not declare that it uses {@code service}
     *
     * @since 9
     */
    @CallerSensitive
    public static <S> ServiceLoader<S> load(Layer layer, Class<S> service) {
        return new ServiceLoader<>(Reflection.getCallerClass(),
                                   Objects.requireNonNull(layer),
                                   Objects.requireNonNull(service));
    }

    /**
     * Returns a string describing this service.
     *
     * @return  A descriptive string
     */
    public String toString() {
        return "java.util.ServiceLoader[" + service.getName() + "]";
    }

}
