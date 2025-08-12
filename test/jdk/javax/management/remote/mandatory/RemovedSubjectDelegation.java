/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8326666
 * @summary Test that Subject Delegation is removed.
 * @modules java.management.rmi
 *          java.management/com.sun.jmx.remote.security
 * @run main/othervm RemovedSubjectDelegation
 */

import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

public class RemovedSubjectDelegation {

    public static void main(String[] args) throws Exception {
        JMXConnectorServer jmxcs = null;
        JMXConnector jmxc = null;
        try {
            // Create an RMI registry
            //
            System.out.println("Start RMI registry...");
            Registry reg = null;
            int port = 5900;
            while (port++ < 5920) {
                try {
                    reg = LocateRegistry.createRegistry(port);
                    System.out.println("RMI registry running on port " + port);
                    break;
                } catch (RemoteException e) {
                    // Failed to create RMI registry...
                    System.out.println("Failed to create RMI registry " +
                                       "on port " + port);
                }
            }
            if (reg == null) {
                throw new RuntimeException("Failed to create RMI registry.");
            }
            // Instantiate the MBean server
            //
            System.out.println("Create the MBean server");
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            // Create an RMI connector server
            //
            System.out.println("Create an RMI connector server");
            JMXServiceURL url = new JMXServiceURL("rmi", null, 0);
            HashMap env = new HashMap();
            jmxcs = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
            jmxcs.start();
            // Create an RMI connector client
            //
            System.out.println("Create an RMI connector client");
            // Not setting env with "jmx.remote.credentials", should not get as far as verifying:
            jmxc = JMXConnectorFactory.connect(jmxcs.getAddress());
            Subject delegationSubject =
                new Subject(true,
                            Collections.singleton(new JMXPrincipal("delegate")),
                            Collections.EMPTY_SET,
                            Collections.EMPTY_SET);

            MBeanServerConnection mbsc = null;
            try {
                mbsc = jmxc.getMBeanServerConnection(delegationSubject);
                throw new RuntimeException("FAIL: delegationSubject was accepted. mbsc=" + mbsc);
            } catch (UnsupportedOperationException e) {
                System.out.println("PASS: " + e);
            }
        } catch (Exception e) {
            System.out.println("Unexpected exception caught = " + e);
            e.printStackTrace();
            throw e;
        } finally {
            if (jmxc != null)
                jmxc.close();
            if (jmxcs != null)
                jmxcs.stop();
        }
    }
}
