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
 * @summary Record timings of JMX Connector operations.
 *
 * @run main/othervm JMXBench rmi
 * @run main/othervm JMXBench http
 * @run main/othervm JMXBench rmi platform
 * @run main/othervm JMXBench http platform
 */
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.lang.management.*;

import javax.management.*;
import javax.management.remote.*;

public class JMXBench {

    String proto;
    boolean platform;
    String id;

    public static void main(String[] args) throws Exception {

        if (args.length < 1 || args.length > 2) {
            throw new RuntimeException("Run test with main arg to specify protocol (e.g. rmi or http), and optionally the keyword 'platform'.");
        }
        boolean platform = (args.length >= 2 && args[1].equals("platform"));
        new JMXBench(args[0], platform).run();
    }

    public JMXBench(String proto, boolean platform) {
        this.proto = proto;
        this.platform = platform;
        id = proto + " " + (platform ? "platformMBS" : "newMBS");
    }

    public void run() throws Exception {

        MBeanServer mbs = platform ? ManagementFactory.getPlatformMBeanServer() : MBeanServerFactory.createMBeanServer();

        JMXServiceURL addr = new JMXServiceURL(proto, null, 0);
        System.out.println("Creating ConnectorServer on " + addr);
        Map<String, ?> env = Collections.singletonMap("jmx.remote.x.daemon", "true");
        JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(addr, env, mbs);

        Timer t = new Timer("server start");
        server.start();
        t.stop();
        System.out.println("Started server on " + server.getAddress());

        System.out.println("Connecting a client to the server ...");

        t = new Timer(id + " connect"); 
        final JMXConnector conn = JMXConnectorFactory.connect(server.getAddress());
        t.stop();

        t = new Timer(id + " getMBSC");
        MBeanServerConnection mbsc = conn.getMBeanServerConnection();
        t.stop();

        t = new Timer(id + " getDefaultDomain");
        String defaultDomain = mbsc.getDefaultDomain();
        t.stop();

        t = new Timer(id + " getMBeanCount");
        int mbeanCount = mbsc.getMBeanCount();
        t.stop();


        ObjectName o = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        MBeanInfo mbeanInfo = null;
        t = new Timer(id + " getMBInfo JMImpl.");
        try {
            mbeanInfo = mbsc.getMBeanInfo(o);
        } catch (Exception e) {
            // ignore
        }
        t.stop();

        MBeanInfo mbeanInfo2 = null;
        if (platform) {
            o = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
            t = new Timer(id + " getMBInfo 2");
            try {
                mbeanInfo2 = mbsc.getMBeanInfo(o);
            } catch (Exception e) {
                // ignore
            }
            t.stop();
        }

        t = new Timer(id + " queryMBeans");
        Set<ObjectInstance> objects = mbsc.queryMBeans(null, null);
        t.stop();

        System.out.println("Closing the client ...");
        t = new Timer(id + " close connection");
        conn.close();
        t.stop();

        System.out.println("defaultDomain: " + defaultDomain);
        System.out.println("mbeanCount: " + mbeanCount);
        System.out.println("MBeanInfo: " + mbeanInfo);
        if (platform) {
            System.out.println("MBeanInfo 2: " + mbeanInfo2);
        }
        System.out.println("objects: " + objects);

    }

    public class Timer {
        String s;
        long t1;

        public Timer(String s) {
            this.s = s;
            t1 = System.nanoTime();
        }

        public void stop() {
            long t = System.nanoTime() - t1;
            System.out.println("Timer: " + s + ": " + t + " nanos, or " + ((float) t/1000000) + " millis");
        }
    }
}
