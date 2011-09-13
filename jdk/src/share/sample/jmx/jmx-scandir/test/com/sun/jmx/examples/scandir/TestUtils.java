/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.jmx.examples.scandir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;

/**
 * A utility class defining static methods used by our tests.
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class TestUtils {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(TestUtils.class.getName());

    /** Creates a new instance of TestUtils */
    private TestUtils() {
    }

    /**
     * Returns the ObjectName of the MBean that a proxy object
     * is proxying.
     **/
    public static ObjectName getObjectName(Object proxy) {
        if (!(proxy instanceof Proxy))
            throw new IllegalArgumentException("not a "+Proxy.class.getName());
        final Proxy p = (Proxy) proxy;
        final InvocationHandler handler =
                Proxy.getInvocationHandler(proxy);
        if (handler instanceof MBeanServerInvocationHandler)
            return ((MBeanServerInvocationHandler)handler).getObjectName();
        throw new IllegalArgumentException("not a JMX Proxy");
    }

    /**
     * Transfroms a proxy implementing T in a proxy implementing T plus
     * NotificationEmitter
     *
     **/
    public static <T> T makeNotificationEmitter(T proxy,
                        Class<T> mbeanInterface) {
        if (proxy instanceof NotificationEmitter)
            return proxy;
        if (proxy == null) return null;
        if (!(proxy instanceof Proxy))
            throw new IllegalArgumentException("not a "+Proxy.class.getName());
        final Proxy p = (Proxy) proxy;
        final InvocationHandler handler =
                Proxy.getInvocationHandler(proxy);
        if (!(handler instanceof MBeanServerInvocationHandler))
            throw new IllegalArgumentException("not a JMX Proxy");
        final MBeanServerInvocationHandler h =
                (MBeanServerInvocationHandler)handler;
        final ObjectName name = h.getObjectName();
        final MBeanServerConnection mbs = h.getMBeanServerConnection();
        final boolean isMXBean = h.isMXBean();
        final T newProxy;
        if (isMXBean)
            newProxy = JMX.newMXBeanProxy(mbs,name,mbeanInterface,true);
        else
            newProxy = JMX.newMBeanProxy(mbs,name,mbeanInterface,true);
        return newProxy;
    }

}
