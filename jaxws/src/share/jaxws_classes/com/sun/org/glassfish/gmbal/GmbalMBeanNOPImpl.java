/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.gmbal;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ReflectionException;

/** A simple no-op implementation of GmbalMBean for use in the no-op impl of
 * ManagedObjectManager.
 *
 * @author ken
 */
public class GmbalMBeanNOPImpl implements GmbalMBean {
    public Object getAttribute(String attribute)
        throws AttributeNotFoundException, MBeanException, ReflectionException {

        return null ;
    }

    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, InvalidAttributeValueException,
            MBeanException, ReflectionException {

        // NO-OP
    }

    public AttributeList getAttributes(String[] attributes) {
        return null ;
    }

    public AttributeList setAttributes(AttributeList attributes) {
        return null ;
    }

    public Object invoke(String actionName, Object[] params, String[] signature)
        throws MBeanException, ReflectionException {

        return null ;
    }

    public MBeanInfo getMBeanInfo() {
        return null ;
    }

    public void removeNotificationListener(NotificationListener listener,
        NotificationFilter filter, Object handback)
        throws ListenerNotFoundException {

        // NO-OP
    }

    public void addNotificationListener(NotificationListener listener,
        NotificationFilter filter, Object handback) throws IllegalArgumentException {

        // NO-OP
    }

    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        // NO-OP
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0] ;
    }

}
