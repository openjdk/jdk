/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *com.sun.tools.attach.AttachNotSupportedException

 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.jcmd;

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.net.URISyntaxException;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import sun.tools.attach.HotSpotVirtualMachine;
import sun.tools.jstat.JStatLogger;
import sun.jvmstat.monitor.Monitor;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.VmIdentifier;

public class JCmd {
    public static void main(String[] args) {
        Arguments arg = null;
        try {
            arg = new Arguments(args);
        } catch (IllegalArgumentException ex) {
            System.err.println("Error parsing arguments: " + ex.getMessage()
                               + "\n");
            Arguments.usage();
            System.exit(1);
        }

        if (arg.isShowUsage()) {
            Arguments.usage();
            System.exit(1);
        }

        if (arg.isListProcesses()) {
            List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
            for (VirtualMachineDescriptor vmd : vmds) {
                System.out.println(vmd.id() + " " + vmd.displayName());
            }
            System.exit(0);
        }

        List<String> pids = new ArrayList<String>();
        if (arg.getPid() == 0) {
            // find all VMs
            List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
            for (VirtualMachineDescriptor vmd : vmds) {
                if (!isJCmdProcess(vmd)) {
                    pids.add(vmd.id());
                }
            }
        } else if (arg.getProcessSubstring() != null) {
            // use the partial class-name match
            List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
            for (VirtualMachineDescriptor vmd : vmds) {
                if (isJCmdProcess(vmd)) {
                    continue;
                }
                try {
                    String mainClass = getMainClass(vmd);
                    if (mainClass != null
                        && mainClass.indexOf(arg.getProcessSubstring()) != -1) {
                            pids.add(vmd.id());
                    }
                } catch (MonitorException|URISyntaxException e) {
                    if (e.getMessage() != null) {
                        System.err.println(e.getMessage());
                    } else {
                        Throwable cause = e.getCause();
                        if ((cause != null) && (cause.getMessage() != null)) {
                            System.err.println(cause.getMessage());
                        } else {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (pids.isEmpty()) {
                System.err.println("Could not find any processes matching : '"
                                   + arg.getProcessSubstring() + "'");
                System.exit(1);
            }
        } else if (arg.getPid() == -1) {
            System.err.println("Invalid pid specified");
            System.exit(1);
        } else {
            // Use the found pid
            pids.add(arg.getPid() + "");
        }

        for (String pid : pids) {
            System.out.println(pid + ":");
            if (arg.isListCounters()) {
                listCounters(pid);
            } else {
                try {
                    executeCommandForPid(pid, arg.getCommand());
                } catch(Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void executeCommandForPid(String pid, String command)
        throws AttachNotSupportedException, IOException,
               UnsupportedEncodingException {
        VirtualMachine vm = VirtualMachine.attach(pid);

        // Cast to HotSpotVirtualMachine as this is an
        // implementation specific method.
        HotSpotVirtualMachine hvm = (HotSpotVirtualMachine) vm;
        String lines[] = command.split("\\n");
        for (String line : lines) {
            if (line.trim().equals("stop")) {
                break;
            }
            try (InputStream in = hvm.executeJCmd(line);) {
                // read to EOF and just print output
                byte b[] = new byte[256];
                int n;
                do {
                    n = in.read(b);
                    if (n > 0) {
                        String s = new String(b, 0, n, "UTF-8");
                        System.out.print(s);
                    }
                } while (n > 0);
            }
        }
        vm.detach();
    }

    private static void listCounters(String pid) {
        // Code from JStat (can't call it directly since it does System.exit)
        VmIdentifier vmId = null;
        try {
            vmId = new VmIdentifier(pid);
        } catch (URISyntaxException e) {
            System.err.println("Malformed VM Identifier: " + pid);
            return;
        }
        try {
            MonitoredHost monitoredHost = MonitoredHost.getMonitoredHost(vmId);
            MonitoredVm monitoredVm = monitoredHost.getMonitoredVm(vmId, -1);
            JStatLogger logger = new JStatLogger(monitoredVm);
            logger.printSnapShot("\\w*", // all names
                    new AscendingMonitorComparator(), // comparator
                    false, // not verbose
                    true, // show unsupported
                    System.out);
            monitoredHost.detach(monitoredVm);
        } catch (MonitorException ex) {
            ex.printStackTrace();
        }
    }

    private static boolean isJCmdProcess(VirtualMachineDescriptor vmd) {
        try {
            String mainClass = getMainClass(vmd);
            return mainClass != null && mainClass.equals(JCmd.class.getName());
        } catch (URISyntaxException|MonitorException ex) {
            return false;
        }
    }

    private static String getMainClass(VirtualMachineDescriptor vmd)
            throws URISyntaxException, MonitorException {
        try {
            String mainClass = null;
            VmIdentifier vmId = new VmIdentifier(vmd.id());
            MonitoredHost monitoredHost = MonitoredHost.getMonitoredHost(vmId);
            MonitoredVm monitoredVm = monitoredHost.getMonitoredVm(vmId, -1);
            mainClass = MonitoredVmUtil.mainClass(monitoredVm, true);
            monitoredHost.detach(monitoredVm);
            return mainClass;
        } catch(NullPointerException e) {
            // There is a potential race, where a running java app is being
            // queried, unfortunately the java app has shutdown after this
            // method is started but before getMonitoredVM is called.
            // If this is the case, then the /tmp/hsperfdata_xxx/pid file
            // will have disappeared and we will get a NullPointerException.
            // Handle this gracefully....
            return null;
        }
    }

    /**
     * Class to compare two Monitor objects by name in ascending order.
     * (from jstat)
     */
    static class AscendingMonitorComparator implements Comparator<Monitor> {

        public int compare(Monitor m1, Monitor m2) {
            String name1 = m1.getName();
            String name2 = m2.getName();
            return name1.compareTo(name2);
        }
    }
}
