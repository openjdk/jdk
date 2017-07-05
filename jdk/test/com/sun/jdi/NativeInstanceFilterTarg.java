/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.Version;

public class NativeInstanceFilterTarg {

    public static void main(String args[]) {
        boolean runTest = jvmSupportsJVMTI_12x();
        String s1 = "abc";
        String s2 = "def";
        latch(s1);
        s1.intern();
        if (runTest) {
            s2.intern(); // this is the call that generates events that ought
                         // to be filtered out.
        } else {
            System.out.println("Neutering test since JVMTI 1.2 not supported");
        }
    }

    // Used by debugger to get an instance to filter with
    public static String latch(String s) { return s; }

    public static boolean jvmSupportsJVMTI_12x() {
        // This fix requires the JVM to support JVMTI 1.2, which doesn't
        // happen until HSX 20.0, build 05.
        int major = Version.jvmMajorVersion();
        int minor = Version.jvmMinorVersion();
        int micro = Version.jvmMicroVersion();
        int build = Version.jvmBuildNumber();

        return (major > 20 || major == 20 &&
                   (minor > 0 || micro > 0 || build >= 5));
    }
}
