/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.URL;

import jdk.internal.access.JavaSecurityPropertiesAccess;
import jdk.internal.event.EventHelper;
import jdk.internal.event.SecurityPropertyModificationEvent;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.StaticProperty;
import sun.security.util.Debug;
import sun.security.util.PropertyExpander;

import sun.security.jca.*;

/**
 * <p>This class centralizes all security properties and common security
 * methods. One of its primary uses is to manage providers.
 *
 * <p>The default values of security properties are read from an
 * implementation-specific location, which is typically the properties file
 * {@code conf/security/java.security} in the Java installation directory.
 *
 * @implNote If the properties file fails to load, the JDK implementation will
 * throw an unspecified error when initializing the {@code Security} class.
 *
 * @author Benjamin Renaud
 * @since 1.1
 */

public final class Security {

    /* Are we debugging? -- for developers */
    private static final Debug sdebug =
                        Debug.getInstance("properties");

    /* The java.security properties */
    private static Properties props;

    /* cache a copy for recording purposes */
    private static Properties initialSecurityProperties;

    // An element in the cache
    private static class ProviderProperty {
        String className;
        Provider provider;
    }

    static {
        // doPrivileged here because there are multiple
        // things in initialize that might require privs.
        // (the FileInputStream call and the File.exists call,
        // the securityPropFile call, etc)
        @SuppressWarnings("removal")
        var dummy = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            initialize();
            return null;
        });
        // Set up JavaSecurityPropertiesAccess in SharedSecrets
        SharedSecrets.setJavaSecurityPropertiesAccess(new JavaSecurityPropertiesAccess() {
            @Override
            public Properties getInitialProperties() {
                return initialSecurityProperties;
            }
        });
    }

    private static void initialize() {
        props = new Properties();
        boolean overrideAll = false;

        // first load the system properties file
        // to determine the value of security.overridePropertiesFile
        File propFile = securityPropFile("java.security");
        boolean success = loadProps(propFile, null, false);
        if (!success) {
            throw new InternalError("Error loading java.security file");
        }

        if ("true".equalsIgnoreCase(props.getProperty
                ("security.overridePropertiesFile"))) {

            String extraPropFile = System.getProperty
                    ("java.security.properties");
            if (extraPropFile != null && extraPropFile.startsWith("=")) {
                overrideAll = true;
                extraPropFile = extraPropFile.substring(1);
            }
            loadProps(null, extraPropFile, overrideAll);
        }
        initialSecurityProperties = (Properties) props.clone();
        if (sdebug != null) {
            for (String key : props.stringPropertyNames()) {
                sdebug.println("Initial security property: " + key + "=" +
                    props.getProperty(key));
            }
        }

    }

    private static boolean loadProps(File masterFile, String extraPropFile, boolean overrideAll) {
        InputStream is = null;
        try {
            if (masterFile != null && masterFile.exists()) {
                is = new FileInputStream(masterFile);
            } else if (extraPropFile != null) {
                extraPropFile = PropertyExpander.expand(extraPropFile);
                File propFile = new File(extraPropFile);
                URL propURL;
                if (propFile.exists()) {
                    propURL = newURL
                            ("file:" + propFile.getCanonicalPath());
                } else {
                    propURL = newURL(extraPropFile);
                }

                is = propURL.openStream();
                if (overrideAll) {
                    props = new Properties();
                    if (sdebug != null) {
                        sdebug.println
                                ("overriding other security properties files!");
                    }
                }
            } else {
                // unexpected
                return false;
            }
            props.load(is);
            if (sdebug != null) {
                // ExceptionInInitializerError if masterFile.getName() is
                // called here (NPE!). Leave as is (and few lines down)
                sdebug.println("reading security properties file: " +
                        masterFile == null ? extraPropFile : "java.security");
            }
            return true;
        } catch (IOException | PropertyExpander.ExpandException e) {
            if (sdebug != null) {
                sdebug.println("unable to load security properties from " +
                        masterFile == null ? extraPropFile : "java.security");
                e.printStackTrace();
            }
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    if (sdebug != null) {
                        sdebug.println("unable to close input stream");
                    }
                }
            }
        }
    }

    /**
     * Don't let anyone instantiate this.
     */
    private Security() {
    }

    private static File securityPropFile(String filename) {
        // maybe check for a system property which will specify where to
        // look. Someday.
        String sep = File.separator;
        return new File(StaticProperty.javaHome() + sep + "conf" + sep +
                        "security" + sep + filename);
    }

    /**
     * Looks up providers, and returns the property (and its associated
     * provider) mapping the key, if any.
     * The order in which the providers are looked up is the
     * provider-preference order, as specified in the security
     * properties file.
     */
    private static ProviderProperty getProviderProperty(String key) {

        List<Provider> providers = Providers.getProviderList().providers();
        for (int i = 0; i < providers.size(); i++) {

            String matchKey;
            Provider prov = providers.get(i);
            String prop = prov.getProperty(key);

            if (prop == null) {
                // Is there a match if we do a case-insensitive property name
                // comparison? Let's try ...
                for (Enumeration<Object> e = prov.keys();
                                e.hasMoreElements(); ) {
                    matchKey = (String)e.nextElement();
                    if (key.equalsIgnoreCase(matchKey)) {
                        prop = prov.getProperty(matchKey);
                        break;
                    }
                }
            }

            if (prop != null) {
                ProviderProperty newEntry = new ProviderProperty();
                newEntry.className = prop;
                newEntry.provider = prov;
                return newEntry;
            }
        }

        return null;
    }

    /**
     * Returns the property (if any) mapping the key for the given provider.
     */
    private static String getProviderProperty(String key, Provider provider) {
        String prop = provider.getProperty(key);
        if (prop == null) {
            // Is there a match if we do a case-insensitive property name
            // comparison? Let's try ...
            for (Enumeration<Object> e = provider.keys();
                                e.hasMoreElements(); ) {
                String matchKey = (String)e.nextElement();
                if (key.equalsIgnoreCase(matchKey)) {
                    prop = provider.getProperty(matchKey);
                    break;
                }
            }
        }
        return prop;
    }

    /**
     * Gets a specified property for an algorithm. The algorithm name
     * should be a standard name. See the <a href=
     * "{@docRoot}/../specs/security/standard-names.html">
     * Java Security Standard Algorithm Names Specification</a>
     * for information about standard algorithm names.
     *
     * One possible use is by specialized algorithm parsers, which may map
     * classes to algorithms which they understand (much like Key parsers
     * do).
     *
     * @param algName the algorithm name.
     *
     * @param propName the name of the property to get.
     *
     * @return the value of the specified property.
     *
     * @deprecated This method used to return the value of a proprietary
     * property in the master file of the "SUN" Cryptographic Service
     * Provider in order to determine how to parse algorithm-specific
     * parameters. Use the new provider-based and algorithm-independent
     * {@code AlgorithmParameters} and {@code KeyFactory} engine
     * classes (introduced in the J2SE version 1.2 platform) instead.
     */
    @Deprecated
    public static String getAlgorithmProperty(String algName,
                                              String propName) {
        ProviderProperty entry = getProviderProperty("Alg." + propName
                                                     + "." + algName);
        if (entry != null) {
            return entry.className;
        } else {
            return null;
        }
    }

    /**
     * Adds a new provider, at a specified position. The position is
     * the preference order in which providers are searched for
     * requested algorithms.  The position is 1-based, that is,
     * 1 is most preferred, followed by 2, and so on.  If the position
     * is less than 1 or greater than n, where n is the number of installed
     * providers, the provider (if not already installed) is inserted at
     * the end of the list, or at the n + 1 position.
     *
     * <p>If the given provider is installed at the requested position,
     * the provider that used to be at that position, and all providers
     * with a position greater than {@code position}, are shifted up
     * one position (towards the end of the list of installed providers).
     *
     * <p>A provider cannot be added if it is already installed.
     *
     * <p>If there is a security manager, the
     * {@link java.lang.SecurityManager#checkSecurityAccess} method is called
     * with the {@code "insertProvider"} permission target name to see if
     * it's ok to add a new provider. If this permission check is denied,
     * {@code checkSecurityAccess} is called again with the
     * {@code "insertProvider."+provider.getName()} permission target name. If
     * both checks are denied, a {@code SecurityException} is thrown.
     *
     * @param provider the provider to be added.
     *
     * @param position the preference position that the caller would
     * like for this provider.
     *
     * @return the actual preference position in which the provider was
     * added, or -1 if the provider was not added because it is
     * already installed.
     *
     * @throws  NullPointerException if provider is {@code null}
     * @throws  SecurityException
     *          if a security manager exists and its {@link
     *          java.lang.SecurityManager#checkSecurityAccess} method
     *          denies access to add a new provider
     *
     * @see #getProvider
     * @see #removeProvider
     * @see java.security.SecurityPermission
     */
    public static synchronized int insertProviderAt(Provider provider,
            int position) {
        String providerName = provider.getName();
        checkInsertProvider(providerName);
        ProviderList list = Providers.getFullProviderList();
        ProviderList newList = ProviderList.insertAt(list, provider, position - 1);
        if (list == newList) {
            return -1;
        }
        Providers.setProviderList(newList);
        return newList.getIndex(providerName) + 1;
    }

    /**
     * Adds a provider to the next position available.
     *
     * <p>If there is a security manager, the
     * {@link java.lang.SecurityManager#checkSecurityAccess} method is called
     * with the {@code "insertProvider"} permission target name to see if
     * it's ok to add a new provider. If this permission check is denied,
     * {@code checkSecurityAccess} is called again with the
     * {@code "insertProvider."+provider.getName()} permission target name. If
     * both checks are denied, a {@code SecurityException} is thrown.
     *
     * @param provider the provider to be added.
     *
     * @return the preference position in which the provider was
     * added, or -1 if the provider was not added because it is
     * already installed.
     *
     * @throws  NullPointerException if provider is {@code null}
     * @throws  SecurityException
     *          if a security manager exists and its {@link
     *          java.lang.SecurityManager#checkSecurityAccess} method
     *          denies access to add a new provider
     *
     * @see #getProvider
     * @see #removeProvider
     * @see java.security.SecurityPermission
     */
    public static int addProvider(Provider provider) {
        /*
         * We can't assign a position here because the statically
         * registered providers may not have been installed yet.
         * insertProviderAt() will fix that value after it has
         * loaded the static providers.
         */
        return insertProviderAt(provider, 0);
    }

    /**
     * Removes the provider with the specified name.
     *
     * <p>When the specified provider is removed, all providers located
     * at a position greater than where the specified provider was are shifted
     * down one position (towards the head of the list of installed
     * providers).
     *
     * <p>This method returns silently if the provider is not installed or
     * if name is {@code null}.
     *
     * <p>First, if there is a security manager, its
     * {@code checkSecurityAccess}
     * method is called with the string {@code "removeProvider."+name}
     * to see if it's ok to remove the provider.
     * If the default implementation of {@code checkSecurityAccess}
     * is used (i.e., that method is not overridden), then this will result in
     * a call to the security manager's {@code checkPermission} method
     * with a {@code SecurityPermission("removeProvider."+name)}
     * permission.
     *
     * @param name the name of the provider to remove.
     *
     * @throws  SecurityException
     *          if a security manager exists and its {@link
     *          java.lang.SecurityManager#checkSecurityAccess} method
     *          denies
     *          access to remove the provider
     *
     * @see #getProvider
     * @see #addProvider
     */
    public static synchronized void removeProvider(String name) {
        check("removeProvider." + name);
        ProviderList list = Providers.getFullProviderList();
        ProviderList newList = ProviderList.remove(list, name);
        Providers.setProviderList(newList);
    }

    /**
     * Returns an array containing all the installed providers. The order of
     * the providers in the array is their preference order.
     *
     * @return an array of all the installed providers.
     */
    public static Provider[] getProviders() {
        return Providers.getFullProviderList().toArray();
    }

    /**
     * Returns the provider installed with the specified name, if
     * any. Returns {@code null} if no provider with the specified name is
     * installed or if name is {@code null}.
     *
     * @param name the name of the provider to get.
     *
     * @return the provider of the specified name.
     *
     * @see #removeProvider
     * @see #addProvider
     */
    public static Provider getProvider(String name) {
        return Providers.getProviderList().getProvider(name);
    }

    /**
     * Returns an array containing all installed providers that satisfy the
     * specified selection criterion, or {@code null} if no such providers
     * have been installed. The returned providers are ordered
     * according to their
     * {@linkplain #insertProviderAt(java.security.Provider, int) preference order}.
     *
     * <p> A cryptographic service is always associated with a particular
     * algorithm or type. For example, a digital signature service is
     * always associated with a particular algorithm (e.g., DSA),
     * and a CertificateFactory service is always associated with
     * a particular certificate type (e.g., X.509).
     *
     * <p>The selection criterion must be specified in one of the following two
     * formats:
     * <ul>
     * <li> <i>{@literal <crypto_service>.<algorithm_or_type>}</i>
     * <p> The cryptographic service name must not contain any dots.
     * <p> A
     * provider satisfies the specified selection criterion iff the provider
     * implements the
     * specified algorithm or type for the specified cryptographic service.
     * <p> For example, "CertificateFactory.X.509"
     * would be satisfied by any provider that supplied
     * a CertificateFactory implementation for X.509 certificates.
     * <li> <i>{@literal <crypto_service>.<algorithm_or_type>
     * <attribute_name>:<attribute_value>}</i>
     * <p> The cryptographic service name must not contain any dots. There
     * must be one or more space characters between the
     * <i>{@literal <algorithm_or_type>}</i> and the
     * <i>{@literal <attribute_name>}</i>.
     *  <p> A provider satisfies this selection criterion iff the
     * provider implements the specified algorithm or type for the specified
     * cryptographic service and its implementation meets the
     * constraint expressed by the specified attribute name/value pair.
     * <p> For example, "Signature.SHA1withDSA KeySize:1024" would be
     * satisfied by any provider that implemented
     * the SHA1withDSA signature algorithm with a keysize of 1024 (or larger).
     *
     * </ul>
     *
     * <p> See the <a href=
     * "{@docRoot}/../specs/security/standard-names.html">
     * Java Security Standard Algorithm Names Specification</a>
     * for information about standard cryptographic service names, standard
     * algorithm names and standard attribute names.
     *
     * @param filter the criterion for selecting
     * providers. The filter is case-insensitive.
     *
     * @return all the installed providers that satisfy the selection
     * criterion, or {@code null} if no such providers have been installed.
     *
     * @throws InvalidParameterException
     *         if the filter is not in the required format
     * @throws NullPointerException if filter is {@code null}
     *
     * @see #getProviders(java.util.Map)
     * @since 1.3
     */
    public static Provider[] getProviders(String filter) {
        String key;
        String value;

        int index = filter.indexOf(':');

        if (index == -1) { // <crypto_service>.<algo_or_type> only
            key = filter.trim();
            value = "";
        } else {
            // <crypto_service>.<algo_or_type> <attr_name>:<attr_value>
            key = filter.substring(0, index).trim();
            value = filter.substring(index + 1).trim();
            // ensure value is not empty here; rest will be checked in Criteria
            if (value.isEmpty()) {
                throw new InvalidParameterException("Invalid filter");
            }
        }

        Hashtable<String, String> hashtableFilter = new Hashtable<>(1);
        hashtableFilter.put(key, value);

        return (getProviders(hashtableFilter));
    }

    /**
     * Returns an array containing all installed providers that satisfy the
     * specified selection criteria, or {@code null} if no such providers have
     * been installed. The returned providers are ordered
     * according to their
     * {@linkplain #insertProviderAt(java.security.Provider, int)
     * preference order}.
     *
     * <p>The selection criteria are represented by a map.
     * Each map entry represents a selection criterion.
     * A provider is selected iff it satisfies all selection
     * criteria. The key for any entry in such a map must be in one of the
     * following two formats:
     * <ul>
     * <li> <i>{@literal <crypto_service>.<algorithm_or_type>}</i>
     * <p> The cryptographic service name must not contain any dots.
     * <p> The value associated with the key must be an empty string.
     * <p> A provider
     * satisfies this selection criterion iff the provider implements the
     * specified algorithm or type for the specified cryptographic service.
     * <li>  <i>{@literal <crypto_service>}.
     * {@literal <algorithm_or_type> <attribute_name>}</i>
     * <p> The cryptographic service name must not contain any dots. There
     * must be one or more space characters between the
     * <i>{@literal <algorithm_or_type>}</i>
     * and the <i>{@literal <attribute_name>}</i>.
     * <p> The value associated with the key must be a non-empty string.
     * A provider satisfies this selection criterion iff the
     * provider implements the specified algorithm or type for the specified
     * cryptographic service and its implementation meets the
     * constraint expressed by the specified attribute name/value pair.
     * </ul>
     *
     * <p> See the <a href=
     * "{@docRoot}/../specs/security/standard-names.html">
     * Java Security Standard Algorithm Names Specification</a>
     * for information about standard cryptographic service names, standard
     * algorithm names and standard attribute names.
     *
     * @param filter the criteria for selecting
     * providers. The filter is case-insensitive.
     *
     * @return all the installed providers that satisfy the selection
     * criteria, or {@code null} if no such providers have been installed.
     *
     * @throws InvalidParameterException
     *         if the filter is not in the required format
     * @throws NullPointerException if filter is {@code null}
     *
     * @see #getProviders(java.lang.String)
     * @since 1.3
     */
    public static Provider[] getProviders(Map<String,String> filter) {
        // Get all installed providers first.
        // Then only return those providers who satisfy the selection criteria.
        Provider[] allProviders = Security.getProviders();
        Set<Map.Entry<String, String>> entries = filter.entrySet();

        if (allProviders == null || allProviders.length == 0) {
            return null;
        } else if (entries == null) {
            // return all installed providers if the selection criteria is null
            return allProviders;
        } else if (entries.isEmpty()) {
            // return null if the selection criteria is empty; this is to match
            // earlier behavior
            return null;
        }

        LinkedList<Provider> candidates =
                new LinkedList<>(Arrays.asList(allProviders));

        // For each selection criterion, remove providers
        // which don't satisfy the criterion from the candidate set.
        for (var e : entries) {
            Criteria cr = new Criteria(e.getKey(), e.getValue());
            candidates.removeIf(p -> !cr.isCriterionSatisfied(p));
            if (candidates.isEmpty()) {
                return null;
            }
        };

        return candidates.toArray(new Provider[0]);
    }

    // Map containing cached Spi Class objects of the specified type
    private static final Map<String, Class<?>> spiMap =
            new ConcurrentHashMap<>();

    /**
     * Return the Class object for the given engine type
     * (e.g. "MessageDigest"). Works for Spis in the java.security package
     * only.
     */
    private static Class<?> getSpiClass(String type) {
        Class<?> clazz = spiMap.get(type);
        if (clazz != null) {
            return clazz;
        }
        try {
            clazz = Class.forName("java.security." + type + "Spi");
            spiMap.put(type, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Spi class not found", e);
        }
    }

    /*
     * Returns an array of objects: the first object in the array is
     * an instance of an implementation of the requested algorithm
     * and type, and the second object in the array identifies the provider
     * of that implementation.
     * The {@code provider} argument can be {@code null}, in which case all
     * configured providers will be searched in order of preference.
     */
    static Object[] getImpl(String algorithm, String type, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null) {
            return GetInstance.getInstance
                (type, getSpiClass(type), algorithm).toArray();
        } else {
            return GetInstance.getInstance
                (type, getSpiClass(type), algorithm, provider).toArray();
        }
    }

    static Object[] getImpl(String algorithm, String type, String provider,
            Object params) throws NoSuchAlgorithmException,
            NoSuchProviderException, InvalidAlgorithmParameterException {
        if (provider == null) {
            return GetInstance.getInstance
                (type, getSpiClass(type), algorithm, params).toArray();
        } else {
            return GetInstance.getInstance
                (type, getSpiClass(type), algorithm, params, provider).toArray();
        }
    }

    /*
     * Returns an array of objects: the first object in the array is
     * an instance of an implementation of the requested algorithm
     * and type, and the second object in the array identifies the provider
     * of that implementation.
     * The {@code provider} argument cannot be {@code null}.
     */
    static Object[] getImpl(String algorithm, String type, Provider provider)
            throws NoSuchAlgorithmException {
        return GetInstance.getInstance
            (type, getSpiClass(type), algorithm, provider).toArray();
    }

    static Object[] getImpl(String algorithm, String type, Provider provider,
            Object params) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        return GetInstance.getInstance
            (type, getSpiClass(type), algorithm, params, provider).toArray();
    }

    /**
     * Gets a security property value.
     *
     * <p>First, if there is a security manager, its
     * {@code checkPermission}  method is called with a
     * {@code java.security.SecurityPermission("getProperty."+key)}
     * permission to see if it's ok to retrieve the specified
     * security property value.
     *
     * @param key the key of the property being retrieved.
     *
     * @return the value of the security property, or {@code null} if there
     *          is no property with that key.
     *
     * @throws  SecurityException
     *          if a security manager exists and its {@link
     *          java.lang.SecurityManager#checkPermission} method
     *          denies
     *          access to retrieve the specified security property value
     * @throws  NullPointerException if key is {@code null}
     *
     * @see #setProperty
     * @see java.security.SecurityPermission
     */
    public static String getProperty(String key) {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SecurityPermission("getProperty."+
                                                      key));
        }
        String name = props.getProperty(key);
        if (name != null)
            name = name.trim(); // could be a class name with trailing ws
        return name;
    }

    /**
     * Sets a security property value.
     *
     * <p>First, if there is a security manager, its
     * {@code checkPermission} method is called with a
     * {@code java.security.SecurityPermission("setProperty."+key)}
     * permission to see if it's ok to set the specified
     * security property value.
     *
     * @param key the name of the property to be set.
     *
     * @param datum the value of the property to be set.
     *
     * @throws  SecurityException
     *          if a security manager exists and its {@link
     *          java.lang.SecurityManager#checkPermission} method
     *          denies access to set the specified security property value
     * @throws  NullPointerException if key or datum is {@code null}
     *
     * @see #getProperty
     * @see java.security.SecurityPermission
     */
    public static void setProperty(String key, String datum) {
        check("setProperty." + key);
        props.put(key, datum);
        invalidateSMCache(key);  /* See below. */

        SecurityPropertyModificationEvent spe = new SecurityPropertyModificationEvent();
        // following is a no-op if event is disabled
        spe.key = key;
        spe.value = datum;
        spe.commit();

        if (EventHelper.isLoggingSecurity()) {
            EventHelper.logSecurityPropertyEvent(key, datum);
        }
    }

    /*
     * Implementation detail:  If the property we just set in
     * setProperty() was either "package.access" or
     * "package.definition", we need to signal to the SecurityManager
     * class that the value has just changed, and that it should
     * invalidate its local cache values.
     */
    private static void invalidateSMCache(String key) {

        final boolean pa = key.equals("package.access");
        final boolean pd = key.equals("package.definition");

        if (pa || pd) {
            SharedSecrets.getJavaLangAccess().invalidatePackageAccessCache();
        }
    }

    private static void check(String directive) {
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkSecurityAccess(directive);
        }
    }

    private static void checkInsertProvider(String name) {
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            try {
                security.checkSecurityAccess("insertProvider");
            } catch (SecurityException se1) {
                try {
                    security.checkSecurityAccess("insertProvider." + name);
                } catch (SecurityException se2) {
                    // throw first exception, but add second to suppressed
                    se1.addSuppressed(se2);
                    throw se1;
                }
            }
        }
    }

    private static class Criteria {
        private final String serviceName;
        private final String algName;
        private final String attrName;
        private final String attrValue;

        Criteria(String key, String value) throws InvalidParameterException {

            int snEndIndex = key.indexOf('.');
            if (snEndIndex <= 0) {
                // There must be a dot in the filter, and the dot
                // shouldn't be at the beginning of this string.
                throw new InvalidParameterException("Invalid filter");
            }

            serviceName = key.substring(0, snEndIndex);
            attrValue = value;

            if (value.isEmpty()) {
                // value is empty. So the key should be in the format of
                // <crypto_service>.<algorithm_or_type>.
                algName = key.substring(snEndIndex + 1);
                attrName = null;
            } else {
                // value is non-empty. So the key must be in the format
                // of <crypto_service>.<algorithm_or_type>(one or more
                // spaces)<attribute_name>
                int algEndIndex = key.indexOf(' ', snEndIndex);
                if (algEndIndex == -1) {
                    throw new InvalidParameterException
                            ("Invalid filter - need algorithm name");
                }
                algName = key.substring(snEndIndex + 1, algEndIndex);
                attrName = key.substring(algEndIndex + 1).trim();
                if (attrName.isEmpty()) {
                    throw new InvalidParameterException
                            ("Invalid filter - need attribute name");
                } else if (isCompositeValue() && attrValue.indexOf('|') != -1) {
                    throw new InvalidParameterException
                            ("Invalid filter - composite values unsupported");
                }
            }

            // check required values
            if (serviceName.isEmpty() || algName.isEmpty()) {
                throw new InvalidParameterException
                        ("Invalid filter - need service and algorithm");
            }
        }

        // returns true when this criteria contains a standard attribute
        // whose value may be composite, i.e. multiple values separated by "|"
        private boolean isCompositeValue() {
            return (attrName != null &&
                    (attrName.equalsIgnoreCase("SupportedKeyClasses") ||
                    attrName.equalsIgnoreCase("SupportedPaddings") ||
                    attrName.equalsIgnoreCase("SupportedModes") ||
                    attrName.equalsIgnoreCase("SupportedKeyFormats")));
        }

        /*
         * Returns {@code true} if the given provider satisfies
         * the selection criterion key:value.
         */
        private boolean isCriterionSatisfied(Provider prov) {
            // Constructed key have ONLY 1 space between algName and attrName
            String key = serviceName + '.' + algName +
                    (attrName != null ? (' ' + attrName) : "");

            // Check whether the provider has a property
            // whose key is the same as the given key.
            String propValue = getProviderProperty(key, prov);

            if (propValue == null) {
                // Check whether we have an alias instead
                // of a standard name in the key.
                String standardName = getProviderProperty("Alg.Alias." +
                        serviceName + "." + algName, prov);
                if (standardName != null) {
                    key = serviceName + "." + standardName +
                            (attrName != null ? ' ' + attrName : "");
                    propValue = getProviderProperty(key, prov);
                }

                if (propValue == null) {
                    // The provider doesn't have the given
                    // key in its property list.
                    return false;
                }
            }

            // If the key is in the format of:
            // <crypto_service>.<algorithm_or_type>,
            // there is no need to check the value.
            if (attrName == null) {
                return true;
            }

            // If we get here, the key must be in the
            // format of <crypto_service>.<algorithm_or_type> <attribute_name>.

            // Check the "Java Security Standard Algorithm Names" guide for the
            // list of supported Service Attributes

            // For KeySize, prop is the max key size the provider supports
            // for a specific <crypto_service>.<algorithm>.
            if (attrName.equalsIgnoreCase("KeySize")) {
                int requestedSize = Integer.parseInt(attrValue);
                int maxSize = Integer.parseInt(propValue);
                return requestedSize <= maxSize;
            }

            // Handle attributes with composite values
            if (isCompositeValue()) {
                String attrValue2 = attrValue.toUpperCase(Locale.ENGLISH);
                propValue = propValue.toUpperCase(Locale.ENGLISH);

                // match value to the property components
                String[] propComponents = propValue.split("\\|");
                for (String pc : propComponents) {
                    if (attrValue2.equals(pc)) return true;
                }
                return false;
            } else {
                // direct string compare (ignore case)
                return attrValue.equalsIgnoreCase(propValue);
            }
        }
    }

    /**
     * Returns a Set of {@code String} objects containing the names of all
     * available algorithms or types for the specified Java cryptographic
     * service (e.g., {@code Signature}, {@code MessageDigest}, {@code Cipher},
     * {@code Mac}, {@code KeyStore}).
     * Returns an empty set if there is no provider that supports the
     * specified service or if {@code serviceName} is {@code null}.
     * For a complete list of Java cryptographic services, please see the
     * {@extLink security_guide_jca
     * Java Cryptography Architecture (JCA) Reference Guide}.
     * Note: the returned set is immutable.
     *
     * @param serviceName the name of the Java cryptographic
     * service (e.g., {@code Signature}, {@code MessageDigest}, {@code Cipher},
     * {@code Mac}, {@code KeyStore}).
     * Note: this parameter is case-insensitive.
     *
     * @return a Set of {@code String} objects containing the names of all
     * available algorithms or types for the specified Java cryptographic
     * service or an empty set if no provider supports the specified service.
     *
     * @since 1.4
     */
    public static Set<String> getAlgorithms(String serviceName) {

        if ((serviceName == null) || (serviceName.isEmpty()) ||
            (serviceName.endsWith("."))) {
            return Collections.emptySet();
        }

        HashSet<String> result = new HashSet<>();
        Provider[] providers = Security.getProviders();

        for (int i = 0; i < providers.length; i++) {
            // Check the keys for each provider.
            for (Enumeration<Object> e = providers[i].keys();
                                                e.hasMoreElements(); ) {
                String currentKey =
                        ((String)e.nextElement()).toUpperCase(Locale.ENGLISH);
                if (currentKey.startsWith(
                        serviceName.toUpperCase(Locale.ENGLISH))) {
                    // We should skip the currentKey if it contains a
                    // whitespace. The reason is: such an entry in the
                    // provider property contains attributes for the
                    // implementation of an algorithm. We are only interested
                    // in entries which lead to the implementation
                    // classes.
                    if (currentKey.indexOf(' ') < 0) {
                        result.add(currentKey.substring(
                                                serviceName.length() + 1));
                    }
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings("deprecation")
    private static URL newURL(String spec) throws MalformedURLException {
        return new URL(spec);
    }
}
