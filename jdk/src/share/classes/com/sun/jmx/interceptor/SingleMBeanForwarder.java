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

package com.sun.jmx.interceptor;

import com.sun.jmx.mbeanserver.Util;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
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
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.MBeanServerSupport;
import javax.management.remote.IdentityMBeanServerForwarder;

/**
 * <p>An {@link MBeanServerForwarder} that simulates the existence of a
 * given MBean.  Requests for that MBean, call it X, are intercepted by the
 * forwarder, and requests for any other MBean are forwarded to the next
 * forwarder in the chain.  Requests such as queryNames which can span both the
 * X and other MBeans are handled by merging the results for X with the results
 * from the next forwarder, unless the "visible" parameter is false, in which
 * case X is invisible to such requests.</p>
 */
public class SingleMBeanForwarder extends IdentityMBeanServerForwarder {

    private final ObjectName mbeanName;
    private final boolean visible;
    private DynamicMBean mbean;

    private MBeanServer mbeanMBS = new MBeanServerSupport() {

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            if (mbeanName.equals(name)) {
                return mbean;
            } else {
                throw new InstanceNotFoundException(name.toString());
            }
        }

        @Override
        protected Set<ObjectName> getNames() {
            return Collections.singleton(mbeanName);
        }

        @Override
        public NotificationEmitter getNotificationEmitterFor(
                ObjectName name) {
            if (mbean instanceof NotificationEmitter)
                return (NotificationEmitter) mbean;
            return null;
        }

