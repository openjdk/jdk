/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @summary Test ClassUnloading flag sanity
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run driver TestClassUnloadingArguments
 */

import java.util.*;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestClassUnloadingArguments {

    public static void testWith(String msg, boolean cu, String... args) throws Exception {
        String[] cmds = Arrays.copyOf(args, args.length + 3);
        cmds[args.length] = "-Xmx128m";
        cmds[args.length + 1] = "-XX:+PrintFlagsFinal";
        cmds[args.length + 2] = "-version";
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(cmds);
        output.shouldHaveExitValue(0);
        output.shouldContain("ClassUnloading");

        Asserts.assertEQ(output.firstMatch("(.+?) ClassUnloading.+?= (.+?) (.+?)", 2),
                Boolean.toString(cu),
                msg + ", but got wrong ClassUnloading");
    }

    public static void main(String[] args) throws Exception {
        testWith("Shenandoah GC should have class unloading enabled by default",
                true,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseShenandoahGC");

        testWith("Shenandoah GC should disable everything",
                false,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseShenandoahGC",
                "-XX:-ClassUnloading");
    }

}
