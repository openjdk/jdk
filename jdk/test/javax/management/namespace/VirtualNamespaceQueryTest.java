/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * @test VirtualNamespaceQueryTest.java
 * @summary General VirtualNamespaceQueryTest test.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean VirtualNamespaceQueryTest Wombat WombatMBean
 *            NamespaceController NamespaceControllerMBean
 *            JMXRemoteTargetNamespace
 * @compile -XDignore.symbol.file=true VirtualNamespaceQueryTest.java
 *          Wombat.java WombatMBean.java
 *          NamespaceController.java NamespaceControllerMBean.java
 *          JMXRemoteTargetNamespace.java
 * @run main VirtualNamespaceQueryTest
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationEmitter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.MBeanServerSupport;

/**
 *
 * @author dfuchs
 */
public class VirtualNamespaceQueryTest {
    public static class WombatRepository extends MBeanServerSupport {
        final Wombat wombat;
        final StandardMBean mbean;
        final ObjectName wombatName;

        public WombatRepository(ObjectName wombatName) {
            try {
                wombat = new Wombat();
                mbean  = wombat;
                this.wombatName = wombatName;
                wombat.preRegister(null,wombatName);
            } catch (Exception x) {
                throw new IllegalArgumentException(x);
            }
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
            throws InstanceNotFoundException {
            if (wombatName.equals(name)) return mbean;
            else throw new InstanceNotFoundException(String.valueOf(name));
        }

        @Override
        protected Set<ObjectName> getNames() {
            final Set<ObjectName> res = Collections.singleton(wombatName);
            return res;
        }

        @Override
        public NotificationEmitter getNotificationEmitterFor(
                ObjectName name) throws InstanceNotFoundException {
            DynamicMBean mb = getDynamicMBeanFor(name);
            if (mb instanceof NotificationEmitter)
                return (NotificationEmitter)mb;
            return null;
        }
    }
    public static class WombatNamespace extends JMXNamespace {
        public WombatNamespace(ObjectName wombatName) {
            super(new WombatRepository(wombatName));
        }
    }

    public static void simpleTest() throws Exception {
        final MBeanServer  server = MBeanServerFactory.newMBeanServer();
        final ObjectName   wombatName = new ObjectName("burrow:type=Wombat");
        final JMXNamespace ns = new WombatNamespace(wombatName);
        server.registerMBean(ns, JMXNamespaces.getNamespaceObjectName("wombats"));
        final Set<ObjectName> dirs =
                server.queryNames(new ObjectName("wombats//*//:type=JMXNamespace"),
                wombatName);
        System.out.println("all dirs: "+dirs);
        if (dirs.size()>0)
            throw new RuntimeException("Unexpected ObjectNames returned: "+dirs);

        final ObjectInstance inst = NamespaceController.createInstance(server);
        final NamespaceControllerMBean controller =
                JMX.newMBeanProxy(server, inst.getObjectName(),
                NamespaceControllerMBean.class);
        final String[] dirNames = controller.findNamespaces(null,null,2);
        System.err.println(Arrays.toString(dirNames));
    }

    public static void main(String[] args) throws Exception {
        simpleTest();
    }
}
