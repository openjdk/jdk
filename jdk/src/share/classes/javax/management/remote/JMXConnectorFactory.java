/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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

package javax.management.remote;

import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.StringTokenizer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.sun.jmx.remote.util.ClassLogger;
import com.sun.jmx.remote.util.EnvHelp;
import sun.reflect.misc.ReflectUtil;


/**
 * <p>Factory to create JMX API connector clients.  There
 * are no instances of this class.</p>
 *
 * <p>Connections are usually made using the {@link
 * #connect(JMXServiceURL) connect} method of this class.  More
 * advanced applications can separate the creation of the connector
 * client, using {@link #newJMXConnector(JMXServiceURL, Map)
 * newJMXConnector} and the establishment of the connection itself, using
 * {@link JMXConnector#connect(Map)}.</p>
 *
 * <p>Each client is created by an instance of {@link
 * JMXConnectorProvider}.  This instance is found as follows.  Suppose
 * the given {@link JMXServiceURL} looks like
 * <code>"service:jmx:<em>protocol</em>:<em>remainder</em>"</code>.
 * Then the factory will attempt to find the appropriate {@link
 * JMXConnectorProvider} for <code><em>protocol</em></code>.  Each
 * occurrence of the character <code>+</code> or <code>-</code> in
 * <code><em>protocol</em></code> is replaced by <code>.</code> or
 * <code>_</code>, respectively.</p>
 *
 * <p>A <em>provider package list</em> is searched for as follows:</p>
 *
 * <ol>
 *
 * <li>If the <code>environment</code> parameter to {@link
 * #newJMXConnector(JMXServiceURL, Map) newJMXConnector} contains the
 * key <code>jmx.remote.protocol.provider.pkgs</code> then the
 * associated value is the provider package list.
 *
 * <li>Otherwise, if the system property
 * <code>jmx.remote.protocol.provider.pkgs</code> exists, then its value
 * is the provider package list.
 *
 * <li>Otherwise, there is no provider package list.
 *
 * </ol>
 *
 * <p>The provider package list is a string that is interpreted as a
 * list of non-empty Java package names separated by vertical bars
 * (<code>|</code>).  If the string is empty, then so is the provider
 * package list.  If the provider package list is not a String, or if
 * it contains an element that is an empty string, a {@link
 * JMXProviderException} is thrown.</p>
 *
 * <p>If the provider package list exists and is not empty, then for
 * each element <code><em>pkg</em></code> of the list, the factory
 * will attempt to load the class
 *
 * <blockquote>
 * <code><em>pkg</em>.<em>protocol</em>.ClientProvider</code>
 * </blockquote>

 * <p>If the <code>environment</code> parameter to {@link
 * #newJMXConnector(JMXServiceURL, Map) newJMXConnector} contains the
 * key <code>jmx.remote.protocol.provider.class.loader</code> then the
 * associated value is the class loader to use to load the provider.
 * If the associated value is not an instance of {@link
 * java.lang.ClassLoader}, an {@link
 * java.lang.IllegalArgumentException} is thrown.</p>
 *
 * <p>If the <code>jmx.remote.protocol.provider.class.loader</code>
 * key is not present in the <code>environment</code> parameter, the
 * calling thread's context class loader is used.</p>
 *
 * <p>If the attempt to load this class produces a {@link
 * ClassNotFoundException}, the search for a handler continues with
 * the next element of the list.</p>
 *
 * <p>Otherwise, a problem with the provider found is signalled by a
 * {@link JMXProviderException} whose {@link
 * JMXProviderException#getCause() <em>cause</em>} indicates the underlying
 * exception, as follows:</p>
 *
 * <ul>
 *
 * <li>if the attempt to load the class produces an exception other
 * than <code>ClassNotFoundException</code>, that is the
 * <em>cause</em>;
 *
 * <li>if {@link Class#newInstance()} for the class produces an
 * exception, that is the <em>cause</em>.
 *
 * </ul>
 *
 * <p>If no provider is found by the above steps, including the
 * default case where there is no provider package list, then the
 * implementation will use its own provider for
 * <code><em>protocol</em></code>, or it will throw a
 * <code>MalformedURLException</code> if there is none.  An
 * implementation may choose to find providers by other means.  For
 * example, it may support the <a
 * href="{@docRoot}/../technotes/guides/jar/jar.html#Service Provider">
 * JAR conventions for service providers</a>, where the service
 * interface is <code>JMXConnectorProvider</code>.</p>
 *
 * <p>Every implementation must support the RMI connector protocol with
 * the default RMI transport, specified with string <code>rmi</code>.
 * An implementation may optionally support the RMI connector protocol
 * with the RMI/IIOP transport, specified with the string
 * <code>iiop</code>.</p>
 *
 * <p>Once a provider is found, the result of the
 * <code>newJMXConnector</code> method is the result of calling {@link
 * JMXConnectorProvider#newJMXConnector(JMXServiceURL,Map) newJMXConnector}
 * on the provider.</p>
 *
 * <p>The <code>Map</code> parameter passed to the
 * <code>JMXConnectorProvider</code> is a new read-only
 * <code>Map</code> that contains all the entries that were in the
 * <code>environment</code> parameter to {@link
 * #newJMXConnector(JMXServiceURL,Map)
 * JMXConnectorFactory.newJMXConnector}, if there was one.
 * Additionally, if the
 * <code>jmx.remote.protocol.provider.class.loader</code> key is not
 * present in the <code>environment</code> parameter, it is added to
 * the new read-only <code>Map</code>.  The associated value is the
 * calling thread's context class loader.</p>
 *
 * @since 1.5
 */
