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
 *
 */


/*
 * @test
 * @summary AOT training run should fail if critical classes have been transformed by JVMTI
 *          with ClassFileLoadHook
 * @bug 8380409
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build TransformCriticalClasses
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar TransformCriticalClassesApp
 * @run main/othervm/native TransformCriticalClasses
 */

import java.util.ArrayList;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.ProcessTools;

public class TransformCriticalClasses {
    public static void main(String... args) throws Exception {
        ArrayList<String> processArgs = new ArrayList<>();

        // Tell the native agent SimpleClassFileLoadHook to do an dummy transformation
        // of java/lang/Class. This class will be defined using the exact same bytecodes
        // as from the JDK, but the JVM will mark it as having been transformed by JVMTI
        // and will exclude it from the AOT configuration file.
        processArgs.add("-agentlib:SimpleClassFileLoadHook=-early,java/lang/Class,xxxxxx,xxxxxx");

        processArgs.add("-XX:AOTMode=record");
        processArgs.add("-XX:AOTConfiguration=app.aotconfig");
        processArgs.add("-Xlog:aot,cds");
        processArgs.add("-cp");
        processArgs.add("app.jar");
        processArgs.add("TransformCriticalClassesApp");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(processArgs);
        CDSTestUtils.executeAndLog(pb, "train")
            .shouldContain("Skipping java/lang/Class: From ClassFileLoadHook")
            .shouldContain("Critical class java.lang.Class has been excluded. AOT configuration file cannot be written")
            .shouldHaveExitValue(1);
    }
}

class TransformCriticalClassesApp {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
