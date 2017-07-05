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

import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

/**
 * <p>An object of this class implements the MBeanServer interface
 * and, for each of its methods forwards the request to a wrapped
 * {@link MBeanServerConnection} object.
 * Some methods of the {@link MBeanServer} interface do not have
 * any equivalent in {@link MBeanServerConnection}. In that case, an
 * {@link UnsupportedOperationException} will be thrown.
 *
 * <p>A typical use of this class is to apply a {@link QueryExp} object locally,
 * on an MBean that resides in a remote MBeanServer. Since an
 * MBeanServerConnection is not an MBeanServer, it cannot be passed
 * to the <code>setMBeanServer()</code> method of the {@link QueryExp}
 * object. However, this object can.</p>
 *
 * @since 1.7
 */
public class MBeanServerConnectionWrapper
        implements MBeanServer {

    private final MBeanServerConnection wrapped;
    private final ClassLoader defaultCl;

    /**
     * Construct a new object that implements {@link MBeanServer} by
     * forwarding its methods to the given {@link MBeanServerConnection}.
     * This constructor is equivalent to {@link #MBeanServerConnectionWrapper(
     * MBeanServerConnection, ClassLoader) MBeanServerConnectionWrapper(wrapped,
     * null)}.
     *
     * @param wrapped the {@link MBeanServerConnection} to which methods
     * are to be forwarded.  This parameter can be null, in which case the
     * {@code MBeanServerConnection} will typically be supplied by overriding
     * {@link #getMBeanServerConnection}.
     */
    public MBeanServerConnectionWrapper(MBeanServerConnection wrapped) {
        this(wrapped, null);
    }

    /**
     * Construct a new object that implements {@link MBeanServer} by
     * forwarding its methods to the given {@link MBeanServerConnection}.
     * The {@code defaultCl} parameter specifies the value to be returned
     * by {@link #getDefaultClassLoader}.  A null value is equivalent to
     * {@link Thread#getContextClassLoader()}.
     *
     * @param wrapped the {@link MBeanServerConnection} to which methods
     * are to be forwarded.  This parameter can be null, in which case the
     * {@code MBeanServerConnection} will typically be supplied by overriding
     * {@link #getMBeanServerConnection}.
     * @param defaultCl the value to be returned by {@link
     * #getDefaultClassLoader}.  A null value is equivalent to the current
     * thread's {@linkplain Thread#getContextClassLoader()}.
     */
    public MBeanServerConnectionWrapper(MBeanServerConnection wrapped,
            ClassLoader defaultCl) {
        this.wrapped = wrapped;
        this.defaultCl = (defaultCl == null) ?
            Thread.currentThread().getContextClassLoader() : defaultCl;
    }

    /**
     * Returns an MBeanServerConnection. This method is called each time
     * an operation must be invoked on the underlying MBeanServerConnection.
     * The default implementation returns the MBeanServerConnection that
     * was supplied to the constructor of this MBeanServerConnectionWrapper.
     **/
    protected MBeanServerConnection getMBeanServerConnection() {
        return wrapped;
    }

    /**
     * Returns the default class loader passed to the constructor.  If the
     * value passed was null, then the returned value will be the
     * {@linkplain Thread#getContextClassLoader() context class loader} at the
     * time this object was constructed.
     *
     * @return the ClassLoader that was passed to the constructor.
     **/
    public ClassLoader getDefaultClassLoader() {
        return defaultCl;
    }

    /**
     * <p>This method is called each time an IOException is raised when
     * trying to forward an operation to the underlying
     * MBeanServerConnection, as a result of calling
     * {@link #getMBeanServerConnection()} or as a result of invoking the
     * operation on the returned connection.  Since the methods in
     * {@link MBeanServer} are not declared to throw {@code IOException},
     * this method must return a {@code RuntimeException} to be thrown
     * instead.  Typically, the original {@code IOException} will be in the
     * {@linkplain Throwable#getCause() cause chain} of the {@code
     * RuntimeException}.</p>
     *
     * <p>Subclasses may redefine this method if they need to perform any
     * specific handling of IOException (logging etc...).</p>
     *
     * @param x The raised IOException.
     * @param method The name of the method in which the exception was
     *        raised. This is one of the methods of the MBeanServer
     *        interface.
     *
     * @return A RuntimeException that should be thrown by the caller.
     *         In this default implementation, this is a
     *         {@link RuntimeException} wrapping <var>x</var>.
     **/
    protected RuntimeException wrapIOException(IOException x, String method) {
        return Util.newRuntimeIOException(x);
    }

    // Take care of getMBeanServerConnection returning null.
    //
    private synchronized MBeanServerConnection connection()
        throws IOException {
        final MBeanServerConnection c = getMBeanServerConnection();
        if (c == null)
            throw new IOException("MBeanServerConnection unavailable");
        return c;
    }

    //--------------------------------------------
    //--------------------------------------------
    //
    // Implementation of the MBeanServer interface
    //
    //--------------------------------------------
    //--------------------------------------------

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void addNotificationListener(ObjectName name,
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws InstanceNotFoundException {
        try {
            connection().addNotificationListener(name, listener,
                                                 filter, handback);
        } catch (IOException x) {
            throw wrapIOException(x,"addNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws InstanceNotFoundException {
        try {
            connection().addNotificationListener(name, listener,
                                                 filter, handback);
        } catch (IOException x) {
            throw wrapIOException(x,"addNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public ObjectInstance createMBean(String className, ObjectName name)
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException {
        try {
            return connection().createMBean(className, name);
        } catch (IOException x) {
            throw wrapIOException(x,"createMBean");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public ObjectInstance createMBean(String className, ObjectName name,
                                      Object params[], String signature[])
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException {
        try {
            return connection().createMBean(className, name,
                                            params, signature);
        } catch (IOException x) {
            throw wrapIOException(x,"createMBean");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public ObjectInstance createMBean(String className,
                                      ObjectName name,
                                      ObjectName loaderName)
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException,
        InstanceNotFoundException {
        try {
            return connection().createMBean(className, name, loaderName);
        } catch (IOException x) {
            throw wrapIOException(x,"createMBean");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public ObjectInstance createMBean(String className,
                                      ObjectName name,
                                      ObjectName loaderName,
                                      Object params[],
                                      String signature[])
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException,
        InstanceNotFoundException {
        try {
            return connection().createMBean(className, name, loaderName,
                                            params, signature);
        } catch (IOException x) {
            throw wrapIOException(x,"createMBean");
        }
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     * @deprecated see {@link MBeanServer#deserialize(ObjectName,byte[])
     *                 MBeanServer}
     */
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
        throws InstanceNotFoundException, OperationsException {
        throw new UnsupportedOperationException("deserialize");
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     * @deprecated see {@link MBeanServer#deserialize(String,byte[])
     *                 MBeanServer}
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
        throws OperationsException, ReflectionException {
        throw new UnsupportedOperationException("deserialize");
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     * @deprecated see {@link MBeanServer#deserialize(String,ObjectName,byte[])
     *                 MBeanServer}
     */
    @Deprecated
    public ObjectInputStream deserialize(String className,
                                         ObjectName loaderName,
                                         byte[] data)
        throws
        InstanceNotFoundException,
        OperationsException,
        ReflectionException {
        throw new UnsupportedOperationException("deserialize");
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public Object getAttribute(ObjectName name, String attribute)
        throws
        MBeanException,
        AttributeNotFoundException,
        InstanceNotFoundException,
        ReflectionException {
        try {
            return connection().getAttribute(name, attribute);
        } catch (IOException x) {
            throw wrapIOException(x,"getAttribute");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, ReflectionException {
        try {
            return connection().getAttributes(name, attributes);
        } catch (IOException x) {
            throw wrapIOException(x,"getAttributes");
        }
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public ClassLoader getClassLoader(ObjectName loaderName)
        throws InstanceNotFoundException {
        throw new UnsupportedOperationException("getClassLoader");
    }

    /**
     * Returns the {@linkplain #getDefaultClassLoader() default class loader}.
     * This behavior can be changed by subclasses.
     */
    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
        throws InstanceNotFoundException {
        return getDefaultClassLoader();
    }

    /**
     * <p>Returns a {@link ClassLoaderRepository} based on the class loader
     * returned by {@link #getDefaultClassLoader()}.</p>
     * @return a {@link ClassLoaderRepository} that contains a single
     *         class loader, returned by {@link #getDefaultClassLoader()}.
     **/
    public ClassLoaderRepository getClassLoaderRepository() {
        // We return a new ClassLoaderRepository each time this method is
        // called. This is by design, because there's no guarantee that
        // getDefaultClassLoader() will always return the same class loader.
        return Util.getSingleClassLoaderRepository(getDefaultClassLoader());
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public String getDefaultDomain() {
        try {
            return connection().getDefaultDomain();
        } catch (IOException x) {
            throw wrapIOException(x,"getDefaultDomain");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public String[] getDomains() {
        try {
            return connection().getDomains();
        } catch (IOException x) {
            throw wrapIOException(x,"getDomains");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public Integer getMBeanCount() {
        try {
            return connection().getMBeanCount();
        } catch (IOException x) {
            throw wrapIOException(x,"getMBeanCount");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws
        InstanceNotFoundException,
        IntrospectionException,
        ReflectionException {
        try {
            return connection().getMBeanInfo(name);
        } catch (IOException x) {
            throw wrapIOException(x,"getMBeanInfo");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException {
        try {
            return connection().getObjectInstance(name);
        } catch (IOException x) {
            throw wrapIOException(x,"getObjectInstance");
        }
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public Object instantiate(String className)
        throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("instantiate");
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public Object instantiate(String className,
                              Object params[],
                              String signature[])
        throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("instantiate");
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public Object instantiate(String className, ObjectName loaderName)
        throws ReflectionException, MBeanException,
               InstanceNotFoundException {
        throw new UnsupportedOperationException("instantiate");
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public Object instantiate(String className, ObjectName loaderName,
                              Object params[], String signature[])
        throws ReflectionException, MBeanException,
               InstanceNotFoundException {
        throw new UnsupportedOperationException("instantiate");
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
        throws
        InstanceNotFoundException,
        MBeanException,
        ReflectionException {
        try {
            return connection().invoke(name,operationName,params,signature);
        } catch (IOException x) {
            throw wrapIOException(x,"invoke");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException {
        try {
            return connection().isInstanceOf(name, className);
        } catch (IOException x) {
            throw wrapIOException(x,"isInstanceOf");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public boolean isRegistered(ObjectName name) {
        try {
            return connection().isRegistered(name);
        } catch (IOException x) {
            throw wrapIOException(x,"isRegistered");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     * If an IOException is raised, returns an empty Set.
     */
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        try {
            return connection().queryMBeans(name, query);
        } catch (IOException x) {
            throw wrapIOException(x,"queryMBeans");
            //return Collections.emptySet();
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     * If an IOException is raised, returns an empty Set.
     */
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        try {
            return connection().queryNames(name, query);
        } catch (IOException x) {
            throw wrapIOException(x,"queryNames");
            //return Collections.emptySet();
        }
    }

    /**
     * Throws an {@link UnsupportedOperationException}. This behavior can
     * be changed by subclasses.
     */
    public ObjectInstance registerMBean(Object object, ObjectName name)
        throws
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        NotCompliantMBeanException {
        throw new UnsupportedOperationException("registerMBean");
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            connection().removeNotificationListener(name, listener);
        } catch (IOException x) {
            throw wrapIOException(x,"removeNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            connection().removeNotificationListener(name, listener,
                                                    filter, handback);
        } catch (IOException x) {
            throw wrapIOException(x,"removeNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            connection().removeNotificationListener(name, listener);
        } catch (IOException x) {
            throw wrapIOException(x,"removeNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            connection().removeNotificationListener(name, listener,
                                                    filter, handback);
        } catch (IOException x) {
            throw wrapIOException(x,"removeNotificationListener");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void setAttribute(ObjectName name, Attribute attribute)
        throws
        InstanceNotFoundException,
        AttributeNotFoundException,
        InvalidAttributeValueException,
        MBeanException,
        ReflectionException {
        try {
            connection().setAttribute(name, attribute);
        } catch (IOException x) {
            throw wrapIOException(x,"setAttribute");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException {
        try {
            return connection().setAttributes(name, attributes);
        } catch (IOException x) {
            throw wrapIOException(x,"setAttributes");
        }
    }

    /**
     * Forward this method to the
     * wrapped object.
     */
    public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException {
        try {
            connection().unregisterMBean(name);
        } catch (IOException x) {
            throw wrapIOException(x,"unregisterMBean");
        }
    }

    //----------------
    // PRIVATE METHODS
    //----------------

}
