/*
 * Copyright (c) 2021, Red Hat, Inc.
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

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorServer;

public class DcmdMBeanRunner {

    private static String HOTSPOT_DIAGNOSTIC_MXBEAN_NAME =
        "com.sun.management:type=DiagnosticCommand";

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL("rmi", null, 0);
        JMXConnectorServer cs = null;
        JMXConnector cc = null;
        try {
            cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
            cs.start();
            JMXServiceURL addr = cs.getAddress();
            cc = JMXConnectorFactory.connect(addr);
            MBeanServerConnection mbsc = cc.getMBeanServerConnection();
            ObjectName name = new ObjectName(HOTSPOT_DIAGNOSTIC_MXBEAN_NAME);
            System.out.println("DiagnosticCommand MBean: " + name);
            System.out.println("DcmdMBeanRunner COMPLETE");
        } finally {
            try {
                cc.close();
                cs.stop();
            } catch (Exception e) { /* ignored */ }
        }
    }
}