public class JMXConnectorFactory {

    /**
     * <p>Name of the attribute that specifies the default class
     * loader. This class loader is used to deserialize return values and
     * exceptions from remote <code>MBeanServerConnection</code>
     * calls.  The value associated with this attribute is an instance
     * of {@link ClassLoader}.</p>
     */
    public static final String DEFAULT_CLASS_LOADER =
        "jmx.remote.default.class.loader";

    /**
     * <p>Name of the attribute that specifies the provider packages
     * that are consulted when looking for the handler for a protocol.
     * The value associated with this attribute is a string with
     * package names separated by vertical bars (<code>|</code>).</p>
     */
    public static final String PROTOCOL_PROVIDER_PACKAGES =
        "jmx.remote.protocol.provider.pkgs";

    /**
     * <p>Name of the attribute that specifies the class
     * loader for loading protocol providers.
     * The value associated with this attribute is an instance
     * of {@link ClassLoader}.</p>
     */
    public static final String PROTOCOL_PROVIDER_CLASS_LOADER =
        "jmx.remote.protocol.provider.class.loader";

    private static final String PROTOCOL_PROVIDER_DEFAULT_PACKAGE =
        "com.sun.jmx.remote.protocol";

    private static final ClassLogger logger =
        new ClassLogger("javax.management.remote.misc", "JMXConnectorFactory");

    /** There are no instances of this class.  */
    private JMXConnectorFactory() {
    }

    /**
     * <p>Creates a connection to the connector server at the given
     * address.</p>
     *
     * <p>This method is equivalent to {@link
     * #connect(JMXServiceURL,Map) connect(serviceURL, null)}.</p>
     *
     * @param serviceURL the address of the connector server to
     * connect to.
     *
     * @return a <code>JMXConnector</code> whose {@link
     * JMXConnector#connect connect} method has been called.
     *
     * @exception NullPointerException if <code>serviceURL</code> is null.
     *
     * @exception IOException if the connector client or the
     * connection cannot be made because of a communication problem.
     *
     * @exception SecurityException if the connection cannot be made
     * for security reasons.
     */
    public static JMXConnector connect(JMXServiceURL serviceURL)
            throws IOException {
        return connect(serviceURL, null);
    }

    /**
     * <p>Creates a connection to the connector server at the given
     * address.</p>
     *
     * <p>This method is equivalent to:</p>
     *
     * <pre>
     * JMXConnector conn = JMXConnectorFactory.newJMXConnector(serviceURL,
     *                                                         environment);
     * conn.connect(environment);
     * </pre>
     *
     * @param serviceURL the address of the connector server to connect to.
     *
     * @param environment a set of attributes to determine how the
     * connection is made.  This parameter can be null.  Keys in this
     * map must be Strings.  The appropriate type of each associated
     * value depends on the attribute.  The contents of
     * <code>environment</code> are not changed by this call.
     *
     * @return a <code>JMXConnector</code> representing the newly-made
     * connection.  Each successful call to this method produces a
     * different object.
     *
     * @exception NullPointerException if <code>serviceURL</code> is null.
     *
     * @exception IOException if the connector client or the
     * connection cannot be made because of a communication problem.
     *
     * @exception SecurityException if the connection cannot be made
     * for security reasons.
     */
    public static JMXConnector connect(JMXServiceURL serviceURL,
                                       Map<String,?> environment)
            throws IOException {
        if (serviceURL == null)
            throw new NullPointerException("Null JMXServiceURL");
        JMXConnector conn = newJMXConnector(serviceURL, environment);
        conn.connect(environment);
        return conn;
    }

