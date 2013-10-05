/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import sun.management.VMManagement;

public class JMapHProfLargeHeapProc {
    private static final List<byte[]> heapGarbage = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        buildLargeHeap(args);

        // Print our pid on stdout
        System.out.println("PID[" + getProcessId() + "]");

        // Wait for input before termination
        System.in.read();
    }

    private static void buildLargeHeap(String[] args) {
        for (long i = 0; i < Integer.parseInt(args[0]); i++) {
            heapGarbage.add(new byte[1024]);
        }
    }

    public static int getProcessId() throws Exception {

        // Get the current process id using a reflection hack
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Field jvm = runtime.getClass().getDeclaredField("jvm");

        jvm.setAccessible(true);
        VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);

        Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");

        pid_method.setAccessible(true);

        int pid = (Integer) pid_method.invoke(mgmt);

        return pid;
    }

}
