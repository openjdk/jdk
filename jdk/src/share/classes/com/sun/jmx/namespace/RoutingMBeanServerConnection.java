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

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMRuntimeException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;

/**
 * A RoutingMBeanServerConnection wraps a MBeanServerConnection, defining
 * abstract methods that can be implemented by subclasses to rewrite
 * routing ObjectNames. It is used to implement
 * HandlerInterceptors (wrapping JMXNamespace instances) and routing
 * proxies (used to implement cd operations).
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public abstract class RoutingMBeanServerConnection<T extends MBeanServerConnection>
        implements MBeanServerConnection {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    /**
     * Creates a new instance of RoutingMBeanServerConnection
     */
    public RoutingMBeanServerConnection() {
    }

    /**
     * Returns the wrapped source connection. The {@code source} connection
     * is a connection to the MBeanServer that contains the actual MBean.
     * In the case of cascading, that would be a connection to the sub
     * agent.
     **/
    protected abstract T source() throws IOException;

    /**
     * Converts a target ObjectName to a source ObjectName.
     * The target ObjectName is the name of the MBean in the mount point
     * target. In the case of cascading, that would be the name of the
     * MBean in the master agent. So if a subagent S containing an MBean
     * named "X" is mounted in the target namespace "foo//" of a master agent M,
     * the source is S, the target is "foo//" in M, the source name is "X", and
     * the target name is "foo//X".
     * In the case of cascading - such as in NamespaceInterceptor, this method
     * will convert "foo//X" (the targetName) into "X", the source name.
     **/
    protected abstract ObjectName toSource(ObjectName targetName)
        throws MalformedObjectNameException;

    /**
     * Converts a source ObjectName to a target ObjectName.
     * (see description of toSource above for explanations)
     * In the case of cascading - such as in NamespaceInterceptor, this method
     * will convert "X" (the sourceName) into "foo//X", the target name.
     **/
    protected abstract ObjectName toTarget(ObjectName sourceName)
        throws MalformedObjectNameException;

    /**
     * Can be overridden by subclasses to check the validity of a new
     * ObjectName used in createMBean or registerMBean.
     * This method is typically used by subclasses which might require
     * special handling for "null";
     **/
    protected ObjectName newSourceMBeanName(ObjectName targetName)
        throws MBeanRegistrationException {
        try {
            return toSource(targetName);
        } catch (Exception x) {
            throw new MBeanRegistrationException(x,"Illegal MBean Name");
        }
    }

    // Calls toSource(), Wraps MalformedObjectNameException.
    ObjectName toSourceOrRuntime(ObjectName targetName)
        throws RuntimeOperationsException {
        try {
            return toSource(targetName);
        } catch (MalformedObjectNameException x) {
            final IllegalArgumentException x2 =
                    new IllegalArgumentException(String.valueOf(targetName),x);
            final RuntimeOperationsException x3 =
                    new RuntimeOperationsException(x2);
            throw x3;
        }
    }


    // Wraps given exception if needed.
    RuntimeException makeCompliantRuntimeException(Exception x) {
        if (x instanceof SecurityException)  return (SecurityException)x;
        if (x instanceof JMRuntimeException) return (JMRuntimeException)x;
        if (x instanceof RuntimeException)
            return new RuntimeOperationsException((RuntimeException)x);
        if (x instanceof IOException)
            return Util.newRuntimeIOException((IOException)x);
        // shouldn't come here...
        final RuntimeException x2 = new UndeclaredThrowableException(x);
        return new RuntimeOperationsException(x2);
    }

    // from MBeanServerConnection
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().getAttributes(sourceName, attributes);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public Object invoke(ObjectName name, String operationName, Object[] params,
                         String[] signature)
        throws InstanceNotFoundException, MBeanException, ReflectionException,
            IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            final Object result =
                    source().invoke(sourceName,operationName,params,
                                   signature);
            return result;
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException, MBeanRegistrationException,
            IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().unregisterMBean(sourceName);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().getMBeanInfo(sourceName);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return processOutputInstance(
                    source().getObjectInstance(sourceName));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public boolean isRegistered(ObjectName name) throws IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().isRegistered(sourceName);
        } catch (RuntimeMBeanException x) {
            throw new RuntimeOperationsException(x.getTargetException());
        } catch (RuntimeException x) {
            throw makeCompliantRuntimeException(x);
        }
    }

    // from MBeanServerConnection
    public void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException, AttributeNotFoundException,
            InvalidAttributeValueException, MBeanException,
            ReflectionException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().setAttribute(sourceName,attribute);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public ObjectInstance createMBean(String className,
            ObjectName name, ObjectName loaderName,
            Object[] params, String[] signature)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException, IOException {
        final ObjectName sourceName = newSourceMBeanName(name);
        // Loader Name is already a sourceLoaderName.
        final ObjectName sourceLoaderName = loaderName;
        try {
            final ObjectInstance instance =
                    source().createMBean(className,sourceName,
                                         sourceLoaderName,
                                         params,signature);
            return processOutputInstance(instance);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public ObjectInstance createMBean(String className, ObjectName name,
            Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, IOException {
        final ObjectName sourceName = newSourceMBeanName(name);
        try {
            return processOutputInstance(source().createMBean(className,
                    sourceName,params,signature));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public ObjectInstance createMBean(String className, ObjectName name,
            ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException, IOException {
        final ObjectName sourceName = newSourceMBeanName(name);
        // Loader Name is already a source Loader Name.
        final ObjectName sourceLoaderName = loaderName;
        try {
            return processOutputInstance(source().createMBean(className,
                    sourceName,sourceLoaderName));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public ObjectInstance createMBean(String className, ObjectName name)
        throws ReflectionException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException,
            NotCompliantMBeanException, IOException {
        final ObjectName sourceName = newSourceMBeanName(name);
        try {
            return processOutputInstance(source().
                    createMBean(className,sourceName));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
     }

    // from MBeanServerConnection
    public Object getAttribute(ObjectName name, String attribute)
        throws MBeanException, AttributeNotFoundException,
            InstanceNotFoundException, ReflectionException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().getAttribute(sourceName,attribute);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public boolean isInstanceOf(ObjectName name, String className)
        throws InstanceNotFoundException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().isInstanceOf(sourceName,className);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return source().
                    setAttributes(sourceName,attributes);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // Return names in the target's context.
    Set<ObjectInstance> processOutputInstances(Set<ObjectInstance> sources) {

        final Set<ObjectInstance> result = Util.equivalentEmptySet(sources);
        for (ObjectInstance i : sources) {
            try {
                final ObjectInstance target = processOutputInstance(i);
                if (excludesFromResult(target.getObjectName(), "queryMBeans"))
                    continue;
                result.add(target);
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Skiping returned item: " +
                             "Unexpected exception while processing " +
                             "ObjectInstance: " + x);
                }
                continue;
            }
        }
        return result;
    }


    // Return names in the target's context.
    ObjectInstance processOutputInstance(ObjectInstance source) {
        if (source == null) return null;
        final ObjectName sourceName = source.getObjectName();
        try {
            final ObjectName targetName = toTarget(sourceName);
            return new ObjectInstance(targetName,source.getClassName());
        } catch (MalformedObjectNameException x) {
            final IllegalArgumentException x2 =
                    new IllegalArgumentException(String.valueOf(sourceName),x);
            final RuntimeOperationsException x3 =
                    new RuntimeOperationsException(x2);
            throw x3;
        }
    }

    // Returns names in the target's context.
    Set<ObjectName> processOutputNames(Set<ObjectName> sourceNames) {

        final Set<ObjectName> names = Util.equivalentEmptySet(sourceNames);
        for (ObjectName n : sourceNames) {
            try {
                final ObjectName targetName = toTarget(n);
                if (excludesFromResult(targetName, "queryNames")) continue;
                names.add(targetName);
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Skiping returned item: " +
                             "Unexpected exception while processing " +
                             "ObjectInstance: " + x);
                }
                continue;
            }
        }
        return names;
    }

    // from MBeanServerConnection
    public Set<ObjectInstance> queryMBeans(ObjectName name,
            QueryExp query) throws IOException {
        if (name == null) name=ObjectName.WILDCARD;
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            return processOutputInstances(
                    source().queryMBeans(sourceName,query));
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection

    public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
        throws IOException {
        if (name == null) name=ObjectName.WILDCARD;
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            final Set<ObjectName> tmp = source().queryNames(sourceName,query);
            final Set<ObjectName> out = processOutputNames(tmp);
            //System.err.println("queryNames: out: "+out);
            return out;
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener)
        throws InstanceNotFoundException,
        ListenerNotFoundException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().removeNotificationListener(sourceName,listener);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void addNotificationListener(ObjectName name, ObjectName listener,
            NotificationFilter filter, Object handback)
            throws InstanceNotFoundException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        // Listener name is already a source listener name.
        try {
            source().addNotificationListener(sourceName,listener,
                    filter,handback);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void addNotificationListener(ObjectName name,
                NotificationListener listener, NotificationFilter filter,
                Object handback) throws InstanceNotFoundException, IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().addNotificationListener(sourceName, listener, filter,
                    handback);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }


    // from MBeanServerConnection
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener, NotificationFilter filter,
            Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
                IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().removeNotificationListener(sourceName,listener,filter,
                    handback);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void removeNotificationListener(ObjectName name, ObjectName listener,
            NotificationFilter filter, Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
            IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        try {
            source().removeNotificationListener(sourceName,listener,
                    filter,handback);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public void removeNotificationListener(ObjectName name, ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException,
               IOException {
        final ObjectName sourceName = toSourceOrRuntime(name);
        // listener name is already a source name...
        final ObjectName sourceListener = listener;
        try {
            source().removeNotificationListener(sourceName,sourceListener);
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public Integer getMBeanCount() throws IOException {
        try {
            return source().getMBeanCount();
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public String[] getDomains() throws IOException {
        try {
            return source().getDomains();
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // from MBeanServerConnection
    public String getDefaultDomain() throws IOException {
        try {
            return source().getDefaultDomain();
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    /**
     * Returns true if the given targetName must be excluded from the
     * query result.
     * In this base class, always return {@code false}.
     * By default all object names returned by the sources are
     * transmitted to the caller - there is no filtering.
     *
     * @param name         A target object name expressed in the caller's
     *                     context. In the case of cascading, where the source
     *                     is a sub agent mounted on e.g. namespace "foo",
     *                     that would be a name prefixed by "foo//"...
     * @param queryMethod  either "queryNames" or "queryMBeans".
     * @return true if the name must be excluded.
     */
    boolean excludesFromResult(ObjectName targetName, String queryMethod) {
        return false;
    }

}
