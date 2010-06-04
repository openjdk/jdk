/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.ServiceLoader;

/**
 * A registry for service provider instances.
 *
 * <p> A <i>service</i> is a well-known set of interfaces and (usually
 * abstract) classes.  A <i>service provider</i> is a specific
 * implementation of a service.  The classes in a provider typically
 * implement the interface or subclass the class defined by the
 * service itself.
 *
 * <p> Service providers are stored in one or more <i>categories</i>,
 * each of which is defined by a class of interface (described by a
 * <code>Class</code> object) that all of its members must implement.
 * The set of categories may be changed dynamically.
 *
 * <p> Only a single instance of a given leaf class (that is, the
 * actual class returned by <code>getClass()</code>, as opposed to any
 * inherited classes or interfaces) may be registered.  That is,
 * suppose that the
 * <code>com.mycompany.mypkg.GreenServiceProvider</code> class
 * implements the <code>com.mycompany.mypkg.MyService</code>
 * interface.  If a <code>GreenServiceProvider</code> instance is
 * registered, it will be stored in the category defined by the
 * <code>MyService</code> class.  If a new instance of
 * <code>GreenServiceProvider</code> is registered, it will replace
 * the previous instance.  In practice, service provider objects are
 * usually singletons so this behavior is appropriate.
 *
 * <p> To declare a service provider, a <code>services</code>
 * subdirectory is placed within the <code>META-INF</code> directory
 * that is present in every JAR file.  This directory contains a file
 * for each service provider interface that has one or more
 * implementation classes present in the JAR file.  For example, if
 * the JAR file contained a class named
 * <code>com.mycompany.mypkg.MyServiceImpl</code> which implements the
 * <code>javax.someapi.SomeService</code> interface, the JAR file
 * would contain a file named: <pre>
 * META-INF/services/javax.someapi.SomeService </pre>
 *
 * containing the line:
 *
 * <pre>
 * com.mycompany.mypkg.MyService
 * </pre>
 *
 * <p> The service provider classes should be to be lightweight and
 * quick to load.  Implementations of these interfaces should avoid
 * complex dependencies on other classes and on native code. The usual
 * pattern for more complex services is to register a lightweight
 * proxy for the heavyweight service.
 *
 * <p> An application may customize the contents of a registry as it
 * sees fit, so long as it has the appropriate runtime permission.
 *
 * <p> For more details on declaring service providers, and the JAR
 * format in general, see the <a
 * href="../../../../technotes/guides/jar/jar.html">
 * JAR File Specification</a>.
 *
 * @see RegisterableService
 *
 */
public class ServiceRegistry {

    // Class -> Registry
    private Map categoryMap = new HashMap();

    /**
     * Constructs a <code>ServiceRegistry</code> instance with a
     * set of categories taken from the <code>categories</code>
     * argument.
     *
     * @param categories an <code>Iterator</code> containing
     * <code>Class</code> objects to be used to define categories.
     *
     * @exception IllegalArgumentException if
     * <code>categories</code> is <code>null</code>.
     */
    public ServiceRegistry(Iterator<Class<?>> categories) {
        if (categories == null) {
            throw new IllegalArgumentException("categories == null!");
        }
        while (categories.hasNext()) {
            Class category = (Class)categories.next();
            SubRegistry reg = new SubRegistry(this, category);
            categoryMap.put(category, reg);
        }
    }

    // The following two methods expose functionality from
    // sun.misc.Service.  If that class is made public, they may be
    // removed.
    //
    // The sun.misc.ServiceConfigurationError class may also be
    // exposed, in which case the references to 'an
    // <code>Error</code>' below should be changed to 'a
    // <code>ServiceConfigurationError</code>'.

    /**
     * Searches for implementations of a particular service class
     * using the given class loader.
     *
     * <p> This method transforms the name of the given service class
     * into a provider-configuration filename as described in the
     * class comment and then uses the <code>getResources</code>
     * method of the given class loader to find all available files
     * with that name.  These files are then read and parsed to
     * produce a list of provider-class names.  The iterator that is
     * returned uses the given class loader to look up and then
     * instantiate each element of the list.
     *
     * <p> Because it is possible for extensions to be installed into
     * a running Java virtual machine, this method may return
     * different results each time it is invoked.
     *
     * @param providerClass a <code>Class</code>object indicating the
     * class or interface of the service providers being detected.
     *
     * @param loader the class loader to be used to load
     * provider-configuration files and instantiate provider classes,
     * or <code>null</code> if the system class loader (or, failing that
     * the bootstrap class loader) is to be used.
     *
     * @return An <code>Iterator</code> that yields provider objects
     * for the given service, in some arbitrary order.  The iterator
     * will throw an <code>Error</code> if a provider-configuration
     * file violates the specified format or if a provider class
     * cannot be found and instantiated.
     *
     * @exception IllegalArgumentException if
     * <code>providerClass</code> is <code>null</code>.
     */
    public static <T> Iterator<T> lookupProviders(Class<T> providerClass,
                                                  ClassLoader loader)
    {
        if (providerClass == null) {
            throw new IllegalArgumentException("providerClass == null!");
        }
        return ServiceLoader.load(providerClass, loader).iterator();
    }

