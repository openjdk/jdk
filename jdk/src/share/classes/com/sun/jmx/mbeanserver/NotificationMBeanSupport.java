/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;

/**
 * <p>A variant of {@code StandardMBeanSupport} where the only
 * methods included are public getters.  This is used by
 * {@code QueryNotificationFilter} to pretend that a Notification is
 * an MBean so it can have a query evaluated on it.  Standard queries
 * never set attributes or invoke methods but custom queries could and
 * we don't want to allow that.  Also we don't want to fail if a
 * Notification happens to have inconsistent types in a pair of getX and
 * setX methods, and we want to include the Object.getClass() method.
 */
public class NotificationMBeanSupport extends StandardMBeanSupport {
    public <T extends Notification> NotificationMBeanSupport(T n)
            throws NotCompliantMBeanException {
        super(n, Util.<Class<T>>cast(n.getClass()));
    }

    @Override
    MBeanIntrospector<Method> getMBeanIntrospector() {
        return introspector;
    }

    private static class Introspector extends StandardMBeanIntrospector {
        @Override
        void checkCompliance(Class<?> mbeanType) {}

        @Override
        List<Method> getMethods(final Class<?> mbeanType)
                throws Exception {
            List<Method> methods = new ArrayList<Method>();
            for (Method m : mbeanType.getMethods()) {
                String name = m.getName();
                Class<?> ret = m.getReturnType();
                if (m.getParameterTypes().length == 0) {
                    if ((name.startsWith("is") && name.length() > 2 &&
                            ret == boolean.class) ||
                        (name.startsWith("get") && name.length() > 3 &&
                            ret != void.class)) {
                        methods.add(m);
                    }
                }
            }
            return methods;
        }

    }
    private static final MBeanIntrospector<Method> introspector =
            new Introspector();
}
