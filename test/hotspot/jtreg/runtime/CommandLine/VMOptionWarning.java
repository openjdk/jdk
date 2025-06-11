/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test VMOptionWarningExperimental
 * @bug 8027314
 * @summary Warn if experimental vm option is used and -XX:+UnlockExperimentalVMOptions isn't specified.
 * @requires vm.flagless
 * @requires !vm.opt.final.UnlockExperimentalVMOptions
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver VMOptionWarning Experimental
 */

/* @test VMOptionWarningDiagnostic
 * @bug 8027314
 * @summary Warn if diagnostic vm option is used and -XX:+UnlockDiagnosticVMOptions isn't specified.
 * @requires vm.flagless
 * @requires !vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver VMOptionWarning Diagnostic
 */

/* @test VMCompileCommandWarningDiagnostic
 * @bug 8351958
 * @summary Warn if compile command that is an alias for a diagnostic vm option is used and -XX:+UnlockDiagnosticVMOptions isn't specified.
 * @requires vm.flagless
 * @requires !vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver VMOptionWarning DiagnosticCompileCommand
 */

/* @test VMOptionWarningDevelop
 * @bug 8027314
 * @summary Warn if develop vm option is used with product version of VM.
 * @requires vm.flagless
 * @requires !vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver VMOptionWarning Develop
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

public class VMOptionWarning {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("wrong number of args: " + args.length);
        }

        ProcessBuilder pb;
        OutputAnalyzer output;
        switch (args[0]) {
            case "Experimental": {
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+AlwaysSafeConstructors", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldNotHaveExitValue(0);
                output.shouldContain("Error: VM option 'AlwaysSafeConstructors' is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions.");
                break;
            }
            case "Diagnostic": {
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintInlining", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldNotHaveExitValue(0);
                output.shouldContain("Error: VM option 'PrintInlining' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
                break;
            }
            case "DiagnosticCompileCommand": {
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompileCommand=PrintAssembly,MyClass::myMethod", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldNotHaveExitValue(0);
                output.shouldContain("Error: VM option 'PrintAssembly' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
                break;
            }
            case "Develop": {
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+VerifyStack", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldNotHaveExitValue(0);
                output.shouldContain("Error: VM option 'VerifyStack' is develop and is available only in debug version of VM.");
                break;
            }
            default: {
                throw new RuntimeException("Invalid argument: " + args[0]);
            }
        }
    }
}
