/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.management;

import com.sun.jmx.interceptor.SingleMBeanForwarder;
import com.sun.jmx.namespace.RoutingConnectionProxy;
import com.sun.jmx.namespace.RoutingProxy;
import com.sun.jmx.namespace.RoutingServerProxy;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaceMBean;
import javax.management.namespace.MBeanServerSupport;
import javax.management.remote.IdentityMBeanServerForwarder;
import javax.management.remote.MBeanServerForwarder;

/**
 * <p>Methods to communicate a client context to MBeans.  A context is
 * a {@literal Map<String, String>} that is provided by the client and
 * that an MBean can consult using the {@link #getContext()} method.
 * The context is set on a per-thread basis and can be consulted by any
 * code that the target MBean calls within the thread.</p>
 *
 * <p>One common usage of client context is to communicate the client's
 * {@link Locale} to MBeans.  For example, if an MBean has a String attribute
 * {@code LastProblemDescription}, the value of that attribute could be
 * a description of the last problem encountered by the MBean, translated
 * into the client's locale.  Different clients accessing this attribute
 * from different locales would each see the appropriate version for their
 * locale.</p>
 *
 * <p>The locale case is sufficiently important that it has a special
 * shorthand, the {@link #getLocale()} method.  This method calls
 * <code>{@link #getContext()}.get({@link #LOCALE_KEY})</code> and converts the
 * resultant String into a Locale object.</p>
 *
 * <p>Here is what an MBean with a localized {@code LastProblemDescription}
 * attribute might look like:</p>
 *
 * <pre>
 * public class LocaleSensitive implements LocaleSensitiveMBean {
 *     ...
 *     public String getLastProblemDescription() {
 *         Locale loc = {@link #getLocale() ClientContext.getLocale()};
 *         ResourceBundle rb = ResourceBundle.getBundle("MyResources", loc);
 *         String resourceKey = getLastProblemResourceKey();
 *         return rb.getString(resourceKey);
 *     }
 *     ...
 * }
 * </pre>
 *
 * <p>Here is how a client can communicate its locale to the target
 * MBean:</p>
 *
 * <pre>
 * JMXConnector connector = JMXConnectorFactory.connect(url);
 * MBeanServerConnection connection = connector.getMBeanServerConnection();
 * <b>MBeanServerConnection localizedConnection =
 *     {@link #withLocale(MBeanServerConnection, Locale)
 *      ClientContext.withLocale}(connection, Locale.getDefault());</b>
 * String problem = localizedConnection.getAttribute(
 *          objectName, "LastProblemDescription");
 * </pre>
 *
 * <p>In the more general case where the client wants to communicate context
 * other than the locale, it can use {@link #withContext(MBeanServerConnection,
 * String, String) withContext} instead of {@code withLocale}, and the target
 * MBean can retrieve the context using {@link #getContext()}.</p>
 *
 *
 * <h3 id="remote-use">Remote use of contexts</h3>
 *
 * <p>The various {@code with*} methods, for example {@link
 * #withLocale(javax.management.MBeanServer, java.util.Locale) withLocale},
 * transmit the context of each request by encoding it in the ObjectName of
 * the request.  For example, if a client creates a connection in the
 * French locale like this...</p>
 *
 * <pre>
 * MBeanServerConnection mbsc = ...;
 * Locale french = new Locale("fr");
 * MBeanServerConnection localizedConnection = ClientContext.withLocale(mbsc, french);
 * </pre>
 *
 * <p>...or, equivalently, like this...</p>
 *
 * <pre>
 * MBeanServerConnection localizedConnection =
 *     ClientContext.withContext(mbsc, {@link #LOCALE_KEY "jmx.locale"}, "fr");
 * </pre>
 *
 * <p>...then the context associates {@code "jmx.locale"} with {@code "fr"}
 * and a request such as<br>
 * {@code localizedConnection.getAttribute("java.lang:type=Runtime", "Name")}<br>
 * is translated into<br>
 * {@code mbsc.getAttribute("jmx.context//jmx.locale=fr//java.lang:Runtime", "Name")}.<br>
 * A special {@linkplain javax.management.namespace namespace} {@code jmx.context//}
 * extracts the context from the string {@code jmx.locale=fr} and establishes
 * it in the thread that will do<br>
 * {@code getAttribute("java.lang:Runtime", "Name")}.</p>
 *
 * <p>The details of how contexts are encoded into ObjectNames are explained
 * in the {@link #encode encode} method.</p>
 *
 * <p>The namespace {@code jmx.context//} just mentioned is only needed by
 * remote clients, since local clients can set the context directly using
 * {@link #doWithContext doWithContext}.  Accordingly, this namespace is not
 * present by default in the {@code MBeanServer}.  Instead, it is
 * <em>simulated</em> by the standard RMI connector using a special
 * {@link MBeanServerForwarder}.  If you are using this connector, you do not
 * need to do anything special.  Other connectors may or may not simulate this
 * namespace in the same way.  If the connector server returns true from the
 * method {@link
 * javax.management.remote.JMXConnectorServer#supportsSystemMBeanServerForwarder()
 * supportsSystemMBeanServerForwarder} then it does simulate the namespace.
 * If you are using another connector, or if you want to be able to use the
 * {@code with*} methods locally, then you can install the {@code
 * MBeanServerForwarder} yourself as described in the method {@link
 * #newContextForwarder newContextForwarder}.</p>
 */
