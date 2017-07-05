/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 7196045
 * @summary Possible JVM deadlock in ThreadTimesClosure when using HotspotInternal non-public API.
 * @run main/othervm -XX:+UsePerfData Test7196045
 */

import java.lang.management.ManagementFactory;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class Test7196045 {

    public static long duration = 1000 * 60 * 2;
    private static final String HOTSPOT_INTERNAL = "sun.management:type=HotspotInternal";

    public static void main(String[] args) {

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName objName= null;
        try {
            ObjectName hotspotInternal = new ObjectName(HOTSPOT_INTERNAL);
            try {
                server.registerMBean(new sun.management.HotspotInternal(), hotspotInternal);
            } catch (JMException e) {
                throw new RuntimeException("HotSpotWatcher: Failed to register the HotspotInternal MBean" + e);
            }
            objName= new ObjectName("sun.management:type=HotspotThreading");

        } catch (MalformedObjectNameException e1) {
            throw new RuntimeException("Bad object name" + e1);
        }

        long endTime = System.currentTimeMillis() + duration;
        long i = 0;
        while (true) {
            try {
                server.getAttribute(objName, "InternalThreadCpuTimes");
            } catch (Exception ex) {
                System.err.println("Exception while getting attribute: " + ex);
            }
            i++;
            if (i % 10000 == 0) {
                System.out.println("Successful iterations: " + i);
            }
            if (System.currentTimeMillis() > endTime) {
                break;
            }
        }
        System.out.println("PASSED.");
    }
}
