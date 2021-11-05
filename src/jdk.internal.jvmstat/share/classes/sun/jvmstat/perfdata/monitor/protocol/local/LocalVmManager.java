/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvmstat.perfdata.monitor.protocol.local;

import sun.jvmstat.monitor.*;
import sun.jvmstat.monitor.event.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Class for managing the LocalMonitoredVm instances on the local system.
 * <p>
 * This class is responsible for the mechanism that detects the active
 * HotSpot Java Virtual Machines on the local host that can be accessed
 * by the current user. The ability to detect all possible HotSpot Java Virtual
 * Machines on the local host may be limited by the permissions of the
 * current user running this JVM.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class LocalVmManager {
    private FilenameFilter userDirFilter;
    private FilenameFilter userDirFileFilter;
    private FilenameFilter oldtmpFileFilter;

    /**
     * Creates a LocalVmManager instance for the local system.
     * <p>
     * Manages LocalMonitoredVm instances for which the current user
     * has appropriate permissions.
     */
    public LocalVmManager() {
        // 1.4.2 and later: The files are in {tmpdir}/hsperfdata_{any_user_name}/[0-9]+
        Pattern userDirPattern = Pattern.compile(PerfDataFile.userDirNamePattern);
        userDirFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return userDirPattern.matcher(name).lookingAt();
            }
        };

        Pattern userDirFilePattern = Pattern.compile(PerfDataFile.fileNamePattern);
        userDirFileFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return userDirFilePattern.matcher(name).matches();
            }
        };

        // 1.4.1 (or earlier?): the files are stored directly under {tmpdir}/ with
        // the following pattern.
        Pattern oldtmpFilePattern = Pattern.compile(PerfDataFile.tmpFileNamePattern);
        oldtmpFileFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return oldtmpFilePattern.matcher(name).matches();
            }
        };
    }

    /**
     * Return the current set of monitorable Java Virtual Machines that
     * are accessible by the current user.
     *
     * @return Set - the Set of monitorable Java Virtual Machines
     */
    public synchronized Set<Integer> activeVms() {
        /*
         * TODO: this method was synchronized due to its thread-unsafe use of the regexp
         * Matcher objects. That is not the case anymore, but I am too afraid to change
         * it now. Maybe fix this later in a separate RFE.
         */
        Set<Integer> jvmSet = new HashSet<Integer>();
        List<String> tmpdirs = PerfDataFile.getTempDirectories(0);

        for (String dir : tmpdirs) {
            File tmpdir = new File(dir);
            if (! tmpdir.isDirectory()) {
                continue;
            }


            // 1.4.2 and later: Look for the files {tmpdir}/hsperfdata_{any_user_name}/[0-9]+
            // that are readable by the current user.
            File[] dirs = tmpdir.listFiles(userDirFilter);
            for (int i = 0 ; i < dirs.length; i ++) {
                if (!dirs[i].isDirectory()) {
                    continue;
                }

                // get a list of files from the directory
                File[] files = dirs[i].listFiles(userDirFileFilter);
                if (files != null) {
                    for (int j = 0; j < files.length; j++) {
                        if (files[j].isFile() && files[j].canRead()) {
                            int vmid = PerfDataFile.getLocalVmId(files[j]);
                            if (vmid != -1) {
                              jvmSet.add(vmid);
                            }
                        }
                    }
                }
            }

            // look for any 1.4.1 files that are readable by the current user.
            File[] files = tmpdir.listFiles(oldtmpFileFilter);
            if (files != null) {
                for (int j = 0; j < files.length; j++) {
                    if (files[j].isFile() && files[j].canRead()) {
                        int vmid = PerfDataFile.getLocalVmId(files[j]);
                        if (vmid != -1) {
                          jvmSet.add(vmid);
                        }
                    }
                }
            }

        }
        return jvmSet;
    }
}
