/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.jmx.mbeanserver.DescriptorCache;
import com.sun.jmx.mbeanserver.Introspector;
import com.sun.jmx.mbeanserver.MBeanInjector;
import com.sun.jmx.mbeanserver.MBeanInstantiator;
import com.sun.jmx.mbeanserver.MBeanIntrospector;
import com.sun.jmx.mbeanserver.MBeanSupport;
import com.sun.jmx.mbeanserver.MXBeanSupport;
import com.sun.jmx.mbeanserver.StandardMBeanSupport;
import com.sun.jmx.mbeanserver.Util;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.management.openmbean.MXBeanMappingFactory;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanConstructorInfo;
import javax.management.openmbean.OpenMBeanConstructorInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenMBeanParameterInfoSupport;

import static com.sun.jmx.defaults.JmxProperties.MISC_LOGGER;
import static javax.management.JMX.MBeanOptions;

/**
 * <p>An MBean whose management interface is determined by reflection
 * on a Java interface.</p>
 *
 * <p>This class brings more flexibility to the notion of Management
 * Interface in the use of Standard MBeans.  Straightforward use of
 * the patterns for Standard MBeans described in the JMX Specification
 * means that there is a fixed relationship between the implementation
 * class of an MBean and its management interface (i.e., if the
 * implementation class is Thing, the management interface must be
 * ThingMBean).  This class makes it possible to keep the convenience
 * of specifying the management interface with a Java interface,
 * without requiring that there be any naming relationship between the
 * implementation and interface classes.</p>
 *
 * <p>By making a DynamicMBean out of an MBean, this class makes
 * it possible to select any interface implemented by the MBean as its
 * management interface, provided that it complies with JMX patterns
 * (i.e., attributes defined by getter/setter etc...).</p>
 *
 * <p> This class also provides hooks that make it possible to supply
 * custom descriptions and names for the {@link MBeanInfo} returned by
 * the DynamicMBean interface.</p>
 *
 * <p>Using this class, an MBean can be created with any
 * implementation class name <i>Impl</i> and with a management
 * interface defined (as for current Standard MBeans) by any interface
 * <i>Intf</i>, in one of two general ways:</p>
 *
 * <ul>
 *
 * <li>Using the public constructor
 *     {@link #StandardMBean(java.lang.Object, java.lang.Class, boolean)
 *     StandardMBean(impl,interface)}:
 *     <pre>
 *     MBeanServer mbs;
 *     ...
 *     Impl impl = new Impl(...);
 *     StandardMBean mbean = new StandardMBean(impl, Intf.class, false);
 *     mbs.registerMBean(mbean, objectName);
 *     </pre></li>
 *
 * <li>Subclassing StandardMBean:
 *     <pre>
 *     public class Impl extends StandardMBean implements Intf {
 *        public Impl() {
 *          super(Intf.class, false);
 *       }
 *       // implement methods of Intf
 *     }
 *
 *     [...]
 *
 *     MBeanServer mbs;
 *     ....
 *     Impl impl = new Impl();
 *     mbs.registerMBean(impl, objectName);
 *     </pre></li>
 *
 * </ul>
 *
 * <p>In either case, the class <i>Impl</i> must implement the
 * interface <i>Intf</i>.</p>
 *
 * <p>Standard MBeans based on the naming relationship between
 * implementation and interface classes are of course still
 * available.</p>
 *
 * <p>This class may also be used to construct MXBeans.  The usage
 * is exactly the same as for Standard MBeans except that in the
 * examples above, the {@code false} parameter to the constructor or
 * {@code super(...)} invocation is instead {@code true}.</p>
 *
 * @since 1.5
 */
public class StandardMBean implements DynamicWrapperMBean, MBeanRegistration {

    /**
     * <p>Options controlling the behavior of {@code StandardMBean} instances.</p>
     */
    public static class Options extends JMX.MBeanOptions {
        private static final long serialVersionUID = 5107355471177517164L;

        private boolean wrappedVisible;
        private boolean forwardRegistration;

        /**
         * <p>Construct an {@code Options} object where all options have
         * their default values.</p>
         */
        public Options() {}

        @Override
        public Options clone() {
            return (Options) super.clone();
        }

        /**
         * <p>Defines whether the {@link StandardMBean#getWrappedObject()
         * getWrappedObject} method returns the wrapped object.</p>
         *
         * <p>If this option is true, then {@code getWrappedObject()} will return
         * the same object as {@link StandardMBean#getImplementation()
         * getImplementation}.  Otherwise, it will return the
         * StandardMBean instance itself.  The setting of this option
         * affects the behavior of {@link MBeanServer#getClassLoaderFor
         * MBeanServer.getClassLoaderFor} and {@link MBeanServer#isInstanceOf
         * MBeanServer.isInstanceOf}.  The default value is false for
         * compatibility reasons, but true is a better value for most new code.</p>
         *
         * @return true if this StandardMBean's {@link
         * StandardMBean#getWrappedObject getWrappedObject} returns the wrapped
         * object.
         */
        public boolean isWrappedObjectVisible() {
            return this.wrappedVisible;
        }

        /**
         * <p>Set the {@link #isWrappedObjectVisible WrappedObjectVisible} option
         * to the given value.</p>
         * @param visible the new value.
         */
        public void setWrappedObjectVisible(boolean visible) {
            this.wrappedVisible = visible;
        }

        /**
         * <p>Defines whether the {@link MBeanRegistration MBeanRegistration}
         * callbacks are forwarded to the wrapped object.</p>
         *
         * <p>If this option is true, then
         * {@link #preRegister(MBeanServer, ObjectName) preRegister},
         * {@link #postRegister(Boolean) postRegister},
         * {@link #preDeregister preDeregister} and
         * {@link #postDeregister postDeregister} methods are forwarded
         * to the wrapped object, in addition to the behaviour specified
         * for the StandardMBean instance itself.
         * The default value is false for compatibility reasons, but true
         * is a better value for most new code.</p>
         *
         * @return true if the <code>MBeanRegistration</code> callbacks
         * are forwarded to the wrapped object.
         */
        public boolean isMBeanRegistrationForwarded() {
            return this.forwardRegistration;
        }

        /**
         * <p>Set the
         * {@link #isMBeanRegistrationForwarded MBeanRegistrationForwarded}
         * option to the given value.</p>
         * @param forward the new value.
         */
        public void setMBeanRegistrationForwarded(boolean forward) {
            this.forwardRegistration = forward;
        }

        // Canonical objects for each of
        // (MXBean,!MXBean) x (WVisible,!WVisible) x (Forward,!Forward)
        private static final Options[] CANONICALS = {
            new Options(), new Options(), new Options(), new Options(),
            new Options(), new Options(), new Options(), new Options(),
        };
        static {
            CANONICALS[1].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[2].setWrappedObjectVisible(true);
            CANONICALS[3].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[3].setWrappedObjectVisible(true);
            CANONICALS[4].setMBeanRegistrationForwarded(true);
            CANONICALS[5].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[5].setMBeanRegistrationForwarded(true);
            CANONICALS[6].setWrappedObjectVisible(true);
            CANONICALS[6].setMBeanRegistrationForwarded(true);
            CANONICALS[7].setMXBeanMappingFactory(MXBeanMappingFactory.DEFAULT);
            CANONICALS[7].setWrappedObjectVisible(true);
            CANONICALS[7].setMBeanRegistrationForwarded(true);
        }
        @Override
        MBeanOptions[] canonicals() {
            return CANONICALS;
        }

