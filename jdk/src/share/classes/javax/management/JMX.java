/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.jmx.mbeanserver.Introspector;
import com.sun.jmx.mbeanserver.MBeanInjector;
import com.sun.jmx.remote.util.ClassLogger;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.TreeMap;
import javax.management.namespace.JMXNamespaces;
import javax.management.openmbean.MXBeanMappingFactory;

/**
 * Static methods from the JMX API.  There are no instances of this class.
 *
 * @since 1.6
 */
public class JMX {
    /* Code within this package can prove that by providing this instance of
     * this class.
     */
    static final JMX proof = new JMX();
    private static final ClassLogger logger =
        new ClassLogger("javax.management.misc", "JMX");

    private JMX() {}

    /**
     * The name of the <a href="Descriptor.html#defaultValue">{@code
     * defaultValue}</a> field.
     */
    public static final String DEFAULT_VALUE_FIELD = "defaultValue";

   /**
     * The name of the <a
     * href="Descriptor.html#descriptionResourceBundleBaseName">{@code
     * descriptionResourceBundleBaseName}</a> field.
     */
    public static final String DESCRIPTION_RESOURCE_BUNDLE_BASE_NAME_FIELD =
            "descriptionResourceBundleBaseName";

    /**
     * The name of the <a href="Descriptor.html#descriptionResourceKey">{@code
     * descriptionResourceKey}</a> field.
     */
    public static final String DESCRIPTION_RESOURCE_KEY_FIELD =
            "descriptionResourceKey";

    /**
     * The name of the <a href="Descriptor.html#immutableInfo">{@code
     * immutableInfo}</a> field.
     */
    public static final String IMMUTABLE_INFO_FIELD = "immutableInfo";

    /**
     * The name of the <a href="Descriptor.html#interfaceClassName">{@code
     * interfaceClassName}</a> field.
     */
    public static final String INTERFACE_CLASS_NAME_FIELD = "interfaceClassName";

    /**
     * The name of the <a href="Descriptor.html#legalValues">{@code
     * legalValues}</a> field.
     */
    public static final String LEGAL_VALUES_FIELD = "legalValues";

    /**
     * The name of the <a href="Descriptor.html#locale">{@code locale}</a>
     * field.
     */
    public static final String LOCALE_FIELD = "locale";

    /**
     * The name of the <a href="Descriptor.html#maxValue">{@code
     * maxValue}</a> field.
     */
    public static final String MAX_VALUE_FIELD = "maxValue";

    /**
     * The name of the <a href="Descriptor.html#minValue">{@code
     * minValue}</a> field.
     */
    public static final String MIN_VALUE_FIELD = "minValue";

    /**
     * The name of the <a href="Descriptor.html#mxbean">{@code
     * mxbean}</a> field.
     */
    public static final String MXBEAN_FIELD = "mxbean";

    /**
     * The name of the
     * <a href="Descriptor.html#mxbeanMappingFactoryClass">{@code
     * mxbeanMappingFactoryClass}</a> field.
     */
    public static final String MXBEAN_MAPPING_FACTORY_CLASS_FIELD =
            "mxbeanMappingFactoryClass";

    /**
     * The name of the <a href="Descriptor.html#openType">{@code
     * openType}</a> field.
     */
    public static final String OPEN_TYPE_FIELD = "openType";

    /**
     * The name of the <a href="Descriptor.html#originalType">{@code
     * originalType}</a> field.
     */
    public static final String ORIGINAL_TYPE_FIELD = "originalType";

    /**
     * <p>Options to apply to an MBean proxy or to an instance of {@link
     * StandardMBean}.</p>
     *
     * <p>For example, to specify the "wrapped object visible" option for a
     * {@code StandardMBean}, you might write this:</p>
     *
     * <pre>
     * StandardMBean.Options opts = new StandardMBean.Options();
     * opts.setWrappedObjectVisible(true);
     * StandardMBean mbean = new StandardMBean(impl, intf, opts);
     * </pre>
     *
     * @see javax.management.JMX.ProxyOptions
     * @see javax.management.StandardMBean.Options
     */
    public static class MBeanOptions implements Serializable, Cloneable {
        private static final long serialVersionUID = -6380842449318177843L;

