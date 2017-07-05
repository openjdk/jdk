/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jps;

import java.util.*;
import java.net.*;
import sun.jvmstat.monitor.*;

/**
 * Application to provide a listing of monitorable java processes.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class Jps {

    private static Arguments arguments;

    public static void main(String[] args) {
        try {
            arguments = new Arguments(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            Arguments.printUsage(System.err);
            return;
        }

        if (arguments.isHelp()) {
            Arguments.printUsage(System.out);
            System.exit(0);
        }

        try {
            HostIdentifier hostId = arguments.hostId();
            MonitoredHost monitoredHost =
                    MonitoredHost.getMonitoredHost(hostId);

            // get the set active JVMs on the specified host.
            Set jvms = monitoredHost.activeVms();

            for (Iterator j = jvms.iterator(); j.hasNext(); /* empty */ ) {
                StringBuilder output = new StringBuilder();
                Throwable lastError = null;

                int lvmid = ((Integer)j.next()).intValue();

                output.append(String.valueOf(lvmid));

                if (arguments.isQuiet()) {
                    System.out.println(output);
                    continue;
                }

                MonitoredVm vm = null;
                String vmidString = "//" + lvmid + "?mode=r";

                try {
                    VmIdentifier id = new VmIdentifier(vmidString);
                    vm = monitoredHost.getMonitoredVm(id, 0);
                } catch (URISyntaxException e) {
                    // unexpected as vmidString is based on a validated hostid
                    lastError = e;
                    assert false;
                } catch (Exception e) {
                    lastError = e;
                } finally {
                    if (vm == null) {
                        /*
                         * we ignore most exceptions, as there are race
                         * conditions where a JVM in 'jvms' may terminate
                         * before we get a chance to list its information.
                         * Other errors, such as access and I/O exceptions
                         * should stop us from iterating over the complete set.
                         */
                        output.append(" -- process information unavailable");
                        if (arguments.isDebug()) {
                            if ((lastError != null)
                                    && (lastError.getMessage() != null)) {
                                output.append("\n\t");
                                output.append(lastError.getMessage());
                            }
                        }
                        System.out.println(output);
                        if (arguments.printStackTrace()) {
                            lastError.printStackTrace();
                        }
                        continue;
                    }
                }

                output.append(" ");
                output.append(MonitoredVmUtil.mainClass(vm,
                        arguments.showLongPaths()));

                if (arguments.showMainArgs()) {
                    String mainArgs = MonitoredVmUtil.mainArgs(vm);
                    if (mainArgs != null && mainArgs.length() > 0) {
                        output.append(" ").append(mainArgs);
                    }
                }
                if (arguments.showVmArgs()) {
                    String jvmArgs = MonitoredVmUtil.jvmArgs(vm);
                    if (jvmArgs != null && jvmArgs.length() > 0) {
                      output.append(" ").append(jvmArgs);
                    }
                }
                if (arguments.showVmFlags()) {
                    String jvmFlags = MonitoredVmUtil.jvmFlags(vm);
                    if (jvmFlags != null && jvmFlags.length() > 0) {
                        output.append(" ").append(jvmFlags);
                    }
                }

                System.out.println(output);

                monitoredHost.detach(vm);
            }
        } catch (MonitorException e) {
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
}