    private static <K,V> Map<K,V> newHashMap() {
        return new HashMap<K,V>();
    }

    private static <K> Map<K,Object> newHashMap(Map<K,?> map) {
        return new HashMap<K,Object>(map);
    }

    /**
     * <p>Creates a connector client for the connector server at the
     * given address.  The resultant client is not connected until its
     * {@link JMXConnector#connect(Map) connect} method is called.</p>
     *
     * @param serviceURL the address of the connector server to connect to.
     *
     * @param environment a set of attributes to determine how the
     * connection is made.  This parameter can be null.  Keys in this
     * map must be Strings.  The appropriate type of each associated
     * value depends on the attribute.  The contents of
     * <code>environment</code> are not changed by this call.
     *
     * @return a <code>JMXConnector</code> representing the new
     * connector client.  Each successful call to this method produces
     * a different object.
     *
     * @exception NullPointerException if <code>serviceURL</code> is null.
     *
     * @exception IOException if the connector client cannot be made
     * because of a communication problem.
     *
     * @exception MalformedURLException if there is no provider for the
     * protocol in <code>serviceURL</code>.
     *
     * @exception JMXProviderException if there is a provider for the
     * protocol in <code>serviceURL</code> but it cannot be used for
     * some reason.
     */
    public static JMXConnector newJMXConnector(JMXServiceURL serviceURL,
                                               Map<String,?> environment)
            throws IOException {

        final Map<String,Object> envcopy;
        if (environment == null)
            envcopy = newHashMap();
        else {
            EnvHelp.checkAttributes(environment);
            envcopy = newHashMap(environment);
        }

        final ClassLoader loader = resolveClassLoader(envcopy);
        final Class<JMXConnectorProvider> targetInterface =
                JMXConnectorProvider.class;
        final String protocol = serviceURL.getProtocol();
        final String providerClassName = "ClientProvider";
        final JMXServiceURL providerURL = serviceURL;

        JMXConnectorProvider provider = getProvider(providerURL, envcopy,
                                               providerClassName,
                                               targetInterface,
                                               loader);

        IOException exception = null;
        if (provider == null) {
            // Loader is null when context class loader is set to null
            // and no loader has been provided in map.
            // com.sun.jmx.remote.util.Service class extracted from j2se
            // provider search algorithm doesn't handle well null classloader.
            if (loader != null) {
                try {
                    JMXConnector connection =
                        getConnectorAsService(loader, providerURL, envcopy);
                    if (connection != null)
                        return connection;
                } catch (JMXProviderException e) {
                    throw e;
                } catch (IOException e) {
                    exception = e;
                }
            }
            provider = getProvider(protocol, PROTOCOL_PROVIDER_DEFAULT_PACKAGE,
                            JMXConnectorFactory.class.getClassLoader(),
                            providerClassName, targetInterface);
        }

        if (provider == null) {
            MalformedURLException e =
                new MalformedURLException("Unsupported protocol: " + protocol);
            if (exception == null) {
                throw e;
            } else {
                throw EnvHelp.initCause(e, exception);
            }
        }

        final Map<String,Object> fixedenv =
                Collections.unmodifiableMap(envcopy);

        return provider.newJMXConnector(serviceURL, fixedenv);
    }

    private static String resolvePkgs(Map<String, ?> env)
            throws JMXProviderException {

        Object pkgsObject = null;

        if (env != null)
            pkgsObject = env.get(PROTOCOL_PROVIDER_PACKAGES);

        if (pkgsObject == null)
            pkgsObject =
                AccessController.doPrivileged(new PrivilegedAction<String>() {
                    public String run() {
                        return System.getProperty(PROTOCOL_PROVIDER_PACKAGES);
                    }
                });

        if (pkgsObject == null)
            return null;

        if (!(pkgsObject instanceof String)) {
            final String msg = "Value of " + PROTOCOL_PROVIDER_PACKAGES +
                " parameter is not a String: " +
                pkgsObject.getClass().getName();
            throw new JMXProviderException(msg);
        }

        final String pkgs = (String) pkgsObject;
        if (pkgs.trim().equals(""))
            return null;

        // pkgs may not contain an empty element
        if (pkgs.startsWith("|") || pkgs.endsWith("|") ||
            pkgs.indexOf("||") >= 0) {
            final String msg = "Value of " + PROTOCOL_PROVIDER_PACKAGES +
                " contains an empty element: " + pkgs;
            throw new JMXProviderException(msg);
        }

        return pkgs;
    }

