/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Use Lookup.defineClass() to load a class with rewritten bytecode. Make sure
 *          the archived class with the same name is not loaded.
 * @requires vm.cds
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile test-classes/RewriteBytecodesInline.java test-classes/Util.java test-classes/Point.java test-classes/WithInlinedField.java RewriteBytecodesInlineTest.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm RewriteBytecodesInlineTest
 */

import java.io.File;
import jdk.test.lib.process.OutputAnalyzer;

public class RewriteBytecodesInlineTest {
  public static void main(String[] args) throws Exception {
    String wbJar = JarBuilder.build(true, "WhiteBox", "jdk/test/whitebox/WhiteBox");
    String use_whitebox_jar = "-Xbootclasspath/a:" + wbJar;

    String appJar = JarBuilder.build("dynamic_define", "RewriteBytecodesInline", "Util", "Point", "WithInlinedField");
    String superClsFile = (new File(System.getProperty("test.classes", "."), "Point.class")).getPath();

    TestCommon.dump(appJar, TestCommon.list("RewriteBytecodesInline", "Point", "WithInlinedField"),
                    // command-line arguments ...
                    use_whitebox_jar,
                    "--enable-preview");

    OutputAnalyzer output = TestCommon.exec(appJar,
                    // command-line arguments ...
                    "--enable-preview",
                    use_whitebox_jar,
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+WhiteBoxAPI",
                    "RewriteBytecodesInline", superClsFile);
    TestCommon.checkExec(output);
  }
}