        @Override
        boolean same(MBeanOptions opts) {
            return (super.same(opts) && opts instanceof Options &&
                    ((Options) opts).wrappedVisible == wrappedVisible &&
                    ((Options) opts).forwardRegistration ==forwardRegistration);
        }
    }

    private final static DescriptorCache descriptors =
        DescriptorCache.getInstance(JMX.proof);

    /**
     * The DynamicMBean that wraps the MXBean or Standard MBean implementation.
     **/
    private volatile MBeanSupport<?> mbean;

    /**
     * The cached MBeanInfo.
     **/
    private volatile MBeanInfo cachedMBeanInfo;

    /**
     * The MBeanOptions for this StandardMBean.
     **/
    private MBeanOptions options;

    /**
     * Make a DynamicMBean out of <var>implementation</var>, using the
     * specified <var>mbeanInterface</var> class.
     * @param implementation The implementation of this MBean.
     *        If <code>null</code>, and null implementation is allowed,
     *        then the implementation is assumed to be <var>this</var>.
     * @param mbeanInterface The Management Interface exported by this
     *        MBean's implementation. If <code>null</code>, then this
     *        object will use standard JMX design pattern to determine
     *        the management interface associated with the given
     *        implementation.
     * @param nullImplementationAllowed <code>true</code> if a null
     *        implementation is allowed. If null implementation is allowed,
     *        and a null implementation is passed, then the implementation
     *        is assumed to be <var>this</var>.
     * @param options MBeanOptions to apply to this instance.
     * @exception IllegalArgumentException if the given
     *    <var>implementation</var> is null, and null is not allowed.
     **/
    @SuppressWarnings("unchecked")  // cast to T
    private <T> void construct(T implementation, Class<T> mbeanInterface,
                               boolean nullImplementationAllowed,
                               MBeanOptions options)
                               throws NotCompliantMBeanException {
        if (implementation == null) {
            // Have to use (T)this rather than mbeanInterface.cast(this)
            // because mbeanInterface might be null.
            if (nullImplementationAllowed)
                implementation = Util.<T>cast(this);
            else throw new IllegalArgumentException("implementation is null");
        }
        if (options == null)
            options = new MBeanOptions();
        MXBeanMappingFactory mappingFactory = options.getMXBeanMappingFactory();
        boolean mx = (mappingFactory != null);
        if (mbeanInterface == null) {
            mbeanInterface = Util.cast(Introspector.getStandardOrMXBeanInterface(
                                       implementation.getClass(), mx));
        }
        if (mx) {
            this.mbean =
                    new MXBeanSupport(implementation, mbeanInterface,
                                      mappingFactory);
        } else {
            this.mbean =
                    new StandardMBeanSupport(implementation, mbeanInterface);
        }
        this.options = options.canonical();
    }

    /**
     * <p>Make a DynamicMBean out of the object
     * <var>implementation</var>, using the specified
     * <var>mbeanInterface</var> class.</p>
     *
     * @param implementation The implementation of this MBean.
     * @param mbeanInterface The Management Interface exported by this
     *        MBean's implementation. If <code>null</code>, then this
     *        object will use standard JMX design pattern to determine
     *        the management interface associated with the given
     *        implementation.
     * @param <T> Allows the compiler to check
     * that {@code implementation} does indeed implement the class
     * described by {@code mbeanInterface}.  The compiler can only
     * check this if {@code mbeanInterface} is a class literal such
     * as {@code MyMBean.class}.
     *
     * @exception IllegalArgumentException if the given
     *    <var>implementation</var> is null.
     * @exception NotCompliantMBeanException if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if the given <var>implementation</var> does not implement the
     *    specified interface.
     **/
    public <T> StandardMBean(T implementation, Class<T> mbeanInterface)
        throws NotCompliantMBeanException {
        construct(implementation, mbeanInterface, false, null);
    }

    /**
     * <p>Make a DynamicMBean out of <var>this</var>, using the specified
     * <var>mbeanInterface</var> class.</p>
     *
     * <p>Calls {@link #StandardMBean(java.lang.Object, java.lang.Class)
     *       this(this,mbeanInterface)}.
     * This constructor is reserved to subclasses.</p>
     *
     * @param mbeanInterface The Management Interface exported by this
     *        MBean.
     *
     * @exception NotCompliantMBeanException if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if <var>this</var> does not implement the specified interface.
     **/
    protected StandardMBean(Class<?> mbeanInterface)
        throws NotCompliantMBeanException {
        construct(null, mbeanInterface, true, null);
    }

