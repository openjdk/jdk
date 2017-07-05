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
 * @summary modules=debug should have logging from statements in the code
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run main ModulesTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;

public class ModulesTest {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xlog:modules=trace", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("define_javabase_module(): Definition of module:");
        output.shouldContain("define_javabase_module(): creation of package");
        output.shouldContain("define_module(): creation of module");
        output.shouldContain("define_module(): creation of package");
        output.shouldContain("set_bootloader_unnamed_module(): recording unnamed");
        output.shouldContain("add_module_exports(): package");
        output.shouldContain("add_reads_module(): Adding read from module");
        output.shouldContain("Setting package: class:");
        output.shouldHaveExitValue(0);
    }
}

