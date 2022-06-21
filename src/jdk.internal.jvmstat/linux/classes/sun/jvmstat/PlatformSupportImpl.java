/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvmstat;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

/*
 * Linux specific implementation of the PlatformSupport routines
 * providing process ID and temp directory support for host and
 * cgroup container processes.
 */
public class PlatformSupportImpl extends PlatformSupport {
    private static final String pidPatternStr = "^[0-9]+$";

    /*
     * Return the temporary directories that the VM uses for the attach
     * and perf data files.  This function returns the traditional
     * /tmp directory in addition to paths within the /proc file system
     * allowing access to container tmp directories such as /proc/{pid}/root/tmp.
     *
     * It is important that this directory is well-known and the
     * same for all VM instances. It cannot be affected by configuration
     * variables such as java.io.tmpdir.
     *
     * Implementation Details:
     *
     * Java processes that run in docker containers are typically running
     * under cgroups with separate pid namespaces which means that pids
     * within the container are different that the pid which is visible
     * from the host.  The container pids typically start with 1 and
     * increase.  The java process running in the container will use these
     * pids when creating the hsperfdata files.  In order to locate java
     * processes that are running in containers, we take advantage of
     * the Linux proc file system which maps the containers tmp directory
     * to the hosts under /proc/{hostpid}/root/tmp.  We use the /proc status
     * file /proc/{hostpid}/status to determine the containers pid and
     * then access the hsperfdata file.  The status file contains an
     * entry "NSPid:" which shows the mapping from the hostpid to the
     * containers pid.
     *
     * Example:
     *
     * NSPid: 24345 11
     *
     * In this example process 24345 is visible from the host,
     * is running under the PID namespace and has a container specific
     * pid of 11.
     *
     * The search for Java processes is done by first looking in the
     * traditional /tmp for host process hsperfdata files and then
     * the search will container in every /proc/{pid}/root/tmp directory.
     * There are of course added complications to this search that
     * need to be taken into account.
     *
     * 1. duplication of tmp directories
     *
     * When cgroups is enabled, the directory /proc/{pid}/root/tmp may
     * exist even if the given pid is not running inside a container. In
     * this case, this directory is usually the same as /tmp and should
     * be skipped, or else we would get duplicated hsperfdata files.
     * This case can be detected if the inode and device id of
     * /proc/{pid}/root/tmp are the same as /tmp.
     *
     * 2. Containerized processes without PID namespaces being enabled.
     *
     * If a container is running a Java process without namespaces being
     * enabled, an hsperfdata file will only be located at
     * /proc/{hostpid}/root/tmp/{hostpid}.  This is handled by
     * checking the last component in the path for both the hostpid
     * and potential namespacepids (if one exists).
     */
    public Set<Integer> activeVms() {
        Set<Integer> jvmSet = new HashSet<Integer>();
        Pattern pidPattern = Pattern.compile(pidPatternStr);
        Matcher pidMatcher = pidPattern.matcher("");


        FilenameFilter pidFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (!dir.isDirectory())
                    return false;
                pidMatcher.reset(name);
                return pidMatcher.matches();
            }
        };

        File procdir = new File("/proc");
        procdir.listFiles((File dir, String name) -> {
                pidMatcher.reset(name);
                if (pidMatcher.matches()) {
                    int pid;
                    try {
                        pid = Integer.parseInt(name);
                    } catch (NumberFormatException e) {
                        return false;
                        // FIXME should never happen
                    }
                    if (getAttachID(pid) != null) {
                        jvmSet.add(pid);
                    }
                }
                return false;
            });

        return jvmSet;
    }

    public Integer getAttachID(int pid) {
        //System.out.println("getAttachID: " + pid);
        String mapsFile = "/proc/" + pid + "/maps";
        String pattern = "/tmp/hsperfdata_";

        try (BufferedReader reader = newBufferedReader(mapsFile)) {
            for (;;) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                int i = line.indexOf(pattern);
                if (i > 0) {
                    //System.out.println("Found: " + line);
                    i += pattern.length();
                    while (i < line.length() && line.charAt(i) != '/') {
                        i++;
                    }
                    i++;
                    String tail = line.substring(i);
                    //System.out.println(tail);
                    try {
                        int attachID = Integer.parseInt(tail);
                        // System.out.println("attachID = " + attachID);
                        return attachID;
                    } catch (NumberFormatException e) {
                        // not a hsperfdata file created by the VM. Ignore
                    }
                }
            }
        } catch (IOException e) {}

        // This process is not mapping a hsperfdata
        return null;
    }

    private static BufferedReader newBufferedReader(String fileName)
        throws IOException
    {
        InputStream in = new FileInputStream(new File(fileName));
        Reader reader = new InputStreamReader(in);
        return new BufferedReader(reader);
    }
}