        static final MBeanOptions MXBEAN = new MBeanOptions();
        static {
            MXBEAN.setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
        }

        private MXBeanMappingFactory mappingFactory;

        /**
         * <p>Construct an {@code MBeanOptions} object where all options have
         * their default values.</p>
         */
        public MBeanOptions() {}

        @Override
        public MBeanOptions clone() {
            try {
                return (MBeanOptions) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * <p>True if this is an MXBean proxy or a StandardMBean instance
         * that is an MXBean.  The default value is false.</p>
         *
         * <p>This method is equivalent to {@link #getMXBeanMappingFactory()
         * this.getMXBeanMappingFactory()}{@code != null}.</p>
         *
         * @return true if this is an MXBean proxy or a StandardMBean instance
         * that is an MXBean.
         */
        public boolean isMXBean() {
            return (this.mappingFactory != null);
        }

        /**
         * <p>The mappings between Java types and Open Types to be used in
         * an MXBean proxy or a StandardMBean instance that is an MXBean,
         * or null if this instance is not for an MXBean.
         * The default value is null.</p>
         *
         * @return the mappings to be used in this proxy or StandardMBean,
         * or null if this instance is not for an MXBean.
         */
        public MXBeanMappingFactory getMXBeanMappingFactory() {
            return mappingFactory;
        }

        /**
         * <p>Set the {@link #getMXBeanMappingFactory() MXBeanMappingFactory} to
         * the given value.  The value should be null if this instance is not
         * for an MXBean.  If this instance is for an MXBean, the value should
         * usually be either a custom mapping factory, or
         * {@link MXBeanMappingFactory#forInterface
         * MXBeanMappingFactory.forInterface}{@code (mxbeanInterface)}
         * which signifies
         * that the {@linkplain MXBeanMappingFactory#DEFAULT default} mapping
         * factory should be used unless an {@code @}{@link
         * javax.management.openmbean.MXBeanMappingFactoryClass
         * MXBeanMappingFactoryClass} annotation on {@code mxbeanInterface}
         * specifies otherwise.</p>
         *
         * <p>Examples:</p>
         * <pre>
         * MBeanOptions opts = new MBeanOptions();
         * opts.setMXBeanMappingFactory(myMappingFactory);
         * MyMXBean proxy = JMX.newMBeanProxy(
         *         mbeanServerConnection, objectName, MyMXBean.class, opts);
         *
         * // ...or...
         *
         * MBeanOptions opts = new MBeanOptions();
         * MXBeanMappingFactory defaultFactoryForMyMXBean =
         *         MXBeanMappingFactory.forInterface(MyMXBean.class);
         * opts.setMXBeanMappingFactory(defaultFactoryForMyMXBean);
         * MyMXBean proxy = JMX.newMBeanProxy(
         *         mbeanServerConnection, objectName, MyMXBean.class, opts);
         * </pre>
         *
         * @param f the new value.  If null, this instance is not for an
         * MXBean.
         */
        public void setMXBeanMappingFactory(MXBeanMappingFactory f) {
            this.mappingFactory = f;
        }

        /* To maximise object sharing, classes in this package can replace
         * a private MBeanOptions with no MXBeanMappingFactory with one
         * of these shared instances.  But they must be EXTREMELY careful
         * never to give out the shared instances to user code, which could
         * modify them.
         */
        private static final MBeanOptions[] CANONICALS = {
            new MBeanOptions(), MXBEAN,
        };
        // Overridden in local subclasses:
        MBeanOptions[] canonicals() {
            return CANONICALS;
        }

        // This is only used by the logic for canonical instances.
        // Overridden in local subclasses:
        boolean same(MBeanOptions opt) {
            return (opt.mappingFactory == mappingFactory);
        }

        final MBeanOptions canonical() {
            for (MBeanOptions opt : canonicals()) {
                if (opt.getClass() == this.getClass() && same(opt))
                    return opt;
            }
            return this;
        }

        final MBeanOptions uncanonical() {
            for (MBeanOptions opt : canonicals()) {
                if (this == opt)
                    return clone();
            }
            return this;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new TreeMap<String, Object>();
            try {
                BeanInfo bi = java.beans.Introspector.getBeanInfo(getClass());
                PropertyDescriptor[] pds = bi.getPropertyDescriptors();
                for (PropertyDescriptor pd : pds) {
                    String name = pd.getName();
                    if (name.equals("class"))
                        continue;
                    Method get = pd.getReadMethod();
                    if (get != null)
                        map.put(name, get.invoke(this));
                }
            } catch (Exception e) {
                Throwable t = e;
                if (t instanceof InvocationTargetException)
                    t = t.getCause();
                map.put("Exception", t);
            }
            return map;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + toMap();
            // For example "MBeanOptions{MXBean=true, <etc>}".
        }

        /**
         * <p>Indicates whether some other object is "equal to" this one. The
         * result is true if and only if the other object is also an instance
         * of MBeanOptions or a subclass, and has the same properties with
         * the same values.</p>
         * @return {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            return toMap().equals(((MBeanOptions) obj).toMap());
        }

        @Override
        public int hashCode() {
            return toMap().hashCode();
        }
    }

    /**
     * <p>Options to apply to an MBean proxy.</p>
     *
     * @see #newMBeanProxy
     */
    public static class ProxyOptions extends MBeanOptions {
        private static final long serialVersionUID = 7238804866098386559L;

        private boolean notificationEmitter;

        /**
         * <p>Construct a {@code ProxyOptions} object where all options have
         * their default values.</p>
         */
        public ProxyOptions() {}

        @Override
        public ProxyOptions clone() {
            return (ProxyOptions) super.clone();
        }

        /**
         * <p>Defines whether the returned proxy should
         * implement {@link NotificationEmitter}.  The default value is false.</p>
         *
         * @return true if this proxy will be a NotificationEmitter.
         *
         * @see JMX#newMBeanProxy(MBeanServerConnection, ObjectName, Class,
         * MBeanOptions)
         */
        public boolean isNotificationEmitter() {
            return this.notificationEmitter;
        }

        /**
         * <p>Set the {@link #isNotificationEmitter NotificationEmitter} option to
         * the given value.</p>
         * @param emitter the new value.
         */
        public void setNotificationEmitter(boolean emitter) {
            this.notificationEmitter = emitter;
        }

        // Canonical objects for each of (MXBean,!MXBean) x (Emitter,!Emitter)
        private static final ProxyOptions[] CANONICALS = {
            new ProxyOptions(), new ProxyOptions(),
            new ProxyOptions(), new ProxyOptions(),
        };
        static {
            CANONICALS[1].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[2].setNotificationEmitter(true);
            CANONICALS[3].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[3].setNotificationEmitter(true);
        }
        @Override
        MBeanOptions[] canonicals() {
            return CANONICALS;
        }

        @Override
        boolean same(MBeanOptions opt) {
            return (super.same(opt) && opt instanceof ProxyOptions &&
                    ((ProxyOptions) opt).notificationEmitter == notificationEmitter);
        }
    }

    /**
     * <p>Make a proxy for a Standard MBean in a local or remote
     * MBean Server.</p>
     *
     * <p>If you have an MBean Server {@code mbs} containing an MBean
     * with {@link ObjectName} {@code name}, and if the MBean's
     * management interface is described by the Java interface
     * {@code MyMBean}, you can construct a proxy for the MBean like
     * this:</p>
     *
     * <pre>
     * MyMBean proxy = JMX.newMBeanProxy(mbs, name, MyMBean.class);
     * </pre>
     *
     * <p>Suppose, for example, {@code MyMBean} looks like this:</p>
     *
     * <pre>
     * public interface MyMBean {
     *     public String getSomeAttribute();
     *     public void setSomeAttribute(String value);
     *     public void someOperation(String param1, int param2);
     * }
     * </pre>
     *
     * <p>Then you can execute:</p>
     *
     * <ul>
     *
     * <li>{@code proxy.getSomeAttribute()} which will result in a
     * call to {@code mbs.}{@link MBeanServerConnection#getAttribute
     * getAttribute}{@code (name, "SomeAttribute")}.
     *
     * <li>{@code proxy.setSomeAttribute("whatever")} which will result
     * in a call to {@code mbs.}{@link MBeanServerConnection#setAttribute
     * setAttribute}{@code (name, new Attribute("SomeAttribute", "whatever"))}.
     *
     * <li>{@code proxy.someOperation("param1", 2)} which will be
     * translated into a call to {@code mbs.}{@link
     * MBeanServerConnection#invoke invoke}{@code (name, "someOperation", <etc>)}.
     *
     * </ul>
     *
     * <p>The object returned by this method is a
     * {@link Proxy} whose {@code InvocationHandler} is an
     * {@link MBeanServerInvocationHandler}.</p>
     *
     * <p>This method is equivalent to {@link
     * #newMBeanProxy(MBeanServerConnection, ObjectName, Class,
     * boolean) newMBeanProxy(connection, objectName, interfaceClass,
     * false)}.</p>
     *
     * @param connection the MBean server to forward to.
     * @param objectName the name of the MBean within
     * {@code connection} to forward to.
     * @param interfaceClass the management interface that the MBean
     * exports, which will also be implemented by the returned proxy.
     *
     * @param <T> allows the compiler to know that if the {@code
     * interfaceClass} parameter is {@code MyMBean.class}, for
     * example, then the return type is {@code MyMBean}.
     *
     * @return the new proxy instance.
     */
    public static <T> T newMBeanProxy(MBeanServerConnection connection,
                                      ObjectName objectName,
                                      Class<T> interfaceClass) {
        return newMBeanProxy(connection, objectName, interfaceClass, false);
    }

    /**
     * <p>Make a proxy for a Standard MBean in a local or remote MBean
     * Server that may also support the methods of {@link
     * NotificationEmitter}.</p>
     *
     * <p>This method behaves the same as {@link
     * #newMBeanProxy(MBeanServerConnection, ObjectName, Class)}, but
     * additionally, if {@code notificationEmitter} is {@code
     * true}, then the MBean is assumed to be a {@link
     * NotificationBroadcaster} or {@link NotificationEmitter} and the
     * returned proxy will implement {@link NotificationEmitter} as
     * well as {@code interfaceClass}.  A call to {@link
     * NotificationBroadcaster#addNotificationListener} on the proxy
     * will result in a call to {@link
     * MBeanServerConnection#addNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)}, and
     * likewise for the other methods of {@link
     * NotificationBroadcaster} and {@link NotificationEmitter}.</p>
     *
     * <p>This method is equivalent to {@link
     * #newMBeanProxy(MBeanServerConnection, ObjectName, Class, JMX.MBeanOptions)
     * newMBeanProxy(connection, objectName, interfaceClass, opts)}, where
     * {@code opts} is a {@link JMX.ProxyOptions} representing the
     * {@code notificationEmitter} parameter.</p>
     *
     * @param connection the MBean server to forward to.
     * @param objectName the name of the MBean within
     * {@code connection} to forward to.
     * @param interfaceClass the management interface that the MBean
     * exports, which will also be implemented by the returned proxy.
     * @param notificationEmitter make the returned proxy
     * implement {@link NotificationEmitter} by forwarding its methods
     * via {@code connection}.
     * @param <T> allows the compiler to know that if the {@code
     * interfaceClass} parameter is {@code MyMBean.class}, for
     * example, then the return type is {@code MyMBean}.
     * @return the new proxy instance.
     */
    public static <T> T newMBeanProxy(MBeanServerConnection connection,
                                      ObjectName objectName,
                                      Class<T> interfaceClass,
                                      boolean notificationEmitter) {
        ProxyOptions opts = new ProxyOptions();
        opts.setNotificationEmitter(notificationEmitter);
        return newMBeanProxy(connection, objectName, interfaceClass, opts);
    }

    /**
     * <p>Make a proxy for an MXBean in a local or remote
     * MBean Server.</p>
     *
     * <p>If you have an MBean Server {@code mbs} containing an
     * MXBean with {@link ObjectName} {@code name}, and if the
     * MXBean's management interface is described by the Java
     * interface {@code MyMXBean}, you can construct a proxy for
     * the MXBean like this:</p>
     *
     * <pre>
     * MyMXBean proxy = JMX.newMXBeanProxy(mbs, name, MyMXBean.class);
     * </pre>
     *
     * <p>Suppose, for example, {@code MyMXBean} looks like this:</p>
     *
     * <pre>
     * public interface MyMXBean {
     *     public String getSimpleAttribute();
     *     public void setSimpleAttribute(String value);
     *     public {@link java.lang.management.MemoryUsage} getMappedAttribute();
     *     public void setMappedAttribute(MemoryUsage memoryUsage);
     *     public MemoryUsage someOperation(String param1, MemoryUsage param2);
     * }
     * </pre>
     *
     * <p>Then:</p>
     *
     * <ul>
     *
     * <li><p>{@code proxy.getSimpleAttribute()} will result in a
     * call to {@code mbs.}{@link MBeanServerConnection#getAttribute
     * getAttribute}{@code (name, "SimpleAttribute")}.</p>
     *
     * <li><p>{@code proxy.setSimpleAttribute("whatever")} will result
     * in a call to {@code mbs.}{@link
     * MBeanServerConnection#setAttribute setAttribute}<code>(name,
     * new Attribute("SimpleAttribute", "whatever"))</code>.<p>
     *
     *     <p>Because {@code String} is a <em>simple type</em>, in the
     *     sense of {@link javax.management.openmbean.SimpleType}, it
     *     is not changed in the context of an MXBean.  The MXBean
     *     proxy behaves the same as a Standard MBean proxy (see
     *     {@link #newMBeanProxy(MBeanServerConnection, ObjectName,
     *     Class) newMBeanProxy}) for the attribute {@code
     *     SimpleAttribute}.</p>
     *
     * <li><p>{@code proxy.getMappedAttribute()} will result in a call
     * to {@code mbs.getAttribute("MappedAttribute")}.  The MXBean
     * mapping rules mean that the actual type of the attribute {@code
     * MappedAttribute} will be {@link
     * javax.management.openmbean.CompositeData CompositeData} and
     * that is what the {@code mbs.getAttribute} call will return.
     * The proxy will then convert the {@code CompositeData} back into
     * the expected type {@code MemoryUsage} using the MXBean mapping
     * rules.</p>
     *
     * <li><p>Similarly, {@code proxy.setMappedAttribute(memoryUsage)}
     * will convert the {@code MemoryUsage} argument into a {@code
     * CompositeData} before calling {@code mbs.setAttribute}.</p>
     *
     * <li><p>{@code proxy.someOperation("whatever", memoryUsage)}
     * will convert the {@code MemoryUsage} argument into a {@code
     * CompositeData} and call {@code mbs.invoke}.  The value returned
     * by {@code mbs.invoke} will be also be a {@code CompositeData},
     * and the proxy will convert this into the expected type {@code
     * MemoryUsage} using the MXBean mapping rules.</p>
     *
     * </ul>
     *
     * <p>This method is equivalent to {@link
     * #newMXBeanProxy(MBeanServerConnection, ObjectName, Class,
     * boolean) newMXBeanProxy(connection, objectName, interfaceClass,
     * false)}.</p>
     *
     * @param connection the MBean server to forward to.
     * @param objectName the name of the MBean within
     * {@code connection} to forward to.
     * @param interfaceClass the MXBean interface,
     * which will also be implemented by the returned proxy.
     *
     * @param <T> allows the compiler to know that if the {@code
     * interfaceClass} parameter is {@code MyMXBean.class}, for
     * example, then the return type is {@code MyMXBean}.
     *
     * @return the new proxy instance.
     */
    public static <T> T newMXBeanProxy(MBeanServerConnection connection,
                                       ObjectName objectName,
                                       Class<T> interfaceClass) {
        return newMXBeanProxy(connection, objectName, interfaceClass, false);
    }

    /**
     * <p>Make a proxy for an MXBean in a local or remote MBean
     * Server that may also support the methods of {@link
     * NotificationEmitter}.</p>
     *
     * <p>This method behaves the same as {@link
     * #newMXBeanProxy(MBeanServerConnection, ObjectName, Class)}, but
     * additionally, if {@code notificationEmitter} is {@code
     * true}, then the MXBean is assumed to be a {@link
     * NotificationBroadcaster} or {@link NotificationEmitter} and the
     * returned proxy will implement {@link NotificationEmitter} as
     * well as {@code interfaceClass}.  A call to {@link
     * NotificationBroadcaster#addNotificationListener} on the proxy
     * will result in a call to {@link
     * MBeanServerConnection#addNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)}, and
     * likewise for the other methods of {@link
     * NotificationBroadcaster} and {@link NotificationEmitter}.</p>
     *
     * <p>This method is equivalent to {@link
     * #newMBeanProxy(MBeanServerConnection, ObjectName, Class, JMX.MBeanOptions)
     * newMBeanProxy(connection, objectName, interfaceClass, opts)}, where
     * {@code opts} is a {@link JMX.ProxyOptions} where the {@link
     * JMX.ProxyOptions#getMXBeanMappingFactory() MXBeanMappingFactory}
     * property is
     * {@link MXBeanMappingFactory#forInterface(Class)
     * MXBeanMappingFactory.forInterface(interfaceClass)} and the {@link
     * JMX.ProxyOptions#isNotificationEmitter() notificationEmitter} property
     * is equal to the {@code notificationEmitter} parameter.</p>
     *
     * @param connection the MBean server to forward to.
     * @param objectName the name of the MBean within
     * {@code connection} to forward to.
     * @param interfaceClass the MXBean interface,
     * which will also be implemented by the returned proxy.
     * @param notificationEmitter make the returned proxy
     * implement {@link NotificationEmitter} by forwarding its methods
     * via {@code connection}.
     * @param <T> allows the compiler to know that if the {@code
     * interfaceClass} parameter is {@code MyMXBean.class}, for
     * example, then the return type is {@code MyMXBean}.
     * @return the new proxy instance.
     */
    public static <T> T newMXBeanProxy(MBeanServerConnection connection,
                                       ObjectName objectName,
                                       Class<T> interfaceClass,
                                       boolean notificationEmitter) {
        ProxyOptions opts = new ProxyOptions();
        MXBeanMappingFactory f = MXBeanMappingFactory.forInterface(interfaceClass);
        opts.setMXBeanMappingFactory(f);
        opts.setNotificationEmitter(notificationEmitter);
        return newMBeanProxy(connection, objectName, interfaceClass, opts);
    }

    /**
     * <p>Make a proxy for a Standard MBean or MXBean in a local or remote MBean
     * Server that may also support the methods of {@link
     * NotificationEmitter} and (for an MXBean) that may define custom MXBean
     * type mappings.</p>
     *
     * <p>This method behaves the same as
     * {@link #newMBeanProxy(MBeanServerConnection, ObjectName, Class)} or
     * {@link #newMXBeanProxy(MBeanServerConnection, ObjectName, Class)},
     * according as {@code opts.isMXBean()} is respectively false or true; but
     * with the following changes based on {@code opts}.</p>
     *
     * <ul>
     *     <li>If {@code opts.isNotificationEmitter()} is {@code
     *         true}, then the MBean is assumed to be a {@link
     *         NotificationBroadcaster} or {@link NotificationEmitter} and the
     *         returned proxy will implement {@link NotificationEmitter} as
     *         well as {@code interfaceClass}.  A call to {@link
     *         NotificationBroadcaster#addNotificationListener} on the proxy
     *         will result in a call to {@link
     *         MBeanServerConnection#addNotificationListener(ObjectName,
     *         NotificationListener, NotificationFilter, Object)}, and
     *         likewise for the other methods of {@link
     *     NotificationBroadcaster} and {@link NotificationEmitter}.</li>
     *
     *     <li>If {@code opts.getMXBeanMappingFactory()} is not null,
     *         then the mappings it defines will be applied to convert between
     *     arbitrary Java types and Open Types.</li>
     * </ul>
     *
     * <p>The object returned by this method is a
     * {@link Proxy} whose {@code InvocationHandler} is an
     * {@link MBeanServerInvocationHandler}.  This means that it is possible
     * to retrieve the parameters that were used to produce the proxy.  If the
     * proxy was produced as follows...</p>
     *
     * <pre>
     * FooMBean proxy =
     *     JMX.newMBeanProxy(connection, objectName, FooMBean.class, opts);
     * </pre>
     *
     * <p>...then you can get the {@code MBeanServerInvocationHandler} like
     * this...</p>
     *
     * <pre>
     * MBeanServerInvocationHandler mbsih = (MBeanServerInvocationHandler)
     *     {@link Proxy#getInvocationHandler(Object)
     *            Proxy.getInvocationHandler}(proxy);
     * </pre>
     *
     * <p>...and you can retrieve {@code connection}, {@code
     * objectName}, and {@code opts} using the {@link
     * MBeanServerInvocationHandler#getMBeanServerConnection()
     * getMBeanServerConnection()}, {@link
     * MBeanServerInvocationHandler#getObjectName() getObjectName()}, and
     * {@link MBeanServerInvocationHandler#getMBeanOptions() getMBeanOptions()}
     * methods on {@code mbsih}.  You can retrieve {@code FooMBean.class}
     * using {@code proxy.getClass().}{@link
     * Class#getInterfaces() getInterfaces()}.</p>
     *
     * @param connection the MBean server to forward to.
     * @param objectName the name of the MBean within
     * {@code connection} to forward to.
     * @param interfaceClass the Standard MBean or MXBean interface,
     * which will also be implemented by the returned proxy.
     * @param opts the options to apply for this proxy.  Can be null,
     * in which case default options are applied.
     * @param <T> allows the compiler to know that if the {@code
     * interfaceClass} parameter is {@code MyMXBean.class}, for
     * example, then the return type is {@code MyMXBean}.
     * @return the new proxy instance.
     *
     * @throws IllegalArgumentException if {@code interfaceClass} is not a
     * valid MXBean interface.
     */
    public static <T> T newMBeanProxy(MBeanServerConnection connection,
                                      ObjectName objectName,
                                      Class<T> interfaceClass,
                                      MBeanOptions opts) {
        try {
            return newMBeanProxy2(connection, objectName, interfaceClass, opts);
        } catch (NotCompliantMBeanException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static <T> T newMBeanProxy2(MBeanServerConnection connection,
                                        ObjectName objectName,
                                        Class<T> interfaceClass,
                                        MBeanOptions opts)
    throws NotCompliantMBeanException {

        if (opts == null)
            opts = new MBeanOptions();

        boolean notificationEmitter = opts instanceof ProxyOptions &&
                ((ProxyOptions) opts).isNotificationEmitter();

        MXBeanMappingFactory mappingFactory = opts.getMXBeanMappingFactory();

        if (mappingFactory != null) {
            // Check interface for MXBean compliance
            Introspector.testComplianceMXBeanInterface(interfaceClass,
                    mappingFactory);
        }

        InvocationHandler handler = new MBeanServerInvocationHandler(
                connection, objectName, opts);
        final Class<?>[] interfaces;
        if (notificationEmitter) {
            interfaces =
                new Class<?>[] {interfaceClass, NotificationEmitter.class};
        } else
            interfaces = new Class<?>[] {interfaceClass};
        Object proxy = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                interfaces,
                handler);
        return interfaceClass.cast(proxy);
    }

    /**
     * <p>Test whether an interface is an MXBean interface.
     * An interface is an MXBean interface if it is annotated
     * {@link MXBean &#64;MXBean} or {@code @MXBean(true)}
     * or if it does not have an {@code @MXBean} annotation
     * and its name ends with "{@code MXBean}".</p>
     *
     * @param interfaceClass The candidate interface.
     *
     * @return true if {@code interfaceClass} is an interface and
     * meets the conditions described.
     *
     * @throws NullPointerException if {@code interfaceClass} is null.
     */
    public static boolean isMXBeanInterface(Class<?> interfaceClass) {
        if (!interfaceClass.isInterface())
            return false;
        MXBean a = interfaceClass.getAnnotation(MXBean.class);
        if (a != null)
            return a.value();
        return interfaceClass.getName().endsWith("MXBean");
        // We don't bother excluding the case where the name is
        // exactly the string "MXBean" since that would mean there
        // was no package name, which is pretty unlikely in practice.
    }

    /**
     * <p>Test if an MBean can emit notifications.  An MBean can emit
     * notifications if either it implements {@link NotificationBroadcaster}
     * (perhaps through its child interface {@link NotificationEmitter}), or
     * it uses <a href="MBeanRegistration.html#injection">resource
     * injection</a> to obtain an instance of {@link SendNotification}
     * through which it can send notifications.</p>
     *
     * @param mbean an MBean object.
     * @return true if the given object is a valid MBean that can emit
     * notifications; false if the object is a valid MBean but that
     * cannot emit notifications.
     * @throws NotCompliantMBeanException if the given object is not
     * a valid MBean.
     */
    public static boolean isNotificationSource(Object mbean)
            throws NotCompliantMBeanException {
        if (mbean instanceof NotificationBroadcaster)
            return true;
        Object resource = (mbean instanceof DynamicWrapperMBean) ?
            ((DynamicWrapperMBean) mbean).getWrappedObject() : mbean;
        return (MBeanInjector.injectsSendNotification(resource));
    }

    /**
     * <p>Return the version of the JMX specification that a (possibly remote)
     * MBean Server is using.  The JMX specification described in this
     * documentation is version 2.0.  The earlier versions that might be
     * reported by this method are 1.0, 1.1, 1.2, and 1.4.  (There is no 1.3.)
     * All of these versions and all future versions can be compared using
     * {@link String#compareTo(String)}.  So, for example, to tell if
     * {@code mbsc} is running at least version 2.0 you can write:</p>
     *
     * <pre>
     * String version = JMX.getSpecificationVersion(mbsc, null);
     * boolean atLeast2dot0 = (version.compareTo("2.0") >= 0);
     * </pre>
     *
     * <p>A remote MBean Server might be running an earlier version of the
     * JMX API, and in that case <a href="package-summary.html#interop">certain
     * features</a> might not be available in it.</p>
     *
     * <p>The version of the MBean Server {@code mbsc} is not necessarily
     * the version of all namespaces within that MBean Server, for example
     * if some of them use {@link javax.management.namespace.JMXRemoteNamespace
     * JMXRemoteNamespace}.  To determine the version of the namespace
     * that a particular MBean is in, give its name as the {@code mbeanName}
     * parameter.</p>
     *
     * @param mbsc a connection to an MBean Server.
     *
     * @param mbeanName the name of an MBean within that MBean Server, or null.
     * If non-null, the namespace of this name, as determined by
     * {@link JMXNamespaces#getContainingNamespace
     * JMXNamespaces.getContainingNamespace}, is the one whose specification
     * version will be returned.
     *
     * @return the JMX specification version reported by that MBean Server.
     *
     * @throws IllegalArgumentException if {@code mbsc} is null, or if
     * {@code mbeanName} includes a wildcard character ({@code *} or {@code ?})
     * in its namespace.
     *
     * @throws IOException if the version cannot be obtained, either because
     * there is a communication problem or because the remote MBean Server
     * does not have the appropriate {@linkplain
     * MBeanServerDelegateMBean#getSpecificationVersion() attribute}.
     *
     * @see <a href="package-summary.html#interop">Interoperability between
     * versions of the JMX specification</a>
     * @see MBeanServerDelegateMBean#getSpecificationVersion
     */
    public static String getSpecificationVersion(
            MBeanServerConnection mbsc, ObjectName mbeanName)
            throws IOException {
        if (mbsc == null)
            throw new IllegalArgumentException("Null MBeanServerConnection");

        String namespace;
        if (mbeanName == null)
            namespace = "";
        else
            namespace = JMXNamespaces.getContainingNamespace(mbeanName);
        if (namespace.contains("*") || namespace.contains("?")) {
            throw new IllegalArgumentException(
                    "ObjectName contains namespace wildcard: " + mbeanName);
        }

        try {
            if (namespace.length() > 0)
                mbsc = JMXNamespaces.narrowToNamespace(mbsc, namespace);
            return (String) mbsc.getAttribute(
                    MBeanServerDelegate.DELEGATE_NAME, "SpecificationVersion");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