    /**
     * Locates and incrementally instantiates the available providers
     * of a given service using the context class loader.  This
     * convenience method is equivalent to:
     *
     * <pre>
     *   ClassLoader cl = Thread.currentThread().getContextClassLoader();
     *   return Service.providers(service, cl);
     * </pre>
     *
     * @param providerClass a <code>Class</code>object indicating the
     * class or interface of the service providers being detected.
     *
     * @return An <code>Iterator</code> that yields provider objects
     * for the given service, in some arbitrary order.  The iterator
     * will throw an <code>Error</code> if a provider-configuration
     * file violates the specified format or if a provider class
     * cannot be found and instantiated.
     *
     * @exception IllegalArgumentException if
     * <code>providerClass</code> is <code>null</code>.
     */
    public static <T> Iterator<T> lookupProviders(Class<T> providerClass) {
        if (providerClass == null) {
            throw new IllegalArgumentException("providerClass == null!");
        }
        return ServiceLoader.load(providerClass).iterator();
    }

    /**
     * Returns an <code>Iterator</code> of <code>Class</code> objects
     * indicating the current set of categories.  The iterator will be
     * empty if no categories exist.
     *
     * @return an <code>Iterator</code> containing
     * <code>Class</code>objects.
     */
    public Iterator<Class<?>> getCategories() {
        Set keySet = categoryMap.keySet();
        return keySet.iterator();
    }

    /**
     * Returns an Iterator containing the subregistries to which the
     * provider belongs.
     */
    private Iterator getSubRegistries(Object provider) {
        List l = new ArrayList();
        Iterator iter = categoryMap.keySet().iterator();
        while (iter.hasNext()) {
            Class c = (Class)iter.next();
            if (c.isAssignableFrom(provider.getClass())) {
                l.add((SubRegistry)categoryMap.get(c));
            }
        }
        return l.iterator();
    }

    /**
     * Adds a service provider object to the registry.  The provider
     * is associated with the given category.
     *
     * <p> If <code>provider</code> implements the
     * <code>RegisterableService</code> interface, its
     * <code>onRegistration</code> method will be called.  Its
     * <code>onDeregistration</code> method will be called each time
     * it is deregistered from a category, for example if a
     * category is removed or the registry is garbage collected.
     *
     * @param provider the service provide object to be registered.
     * @param category the category under which to register the
     * provider.
     *
     * @return true if no provider of the same class was previously
     * registered in the same category category.
     *
     * @exception IllegalArgumentException if <code>provider</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     * @exception ClassCastException if provider does not implement
     * the <code>Class</code> defined by <code>category</code>.
     */
    public <T> boolean registerServiceProvider(T provider,
                                               Class<T> category) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        if (!category.isAssignableFrom(provider.getClass())) {
            throw new ClassCastException();
        }