public class ClientContext {
    /**
     * <p>The context key for the client locale.  The string associated with
     * this key is an encoded locale such as {@code en_US} which could be
     * returned by {@link Locale#toString()}.</p>
     */
    public static final String LOCALE_KEY = "jmx.locale";

    private static final Logger LOG =
            Logger.getLogger("javax.management.context");

    /**
     * <p>The namespace that implements contexts, {@value}.</p>
     */
    public static final String
            NAMESPACE = "jmx.context";
    private static final String NAMESPACE_PLUS_SEP =
            NAMESPACE + NAMESPACE_SEPARATOR;
    static final ObjectName CLIENT_CONTEXT_NAMESPACE_HANDLER =
            ObjectName.valueOf(NAMESPACE_PLUS_SEP + ":" +
                    JMXNamespace.TYPE_ASSIGNMENT);
    private static final ObjectName NAMESPACE_HANDLER_WITHOUT_NAMESPACE =
            ObjectName.valueOf(":" + JMXNamespace.TYPE_ASSIGNMENT);

    private static final ThreadLocal<Map<String, String>> contextThreadLocal =
            new InheritableThreadLocal<Map<String, String>>() {
        @Override
        protected Map<String, String> initialValue() {
            return Collections.emptyMap();
        }
    };

    /** There are no instances of this class. */
    private ClientContext() {
    }

    /**
     * <p>Get the client context associated with the current thread.
     *
     * @return the client context associated with the current thread.
     * This may be an empty Map, but it cannot be null.  The returned
     * Map cannot be modified.
     */
    public static Map<String, String> getContext() {
        return Collections.unmodifiableMap(contextThreadLocal.get());
    }

    /**
     * <p>Get the client locale associated with the current thread.
     * If the client context includes the {@value #LOCALE_KEY} key
     * then the returned value is the Locale encoded in that key.
     * Otherwise the returned value is the {@linkplain Locale#getDefault()
     * default locale}.
     *
     * @return the client locale.
     */
    public static Locale getLocale() {
        String localeS = getContext().get(LOCALE_KEY);
        if (localeS == null)
            return Locale.getDefault();
        // Parse the locale string.  Why isn't there a method in Locale for this?
        String language, country, variant;
        int ui = localeS.indexOf('_');
        if (ui < 0) {
            language = localeS;
            country = variant = "";
        } else {
            language = localeS.substring(0, ui);
            localeS = localeS.substring(ui + 1);
            ui = localeS.indexOf('_');
            if (ui < 0) {
                country = localeS;
                variant = "";
            } else {
                country = localeS.substring(0, ui);
                variant = localeS.substring(ui + 1);
            }
        }
        return new Locale(language, country, variant);
    }

    /**
     * <p>Execute the given {@code task} with the client context set to
     * the given Map.  This Map will be the result of {@link #getContext()}
     * within the {@code task}.</p>
     *
     * <p>The {@code task} may include nested calls to {@code doWithContext}.
     * The value returned by {@link #getContext} at any point is the Map
     * provided to the most recent {@code doWithContext} (in the current thread)
     * that has not yet returned.</p>
     *
     * <p>The {@link #getContext()} method returns the same value immediately
     * after a call to this method as immediately before.  In other words,
     * {@code doWithContext} only affects the context during the execution of
     * the {@code task}.</p>
     *
     * <p>As an example, suppose you want to get an attribute with whatever
     * context has already been set, plus the locale set to "fr".  You could
     * write this:</p>
     *
     * <pre>
     * {@code Map<String, String>} context =
     *     new {@code HashMap<String, String>}(ClientContext.getContext());
     * context.put(ClientContext.LOCALE_KEY, "fr");
     * String lastProblemDescription =
     *     ClientContext.doWithContext(context, new {@code Callable<String>}() {
     *         public String call() {
     *             return (String) mbeanServer.getAttribute(mbean, "LastProblemDescription");
     *         }
     *     });
     * </pre>
     *
     * @param <T> the type of value that the task will return.  This type
     * parameter is usually inferred from the type of the {@code task}
     * parameter.  For example, if {@code task} is a {@code Callable<String>}
     * then {@code T} is {@code String}.  If the task does not return a value,
     * use a {@code Callable<Void>} and return null from its
     * {@link Callable#call call} method.
     * @param context the context to use while executing {@code task}.
     * @param task the task to run with the {@code key}={@code value}
     * binding.
     * @return the result of {@link Callable#call() task.call()}.
     * @throws IllegalArgumentException if either parameter is null, or
     * if any key in {@code context} is null or empty, or if any value
     * in {@code context} is null.
     * @throws Exception If {@link Callable#call() task.call()} throws an
     * exception, {@code doWithContext} throws the same exception.
     */
    public static <T> T doWithContext(Map<String, String> context, Callable<T> task)
    throws Exception {
        if (context == null || task == null)
            throw new IllegalArgumentException("Null parameter");
        Map<String, String> contextCopy = new TreeMap<String, String>(context);
        validateContext(contextCopy);
        Map<String, String> oldContextMap = contextThreadLocal.get();
        try {
            contextThreadLocal.set(contextCopy);
            return task.call();
        } finally {
            contextThreadLocal.set(oldContextMap);
        }
    }

