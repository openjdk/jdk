/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
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

package sun.tools.common;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

/**
 * Class for finding process matching a process argument,
 * excluding tool it self and returning a list containing
 * the process identifiers.
 */
public class ProcessArgumentMatcher {
    private String excludeCls;
    private String matchClass = null;
    private String singlePid = null;
    private boolean matchAll = false;

    public ProcessArgumentMatcher(String pidArg, Class<?> excludeClass) {
        excludeCls = excludeClass.getName();
        if (pidArg == null || pidArg.isEmpty()) {
            throw new IllegalArgumentException("Pid string is invalid");
        }
        if (pidArg.charAt(0) == '-') {
            throw new IllegalArgumentException("Unrecognized " + pidArg);
        }
        try {
            long pid = Long.parseLong(pidArg);
            if (pid == 0) {
                matchAll = true;
            } else {
                singlePid = String.valueOf(pid);
            }
        } catch (NumberFormatException nfe) {
            matchClass = pidArg;
        }
    }

    private boolean check(VirtualMachineDescriptor vmd) {
        String mainClass = null;
        try {
            VmIdentifier vmId = new VmIdentifier(vmd.id());
            MonitoredHost monitoredHost = MonitoredHost.getMonitoredHost(vmId);
            MonitoredVm monitoredVm = monitoredHost.getMonitoredVm(vmId, -1);
            mainClass = MonitoredVmUtil.mainClass(monitoredVm, true);
            monitoredHost.detach(monitoredVm);
        } catch (NullPointerException npe) {
            // There is a potential race, where a running java app is being
            // queried, unfortunately the java app has shutdown after this
            // method is started but before getMonitoredVM is called.
            // If this is the case, then the /tmp/hsperfdata_xxx/pid file
            // will have disappeared and we will get a NullPointerException.
            // Handle this gracefully....
            return false;
        } catch (MonitorException | URISyntaxException e) {
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
            return false;
        }

        if (mainClass.equals(excludeCls)) {
            return false;
        }

        if (matchAll || mainClass.indexOf(matchClass) != -1) {
            return true;
        }

        return false;
    }

    public Collection<String> getPids() {
        Collection<String> pids = new ArrayList<>();
        if (singlePid != null) {
            pids.add(singlePid);
            return pids;
        }
        List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : vmds) {
            if (check(vmd)) {
                pids.add(vmd.id());
            }
        }
        return pids;
    }
}