        return reg.registerServiceProvider(provider);
    }

    /**
     * Adds a service provider object to the registry.  The provider
     * is associated within each category present in the registry
     * whose <code>Class</code> it implements.
     *
     * <p> If <code>provider</code> implements the
     * <code>RegisterableService</code> interface, its
     * <code>onRegistration</code> method will be called once for each
     * category it is registered under.  Its
     * <code>onDeregistration</code> method will be called each time
     * it is deregistered from a category or when the registry is
     * finalized.
     *
     * @param provider the service provider object to be registered.
     *
     * @exception IllegalArgumentException if
     * <code>provider</code> is <code>null</code>.
     */
    public void registerServiceProvider(Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        Iterator regs = getSubRegistries(provider);
        while (regs.hasNext()) {
            SubRegistry reg = (SubRegistry)regs.next();
            reg.registerServiceProvider(provider);
        }
    }

    /**
     * Adds a set of service provider objects, taken from an
     * <code>Iterator</code> to the registry.  Each provider is
     * associated within each category present in the registry whose
     * <code>Class</code> it implements.
     *
     * <p> For each entry of <code>providers</code> that implements
     * the <code>RegisterableService</code> interface, its
     * <code>onRegistration</code> method will be called once for each
     * category it is registered under.  Its
     * <code>onDeregistration</code> method will be called each time
     * it is deregistered from a category or when the registry is
     * finalized.
     *
     * @param providers an Iterator containing service provider
     * objects to be registered.
     *
     * @exception IllegalArgumentException if <code>providers</code>
     * is <code>null</code> or contains a <code>null</code> entry.
     */
    public void registerServiceProviders(Iterator<?> providers) {
        if (providers == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        while (providers.hasNext()) {
            registerServiceProvider(providers.next());
        }
    }

    /**
     * Removes a service provider object from the given category.  If
     * the provider was not previously registered, nothing happens and
     * <code>false</code> is returned.  Otherwise, <code>true</code>
     * is returned.  If an object of the same class as
     * <code>provider</code> but not equal (using <code>==</code>) to
     * <code>provider</code> is registered, it will not be
     * deregistered.
     *
     * <p> If <code>provider</code> implements the
     * <code>RegisterableService</code> interface, its
     * <code>onDeregistration</code> method will be called.
     *
     * @param provider the service provider object to be deregistered.
     * @param category the category from which to deregister the
     * provider.
     *
     * @return <code>true</code> if the provider was previously
     * registered in the same category category,
     * <code>false</code> otherwise.
     *
     * @exception IllegalArgumentException if <code>provider</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     * @exception ClassCastException if provider does not implement
     * the class defined by <code>category</code>.
     */
    public <T> boolean deregisterServiceProvider(T provider,
                                                 Class<T> category) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        if (!category.isAssignableFrom(provider.getClass())) {
            throw new ClassCastException();
        }
        return reg.deregisterServiceProvider(provider);
    }

    /**
     * Removes a service provider object from all categories that
     * contain it.
     *
     * @param provider the service provider object to be deregistered.
     *
     * @exception IllegalArgumentException if <code>provider</code> is
     * <code>null</code>.
     */
    public void deregisterServiceProvider(Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        Iterator regs = getSubRegistries(provider);
        while (regs.hasNext()) {
            SubRegistry reg = (SubRegistry)regs.next();
            reg.deregisterServiceProvider(provider);
        }
    }

    /**
     * Returns <code>true</code> if <code>provider</code> is currently
     * registered.
     *
     * @param provider the service provider object to be queried.
     *
     * @return <code>true</code> if the given provider has been
     * registered.
     *
     * @exception IllegalArgumentException if <code>provider</code> is
     * <code>null</code>.
     */
    public boolean contains(Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null!");
        }
        Iterator regs = getSubRegistries(provider);
        while (regs.hasNext()) {
            SubRegistry reg = (SubRegistry)regs.next();
            if (reg.contains(provider)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns an <code>Iterator</code> containing all registered
     * service providers in the given category.  If
     * <code>useOrdering</code> is <code>false</code>, the iterator
     * will return all of the server provider objects in an arbitrary
     * order.  Otherwise, the ordering will respect any pairwise
     * orderings that have been set.  If the graph of pairwise
     * orderings contains cycles, any providers that belong to a cycle
     * will not be returned.
     *
     * @param category the category to be retrieved from.
     * @param useOrdering <code>true</code> if pairwise orderings
     * should be taken account in ordering the returned objects.
     *
     * @return an <code>Iterator</code> containing service provider
     * objects from the given category, possibly in order.
     *
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     */
    public <T> Iterator<T> getServiceProviders(Class<T> category,
                                               boolean useOrdering) {
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        return reg.getServiceProviders(useOrdering);
    }

    /**
     * A simple filter interface used by
     * <code>ServiceRegistry.getServiceProviders</code> to select
     * providers matching an arbitrary criterion.  Classes that
     * implement this interface should be defined in order to make use
     * of the <code>getServiceProviders</code> method of
     * <code>ServiceRegistry</code> that takes a <code>Filter</code>.
     *
     * @see ServiceRegistry#getServiceProviders(Class, ServiceRegistry.Filter, boolean)
     */
    public interface Filter {

        /**
         * Returns <code>true</code> if the given
         * <code>provider</code> object matches the criterion defined
         * by this <code>Filter</code>.
         *
         * @param provider a service provider <code>Object</code>.
         *
         * @return true if the provider matches the criterion.
         */
        boolean filter(Object provider);
    }

    /**
     * Returns an <code>Iterator</code> containing service provider
     * objects within a given category that satisfy a criterion
     * imposed by the supplied <code>ServiceRegistry.Filter</code>
     * object's <code>filter</code> method.
     *
     * <p> The <code>useOrdering</code> argument controls the
     * ordering of the results using the same rules as
     * <code>getServiceProviders(Class, boolean)</code>.
     *
     * @param category the category to be retrieved from.
     * @param filter an instance of <code>ServiceRegistry.Filter</code>
     * whose <code>filter</code> method will be invoked.
     * @param useOrdering <code>true</code> if pairwise orderings
     * should be taken account in ordering the returned objects.
     *
     * @return an <code>Iterator</code> containing service provider
     * objects from the given category, possibly in order.
     *
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     */
    public <T> Iterator<T> getServiceProviders(Class<T> category,
                                               Filter filter,
                                               boolean useOrdering) {
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        Iterator iter = getServiceProviders(category, useOrdering);
        return new FilterIterator(iter, filter);
    }

    /**
     * Returns the currently registered service provider object that
     * is of the given class type.  At most one object of a given
     * class is allowed to be registered at any given time.  If no
     * registered object has the desired class type, <code>null</code>
     * is returned.
     *
     * @param providerClass the <code>Class</code> of the desired
     * service provider object.
     *
     * @return a currently registered service provider object with the
     * desired <code>Class</code>type, or <code>null</code> is none is
     * present.
     *
     * @exception IllegalArgumentException if <code>providerClass</code> is
     * <code>null</code>.
     */
    public <T> T getServiceProviderByClass(Class<T> providerClass) {
        if (providerClass == null) {
            throw new IllegalArgumentException("providerClass == null!");
        }
        Iterator iter = categoryMap.keySet().iterator();
        while (iter.hasNext()) {
            Class c = (Class)iter.next();
            if (c.isAssignableFrom(providerClass)) {
                SubRegistry reg = (SubRegistry)categoryMap.get(c);
                T provider = reg.getServiceProviderByClass(providerClass);
                if (provider != null) {
                    return provider;
                }
            }
        }
        return null;
    }

    /**
     * Sets a pairwise ordering between two service provider objects
     * within a given category.  If one or both objects are not
     * currently registered within the given category, or if the
     * desired ordering is already set, nothing happens and
     * <code>false</code> is returned.  If the providers previously
     * were ordered in the reverse direction, that ordering is
     * removed.
     *
     * <p> The ordering will be used by the
     * <code>getServiceProviders</code> methods when their
     * <code>useOrdering</code> argument is <code>true</code>.
     *
     * @param category a <code>Class</code> object indicating the
     * category under which the preference is to be established.
     * @param firstProvider the preferred provider.
     * @param secondProvider the provider to which
     * <code>firstProvider</code> is preferred.
     *
     * @return <code>true</code> if a previously unset ordering
     * was established.
     *
     * @exception IllegalArgumentException if either provider is
     * <code>null</code> or they are the same object.
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     */
    public <T> boolean setOrdering(Class<T> category,
                                   T firstProvider,
                                   T secondProvider) {
        if (firstProvider == null || secondProvider == null) {
            throw new IllegalArgumentException("provider is null!");
        }
        if (firstProvider == secondProvider) {
            throw new IllegalArgumentException("providers are the same!");
        }
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        if (reg.contains(firstProvider) &&
            reg.contains(secondProvider)) {
            return reg.setOrdering(firstProvider, secondProvider);
        }
        return false;
    }

    /**
     * Sets a pairwise ordering between two service provider objects
     * within a given category.  If one or both objects are not
     * currently registered within the given category, or if no
     * ordering is currently set between them, nothing happens
     * and <code>false</code> is returned.
     *
     * <p> The ordering will be used by the
     * <code>getServiceProviders</code> methods when their
     * <code>useOrdering</code> argument is <code>true</code>.
     *
     * @param category a <code>Class</code> object indicating the
     * category under which the preference is to be disestablished.
     * @param firstProvider the formerly preferred provider.
     * @param secondProvider the provider to which
     * <code>firstProvider</code> was formerly preferred.
     *
     * @return <code>true</code> if a previously set ordering was
     * disestablished.
     *
     * @exception IllegalArgumentException if either provider is
     * <code>null</code> or they are the same object.
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     */
    public <T> boolean unsetOrdering(Class<T> category,
                                     T firstProvider,
                                     T secondProvider) {
        if (firstProvider == null || secondProvider == null) {
            throw new IllegalArgumentException("provider is null!");
        }
        if (firstProvider == secondProvider) {
            throw new IllegalArgumentException("providers are the same!");
        }
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        if (reg.contains(firstProvider) &&
            reg.contains(secondProvider)) {
            return reg.unsetOrdering(firstProvider, secondProvider);
        }
        return false;
    }

    /**
     * Deregisters all service provider object currently registered
     * under the given category.
     *
     * @param category the category to be emptied.
     *
     * @exception IllegalArgumentException if there is no category
     * corresponding to <code>category</code>.
     */
    public void deregisterAll(Class<?> category) {
        SubRegistry reg = (SubRegistry)categoryMap.get(category);
        if (reg == null) {
            throw new IllegalArgumentException("category unknown!");
        }
        reg.clear();
    }

    /**
     * Deregisters all currently registered service providers from all
     * categories.
     */
    public void deregisterAll() {
        Iterator iter = categoryMap.values().iterator();
        while (iter.hasNext()) {
            SubRegistry reg = (SubRegistry)iter.next();
            reg.clear();
        }
    }

    /**
     * Finalizes this object prior to garbage collection.  The
     * <code>deregisterAll</code> method is called to deregister all
     * currently registered service providers.  This method should not
     * be called from application code.
     *
     * @exception Throwable if an error occurs during superclass
     * finalization.
     */
    public void finalize() throws Throwable {
        deregisterAll();
        super.finalize();
    }
}


/**
 * A portion of a registry dealing with a single superclass or
 * interface.
 */
class SubRegistry {

    ServiceRegistry registry;

    Class category;

    // Provider Objects organized by partial oridering
    PartiallyOrderedSet poset = new PartiallyOrderedSet();

    // Class -> Provider Object of that class
    Map<Class<?>,Object> map = new HashMap();

    public SubRegistry(ServiceRegistry registry, Class category) {
        this.registry = registry;
        this.category = category;
    }

    public boolean registerServiceProvider(Object provider) {
        Object oprovider = map.get(provider.getClass());
        boolean present =  oprovider != null;

        if (present) {
            deregisterServiceProvider(oprovider);
        }
        map.put(provider.getClass(), provider);
        poset.add(provider);
        if (provider instanceof RegisterableService) {
            RegisterableService rs = (RegisterableService)provider;
            rs.onRegistration(registry, category);
        }

        return !present;
    }

    /**
     * If the provider was not previously registered, do nothing.
     *
     * @return true if the provider was previously registered.
     */
    public boolean deregisterServiceProvider(Object provider) {
        Object oprovider = map.get(provider.getClass());

        if (provider == oprovider) {
            map.remove(provider.getClass());
            poset.remove(provider);
            if (provider instanceof RegisterableService) {
                RegisterableService rs = (RegisterableService)provider;
                rs.onDeregistration(registry, category);
            }

            return true;
        }
        return false;
    }

    public boolean contains(Object provider) {
        Object oprovider = map.get(provider.getClass());
        return oprovider == provider;
    }

    public boolean setOrdering(Object firstProvider,
                               Object secondProvider) {
        return poset.setOrdering(firstProvider, secondProvider);
    }

    public boolean unsetOrdering(Object firstProvider,
                                 Object secondProvider) {
        return poset.unsetOrdering(firstProvider, secondProvider);
    }

    public Iterator getServiceProviders(boolean useOrdering) {
        if (useOrdering) {
            return poset.iterator();
        } else {
            return map.values().iterator();
        }
    }

    public <T> T getServiceProviderByClass(Class<T> providerClass) {
        return (T)map.get(providerClass);
    }

    public void clear() {
        Iterator iter = map.values().iterator();
        while (iter.hasNext()) {
            Object provider = iter.next();
            iter.remove();

            if (provider instanceof RegisterableService) {
                RegisterableService rs = (RegisterableService)provider;
                rs.onDeregistration(registry, category);
            }
        }
        poset.clear();
    }

    public void finalize() {
        clear();
    }
}


/**
 * A class for wrapping <code>Iterators</code> with a filter function.
 * This provides an iterator for a subset without duplication.
 */
class FilterIterator<T> implements Iterator<T> {

    private Iterator<T> iter;
    private ServiceRegistry.Filter filter;

    private T next = null;

    public FilterIterator(Iterator<T> iter,
                          ServiceRegistry.Filter filter) {
        this.iter = iter;
        this.filter = filter;
        advance();
    }

    private void advance() {
        while (iter.hasNext()) {
            T elt = iter.next();
            if (filter.filter(elt)) {
                next = elt;
                return;
            }
        }

        next = null;
    }

    public boolean hasNext() {
        return next != null;
    }

    public T next() {
        if (next == null) {
            throw new NoSuchElementException();
        }
        T o = next;
        advance();
        return o;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
