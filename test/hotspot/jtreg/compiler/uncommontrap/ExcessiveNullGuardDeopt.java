/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company All Rights Reserved.
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
 * @test
 * @bug 8278135
 * @summary Excessive null check guard deoptimization due to java.lang.Class unloaded
 *
 * @library /test/lib
 *
 * @requires vm.compiler2.enabled
 * @requires vm.debug == true
 * @compile CodeDependenciesSimple.java
 *
 * @run driver compiler.uncommontrap.ExcessiveNullGuardDeopt
 */

package compiler.uncommontrap;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ExcessiveNullGuardDeopt {
    public static void main(String[] args) throws Exception {
        String[] procArgs = new String[] {
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+TraceDeoptimization",
            "-XX:CompileOnly=compiler/uncommontrap/CodeDependenciesSimple.foo",
            "compiler.uncommontrap.CodeDependenciesSimple",
        };
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getOutput());
        output.shouldHaveExitValue(0);
        output.shouldNotContain("reason=null_assert_or_unreached0");
    }
}
