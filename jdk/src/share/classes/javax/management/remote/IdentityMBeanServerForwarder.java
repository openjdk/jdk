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
package javax.management.remote;

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
 * An {@link MBeanServerForwarder} that forwards all {@link MBeanServer}
 * operations unchanged to the next {@code MBeanServer} in the chain.
 * This class is typically subclassed to override some but not all methods.
 */
public class IdentityMBeanServerForwarder implements MBeanServerForwarder {

    private MBeanServer next;

    /**
     * <p>Construct a forwarder that has no next {@code MBeanServer}.
     * The resulting object will be unusable until {@link #setMBeanServer
     * setMBeanServer} is called to establish the next item in the chain.</p>
     */
    public IdentityMBeanServerForwarder() {
    }

    /**
     * <p>Construct a forwarder that forwards to the given {@code MBeanServer}.
     * It is not an error for {@code next} to be null, but the resulting object
     * will be unusable until {@link #setMBeanServer setMBeanServer} is called
     * to establish the next item in the chain.</p>
     */
    public IdentityMBeanServerForwarder(MBeanServer next) {
        this.next = next;
    }

    public synchronized MBeanServer getMBeanServer() {
        return next;
    }

    public synchronized void setMBeanServer(MBeanServer mbs) {
        next = mbs;
    }

    private synchronized MBeanServer next() {
        return next;
    }

    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException {
        next().unregisterMBean(name);
    }

    public AttributeList setAttributes(ObjectName name,
                                        AttributeList attributes)
            throws InstanceNotFoundException, ReflectionException {
        return next().setAttributes(name, attributes);
    }

    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException,
                   InvalidAttributeValueException, MBeanException,
                   ReflectionException {
        next().setAttribute(name, attribute);
    }

    public void removeNotificationListener(ObjectName name,
                                            NotificationListener listener,
                                            NotificationFilter filter,
                                            Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        next().removeNotificationListener(name, listener, filter, handback);
    }

    public void removeNotificationListener(ObjectName name,
                                            NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        next().removeNotificationListener(name, listener);
    }

    public void removeNotificationListener(ObjectName name, ObjectName listener,
                                            NotificationFilter filter,
                                            Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        next().removeNotificationListener(name, listener, filter, handback);
    }

    public void removeNotificationListener(ObjectName name, ObjectName listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        next().removeNotificationListener(name, listener);
    }

    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException,
                   NotCompliantMBeanException {
        return next().registerMBean(object, name);
    }

    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        return next().queryNames(name, query);
    }

    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        return next().queryMBeans(name, query);
    }

    public boolean isRegistered(ObjectName name) {
        return next().isRegistered(name);
    }

    public boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException {
        return next().isInstanceOf(name, className);
    }

    public Object invoke(ObjectName name, String operationName, Object[] params,
                          String[] signature)
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException {
        return next().invoke(name, operationName, params, signature);
    }

    public Object instantiate(String className, ObjectName loaderName,
                               Object[] params, String[] signature)
            throws ReflectionException, MBeanException,
                   InstanceNotFoundException {
        return next().instantiate(className, loaderName, params, signature);
    }

    public Object instantiate(String className, Object[] params,
                               String[] signature)
            throws ReflectionException, MBeanException {
        return next().instantiate(className, params, signature);
    }

    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
                   InstanceNotFoundException {
        return next().instantiate(className, loaderName);
    }

    public Object instantiate(String className)
            throws ReflectionException, MBeanException {
        return next().instantiate(className);
    }

    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException {
        return next().getObjectInstance(name);
    }

    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException,
                   ReflectionException {
        return next().getMBeanInfo(name);
    }

    public Integer getMBeanCount() {
        return next().getMBeanCount();
    }

    public String[] getDomains() {
        return next().getDomains();
    }

    public String getDefaultDomain() {
        return next().getDefaultDomain();
    }

    public ClassLoaderRepository getClassLoaderRepository() {
        return next().getClassLoaderRepository();
    }

    public ClassLoader getClassLoaderFor(ObjectName mbeanName)
            throws InstanceNotFoundException {
        return next().getClassLoaderFor(mbeanName);
    }

    public ClassLoader getClassLoader(ObjectName loaderName)
            throws InstanceNotFoundException {
        return next().getClassLoader(loaderName);
    }

    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException {
        return next().getAttributes(name, attributes);
    }

    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException {
        return next().getAttribute(name, attribute);
    }

    @Deprecated
    public ObjectInputStream deserialize(String className,
                                          ObjectName loaderName,
                                          byte[] data)
            throws InstanceNotFoundException, OperationsException,
                   ReflectionException {
        return next().deserialize(className, loaderName, data);
    }

    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException {
        return next().deserialize(className, data);
    }

    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException {
        return next().deserialize(name, data);
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                       ObjectName loaderName, Object[] params,
                                       String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException {
        return next().createMBean(className, name, loaderName, params, signature);
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                       Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException {
        return next().createMBean(className, name, params, signature);
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                       ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException {
        return next().createMBean(className, name, loaderName);
    }

    public ObjectInstance createMBean(String className, ObjectName name)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException {
        return next().createMBean(className, name);
    }

    public void addNotificationListener(ObjectName name, ObjectName listener,
                                         NotificationFilter filter,
                                         Object handback)
            throws InstanceNotFoundException {
        next().addNotificationListener(name, listener, filter, handback);
    }

    public void addNotificationListener(ObjectName name,
                                         NotificationListener listener,
                                         NotificationFilter filter,
                                         Object handback)
            throws InstanceNotFoundException {
        next().addNotificationListener(name, listener, filter, handback);
    }
}