        // This will only be called if mbeanName has an empty domain.
        // In that case a getAttribute (e.g.) of that name will have the
        // domain replaced by MBeanServerSupport with the default domain,
        // so we must be sure that the default domain is empty too.
        @Override
        public String getDefaultDomain() {
            return mbeanName.getDomain();
        }
    };

    public SingleMBeanForwarder(
            ObjectName mbeanName, DynamicMBean mbean, boolean visible) {
        this.mbeanName = mbeanName;
        this.visible = visible;
        setSingleMBean(mbean);
    }

    protected void setSingleMBean(DynamicMBean mbean) {
        this.mbean = mbean;
    }

    @Override
    public void addNotificationListener(ObjectName name, ObjectName listener,
                                         NotificationFilter filter,
                                         Object handback)
            throws InstanceNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.addNotificationListener(name, listener, filter, handback);
        else
            super.addNotificationListener(name, listener, filter, handback);
    }

    @Override
    public void addNotificationListener(ObjectName name,
                                         NotificationListener listener,
                                         NotificationFilter filter,
                                         Object handback)
            throws InstanceNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.addNotificationListener(name, listener, filter, handback);
        else
            super.addNotificationListener(name, listener, filter, handback);
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                                       ObjectName loaderName, Object[] params,
                                       String[] signature)
            throws ReflectionException,
                   InstanceAlreadyExistsException,
                   MBeanRegistrationException,
                   MBeanException,
                   NotCompliantMBeanException,
                   InstanceNotFoundException {
        if (mbeanName.equals(name))
            throw new InstanceAlreadyExistsException(mbeanName.toString());
        else
            return super.createMBean(className, name, loaderName, params, signature);
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                                       Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException {
        if (mbeanName.equals(name))
            throw new InstanceAlreadyExistsException(mbeanName.toString());
        return super.createMBean(className, name, params, signature);
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name,
                                       ObjectName loaderName)
            throws ReflectionException,
                   InstanceAlreadyExistsException,
                   MBeanRegistrationException,
                   MBeanException,
                   NotCompliantMBeanException,
                   InstanceNotFoundException {
        if (mbeanName.equals(name))
            throw new InstanceAlreadyExistsException(mbeanName.toString());
        return super.createMBean(className, name, loaderName);
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name)
            throws ReflectionException,
                   InstanceAlreadyExistsException,
                   MBeanRegistrationException,
                   MBeanException,
                   NotCompliantMBeanException {
        if (mbeanName.equals(name))
            throw new InstanceAlreadyExistsException(mbeanName.toString());
        return super.createMBean(className, name);
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException,
                   AttributeNotFoundException,
                   InstanceNotFoundException,
                   ReflectionException {
        if (mbeanName.equals(name))
            return mbeanMBS.getAttribute(name, attribute);
        else
            return super.getAttribute(name, attribute);
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException {
        if (mbeanName.equals(name))
            return mbeanMBS.getAttributes(name, attributes);
        else
            return super.getAttributes(name, attributes);
    }

    @Override
    public ClassLoader getClassLoader(ObjectName loaderName)
            throws InstanceNotFoundException {
        if (mbeanName.equals(loaderName))
            return mbeanMBS.getClassLoader(loaderName);
        else
            return super.getClassLoader(loaderName);
    }

    @Override
    public ClassLoader getClassLoaderFor(ObjectName name)
            throws InstanceNotFoundException {
        if (mbeanName.equals(name))
            return mbeanMBS.getClassLoaderFor(name);
        else
            return super.getClassLoaderFor(name);
    }

    @Override
    public String[] getDomains() {
        String[] domains = super.getDomains();
        if (!visible)
            return domains;
        TreeSet<String> domainSet = new TreeSet<String>(Arrays.asList(domains));
        domainSet.add(mbeanName.getDomain());
        return domainSet.toArray(new String[domainSet.size()]);
    }

    @Override
    public Integer getMBeanCount() {
        Integer count = super.getMBeanCount();
        if (visible && !super.isRegistered(mbeanName))
            count++;
        return count;
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException,
                   IntrospectionException,
                   ReflectionException {
        if (mbeanName.equals(name))
            return mbeanMBS.getMBeanInfo(name);
        else
            return super.getMBeanInfo(name);
    }

    @Override
    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException {
        if (mbeanName.equals(name))
            return mbeanMBS.getObjectInstance(name);
        else
            return super.getObjectInstance(name);
    }

    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params,
                          String[] signature)
            throws InstanceNotFoundException,
                   MBeanException,
                   ReflectionException {
        if (mbeanName.equals(name))
            return mbeanMBS.invoke(name, operationName, params, signature);
        else
            return super.invoke(name, operationName, params, signature);
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException {
        if (mbeanName.equals(name))
            return mbeanMBS.isInstanceOf(name, className);
        else
            return super.isInstanceOf(name, className);
    }

    @Override
    public boolean isRegistered(ObjectName name) {
        if (mbeanName.equals(name))
            return true;
        else
            return super.isRegistered(name);
    }

    /**
     * This is a ugly hack. Although jmx.context//*:* matches jmx.context//:*
     * queryNames(jmx.context//*:*,null) must not return jmx.context//:*
     * @param  pattern the pattern to match against. must not be null.
     * @return true if mbeanName can be included, false if it must not.
     */
    private boolean applies(ObjectName pattern) {
        // we know pattern is not null.
        if (!visible || !pattern.apply(mbeanName))
            return false;

        final String dompat = pattern.getDomain();
        if (!dompat.contains(JMXNamespaces.NAMESPACE_SEPARATOR))
            return true; // We already checked that patterns apply.

        if (mbeanName.getDomain().endsWith(JMXNamespaces.NAMESPACE_SEPARATOR)) {
            // only matches if pattern ends with //
            return dompat.endsWith(JMXNamespaces.NAMESPACE_SEPARATOR);
        }

        // should not come here, unless mbeanName contains a // in the
        // middle of its domain, which would be weird.
        // let query on mbeanMBS proceed and take care of that.
        //
        return true;
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        Set<ObjectInstance> names = super.queryMBeans(name, query);
        if (visible) {
            if (name == null || applies(name) ) {
                // Don't assume mbs.queryNames returns a writable set.
                names = Util.cloneSet(names);
                names.addAll(mbeanMBS.queryMBeans(name, query));
            }
        }
        return names;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        Set<ObjectName> names = super.queryNames(name, query);
        if (visible) {
            if (name == null || applies(name)) {
                // Don't assume mbs.queryNames returns a writable set.
                names = Util.cloneSet(names);
                names.addAll(mbeanMBS.queryNames(name, query));
            }
        }
        return names;
    }


    @Override
    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException,
                   MBeanRegistrationException,
                   NotCompliantMBeanException {
        if (mbeanName.equals(name))
            throw new InstanceAlreadyExistsException(mbeanName.toString());
        else
            return super.registerMBean(object, name);
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                            NotificationListener listener,
                                            NotificationFilter filter,
                                            Object handback)
            throws InstanceNotFoundException,
                   ListenerNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.removeNotificationListener(name, listener, filter, handback);
        else
            super.removeNotificationListener(name, listener, filter, handback);
    }

    @Override
    public void removeNotificationListener(ObjectName name,
                                            NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.removeNotificationListener(name, listener);
        else
            super.removeNotificationListener(name, listener);
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener,
                                            NotificationFilter filter,
                                            Object handback)
            throws InstanceNotFoundException,
                   ListenerNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.removeNotificationListener(name, listener, filter, handback);
        else
            super.removeNotificationListener(name, listener, filter, handback);
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        if (mbeanName.equals(name))
            mbeanMBS.removeNotificationListener(name, listener);
        else
            super.removeNotificationListener(name, listener);
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException,
                   AttributeNotFoundException,
                   InvalidAttributeValueException,
                   MBeanException,
                   ReflectionException {
        if (mbeanName.equals(name))
            mbeanMBS.setAttribute(name, attribute);
        else
            super.setAttribute(name, attribute);
    }

    @Override
    public AttributeList setAttributes(ObjectName name,
                                        AttributeList attributes)
            throws InstanceNotFoundException, ReflectionException {
        if (mbeanName.equals(name))
            return mbeanMBS.setAttributes(name, attributes);
        else
            return super.setAttributes(name, attributes);
    }

    @Override
    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException,
                   MBeanRegistrationException {
        if (mbeanName.equals(name))
            mbeanMBS.unregisterMBean(name);
        else
            super.unregisterMBean(name);
    }
}
