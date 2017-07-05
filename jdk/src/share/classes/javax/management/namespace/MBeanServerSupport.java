/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.management.namespace;

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.mbeanserver.Util;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.DynamicWrapperMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMRuntimeException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryEval;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.loading.ClassLoaderRepository;

/**
 * <p>Base class for custom implementations of the {@link MBeanServer}
 * interface. The commonest use of this class is as the {@linkplain
 * JMXNamespace#getSourceServer() source server} for a {@link
 * JMXNamespace}, although this class can be used anywhere an {@code
 * MBeanServer} instance is required. Note that the usual ways to
 * obtain an {@code MBeanServer} instance are either to use {@link
 * java.lang.management.ManagementFactory#getPlatformMBeanServer()
 * ManagementFactory.getPlatformMBeanServer()} or to use the {@code
 * newMBeanServer} or {@code createMBeanServer} methods from {@link
 * javax.management.MBeanServerFactory MBeanServerFactory}. {@code
 * MBeanServerSupport} is for certain cases where those are not
 * appropriate.</p>
 *
 * <p>There are two main use cases for this class: <a
 * href="#special-purpose">special-purpose MBeanServer implementations</a>,
 * and <a href="#virtual">namespaces containing Virtual MBeans</a>. The next
 * sections explain these use cases.</p>
 *
 * <p>In the simplest case, a subclass needs to implement only two methods:</p>
 *
 * <ul>
 *     <li>
 *         {@link #getNames getNames} which returns the name of
 *         all MBeans handled by this {@code MBeanServer}.
 *     </li>
 *     <li>
 *         {@link #getDynamicMBeanFor getDynamicMBeanFor} which returns a
 *         {@link DynamicMBean} that can be used to invoke operations and
 *         obtain meta data (MBeanInfo) on a given MBean.
 *     </li>
 * </ul>
 *
 * <p>Subclasses can create such {@link DynamicMBean} MBeans on the fly - for
 * instance, using the class {@link javax.management.StandardMBean}, just for
 * the duration of an MBeanServer method call.</p>
 *
 * <h4 id="special-purpose">Special-purpose MBeanServer implementations</h4>
 *
 * <p>In some cases
 * the general-purpose {@code MBeanServer} that you get from
 * {@link javax.management.MBeanServerFactory MBeanServerFactory} is not
 * appropriate.  You might need different security checks, or you might
 * want a mock {@code MBeanServer} suitable for use in tests, or you might
 * want a simplified and optimized {@code MBeanServer} for a special purpose.</p>
 *
 * <p>As an example of a special-purpose {@code MBeanServer}, the class {@link
 * javax.management.QueryNotificationFilter QueryNotificationFilter} constructs
 * an {@code MBeanServer} instance every time it filters a notification,
 * with just one MBean that represents the notification. Although it could
 * use {@code MBeanServerFactory.newMBeanServer}, a special-purpose {@code
 * MBeanServer} will be quicker to create, use less memory, and have simpler
 * methods that execute faster.</p>
 *
 * <p>Here is an example of a special-purpose {@code MBeanServer}
 * implementation that contains exactly one MBean, which is specified at the
 * time of creation.</p>
 *
 * <pre>
 * public class SingletonMBeanServer extends MBeanServerSupport {
 *     private final ObjectName objectName;
 *     private final DynamicMBean mbean;
 *
 *     public SingletonMBeanServer(ObjectName objectName, DynamicMBean mbean) {
 *         this.objectName = objectName;
 *         this.mbean = mbean;
 *     }
 *
 *     &#64;Override
 *     protected {@code Set<ObjectName>} {@link #getNames getNames}() {
 *         return Collections.singleton(objectName);
 *     }
 *
 *     &#64;Override
 *     public DynamicMBean {@link #getDynamicMBeanFor
 *                                getDynamicMBeanFor}(ObjectName name)
 *             throws InstanceNotFoundException {
 *         if (objectName.equals(name))
 *             return mbean;
 *         else
 *             throw new InstanceNotFoundException(name);
 *     }
 * }
 * </pre>
 *
 * <p>Using this class, you could make an {@code MBeanServer} that contains
 * a {@link javax.management.timer.Timer Timer} MBean like this:</p>
 *
 * <pre>
 *     Timer timer = new Timer();
 *     DynamicMBean mbean = new {@link javax.management.StandardMBean
 *                                     StandardMBean}(timer, TimerMBean.class);
 *     ObjectName name = new ObjectName("com.example:type=Timer");
 *     MBeanServer timerMBS = new SingletonMBeanServer(name, mbean);
 * </pre>
 *
 * <p>When {@code getDynamicMBeanFor} always returns the same object for the
 * same name, as here, notifications work in the expected way: if the object
 * is a {@link NotificationEmitter} then listeners can be added using
 * {@link MBeanServer#addNotificationListener(ObjectName, NotificationListener,
 * NotificationFilter, Object) MBeanServer.addNotificationListener}.  If
 * {@code getDynamicMBeanFor} does not always return the same object for the
 * same name, more work is needed to make notifications work, as described
 * <a href="#notifs">below</a>.</p>
 *
 * <h4 id="virtual">Namespaces containing Virtual MBeans</h4>
 *
 * <p>Virtual MBeans are MBeans that do not exist as Java objects,
 * except transiently while they are being accessed.  This is useful when
 * there might be very many of them, or when keeping track of their creation
 * and deletion might be expensive or hard.  For example, you might have one
 * MBean per system process.  With an ordinary {@code MBeanServer}, you would
 * have to list the system processes in order to create an MBean object for
 * each one, and you would have to track the arrival and departure of system
 * processes in order to create or delete the corresponding MBeans.  With
 * Virtual MBeans, you only need the MBean for a given process at the exact
 * point where it is referenced with a call such as
 * {@link MBeanServer#getAttribute MBeanServer.getAttribute}.</p>
 *
 * <p>Here is an example of an {@code MBeanServer} implementation that has
 * one MBean for every system property.  The system property {@code "java.home"}
 * is represented by the MBean called {@code
 * com.example:type=Property,name="java.home"}, with an attribute called
 * {@code Value} that is the value of the property.</p>
 *
 * <pre>
 * public interface PropertyMBean {
 *     public String getValue();
 * }
 *
 * <a name="PropsMBS"></a>public class PropsMBS extends MBeanServerSupport {
 *     public static class PropertyImpl implements PropertyMBean {
 *         private final String name;
 *
 *         public PropertyImpl(String name) {
 *             this.name = name;
 *         }
 *
 *         public String getValue() {
 *             return System.getProperty(name);
 *         }
 *     }
 *
 *     &#64;Override
 *     public DynamicMBean {@link #getDynamicMBeanFor
 *                                getDynamicMBeanFor}(ObjectName name)
 *             throws InstanceNotFoundException {
 *
 *         // Check that the name is a legal one for a Property MBean
 *         ObjectName namePattern = ObjectName.valueOf(
 *                     "com.example:type=Property,name=\"*\"");
 *         if (!namePattern.apply(name))
 *             throw new InstanceNotFoundException(name);
 *
 *         // Extract the name of the property that the MBean corresponds to
 *         String propName = ObjectName.unquote(name.getKeyProperty("name"));
 *         if (System.getProperty(propName) == null)
 *             throw new InstanceNotFoundException(name);
 *
 *         // Construct and return a transient MBean object
 *         PropertyMBean propMBean = new PropertyImpl(propName);
 *         return new StandardMBean(propMBean, PropertyMBean.class, false);
 *     }
 *
 *     &#64;Override
 *     protected {@code Set<ObjectName>} {@link #getNames getNames}() {
 *         {@code Set<ObjectName> names = new TreeSet<ObjectName>();}
 *         Properties props = System.getProperties();
 *         for (String propName : props.stringPropertyNames()) {
 *             ObjectName objectName = ObjectName.valueOf(
 *                     "com.example:type=Property,name=" +
 *                     ObjectName.quote(propName));
 *             names.add(objectName);
 *         }
 *         return names;
 *     }
 * }
 * </pre>
 *
 * <p id="virtual-notif-example">Because the {@code getDynamicMBeanFor} method
 * returns a different object every time it is called, the default handling
 * of notifications will not work, as explained <a href="#notifs">below</a>.
 * In this case it does not matter, because the object returned by {@code
 * getDynamicMBeanFor} is not a {@code NotificationEmitter}, so {@link
 * MBeanServer#addNotificationListener(ObjectName, NotificationListener,
 * NotificationFilter, Object) MBeanServer.addNotificationListener} will
 * always fail. But if we wanted to extend {@code PropsMBS} so that the MBean
 * for property {@code "foo"} emitted a notification every time that property
 * changed, we would need to do it as shown below. (Because there is no API to
 * be informed when a property changes, this code assumes that some other code
 * calls the {@code propertyChanged} method every time a property changes.)</p>
 *
 * <pre>
 * public class PropsMBS {
 *     ...as <a href="#PropsMBS">above</a>...
 *
 *     private final {@link VirtualEventManager} vem = new VirtualEventManager();
 *
 *     &#64;Override
 *     public NotificationEmitter {@link #getNotificationEmitterFor
 *                                       getNotificationEmitterFor}(
 *             ObjectName name) throws InstanceNotFoundException {
 *         getDynamicMBeanFor(name);  // check that the name is valid
 *         return vem.{@link VirtualEventManager#getNotificationEmitterFor
 *                           getNotificationEmitterFor}(name);
 *     }
 *
 *     public void propertyChanged(String name, String newValue) {
 *         ObjectName objectName = ObjectName.valueOf(
 *                 "com.example:type=Property,name=" + ObjectName.quote(name));
 *         Notification n = new Notification(
 *                 "com.example.property.changed", objectName, 0L,
 *                 "Property " + name + " changed");
 *         n.setUserData(newValue);
 *         vem.{@link VirtualEventManager#publish publish}(objectName, n);
 *     }
 * }
 * </pre>
 *
 * <h4 id="creation">MBean creation and deletion</h4>
 *
 * <p>MBean creation through {@code MBeanServer.createMBean} is disabled
 * by default. Subclasses which need to support MBean creation
 * through {@code createMBean} need to implement a single method {@link
 * #createMBean(String, ObjectName, ObjectName, Object[], String[],
 * boolean)}.</p>
 *
 * <p>Similarly MBean registration and unregistration through {@code
 * registerMBean} and {@code unregisterMBean} are disabled by default.
 * Subclasses which need to support MBean registration and
 * unregistration will need to implement {@link #registerMBean registerMBean}
 * and {@link #unregisterMBean unregisterMBean}.</p>
 *
 * <h4 id="notifs">Notifications</h4>
 *
 * <p>By default {@link MBeanServer#addNotificationListener(ObjectName,
 * NotificationListener, NotificationFilter, Object) addNotificationListener}
 * is accepted for an MBean <em>{@code name}</em> if {@link #getDynamicMBeanFor
 * getDynamicMBeanFor}<code>(<em>name</em>)</code> returns an object that is a
 * {@link NotificationEmitter}.  That is appropriate if
 * {@code getDynamicMBeanFor}<code>(<em>name</em>)</code> always returns the
 * same object for the same <em>{@code name}</em>.  But with
 * Virtual MBeans, every call to {@code getDynamicMBeanFor} returns a new object,
 * which is discarded as soon as the MBean request has finished.
 * So a listener added to that object would be immediately forgotten.</p>
 *
 * <p>The simplest way for a subclass that defines Virtual MBeans
 * to support notifications is to create a private {@link VirtualEventManager}
 * and override the method {@link
 * #getNotificationEmitterFor getNotificationEmitterFor} as follows:</p>
 *
 * <pre>
 *     private final VirtualEventManager vem = new VirtualEventManager();
 *
 *     &#64;Override
 *     public NotificationEmitter getNotificationEmitterFor(
 *             ObjectName name) throws InstanceNotFoundException {
 *         // Check that the name is a valid Virtual MBean.
 *         // This is the easiest way to do that, but not always the
 *         // most efficient:
 *         getDynamicMBeanFor(name);
 *
 *         // Return an object that supports add/removeNotificationListener
 *         // through the VirtualEventManager.
 *         return vem.getNotificationEmitterFor(name);
 *     }
 * </pre>
 *
 * <p>A notification <em>{@code n}</em> can then be sent from the Virtual MBean
 * called <em>{@code name}</em> by calling {@link VirtualEventManager#publish
 * vem.publish}<code>(<em>name</em>, <em>n</em>)</code>.  See the example
 * <a href="#virtual-notif-example">above</a>.</p>
 *
 * @since 1.7
 */
