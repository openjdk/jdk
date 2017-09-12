/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package compiler.tiered;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes given test in a separate VM with enabled Tiered Compilation for
 * CompilationPolicyChoice 2 and 3
 */
public class TransitionsTestExecutor {
    public static void main(String[] args) throws Throwable {
        if (CompilerWhiteBoxTest.skipOnTieredCompilation(false)) {
            return;
        }
        if (args.length != 1) {
            throw new Error("TESTBUG: Test name should be specified");
        }
        executeTestFor(2, args[0]);
        executeTestFor(3, args[0]);
    }

    private static void executeTestFor(int compilationPolicy, String testName) throws Throwable {
        String policy = "-XX:CompilationPolicyChoice=" + compilationPolicy;

        // Get runtime arguments including VM options given to this executor
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        List<String> vmArgs = runtime.getInputArguments();

        // Construct execution command with compilation policy choice and test name
        List<String> args = new ArrayList<>(vmArgs);
        Collections.addAll(args, policy, testName);

        OutputAnalyzer out = ProcessTools.executeTestJvm(args.toArray(new String[args.size()]));
        out.shouldHaveExitValue(0);
    }
}
