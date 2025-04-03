/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.management;


// java import
import java.util.Set;
import java.io.ObjectInputStream;

// RI import
import javax.management.loading.ClassLoaderRepository;

/**
 * <p>This is the interface for MBean manipulation on the agent
 * side. It contains the methods necessary for the creation,
 * registration, and deletion of MBeans as well as the access methods
 * for registered MBeans.  This is the core component of the JMX
 * infrastructure.</p>
 *
 * <p>User code does not usually implement this interface.  Instead,
 * an object that implements this interface is obtained with one of
 * the methods in the {@link javax.management.MBeanServerFactory} class.</p>
 *
 * <p>Every MBean which is added to the MBean server becomes
 * manageable: its attributes and operations become remotely
 * accessible through the connectors/adaptors connected to that MBean
 * server.  A Java object cannot be registered in the MBean server
 * unless it is a JMX compliant MBean.</p>
 *
 * <p id="notif">When an MBean is registered or unregistered in the
 * MBean server a {@link javax.management.MBeanServerNotification
 * MBeanServerNotification} Notification is emitted. To register an
 * object as listener to MBeanServerNotifications you should call the
 * MBean server method {@link #addNotificationListener
 * addNotificationListener} with <CODE>ObjectName</CODE> the
 * <CODE>ObjectName</CODE> of the {@link
 * javax.management.MBeanServerDelegate MBeanServerDelegate}.  This
 * <CODE>ObjectName</CODE> is: <BR>
 * <CODE>JMImplementation:type=MBeanServerDelegate</CODE>.</p>
 *
 * <p>Methods of {@code MBeanServer} and its subclasses may throw
 * {@link SecurityException} if the implementation doesn't authorize
 * access to the underlying resource.
 *
 * @since 1.5
 */

public interface MBeanServer extends MBeanServerConnection {

    /**
     * {@inheritDoc}
     * <p>If this method successfully creates an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     * @throws RuntimeMBeanException {@inheritDoc}
     * @throws RuntimeErrorException {@inheritDoc}
     */
    public ObjectInstance createMBean(String className, ObjectName name)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException;

    /**
     * {@inheritDoc}
     * <p>If this method successfully creates an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     * @throws RuntimeMBeanException {@inheritDoc}
     * @throws RuntimeErrorException {@inheritDoc}
     */
    public ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException;

    /**
     * {@inheritDoc}
     * <p>If this method successfully creates an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     * @throws RuntimeMBeanException {@inheritDoc}
     * @throws RuntimeErrorException {@inheritDoc}
     */
    public ObjectInstance createMBean(String className, ObjectName name,
                                      Object params[], String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException;

    /**
     * {@inheritDoc}
     * <p>If this method successfully creates an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     * @throws RuntimeMBeanException {@inheritDoc}
     * @throws RuntimeErrorException {@inheritDoc}
     */
    public ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName, Object params[],
                                      String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException;

    /**
     * <p>Registers a pre-existing object as an MBean with the MBean
     * server. If the object name given is null, the MBean must
     * provide its own name by implementing the {@link
     * javax.management.MBeanRegistration MBeanRegistration} interface
     * and returning the name from the {@link
     * MBeanRegistration#preRegister preRegister} method.
     *
     * <p>If this method successfully registers an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @param object The  MBean to be registered as an MBean.
     * @param name The object name of the MBean. May be null.
     *
     * @return An <CODE>ObjectInstance</CODE>, containing the
     * <CODE>ObjectName</CODE> and the Java class name of the newly
     * registered MBean.  If the contained <code>ObjectName</code>
     * is <code>n</code>, the contained Java class name is
     * <code>{@link #getMBeanInfo getMBeanInfo(n)}.getClassName()</code>.
     *
     * @exception InstanceAlreadyExistsException The MBean is already
     * under the control of the MBean server.
     * @exception MBeanRegistrationException The
     * <CODE>preRegister</CODE> (<CODE>MBeanRegistration</CODE>
     * interface) method of the MBean has thrown an exception. The
     * MBean will not be registered.
     * @exception RuntimeMBeanException If the <CODE>postRegister</CODE>
     * (<CODE>MBeanRegistration</CODE> interface) method of the MBean throws a
     * <CODE>RuntimeException</CODE>, the <CODE>registerMBean</CODE> method will
     * throw a <CODE>RuntimeMBeanException</CODE>, although the MBean
     * registration succeeded. In such a case, the MBean will be actually
     * registered even though the <CODE>registerMBean</CODE> method
     * threw an exception.  Note that <CODE>RuntimeMBeanException</CODE> can
     * also be thrown by <CODE>preRegister</CODE>, in which case the MBean
     * will not be registered.
     * @exception RuntimeErrorException If the <CODE>postRegister</CODE>
     * (<CODE>MBeanRegistration</CODE> interface) method of the MBean throws an
     * <CODE>Error</CODE>, the <CODE>registerMBean</CODE> method will
     * throw a <CODE>RuntimeErrorException</CODE>, although the MBean
     * registration succeeded. In such a case, the MBean will be actually
     * registered even though the <CODE>registerMBean</CODE> method
     * threw an exception.  Note that <CODE>RuntimeErrorException</CODE> can
     * also be thrown by <CODE>preRegister</CODE>, in which case the MBean
     * will not be registered.
     * @exception NotCompliantMBeanException This object is not a JMX
     * compliant MBean
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: The object
     * passed in parameter is null or no object name is specified.
     * @see javax.management.MBeanRegistration
     */
    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException,
                   NotCompliantMBeanException;

    /**
     * {@inheritDoc}
     *
     * <p>If this method successfully unregisters an MBean, a notification
     * is sent as described <a href="#notif">above</a>.</p>
     *
     * @throws RuntimeOperationsException {@inheritDoc}
     * @throws RuntimeMBeanException {@inheritDoc}
     * @throws RuntimeErrorException {@inheritDoc}
     */
    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException;

    // doc comment inherited from MBeanServerConnection
    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException;

    /**
     * {@inheritDoc}
     */
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query);