    private static void validateContext(Map<String, String> context) {
        for (Map.Entry<String, String> entry : context.entrySet()) {
            // If the user passes a raw Map rather than a Map<String, String>,
            // entries could contain objects other than Strings.  If so,
            // we'll get a ClassCastException here.
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null)
                throw new IllegalArgumentException("Null key or value in context");
            if (key.equals(""))
                throw new IllegalArgumentException("Empty key in context");
        }
    }

    /**
     * <p>Return an MBeanServer object that is equivalent to the given
     * MBeanServer object except that operations on MBeans run with
     * the given Locale in their {@linkplain #getContext() thread context}.
     * Note that this will only work if the given MBeanServer supports
     * contexts, as described <a href="#remote-use">above</a>.</p>
     *
     * <p>This method is equivalent to {@link #withContext(MBeanServer,
     * String, String) withContext}<code>(mbs, {@value LOCALE_KEY},
     * locale.toString())</code>.</p>
     *
     * @throws IllegalArgumentException if either parameter is null, or if
     * {@code mbs} does not support contexts.  In the second case only,
     * the cause of the {@code IllegalArgumentException} will be an {@link
     * InstanceNotFoundException}.
     */
    public static MBeanServer withLocale(MBeanServer mbs, Locale locale) {
        return withLocale(mbs, MBeanServer.class, locale);
    }

    /**
     * <p>Return an MBeanServerConnection object that is equivalent to the given
     * MBeanServerConnection object except that operations on MBeans run with
     * the given Locale in their {@linkplain #getContext() thread context}.
     * Note that this will only work if the given MBeanServerConnection supports
     * contexts, as described <a href="#remote-use">above</a>.</p>
     *
     * <p>This method is equivalent to {@link #withContext(MBeanServerConnection,
     * String, String) withContext}<code>(mbs, {@value LOCALE_KEY},
     * locale.toString())</code>.</p>
     *
     * @throws IllegalArgumentException if either parameter is null, or if
     * the communication with {@code mbsc} fails, or if {@code mbsc} does not
     * support contexts.  If the communication with {@code mbsc} fails, the
     * {@linkplain Throwable#getCause() cause} of this exception will be an
     * {@code IOException}.  If {@code mbsc} does not support contexts, the
     * cause will be an {@link InstanceNotFoundException}.
     */
    public static MBeanServerConnection withLocale(
            MBeanServerConnection mbsc, Locale locale) {
        return withLocale(mbsc, MBeanServerConnection.class, locale);
    }

    private static <T extends MBeanServerConnection> T withLocale(
            T mbsc, Class<T> mbscClass, Locale locale) {
        if (locale == null)
            throw new IllegalArgumentException("Null locale");
        return withContext(mbsc, mbscClass, LOCALE_KEY, locale.toString());
    }

    /**
     * <p>Return an MBeanServer object that is equivalent to the given
     * MBeanServer object except that operations on MBeans run with
     * the given key bound to the given value in their {@linkplain
     * #getContext() thread context}.
     * Note that this will only work if the given MBeanServer supports
     * contexts, as described <a href="#remote-use">above</a>.</p>
     *
     * @param mbs the original MBeanServer.
     * @param key the key to bind in the context of MBean operations
     * in the returned MBeanServer object.
     * @param value the value to bind to the key in the context of MBean
     * operations in the returned MBeanServer object.
     * @throws IllegalArgumentException if any parameter is null, or
     * if {@code key} is the empty string, or if {@code mbs} does not support
     * contexts.  In the last case only, the cause of the {@code
     * IllegalArgumentException} will be an {@link InstanceNotFoundException}.
     */
    public static MBeanServer withContext(
            MBeanServer mbs, String key, String value) {
        return withContext(mbs, MBeanServer.class, key, value);
    }

    /**
     * <p>Return an MBeanServerConnection object that is equivalent to the given
     * MBeanServerConnection object except that operations on MBeans run with
     * the given key bound to the given value in their {@linkplain
     * #getContext() thread context}.
     * Note that this will only work if the given MBeanServerConnection supports
     * contexts, as described <a href="#remote-use">above</a>.</p>
     *
     * @param mbsc the original MBeanServerConnection.
     * @param key the key to bind in the context of MBean operations
     * in the returned MBeanServerConnection object.
     * @param value the value to bind to the key in the context of MBean
     * operations in the returned MBeanServerConnection object.
     * @throws IllegalArgumentException if any parameter is null, or
     * if {@code key} is the empty string, or if the communication with {@code
     * mbsc} fails, or if {@code mbsc} does not support contexts.  If
     * the communication with {@code mbsc} fails, the {@linkplain
     * Throwable#getCause() cause} of this exception will be an {@code
     * IOException}.  If {@code mbsc} does not support contexts, the cause will
     * be an {@link InstanceNotFoundException}.
     */
    public static MBeanServerConnection withContext(
            MBeanServerConnection mbsc, String key, String value) {
        return withContext(mbsc, MBeanServerConnection.class, key, value);
    }


    /**
     * <p>Returns an MBeanServerConnection object that is equivalent to the
     * given MBeanServerConnection object except that remote operations on
     * MBeans run with the context that has been established by the client
     * using {@link #doWithContext doWithContext}.  Note that this will
     * only work if the remote system supports contexts, as described <a
     * href="#remote-use">above</a>.</p>
     *
     * <p>For example, suppose the remote system does support contexts, and you
     * have created a {@code JMXConnector} like this:</p>
     *
     * <pre>
     * JMXServiceURL url = ...;
     * JMXConnector client = JMXConnectorFactory.connect(url);
     * MBeanServerConnection mbsc = client.getMBeanServerConnection();
     * <b>mbsc = ClientContext.withDynamicContext(mbsc);</b>
     * </pre>
     *
     * <p>Then if you do this...</p>
     *
     * <pre>
     * MBeanInfo mbi = ClientContext.doWithContext(
     *     Collections.singletonMap(ClientContext.LOCALE_KEY, "fr"),
     *     new {@code Callable<MBeanInfo>}() {
     *         public MBeanInfo call() {
     *             return mbsc.getMBeanInfo(objectName);
     *         }
     *     });
     * </pre>
     *
     * <p>...then the context with the locale set to "fr" will be in place
     * when the {@code getMBeanInfo} is executed on the remote MBean Server.</p>
     *
     * @param mbsc the original MBeanServerConnection.
     *
     * @throws IllegalArgumentException if the {@code mbsc} parameter is null,
     * or if the communication with {@code mbsc} fails, or if {@code mbsc}
     * does not support contexts.  If the communication with {@code mbsc}
     * fails, the {@linkplain Throwable#getCause() cause} of this exception
     * will be an {@code IOException}.  If {@code mbsc} does not support
     * contexts, the cause will be an {@link InstanceNotFoundException}.
     */
    public static MBeanServerConnection withDynamicContext(
            MBeanServerConnection mbsc) {
        // Probe mbsc to get the right exception if it's incommunicado or
        // doesn't support namespaces.
        JMXNamespaces.narrowToNamespace(mbsc, NAMESPACE);
        return (MBeanServerConnection) Proxy.newProxyInstance(
                MBeanServerConnection.class.getClassLoader(),
                new Class<?>[] {MBeanServerConnection.class},
                new DynamicContextIH(mbsc));
    }

    private static class DynamicContextIH implements InvocationHandler {
        private final MBeanServerConnection mbsc;

        public DynamicContextIH(MBeanServerConnection mbsc) {
            this.mbsc = mbsc;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            MBeanServerConnection dynMBSC = withContext(
                    mbsc, MBeanServerConnection.class, getContext(), false);
            try {
                return method.invoke(dynMBSC, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static <T extends MBeanServerConnection> T withContext(
            T mbsc, Class<T> mbscClass, String key, String value) {
        return withContext(
                mbsc, mbscClass, Collections.singletonMap(key, value), true);
    }

    private static <T extends MBeanServerConnection> T withContext(
            T mbsc, Class<T> mbscClass, Map<String, String> context,
            boolean probe) {
        if (mbsc == null || context == null)
            throw new IllegalArgumentException("Null parameter");
        if (context.isEmpty())
            return mbsc;
        validateContext(context);
        Map<String, String> contextMap = null;
        if (mbsc.getClass() == RoutingServerProxy.class ||
                mbsc.getClass() == RoutingProxy.class) {
            RoutingProxy<?> nsp = (RoutingProxy<?>) mbsc;
            String where = nsp.getSourceNamespace();
            if (where.startsWith(NAMESPACE_PLUS_SEP)) {
                /* Try to merge the existing context namespace with the
                 * new one.  If it doesn't work, we fall back to just
                 * prefixing jmx.context//key=value, which
                 * might lead to a name like jmx.c//k1=v1//jmx.c//k2=v2//d:k=v.
                 */
                String encodedContext =
                        where.substring(NAMESPACE_PLUS_SEP.length());
                if (encodedContext.indexOf(NAMESPACE_SEPARATOR) < 0) {
                    contextMap = stringToMapOrNull(encodedContext);
                    if (contextMap != null) {
                        contextMap.putAll(context);
                        mbsc = mbscClass.cast(nsp.source());
                    }
                }
            }
        }
        if (contextMap == null)
            contextMap = context;
        String contextDir = NAMESPACE_PLUS_SEP + mapToString(contextMap);
        if (mbscClass == MBeanServer.class) {
            return mbscClass.cast(RoutingServerProxy.cd(
                    (MBeanServer) mbsc, contextDir, probe));
        } else if (mbscClass == MBeanServerConnection.class) {
            return mbscClass.cast(RoutingConnectionProxy.cd(
                    mbsc, contextDir, probe));
        } else
            throw new AssertionError("Bad MBSC: " + mbscClass);
    }

    /**
     * <p>Returns an encoded context prefix for ObjectNames.
     * If the given context is empty, {@code ""} is returned.
     * Otherwise, this method returns a string of the form
     * {@code "jmx.context//key=value;key=value;..."}.
     * For example, if the context has keys {@code "jmx.locale"}
     * and {@code "xid"} with respective values {@code "fr"}
     * and {@code "1234"}, this method will return
     * {@code "jmx.context//jmx.locale=fr;xid=1234"} or
     * {@code "jmx.context//xid=1234;jmx.locale=fr"}.</p>
     *
     * <p>Each key and each value in the encoded string is subject to
     * encoding as if by the method {@link URLEncoder#encode(String, String)}
     * with a character encoding of {@code "UTF-8"}, but with the additional
     * encoding of any {@code *} character as {@code "%2A"}.  This ensures
     * that keys and values can contain any character.  Without encoding,
     * characters such as {@code =} and {@code :} would pose problems.</p>
     *
     * @param context the context to encode.
     *
     * @return the context in encoded form.
     *
     * @throws IllegalArgumentException if the {@code context} parameter
     * is null or if it contains a null key or value.
     **/
    public static String encode(Map<String, String> context) {
        if (context == null)
            throw new IllegalArgumentException("Null context");
        if (context.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null)
                throw new IllegalArgumentException("Null key or value");
            if (sb.length() > 0)
                sb.append(";");
            sb.append(encode(key)).append("=").append(encode(value));
        }
        sb.insert(0, NAMESPACE_PLUS_SEP);
        return sb.toString();
    }

    /**
     * <p>Create a new {@link MBeanServerForwarder} that applies the context
     * received from a client to the current thread.  A client using
     * one of the various {@code with*} methods (for example {@link
     * #withContext(MBeanServerConnection, String, String) withContext}) will
     * encode that context into the {@code ObjectName} of each
     * {@code MBeanServer} request.  The object returned by this method
     * decodes the context from that {@code ObjectName} and applies it
     * as described for {@link #doWithContext doWithContext} while performing
     * the {@code MBeanServer} request using the {@code ObjectName} without
     * the encoded context.</p>
     *
     * <p>This forwarder can be used in a number of ways:</p>
     *
     * <ul>
     * <li>
     * <p>To add context decoding to a local {@code MBeanServer}, you can
     * write:</p>
     * <pre>
     * MBeanServer mbs = {@link
     * java.lang.management.ManagementFactory#getPlatformMBeanServer()
     * ManagementFactory.getPlatformMBeanServer()};  // for example
     * mbs = ClientContext.newContextForwarder(mbs, null);
     * </pre>
     *
     * <li>
     * <p>To add context decoding to a {@linkplain
     * javax.management.remote.JMXConnectorServer connector server}:</p>
     * <pre>
     * JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(...);
     * MBeanServer nextMBS = cs.getMBeanServer();
     * MBeanServerForwarder mbsf = ClientContext.newContextForwarder(nextMBS, null);
     * cs.{@link
     * javax.management.remote.JMXConnectorServer#setMBeanServerForwarder
     * setMBeanServerForwarder}(mbsf);
     * </pre>
     *
     * <li>
     * <p>For connectors, such as the standard RMI connector, that support
     * a {@linkplain
     * javax.management.remote.JMXConnectorServer#getSystemMBeanServerForwarder
     * system chain} of {@code MBeanServerForwarder}s, this forwarder will
     * be installed in that chain by default.  See
     * {@link javax.management.remote.JMXConnectorServer#CONTEXT_FORWARDER
     * JMXConnectorServer.CONTEXT_FORWARDER}.
     * </p>
     *
     * </ul>
     *
     * @param nextMBS the next {@code MBeanServer} in the chain of
     * forwarders, which might be another {@code MBeanServerForwarder} or
     * a plain {@code MBeanServer}.  This is the object to which {@code
     * MBeanServer} requests that do not include a context are sent.  It
     * will be the value of {@link MBeanServerForwarder#getMBeanServer()
     * getMBeanServer()} on the returned object, and can be changed with {@link
     * MBeanServerForwarder#setMBeanServer setMBeanServer}.  It can be null but
     * must be set to a non-null value before any {@code MBeanServer} requests
     * arrive.
     *
     * @param loopMBS the {@code MBeanServer} to which requests that contain
     * an encoded context should be sent once the context has been decoded.
     * For example, if the request is {@link MBeanServer#getAttribute
     * getAttribute}{@code ("jmx.context//jmx.locale=fr//java.lang:type=Runtime",
     * "Name")}, then the {@linkplain #getContext() context} of the thread
     * executing that request will have {@code "jmx.locale"} set to {@code "fr"}
     * while executing {@code loopMBS.getAttribute("java.lang:type=Runtime",
     * "Name")}.  If this parameter is null, then these requests will be
     * sent to the newly-created {@code MBeanServerForwarder}.  Usually
     * the parameter will either be null or will be the result of {@link
     * javax.management.remote.JMXConnectorServer#getSystemMBeanServerForwarder
     * getSystemMBeanServerForwarder()} for the connector server in which
     * this forwarder will be installed.
     *
     * @return a new {@code MBeanServerForwarder} that decodes client context
     * from {@code ObjectName}s.
     */
    /*
     * What we're building here is confusing enough to need a diagram.
     * The MBSF that we return is actually the composition of two forwarders:
     * the first one simulates the existence of the MBean
     * jmx.context//:type=JMXNamespace, and the second one simulates the
     * existence of the namespace jmx.context//.  Furthermore, that namespace
     * loops back to the composed forwarder, so that something like
     * jmx.context//foo=bar//jmxcontext//baz=buh will work.  And the loopback
     * goes through yet another forwarder, which simulates the existence of
     * (e.g.) jmx.context//foo=bar//:type=JMXNamespace, which is needed
     * notably so that narrowToNamespace will work.
     *
     *          |     +--------------------------------------------------+
     *          v     v                                                  |
     * +----------------+                                                |
     * | Handler MBSF   |->accesses to jmx.context//:type=JMXNamespace   |
     * +----------------+    (handled completely here)   +-------------------+
     *          |                                        | 2nd Handler MBSF  |
     *          v                                        +-------------------+
     * +----------------+                                                ^
     * | Namespace MBSF |->accesses to jmx.context//**-------------------+
     * +----------------+    (after attaching context to thread)
     *          |
     *          v          accesses to anything else
     *
     * And finally, we need to ensure that from the outside the composed object
     * looks like a single forwarder, so that its get/setMBeanServer methods
     * will do the expected thing.  That's what the anonymous subclass is for.
     */
    public static MBeanServerForwarder newContextForwarder(
            MBeanServer nextMBS, MBeanServer loopMBS) {
        final MBeanServerForwarder mbsWrapper =
                new IdentityMBeanServerForwarder(nextMBS);
        DynamicMBean handlerMBean = new StandardMBean(
                new JMXNamespace(mbsWrapper), JMXNamespaceMBean.class, false);
        SingleMBeanForwarder handlerForwarder = new SingleMBeanForwarder(
                CLIENT_CONTEXT_NAMESPACE_HANDLER, handlerMBean, true) {
            @Override
            public MBeanServer getMBeanServer() {
                return ((MBeanServerForwarder) super.getMBeanServer()).getMBeanServer();
            }

            @Override
            public void setMBeanServer(MBeanServer mbs1) {
                MBeanServerForwarder mbsf1 = (MBeanServerForwarder)
                        super.getMBeanServer();
                if (mbsf1 != null)
                    mbsf1.setMBeanServer(mbs1);
                else
                    super.setMBeanServer(mbs1);
                mbsWrapper.setMBeanServer(mbs1);
            }
        };
        if (loopMBS == null)
            loopMBS = handlerForwarder;
        ContextInvocationHandler contextIH =
                new ContextInvocationHandler(nextMBS, loopMBS);
        MBeanServerForwarder contextForwarder = newForwarderProxy(contextIH);
        handlerForwarder.setMBeanServer(contextForwarder);
        return handlerForwarder;
    }

    /**
     * <p>Create a new {@link MBeanServerForwarder} that localizes
     * descriptions in {@code MBeanInfo} instances returned by
     * {@link MBeanServer#getMBeanInfo getMBeanInfo}.  The {@code
     * MBeanServerForwarder} returned by this method passes all {@code
     * MBeanServer} methods through unchanged to the supplied object, {@code
     * mbs}, with the exception of {@code getMBeanInfo}.  To handle {@code
     * getMBeanInfo(objectName)}, it calls {@code mbs.getMBeanInfo(objectName)}
     * to get an {@code MBeanInfo}, {@code mbi}; it calls {@link
     * MBeanServer#getClassLoaderFor mbs.getClassLoaderFor(objectName)} to
     * get a {@code ClassLoader}, {@code cl}; and it calls {@link
     * #getLocale} to get a {@code Locale}, {@code locale}.  The order
     * of these three calls is not specified.  Then the result is {@code
     * mbi.localizeDescriptions(locale, loader)}.</p>
     *
     * <p>This forwarder can be used in a number of ways:</p>
     *
     * <ul>
     * <li>
     * <p>To add description localization to a local {@code MBeanServer}, you
     * can write:</p>
     *
     * <pre>
     * MBeanServer mbs = {@link
     * java.lang.management.ManagementFactory#getPlatformMBeanServer()
     * ManagementFactory.getPlatformMBeanServer()};  // for example
     * mbs = ClientContext.newLocalizeMBeanInfoForwarder(mbs);
     * </pre>
     *
     * <li>
     * <p>To add description localization to a {@linkplain
     * javax.management.remote.JMXConnectorServer connector server}, you will
     * need to add both a {@linkplain #newContextForwarder context forwarder}
     * and a localization forwarder, for example like this:</p>
     *
     * <pre>
     * JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(...);
     * MBeanServer nextMBS = cs.getMBeanServer();
     * MBeanServerForwarder localizeMBSF =
     *     ClientContext.newLocalizeMBeanInfoForwarder(nextMBS);
     * MBeanServerForwarder contextMBSF =
     *     ClientContext.newContextForwarder(localizeMBSF, null);
     * cs.{@link
     * javax.management.remote.JMXConnectorServer#setMBeanServerForwarder
     * setMBeanServerForwarder}(contextMBSF);
     * </pre>
     *
     * <p>Notice that the context forwarder must run before the localization
     * forwarder, so that the locale is correctly established when the latter
     * runs.  So the {@code nextMBS} parameter of the context forwarder must
     * be the localization forwarder, and not vice versa.</p>
     *
     * <li>
     * <p>For connectors, such as the standard RMI connector, that support
     * a {@linkplain
     * javax.management.remote.JMXConnectorServer#getSystemMBeanServerForwarder
     * system chain} of {@code MBeanServerForwarder}s, the context forwarder and
     * the localization forwarder will be installed in that chain, in the right
     * order, if you include
     * {@link
     * javax.management.remote.JMXConnectorServer#LOCALIZE_MBEAN_INFO_FORWARDER
     * LOCALIZE_MBEAN_INFO_FORWARDER} in the environment {@code Map} with
     * the value {@code "true"}, for example like this:</p>
     * </p>
     * <pre>
     * MBeanServer mbs = ...;
     * JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://...");
     * {@code Map<String, Object>} env = new {@code HashMap<String, Object>}();
     * env.put(JMXConnectorServer.LOCALIZE_MBEAN_INFO_FORWARDER, "true");
     * JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
     *     url, env, mbs);
     * </pre>
     *
     * </ul>
     *
     * @param mbs the next {@code MBeanServer} in the chain of
     * forwarders, which might be another {@code MBeanServerForwarder}
     * or a plain {@code MBeanServer}.  It will be the value of
     * {@link MBeanServerForwarder#getMBeanServer() getMBeanServer()}
     * on the returned object, and can be changed with {@link
     * MBeanServerForwarder#setMBeanServer setMBeanServer}.  It can be null but
     * must be set to a non-null value before any {@code MBeanServer} requests
     * arrive.
     *
     * @return a new {@code MBeanServerForwarder} that localizes descriptions
     * in the result of {@code getMBeanInfo}.
     */
    public static MBeanServerForwarder newLocalizeMBeanInfoForwarder(
            MBeanServer mbs) {
        return new IdentityMBeanServerForwarder(mbs) {
            @Override
            public MBeanInfo getMBeanInfo(ObjectName name)
                    throws InstanceNotFoundException, IntrospectionException,
                           ReflectionException {
                MBeanInfo mbi = super.getMBeanInfo(name);
                Locale locale = getLocale();
                ClassLoader loader = getClassLoaderFor(name);
                return mbi.localizeDescriptions(locale, loader);
            }
        };
    }

    private static MBeanServerForwarder newForwarderProxy(InvocationHandler ih) {
        return (MBeanServerForwarder) Proxy.newProxyInstance(
                MBeanServerForwarder.class.getClassLoader(),
                new Class<?>[] {MBeanServerForwarder.class},
                ih);
    }

    // A proxy connection that will strip the 'contextDir' at input (routing),
    // and put it back at output (createMBean / registerMBean / query* /
    // getObjectInstance). Usually RoutingProxy / RoutingServerProxy are used
    // the other way round (they are used for 'cd' - where they need to add
    // something at input and remove it at output).
    // For 'cd' operations we create RoutingProxys with a non empty sourceDir,
    // and a possibly non-empty targetDir. This is the only case where we use
    // RoutingProxies with an empty sourceDir (sourceDir is what we add at input
    // and remove at output, targetDir is what we remove at input and add at
    // output.
    //
    // Note that using a transient ContextRoutingConnection
    // is possible only because RoutingProxys don't rewrite
    // notifications sources - otherwise we would have to
    // keep the ContextRoutingConnection - just to preserve
    // the 'wrapping listeners'
    //
    private static final class ContextRoutingConnection
            extends RoutingServerProxy {
        public ContextRoutingConnection(MBeanServer source,
                                 String contextDir) {
            super(source, "", contextDir, false);
        }

        // Not really needed - but this is safer and more optimized.
        // See RoutingProxy for more details.
        //
        @Override
        public Integer getMBeanCount() {
            return source().getMBeanCount();
        }

        // Not really needed - but this is safer and more optimized.
        // See RoutingProxy for more details.
        //
        @Override
        public String[] getDomains() {
            return source().getDomains();
        }

        // Not really needed - but this is safer and more optimized.
        // See RoutingProxy for more details.
        //
        @Override
        public String getDefaultDomain() {
            return source().getDefaultDomain();
        }

    }

    private static class ContextInvocationHandler implements InvocationHandler {
        /*
         * MBeanServer requests that don't include jmx.context//foo=bar//
         * are forwarded to forwardMBS, which is the unadorned MBeanServer
         * that knows nothing about the context namespace.
         * MBeanServer requests that do include this prefix will
         * usually (depending on the value of the loopMBS parameter to
         * newContextForwarder) loop back to the combined MBeanServerForwarder
         * that first implements
         * jmx.context//:type=JMXNamespace and then implements
         * jmx.context//foo=bar//.  The reason is that it is valid
         * to have jmx.context//foo=bar//jmx.context//baz=buh//, although
         * usually that will be combined into jmx.context//foo=bar;baz=buh//.
         *
         * Before forwarding to loopMBS, we must check for :type=JMXNamespace
         * so that jmx.context//foo=bar//:type=JMXNamespace will exist.  Its
         * existence is partial because it must remain "invisible": it should
         * not show up in queryNames or getMBeanCount even though it does
         * accept getAttribute and isRegistered and all other methods that
         * reference a single MBean.
         */
        private MBeanServer forwardMBS;
        private final MBeanServer loopMBS;
        private static final MBeanServer emptyMBS = new MBeanServerSupport() {
            @Override
            public DynamicMBean getDynamicMBeanFor(ObjectName name)
                    throws InstanceNotFoundException {
                throw new InstanceNotFoundException(name.toString());
            }

            @Override
            protected Set<ObjectName> getNames() {
                return Collections.emptySet();
            }
        };

        ContextInvocationHandler(MBeanServer forwardMBS, MBeanServer loopMBS) {
            this.forwardMBS = forwardMBS;
            DynamicMBean handlerMBean = new StandardMBean(
                    new JMXNamespace(loopMBS), JMXNamespaceMBean.class, false);
            MBeanServerForwarder handlerMBS = new SingleMBeanForwarder(
                    NAMESPACE_HANDLER_WITHOUT_NAMESPACE, handlerMBean, false);
            handlerMBS.setMBeanServer(loopMBS);
            this.loopMBS = handlerMBS;
        }

        public Object invoke(Object proxy, final Method method, final Object[] args)
        throws Throwable {
            String methodName = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();

            // If this is a method from MBeanServerForwarder, handle it here.
            // There are only two such methods: getMBeanServer() and
            // setMBeanServer(mbs).
            if (methodName.equals("getMBeanServer"))
                return forwardMBS;
            else if (methodName.equals("setMBeanServer")) {
                this.forwardMBS = (MBeanServer) args[0];
                return null;
            }

            // It is a method from MBeanServer.
            // Find the first parameter whose declared type is ObjectName,
            // and see if it is in the context namespace.  If so we need to
            // trigger the logic for that namespace.  If not, we simply
            // forward to the next MBeanServer in the chain.  This logic
            // depends on the fact that if a method in the MBeanServer interface
            // has a "routing" ObjectName parameter, it is always the first
            // parameter of that type.  Conversely, if a method has an
            // ObjectName parameter, then it makes sense to "route" that
            // method.  Except for deserialize and instantiate, but if we
            // recognize a context namespace in those methods' ObjectName
            // parameters it is pretty harmless.
            int objectNameI = -1;
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i] == ObjectName.class) {
                    objectNameI = i;
                    break;
                }
            }

            if (objectNameI < 0)
                return invoke(method, forwardMBS, args);

            ObjectName target = (ObjectName) args[objectNameI];
            if (target == null ||
                    !target.getDomain().startsWith(NAMESPACE_PLUS_SEP))
                return invoke(method, forwardMBS, args);

            String domain = target.getDomain().substring(NAMESPACE_PLUS_SEP.length());

            // The method routes through the (simulated) context namespace.
            // Decode the context after it, e.g. jmx.context//jmx.locale=fr//...
            // If there is no context part, we can throw an exception,
            // because a forwarder has already handled the unique MBean
            // jmx.context//:type=JMXNamespace.
            int sep = domain.indexOf(NAMESPACE_SEPARATOR);
            if (sep < 0)
                return invoke(method, emptyMBS, args);  // throw exception
            final String encodedContext = domain.substring(0, sep);

            if (method.getName().startsWith("query") &&
                    (encodedContext.contains("*") || encodedContext.contains("?"))) {
                // Queries like jmx.context//*//d:k=v return
                // an empty set, consistent with "real" namespaces.
                return Collections.EMPTY_SET;
            }

            Map<String, String> ctx = new TreeMap<String, String>(getContext());
            ctx.putAll(stringToMap(encodedContext));

            return doWithContext(ctx, new Callable<Object>() {
                public Object call() throws Exception {
                    // Create a proxy connection that will strip
                    // "jmx.context//" + encodedContext + "//" on input,
                    // and put it back on output.
                    //
                    // Note that using a transient ContextRoutingConnection
                    // is possible only because it doesn't rewrite
                    // notification sources - otherwise we would have to
                    // keep the ContextRoutingConnection - just to preserve
                    // the 'wrapping listeners'
                    //
                    String namespace = NAMESPACE_PLUS_SEP + encodedContext;
                    final ContextRoutingConnection route =
                              new ContextRoutingConnection(loopMBS, namespace);

                    if (LOG.isLoggable(Level.FINE))
                        LOG.fine("context="+encodedContext);
                    if (LOG.isLoggable(Level.FINER))
                        LOG.finer(method.getName()+""+
                            ((args==null)?"()":(""+Arrays.asList(args))));

                    return invoke(method, route, args);
                }
            });
        }

        private static Object invoke(Method method, Object target, Object[] args)
                throws Exception {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error)
                    throw (Error) cause;
                throw (Exception) cause;
            }
        }
    }

    private static String mapToString(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = encode(entry.getKey());
            String value = encode(entry.getValue());
            if (sb.length() > 0)
                sb.append(";");
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    private static Map<String, String> stringToMap(String encodedContext) {
        Map<String, String> map = stringToMapOrNull(encodedContext);
        if (map == null) {
            throw new IllegalArgumentException(
                    "Invalid encoded context: " + encodedContext);
        }
        return map;
    }

    private static Map<String, String> stringToMapOrNull(String encodedContext) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        StringTokenizer stok = new StringTokenizer(encodedContext, ";");
        while (stok.hasMoreTokens()) {
            String tok = stok.nextToken();
            int eq = tok.indexOf('=');
            if (eq < 0)
                return null;
            String key = decode(tok.substring(0, eq));
            if (key.equals(""))
                return null;
            String value = decode(tok.substring(eq + 1));
            map.put(key, value);
        }
        return map;
    }

    private static String encode(String s) {
        try {
            s = URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);  // Should not happen
        }
        return s.replace("*", "%2A");
        // The * character is left intact in URL encodings, but for us it
        // is special (an ObjectName wildcard) so we must map it.
        // We are assuming that URLDecoder will decode it the same way as any
        // other hex escape.
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
