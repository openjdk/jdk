/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.event;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.management.MBeanServerConnection;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventConsumer;
import javax.management.event.NotificationManager;

/**
 * Override the methods related to the notification to use the
 * Event service.
 */
public interface EventConnection extends MBeanServerConnection, EventConsumer {
    public EventClient getEventClient();

    public static class Factory {
        public static EventConnection make(
                final MBeanServerConnection mbsc,
                final EventClient eventClient)
                throws IOException {
            if (!mbsc.isRegistered(EventClientDelegate.OBJECT_NAME)) {
                throw new IOException(
                        "The server does not support the event service.");
            }
            InvocationHandler ih = new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args)
                        throws Throwable {
                    Class<?> intf = method.getDeclaringClass();
                    try {
                        if (intf.isInstance(eventClient))
                            return method.invoke(eventClient, args);
                        else
                            return method.invoke(mbsc, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            };
            // It is important to declare NotificationManager.class first
            // in the array below, so that the relevant addNL and removeNL
            // methods will show up with method.getDeclaringClass() as
            // being from that interface and not MBeanServerConnection.
            return (EventConnection) Proxy.newProxyInstance(
                    NotificationManager.class.getClassLoader(),
                    new Class<?>[] {
                        NotificationManager.class, EventConnection.class,
                    },
                    ih);
        }
    }
}
