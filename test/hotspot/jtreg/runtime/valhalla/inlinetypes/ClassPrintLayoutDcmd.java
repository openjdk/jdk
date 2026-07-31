/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All rights reserved.
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package runtime.valhalla.inlinetypes;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;


/*
 * @test
 * @summary Test the VM.class_print_layout command
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ClassPrintLayoutDcmd.java
 * @run main runtime.valhalla.inlinetypes.ClassPrintLayoutDcmd
 */

public value class ClassPrintLayoutDcmd {

    @LooselyConsistentValue
    static value class Point {
        int i = 0;
        int j = 0;
    }

    @LooselyConsistentValue
    static value class Line {
        @NullRestricted
        Point p1;
        @NullRestricted
        Point p2;
        Line() {
            this.p1 = this.p2 = new Point();
        }
    }
    static {
        try {
            Class.forName(Line.class.getName());
        } catch(Throwable e) {
            throw new RuntimeException("failed to find class");
        }
    }
    static ProcessBuilder pb = new ProcessBuilder();

    static void testCmd(String arg, int expectExitCode, String... expectStrings) throws Exception {
        // Grab my own PID
        String pid = Long.toString(ProcessTools.getProcessId());

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.class_print_layout", arg });
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(expectExitCode);
        for (String expectString : expectStrings) {
            output.shouldContain(expectString);
        }
    }

    public static void main(String args[]) throws Exception {
        testCmd("foo/bar", 0, "");
        testCmd("", 1, "IllegalArgumentException", "mandatory");
        testCmd("java/lang/Object", 0, "java/lang/Object", "@bootstrap");
        testCmd("java/lang/Class", 0, "java/lang/Class", "@bootstrap");
        testCmd("runtime/valhalla/inlinetypes/ClassPrintLayoutDcmd$Line", 0, "@app", "p1", "p2");
    }
}