public abstract class MBeanServerSupport implements MBeanServer {

    /**
     * A logger for this class.
     */
    private static final Logger LOG =
            JmxProperties.NAMESPACE_LOGGER;

    /**
     * <p>Make a new {@code MBeanServerSupport} instance.</p>
     */
    protected MBeanServerSupport() {
    }

    /**
     * <p>Returns a dynamically created handle that makes it possible to
     * access the named MBean for the duration of a method call.</p>
     *
     * <p>An easy way to create such a {@link DynamicMBean} handle is, for
     * instance, to create a temporary MXBean instance and to wrap it in
     * an instance of
     * {@link javax.management.StandardMBean}.
     * This handle should remain valid for the duration of the call
     * but can then be discarded.</p>
     * @param name the name of the MBean for which a request was received.
     * @return a {@link DynamicMBean} handle that can be used to invoke
     * operations on the named MBean.
     * @throws InstanceNotFoundException if no such MBean is supposed
     *         to exist.
     */
    public abstract DynamicMBean getDynamicMBeanFor(ObjectName name)
                        throws InstanceNotFoundException;

    /**
     * <p>Subclasses should implement this method to return
     * the names of all MBeans handled by this object instance.</p>
     *
     * <p>The object returned by getNames() should be safely {@linkplain
     * Set#iterator iterable} even in the presence of other threads that may
     * cause the set of names to change. Typically this means one of the
     * following:</p>
     *
     * <ul>
     * <li>the returned set of names is always the same; or
     * <li>the returned set of names is an object such as a {@link
     * java.util.concurrent.CopyOnWriteArraySet CopyOnWriteArraySet} that is
     * safely iterable even if the set is changed by other threads; or
     * <li>a new Set is constructed every time this method is called.
     * </ul>
     *
     * @return the names of all MBeans handled by this object.
     */
    protected abstract Set<ObjectName> getNames();

