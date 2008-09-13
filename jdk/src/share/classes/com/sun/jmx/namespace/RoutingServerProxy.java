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

package com.sun.jmx.namespace;


import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
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
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;
import javax.management.namespace.JMXNamespaces;

/**
 * A RoutingServerProxy is an MBeanServer proxy that proxies a
 * source name space in a source MBeanServer.
 * It wraps a source MBeanServer, and rewrites routing ObjectNames.
 * It is typically use for implementing 'cd' operations, and
 * will add the source name space to routing ObjectNames at input,
 * and remove it at output.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 *
 * @since 1.7
 */
// See class hierarchy and detailled explanations in RoutingProxy in this
// package.
//
public class RoutingServerProxy
        extends RoutingProxy<MBeanServer>
        implements MBeanServer {

    /**
     * Creates a new instance of RoutingServerProxy
     */
    public RoutingServerProxy(MBeanServer source,
                           String sourceNs) {
        this(source,sourceNs,"",false);
    }

    public RoutingServerProxy(MBeanServer source,
                                String sourceNs,
                                String targetNs,
                                boolean forwardsContext) {
        super(source,sourceNs,targetNs,forwardsContext);
    }

    /**
     * This method is called each time an IOException is raised when
     * trying to forward an operation to the underlying
     * MBeanServerConnection, as a result of calling
     * {@link #getMBeanServerConnection()} or as a result of invoking the
     * operation on the returned connection.
     * Subclasses may redefine this method if they need to perform any
     * specific handling of IOException (logging etc...).
     * @param x The raised IOException.
     * @param method The name of the method in which the exception was
     *        raised. This is one of the methods of the MBeanServer
     *        interface.
     * @return A RuntimeException that should be thrown by the caller.
     *         In this default implementation, this is an
     *         {@link UndeclaredThrowableException} wrapping <var>x</var>.
     **/
    protected RuntimeException handleIOException(IOException x,
                                                 String method) {
        return Util.newRuntimeIOException(x);
    }


    //--------------------------------------------
    //--------------------------------------------
    //
    // Implementation of the MBeanServer interface
    //
    //--------------------------------------------
    //--------------------------------------------
    @Override
    public void addNotificationListener(ObjectName name,
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws InstanceNotFoundException {
        try {
            super.addNotificationListener(name, listener,
                                                 filter, handback);
        } catch (IOException x) {
            throw handleIOException(x,"addNotificationListener");
        }
    }

    @Override
    public void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws InstanceNotFoundException {
        try {
            super.addNotificationListener(name, listener,
                                                 filter, handback);
        } catch (IOException x) {
            throw handleIOException(x,"addNotificationListener");
        }
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name)
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException {
        try {
            return super.createMBean(className, name);
        } catch (IOException x) {
            throw handleIOException(x,"createMBean");
        }
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                                      Object params[], String signature[])
        throws
        ReflectionException,
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        MBeanException,
        NotCompliantMBeanException {
        try {
            return super.createMBean(className, name,
                                     params, signature);
        } catch (IOException x) {
            throw handleIOException(x,"createMBean");
        }
    }

    @Override
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
            return super.createMBean(className, name, loaderName);
        } catch (IOException x) {
            throw handleIOException(x,"createMBean");
        }
    }

    @Override
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
            return super.createMBean(className, name, loaderName,
                                            params, signature);
        } catch (IOException x) {
            throw handleIOException(x,"createMBean");
        }
    }

    /**
     * @deprecated see {@link MBeanServer#deserialize(ObjectName,byte[])
     *                 MBeanServer}
     **/
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
        throws InstanceNotFoundException, OperationsException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().deserialize(sourceName,data);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    /**
     * @deprecated see {@link MBeanServer#deserialize(String,byte[])
     *                 MBeanServer}
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
        throws OperationsException, ReflectionException {
        try {
            return source().deserialize(className,data);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    /**
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
        try {
            return source().deserialize(className,loaderName,data);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
        throws
        MBeanException,
        AttributeNotFoundException,
        InstanceNotFoundException,
        ReflectionException {
        try {
            return super.getAttribute(name, attribute);
        } catch (IOException x) {
            throw handleIOException(x,"getAttribute");
        }
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, ReflectionException {
        try {
            return super.getAttributes(name, attributes);
        } catch (IOException x) {
            throw handleIOException(x,"getAttributes");
        }
    }

    public ClassLoader getClassLoader(ObjectName loaderName)
        throws InstanceNotFoundException {
        final ObjectName sourceName = toSourceOrRuntime(loaderName);
        try {
            return source().getClassLoader(sourceName);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
        throws InstanceNotFoundException {
        final ObjectName sourceName = toSourceOrRuntime(mbeanName);
        try {
            return source().getClassLoaderFor(sourceName);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    public ClassLoaderRepository getClassLoaderRepository() {
        try {
            return source().getClassLoaderRepository();
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    @Override
    public String getDefaultDomain() {
        try {
            return super.getDefaultDomain();
        } catch (IOException x) {
            throw handleIOException(x,"getDefaultDomain");
        }
    }

    @Override
    public String[] getDomains() {
        try {
            return super.getDomains();
        } catch (IOException x) {
            throw handleIOException(x,"getDomains");
        }
    }

    @Override
    public Integer getMBeanCount() {
        try {
            return super.getMBeanCount();
        } catch (IOException x) {
            throw handleIOException(x,"getMBeanCount");
        }
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws
        InstanceNotFoundException,
        IntrospectionException,
        ReflectionException {
        try {
            return super.getMBeanInfo(name);
        } catch (IOException x) {
            throw handleIOException(x,"getMBeanInfo");
        }
    }

    @Override
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException {
        try {
            return super.getObjectInstance(name);
        } catch (IOException x) {
            throw handleIOException(x,"getObjectInstance");
        }
    }

    public Object instantiate(String className)
        throws ReflectionException, MBeanException {
        try {
            return source().instantiate(className);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    public Object instantiate(String className,
                              Object params[],
                              String signature[])
        throws ReflectionException, MBeanException {
        try {
            return source().instantiate(className,
                    params,signature);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    public Object instantiate(String className, ObjectName loaderName)
        throws ReflectionException, MBeanException,
               InstanceNotFoundException {
        final ObjectName srcLoaderName = toSourceOrRuntime(loaderName);
        try {
            return source().instantiate(className,srcLoaderName);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    public Object instantiate(String className, ObjectName loaderName,
                              Object params[], String signature[])
        throws ReflectionException, MBeanException,
               InstanceNotFoundException {
        final ObjectName srcLoaderName = toSourceOrRuntime(loaderName);
        try {
            return source().instantiate(className,srcLoaderName,
                    params,signature);
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    @Override
    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
        throws
        InstanceNotFoundException,
        MBeanException,
        ReflectionException {
        try {
            return super.invoke(name,operationName,params,signature);
        } catch (IOException x) {
            throw handleIOException(x,"invoke");
        }
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException {
        try {
            return super.isInstanceOf(name, className);
        } catch (IOException x) {
            throw handleIOException(x,"isInstanceOf");
        }
    }

    @Override
    public boolean isRegistered(ObjectName name) {
        try {
            return super.isRegistered(name);
        } catch (IOException x) {
            throw handleIOException(x,"isRegistered");
        }
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        try {
            return super.queryMBeans(name, query);
        } catch (IOException x) {
            handleIOException(x,"queryMBeans");
            return Collections.emptySet();
        }
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        try {
            return super.queryNames(name, query);
        } catch (IOException x) {
            handleIOException(x,"queryNames");
            return Collections.emptySet();
        }
    }

    public ObjectInstance registerMBean(Object object, ObjectName name)
        throws
        InstanceAlreadyExistsException,
        MBeanRegistrationException,
        NotCompliantMBeanException {
        final ObjectName sourceName = newSourceMBeanName(name);
        try {
            return processOutputInstance(
                    source().registerMBean(object,sourceName));
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener);
        } catch (IOException x) {
            throw handleIOException(x,"removeNotificationListener");
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener,
                                                    filter, handback);
        } catch (IOException x) {
            throw handleIOException(x,"removeNotificationListener");
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener);
        } catch (IOException x) {
            throw handleIOException(x,"removeNotificationListener");
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
        throws InstanceNotFoundException, ListenerNotFoundException {
        try {
            super.removeNotificationListener(name, listener,
                                                    filter, handback);
        } catch (IOException x) {
            throw handleIOException(x,"removeNotificationListener");
        }
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
        throws
        InstanceNotFoundException,
        AttributeNotFoundException,
        InvalidAttributeValueException,
        MBeanException,
        ReflectionException {
        try {
            super.setAttribute(name, attribute);
        } catch (IOException x) {
            throw handleIOException(x,"setAttribute");
        }
    }

    @Override
    public AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException {
        try {
            return super.setAttributes(name, attributes);
        } catch (IOException x) {
            throw handleIOException(x,"setAttributes");
        }
    }

    @Override
    public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException {
        try {
           super.unregisterMBean(name);
        } catch (IOException x) {
            throw handleIOException(x,"unregisterMBean");
        }
    }

    static final RoutingProxyFactory<MBeanServer,RoutingServerProxy>
        FACTORY = new RoutingProxyFactory<MBeanServer,RoutingServerProxy>() {

        public RoutingServerProxy newInstance(MBeanServer source,
                String sourcePath, String targetPath,
                boolean forwardsContext) {
            return new RoutingServerProxy(source,sourcePath,
                    targetPath,forwardsContext);
        }

        public RoutingServerProxy newInstance(
                MBeanServer source, String sourcePath) {
            return new RoutingServerProxy(source,sourcePath);
        }
    };

    public static MBeanServer cd(MBeanServer source, String sourcePath) {
        return RoutingProxy.cd(RoutingServerProxy.class, FACTORY,
                source, sourcePath);
    }
}