    static <T> T getProvider(JMXServiceURL serviceURL,
                             final Map<String, Object> environment,
                             String providerClassName,
                             Class<T> targetInterface,
                             final ClassLoader loader)
            throws IOException {

        final String protocol = serviceURL.getProtocol();

        final String pkgs = resolvePkgs(environment);

        T instance = null;

        if (pkgs != null) {
            instance =
                getProvider(protocol, pkgs, loader, providerClassName,
                            targetInterface);

            if (instance != null) {
                boolean needsWrap = (loader != instance.getClass().getClassLoader());
                environment.put(PROTOCOL_PROVIDER_CLASS_LOADER, needsWrap ? wrap(loader) : loader);
            }
        }

        return instance;
    }

    static <T> Iterator<T> getProviderIterator(final Class<T> providerClass,
                                               final ClassLoader loader) {
       ServiceLoader<T> serviceLoader =
                ServiceLoader.load(providerClass, loader);
       return serviceLoader.iterator();
    }

    private static ClassLoader wrap(final ClassLoader parent) {
        return parent != null ? AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return new ClassLoader(parent) {
                    @Override
                    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                        ReflectUtil.checkPackageAccess(name);
                        return super.loadClass(name, resolve);
                    }
                };
            }
        }) : null;
    }

    private static JMXConnector getConnectorAsService(ClassLoader loader,
                                                      JMXServiceURL url,
                                                      Map<String, ?> map)
        throws IOException {

        Iterator<JMXConnectorProvider> providers =
                getProviderIterator(JMXConnectorProvider.class, loader);
        JMXConnector connection;
        IOException exception = null;
        while (providers.hasNext()) {
            JMXConnectorProvider provider = providers.next();
            try {
                connection = provider.newJMXConnector(url, map);
                return connection;
            } catch (JMXProviderException e) {
                throw e;
            } catch (Exception e) {
                if (logger.traceOn())
                    logger.trace("getConnectorAsService",
                                 "URL[" + url +
                                 "] Service provider exception: " + e);
                if (!(e instanceof MalformedURLException)) {
                    if (exception == null) {
                        if (e instanceof IOException) {
                            exception = (IOException) e;
                        } else {
                            exception = EnvHelp.initCause(
                                new IOException(e.getMessage()), e);
                        }
                    }
                }
                continue;
            }
        }
        if (exception == null)
            return null;
        else
            throw exception;
    }

    static <T> T getProvider(String protocol,
                              String pkgs,
                              ClassLoader loader,
                              String providerClassName,
                              Class<T> targetInterface)
            throws IOException {

        StringTokenizer tokenizer = new StringTokenizer(pkgs, "|");

        while (tokenizer.hasMoreTokens()) {
            String pkg = tokenizer.nextToken();
            String className = (pkg + "." + protocol2package(protocol) +
                                "." + providerClassName);
            Class<?> providerClass;
            try {
                providerClass = Class.forName(className, true, loader);
            } catch (ClassNotFoundException e) {
                //Add trace.
                continue;
            }

            if (!targetInterface.isAssignableFrom(providerClass)) {
                final String msg =
                    "Provider class does not implement " +
                    targetInterface.getName() + ": " +
                    providerClass.getName();
                throw new JMXProviderException(msg);
            }

            // We have just proved that this cast is correct
            Class<? extends T> providerClassT = Util.cast(providerClass);
            try {
                return providerClassT.newInstance();
            } catch (Exception e) {
                final String msg =
                    "Exception when instantiating provider [" + className +
                    "]";
                throw new JMXProviderException(msg, e);
            }
        }

        return null;
    }

    static ClassLoader resolveClassLoader(Map<String, ?> environment) {
        ClassLoader loader = null;

        if (environment != null) {
            try {
                loader = (ClassLoader)
                    environment.get(PROTOCOL_PROVIDER_CLASS_LOADER);
            } catch (ClassCastException e) {
                final String msg =
                    "The ClassLoader supplied in the environment map using " +
                    "the " + PROTOCOL_PROVIDER_CLASS_LOADER +
                    " attribute is not an instance of java.lang.ClassLoader";
                throw new IllegalArgumentException(msg);
            }
        }

        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }

        return loader;
    }

    private static String protocol2package(String protocol) {
        return protocol.replace('+', '.').replace('-', '_');
    }
}
