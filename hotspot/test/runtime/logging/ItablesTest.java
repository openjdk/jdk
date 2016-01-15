/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.    See the GNU General Public License
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
 * @bug 8141564
 * @summary itables=trace should have logging from each of the statements
 *          in the code
 * @library /testlibrary
 * @ignore 8146435
 * @compile ClassB.java
 * @modules java.base/sun.misc
 *          java.management
 * @run driver ItablesTest
 */

import jdk.test.lib.*;

public class ItablesTest {
    public static void main(String[] args) throws Exception {
        if (Platform.isDebugBuild()) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xlog:itables=trace", "ClassB");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain(": Initializing itables for ClassB");
            output.shouldContain(": Initializing itable indices for interface ");
            output.shouldContain("vtable index ");
            output.shouldContain("itable index ");
            output.shouldContain("target: ClassB.Method1()V, method_holder: ClassB target_method flags: public");
            output.shouldContain("invokeinterface resolved method: caller-class");
            output.shouldContain("invokespecial resolved method: caller-class:ClassB");
            output.shouldContain("invokespecial selected method: resolved-class:ClassB");
            output.shouldContain("invokeinterface selected method: receiver-class");
            output.shouldContain("Resolving: klass: ");
            output.shouldHaveExitValue(0);
        }
    }
}
