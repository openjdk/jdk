/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URISyntaxException;
import java.util.Set;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.VmIdentifier;

/**
 *
 * @test
 * @bug 6672135
 * @summary setInterval() for local MonitoredHost and local MonitoredVm
 * @author Tomas Hurka
 * @run main/othervm -XX:+UsePerfData CR6672135
 */
public class CR6672135 {

    private static final int INTERVAL = 2000;

    public static void main(String[] args) {
        int vmInterval;
        int hostInterval;

        try {
            MonitoredHost localHost = MonitoredHost.getMonitoredHost("localhost");
            Set vms = localHost.activeVms();
            Integer vmInt =  (Integer) vms.iterator().next();
            String uriString = "//" + vmInt + "?mode=r"; // NOI18N
            VmIdentifier vmId = new VmIdentifier(uriString);
            MonitoredVm vm = localHost.getMonitoredVm(vmId);

            vm.setInterval(INTERVAL);
            localHost.setInterval(INTERVAL);
            vmInterval = vm.getInterval();
            hostInterval = localHost.getInterval();
        } catch (Exception ex) {
            throw new Error ("Test failed",ex);
        }
        System.out.println("VM "+vmInterval);
        if (vmInterval != INTERVAL) {
            throw new Error("Test failed");
        }
        System.out.println("Host "+hostInterval);
        if (hostInterval != INTERVAL) {
            throw new Error("Test failed");
        }
    }
}

