/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Compile a call to a redefined method while recording AOT training data
 * @requires vm.cds.supports.aot.class.linking & vm.compiler2.enabled & vm.jvmti
 * @library /test/lib
 * @run driver RedefineClassHelper
 * @build RedefineCalleeInTraining jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             RedefineCalleeInTrainingApp RedefineCalleeInTrainingTarget
 * @run driver RedefineCalleeInTraining
 */

import java.io.InputStream;
import java.lang.reflect.Method;

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.whitebox.WhiteBox;

public class RedefineCalleeInTraining {
    public static void main(String[] args) throws Exception {
        SimpleCDSAppTester.of("RedefineCalleeInTraining")
            .addVmArgs("-javaagent:redefineagent.jar",
                       "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                       "-Xbootclasspath/a:WhiteBox.jar", "-Xbatch", "-XX:-TieredCompilation",
                       "-XX:CompileCommand=compileonly,RedefineCalleeInTrainingApp::caller",
                       "-Xlog:aot")
            .classpath("app.jar")
            .appCommandLine("RedefineCalleeInTrainingApp")
            .setTrainingChecker(out -> {
                out.shouldContain("Compiled caller after redefinition");
                out.shouldContain("Skipping RedefineCalleeInTrainingTarget: Has been redefined");
            })
            .setProductionChecker(out -> out.shouldContain("Compiled caller after redefinition"))
            .runAOTWorkflow();
    }
}

class RedefineCalleeInTrainingApp {
    static int caller(int x) {
        return RedefineCalleeInTrainingTarget.callee(x);
    }

    public static void main(String[] args) throws Exception {
        // Resolve the call and initialize the callee's holder before compilation.
        if (caller(1) != 2) {
            throw new RuntimeException("Unexpected result before redefinition");
        }
        try (InputStream in = RedefineCalleeInTrainingTarget.class.getResourceAsStream(
                 "RedefineCalleeInTrainingTarget.class")) {
            // Even an identical redefinition excludes the holder from training data.
            RedefineClassHelper.redefineClass(RedefineCalleeInTrainingTarget.class, in.readAllBytes());
        }

        // The caller is not redefined, so its C2 compilation has training data.
        // Inlining the callee queries ciMethod::inline_instructions_size(), but
        // MethodTrainingData::make() returns null for the redefined callee.
        WhiteBox wb = WhiteBox.getWhiteBox();
        Method method = RedefineCalleeInTrainingApp.class.getDeclaredMethod("caller", int.class);
        wb.deoptimizeMethod(method);
        if (!wb.enqueueMethodForCompilation(method, 4) || wb.getMethodCompilationLevel(method) != 4) {
            throw new RuntimeException("Caller was not compiled by C2");
        }
        if (caller(2) != 3) {
            throw new RuntimeException("Unexpected result after redefinition");
        }
        System.out.println("Compiled caller after redefinition");
    }
}

class RedefineCalleeInTrainingTarget {
    static int callee(int x) {
        return x + 1;
    }
}
