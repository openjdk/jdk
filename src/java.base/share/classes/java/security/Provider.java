/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import jdk.internal.event.SecurityProviderServiceEvent;

import javax.crypto.KDFParameters;
import javax.security.auth.login.Configuration;
import java.io.*;
import java.security.cert.CertStoreParameters;
import java.util.*;
import static java.util.Locale.ENGLISH;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents a "provider" for the
 * Java Security API, where a provider implements some or all parts of
 * Java Security. Services that a provider may implement include:
 *
 * <ul>
 *
 * <li>Algorithms (such as DSA, RSA, or SHA-256).
 *
 * <li>Key generation, conversion, and management facilities (such as for
 * algorithm-specific keys).
 *
 * </ul>
 *
 * <p>Some provider implementations may encounter unrecoverable internal
 * errors during their operation, for example a failure to communicate with a
 * security token. A {@link ProviderException} should be used to indicate
 * such errors.
 *
 * <p>Please note that a provider can be used to implement any security
 * service in Java that uses a pluggable architecture with a choice
 * of implementations that fit underneath.
 *
 * <p>The service type {@code Provider} is reserved for use by the
 * security framework. Services of this type cannot be added, removed,
 * or modified by applications.
 * The following attributes are automatically placed in each Provider object:
 * <table class="striped">
 * <caption><b>Attributes Automatically Placed in a Provider Object</b></caption>
 * <thead>
 * <tr><th scope="col">Name</th><th scope="col">Value</th>
 * </thead>
 * <tbody style="text-align:left">
 * <tr><th scope="row">{@code Provider.id name}</th>
 *     <td>{@code String.valueOf(provider.getName())}</td>
 * <tr><th scope="row">{@code Provider.id version}</th>
 *     <td>{@code String.valueOf(provider.getVersionStr())}</td>
 * <tr><th scope="row">{@code Provider.id info}</th>
 *     <td>{@code String.valueOf(provider.getInfo())}</td>
 * <tr><th scope="row">{@code Provider.id className}</th>
 *     <td>{@code provider.getClass().getName()}</td>
 * </tbody>
 * </table>
 *
 * <p>Each provider has a name and a version string. A provider normally
 * identifies itself with a file named {@code java.security.Provider}
 * in the resource directory {@code META-INF/services}.
 * Security providers are looked up via the {@link ServiceLoader} mechanism
 * using the {@link ClassLoader#getSystemClassLoader application class loader}.
 *
 * <p>Providers may be configured such that they are automatically
 * installed and made available at runtime via the
 * {@link Security#getProviders() Security.getProviders()} method.
 * The mechanism for configuring and installing security providers is
 * implementation-specific.
 *
 * @implNote
 * The JDK implementation supports static registration of the security
 * providers via the {@code conf/security/java.security} file in the Java
 * installation directory. These providers are automatically installed by
 * the JDK runtime, see {@extLink security_guide_jca_provider
 * The Provider Class}
 * in the Java Cryptography Architecture (JCA) Reference Guide
 * for information about how a particular type of provider, the cryptographic
 * service provider, works and is installed.
 *
 * @author Benjamin Renaud
 * @author Andreas Sterbenz
 * @since 1.1
 */
public abstract class Provider extends Properties {

    // Declare serialVersionUID to be compatible with JDK1.1
    @java.io.Serial
    private static final long serialVersionUID = -4298000515446427739L;

    private static final sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance("provider", "Provider");

    /**
     * The provider name.
     *
     * @serial
     */
    private final String name;

    /**
     * A description of the provider and its services.
     *
     * @serial
     */
    private final String info;

    /**
     * The provider version number.
     *
     * @serial
     */
    private double version;

    /**
     * The provider version string.
     *
     * @serial
     */
    private String versionStr;

    private transient Set<Map.Entry<Object,Object>> entrySet = null;
    private transient int entrySetCallCount = 0;

    private transient boolean initialized;

    private static final Object[] EMPTY = new Object[0];

    private static double parseVersionStr(String s) {
        try {
            int firstDotIdx = s.indexOf('.');
            int nextDotIdx = s.indexOf('.', firstDotIdx + 1);
            if (nextDotIdx != -1) {
                s = s.substring(0, nextDotIdx);
            }
            int endIdx = s.indexOf('-');
            if (endIdx > 0) {
                s = s.substring(0, endIdx);
            }
            endIdx = s.indexOf('+');
            if (endIdx > 0) {
                s = s.substring(0, endIdx);
            }
            return Double.parseDouble(s);
        } catch (NullPointerException | NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * Constructs a {@code Provider} with the specified name, version number,
     * and information. Calling this constructor is equivalent to call the
     * {@link #Provider(String, String, String)} with {@code name}
     * name, {@code Double.toString(version)}, and {@code info}.
     *
     * @param name the provider name.
     *
     * @param version the provider version number.
     *
     * @param info a description of the provider and its services.
     *
     * @deprecated use {@link #Provider(String, String, String)} instead.
     */
    @Deprecated(since="9")
    @SuppressWarnings("this-escape")
    protected Provider(String name, double version, String info) {
        this.name = name;
        this.version = version;
        this.versionStr = Double.toString(version);
        this.info = info;
        this.servicesMap = new ServicesMap();
        this.prngAlgos = new LinkedHashSet<>(6);
        putId();
        initialized = true;
    }

    /**
     * Constructs a {@code Provider} with the specified name, version string,
     * and information.
     *
     * <p>The version string contains a version number optionally followed
     * by other information separated by one of the characters of '+', '-'.
     *
     * The format for the version number is:
     *
     * <blockquote><pre>
     *     ^[0-9]+(\.[0-9]+)*
     * </pre></blockquote>
     *
     * <p>In order to return the version number in a double, when there are
     * more than two components (separated by '.' as defined above), only
     * the first two components are retained. The resulting string is then
     * passed to {@link Double#valueOf(String)} to generate version number,
     * i.e. {@link #getVersion}.
     * <p>If the conversion failed, value 0 will be used.
     *
     * @param name the provider name.
     *
     * @param versionStr the provider version string.
     *
     * @param info a description of the provider and its services.
     *
     * @since 9
     */
    @SuppressWarnings("this-escape")
    protected Provider(String name, String versionStr, String info) {
        this.name = name;
        this.versionStr = versionStr;
        this.version = parseVersionStr(versionStr);
        this.info = info;
        this.servicesMap = new ServicesMap();
        this.prngAlgos = new LinkedHashSet<>(6);
        putId();
        initialized = true;
    }

    /**
     * Apply the supplied configuration argument to this {@code Provider}
     * instance and return the configured {@code Provider}. Note that if
     * this {@code Provider} cannot be configured in-place, a new
     * {@code Provider} will be created and returned. Therefore,
     * callers should always use the returned {@code Provider}.
     *
     * @implSpec
     * The default implementation throws {@code UnsupportedOperationException}.
     * Subclasses should override this method only if a configuration argument
     * is supported.
     *
     * @param configArg the configuration information for configuring this
     *         provider.
     *
     * @throws UnsupportedOperationException if a configuration argument is
     *         not supported.
     * @throws NullPointerException if the supplied configuration argument is
     *         {@code null}.
     * @throws InvalidParameterException if the supplied configuration argument
     *         is invalid.
     * @return a {@code Provider} configured with the supplied configuration
     *         argument.
     *
     * @since 9
     */
    public Provider configure(String configArg) {
        throw new UnsupportedOperationException("configure is not supported");
    }

    /**
     * Check if this {@code Provider} instance has been configured.
     *
     * @implSpec
     * The default implementation returns {@code true}.
     * Subclasses should override this method if the {@code Provider} requires
     * an explicit {@code configure} call after being constructed.
     *
     * @return {@code true} if no further configuration is needed,
     * {@code false} otherwise.
     *
     * @since 9
     */
    public boolean isConfigured() {
        return true;
    }


    /**
     * Returns the name of this {@code Provider}.
     *
     * @return the name of this {@code Provider}.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the version number for this {@code Provider}.
     *
     * @return the version number for this {@code Provider}.
     *
     * @deprecated use {@link #getVersionStr} instead.
     */
    @Deprecated(since="9")
    public double getVersion() {
        return version;
    }

    /**
     * Returns the version string for this {@code Provider}.
     *
     * @return the version string for this {@code Provider}.
     *
     * @since 9
     */
    public String getVersionStr() {
        return versionStr;
    }

    /**
     * Returns a human-readable description of the {@code Provider} and its
     * services.  This may return an HTML page, with relevant links.
     *
     * @return a description of the {@code Provider} and its services.
     */
    public String getInfo() {
        return info;
    }

    /**
     * Returns a string with the name and the version string
     * of this {@code Provider}.
     *
     * @return the string with the name and the version string
     * for this {@code Provider}.
     */
    public String toString() {
        return name + " version " + versionStr;
    }

    /**
     * Clears this {@code Provider} so that it no longer contains the properties
     * used to look up facilities implemented by the {@code Provider}.
     *
     * @since 1.2
     */
    @Override
    public synchronized void clear() {
        checkInitialized();
        if (debug != null) {
            debug.println("Remove " + name + " provider properties");
        }
        implClear();
    }

    /**
     * Reads a property list (key and element pairs) from the input stream.
     *
     * @param inStream the input stream.
     * @throws    IOException if an error occurred when reading from the
     *               input stream.
     * @see java.util.Properties#load
     */
    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        checkInitialized();
        if (debug != null) {
            debug.println("Load " + name + " provider properties");
        }
        Properties tempProperties = new Properties();
        tempProperties.load(inStream);
        implPutAll(tempProperties);
    }

    /**
     * Copies all the mappings from the specified Map to this {@code Provider}.
     * These mappings will replace any properties that this {@code Provider} had
     * for any of the keys currently in the specified Map.
     *
     * @since 1.2
     */
    @Override
    public synchronized void putAll(Map<?,?> t) {
        checkInitialized();
        if (debug != null) {
            debug.println("Put all " + name + " provider properties");
        }
        implPutAll(t);
    }

    /**
     * Returns an unmodifiable Set view of the property entries contained
     * in this {@code Provider}.
     *
     * @see   java.util.Map.Entry
     * @since 1.2
     */
    @Override
    public synchronized Set<Map.Entry<Object,Object>> entrySet() {
        checkInitialized();
        if (entrySet == null) {
            if (entrySetCallCount++ == 0)  // Initial call
                entrySet = Collections.unmodifiableMap(this).entrySet();
            else
                return super.entrySet();   // Recursive call
        }

        // This exception will be thrown if the implementation of
        // Collections.unmodifiableMap.entrySet() is changed such that it
        // no longer calls entrySet() on the backing Map.  (Provider's
        // entrySet implementation depends on this "implementation detail",
        // which is unlikely to change.
        if (entrySetCallCount != 2)
            throw new RuntimeException("Internal error.");

        return entrySet;
    }

    /**
     * Returns an unmodifiable Set view of the property keys contained in
     * this {@code Provider}.
     *
     * @since 1.2
     */
    @Override
    public Set<Object> keySet() {
        checkInitialized();
        return Collections.unmodifiableSet(super.keySet());
    }

    /**
     * Returns an unmodifiable Collection view of the property values
     * contained in this {@code Provider}.
     *
     * @since 1.2
     */
    @Override
    public Collection<Object> values() {
        checkInitialized();
        return Collections.unmodifiableCollection(super.values());
    }

    /**
     * Sets the {@code key} property to have the specified
     * {@code value}.
     *
     * @since 1.2
     */
    @Override
    public synchronized Object put(Object key, Object value) {
        checkInitialized();
        if (debug != null) {
            debug.println("Set " + name + " provider property [" +
                          key + "/" + value +"]");
        }
        return implPut(key, value);
    }

    /**
     * If the specified key is not already associated with a value (or is mapped
     * to {@code null}) associates it with the given value and returns
     * {@code null}, else returns the current value.
     *
     * @since 1.8
     */
    @Override
    public synchronized Object putIfAbsent(Object key, Object value) {
        checkInitialized();
        if (debug != null) {
            debug.println("Set " + name + " provider property [" +
                          key + "/" + value +"]");
        }
        return implPutIfAbsent(key, value);
    }

    /**
     * Removes the {@code key} property (and its corresponding
     * {@code value}).
     *
     * @since 1.2
     */
    @Override
    public synchronized Object remove(Object key) {
        checkInitialized();
        if (debug != null) {
            debug.println("Remove " + name + " provider property " + key);
        }
        return implRemove(key);
    }

    /**
     * Removes the entry for the specified key only if it is currently
     * mapped to the specified value.
     *
     * @since 1.8
     */
    @Override
    public synchronized boolean remove(Object key, Object value) {
        checkInitialized();
        if (debug != null) {
            debug.println("Remove " + name + " provider property " + key);
        }
        return implRemove(key, value);
    }

    /**
     * Replaces the entry for the specified key only if currently
     * mapped to the specified value.
     *
     * @since 1.8
     */
    @Override
    public synchronized boolean replace(Object key, Object oldValue,
            Object newValue) {
        checkInitialized();
        if (debug != null) {
            debug.println("Replace " + name + " provider property " + key);
        }
        return implReplace(key, oldValue, newValue);
    }

    /**
     * Replaces the entry for the specified key only if it is
     * currently mapped to some value.
     *
     * @since 1.8
     */
    @Override
    public synchronized Object replace(Object key, Object value) {
        checkInitialized();
        if (debug != null) {
            debug.println("Replace " + name + " provider property " + key);
        }
        return implReplace(key, value);
    }

    /**
     * Replaces each entry's value with the result of invoking the given
     * function on that entry, in the order entries are returned by an entry
     * set iterator, until all entries have been processed or the function
     * throws an exception.
     *
     * @since 1.8
     */
    @Override
    public synchronized void replaceAll(BiFunction<? super Object,
            ? super Object, ? extends Object> function) {
        checkInitialized();
        if (debug != null) {
            debug.println("ReplaceAll " + name + " provider property ");
        }
        implReplaceAll(function);
    }

    /**
     * Attempts to compute a mapping for the specified key and its
     * current mapped value (or {@code null} if there is no current
     * mapping).
     *
     * @since 1.8
     */
    @Override
    public synchronized Object compute(Object key, BiFunction<? super Object,
            ? super Object, ? extends Object> remappingFunction) {
        checkInitialized();
        if (debug != null) {
            debug.println("Compute " + name + " provider property " + key);
        }
        return implCompute(key, remappingFunction);
    }

    /**
     * If the specified key is not already associated with a value (or
     * is mapped to {@code null}), attempts to compute its value using
     * the given mapping function and enters it into this map unless
     * {@code null}.
     *
     * @since 1.8
     */
    @Override
    public synchronized Object computeIfAbsent(Object key,
            Function<? super Object, ? extends Object> mappingFunction) {
        checkInitialized();
        if (debug != null) {
            debug.println("ComputeIfAbsent " + name + " provider property " +
                    key);
        }
        return implComputeIfAbsent(key, mappingFunction);
    }

    /**
     * If the value for the specified key is present and non-null, attempts to
     * compute a new mapping given the key and its current mapped value.
     *
     * @since 1.8
     */
    @Override
    public synchronized Object computeIfPresent(Object key,
            BiFunction<? super Object, ? super Object, ? extends Object>
            remappingFunction) {
        checkInitialized();
        if (debug != null) {
            debug.println("ComputeIfPresent " + name + " provider property " +
                    key);
        }
        return implComputeIfPresent(key, remappingFunction);
    }

    /**
     * If the specified key is not already associated with a value or is
     * associated with {@code null}, associates it with the given value.
     * Otherwise, replaces the value with the results of the given remapping
     * function, or removes if the result is {@code null}. This method may be
     * of use when combining multiple mapped values for a key.
     *
     * @since 1.8
     */
    @Override
    public synchronized Object merge(Object key, Object value,
            BiFunction<? super Object, ? super Object, ? extends Object>
            remappingFunction) {
        checkInitialized();
        if (debug != null) {
            debug.println("Merge " + name + " provider property " + key);
        }
        return implMerge(key, value, remappingFunction);
    }

    // let javadoc show doc from superclass
    @Override
    public synchronized Object get(Object key) {
        checkInitialized();
        return super.get(key);
    }
    /**
     * @since 1.8
     */
    @Override
    public synchronized Object getOrDefault(Object key, Object defaultValue) {
        checkInitialized();
        return super.getOrDefault(key, defaultValue);
    }

    /**
     * @since 1.8
     */
    @Override
    public synchronized void forEach(BiConsumer<? super Object, ? super Object>
            action) {
        checkInitialized();
        super.forEach(action);
    }

    // let javadoc show doc from superclass
    @Override
    public synchronized Enumeration<Object> keys() {
        checkInitialized();
        return super.keys();
    }

    // let javadoc show doc from superclass
    @Override
    public synchronized Enumeration<Object> elements() {
        checkInitialized();
        return super.elements();
    }

    // let javadoc show doc from superclass
    public String getProperty(String key) {
        checkInitialized();
        return super.getProperty(key);
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException();
        }
    }

    // For backward compatibility, the registration ordering of
    // SecureRandom (RNG) algorithms needs to be preserved for
    // "new SecureRandom()" calls when this provider is used
    // NOTE: may need extra mechanism for providers to indicate their
    // preferred ordering of SecureRandom algorithms since registration
    // ordering info is lost once serialized
    private transient Set<ServiceKey> prngAlgos;

    // Map of services registered by this provider. This map may be republished
    // (assigned to a new one instead of modified in place) when a series of
    // changes with the Legacy API must be seen by readers as an atomic
    // operation. See Providers::putAll.
    private transient volatile ServicesMap servicesMap;

    /*
     * This class defines a structure to store and obtain services registered by
     * this provider, according to the Current (preferred) and Legacy APIs.
     * Synchronization is required for write accesses, while reads are
     * lock-free.
     */
    private final class ServicesMap {
        /*
         * Enum to inform the result of an operation on the services map.
         */
        enum SvcOpResult {
            SUCCESS,
            ERROR
        }

        /*
         * Interface to add and remove services to the map according to the
         * Current API. These functions update the Properties map to reflect
         * service changes, including algorithms, aliases and attributes.
         *
         * Services added with the Legacy API may be overwritten with this API.
         *
         * This interface guarantees atomicity from a service reader point
         * of view. In other words, a reader that gets a service will see all
         * its attributes and aliases as they were at time of registration.
         */
        interface Current {
            SvcOpResult putService(Service svc);
            SvcOpResult removeService(Service svc);
        }

        /*
         * Interface to add, modify and remove services on the map according to
         * the Legacy API. These functions update the Properties map to reflect
         * service changes, including algorithms, aliases and attributes.
         *
         * Services added with the Current API cannot be overwritten with
         * this API.
         *
         * Notice that this interface does not guarantee atomicity in a
         * sequence of operations from a service reader point of view. As
         * an example, a service reader may get a service missing an attribute
         * if looked up between a writer's putClassName() and putAttribute()
         * calls. For atomic changes with the Legacy API see Provider::putAll.
         */
        interface Legacy {
            SvcOpResult putClassName(ServiceKey key, String className,
                    String propKey);
            SvcOpResult putAlias(ServiceKey key, ServiceKey aliasKey,
                    String propKey);
            SvcOpResult putAttribute(ServiceKey key, String attrName,
                    String attrValue, String propKey);
            SvcOpResult remove(ServiceKey key, String className);
            SvcOpResult removeAlias(ServiceKey key, ServiceKey aliasKey);
            SvcOpResult removeAttribute(ServiceKey key, String attrName,
                    String attrValue);
        }

        /*
         * This class is the internal implementation of the services map.
         * Services can be added or removed either through the Current or the
         * Legacy API.
         */
        private final class ServicesMapImpl implements Current, Legacy {
            /*
             * Record to aggregate information about the lookup of a service on
             * the internal map. See ServicesMapImpl::find for a description of
             * possible values.
             */
            private record MappingInfo(Service svc, ServiceKey algKey,
                    Boolean isLegacy) {}

            // The internal services map, containing services registered with
            // the Current and the Legacy APIs. Concurrent read and write access
            // to this map is expected. Both algorithm and alias service keys
            // are added to this map.
            private final Map<ServiceKey, Service> services;

            // Auxiliary set to determine if a service on the services map
            // was added with the Legacy API. The absence of a service key
            // on this set is an indication that the service was either not
            // added or added with the Current API. Only algorithm service keys
            // are added to this set.
            private final Set<ServiceKey> legacySvcKeys;

            // Auxiliary map to keep track of the Properties map entries that
            // originated entries on the internal map. This information is used
            // to avoid inconsistencies. Both algorithm and alias service keys
            // are added to this map.
            private final Map<ServiceKey, String> serviceProps;

            // Auxiliary map to keep track of the Properties map entries that
            // originated service attributes on the internal map. This
            // information is used to avoid inconsistencies. Only algorithm
            // service keys are added to this map.
            private final Map<ServiceKey, Map<UString, String>>
                    serviceAttrProps;

            ServicesMapImpl() {
                services = new ConcurrentHashMap<>();
                legacySvcKeys = new HashSet<>();
                serviceProps = new HashMap<>();
                serviceAttrProps = new HashMap<>();
            }

            /*
             * Constructor to create a thin working copy such that readers of
             * the original map do not notice new changes. Used for atomic
             * changes with the Legacy API. See Providers::putAll.
             */
            ServicesMapImpl(ServicesMapImpl original) {
                services = new ConcurrentHashMap<>(original.services);
                legacySvcKeys = original.legacySvcKeys;
                serviceProps = original.serviceProps;
                serviceAttrProps = original.serviceAttrProps;
            }

            /*
             * Finds information about a service on the internal map. The key
             * for the lookup can be either algorithm or alias based. If the
             * service is found, svc refers to it, algKey to the algorithm
             * service key and isLegacy informs if the service was stored with
             * the Current or the Legacy API. Otherwise, svc is null, algKey
             * refers to the key used for the lookup and isLegacy is null.
             */
            private MappingInfo find(ServiceKey key) {
                Service svc = services.get(key);
                ServiceKey algKey = svc != null ? svc.algKey : key;
                Boolean isLegacy = svc != null ?
                        legacySvcKeys.contains(algKey) : null;
                return new MappingInfo(svc, algKey, isLegacy);
            }

            /*
             * Returns a set of services with services stored on the internal
             * map. This method can be invoked concurrently with write accesses
             * on the map and is lock-free.
             */
            Set<Service> getServices() {
                Set<Service> set = new LinkedHashSet<>();
                for (Map.Entry<ServiceKey, Service> e : services.entrySet()) {
                    Service svc = e.getValue();
                    //
                    // Skip alias based entries and filter out invalid services.
                    //
                    // Note: Multiple versions of the same service (reflecting
                    // different points in time) can be generated by concurrent
                    // writes with the Legacy API and, as a result of the
                    // copy-on-write strategy, seen under different service
                    // keys here. Each version has a unique object identity
                    // and, thus, would be distinguishable for a Set<Service>
                    // set. To avoid duplicates, we skip alias keys and use
                    // the version of the service pointed by the algorithm key.
                    if (e.getKey().equals(svc.algKey) && isValid(svc)) {
                        set.add(svc);
                    }
                }
                return set;
            }

            Service getService(ServiceKey key) {
                Service svc = services.get(key);
                return svc != null && isValid(svc) ? svc : null;
            }

            void clear() {
                services.clear();
                legacySvcKeys.clear();
                serviceProps.clear();
                serviceAttrProps.clear();
            }

            /*
             * Signals that there were changes on the services map and the
             * cached set of services need to be recomputed before use.
             */
            private void notifyChanges() {
                serviceSet.set(null);
            }

            /*
             * A service is invalid if it was added with the Legacy API through
             * an alias and does not have class information yet. We keep these
             * services on the internal map but filter them out for readers, so
             * they don't cause a NullPointerException when trying to create a
             * new instance.
             */
            private boolean isValid(Service svc) {
                return svc.className != null;
            }

            /*
             * Current API methods to add and remove services.
             */

            @Override
            public SvcOpResult putService(Service svc) {
                svc.generateServiceKeys();

                // Define a set of algorithm and alias keys that, if already
                // on the services map, will be kept at all times until
                // overwritten. This prevents concurrent readers from seeing
                // 'holes' on the map while doing updates.
                Set<ServiceKey> keysToBeKept =
                        new HashSet<>(svc.aliasKeys.size() + 1);
                keysToBeKept.add(svc.algKey);
                keysToBeKept.addAll(svc.aliasKeys.keySet());

                // The new service algorithm key may be in use already.
                resolveKeyConflict(svc.algKey, keysToBeKept);

                // The service will be registered to its provider's ServicesMap.
                svc.registered = true;

                // Register the new service under its algorithm service key.
                // At this point, readers  will have access to it.
                services.put(svc.algKey, svc);

                // Add an entry to the Properties map to reflect the new service
                // under its algorithm key, and keep track of this information
                // for further changes in the future (i.e. removal of the
                // service).
                String propKey = svc.getType() + "." + svc.getAlgorithm();
                serviceProps.put(svc.algKey, propKey);
                Provider.super.put(propKey, svc.getClassName());

                // Register the new service under its aliases.
                for (Map.Entry<ServiceKey, String> e :
                        svc.aliasKeys.entrySet()) {
                    ServiceKey aliasKey = e.getKey();

                    // The new service alias may be in use already.
                    resolveKeyConflict(aliasKey, keysToBeKept);

                    // Register the new service under its alias service key. At
                    // this point, readers will have access through this alias.
                    services.put(aliasKey, svc);

                    // Add an entry to the Properties map to reflect the new
                    // service under its alias service key, and keep track
                    // of this information for further changes in the future
                    // (i.e. removal of the service).
                    propKey = ALIAS_PREFIX + svc.getType() + "." + e.getValue();
                    serviceProps.put(aliasKey, propKey);
                    Provider.super.put(propKey, svc.getAlgorithm());
                }

                if (!svc.attributes.isEmpty()) {
                    // Register the new service attributes on the Properties map
                    // and keep track of them for further changes in the future
                    // (i.e. removal of the service).
                    Map<UString, String> newAttrProps =
                            new HashMap<>(svc.attributes.size());
                    for (Map.Entry<UString, String> attr :
                            svc.attributes.entrySet()) {
                        propKey = svc.getType() + "." + svc.getAlgorithm() +
                                " " + attr.getKey().string;
                        newAttrProps.put(attr.getKey(), propKey);
                        Provider.super.put(propKey, attr.getValue());
                    }
                    serviceAttrProps.put(svc.algKey, newAttrProps);
                }

                Provider.this.checkAndUpdateSecureRandom(svc.algKey, true);

                return SvcOpResult.SUCCESS;
            }

            /*
             * Handle cases in which a service key (algorithm or alias based)
             * is in use already. This might require modifications to a service,
             * the Properties map or auxiliary structures. This method must be
             * called from the Current API only.
             */
            private void resolveKeyConflict(ServiceKey key,
                    Set<ServiceKey> keysToBeKept) {
                assert keysToBeKept.contains(key) : "Inconsistent " +
                        "keysToBeKept set.";
                MappingInfo miByKey = find(key);
                if (miByKey.svc != null) {
                    // The service key (algorithm or alias) is in use already.
                    SvcOpResult opResult = SvcOpResult.SUCCESS;
                    if (miByKey.algKey.equals(key)) {
                        // It is used as an algorithm. Remove the service.
                        opResult = removeCommon(miByKey, false, keysToBeKept);
                    } else {
                        // It is used as an alias.
                        if (miByKey.isLegacy) {
                            // The service was added with the Legacy API.
                            // Remove the alias only.
                            opResult = removeAlias(miByKey.algKey, key,
                                    keysToBeKept);
                        } else {
                            // The service was added with the Current API.
                            // Overwrite the alias entry on the services map
                            // without modifying the service that is currently
                            // using it.

                            // Remove any Properties map key entry because, if
                            // no longer used as an alias, the entry would not
                            // be overwritten. Note: The serviceProps key entry
                            // will be overwritten later.
                            String oldPropKey = serviceProps.remove(key);
                            assert oldPropKey != null :
                                    "Invalid alias property.";
                            Provider.super.remove(oldPropKey);
                        }
                    }
                    assert opResult == SvcOpResult.SUCCESS : "Unexpected" +
                            " error removing an existing service or alias.";
                }
            }

            @Override
            public SvcOpResult removeService(Service svc) {
                if (svc.algKey != null) {
                    MappingInfo mi = find(svc.algKey);
                    if (mi.svc != null) {
                        SvcOpResult opResult = removeCommon(mi, false,
                                Collections.emptySet());
                        assert opResult == SvcOpResult.SUCCESS : "Unexpected" +
                                " error removing an existing service.";
                        return opResult;
                    }
                }
                return SvcOpResult.SUCCESS;
            }

            /*
             * Common (Current and Legacy) API methods to add and remove
             * services.
             */

            /*
             * This method is invoked both when removing and overwriting a
             * service. The keysToBeKept set is used when overwriting to
             * prevent readers from seeing a 'hole' on the services map
             * between removing and adding entries.
             */
            private SvcOpResult removeCommon(MappingInfo mi,
                    boolean legacyApiCall, Set<ServiceKey> keysToBeKept) {
                assert mi.svc != null : "Invalid service for removal.";
                if (!mi.isLegacy && legacyApiCall) {
                    // Services added with the Current API cannot be
                    // removed with the Legacy API.
                    return SvcOpResult.ERROR;
                }

                if (mi.isLegacy) {
                    legacySvcKeys.remove(mi.algKey);
                }

                if (!keysToBeKept.contains(mi.algKey)) {
                    services.remove(mi.algKey);
                }

                // Update the Properties map to reflect the algorithm removal.
                // Note: oldPropKey may be null for services added through
                // aliases or attributes (Legacy API) that still don't have a
                // class name (invalid).
                String oldPropKey = serviceProps.remove(mi.algKey);
                if (oldPropKey != null) {
                    Provider.super.remove(oldPropKey);
                }

                // Remove registered service aliases.
                for (ServiceKey aliasKey : mi.svc.aliasKeys.keySet()) {
                    if (!mi.isLegacy) {
                        // Services added with the Current API can have aliases
                        // overwritten by other services added with the same
                        // API. Do nothing in these cases: the alias on the
                        // services map does not belong to the removed service
                        // anymore.
                        MappingInfo miByAlias = find(aliasKey);
                        if (miByAlias.svc != mi.svc) {
                            continue;
                        }
                    }
                    if (!keysToBeKept.contains(aliasKey)) {
                        services.remove(aliasKey);
                    }

                    // Update the Properties map to reflect the alias removal.
                    // Note: oldPropKey cannot be null because aliases always
                    // have a corresponding Properties map entry.
                    oldPropKey = serviceProps.remove(aliasKey);
                    assert oldPropKey != null : "Unexpected null " +
                            "Property value for an alias.";
                    Provider.super.remove(oldPropKey);
                }

                // Remove registered service attributes.
                Map<UString, String> oldAttrProps =
                        serviceAttrProps.remove(mi.algKey);
                if (oldAttrProps != null) {
                    for (String oldAttrPropKey : oldAttrProps.values()) {
                        // Update the Properties map to reflect the attribute
                        // removal. Note: oldAttrPropKey cannot be null because
                        // attributes always have a corresponding Properties map
                        // entry.
                        assert oldAttrPropKey != null : "Unexpected null " +
                                "Property value for an attribute.";
                        Provider.super.remove(oldAttrPropKey);
                    }
                }

                notifyChanges();

                Provider.this.checkAndUpdateSecureRandom(mi.svc.algKey, false);

                return SvcOpResult.SUCCESS;
            }

            /*
             * Legacy API methods to add, modify and remove services.
             */

            @Override
            public SvcOpResult putClassName(ServiceKey key, String className,
                    String propKey) {
                assert key != null && className != null && propKey != null :
                        "Service information missing.";
                return updateSvc(key, (MappingInfo oldMi, Service newSvc) -> {
                    String canonicalPropKey = propKey;
                    if (oldMi.svc != null) {
                        // The service exists. Get its Properties map entry.
                        // Note: Services added through an alias or an attribute
                        // may don't have one.
                        String oldPropKey = serviceProps.get(oldMi.algKey);
                        if (oldMi.algKey.equals(key)) {
                            // The service was found by an algorithm.
                            if (oldPropKey != null) {
                                // Remove any previous Properties map entry
                                // before adding a new one, so we handle
                                // differences in casing.
                                Provider.super.remove(oldPropKey);
                            }
                        } else {
                            // The service was found by an alias. Use an
                            // algorithm entry on the Properties map. Create a
                            // new one if it does not exist.
                            canonicalPropKey = oldPropKey != null ?
                                    oldPropKey : newSvc.getType() + "." +
                                    newSvc.getAlgorithm();
                        }
                    }

                    newSvc.className = className;

                    // Keep track of the Properties map entry for further
                    // changes in the future (i.e. removal of the service).
                    serviceProps.put(oldMi.algKey, canonicalPropKey);
                    Provider.super.put(canonicalPropKey, className);

                    Provider.this.checkAndUpdateSecureRandom(
                            newSvc.algKey, true);

                    return SvcOpResult.SUCCESS;
                });
            }

            @Override
            public SvcOpResult putAlias(ServiceKey key, ServiceKey aliasKey,
                    String propKey) {
                assert key != null && aliasKey != null && propKey != null :
                        "Alias information missing.";
                assert key.type.equals(aliasKey.type) :
                        "Inconsistent service key types.";
                return updateSvc(key, (MappingInfo oldMi, Service newSvc) -> {
                    MappingInfo miByAlias = find(aliasKey);
                    if (miByAlias.svc != null) {
                        // The alias is associated to a service on the map.
                        if (miByAlias.algKey.equals(aliasKey)) {
                            // The alias is an algorithm. Never overwrite
                            // algorithms with aliases from the Legacy API.
                            return SvcOpResult.ERROR;
                        } else if (!miByAlias.isLegacy) {
                            // Do not remove the alias of services added with
                            // the Current API.
                            return SvcOpResult.ERROR;
                        } else if (miByAlias.svc == oldMi.svc) {
                            // The service has the alias that we are adding.
                            // This is possible if, for example, the alias
                            // casing is changing.
                            //
                            // Update the Properties map to remove the alias
                            // with the old casing. Note: oldPropKey cannot be
                            // null because aliases always have a corresponding
                            // Properties map entry.
                            String oldPropKey = serviceProps.remove(aliasKey);
                            assert oldPropKey != null : "Unexpected null " +
                                    "Property value for an alias.";
                            Provider.super.remove(oldPropKey);
                        } else {
                            // The alias belongs to a different service.
                            // Remove it first.
                            SvcOpResult opResult = removeAlias(miByAlias.algKey,
                                    aliasKey, Set.of(aliasKey));
                            assert opResult == SvcOpResult.SUCCESS :
                                    "Unexpected error removing an alias.";
                        }
                    } else {
                        // The alias was not found on the map.
                        if (aliasKey.equals(key)) {
                            // The alias would be equal to the algorithm for
                            // the new service.
                            return SvcOpResult.ERROR;
                        }
                    }

                    newSvc.addAliasKey(aliasKey);

                    // Keep track of the Properties map entry for further
                    // changes in the future (i.e. removal of the service).
                    serviceProps.put(aliasKey, propKey);
                    // If the service to which we will add an alias was found by
                    // an alias, use its algorithm for the Properties map entry.
                    String canonicalAlgorithm = oldMi.algKey.equals(key) ?
                            key.originalAlgorithm : newSvc.getAlgorithm();
                    Provider.super.put(propKey, canonicalAlgorithm);

                    return SvcOpResult.SUCCESS;
                });
            }

            @Override
            public SvcOpResult putAttribute(ServiceKey key, String attrName,
                    String attrValue, String propKey) {
                assert key != null && attrName != null && attrValue != null &&
                        propKey != null : "Attribute information missing.";
                return updateSvc(key, (MappingInfo oldMi, Service newSvc) -> {
                    String canonicalPropKey = propKey;
                    UString attrNameKey = new UString(attrName);
                    Map<UString, String> attrProps =
                            serviceAttrProps.computeIfAbsent(
                                    oldMi.algKey, k -> new HashMap<>());
                    assert oldMi.svc != null || attrProps.isEmpty() :
                            "Inconsistent service attributes data.";
                    // Try to get the attribute's Properties map entry. Note:
                    // oldPropKey can be null if the service was not found or
                    // does not have the attribute.
                    String oldPropKey = attrProps.get(attrNameKey);
                    if (oldMi.algKey.equals(key)) {
                        // The service was found by an algorithm.
                        if (oldPropKey != null) {
                            // Remove any previous Properties map entry before
                            // adding a new one, so we handle differences in
                            // casing.
                            Provider.super.remove(oldPropKey);
                        }
                    } else {
                        // The service was found by an alias. Use an algorithm
                        // based entry on the Properties map. Create a new one
                        // if it does not exist.
                        canonicalPropKey = oldPropKey != null ? oldPropKey :
                                newSvc.getType() + "." + newSvc.getAlgorithm() +
                                " " + attrName;
                    }

                    newSvc.addAttribute(attrName, attrValue);

                    // Keep track of the Properties map entry for further
                    // changes in the future (i.e. removal of the service).
                    attrProps.put(attrNameKey, canonicalPropKey);
                    Provider.super.put(canonicalPropKey, attrValue);

                    return SvcOpResult.SUCCESS;
                });
            }

            @Override
            public SvcOpResult remove(ServiceKey key, String className) {
                assert key != null && className != null :
                        "Service information missing.";
                MappingInfo mi = find(key);
                if (mi.svc != null) {
                    assert className.equals(mi.svc.getClassName()) :
                            "Unexpected class name.";
                    return removeCommon(mi, true, Collections.emptySet());
                }
                assert false : "Should not reach.";
                return SvcOpResult.ERROR;
            }

            @Override
            public SvcOpResult removeAlias(ServiceKey key,
                    ServiceKey aliasKey) {
                return removeAlias(key, aliasKey, Collections.emptySet());
            }

            /*
             * This method is invoked both when removing and overwriting a
             * service alias. The keysToBeKept set is used when overwriting to
             * prevent readers from seeing a 'hole' on the services map between
             * removing and adding entries.
             */
            private SvcOpResult removeAlias(ServiceKey key, ServiceKey aliasKey,
                    Set<ServiceKey> keysToBeKept) {
                assert key != null && aliasKey != null && keysToBeKept != null :
                        "Alias information missing.";
                return updateSvc(key, (MappingInfo oldMi, Service newSvc) -> {
                    MappingInfo miByAlias = find(aliasKey);
                    if (oldMi.svc != null && miByAlias.svc == oldMi.svc &&
                            !miByAlias.algKey.equals(aliasKey)) {
                        // The alias is a real alias and is associated to the
                        // service on the map.
                        if (!keysToBeKept.contains(aliasKey)) {
                            services.remove(aliasKey);
                        }

                        newSvc.removeAliasKey(aliasKey);

                        // Update the Properties map to reflect the alias
                        // removal. Note: oldPropKey cannot be null because
                        // aliases always have a corresponding Properties map
                        // entry.
                        String oldPropKey = serviceProps.remove(aliasKey);
                        assert oldPropKey != null : "Invalid alias property.";
                        Provider.super.remove(oldPropKey);

                        return SvcOpResult.SUCCESS;
                    }
                    assert false : "Should not reach.";
                    return SvcOpResult.ERROR;
                });
            }

            @Override
            public SvcOpResult removeAttribute(ServiceKey key,
                    String attrName, String attrValue) {
                assert key != null && attrName != null && attrValue != null :
                        "Attribute information missing.";
                return updateSvc(key, (MappingInfo oldMi, Service newSvc) -> {
                    Map<UString, String> oldAttrProps =
                            serviceAttrProps.get(oldMi.algKey);
                    if (oldAttrProps != null) {
                        // The service was found and has attributes.
                        assert oldMi.svc != null : "Inconsistent service " +
                                "attributes data.";

                        newSvc.removeAttribute(attrName, attrValue);
                        assert newSvc.getAttribute(attrName) == null :
                                "Attribute was not removed from the service.";

                        // Update the Properties map to reflect the attribute
                        // removal. Note: oldPropKey cannot be null because
                        // attributes always have a corresponding Properties
                        // map entry.
                        String oldPropKey = oldAttrProps.remove(
                                new UString(attrName));
                        assert oldPropKey != null :
                                "Invalid attribute property.";
                        Provider.super.remove(oldPropKey);

                        if (oldAttrProps.isEmpty()) {
                            // If the removed attribute was the last one,
                            // remove the map.
                            serviceAttrProps.remove(oldMi.algKey);
                        }

                        return SvcOpResult.SUCCESS;
                    }
                    assert false : "Should not reach.";
                    return SvcOpResult.ERROR;
                });
            }

            @FunctionalInterface
            private interface ServiceUpdateCallback {
                SvcOpResult apply(MappingInfo oldMi, Service newSvc);
            }

            /*
             * This method tries to find a service on the map (based on an
             * algorithm or alias) and pass a copy of it to an update callback
             * (copy-on-write). If the service found was added with the Current
             * API, no update should be done. If a service was not found, a new
             * instance may be created.
             *
             * The updated version of the service is put on the services map.
             * Algorithm and alias based entries pointing to the old version
             * of the service are overwritten.
             */
            private SvcOpResult updateSvc(ServiceKey key,
                    ServiceUpdateCallback updateCb) {
                Service newSvc;
                MappingInfo oldMi = find(key);
                if (oldMi.svc != null) {
                    // Service exists.
                    if (!oldMi.isLegacy) {
                        // Don't update services added with the Current API.
                        return SvcOpResult.ERROR;
                    }
                    // Create a copy of the service for a copy-on-write update.
                    newSvc = new Service(oldMi.svc);
                } else {
                    // Service does not exist.
                    newSvc = new Service(Provider.this, key);
                }
                SvcOpResult opResult = updateCb.apply(oldMi, newSvc);
                if (opResult == SvcOpResult.ERROR) {
                    // Something went wrong and the update should not be done.
                    return opResult;
                }

                // The service (or its updated version) will be registered to
                // its provider's ServicesMap.
                newSvc.registered = true;

                // Register the updated version of the service under its
                // algorithm and aliases on the map. This may overwrite entries
                // or add new ones. The previous callback should have handled
                // the removal of an alias.
                for (ServiceKey aliasKey : newSvc.aliasKeys.keySet()) {
                    services.put(aliasKey, newSvc);
                }

                assert oldMi.algKey.type.equals(newSvc.getType()) &&
                        oldMi.algKey.originalAlgorithm.equals(
                                newSvc.getAlgorithm()) : "Invalid key.";
                services.put(oldMi.algKey, newSvc);

                legacySvcKeys.add(oldMi.algKey);

                // Notify a change.
                notifyChanges();

                return opResult;
            }
        }

        // Placeholder for a thread to mark that serviceSet values are being
        // computed after a services update. Only one thread at a time can
        // effectively assign this value.
        private static final Set<Service> SERVICE_SET_IN_PROGRESS = Set.of();

        // Unmodifiable set of all services. Possible values for this field
        // are: 1) null (indicates that the set has to be recomputed after a
        // service update), 2) SERVICE_SET_IN_PROGRESS (indicates that a thread
        // is recomputing its value), and 3) an actual set of services.
        private final AtomicReference<Set<Service>> serviceSet;

        // Implementation of ServicesMap that handles the Current and Legacy
        // APIs.
        private final ServicesMapImpl impl;

        ServicesMap() {
            impl = new ServicesMapImpl();
            serviceSet = new AtomicReference<>();
        }

        /*
         * Constructor to create a thin working copy such that readers of the
         * original map do not notice any new changes. Used for atomic
         * changes with the Legacy API. See Providers::putAll.
         */
        ServicesMap(ServicesMap original) {
            impl = new ServicesMapImpl(original.impl);
            serviceSet = new AtomicReference<>(original.serviceSet.get());
        }

        /*
         * Returns a Current API view of the services map.
         */
        Current asCurrent() {
            return impl;
        }

        /*
         * Returns a Legacy API view of the services map.
         */
        Legacy asLegacy() {
            return impl;
        }

        /*
         * Returns an unmodifiable set of available services. Recomputes
         * serviceSet if needed, after a service update. Both services added
         * with the Current and Legacy APIs are included. If no services are
         * found, the returned set is empty. This method is thread-safe and
         * lock-free.
         */
        Set<Service> getServices() {
            Set<Service> serviceSetLocal = serviceSet.compareAndExchange(
                    null, SERVICE_SET_IN_PROGRESS);
            if (serviceSetLocal == null ||
                    serviceSetLocal == SERVICE_SET_IN_PROGRESS) {
                // A cached set is not available. Instead of locking, compute
                // the set to be returned and, eventually, make it available
                // for others to use.
                Set<Service> newSet = Collections.unmodifiableSet(
                        impl.getServices());
                if (serviceSetLocal == null) {
                    // We won the race to make the computed set available for
                    // others to use. However, only make it available if it
                    // is still current (in other words, there were no further
                    // changes). If it is not current, the next reader will
                    // do the job.
                    serviceSet.compareAndExchange(
                            SERVICE_SET_IN_PROGRESS, newSet);
                }
                serviceSetLocal = newSet;
            }
            return serviceSetLocal;
        }

        /*
         * Returns an available service. Both services added with the Current
         * and Legacy APIs are considered in the search. Thread-safe and
         * lock-free.
         */
        Service getService(ServiceKey key) {
            return impl.getService(key);
        }

        /*
         * Clears the internal ServicesMap state. The caller must synchronize
         * changes with the Properties map.
         */
        void clear() {
            impl.clear();
            serviceSet.set(null);
        }
    }

    // register the id attributes for this provider
    // this is to ensure that equals() and hashCode() do not incorrectly
    // report to different provider objects as the same
    private void putId() {
        // note: name and info may be null
        super.put("Provider.id name", String.valueOf(name));
        super.put("Provider.id version", String.valueOf(versionStr));
        super.put("Provider.id info", String.valueOf(info));
        super.put("Provider.id className", this.getClass().getName());
    }

    /*
     * Creates a copy of the Properties map that is useful to iterate when
     * applying changes to the original one. Notice that we are calling
     * super.entrySet() purposefully to avoid landing into a subclass override.
     */
    private Properties copyProperties() {
        Properties copy = new Properties(super.size());
        for (Map.Entry<Object, Object> entry : super.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

   /**
    * Reads the {@code ObjectInputStream} for the default serializable fields.
    * If the serialized field {@code versionStr} is found in the STREAM FIELDS,
    * its {@code String} value will be used to populate both the version string
    * and version number. If {@code versionStr} is not found, but
    * {@code version} is, then its double value will be used to populate
    * both fields.
    *
    * @param in the {@code ObjectInputStream} to read
    * @throws IOException if an I/O error occurs
    * @throws ClassNotFoundException if a serialized class cannot be loaded
    */
    @java.io.Serial
    private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
        Properties copy = copyProperties();
        defaults = null;
        in.defaultReadObject();
        if (this.versionStr == null) {
            // set versionStr based on version when not found in serialized bytes
            this.versionStr = Double.toString(this.version);
        } else {
            // otherwise, set version based on versionStr
            this.version = parseVersionStr(this.versionStr);
        }
        this.servicesMap = new ServicesMap();
        this.prngAlgos = new LinkedHashSet<>(6);
        implClear();
        initialized = true;
        putAll(copy);
    }

    /*
     * Enum to determine if changes to the Properties map must be applied by the
     * caller (UPDATE) or skipped (SKIP).
     *
     * If a change does not concern a ServicesMap, UPDATE is returned. An
     * example of this is when adding, modifying or removing an entry that is
     * not a service, alias or attribute.
     *
     * If the change concerns a ServicesMap, SKIP is returned. The change may
     * have been applied internally or ignored due to an error. In the former
     * case, Properties map entries are synchronized. In the latter, Properties
     * map entries are not modified.
     */
    private enum PropertiesMapAction {
        UPDATE,
        SKIP
    }

    private PropertiesMapAction doLegacyOp(ServicesMap servicesMap, Object key,
            Object value, Object oldValue, OPType opType) {
        if (key instanceof String ks) {
            if (ks.startsWith("Provider.")) {
                // Ignore provider related updates.
                return PropertiesMapAction.SKIP;
            }
            if (value instanceof String vs) {
                return parseLegacy(servicesMap, ks, vs, opType);
            } else if (value != null && oldValue instanceof String oldValueS &&
                    opType == OPType.ADD) {
                // An entry in the Properties map potentially concerning the
                // ServicesMap is about to be replaced by one that does not.
                // From the ServicesMap point of view, this could be equivalent
                // to a removal. In any case, let the caller proceed with the
                // Properties map update.
                parseLegacy(servicesMap, ks, oldValueS, OPType.REMOVE);
            }
        }
        // The change does not concern a ServicesMap.
        return PropertiesMapAction.UPDATE;
    }

    /**
     * Copies all the mappings from the specified Map to this provider.
     */
    private void implPutAll(Map<?,?> t) {
        // For service readers to see this change as atomic, add the elements in
        // a local thin copy of the ServicesMap and then publish it.
        ServicesMap servicesMapCopy = new ServicesMap(servicesMap);
        for (Map.Entry<?,?> e : t.entrySet()) {
            implPut(servicesMapCopy, e.getKey(), e.getValue());
        }
        servicesMap = servicesMapCopy;
    }

    private void implClear() {
        servicesMap.clear();
        prngAlgos.clear();
        super.clear();
        putId();
    }

    private Object implRemove(Object key) {
        Object oldValue = super.get(key);
        return doLegacyOp(servicesMap, key, oldValue, null, OPType.REMOVE) ==
                PropertiesMapAction.UPDATE ? super.remove(key) : oldValue;
    }

    private boolean implRemove(Object key, Object value) {
        if (Objects.equals(super.get(key), value) && value != null) {
            implRemove(key);
            return !super.contains(key);
        }
        return false;
    }

    private boolean implReplace(Object key, Object oldValue, Object newValue) {
        Objects.requireNonNull(oldValue);
        Objects.requireNonNull(newValue);
        if (super.containsKey(key) &&
                Objects.equals(super.get(key), oldValue)) {
            implPut(key, newValue);
            return super.get(key) == newValue;
        }
        return false;
    }

    private Object implReplace(Object key, Object value) {
        Objects.requireNonNull(value);
        if (super.containsKey(key)) {
            return implPut(key, value);
        }
        return null;
    }

    @SuppressWarnings("unchecked") // Function must actually operate over strings
    private void implReplaceAll(BiFunction<? super Object, ? super Object,
            ? extends Object> function) {
        Properties propertiesCopy = copyProperties();
        propertiesCopy.replaceAll(function);
        putAll(propertiesCopy);
    }

    @SuppressWarnings("unchecked") // Function must actually operate over strings
    private Object implMerge(Object key, Object value,
            BiFunction<? super Object, ? super Object, ? extends Object>
            remappingFunction) {
        Objects.requireNonNull(value);
        Object oldValue = super.get(key);
        Object newValue = (oldValue == null) ? value :
                remappingFunction.apply(oldValue, value);
        if (newValue == null) {
            implRemove(key);
        } else {
            implPut(key, newValue);
        }
        return super.get(key);
    }

    @SuppressWarnings("unchecked") // Function must actually operate over strings
    private Object implCompute(Object key, BiFunction<? super Object,
            ? super Object, ? extends Object> remappingFunction) {
        Object oldValue = super.get(key);
        Object newValue = remappingFunction.apply(key, oldValue);
        if (newValue != null) {
            implPut(key, newValue);
        } else if (oldValue != null) {
            // The Properties map cannot contain null values, so checking
            // super.containsKey(key) would be superfluous.
            implRemove(key);
        }
        return super.get(key);
    }

    @SuppressWarnings("unchecked") // Function must actually operate over strings
    private Object implComputeIfAbsent(Object key, Function<? super Object,
            ? extends Object> mappingFunction) {
        Object oldValue = super.get(key);
        if (oldValue == null) {
            Object newValue = mappingFunction.apply(key);
            if (newValue != null) {
                implPut(key, newValue);
                return super.get(key);
            }
        }
        return oldValue;
    }

    @SuppressWarnings("unchecked") // Function must actually operate over strings
    private Object implComputeIfPresent(Object key, BiFunction<? super Object,
            ? super Object, ? extends Object> remappingFunction) {
        Object oldValue = super.get(key);
        if (oldValue != null) {
            Object newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                implPut(key, newValue);
            } else {
                implRemove(key);
            }
            return super.get(key);
        }
        return null;
    }

    private Object implPutIfAbsent(Object key, Object value) {
        Objects.requireNonNull(value);
        Object oldValue = super.get(key);
        return oldValue == null ? implPut(key, value) : oldValue;
    }

    private Object implPut(Object key, Object value) {
        return implPut(servicesMap, key, value);
    }

    private Object implPut(ServicesMap servicesMap, Object key, Object value) {
        Objects.requireNonNull(value);
        Object oldValue = super.get(key);
        return doLegacyOp(servicesMap, key, value, oldValue, OPType.ADD) ==
                PropertiesMapAction.UPDATE ? super.put(key, value) : oldValue;
    }

    // used as key in the serviceMap and legacyMap HashMaps
    private static final class ServiceKey {
        private final String type;
        private final String algorithm;
        private final String originalAlgorithm;
        private ServiceKey(String type, String algorithm, boolean intern) {
            this.type = type;
            this.originalAlgorithm = algorithm;
            algorithm = algorithm.toUpperCase(ENGLISH);
            this.algorithm = intern ? algorithm.intern() : algorithm;
        }
        public int hashCode() {
            return type.hashCode() * 31 + algorithm.hashCode();
        }
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            return obj instanceof ServiceKey other
                && this.type.equals(other.type)
                && this.algorithm.equals(other.algorithm);
        }

        // Don't change '==' to equals.
        // This method tests for equality of pointers.
        boolean matches(String type, String algorithm) {
            return (this.type == type) && (this.originalAlgorithm == algorithm);
        }
        public String toString() {
            return type + "." + algorithm;
        }
    }

    private static String[] getTypeAndAlgorithm(String key) {
        int i = key.indexOf('.');
        if (i < 1) {
            if (debug != null) {
                debug.println("Ignoring invalid entry in provider: "
                        + key);
            }
            return null;
        }
        String type = key.substring(0, i);
        String alg = key.substring(i + 1);
        return new String[] {type, alg};
    }

    private static final String ALIAS_PREFIX = "Alg.Alias.";
    private static final String ALIAS_PREFIX_LOWER = "alg.alias.";
    private static final int ALIAS_LENGTH = ALIAS_PREFIX.length();

    private enum OPType {
        ADD, REMOVE
    }

    /*
     * Parse a String entry change on the Properties map and, if concerns to the
     * ServicesMap, apply its corresponding operation through the Legacy API.
     * Returns whether the change on the Properties map should proceed or not.
     */
    private PropertiesMapAction parseLegacy(ServicesMap servicesMap,
            String propKey, String propValue, OPType opType) {
        // alias
        if (propKey.toLowerCase(ENGLISH).startsWith(ALIAS_PREFIX_LOWER)) {
            // e.g. put("Alg.Alias.MessageDigest.SHA", "SHA-1");
            // aliasKey ~ MessageDigest.SHA
            String aliasKeyStr = propKey.substring(ALIAS_LENGTH);
            String[] typeAndAlg = getTypeAndAlgorithm(aliasKeyStr);
            if (typeAndAlg == null) {
                return PropertiesMapAction.UPDATE;
            }
            Objects.requireNonNull(propValue,
                    "alias value should map to an alg");
            String type = getEngineName(typeAndAlg[0]);
            String aliasAlg = typeAndAlg[1].intern();
            ServiceKey svcKey = new ServiceKey(type, propValue, true);
            ServiceKey aliasKey = new ServiceKey(type, aliasAlg, true);
            switch (opType) {
                case ADD -> servicesMap.asLegacy()
                        .putAlias(svcKey, aliasKey, propKey);
                case REMOVE -> servicesMap.asLegacy()
                        .removeAlias(svcKey, aliasKey);
            }
        } else {
            String[] typeAndAlg = getTypeAndAlgorithm(propKey);
            if (typeAndAlg == null) {
                return PropertiesMapAction.UPDATE;
            }
            int i = typeAndAlg[1].indexOf(' ');
            // regular registration
            if (i == -1) {
                // e.g. put("MessageDigest.SHA-1", "sun.security.provider.SHA");
                String type = getEngineName(typeAndAlg[0]);
                String algo = typeAndAlg[1].intern();
                ServiceKey svcKey = new ServiceKey(type, algo, true);
                switch (opType) {
                    case ADD -> servicesMap.asLegacy()
                            .putClassName(svcKey, propValue, propKey);
                    case REMOVE -> servicesMap.asLegacy()
                            .remove(svcKey, propValue);
                }
            } else { // attribute
                // e.g. put("MessageDigest.SHA-1 ImplementedIn", "Software");
                String type = getEngineName(typeAndAlg[0]);
                String attrString = typeAndAlg[1];
                String algo = attrString.substring(0, i).intern();
                String attrName = attrString.substring(i + 1);
                // kill additional spaces
                while (attrName.startsWith(" ")) {
                    attrName = attrName.substring(1);
                }
                attrName = attrName.intern();
                ServiceKey svcKey = new ServiceKey(type, algo, true);
                switch (opType) {
                    case ADD -> servicesMap.asLegacy()
                            .putAttribute(svcKey, attrName, propValue, propKey);
                    case REMOVE -> servicesMap.asLegacy()
                            .removeAttribute(svcKey, attrName, propValue);
                }
            }
        }
        return PropertiesMapAction.SKIP;
    }

    /**
     * Get the service describing this Provider's implementation of the
     * specified type of this algorithm or alias. If no such
     * implementation exists, this method returns {@code null}.
     *
     * @param type the type of {@link Service service} requested
     * (for example, {@code MessageDigest})
     * @param algorithm the case-insensitive algorithm name (or alternate
     * alias) of the service requested (for example, {@code SHA-1})
     *
     * @return the service describing this Provider's matching service
     * or {@code null} if no such service exists
     *
     * @throws NullPointerException if type or algorithm is {@code null}
     *
     * @since 1.5
     */
    public Service getService(String type, String algorithm) {
        checkInitialized();
        // avoid allocating a new ServiceKey object if possible
        ServiceKey key = previousKey.get();
        if (!key.matches(type, algorithm)) {
            key = new ServiceKey(type, algorithm, false);
            previousKey.set(key);
        }

        Service s = servicesMap.getService(key);

        if (s != null && SecurityProviderServiceEvent.isTurnedOn()) {
            var e  = new SecurityProviderServiceEvent();
            e.provider = getName();
            e.type = type;
            e.algorithm = algorithm;
            e.commit();
        }

        return s;
    }

    // ServiceKey from previous getService() call
    // by re-using it if possible we avoid allocating a new object
    // and the toUpperCase() call.
    // re-use will occur e.g. as the framework traverses the provider
    // list and queries each provider with the same values until it finds
    // a matching service
    private static final ThreadLocal<ServiceKey> previousKey =
        ThreadLocal.withInitial(() -> new ServiceKey("","", false));

    /**
     * Get an unmodifiable Set of all services supported by
     * this {@code Provider}.
     *
     * @return an unmodifiable Set of all services supported by
     * this {@code Provider}
     *
     * @since 1.5
     */
    public Set<Service> getServices() {
        checkInitialized();
        return servicesMap.getServices();
    }

    /**
     * Add a service. If a service of the same type with the same algorithm
     * name exists, and it was added using {@link #putService putService()} or
     * {@link #put put()}, it is replaced by the new service. This method also
     * places information about this service in the provider's Hashtable
     * values in the format described in the {@extLink security_guide_jca
     * Java Cryptography Architecture (JCA) Reference Guide}.
     *
     * @param s the Service to add
     *
     * @throws NullPointerException if s is {@code null}
     *
     * @since 1.5
     */
    protected synchronized void putService(Service s) {
        checkInitialized();
        if (debug != null) {
            debug.println(name + ".putService(): " + s);
        }
        if (s == null) {
            throw new NullPointerException();
        }
        if (s.getProvider() != this) {
            throw new IllegalArgumentException
                    ("service.getProvider() must match this Provider object");
        }
        servicesMap.asCurrent().putService(s);
    }

    private void checkAndUpdateSecureRandom(ServiceKey algKey, boolean doAdd) {
        if (algKey.type.equalsIgnoreCase("SecureRandom")) {
            if (doAdd) {
                prngAlgos.add(algKey);
            } else {
                prngAlgos.remove(algKey);
            }
            if (debug != null) {
                debug.println((doAdd ? "Add" : "Remove") + " " + algKey);
            }
        }
    }

    // used by new SecureRandom() to find out the default SecureRandom
    // service for this provider
    Service getDefaultSecureRandomService() {
        checkInitialized();

        if (!prngAlgos.isEmpty()) {
            String algo = prngAlgos.iterator().next().originalAlgorithm;
            // IMPORTANT: use the Service obj returned by getService(...) call
            // as providers may override putService(...)/getService(...) and
            // return their own Service objects
            return getService("SecureRandom", algo);
        }

        return null;
    }

    /**
     * Remove a service previously added using {@link #putService putService()}
     * or {@link #put put()}. The specified service is removed from
     * this {@code Provider}. It will no longer be returned by
     * {@link #getService getService()} and its information will be removed
     * from this provider's Hashtable.
     *
     * @param s the Service to be removed
     *
     * @throws NullPointerException if s is {@code null}
     *
     * @since 1.5
     */
    protected synchronized void removeService(Service s) {
        checkInitialized();
        if (debug != null) {
            debug.println(name + ".removeService(): " + s);
        }
        if (s == null) {
            throw new NullPointerException();
        }
        servicesMap.asCurrent().removeService(s);
    }

    // Wrapped String that behaves in a case-insensitive way for equals/hashCode
    private static class UString {
        final String string;
        final String lowerString;

        UString(String s) {
            this.string = s;
            this.lowerString = s.toLowerCase(ENGLISH);
        }

        public int hashCode() {
            return lowerString.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            return obj instanceof UString other
                    && lowerString.equals(other.lowerString);
        }

        public String toString() {
            return string;
        }
    }

    // describe relevant properties of a type of engine
    private static class EngineDescription {
        final String name;
        final boolean supportsParameter;
        final Class<?> constructorParameterClass;

        EngineDescription(String name, boolean sp, Class<?> constructorParameterClass) {
            this.name = name;
            this.supportsParameter = sp;
            this.constructorParameterClass = constructorParameterClass;
        }
    }

    // built in knowledge of the engine types shipped as part of the JDK
    private static final Map<String,EngineDescription> knownEngines;

    private static void addEngine(String name, boolean sp, Class<?> constructorParameterClass) {
        EngineDescription ed = new EngineDescription(name, sp, constructorParameterClass);
        // also index by canonical name to avoid toLowerCase() for some lookups
        knownEngines.put(name.toLowerCase(ENGLISH), ed);
        knownEngines.put(name, ed);
    }

    static {
        knownEngines = new HashMap<>();
        // JCA
        addEngine("AlgorithmParameterGenerator",        false, null);
        addEngine("AlgorithmParameters",                false, null);
        addEngine("KeyFactory",                         false, null);
        addEngine("KeyPairGenerator",                   false, null);
        addEngine("KeyStore",                           false, null);
        addEngine("MessageDigest",                      false, null);
        addEngine("SecureRandom",                       false,
                SecureRandomParameters.class);
        addEngine("Signature",                          true,  null);
        addEngine("CertificateFactory",                 false, null);
        addEngine("CertPathBuilder",                    false, null);
        addEngine("CertPathValidator",                  false, null);
        addEngine("CertStore",                          false,
                CertStoreParameters.class);
        // JCE
        addEngine("Cipher",                             true,  null);
        addEngine("ExemptionMechanism",                 false, null);
        addEngine("Mac",                                true,  null);
        addEngine("KeyAgreement",                       true,  null);
        addEngine("KeyGenerator",                       false, null);
        addEngine("SecretKeyFactory",                   false, null);
        addEngine("KEM",                                true,  null);
        addEngine("KDF",                                false, KDFParameters.class);
        // JSSE
        addEngine("KeyManagerFactory",                  false, null);
        addEngine("SSLContext",                         false, null);
        addEngine("TrustManagerFactory",                false, null);
        // JGSS
        addEngine("GssApiMechanism",                    false, null);
        // SASL
        addEngine("SaslClientFactory",                  false, null);
        addEngine("SaslServerFactory",                  false, null);
        // POLICY
        @SuppressWarnings("removal")
        Class<Policy.Parameters> policyParams = Policy.Parameters.class;
        addEngine("Policy",                             false,
                policyParams);
        // CONFIGURATION
        addEngine("Configuration",                      false,
                Configuration.Parameters.class);
        // XML DSig
        addEngine("XMLSignatureFactory",                false, null);
        addEngine("KeyInfoFactory",                     false, null);
        addEngine("TransformService",                   false, null);
        // Smart Card I/O
        addEngine("TerminalFactory",                    false,
                            Object.class);
    }

    // get the "standard" (mixed-case) engine name for arbitrary case engine name
    // if there is no known engine by that name, return s
    private static String getEngineName(String s) {
        // try original case first, usually correct
        EngineDescription e = knownEngines.get(s);
        if (e == null) {
            e = knownEngines.get(s.toLowerCase(ENGLISH));
        }
        return (e == null) ? s : e.name;
    }

    /**
     * The description of a security service. It encapsulates the properties
     * of a service and contains a factory method to obtain new implementation
     * instances of this service.
     *
     * <p>Each service has a provider that offers the service, a type,
     * an algorithm name, and the name of the class that implements the
     * service. Optionally, it also includes a list of alternate algorithm
     * names for this service (aliases) and attributes, which are a map of
     * (name, value) {@code String} pairs.
     *
     * <p>This class defines the methods {@link #supportsParameter
     * supportsParameter()} and {@link #newInstance newInstance()}
     * which are used by the Java security framework when it searches for
     * suitable services and instantiates them. The valid arguments to those
     * methods depend on the type of service. For the service types defined
     * within Java SE, see the
     * {@extLink security_guide_jca
     * Java Cryptography Architecture (JCA) Reference Guide}
     * for the valid values.
     * Note that components outside of Java SE can define additional types of
     * services and their behavior.
     *
     * <p>Instances of this class are immutable.
     *
     * @since 1.5
     */
    public static class Service {
        private final String type;
        private final String algorithm;
        private String className;
        private final Provider provider;
        private List<String> aliases;
        private Map<UString, String> attributes;
        private final EngineDescription engineDescription;

        // For services added to a ServicesMap, their algorithm service key.
        // This value derives from the algorithm field. For services (still)
        // not added to a ServicesMap, value is null.
        private ServiceKey algKey;

        // For services added to a ServicesMap, this is a map from alias service
        // keys to alias string values. Empty map if no aliases. While map
        // entries derive from the aliases field, keys are not repeated
        // (case-insensitive comparison) and not equal to the algorithm. For
        // services (still) not added to a ServicesMap, value is an empty map.
        private Map<ServiceKey, String> aliasKeys;

        // Reference to the cached implementation Class object.
        // Will be a Class if this service is loaded from the built-in
        // classloader (unloading not possible), otherwise a WeakReference to a
        // Class
        private Object classCache;

        // Will be a Constructor if this service is loaded from the built-in
        // classloader (unloading not possible), otherwise a WeakReference to
        // a Constructor
        private Object constructorCache;

        // flag indicating whether this service has its attributes for
        // supportedKeyFormats or supportedKeyClasses set
        // if null, the values have not been initialized
        // if TRUE, at least one of supportedFormats/Classes is non-null
        private volatile Boolean hasKeyAttributes;

        // supported encoding formats
        private String[] supportedFormats;

        // names of the supported key (super) classes
        private Class<?>[] supportedClasses;

        // whether this service has been registered with the Provider
        private boolean registered;

        private static final Class<?>[] CLASS0 = new Class<?>[0];

        /*
         * Constructor used from the ServicesMap Legacy API.
         */
        private Service(Provider provider, ServiceKey algKey) {
            assert algKey.algorithm.intern() == algKey.algorithm :
                    "Algorithm should be interned.";
            this.provider = provider;
            this.algKey = algKey;
            algorithm = algKey.originalAlgorithm;
            type = algKey.type;
            engineDescription = knownEngines.get(type);
            aliases = Collections.emptyList();
            aliasKeys = Collections.emptyMap();
            attributes = Collections.emptyMap();
        }

        /*
         * Copy constructor used from the ServicesMap Legacy API for the
         * copy-on-write strategy. This constructor is invoked after every
         * update to a service on the ServicesMap.
         */
        private Service(Service svc) {
            provider = svc.provider;
            type = svc.type;
            algorithm = svc.algorithm;
            algKey = svc.algKey;
            className = svc.className;
            engineDescription = svc.engineDescription;
            if ((Object)svc.aliases == Collections.emptyList()) {
                aliases = Collections.emptyList();
                aliasKeys = Collections.emptyMap();
            } else {
                aliases = new ArrayList<>(svc.aliases);
                aliasKeys = new HashMap<>(svc.aliasKeys);
            }
            if ((Object)svc.attributes == Collections.emptyMap()) {
                attributes = Collections.emptyMap();
            } else {
                attributes = new HashMap<>(svc.attributes);
            }
            registered = false;

            // Do not copy cached fields because the updated service may have a
            // different class name or attributes and these values have to be
            // regenerated.
            classCache = null;
            constructorCache = null;
            hasKeyAttributes = null;
            supportedFormats = null;
            supportedClasses = null;
        }

        /*
         * Methods used from the ServicesMap Legacy API to update a service.
         */

        private void addAliasKey(ServiceKey aliasKey) {
            assert !aliasKey.equals(algKey) : "Alias key cannot be equal to " +
                    "the algorithm.";
            assert aliasKey.type.equals(type) : "Invalid alias key type.";
            assert aliasKey.algorithm.intern() == aliasKey.algorithm :
                    "Alias should be interned.";
            if ((Object)aliases == Collections.emptyList()) {
                aliases = new ArrayList<>(2);
                aliasKeys = new HashMap<>(2);
            } else if (aliasKeys.containsKey(aliasKey)) {
                // When overwriting aliases, remove first to handle differences
                // in alias string casing.
                removeAliasKey(aliasKey);
            }
            aliases.add(aliasKey.originalAlgorithm);
            aliasKeys.put(aliasKey, aliasKey.originalAlgorithm);
        }

        private void removeAliasKey(ServiceKey aliasKey) {
            assert aliasKeys.containsKey(aliasKey) &&
                    aliases.contains(aliasKeys.get(aliasKey)) :
                    "Removing nonexistent alias.";
            aliases.remove(aliasKeys.remove(aliasKey));
        }

        private void addAttribute(String attrName, String attrValue) {
            if ((Object)attributes == Collections.emptyMap()) {
                attributes = new HashMap<>(8);
            }
            attributes.put(new UString(attrName), attrValue);
        }

        private void removeAttribute(String attrName, String attrValue) {
            UString attrKey = new UString(attrName);
            assert attributes.get(attrKey) == attrValue :
                    "Attribute value expected to exist with the same identity.";
            attributes.remove(attrKey, attrValue);
        }

        /**
         * Construct a new service.
         *
         * @param provider the provider that offers this service
         * @param type the type of this service
         * @param algorithm the algorithm name
         * @param className the name of the class implementing this service
         * @param aliases List of aliases or {@code null} if algorithm has no
         *                   aliases
         * @param attributes Map of attributes or {@code null} if this
         *                   implementation has no attributes
         *
         * @throws NullPointerException if provider, type, algorithm, or
         * className is {@code null}
         */
        public Service(Provider provider, String type, String algorithm,
                String className, List<String> aliases,
                Map<String, String> attributes) {
            if ((provider == null) || (type == null) ||
                    (algorithm == null) || (className == null)) {
                throw new NullPointerException();
            }
            this.provider = provider;
            this.type = getEngineName(type);
            engineDescription = knownEngines.get(type);
            this.algorithm = algorithm;
            algKey = null;
            this.className = className;
            if (aliases == null) {
                this.aliases = Collections.emptyList();
            } else {
                this.aliases = new ArrayList<>(aliases);
            }
            aliasKeys = Collections.emptyMap();
            if (attributes == null) {
                this.attributes = Collections.emptyMap();
            } else {
                this.attributes = new HashMap<>();
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    this.attributes.put(new UString(entry.getKey()),
                            entry.getValue());
                }
            }
        }

        /*
         * When a Service is added to a ServicesMap with the Current API,
         * service and alias keys must be generated. Currently used by
         * ServicesMapImpl::putService. Legacy API methods do not need to call:
         * they generated the algorithm key at construction time and alias
         * keys with Service::addAliasKey.
         */
        private void generateServiceKeys() {
            if (algKey == null) {
                assert (Object)aliasKeys == Collections.emptyMap() :
                        "aliasKeys expected to be the empty map.";
                algKey = new ServiceKey(type, algorithm, true);
                aliasKeys = new HashMap<>(aliases.size());
                for (String alias : aliases) {
                    ServiceKey aliasKey = new ServiceKey(type, alias, true);
                    if (!aliasKey.equals(algKey)) {
                        aliasKeys.put(aliasKey, alias);
                    }
                }
                aliasKeys = Collections.unmodifiableMap(aliasKeys);
            }
        }

        /**
         * Get the type of this service. For example, {@code MessageDigest}.
         *
         * @return the type of this service
         */
        public final String getType() {
            return type;
        }

        /**
         * Return the name of the algorithm of this service. For example,
         * {@code SHA-1}.
         *
         * @return the algorithm of this service
         */
        public final String getAlgorithm() {
            return algorithm;
        }

        /**
         * Return the Provider of this service.
         *
         * @return the Provider of this service
         */
        public final Provider getProvider() {
            return provider;
        }

        /**
         * Return the name of the class implementing this service.
         *
         * @return the name of the class implementing this service
         */
        public final String getClassName() {
            return className;
        }

        /**
         * Return the value of the specified attribute or {@code null} if this
         * attribute is not set for this Service.
         *
         * @param name the name of the requested attribute
         *
         * @return the value of the specified attribute or {@code null} if the
         *         attribute is not present
         *
         * @throws NullPointerException if name is {@code null}
         */
        public final String getAttribute(String name) {
            if (name == null) {
                throw new NullPointerException();
            }
            return attributes.get(new UString(name));
        }

        /**
         * Return a new instance of the implementation described by this
         * service. The security provider framework uses this method to
         * construct implementations. Applications will typically not need
         * to call it.
         *
         * <p>The default implementation uses reflection to invoke the
         * standard constructor for this type of service.
         * Security providers can override this method to implement
         * instantiation in a different way.
         * For details and the values of constructorParameter that are
         * valid for the various types of services see the
         * {@extLink security_guide_jca
         * Java Cryptography Architecture (JCA) Reference Guide}.
         *
         * @param constructorParameter the value to pass to the constructor,
         * or {@code null} if this type of service does not use a
         * constructorParameter.
         *
         * @return a new implementation of this service
         *
         * @throws InvalidParameterException if the value of
         * constructorParameter is invalid for this type of service.
         * @throws NoSuchAlgorithmException if instantiation failed for
         * any other reason.
         */
        public Object newInstance(Object constructorParameter)
                throws NoSuchAlgorithmException {
            if (!registered) {
                if (provider.getService(type, algorithm) != this) {
                    throw new NoSuchAlgorithmException
                        ("Service not registered with Provider "
                        + provider.getName() + ": " + this);
                }
                registered = true;
            }
            Class<?> ctrParamClz;
            try {
                EngineDescription cap = engineDescription;
                if (cap == null) {
                    // unknown engine type, use generic code
                    // this is the code path future for non-core
                    // optional packages
                    ctrParamClz = constructorParameter == null?
                        null : constructorParameter.getClass();
                } else {
                    ctrParamClz = cap.constructorParameterClass;
                    if (constructorParameter != null) {
                        if (ctrParamClz == null) {
                            throw new InvalidParameterException
                                ("constructorParameter not used with " + type
                                + " engines");
                        } else {
                            Class<?> argClass = constructorParameter.getClass();
                            if (!ctrParamClz.isAssignableFrom(argClass)) {
                                throw new InvalidParameterException
                                    ("constructorParameter must be instanceof "
                                    + cap.constructorParameterClass.getName().replace('$', '.')
                                    + " for engine type " + type);
                            }
                        }
                    }
                }
                // constructorParameter can be null if not provided
                return newInstanceUtil(ctrParamClz, constructorParameter);
            } catch (NoSuchAlgorithmException e) {
                throw e;
            } catch (InvocationTargetException e) {
                throw new NoSuchAlgorithmException
                    ("Error constructing implementation (algorithm: "
                    + algorithm + ", provider: " + provider.getName()
                    + ", class: " + className + ")", e.getCause());
            } catch (Exception e) {
                throw new NoSuchAlgorithmException
                    ("Error constructing implementation (algorithm: "
                    + algorithm + ", provider: " + provider.getName()
                    + ", class: " + className + ")", e);
            }
        }

        private Object newInstanceOf() throws Exception {
            Constructor<?> con = getDefaultConstructor();
            return con.newInstance(EMPTY);
        }

        private Object newInstanceUtil(Class<?> ctrParamClz, Object ctorParamObj)
                throws Exception
        {
            if (ctrParamClz == null) {
                return newInstanceOf();
            } else {
                // Looking for the constructor with a params first and fallback
                // to one without if not found. This is to support the enhanced
                // SecureRandom where both styles of constructors are supported.
                // Before jdk9, there was no params support (only getInstance(alg))
                // and an impl only had the params-less constructor. Since jdk9,
                // there is getInstance(alg,params) and an impl can contain
                // an Impl(params) constructor.
                try {
                    Constructor<?> con = getImplClass().getConstructor(ctrParamClz);
                    return con.newInstance(ctorParamObj);
                } catch (NoSuchMethodException nsme) {
                    // For pre-jdk9 SecureRandom implementations, they only
                    // have params-less constructors which still works when
                    // the input ctorParamObj is null.
                    //
                    // For other primitives using params, ctorParamObj should not
                    // be null and nsme is thrown, just like before.
                    if (ctorParamObj == null) {
                        try {
                            return newInstanceOf();
                        } catch (NoSuchMethodException nsme2) {
                            nsme.addSuppressed(nsme2);
                            throw nsme;
                        }
                    } else {
                        throw nsme;
                    }
                }
            }
        }

        // return the implementation Class object for this service
        private Class<?> getImplClass() throws NoSuchAlgorithmException {
            try {
                Object cache = classCache;
                if (cache instanceof Class<?> clazz) {
                    return clazz;
                }
                Class<?> clazz = null;
                if (cache instanceof WeakReference<?> ref){
                    clazz = (Class<?>)ref.get();
                }
                if (clazz == null) {
                    ClassLoader cl = provider.getClass().getClassLoader();
                    if (cl == null) {
                        clazz = Class.forName(className);
                    } else {
                        clazz = cl.loadClass(className);
                    }
                    if (!Modifier.isPublic(clazz.getModifiers())) {
                        throw new NoSuchAlgorithmException
                            ("class configured for " + type + " (provider: " +
                            provider.getName() + ") is not public.");
                    }
                    classCache = (cl == null) ? clazz : new WeakReference<Class<?>>(clazz);
                }
                return clazz;
            } catch (ClassNotFoundException e) {
                throw new NoSuchAlgorithmException
                    ("class configured for " + type + " (provider: " +
                    provider.getName() + ") cannot be found.", e);
            }
        }

        private Constructor<?> getDefaultConstructor()
            throws NoSuchAlgorithmException, NoSuchMethodException
        {
            Object cache = constructorCache;
            if (cache instanceof Constructor<?> con) {
                return con;
            }
            Constructor<?> con = null;
            if (cache instanceof WeakReference<?> ref){
                con = (Constructor<?>)ref.get();
            }
            if (con == null) {
                Class<?> clazz = getImplClass();
                con = clazz.getConstructor();
                constructorCache = (clazz.getClassLoader() == null)
                        ? con : new WeakReference<Constructor<?>>(con);
            }
            return con;
        }

        /**
         * Test whether this Service can use the specified parameter.
         * Returns {@code false} if this service cannot use the parameter.
         * Returns {@code true} if this service can use the parameter,
         * if a fast test is infeasible, or if the status is unknown.
         *
         * <p>The security provider framework uses this method with
         * some types of services to quickly exclude non-matching
         * implementations for consideration.
         * Applications will typically not need to call it.
         *
         * <p>For details and the values of parameter that are valid for the
         * various types of services see the top of this class and the
         * {@extLink security_guide_jca
         * Java Cryptography Architecture (JCA) Reference Guide}.
         * Security providers can override it to implement their own test.
         *
         * @param parameter the parameter to test
         *
         * @return {@code false} if this service cannot use the specified
         * parameter; {@code true} if it can possibly use the parameter
         *
         * @throws InvalidParameterException if the value of parameter is
         * invalid for this type of service or if this method cannot be
         * used with this type of service
         */
        public boolean supportsParameter(Object parameter) {
            EngineDescription cap = engineDescription;
            if (cap == null) {
                // unknown engine type, return true by default
                return true;
            }
            if (!cap.supportsParameter) {
                throw new InvalidParameterException("supportsParameter() not "
                    + "used with " + type + " engines");
            }
            // allow null for keys without attributes for compatibility
            if ((parameter != null) && (!(parameter instanceof Key))) {
                throw new InvalidParameterException
                    ("Parameter must be instanceof Key for engine " + type);
            }
            if (!hasKeyAttributes()) {
                return true;
            }
            if (parameter == null) {
                return false;
            }
            Key key = (Key)parameter;
            if (supportsKeyFormat(key)) {
                return true;
            }
            return supportsKeyClass(key);
        }

        /**
         * Return whether this service has its supported properties for
         * keys defined. Parses the attributes if not yet initialized.
         */
        private boolean hasKeyAttributes() {
            Boolean b = hasKeyAttributes;
            if (b == null) {
                synchronized (this) {
                    b = hasKeyAttributes;
                    if (b == null) {
                        String s;
                        s = getAttribute("SupportedKeyFormats");
                        if (s != null) {
                            supportedFormats = s.split("\\|");
                        }
                        s = getAttribute("SupportedKeyClasses");
                        if (s != null) {
                            String[] classNames = s.split("\\|");
                            List<Class<?>> classList =
                                new ArrayList<>(classNames.length);
                            for (String className : classNames) {
                                Class<?> clazz = getKeyClass(className);
                                if (clazz != null) {
                                    classList.add(clazz);
                                }
                            }
                            supportedClasses = classList.toArray(CLASS0);
                        }
                        b = (supportedFormats != null)
                            || (supportedClasses != null);
                        hasKeyAttributes = b;
                    }
                }
            }
            return b.booleanValue();
        }

        // get the key class object of the specified name
        private Class<?> getKeyClass(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                // ignore
            }
            try {
                ClassLoader cl = provider.getClass().getClassLoader();
                if (cl != null) {
                    return cl.loadClass(name);
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
            return null;
        }

        private boolean supportsKeyFormat(Key key) {
            if (supportedFormats == null) {
                return false;
            }
            String format = key.getFormat();
            if (format == null) {
                return false;
            }
            for (String supportedFormat : supportedFormats) {
                if (supportedFormat.equals(format)) {
                    return true;
                }
            }
            return false;
        }

        private boolean supportsKeyClass(Key key) {
            if (supportedClasses == null) {
                return false;
            }
            Class<?> keyClass = key.getClass();
            for (Class<?> clazz : supportedClasses) {
                if (clazz.isAssignableFrom(keyClass)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Return a {@code String} representation of this service.
         *
         * @return a {@code String} representation of this service.
         */
        public String toString() {
            String aString = aliases.isEmpty()
                ? "" : "\r\n  aliases: " + aliases.toString();
            String attrs = attributes.isEmpty()
                ? "" : "\r\n  attributes: " + attributes.toString();
            return provider.getName() + ": " + type + "." + algorithm
                + " -> " + className + aString + attrs + "\r\n";
        }
    }
}
