/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8175000 8314491
 * @summary test jexec
 * @requires os.family == "linux"
 * @build TestHelper
 * @run main Jexec
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Jexec extends TestHelper {
    private final File testJar;
    private final File jexecCmd;
    private final String message = "Hello, do you read me ?";

    Jexec() throws IOException {
        jexecCmd = new File(JAVA_LIB, "jexec");
        if (!jexecCmd.exists() || !jexecCmd.canExecute()) {
            throw new Error("jexec: does not exist or not executable");
        }

        testJar = new File("test.jar");
        StringBuilder tsrc = new StringBuilder();
        tsrc.append("public static void main(String... args) {\n");
        tsrc.append("   for (String x : args) {\n");
        tsrc.append("        System.out.println(x);\n");
        tsrc.append("   }\n");
        tsrc.append("}\n");
        createJar(testJar, tsrc.toString());
    }

    public static void main(String... args) throws Exception {
        Jexec t = new Jexec();
        t.run(null);
    }

    private void runTest(String... cmds) throws Exception {
        TestResult tr = doExec(cmds);
        if (!tr.isOK()) {
            System.err.println(tr);
            throw new Exception("incorrect exit value");
        }
        if (!tr.contains(message)) {
            System.err.println(tr);
            throw new Exception("expected message \'" + message + "\' not found");
        }
    }

    @Test
    void jexec() throws Exception {
        runTest(jexecCmd.getAbsolutePath(),
                testJar.getAbsolutePath(), message);
    }

    @Test
    void jexecInPath() throws Exception {
        Path jexec = Path.of(jexecCmd.getAbsolutePath());
        runTest("/bin/sh", "-c",
                String.format("PATH=%s ; jexec %s '%s'",jexec.getParent(), testJar.getAbsolutePath(), message));
    }
}