    /**
     * <p>List names matching the given pattern.
     * The default implementation of this method calls {@link #getNames()}
     * and returns the subset of those names matching {@code pattern}.</p>
     *
     * @param pattern an ObjectName pattern
     * @return the list of MBean names that match the given pattern.
     */
    protected Set<ObjectName> getMatchingNames(ObjectName pattern) {
        return Util.filterMatchingNames(pattern, getNames());
    }

    /**
     * <p>Returns a {@link NotificationEmitter} which can be used to
     * subscribe or unsubscribe for notifications with the named
     * mbean.</p>
     *
     * <p>The default implementation of this method calls {@link
     * #getDynamicMBeanFor getDynamicMBeanFor(name)} and returns that object
     * if it is a {@code NotificationEmitter}, otherwise null. See <a
     * href="#notifs">above</a> for further discussion of notification
     * handling.</p>
     *
     * @param name The name of the MBean whose notifications are being
     * subscribed, or unsuscribed.
     *
     * @return A {@link NotificationEmitter} that can be used to subscribe or
     * unsubscribe for notifications emitted by the named MBean, or {@code
     * null} if the MBean does not emit notifications and should not be
     * considered as a {@code NotificationEmitter}.
     *
     * @throws InstanceNotFoundException if {@code name} is not the name of
     * an MBean in this {@code MBeanServer}.
     */
    public NotificationEmitter getNotificationEmitterFor(ObjectName name)
            throws InstanceNotFoundException {
        DynamicMBean mbean = getDynamicMBeanFor(name);
        if (mbean instanceof NotificationEmitter)
            return (NotificationEmitter) mbean;
        else
            return null;
    }