    /**
     * {@inheritDoc}
     */
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query);

    // doc comment inherited from MBeanServerConnection
    /**
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public boolean isRegistered(ObjectName name);

    /**
     * Returns the number of MBeans registered in the MBean server.
     *
     * @return the number of registered MBeans, wrapped in an Integer.
     * If the caller's permissions are restricted, this number may
     * be greater than the number of MBeans the caller can access.
     */
    public Integer getMBeanCount();

    // doc comment inherited from MBeanServerConnection
    /**
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException;

    // doc comment inherited from MBeanServerConnection
    /**
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException;

    // doc comment inherited from MBeanServerConnection
    /**
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException,
                   InvalidAttributeValueException, MBeanException,
                   ReflectionException;

    // doc comment inherited from MBeanServerConnection
    /**
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException;

    // doc comment inherited from MBeanServerConnection
    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException;

    // doc comment inherited from MBeanServerConnection
    public String getDefaultDomain();

    // doc comment inherited from MBeanServerConnection
    public String[] getDomains();

    // doc comment inherited from MBeanServerConnection, plus:
    /**
     * {@inheritDoc}
     * If the source of the notification
     * is a reference to an MBean object, the MBean server will replace it
     * by that MBean's ObjectName.  Otherwise the source is unchanged.
     */
    public void addNotificationListener(ObjectName name,
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException;

    /**
     * {@inheritDoc}
     * @throws RuntimeOperationsException {@inheritDoc}
     */
    public void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException;

    // doc comment inherited from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException;

    // doc comment inherited from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException;

    // doc comment inherited from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException;

    // doc comment inherited from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException;

    // doc comment inherited from MBeanServerConnection
    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException,
                   ReflectionException;


    // doc comment inherited from MBeanServerConnection
    public boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException;

    /**
     * <p>Instantiates an object using the list of all class loaders
     * registered in the MBean server's {@link
     * javax.management.loading.ClassLoaderRepository Class Loader
     * Repository}.  The object's class should have a public
     * constructor.  This method returns a reference to the newly
     * created object.  The newly created object is not registered in
     * the MBean server.</p>
     *
     * <p>This method is equivalent to {@link
     * #instantiate(String,Object[],String[])
     * instantiate(className, (Object[]) null, (String[]) null)}.</p>
     *
     * @param className The class name of the object to be instantiated.
     *
     * @return The newly instantiated object.
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to
     * invoke the object's constructor.
     * @exception MBeanException The constructor of the object has
     * thrown an exception
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: The className
     * passed in parameter is null.
     */
    public Object instantiate(String className)
            throws ReflectionException, MBeanException;


    /**
     * <p>Instantiates an object using the class Loader specified by its
     * <CODE>ObjectName</CODE>.  If the loader name is null, the
     * ClassLoader that loaded the MBean Server will be used.  The
     * object's class should have a public constructor.  This method
     * returns a reference to the newly created object.  The newly
     * created object is not registered in the MBean server.</p>
     *
     * <p>This method is equivalent to {@link
     * #instantiate(String,ObjectName,Object[],String[])
     * instantiate(className, loaderName, (Object[]) null, (String[])
     * null)}.</p>
     *
     * @param className The class name of the MBean to be instantiated.
     * @param loaderName The object name of the class loader to be used.
     *
     * @return The newly instantiated object.
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to
     * invoke the object's constructor.
     * @exception MBeanException The constructor of the object has
     * thrown an exception.
     * @exception InstanceNotFoundException The specified class loader
     * is not registered in the MBeanServer.
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: The className
     * passed in parameter is null.
     */
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
                   InstanceNotFoundException;

    /**
     * <p>Instantiates an object using the list of all class loaders
     * registered in the MBean server {@link
     * javax.management.loading.ClassLoaderRepository Class Loader
     * Repository}.  The object's class should have a public
     * constructor.  The call returns a reference to the newly created
     * object.  The newly created object is not registered in the
     * MBean server.</p>
     *
     * @param className The class name of the object to be instantiated.
     * @param params An array containing the parameters of the
     * constructor to be invoked.
     * @param signature An array containing the signature of the
     * constructor to be invoked.
     *
     * @return The newly instantiated object.
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to
     * invoke the object's constructor.
     * @exception MBeanException The constructor of the object has
     * thrown an exception
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: The className
     * passed in parameter is null.
     */
    public Object instantiate(String className, Object params[],
                              String signature[])
            throws ReflectionException, MBeanException;

    /**
     * <p>Instantiates an object. The class loader to be used is
     * identified by its object name. If the object name of the loader
     * is null, the ClassLoader that loaded the MBean server will be
     * used.  The object's class should have a public constructor.
     * The call returns a reference to the newly created object.  The
     * newly created object is not registered in the MBean server.</p>
     *
     * @param className The class name of the object to be instantiated.
     * @param loaderName The object name of the class loader to be used.
     * @param params An array containing the parameters of the
     * constructor to be invoked.
     * @param signature An array containing the signature of the
     * constructor to be invoked.
     *
     * @return The newly instantiated object.
     *
     * @exception ReflectionException Wraps a <CODE>java.lang.ClassNotFoundException</CODE> or the <CODE>java.lang.Exception</CODE> that
     * occurred when trying to invoke the object's constructor.
     * @exception MBeanException The constructor of the object has
     * thrown an exception
     * @exception InstanceNotFoundException The specified class loader
     * is not registered in the MBean server.
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: The className
     * passed in parameter is null.
     */
    public Object instantiate(String className, ObjectName loaderName,
                              Object params[], String signature[])
            throws ReflectionException, MBeanException,
                   InstanceNotFoundException;

    /**
     * <p>De-serializes a byte array in the context of the class loader
     * of an MBean.</p>
     *
     * @param name The name of the MBean whose class loader should be
     * used for the de-serialization.
     * @param data The byte array to be de-sererialized.
     *
     * @implSpec This method throws {@link UnsupportedOperationException} by default.
     *
     * @return The de-serialized object stream.
     *
     * @exception InstanceNotFoundException The MBean specified is not
     * found.
     * @exception OperationsException Any of the usual Input/Output
     * related exceptions.
     *
     * @deprecated Use {@link #getClassLoaderFor getClassLoaderFor} to
     * obtain the appropriate class loader for deserialization.
     */
    @Deprecated(since="1.5")
    default public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * <p>De-serializes a byte array in the context of a given MBean
     * class loader.  The class loader is found by loading the class
     * <code>className</code> through the {@link
     * javax.management.loading.ClassLoaderRepository Class Loader
     * Repository}.  The resultant class's class loader is the one to
     * use.
     *
     * @param className The name of the class whose class loader should be
     * used for the de-serialization.
     * @param data The byte array to be de-sererialized.
     *
     * @implSpec This method throws {@link UnsupportedOperationException} by default.
     *
     * @return  The de-serialized object stream.
     *
     * @exception OperationsException Any of the usual Input/Output
     * related exceptions.
     * @exception ReflectionException The specified class could not be
     * loaded by the class loader repository
     *
     * @deprecated Use {@link #getClassLoaderRepository} to obtain the
     * class loader repository and use it to deserialize.
     */
    @Deprecated(since="1.5")
    default public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException {
        throw new UnsupportedOperationException("Not supported.");
    }


    /**
     * <p>De-serializes a byte array in the context of a given MBean
     * class loader.  The class loader is the one that loaded the
     * class with name "className".  The name of the class loader to
     * be used for loading the specified class is specified.  If null,
     * the MBean Server's class loader will be used.</p>
     *
     * @param className The name of the class whose class loader should be
     * used for the de-serialization.
     * @param loaderName The name of the class loader to be used for
     * loading the specified class.  If null, the MBean Server's class
     * loader will be used.
     * @param data The byte array to be de-sererialized.
     *
     * @implSpec This method throws {@link UnsupportedOperationException} by default.
     *
     * @return  The de-serialized object stream.
     *
     * @exception InstanceNotFoundException The specified class loader
     * MBean is not found.
     * @exception OperationsException Any of the usual Input/Output
     * related exceptions.
     * @exception ReflectionException The specified class could not be
     * loaded by the specified class loader.
     *
     * @deprecated Use {@link #getClassLoader getClassLoader} to obtain
     * the class loader for deserialization.
     */
    @Deprecated(since="1.5")
    default public ObjectInputStream deserialize(String className,
                                         ObjectName loaderName,
                                         byte[] data)
            throws InstanceNotFoundException, OperationsException,
                   ReflectionException {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * <p>Return the {@link java.lang.ClassLoader} that was used for
     * loading the class of the named MBean.</p>
     *
     * @param mbeanName The ObjectName of the MBean.
     *
     * @return The ClassLoader used for that MBean.  If <var>l</var>
     * is the MBean's actual ClassLoader, and <var>r</var> is the
     * returned value, then either:
     *
     * <ul>
     * <li><var>r</var> is identical to <var>l</var>; or
     * <li>the result of <var>r</var>{@link
     * ClassLoader#loadClass(String) .loadClass(<var>s</var>)} is the
     * same as <var>l</var>{@link ClassLoader#loadClass(String)
     * .loadClass(<var>s</var>)} for any string <var>s</var>.
     * </ul>
     *
     * What this means is that the ClassLoader may be wrapped in
     * another ClassLoader for security or other reasons.
     *
     * @exception InstanceNotFoundException if the named MBean is not found.
     *
     */
    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
        throws InstanceNotFoundException;

    /**
     * <p>Return the named {@link java.lang.ClassLoader}.</p>
     *
     * @param loaderName The ObjectName of the ClassLoader.  May be
     * null, in which case the MBean server's own ClassLoader is
     * returned.
     *
     * @return The named ClassLoader.  If <var>l</var> is the actual
     * ClassLoader with that name, and <var>r</var> is the returned
     * value, then either:
     *
     * <ul>
     * <li><var>r</var> is identical to <var>l</var>; or
     * <li>the result of <var>r</var>{@link
     * ClassLoader#loadClass(String) .loadClass(<var>s</var>)} is the
     * same as <var>l</var>{@link ClassLoader#loadClass(String)
     * .loadClass(<var>s</var>)} for any string <var>s</var>.
     * </ul>
     *
     * What this means is that the ClassLoader may be wrapped in
     * another ClassLoader for security or other reasons.
     *
     * @exception InstanceNotFoundException if the named ClassLoader is
     *    not found.
     *
     */
    public ClassLoader getClassLoader(ObjectName loaderName)
        throws InstanceNotFoundException;

    /**
     * <p>Return the ClassLoaderRepository for this MBeanServer.
     * @return The ClassLoaderRepository for this MBeanServer.
     *
     */
    public ClassLoaderRepository getClassLoaderRepository();
}
