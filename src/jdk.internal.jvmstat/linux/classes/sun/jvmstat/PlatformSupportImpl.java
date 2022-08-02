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
    private static final String containerTmpPath = "/root" + getTemporaryDirectory();
    private static final String pidPatternStr = "^[0-9]+$";

    private long tmpInode;
    private long tmpDev;

    public PlatformSupportImpl() {
        super();
        try {
            var tmpPath = Path.of(getTemporaryDirectory());
            tmpInode = (Long)Files.getAttribute(tmpPath, "unix:ino");
            tmpDev = (Long)Files.getAttribute(tmpPath, "unix:dev");
        } catch (IOException e) {
            tmpInode = -1L;
            tmpDev = -1L;
        }
    }

    private boolean tempDirectoryEquals(Path p) {
        try {
            long ino = (Long)Files.getAttribute(p, "unix:ino");
            long dev = (Long)Files.getAttribute(p, "unix:dev");
            return (ino == tmpInode) && (dev == tmpDev);
        } catch (IOException e) {
            return false;
        }
    }

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
    public List<String> getTemporaryDirectories(int pid) {
        FilenameFilter pidFilter;
        Matcher pidMatcher;
        Pattern pidPattern = Pattern.compile(pidPatternStr);

        File procdir = new File("/proc");

        if (pid != 0) {
            pidPattern = Pattern.compile(Integer.toString(pid));
        }
        else {
            pidPattern = Pattern.compile(pidPatternStr);
        }
        pidMatcher = pidPattern.matcher("");

        // Add the default temporary directory first
        List<String> v = new ArrayList<>();
        v.add(getTemporaryDirectory());

        pidFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (!dir.isDirectory())
                    return false;
                pidMatcher.reset(name);
                return pidMatcher.matches();
            }
        };

        File[] dirs = procdir.listFiles(pidFilter);

        // Add all unique /proc/{pid}/root/tmp dirs that are not mapped to /tmp
        for (File dir : dirs) {
            String containerTmpDir = dir.getAbsolutePath() + containerTmpPath;
            File containerFile = new File(containerTmpDir);

            if (containerFile.exists() && containerFile.isDirectory() &&
                containerFile.canRead() &&
                !tempDirectoryEquals(containerFile.toPath())) {
                v.add(containerTmpDir);
            }
        }

        return v;
    }


    /*
     * Extract either the host PID or the NameSpace PID
     * from a file path.
     *
     * File path should be in 1 of these 2 forms:
     *
     * /proc/{pid}/root/tmp/hsperfdata_{user}/{nspid}
     *              or
     * /tmp/hsperfdata_{user}/{pid}
     *
     * In either case we want to return {pid} and NOT {nspid}
     *
     * This function filters out host pids which do not have
     * associated hsperfdata files.  This is due to the fact that
     * getTemporaryDirectories will return /proc/{pid}/root/tmp
     * paths for all container processes whether they are java
     * processes or not causing duplicate matches.
     */
    public int getLocalVmId(File file) throws NumberFormatException {
        String p = file.getAbsolutePath();
        String s[] = p.split("\\/");

        // Determine if this file is from a container
        if (s.length == 7 && s[1].equals("proc")) {
            int hostpid = Integer.parseInt(s[2]);
            int nspid = Integer.parseInt(s[6]);
            if (nspid == hostpid || nspid == getNamespaceVmId(hostpid)) {
                return hostpid;
            }
            else {
                return -1;
            }
        }
        else {
            return Integer.parseInt(file.getName());
        }
    }


    /*
     * Return the inner most namespaced PID if there is one,
     * otherwise return the original PID.
     */
    public int getNamespaceVmId(int pid) {
        // Assuming a real procfs sits beneath, reading this doesn't block
        // nor will it consume a lot of memory.
        Path statusPath = Paths.get("/proc", Integer.toString(pid), "status");
        if (Files.notExists(statusPath)) {
            return pid; // Likely a bad pid, but this is properly handled later.
        }

        try {
            for (String line : Files.readAllLines(statusPath)) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].trim().equals("NSpid")) {
                    parts = parts[1].trim().split("\\s+");
                    // The last entry represents the PID the JVM "thinks" it is.
                    // Even in non-namespaced pids these entries should be
                    // valid. You could refer to it as the inner most pid.
                    int ns_pid = Integer.parseInt(parts[parts.length - 1]);
                    return ns_pid;
                }
            }
            // Old kernels may not have NSpid field (i.e. 3.10).
            // Fallback to original pid in the event we cannot deduce.
            return pid;
        } catch (NumberFormatException | IOException x) {
            return pid;
        }
    }
}