    private NotificationEmitter getNonNullNotificationEmitterFor(
            ObjectName name)
            throws InstanceNotFoundException {
        NotificationEmitter emitter = getNotificationEmitterFor(name);
        if (emitter == null) {
            IllegalArgumentException iae = new IllegalArgumentException(
                    "Not a NotificationEmitter: " + name);
            throw new RuntimeOperationsException(iae);
        }
        return emitter;
    }

    /**
     * <p>Creates a new MBean in the MBean name space.
     * This operation is not supported in this base class implementation.</p>
     * The default implementation of this method always throws an {@link
     * UnsupportedOperationException}
     * wrapped in a {@link RuntimeOperationsException}.</p>
     *
     * <p>Subclasses may redefine this method to provide an implementation.
     * All the various flavors of {@code MBeanServer.createMBean} methods
     * will eventually call this method. A subclass that wishes to
     * support MBean creation through {@code createMBean} thus only
     * needs to provide an implementation for this one method.</p>
     *
     * <p>A subclass implementation of this method should respect the contract
     * of the various {@code createMBean} methods in the {@link MBeanServer}
     * interface, in particular as regards exception wrapping.</p>
     *
     * @param className The class name of the MBean to be instantiated.
     * @param name The object name of the MBean. May be null.
     * @param params An array containing the parameters of the
     * constructor to be invoked.
     * @param signature An array containing the signature of the
     * constructor to be invoked.
     * @param loaderName The object name of the class loader to be used.
     * @param useCLR This parameter is {@code true} when this method
     *        is called from one of the {@code MBeanServer.createMBean} methods
     *        whose signature does not include the {@code ObjectName} of an
     *        MBean class loader to use for loading the MBean class.
     *
     * @return An <CODE>ObjectInstance</CODE>, containing the
     * <CODE>ObjectName</CODE> and the Java class name of the newly
     * instantiated MBean.  If the contained <code>ObjectName</code>
     * is <code>n</code>, the contained Java class name is
     * <code>{@link javax.management.MBeanServer#getMBeanInfo
     * getMBeanInfo(n)}.getClassName()</code>.
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or a
     * <CODE>java.lang.Exception</CODE> that occurred when trying to
     * invoke the MBean's constructor.
     * @exception InstanceAlreadyExistsException The MBean is already
     * under the control of the MBean server.
     * @exception MBeanRegistrationException The
     * <CODE>preRegister</CODE> (<CODE>MBeanRegistration</CODE>
     * interface) method of the MBean has thrown an exception. The
     * MBean will not be registered.
     * @exception RuntimeMBeanException If the MBean's constructor or its
     * {@code preRegister} or {@code postRegister} method threw
     * a {@code RuntimeException}. If the <CODE>postRegister</CODE>
     * (<CODE>MBeanRegistration</CODE> interface) method of the MBean throws a
     * <CODE>RuntimeException</CODE>, the <CODE>createMBean</CODE> method will
     * throw a <CODE>RuntimeMBeanException</CODE>, although the MBean creation
     * and registration succeeded. In such a case, the MBean will be actually
     * registered even though the <CODE>createMBean</CODE> method
     * threw an exception. Note that <CODE>RuntimeMBeanException</CODE> can
     * also be thrown by <CODE>preRegister</CODE>, in which case the MBean
     * will not be registered.
     * @exception MBeanException The constructor of the MBean has
     * thrown an exception
     * @exception NotCompliantMBeanException This class is not a JMX
     * compliant MBean
     * @exception InstanceNotFoundException The specified class loader
     * is not registered in the MBean server.
     * @exception RuntimeOperationsException Wraps either:
     * <ul>
     * <li>a <CODE>java.lang.IllegalArgumentException</CODE>: The className
     * passed in parameter is null, the <CODE>ObjectName</CODE> passed in
     * parameter contains a pattern or no <CODE>ObjectName</CODE> is specified
     * for the MBean; or</li>
     * <li>an {@code UnsupportedOperationException} if creating MBeans is not
     * supported by this {@code MBeanServer} implementation.
     * </ul>
     */
    public ObjectInstance createMBean(String className,
            ObjectName name, ObjectName loaderName, Object[] params,
            String[] signature, boolean useCLR)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        throw newUnsupportedException("createMBean");
    }


    /**
     * <p>Attempts to determine whether the named MBean should be
     * considered as an instance of a given class.  The default implementation
     * of this method calls {@link #getDynamicMBeanFor getDynamicMBeanFor(name)}
     * to get an MBean object.  Then its behaviour is the same as the standard
     * {@link MBeanServer#isInstanceOf MBeanServer.isInstanceOf} method.</p>
     *
     * {@inheritDoc}
     */
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException {

        final DynamicMBean instance = nonNullMBeanFor(name);

        try {
            final String mbeanClassName = instance.getMBeanInfo().getClassName();

            if (mbeanClassName.equals(className))
                return true;

            final Object resource;
            final ClassLoader cl;
            if (instance instanceof DynamicWrapperMBean) {
                DynamicWrapperMBean d = (DynamicWrapperMBean) instance;
                resource = d.getWrappedObject();
                cl = d.getWrappedClassLoader();
            } else {
                resource = instance;
                cl = instance.getClass().getClassLoader();
            }

            final Class<?> classNameClass = Class.forName(className, false, cl);

            if (classNameClass.isInstance(resource))
                return true;

            if (classNameClass == NotificationBroadcaster.class ||
                    classNameClass == NotificationEmitter.class) {
                try {
                    getNotificationEmitterFor(name);
                    return true;
                } catch (Exception x) {
                    LOG.finest("MBean " + name +
                            " is not a notification emitter. Ignoring: "+x);
                    return false;
                }
            }

            final Class<?> resourceClass = Class.forName(mbeanClassName, false, cl);
            return classNameClass.isAssignableFrom(resourceClass);
        } catch (Exception x) {
            /* Could be SecurityException or ClassNotFoundException */
            LOG.logp(Level.FINEST,
                    MBeanServerSupport.class.getName(),
                    "isInstanceOf", "Exception calling isInstanceOf", x);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method returns the string
     * "DefaultDomain".</p>
     */
    public String getDefaultDomain() {
        return "DefaultDomain";
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method returns
     * {@link #getNames()}.size().</p>
     */
    public Integer getMBeanCount() {
        return getNames().size();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method first calls {@link #getNames
     * getNames()} to get a list of all MBean names,
     * and from this set of names, derives the set of domains which contain
     * MBeans.</p>
     */
    public String[] getDomains() {
        final Set<ObjectName> names = getNames();
        final Set<String> res = new TreeSet<String>();
        for (ObjectName n : names) {
            if (n == null) continue; // not allowed but you never know.
            res.add(n.getDomain());
        }
        return res.toArray(new String[res.size()]);
    }


    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link
     *    #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a handle
     * to the named MBean,
     * and then call {@link DynamicMBean#getAttribute getAttribute}
     * on that {@link DynamicMBean} handle.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public Object getAttribute(ObjectName name, String attribute)
        throws MBeanException, AttributeNotFoundException,
               InstanceNotFoundException, ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        return mbean.getAttribute(attribute);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #getDynamicMBeanFor getDynamicMBeanFor(name)}
     * to obtain a handle to the named MBean,
     * and then call {@link DynamicMBean#setAttribute setAttribute}
     * on that {@link DynamicMBean} handle.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException, AttributeNotFoundException,
            InvalidAttributeValueException, MBeanException,
            ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        mbean.setAttribute(attribute);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a
     * handle to the named MBean,
     * and then call {@link DynamicMBean#getAttributes getAttributes}
     * on that {@link DynamicMBean} handle.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public AttributeList getAttributes(ObjectName name,
            String[] attributes) throws InstanceNotFoundException,
            ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        return mbean.getAttributes(attributes);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a
     * handle to the named MBean,
     * and then call {@link DynamicMBean#setAttributes setAttributes}
     * on that {@link DynamicMBean} handle.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        return mbean.setAttributes(attributes);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a
     * handle to the named MBean,
     * and then call {@link DynamicMBean#invoke invoke}
     * on that {@link DynamicMBean} handle.</p>
     */
    public Object invoke(ObjectName name, String operationName,
                Object[] params, String[] signature)
                throws InstanceNotFoundException, MBeanException,
                       ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        return mbean.invoke(operationName, params, signature);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a
     * handle to the named MBean,
     * and then call {@link DynamicMBean#getMBeanInfo getMBeanInfo}
     * on that {@link DynamicMBean} handle.</p>
     */
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException, IntrospectionException,
               ReflectionException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        return mbean.getMBeanInfo();
   }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will call
     * {@link #getDynamicMBeanFor getDynamicMBeanFor(name)}.<!--
     * -->{@link DynamicMBean#getMBeanInfo getMBeanInfo()}.<!--
     * -->{@link MBeanInfo#getClassName getClassName()} to get the
     * class name to combine with {@code name} to produce a new
     * {@code ObjectInstance}.</p>
     */
    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException {
        final DynamicMBean mbean = nonNullMBeanFor(name);
        final String className = mbean.getMBeanInfo().getClassName();
        return new ObjectInstance(name, className);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first call {@link
     * #getDynamicMBeanFor getDynamicMBeanFor(name)} to obtain a handle to the
     * named MBean. If {@code getDynamicMBeanFor} returns an object, {@code
     * isRegistered} will return true. If {@code getDynamicMBeanFor} returns
     * null or throws {@link InstanceNotFoundException}, {@code isRegistered}
     * will return false.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public boolean isRegistered(ObjectName name) {
        try {
            final DynamicMBean mbean = getDynamicMBeanFor(name);
            return mbean!=null;
        } catch (InstanceNotFoundException x) {
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("MBean "+name+" is not registered: "+x);
            return false;
        }
    }


    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method will first
     * call {@link #queryNames queryNames}
     * to get a list of all matching MBeans, and then, for each returned name,
     * call {@link #getObjectInstance getObjectInstance(name)}.</p>
     */
    public Set<ObjectInstance> queryMBeans(ObjectName pattern, QueryExp query) {
        final Set<ObjectName> names = queryNames(pattern, query);
        if (names.isEmpty()) return Collections.emptySet();
        final Set<ObjectInstance> mbeans = new HashSet<ObjectInstance>();
        for (ObjectName name : names) {
            try {
                mbeans.add(getObjectInstance(name));
            } catch (SecurityException x) { // DLS: OK
                continue;
            } catch (InstanceNotFoundException x) { // DLS: OK
                continue;
            }
        }
        return mbeans;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method calls {@link #getMatchingNames
     * getMatchingNames(pattern)} to obtain a list of MBeans matching
     * the given name pattern. If the {@code query} parameter is null,
     * this will be the result. Otherwise, it will evaluate the
     * {@code query} parameter for each of the returned names, exactly
     * as an {@code MBeanServer} would. This might result in
     * {@link #getDynamicMBeanFor getDynamicMBeanFor} being called
     * several times for each returned name.</p>
     */
    public Set<ObjectName> queryNames(ObjectName pattern, QueryExp query) {
        try {
            final Set<ObjectName> res = getMatchingNames(pattern);
            return filterListOfObjectNames(res, query);
        } catch (Exception x) {
            LOG.fine("Unexpected exception raised in queryNames: "+x);
            LOG.log(Level.FINEST, "Unexpected exception raised in queryNames", x);
        }
        // We reach here only when an exception was raised.
        //
        return Collections.emptySet();
    }

    private final static boolean apply(final QueryExp query,
                  final ObjectName on,
                  final MBeanServer srv) {
        boolean res = false;
        MBeanServer oldServer = QueryEval.getMBeanServer();
        query.setMBeanServer(srv);
        try {
            res = query.apply(on);
        } catch (Exception e) {
            LOG.finest("QueryExp.apply threw exception, returning false." +
                    " Cause: "+e);
            res = false;
        } finally {
           /*
            * query.setMBeanServer is probably
            * QueryEval.setMBeanServer so put back the old
            * value.  Since that method uses a ThreadLocal
            * variable, this code is only needed for the
            * unusual case where the user creates a custom
            * QueryExp that calls a nested query on another
            * MBeanServer.
            */
            query.setMBeanServer(oldServer);
        }
        return res;
    }

    /**
     * Filters a {@code Set<ObjectName>} according to a pattern and a query.
     * This might be quite inefficient for virtual name spaces.
     */
    Set<ObjectName>
            filterListOfObjectNames(Set<ObjectName> list,
                                    QueryExp query) {
        if (list.isEmpty() || query == null)
            return list;

        // create a new result set
        final Set<ObjectName> result = new HashSet<ObjectName>();

        for (ObjectName on : list) {
            // if on doesn't match query exclude it.
            if (apply(query, on, this))
                result.add(on);
        }
        return result;
    }


    // Don't use {@inheritDoc}, because we don't want to say that the
    // MBeanServer replaces a reference to the MBean by its ObjectName.
    /**
     * <p>Adds a listener to a registered MBean. A notification emitted by
     * the MBean will be forwarded to the listener.</p>
     *
     * <p>This implementation calls
     * {@link #getNotificationEmitterFor getNotificationEmitterFor}
     * and invokes {@code addNotificationListener} on the
     * {@link NotificationEmitter} it returns.
     *
     * @see #getDynamicMBeanFor getDynamicMBeanFor
     * @see #getNotificationEmitterFor getNotificationEmitterFor
     */
    public void addNotificationListener(ObjectName name,
            NotificationListener listener, NotificationFilter filter,
            Object handback) throws InstanceNotFoundException {
        final NotificationEmitter emitter =
                getNonNullNotificationEmitterFor(name);
        emitter.addNotificationListener(listener, filter, handback);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation calls
     * {@link #getNotificationEmitterFor getNotificationEmitterFor}
     * and invokes {@code removeNotificationListener} on the
     * {@link NotificationEmitter} it returns.
     * @see #getDynamicMBeanFor getDynamicMBeanFor
     * @see #getNotificationEmitterFor getNotificationEmitterFor
     */
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        final NotificationEmitter emitter =
                getNonNullNotificationEmitterFor(name);
        emitter.removeNotificationListener(listener);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation calls
     * {@link #getNotificationEmitterFor getNotificationEmitterFor}
     * and invokes {@code removeNotificationListener} on the
     * {@link NotificationEmitter} it returns.
     * @see #getDynamicMBeanFor getDynamicMBeanFor
     * @see #getNotificationEmitterFor getNotificationEmitterFor
     */
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener, NotificationFilter filter,
            Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        NotificationEmitter emitter =
                getNonNullNotificationEmitterFor(name);
        emitter.removeNotificationListener(listener);
    }


    /**
     * <p>Adds a listener to a registered MBean.</p>
     *
     * <p>The default implementation of this method first calls
     * {@link #getDynamicMBeanFor getDynamicMBeanFor(listenerName)}.
     * If that successfully returns an object, call it {@code
     * mbean}, then (a) if {@code mbean} is an instance of {@link
     * NotificationListener} then this method calls {@link
     * #addNotificationListener(ObjectName, NotificationListener,
     * NotificationFilter, Object) addNotificationListener(name, mbean, filter,
     * handback)}, otherwise (b) this method throws an exception as specified
     * for this case.</p>
     *
     * <p>This default implementation is not appropriate for Virtual MBeans,
     * although that only matters if the object returned by {@code
     * getDynamicMBeanFor} can be an instance of
     * {@code NotificationListener}.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public void addNotificationListener(ObjectName name, ObjectName listenerName,
            NotificationFilter filter, Object handback)
            throws InstanceNotFoundException {
        NotificationListener listener = getListenerMBean(listenerName);
        addNotificationListener(name, listener, filter, handback);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public void removeNotificationListener(ObjectName name,
            ObjectName listenerName)
            throws InstanceNotFoundException, ListenerNotFoundException {
        NotificationListener listener = getListenerMBean(listenerName);
        removeNotificationListener(name, listener);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public void removeNotificationListener(ObjectName name,
            ObjectName listenerName, NotificationFilter filter,
            Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        NotificationListener listener = getListenerMBean(listenerName);
        removeNotificationListener(name, listener, filter, handback);
    }

    private NotificationListener getListenerMBean(ObjectName listenerName)
            throws InstanceNotFoundException {
        Object mbean = getDynamicMBeanFor(listenerName);
        if (mbean instanceof NotificationListener)
            return (NotificationListener) mbean;
        else {
            throw newIllegalArgumentException(
                    "MBean is not a NotificationListener: " + listenerName);
        }
    }


    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link InstanceNotFoundException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @return the default implementation of this method never returns.
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public ClassLoader getClassLoader(ObjectName loaderName)
            throws InstanceNotFoundException {
        final UnsupportedOperationException failed =
                new UnsupportedOperationException("getClassLoader");
        final InstanceNotFoundException x =
                new InstanceNotFoundException(String.valueOf(loaderName));
        x.initCause(failed);
        throw x;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method calls
     * {@link #getDynamicMBeanFor getDynamicMBeanFor(mbeanName)} and applies
     * the logic just described to the result.</p>
     */
    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
            throws InstanceNotFoundException {
        final DynamicMBean mbean = nonNullMBeanFor(mbeanName);
        if (mbean instanceof DynamicWrapperMBean)
            return ((DynamicWrapperMBean) mbean).getWrappedClassLoader();
        else
            return mbean.getClass().getClassLoader();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation of this method returns a
     * {@link ClassLoaderRepository} containing exactly one loader,
     * the {@linkplain Thread#getContextClassLoader() context class loader}
     * for the current thread.
     * Subclasses can override this method to return a different
     * {@code ClassLoaderRepository}.</p>
     */
    public ClassLoaderRepository getClassLoaderRepository() {
        // We return a new ClassLoaderRepository each time this
        // method is called. This is by design, because the
        // SingletonClassLoaderRepository is a very small object and
        // getClassLoaderRepository() will not be called very often
        // (the connector server calls it once) - in the context of
        // MBeanServerSupport there's a very good chance that this method will
        // *never* be called.
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        return Util.getSingleClassLoaderRepository(ccl);
    }


    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException,
            NotCompliantMBeanException {
        throw newUnsupportedException("registerMBean");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException {
        throw newUnsupportedException("unregisterMBean");
    }

    /**
     * Calls {@link #createMBean(String, ObjectName,
     *           ObjectName, Object[], String[], boolean)
     * createMBean(className, name, null, params, signature, true)};
     */
    public final ObjectInstance createMBean(String className, ObjectName name,
            Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException {
        try {
            return createMBean(className, name, null, params, signature, true);
        } catch (InstanceNotFoundException ex) {
            // should not happen!
            throw new MBeanException(ex, "Unexpected exception: " + ex);
        }
    }

    /**
     * Calls {@link #createMBean(String, ObjectName,
     *           ObjectName, Object[], String[], boolean)
     * createMBean(className,name, loaderName, params, signature, false)};
     */
    public final ObjectInstance createMBean(String className, ObjectName name,
            ObjectName loaderName, Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        return createMBean(className, name, loaderName, params, signature, false);
    }

    /**
     * Calls {@link #createMBean(String, ObjectName,
     *           ObjectName, Object[], String[], boolean)
     * createMBean(className, name, null, null, null, true)};
     */
    public final ObjectInstance createMBean(String className, ObjectName name)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException {
        try {
            return createMBean(className, name, null, null, null, true);
        } catch (InstanceNotFoundException ex) {
            // should not happen!
            throw new MBeanException(ex, "Unexpected exception: " + ex);
        }
    }

    /**
     * Calls {@link #createMBean(String, ObjectName,
     *           ObjectName, Object[], String[], boolean)
     * createMBean(className, name, loaderName, null, null, false)};
     */
    public final ObjectInstance createMBean(String className, ObjectName name,
            ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        return createMBean(className, name, loaderName, null, null, false);
    }


    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public Object instantiate(String className)
            throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public Object instantiate(String className, Object[] params,
            String[] signature) throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    public Object instantiate(String className, ObjectName loaderName,
            Object[] params, String[] signature)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        throw new UnsupportedOperationException("Not applicable.");
    }


    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This operation is not supported in this base class implementation.
     * The default implementation of this method always throws
     * {@link RuntimeOperationsException} wrapping
     * {@link UnsupportedOperationException}.</p>
     *
     * @throws javax.management.RuntimeOperationsException wrapping
     *        {@link UnsupportedOperationException}
     */
    @Deprecated
    public ObjectInputStream deserialize(String className,
            ObjectName loaderName, byte[] data)
            throws InstanceNotFoundException, OperationsException,
            ReflectionException {
        throw new UnsupportedOperationException("Not applicable.");
    }


    // Calls getDynamicMBeanFor, and throws an InstanceNotFoundException
    // if the returned mbean is null.
    // The DynamicMBean returned by this method is thus guaranteed to be
    // non null.
    //
    private DynamicMBean nonNullMBeanFor(ObjectName name)
            throws InstanceNotFoundException {
        if (name == null)
            throw newIllegalArgumentException("Null ObjectName");
        if (name.getDomain().equals("")) {
            String defaultDomain = getDefaultDomain();
            try {
                name = name.withDomain(getDefaultDomain());
            } catch (Exception e) {
                throw newIllegalArgumentException(
                        "Illegal default domain: " + defaultDomain);
            }
        }
        final DynamicMBean mbean = getDynamicMBeanFor(name);
        if (mbean!=null) return mbean;
        throw new InstanceNotFoundException(String.valueOf(name));
    }

    static RuntimeException newUnsupportedException(String operation) {
        return new RuntimeOperationsException(
            new UnsupportedOperationException(
                operation+": Not supported in this namespace"));
    }

    static RuntimeException newIllegalArgumentException(String msg) {
        return new RuntimeOperationsException(
                new IllegalArgumentException(msg));
    }

}
