/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.mbeanserver;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.DynamicWrapperMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * Create wrappers for DynamicMBean that implement NotificationEmitter
 * and SendNotification.
 */
public class NotifySupport
        implements DynamicMBean2, NotificationEmitter, MBeanRegistration {

    private final DynamicMBean mbean;
    private final NotificationBroadcasterSupport nbs;

    public static DynamicMBean wrap(
            DynamicMBean mbean, NotificationBroadcasterSupport nbs) {
        return new NotifySupport(mbean, nbs);
    }

    private NotifySupport(DynamicMBean mbean, NotificationBroadcasterSupport nbs) {
        this.mbean = mbean;
        this.nbs = nbs;
    }

    public static NotificationBroadcasterSupport getNB(DynamicMBean mbean) {
        if (mbean instanceof NotifySupport)
            return ((NotifySupport) mbean).nbs;
        else
            return null;
    }

    public String getClassName() {
        if (mbean instanceof DynamicMBean2)
            return ((DynamicMBean2) mbean).getClassName();
        Object w = mbean;
        if (w instanceof DynamicWrapperMBean)
            w = ((DynamicWrapperMBean) w).getWrappedObject();
        return w.getClass().getName();
    }

    public void preRegister2(MBeanServer mbs, ObjectName name) throws Exception {
        if (mbean instanceof DynamicMBean2)
            ((DynamicMBean2) mbean).preRegister2(mbs, name);
    }

    public void registerFailed() {
        if (mbean instanceof DynamicMBean2)
            ((DynamicMBean2) mbean).registerFailed();
    }

    public Object getWrappedObject() {
        if (mbean instanceof DynamicWrapperMBean)
            return ((DynamicWrapperMBean) mbean).getWrappedObject();
        else
            return mbean;
    }

    public ClassLoader getWrappedClassLoader() {
        if (mbean instanceof DynamicWrapperMBean)
            return ((DynamicWrapperMBean) mbean).getWrappedClassLoader();
        else
            return mbean.getClass().getClassLoader();
    }

    public Object getAttribute(String attribute) throws AttributeNotFoundException,
                                                        MBeanException,
                                                        ReflectionException {
        return mbean.getAttribute(attribute);
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException,
                                                         InvalidAttributeValueException,
                                                         MBeanException,
                                                         ReflectionException {
        mbean.setAttribute(attribute);
    }

    public AttributeList setAttributes(AttributeList attributes) {
        return mbean.setAttributes(attributes);
    }

    public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException {
        return mbean.invoke(actionName, params, signature);
    }

    public MBeanInfo getMBeanInfo() {
        return mbean.getMBeanInfo();
    }

    public AttributeList getAttributes(String[] attributes) {
        return mbean.getAttributes(attributes);
    }

    public void removeNotificationListener(NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback) throws ListenerNotFoundException {
        nbs.removeNotificationListener(listener, filter, handback);
    }

    public void removeNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {
        nbs.removeNotificationListener(listener);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return nbs.getNotificationInfo();
    }

    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback) {
        nbs.addNotificationListener(listener, filter, handback);
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        if (mbr() != null)
            return mbr().preRegister(server, name);
        else
            return name;
    }

    public void postRegister(Boolean registrationDone) {
        if (mbr() != null)
            mbr().postRegister(registrationDone);
    }

    public void preDeregister() throws Exception {
        if (mbr() != null)
            mbr().preDeregister();
    }

    public void postDeregister() {
        if (mbr() != null)
            mbr().postDeregister();
    }

    private MBeanRegistration mbr() {
        if (mbean instanceof MBeanRegistration)
            return (MBeanRegistration) mbean;
        else
            return null;
    }
}
