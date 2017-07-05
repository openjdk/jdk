/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
/*
 * @test MXBeanRefTest.java
 * @bug 5072476
 * @summary Test that MXBean proxy references work correctly in the presence
 * of namespaces.
 * @author Eamonn Mcmanus
 */

/**
 * The idea is that we will create a hierarchy like this:
 * a//
 *   X
 *   b//
 *     Y
 *     Z
 * and we will use MXBean references so we have links like this:
 * a//
 *   X----+
 *   b//  |
 *       /
 *     Y
 *      \
 *      /
 *     Z
 * In other words, X.getY() will return a proxy for Y, which the MXBean
 * framework will map to b//Y.  A proxy for a//X should then map this
 * into a proxy for a//b//Y.  That's easy.  But then if we call getZ()
 * on this proxy, the MXBean framework will return just Z, and the proxy
 * must map that into a proxy for a//b//Z.
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.openmbean.OpenDataException;

public class MXBeanRefTest {

    public static interface ZMXBean {
        public void success();
    }
    public static class ZImpl implements ZMXBean {
        public void success() {}
    }

    public static interface YMXBean {
        public ZMXBean getZ();
        public void setZ(ZMXBean z);
    }
    public static class YImpl implements YMXBean {
        private ZMXBean z;

        public YImpl(ZMXBean z) {
            this.z = z;
        }

        public ZMXBean getZ() {
            return z;
        }

        public void setZ(ZMXBean z) {
            this.z = z;
        }
    }

    public static interface XMXBean {
        public YMXBean getY();
    }
    public static class XImpl implements XMXBean {
        private final YMXBean yProxy;

        public XImpl(YMXBean yProxy) {
            this.yProxy = yProxy;
        }

        public YMXBean getY() {
            return yProxy;
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        // Set up namespace hierarchy a//b//
        MBeanServer ambs = MBeanServerFactory.newMBeanServer();
        MBeanServer bmbs = MBeanServerFactory.newMBeanServer();
        JMXNamespace bHandler = new JMXNamespace(bmbs);
        ObjectName bHandlerName = JMXNamespaces.getNamespaceObjectName("b");
        System.out.println(bHandlerName);
        ambs.registerMBean(bHandler, bHandlerName);
        JMXNamespace aHandler = new JMXNamespace(ambs);
        ObjectName aHandlerName = JMXNamespaces.getNamespaceObjectName("a");
        mbs.registerMBean(aHandler, aHandlerName);

        ZMXBean z = new ZImpl();
        ObjectName zName = new ObjectName("foo:type=Z");
        bmbs.registerMBean(z, zName);

        YMXBean y = new YImpl(z);
        ObjectName yName = new ObjectName("foo:type=Y");
        bmbs.registerMBean(y, yName);

        ObjectName yNameInA = new ObjectName("b//" + yName);
        System.out.println("MBeanInfo for Y as seen from a//:");
        System.out.println(ambs.getMBeanInfo(yNameInA));
        YMXBean yProxyInA = JMX.newMXBeanProxy(ambs, yNameInA, YMXBean.class);
        XMXBean x = new XImpl(yProxyInA);
        ObjectName xName = new ObjectName("foo:type=X");
        ambs.registerMBean(x, xName);

        ObjectName xNameFromTop = new ObjectName("a//" + xName);
        XMXBean xProxy = JMX.newMXBeanProxy(mbs, xNameFromTop, XMXBean.class);
        System.out.println("Name of X Proxy: " + proxyName(xProxy));
        YMXBean yProxy = xProxy.getY();
        System.out.println("Name of Y Proxy: " + proxyName(yProxy));
        ZMXBean zProxy = yProxy.getZ();
        System.out.println("Name of Z Proxy: " + proxyName(zProxy));

        System.out.println("Operation through Z proxy...");
        zProxy.success();

        System.out.println("Changing Y's ref to Z...");
        yProxy.setZ(zProxy);
        zProxy = yProxy.getZ();
        System.out.println("Name of Z Proxy now: " + proxyName(zProxy));
        System.out.println("Operation through Z proxy again...");
        zProxy.success();

        System.out.println("Changing Y's ref to a bogus one...");
        ZMXBean zProxyBad = JMX.newMXBeanProxy(mbs, zName, ZMXBean.class);
        try {
            yProxy.setZ(zProxyBad);
        } catch (UndeclaredThrowableException e) {
            Throwable cause = e.getCause();
            if (cause instanceof OpenDataException) {
                System.out.println("...correctly got UndeclaredThrowableException");
                System.out.println("...wrapping: " + cause);
            } else
                throw new Exception("FAILED: wrong exception: " + cause);
        }

        System.out.println("Test passed");
    }

    private static ObjectName proxyName(Object proxy) {
        InvocationHandler ih = Proxy.getInvocationHandler(proxy);
        MBeanServerInvocationHandler mbsih = (MBeanServerInvocationHandler) ih;
        return mbsih.getObjectName();
    }
}