    /**
     * <p>Make a DynamicMBean out of the object
     * <var>implementation</var>, using the specified
     * <var>mbeanInterface</var> class, and choosing whether the
     * resultant MBean is an MXBean.  This constructor can be used
     * to make either Standard MBeans or MXBeans.  Unlike the
     * constructor {@link #StandardMBean(Object, Class)}, it
     * does not throw NotCompliantMBeanException.</p>
     *
     * @param implementation The implementation of this MBean.
     * @param mbeanInterface The Management Interface exported by this
     *        MBean's implementation. If <code>null</code>, then this
     *        object will use standard JMX design pattern to determine
     *        the management interface associated with the given
     *        implementation.
     * @param isMXBean If true, the {@code mbeanInterface} parameter
     * names an MXBean interface and the resultant MBean is an MXBean.
     * @param <T> Allows the compiler to check
     * that {@code implementation} does indeed implement the class
     * described by {@code mbeanInterface}.  The compiler can only
     * check this if {@code mbeanInterface} is a class literal such
     * as {@code MyMBean.class}.
     *
     * @exception IllegalArgumentException if the given
     *    <var>implementation</var> is null, or if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if the given <var>implementation</var> does not implement the
     *    specified interface.
     *
     * @since 1.6
     **/
    public <T> StandardMBean(T implementation, Class<T> mbeanInterface,
                             boolean isMXBean) {
        try {
            MBeanOptions opts = new MBeanOptions();
            if (mbeanInterface == null) {
                mbeanInterface = Util.cast(Introspector.getStandardOrMXBeanInterface(
                        implementation.getClass(), isMXBean));
            }
            if (isMXBean) {
                MXBeanMappingFactory f = MXBeanMappingFactory.forInterface(
                        mbeanInterface);
                opts.setMXBeanMappingFactory(f);
            }
            construct(implementation, mbeanInterface, false, opts);
        } catch (NotCompliantMBeanException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * <p>Make a DynamicMBean out of <var>this</var>, using the specified
     * <var>mbeanInterface</var> class, and choosing whether the resulting
     * MBean is an MXBean.  This constructor can be used
     * to make either Standard MBeans or MXBeans.  Unlike the
     * constructor {@link #StandardMBean(Object, Class)}, it
     * does not throw NotCompliantMBeanException.</p>
     *
     * <p>Calls {@link #StandardMBean(java.lang.Object, java.lang.Class, boolean)
     *       this(this, mbeanInterface, isMXBean)}.
     * This constructor is reserved to subclasses.</p>
     *
     * @param mbeanInterface The Management Interface exported by this
     *        MBean.
     * @param isMXBean If true, the {@code mbeanInterface} parameter
     * names an MXBean interface and the resultant MBean is an MXBean.
     *
     * @exception IllegalArgumentException if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if <var>this</var> does not implement the specified interface.
     *
     * @since 1.6
     **/
    protected StandardMBean(Class<?> mbeanInterface, boolean isMXBean) {
        try {
            MBeanOptions opts = new MBeanOptions();
            if (mbeanInterface == null) {
                mbeanInterface = Introspector.getStandardOrMXBeanInterface(
                        getClass(), isMXBean);
            }
            if (isMXBean) {
                MXBeanMappingFactory f = MXBeanMappingFactory.forInterface(
                        mbeanInterface);
                opts.setMXBeanMappingFactory(f);
            }
            construct(null, mbeanInterface, true, opts);
        } catch (NotCompliantMBeanException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * <p>Make a DynamicMBean out of the object
     * <var>implementation</var>, using the specified
     * <var>mbeanInterface</var> class and the specified options.</p>
     *
     * @param implementation The implementation of this MBean.
     * @param mbeanInterface The Management Interface exported by this
     *        MBean's implementation. If <code>null</code>, then this
     *        object will use standard JMX design pattern to determine
     *        the management interface associated with the given
     *        implementation.
     * @param options MBeanOptions that control the operation of the resulting
     *        MBean.
     * @param <T> Allows the compiler to check
     * that {@code implementation} does indeed implement the class
     * described by {@code mbeanInterface}.  The compiler can only
     * check this if {@code mbeanInterface} is a class literal such
     * as {@code MyMBean.class}.
     *
     * @exception IllegalArgumentException if the given
     *    <var>implementation</var> is null, or if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if the given <var>implementation</var> does not implement the
     *    specified interface.
     **/
    public <T> StandardMBean(T implementation,
                             Class<T> mbeanInterface,
                             MBeanOptions options) {
        try {
            construct(implementation, mbeanInterface, false, options);
        } catch (NotCompliantMBeanException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * <p>Make a DynamicMBean out of <var>this</var>, using the specified
     * <var>mbeanInterface</var> class and the specified options.</p>
     *
     * <p>Calls {@link #StandardMBean(Object, Class, JMX.MBeanOptions)
     *       this(this,mbeanInterface,options)}.
     * This constructor is reserved to subclasses.</p>
     *
     * @param mbeanInterface The Management Interface exported by this
     *        MBean.
     * @param options MBeanOptions that control the operation of the resulting
     *        MBean.
     *
     * @exception IllegalArgumentException if the <var>mbeanInterface</var>
     *    does not follow JMX design patterns for Management Interfaces, or
     *    if <var>this</var> does not implement the specified interface.
     **/
    protected StandardMBean(Class<?> mbeanInterface, MBeanOptions options) {
        try {
            construct(null, mbeanInterface, true, options);
        } catch (NotCompliantMBeanException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * <p>Replace the implementation object wrapped in this object.</p>
     *
     * @param implementation The new implementation of this Standard MBean
     * (or MXBean). The <code>implementation</code> object must implement
     * the Standard MBean (or MXBean) interface that was supplied when this
     * <code>StandardMBean</code> was constructed.
     *
     * @exception IllegalArgumentException if the given
     * <var>implementation</var> is null.
     * @exception IllegalStateException if the
     * {@link Options#isMBeanRegistrationForwarded MBeanRegistrationForwarded}
     * option is true.
     * @exception NotCompliantMBeanException if the given
     * <var>implementation</var> does not implement the
     * Standard MBean (or MXBean) interface that was
     * supplied at construction.
     *
     * @see #getImplementation
     **/
    public void setImplementation(Object implementation)
        throws NotCompliantMBeanException {

        if (implementation == null)
            throw new IllegalArgumentException("implementation is null");

        if(options instanceof Options &&
                ((Options) options).isMBeanRegistrationForwarded())
           throw new IllegalStateException("Implementation can't be changed " +
                   "because MBeanRegistrationForwarded option is true");

        setImplementation2(implementation);
    }

    private <T> void setImplementation2(T implementation)
    throws NotCompliantMBeanException {
        Class<? super T> intf = Util.cast(getMBeanInterface());

        if (this.mbean.isMXBean()) {
            this.mbean = new MXBeanSupport(implementation,
                    intf,
                    options.getMXBeanMappingFactory());
        } else {
            this.mbean = new StandardMBeanSupport(implementation, intf);
        }
    }

    /**
     * Get the implementation of this Standard MBean (or MXBean).
     * @return The implementation of this Standard MBean (or MXBean).
     *
     * @see #setImplementation
     **/
    public Object getImplementation() {
        return mbean.getWrappedObject();
    }

    /**
     * <p>Get the wrapped implementation object or return this object.</p>
     *
     * <p>For compatibility reasons, this method only returns the wrapped
     * implementation object if the {@link Options#isWrappedObjectVisible
     * WrappedObjectVisible} option was specified when this StandardMBean
     * was created.  Otherwise it returns {@code this}.</p>
     *
     * <p>If you want the MBeanServer's {@link MBeanServer#getClassLoaderFor
     * getClassLoaderFor} and {@link MBeanServer#isInstanceOf
     * isInstanceOf} methods to refer to the wrapped implementation and
     * not this StandardMBean object, then you must set the
     * {@code WrappedObjectVisible} option, for example using:</p>
     *
     * <pre>
     * StandardMBean.Options opts = new StandardMBean.Options();
     * opts.setWrappedObjectVisible(true);
     * StandardMBean mbean = new StandardMBean(impl, MyMBean.class, opts);
     * </pre>
     *
     * @return The wrapped implementation object, or this StandardMBean
     * instance.
     */
    public Object getWrappedObject() {
        if (options instanceof Options &&
                ((Options) options).isWrappedObjectVisible())
            return getImplementation();
        else
            return this;
    }

    /**
     * <p>Get the ClassLoader of the wrapped implementation object or of this
     * object.</p>
     *
     * <p>For compatibility reasons, this method only returns the ClassLoader
     * of the wrapped implementation object if the {@link
     * Options#isWrappedObjectVisible WrappedObjectVisible} option was
     * specified when this StandardMBean was created. Otherwise it returns
     * {@code this.getClass().getClassLoader()}.</p>
     *
     * <p>If you want the MBeanServer's {@link MBeanServer#getClassLoaderFor
     * getClassLoaderFor} and {@link MBeanServer#isInstanceOf
     * isInstanceOf} methods to refer to the wrapped implementation and
     * not this StandardMBean object, then you must set the
     * {@code WrappedObjectVisible} option, for example using:</p>
     *
     * <pre>
     * StandardMBean.Options opts = new StandardMBean.Options();
     * opts.setWrappedObjectVisible(true);
     * StandardMBean mbean = new StandardMBean(impl, MyMBean.class, opts);
     * </pre>
     *
     * @return The ClassLoader of the wrapped Cimplementation object, or of
     * this StandardMBean instance.
     */
    public ClassLoader getWrappedClassLoader() {
        return getWrappedObject().getClass().getClassLoader();
    }

    /**
     * Get the Management Interface of this Standard MBean (or MXBean).
     * @return The management interface of this Standard MBean (or MXBean).
     **/
    public final Class<?> getMBeanInterface() {
        return mbean.getMBeanInterface();
    }

    /**
     * Get the class of the implementation of this Standard MBean (or MXBean).
     * @return The class of the implementation of this Standard MBean (or MXBean).
     **/
    public Class<?> getImplementationClass() {
        return mbean.getWrappedObject().getClass();
    }

    /**
     * Return the MBeanOptions that were specified or implied for this StandardMBean
     * instance.  If an MBeanOptions object was supplied when this StandardMBean
     * instance was constructed, and if that object has not been modified in the
     * meantime, then the returned object will be equal to that object, although
     * it might not be the same object.
     * @return The MBeanOptions that were specified or implied for this StandardMBean
     * instance.
     */
    public MBeanOptions getOptions() {
        return options.uncanonical();
    }

    // ------------------------------------------------------------------
    // From the DynamicMBean interface.
    // ------------------------------------------------------------------
    public Object getAttribute(String attribute)
        throws AttributeNotFoundException,
               MBeanException,
               ReflectionException {
        return mbean.getAttribute(attribute);
    }

    // ------------------------------------------------------------------
    // From the DynamicMBean interface.
    // ------------------------------------------------------------------
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException,
               InvalidAttributeValueException,
               MBeanException,
               ReflectionException {
        mbean.setAttribute(attribute);
    }

    // ------------------------------------------------------------------
    // From the DynamicMBean interface.
    // ------------------------------------------------------------------
    public AttributeList getAttributes(String[] attributes) {
        return mbean.getAttributes(attributes);
    }

    // ------------------------------------------------------------------
    // From the DynamicMBean interface.
    // ------------------------------------------------------------------
    public AttributeList setAttributes(AttributeList attributes) {
        return mbean.setAttributes(attributes);
    }

    // ------------------------------------------------------------------
    // From the DynamicMBean interface.
    // ------------------------------------------------------------------
    public Object invoke(String actionName, Object params[], String signature[])
            throws MBeanException, ReflectionException {
        return mbean.invoke(actionName, params, signature);
    }

    /**
     * Get the {@link MBeanInfo} for this MBean.
     * <p>
     * This method implements
     * {@link javax.management.DynamicMBean#getMBeanInfo()
     *   DynamicMBean.getMBeanInfo()}.
     * <p>
     * This method first calls {@link #getCachedMBeanInfo()} in order to
     * retrieve the cached MBeanInfo for this MBean, if any. If the
     * MBeanInfo returned by {@link #getCachedMBeanInfo()} is not null,
     * then it is returned.<br>
     * Otherwise, this method builds a default MBeanInfo for this MBean,
     * using the Management Interface specified for this MBean.
     * <p>
     * While building the MBeanInfo, this method calls the customization
     * hooks that make it possible for subclasses to supply their custom
     * descriptions, parameter names, etc...<br>
     * Finally, it calls {@link #cacheMBeanInfo(javax.management.MBeanInfo)
     * cacheMBeanInfo()} in order to cache the new MBeanInfo.
     * @return The cached MBeanInfo for that MBean, if not null, or a
     *         newly built MBeanInfo if none was cached.
     **/
    public MBeanInfo getMBeanInfo() {
        try {
            final MBeanInfo cached = getCachedMBeanInfo();
            if (cached != null) return cached;
        } catch (RuntimeException x) {
            if (MISC_LOGGER.isLoggable(Level.FINEST)) {
                MISC_LOGGER.logp(Level.FINEST,
                        MBeanServerFactory.class.getName(), "getMBeanInfo",
                        "Failed to get cached MBeanInfo", x);
            }
        }

        if (MISC_LOGGER.isLoggable(Level.FINER)) {
            MISC_LOGGER.logp(Level.FINER,
                    MBeanServerFactory.class.getName(), "getMBeanInfo",
                    "Building MBeanInfo for " +
                    getImplementationClass().getName());
        }

        MBeanSupport<?> msupport = mbean;
        final MBeanInfo bi = msupport.getMBeanInfo();
        final Object impl = msupport.getWrappedObject();

        final boolean immutableInfo = immutableInfo(this.getClass());

        final String                  cname = getClassName(bi);
        final String                  text  = getDescription(bi);
        final MBeanConstructorInfo[]  ctors = getConstructors(bi,impl);
        final MBeanAttributeInfo[]    attrs = getAttributes(bi);
        final MBeanOperationInfo[]    ops   = getOperations(bi);
        final MBeanNotificationInfo[] ntfs  = getNotifications(bi);
        final Descriptor              desc  = getDescriptor(bi, immutableInfo);

        final MBeanInfo nmbi = new MBeanInfo(
                cname, text, attrs, ctors, ops, ntfs, desc);
        try {
            cacheMBeanInfo(nmbi);
        } catch (RuntimeException x) {
            if (MISC_LOGGER.isLoggable(Level.FINEST)) {
                MISC_LOGGER.logp(Level.FINEST,
                        MBeanServerFactory.class.getName(), "getMBeanInfo",
                        "Failed to cache MBeanInfo", x);
            }
        }

        return nmbi;
    }

    /**
     * Customization hook:
     * Get the className that will be used in the MBeanInfo returned by
     * this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom class name.  The default implementation returns
     * {@link MBeanInfo#getClassName() info.getClassName()}.
     * @param info The default MBeanInfo derived by reflection.
     * @return the class name for the new MBeanInfo.
     **/
    protected String getClassName(MBeanInfo info) {
        if (info == null) return getImplementationClass().getName();
        return info.getClassName();
    }

    /**
     * Customization hook:
     * Get the description that will be used in the MBeanInfo returned by
     * this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom MBean description.  The default implementation returns
     * {@link MBeanInfo#getDescription() info.getDescription()}.
     * @param info The default MBeanInfo derived by reflection.
     * @return the description for the new MBeanInfo.
     **/
    protected String getDescription(MBeanInfo info) {
        if (info == null) return null;
        return info.getDescription();
    }

    /**
     * <p>Customization hook:
     * Get the description that will be used in the MBeanFeatureInfo
     * returned by this MBean.</p>
     *
     * <p>Subclasses may redefine this method in order to supply
     * their custom description.  The default implementation returns
     * {@link MBeanFeatureInfo#getDescription()
     * info.getDescription()}.</p>
     *
     * <p>This method is called by
     *      {@link #getDescription(MBeanAttributeInfo)},
     *      {@link #getDescription(MBeanOperationInfo)},
     *      {@link #getDescription(MBeanConstructorInfo)}.</p>
     *
     * @param info The default MBeanFeatureInfo derived by reflection.
     * @return the description for the given MBeanFeatureInfo.
     **/
    protected String getDescription(MBeanFeatureInfo info) {
        if (info == null) return null;
        return info.getDescription();
    }

    /**
     * Customization hook:
     * Get the description that will be used in the MBeanAttributeInfo
     * returned by this MBean.
     *
     * <p>Subclasses may redefine this method in order to supply their
     * custom description.  The default implementation returns {@link
     * #getDescription(MBeanFeatureInfo)
     * getDescription((MBeanFeatureInfo) info)}.
     * @param info The default MBeanAttributeInfo derived by reflection.
     * @return the description for the given MBeanAttributeInfo.
     **/
    protected String getDescription(MBeanAttributeInfo info) {
        return getDescription((MBeanFeatureInfo)info);
    }

    /**
     * Customization hook:
     * Get the description that will be used in the MBeanConstructorInfo
     * returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom description.
     * The default implementation returns {@link
     * #getDescription(MBeanFeatureInfo)
     * getDescription((MBeanFeatureInfo) info)}.
     * @param info The default MBeanConstructorInfo derived by reflection.
     * @return the description for the given MBeanConstructorInfo.
     **/
    protected String getDescription(MBeanConstructorInfo info) {
        return getDescription((MBeanFeatureInfo)info);
    }

    /**
     * Customization hook:
     * Get the description that will be used for the  <var>sequence</var>
     * MBeanParameterInfo of the MBeanConstructorInfo returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom description.  The default implementation returns
     * {@link MBeanParameterInfo#getDescription() param.getDescription()}.
     *
     * @param ctor  The default MBeanConstructorInfo derived by reflection.
     * @param param The default MBeanParameterInfo derived by reflection.
     * @param sequence The sequence number of the parameter considered
     *        ("0" for the first parameter, "1" for the second parameter,
     *        etc...).
     * @return the description for the given MBeanParameterInfo.
     **/
    protected String getDescription(MBeanConstructorInfo ctor,
                                    MBeanParameterInfo   param,
                                    int sequence) {
        if (param == null) return null;
        return param.getDescription();
    }

    /**
     * Customization hook:
     * Get the name that will be used for the <var>sequence</var>
     * MBeanParameterInfo of the MBeanConstructorInfo returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom parameter name.  The default implementation returns
     * {@link MBeanParameterInfo#getName() param.getName()}.
     *
     * @param ctor  The default MBeanConstructorInfo derived by reflection.
     * @param param The default MBeanParameterInfo derived by reflection.
     * @param sequence The sequence number of the parameter considered
     *        ("0" for the first parameter, "1" for the second parameter,
     *        etc...).
     * @return the name for the given MBeanParameterInfo.
     **/
    protected String getParameterName(MBeanConstructorInfo ctor,
                                      MBeanParameterInfo param,
                                      int sequence) {
        if (param == null) return null;
        return param.getName();
    }

    /**
     * Customization hook:
     * Get the description that will be used in the MBeanOperationInfo
     * returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom description.  The default implementation returns
     * {@link #getDescription(MBeanFeatureInfo)
     * getDescription((MBeanFeatureInfo) info)}.
     * @param info The default MBeanOperationInfo derived by reflection.
     * @return the description for the given MBeanOperationInfo.
     **/
    protected String getDescription(MBeanOperationInfo info) {
        return getDescription((MBeanFeatureInfo)info);
    }

    /**
     * Customization hook:
     * Get the <var>impact</var> flag of the operation that will be used in
     * the MBeanOperationInfo returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom impact flag.  The default implementation returns
     * {@link MBeanOperationInfo#getImpact() info.getImpact()}.
     * @param info The default MBeanOperationInfo derived by reflection.
     * @return the impact flag for the given MBeanOperationInfo.
     **/
    protected int getImpact(MBeanOperationInfo info) {
        if (info == null) return MBeanOperationInfo.UNKNOWN;
        return info.getImpact();
    }

    /**
     * Customization hook:
     * Get the name that will be used for the <var>sequence</var>
     * MBeanParameterInfo of the MBeanOperationInfo returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom parameter name.  The default implementation returns
     * {@link MBeanParameterInfo#getName() param.getName()}.
     *
     * @param op    The default MBeanOperationInfo derived by reflection.
     * @param param The default MBeanParameterInfo derived by reflection.
     * @param sequence The sequence number of the parameter considered
     *        ("0" for the first parameter, "1" for the second parameter,
     *        etc...).
     * @return the name to use for the given MBeanParameterInfo.
     **/
    protected String getParameterName(MBeanOperationInfo op,
                                      MBeanParameterInfo param,
                                      int sequence) {
        if (param == null) return null;
        return param.getName();
    }

    /**
     * Customization hook:
     * Get the description that will be used for the  <var>sequence</var>
     * MBeanParameterInfo of the MBeanOperationInfo returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom description.  The default implementation returns
     * {@link MBeanParameterInfo#getDescription() param.getDescription()}.
     *
     * @param op    The default MBeanOperationInfo derived by reflection.
     * @param param The default MBeanParameterInfo derived by reflection.
     * @param sequence The sequence number of the parameter considered
     *        ("0" for the first parameter, "1" for the second parameter,
     *        etc...).
     * @return the description for the given MBeanParameterInfo.
     **/
    protected String getDescription(MBeanOperationInfo op,
                                    MBeanParameterInfo param,
                                    int sequence) {
        if (param == null) return null;
        return param.getDescription();
    }

    /**
     * Customization hook:
     * Get the MBeanConstructorInfo[] that will be used in the MBeanInfo
     * returned by this MBean.
     * <br>
     * By default, this method returns <code>null</code> if the wrapped
     * implementation is not <var>this</var>. Indeed, if the wrapped
     * implementation is not this object itself, it will not be possible
     * to recreate a wrapped implementation by calling the implementation
     * constructors through <code>MBeanServer.createMBean(...)</code>.<br>
     * Otherwise, if the wrapped implementation is <var>this</var>,
     * <var>ctors</var> is returned.
     * <br>
     * Subclasses may redefine this method in order to modify this
     * behavior, if needed.
     * @param ctors The default MBeanConstructorInfo[] derived by reflection.
     * @param impl  The wrapped implementation. If <code>null</code> is
     *        passed, the wrapped implementation is ignored and
     *        <var>ctors</var> is returned.
     * @return the MBeanConstructorInfo[] for the new MBeanInfo.
     **/
    protected MBeanConstructorInfo[]
        getConstructors(MBeanConstructorInfo[] ctors, Object impl) {
            if (ctors == null) return null;
            if (impl != null && impl != this) return null;
            return ctors;
    }

    /**
     * Customization hook:
     * Get the MBeanNotificationInfo[] that will be used in the MBeanInfo
     * returned by this MBean.
     * <br>
     * Subclasses may redefine this method in order to supply their
     * custom notifications.
     * @param info The default MBeanInfo derived by reflection.
     * @return the MBeanNotificationInfo[] for the new MBeanInfo.
     **/
    MBeanNotificationInfo[] getNotifications(MBeanInfo info) {
        return info.getNotifications();
    }

    /**
     * <p>Get the Descriptor that will be used in the MBeanInfo
     * returned by this MBean.</p>
     *
     * <p>Subclasses may redefine this method in order to supply
     * their custom descriptor.</p>
     *
     * <p>The default implementation of this method returns a Descriptor
     * that contains at least the field {@code interfaceClassName}, with
     * value {@link #getMBeanInterface()}.getName(). It may also contain
     * the field {@code immutableInfo}, with a value that is the string
     * {@code "true"} if the implementation can determine that the
     * {@code MBeanInfo} returned by {@link #getMBeanInfo()} will always
     * be the same. It may contain other fields: fields defined by the
     * JMX specification must have appropriate values, and other fields
     * must follow the conventions for non-standard field names.</p>
     *
     * @param info The default MBeanInfo derived by reflection.
     * @return the Descriptor for the new MBeanInfo.
     */
    Descriptor getDescriptor(MBeanInfo info, boolean immutableInfo) {
        ImmutableDescriptor desc;
        if (info == null ||
            info.getDescriptor() == null ||
            info.getDescriptor().getFieldNames().length == 0) {
            final String interfaceClassNameS =
                "interfaceClassName=" + getMBeanInterface().getName();
            final String immutableInfoS =
                "immutableInfo=" + immutableInfo;
            desc = new ImmutableDescriptor(interfaceClassNameS, immutableInfoS);
            desc = descriptors.get(desc);
        } else {
            Descriptor d = info.getDescriptor();
            Map<String,Object> fields = new HashMap<String,Object>();
            for (String fieldName : d.getFieldNames()) {
                if (fieldName.equals("immutableInfo")) {
                    // Replace immutableInfo as the underlying MBean/MXBean
                    // could already implement NotificationBroadcaster and
                    // return immutableInfo=true in its MBeanInfo.
                    fields.put(fieldName, Boolean.toString(immutableInfo));
                } else {
                    fields.put(fieldName, d.getFieldValue(fieldName));
                }
            }
            desc = new ImmutableDescriptor(fields);
        }
        return desc;
    }

    /**
     * Customization hook:
     * Return the MBeanInfo cached for this object.
     *
     * <p>Subclasses may redefine this method in order to implement their
     * own caching policy.  The default implementation stores one
     * {@link MBeanInfo} object per instance.
     *
     * @return The cached MBeanInfo, or null if no MBeanInfo is cached.
     *
     * @see #cacheMBeanInfo(MBeanInfo)
     **/
    protected MBeanInfo getCachedMBeanInfo() {
        return cachedMBeanInfo;
    }

    /**
     * Customization hook:
     * cache the MBeanInfo built for this object.
     *
     * <p>Subclasses may redefine this method in order to implement
     * their own caching policy.  The default implementation stores
     * <code>info</code> in this instance.  A subclass can define
     * other policies, such as not saving <code>info</code> (so it is
     * reconstructed every time {@link #getMBeanInfo()} is called) or
     * sharing a unique {@link MBeanInfo} object when several
     * <code>StandardMBean</code> instances have equal {@link
     * MBeanInfo} values.
     *
     * @param info the new <code>MBeanInfo</code> to cache.  Any
     * previously cached value is discarded.  This parameter may be
     * null, in which case there is no new cached value.
     **/
    protected void cacheMBeanInfo(MBeanInfo info) {
        cachedMBeanInfo = info;
    }

    private static <T> boolean identicalArrays(T[] a, T[] b) {
        if (a == b)
            return true;
        if (a == null || b == null || a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i])
                return false;
        }
        return true;
    }

    private static <T> boolean equal(T a, T b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    private static MBeanParameterInfo
            customize(MBeanParameterInfo pi,
                      String name,
                      String description) {
        if (equal(name, pi.getName()) &&
                equal(description, pi.getDescription()))
            return pi;
        else if (pi instanceof OpenMBeanParameterInfo) {
            OpenMBeanParameterInfo opi = (OpenMBeanParameterInfo) pi;
            return new OpenMBeanParameterInfoSupport(name,
                                                     description,
                                                     opi.getOpenType(),
                                                     pi.getDescriptor());
        } else {
            return new MBeanParameterInfo(name,
                                          pi.getType(),
                                          description,
                                          pi.getDescriptor());
        }
    }

    private static MBeanConstructorInfo
            customize(MBeanConstructorInfo ci,
                      String description,
                      MBeanParameterInfo[] signature) {
        if (equal(description, ci.getDescription()) &&
                identicalArrays(signature, ci.getSignature()))
            return ci;
        if (ci instanceof OpenMBeanConstructorInfo) {
            OpenMBeanParameterInfo[] oparams =
                paramsToOpenParams(signature);
            return new OpenMBeanConstructorInfoSupport(ci.getName(),
                                                       description,
                                                       oparams,
                                                       ci.getDescriptor());
        } else {
            return new MBeanConstructorInfo(ci.getName(),
                                            description,
                                            signature,
                                            ci.getDescriptor());
        }
    }

    private static MBeanOperationInfo
            customize(MBeanOperationInfo oi,
                      String description,
                      MBeanParameterInfo[] signature,
                      int impact) {
        if (equal(description, oi.getDescription()) &&
                identicalArrays(signature, oi.getSignature()) &&
                impact == oi.getImpact())
            return oi;
        if (oi instanceof OpenMBeanOperationInfo) {
            OpenMBeanOperationInfo ooi = (OpenMBeanOperationInfo) oi;
            OpenMBeanParameterInfo[] oparams =
                paramsToOpenParams(signature);
            return new OpenMBeanOperationInfoSupport(oi.getName(),
                                                     description,
                                                     oparams,
                                                     ooi.getReturnOpenType(),
                                                     impact,
                                                     oi.getDescriptor());
        } else {
            return new MBeanOperationInfo(oi.getName(),
                                          description,
                                          signature,
                                          oi.getReturnType(),
                                          impact,
                                          oi.getDescriptor());
        }
    }

    private static MBeanAttributeInfo
            customize(MBeanAttributeInfo ai,
                      String description) {
        if (equal(description, ai.getDescription()))
            return ai;
        if (ai instanceof OpenMBeanAttributeInfo) {
            OpenMBeanAttributeInfo oai = (OpenMBeanAttributeInfo) ai;
            return new OpenMBeanAttributeInfoSupport(ai.getName(),
                                                     description,
                                                     oai.getOpenType(),
                                                     ai.isReadable(),
                                                     ai.isWritable(),
                                                     ai.isIs(),
                                                     ai.getDescriptor());
        } else {
            return new MBeanAttributeInfo(ai.getName(),
                                          ai.getType(),
                                          description,
                                          ai.isReadable(),
                                          ai.isWritable(),
                                          ai.isIs(),
                                          ai.getDescriptor());
        }
    }

    private static OpenMBeanParameterInfo[]
            paramsToOpenParams(MBeanParameterInfo[] params) {
        if (params instanceof OpenMBeanParameterInfo[])
            return (OpenMBeanParameterInfo[]) params;
        OpenMBeanParameterInfo[] oparams =
            new OpenMBeanParameterInfoSupport[params.length];
        System.arraycopy(params, 0, oparams, 0, params.length);
        return oparams;
    }

    // ------------------------------------------------------------------
    // Build the custom MBeanConstructorInfo[]
    // ------------------------------------------------------------------
    private MBeanConstructorInfo[]
            getConstructors(MBeanInfo info, Object impl) {
        final MBeanConstructorInfo[] ctors =
            getConstructors(info.getConstructors(), impl);
        if (ctors == null)
            return null;
        final int ctorlen = ctors.length;
        final MBeanConstructorInfo[] nctors = new MBeanConstructorInfo[ctorlen];
        for (int i=0; i<ctorlen; i++) {
            final MBeanConstructorInfo c = ctors[i];
            final MBeanParameterInfo[] params = c.getSignature();
            final MBeanParameterInfo[] nps;
            if (params != null) {
                final int plen = params.length;
                nps = new MBeanParameterInfo[plen];
                for (int ii=0;ii<plen;ii++) {
                    MBeanParameterInfo p = params[ii];
                    nps[ii] = customize(p,
                                        getParameterName(c,p,ii),
                                        getDescription(c,p,ii));
                }
            } else {
                nps = null;
            }
            nctors[i] =
                customize(c, getDescription(c), nps);
        }
        return nctors;
    }

    // ------------------------------------------------------------------
    // Build the custom MBeanOperationInfo[]
    // ------------------------------------------------------------------
    private MBeanOperationInfo[] getOperations(MBeanInfo info) {
        final MBeanOperationInfo[] ops = info.getOperations();
        if (ops == null)
            return null;
        final int oplen = ops.length;
        final MBeanOperationInfo[] nops = new MBeanOperationInfo[oplen];
        for (int i=0; i<oplen; i++) {
            final MBeanOperationInfo o = ops[i];
            final MBeanParameterInfo[] params = o.getSignature();
            final MBeanParameterInfo[] nps;
            if (params != null) {
                final int plen = params.length;
                nps = new MBeanParameterInfo[plen];
                for (int ii=0;ii<plen;ii++) {
                    MBeanParameterInfo p = params[ii];
                    nps[ii] = customize(p,
                                        getParameterName(o,p,ii),
                                        getDescription(o,p,ii));
                }
            } else {
                nps = null;
            }
            nops[i] = customize(o, getDescription(o), nps, getImpact(o));
        }
        return nops;
    }

    // ------------------------------------------------------------------
    // Build the custom MBeanAttributeInfo[]
    // ------------------------------------------------------------------
    private MBeanAttributeInfo[] getAttributes(MBeanInfo info) {
        final MBeanAttributeInfo[] atts = info.getAttributes();
        if (atts == null)
            return null; // should not happen
        final MBeanAttributeInfo[] natts;
        final int attlen = atts.length;
        natts = new MBeanAttributeInfo[attlen];
        for (int i=0; i<attlen; i++) {
            final MBeanAttributeInfo a = atts[i];
            natts[i] = customize(a, getDescription(a));
        }
        return natts;
    }

    // ------------------------------------------------------------------
    // Resolve from a type name to a Class.
    // ------------------------------------------------------------------
    private static Class<?> resolveClass(MBeanFeatureInfo info, String type,
            Class<?> mbeanItf)
            throws ClassNotFoundException {
        String t = (String) info.getDescriptor().
                getFieldValue(JMX.ORIGINAL_TYPE_FIELD);
        if (t == null) {
            t = type;
        }
        Class<?> clazz = MBeanInstantiator.primitiveType(t);
        if(clazz == null)
            clazz = Class.forName(t, false, mbeanItf.getClassLoader());
        return clazz;
    }

    // ------------------------------------------------------------------
    // Return the subset of valid Management methods
    // ------------------------------------------------------------------
    private static Method getManagementMethod(final Class<?> mbeanType,
            String opName, Class<?>... parameters) throws NoSuchMethodException,
            SecurityException {
        Method m = mbeanType.getMethod(opName, parameters);
        if (mbeanType.isInterface()) {
            return m;
        }
        final List<Method> methods = new ArrayList<Method>();
        try {
            MBeanIntrospector.getAnnotatedMethods(mbeanType, methods);
        }catch (SecurityException ex) {
            throw ex;
        }catch (NoSuchMethodException ex) {
            throw ex;
        }catch (Exception ex) {
            NoSuchMethodException nsme =
                    new NoSuchMethodException(ex.toString());
            nsme.initCause(ex);
            throw nsme;
        }

        if(methods.contains(m)) return m;

        throw new NoSuchMethodException("Operation " + opName +
                " not found in management interface " + mbeanType.getName());
    }
    /**
     * Retrieve the set of MBean attribute accessor <code>Method</code>s
     * located in the <code>mbeanInterface</code> MBean interface that
     * correspond to the <code>attr</code> <code>MBeanAttributeInfo</code>
     * parameter.
     * @param mbeanInterface the management interface.
     * Can be a standard MBean or MXBean interface, or a Java class
     * annotated with {@link MBean &#64;MBean} or {@link MXBean &#64;MXBean}.
     * @param attr The attribute we want the accessors for.
     * @return The set of accessors.
     * @throws java.lang.NoSuchMethodException if no accessor exists
     * for the given {@link MBeanAttributeInfo MBeanAttributeInfo}.
     * @throws java.lang.IllegalArgumentException if at least one
     * of the two parameters is null.
     * @throws java.lang.ClassNotFoundException if the class named in the
     * attribute type is not found.
     * @throws java.lang.SecurityException if this exception is
     * encountered while introspecting the MBean interface.
     */
    public static Set<Method> findAttributeAccessors(Class<?> mbeanInterface,
            MBeanAttributeInfo attr)
            throws NoSuchMethodException,
            ClassNotFoundException {
        if (mbeanInterface == null || attr == null) {
            throw new IllegalArgumentException("mbeanInterface or attr " +
                    "parameter is null");
        }
        String attributeName = attr.getName();
        Set<Method> methods = new HashSet<Method>();
        Class<?> clazz = resolveClass(attr, attr.getType(), mbeanInterface);
        if (attr.isReadable()) {
            String radical = "get";
            if(attr.isIs()) radical = "is";
            Method getter = getManagementMethod(mbeanInterface, radical +
                    attributeName);
            if (getter.getReturnType().equals(clazz)) {
                methods.add(getter);
            } else {
                throw new NoSuchMethodException("Invalid getter return type, " +
                        "should be " + clazz + ", found " +
                        getter.getReturnType());
            }
        }
        if (attr.isWritable()) {
            Method setter = getManagementMethod(mbeanInterface, "set" +
                    attributeName,
                    clazz);
            if (setter.getReturnType().equals(Void.TYPE)) {
                methods.add(setter);
            } else {
                throw new NoSuchMethodException("Invalid setter return type, " +
                        "should be void, found " + setter.getReturnType());
            }
        }
        return methods;
    }

    /**
     * Retrieve the MBean operation <code>Method</code>
     * located in the <code>mbeanInterface</code> MBean interface that
     * corresponds to the provided <code>op</code>
     * <code>MBeanOperationInfo</code> parameter.
     * @param mbeanInterface the management interface.
     * Can be a standard MBean or MXBean interface, or a Java class
     * annotated with {@link MBean &#64;MBean} or {@link MXBean &#64;MXBean}.
     * @param op The operation we want the method for.
     * @return the method corresponding to the provided MBeanOperationInfo.
     * @throws java.lang.NoSuchMethodException if no method exists
     * for the given {@link MBeanOperationInfo MBeanOperationInfo}.
     * @throws java.lang.IllegalArgumentException if at least one
     * of the two parameters is null.
     * @throws java.lang.ClassNotFoundException if one of the
     * classes named in the operation signature array is not found.
     * @throws java.lang.SecurityException if this exception is
     * encountered while introspecting the MBean interface.
     */
    public static Method findOperationMethod(Class<?> mbeanInterface,
            MBeanOperationInfo op)
            throws ClassNotFoundException, NoSuchMethodException {
        if (mbeanInterface == null || op == null) {
            throw new IllegalArgumentException("mbeanInterface or op " +
                    "parameter is null");
        }
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (MBeanParameterInfo info : op.getSignature()) {
            Class<?> clazz = resolveClass(info, info.getType(), mbeanInterface);
            classes.add(clazz);
        }
        Class<?>[] signature = new Class<?>[classes.size()];
        classes.toArray(signature);
        return getManagementMethod(mbeanInterface, op.getName(), signature);
    }

    /**
     * <p>Allows the MBean to perform any operations it needs before
     * being registered in the MBean server.  If the name of the MBean
     * is not specified, the MBean can provide a name for its
     * registration.  If any exception is raised, the MBean will not be
     * registered in the MBean server.</p>
     *
     * <p>The default implementation of this method returns the {@code name}
     * parameter. If the
     * {@link Options#isMBeanRegistrationForwarded MBeanRegistrationForwarded}
     * option is set to true, then this method is forwarded to the object
     * returned by the {@link #getImplementation getImplementation()} method.
     * The name returned by this call is then returned by this method.
     * It does nothing else for Standard MBeans.  For MXBeans, it records
     * the {@code MBeanServer} and {@code ObjectName} parameters so they can
     * be used to translate inter-MXBean references.</p>
     *
     * <p>It is good practice for a subclass that overrides this method
     * to call the overridden method via {@code super.preRegister(...)}.
     * This is necessary if this object is an MXBean that is referenced
     * by attributes or operations in other MXBeans.</p>
     *
     * @param server The MBean server in which the MBean will be registered.
     *
     * @param name The object name of the MBean.  This name is null if
     * the name parameter to one of the <code>createMBean</code> or
     * <code>registerMBean</code> methods in the {@link MBeanServer}
     * interface is null.  In that case, this method must return a
     * non-null ObjectName for the new MBean.
     *
     * @return The name under which the MBean is to be registered.
     * This value must not be null.  If the <code>name</code>
     * parameter is not null, it will usually but not necessarily be
     * the returned value.
     *
     * @throws IllegalArgumentException if this is an MXBean and
     * {@code name} is null.
     *
     * @throws InstanceAlreadyExistsException if this is an MXBean and
     * it has already been registered under another name (in this
     * MBean Server or another).
     *
     * @throws Exception no other checked exceptions are thrown by
     * this method but {@code Exception} is declared so that subclasses
     * can override the method and throw their own exceptions.
     *
     * @since 1.6
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        // Forward preRegister before to call register and
        // inject parameters.
        if(shouldForwardMBeanRegistration())
            name = ((MBeanRegistration)getImplementation()).
                    preRegister(server, name);
        mbean.register(server, name);
        MBeanInjector.inject(mbean.getWrappedObject(), server, name);
        return name;
    }

    /**
     * <p>Allows the MBean to perform any operations needed after having been
     * registered in the MBean server or after the registration has failed.</p>
     *
     * <p>If the
     * {@link Options#isMBeanRegistrationForwarded MBeanRegistrationForwarded}
     * option is set to true, then this method is forwarded to the object
     * returned by the {@link #getImplementation getImplementation()} method.
     * The default implementation of this method does nothing else for
     * Standard MBeans.  For MXBeans, it undoes any work done by
     * {@link #preRegister preRegister} if registration fails.</p>
     *
     * <p>It is good practice for a subclass that overrides this method
     * to call the overridden method via {@code super.postRegister(...)}.
     * This is necessary if this object is an MXBean that is referenced
     * by attributes or operations in other MXBeans.</p>
     *
     * @param registrationDone Indicates whether or not the MBean has
     * been successfully registered in the MBean server. The value
     * false means that the registration phase has failed.
     *
     * @since 1.6
     */
    public void postRegister(Boolean registrationDone) {
        if (!registrationDone)
            mbean.unregister();
        if(shouldForwardMBeanRegistration())
            ((MBeanRegistration)getImplementation()).
                    postRegister(registrationDone);
    }

    /**
     * <p>Allows the MBean to perform any operations it needs before
     * being unregistered by the MBean server.</p>
     *
     * <p>If the
     * {@link Options#isMBeanRegistrationForwarded MBeanRegistrationForwarded}
     * option is set to true, then this method is forwarded to the object
     * returned by the {@link #getImplementation getImplementation()} method.
     * Other than that, the default implementation of this method does nothing.
     * </p>
     *
     * <p>It is good practice for a subclass that overrides this method
     * to call the overridden method via {@code super.preDeregister(...)}.</p>
     *
     * @throws Exception no checked exceptions are throw by this method
     * but {@code Exception} is declared so that subclasses can override
     * this method and throw their own exceptions.
     *
     * @since 1.6
     */
    public void preDeregister() throws Exception {
        if(shouldForwardMBeanRegistration())
            ((MBeanRegistration)getImplementation()).preDeregister();
    }

    /**
     * <p>Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.</p>
     *
     * <p>If the
     * {@link Options#isMBeanRegistrationForwarded MBeanRegistrationForwarded}
     * option is set to true, then this method is forwarded to the object
     * returned by the {@link #getImplementation getImplementation()} method.
     * The default implementation of this method does nothing else for
     * Standard MBeans.  For MXBeans, it removes any information that
     * was recorded by the {@link #preRegister preRegister} method.</p>
     *
     * <p>It is good practice for a subclass that overrides this method
     * to call the overridden method via {@code super.postRegister(...)}.
     * This is necessary if this object is an MXBean that is referenced
     * by attributes or operations in other MXBeans.</p>
     *
     * @since 1.6
     */
    public void postDeregister() {
        mbean.unregister();
        if(shouldForwardMBeanRegistration())
            ((MBeanRegistration)getImplementation()).postDeregister();
    }

    private boolean shouldForwardMBeanRegistration() {
        return (getImplementation() instanceof MBeanRegistration) &&
           (options instanceof Options &&
                ((Options) options).isMBeanRegistrationForwarded());
    }
    //
    // MBeanInfo immutability
    //

    /**
     * Cached results of previous calls to immutableInfo. This is
     * a WeakHashMap so that we don't prevent a class from being
     * garbage collected just because we know whether its MBeanInfo
     * is immutable.
     */
    private static final Map<Class<?>, Boolean> mbeanInfoSafeMap =
        new WeakHashMap<Class<?>, Boolean>();

    /**
     * Return true if {@code subclass} is known to preserve the immutability
     * of the {@code MBeanInfo}. The {@code subclass} is considered to have
     * an immutable {@code MBeanInfo} if it does not override any of the
     * getMBeanInfo, getCachedMBeanInfo, cacheMBeanInfo and getNotificationInfo
     * methods.
     */
    static boolean immutableInfo(Class<? extends StandardMBean> subclass) {
        if (subclass == StandardMBean.class ||
            subclass == StandardEmitterMBean.class)
            return true;
        synchronized (mbeanInfoSafeMap) {
            Boolean safe = mbeanInfoSafeMap.get(subclass);
            if (safe == null) {
                try {
                    MBeanInfoSafeAction action =
                        new MBeanInfoSafeAction(subclass);
                    safe = AccessController.doPrivileged(action);
                } catch (Exception e) { // e.g. SecurityException
                    /* We don't know, so we assume it isn't.  */
                    safe = false;
                }
                mbeanInfoSafeMap.put(subclass, safe);
            }
            return safe;
        }
    }

    static boolean overrides(Class<?> subclass, Class<?> superclass,
                             String name, Class<?>... params) {
        for (Class<?> c = subclass; c != superclass; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod(name, params);
                return true;
            } catch (NoSuchMethodException e) {
                // OK: this class doesn't override it
            }
        }
        return false;
    }

    private static class MBeanInfoSafeAction
            implements PrivilegedAction<Boolean> {

        private final Class<?> subclass;

        MBeanInfoSafeAction(Class<?> subclass) {
            this.subclass = subclass;
        }

        public Boolean run() {
            // Check for "void cacheMBeanInfo(MBeanInfo)" method.
            //
            if (overrides(subclass, StandardMBean.class,
                          "cacheMBeanInfo", MBeanInfo.class))
                return false;

            // Check for "MBeanInfo getCachedMBeanInfo()" method.
            //
            if (overrides(subclass, StandardMBean.class,
                          "getCachedMBeanInfo", (Class<?>[]) null))
                return false;

            // Check for "MBeanInfo getMBeanInfo()" method.
            //
            if (overrides(subclass, StandardMBean.class,
                          "getMBeanInfo", (Class<?>[]) null))
                return false;

            // Check for "MBeanNotificationInfo[] getNotificationInfo()"
            // method.
            //
            // This method is taken into account for the MBeanInfo
            // immutability checks if and only if the given subclass is
            // StandardEmitterMBean itself or can be assigned to
            // StandardEmitterMBean.
            //
            if (StandardEmitterMBean.class.isAssignableFrom(subclass))
                if (overrides(subclass, StandardEmitterMBean.class,
                              "getNotificationInfo", (Class<?>[]) null))
                    return false;
            return true;
        }
    }
}
