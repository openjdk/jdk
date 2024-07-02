/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8012723
 * @summary strictfp interface misses strictfp modifer on default method
 * @library /tools/lib /test/lib
 * @enablePreview
 * @run main CheckACC_STRICTFlagOnDefaultMethodTest
 */

import jdk.test.lib.compiler.CompilerUtils;
import toolbox.ToolBox;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class CheckACC_STRICTFlagOnDefaultMethodTest {
    private static final String AssertionErrorMessage =
        "All methods should have the ACC_STRICT access flag " +
        "please check output";
    private static final String offendingMethodErrorMessage =
        "Method %s of class %s doesn't have the ACC_STRICT access flag";
    private static final String SOURCE = """
            strictfp interface StrictfpInterface {
                default void default_interface_method() {}
                static void static_interface_method() {}
            }
            """;

    private List<String> errors = new ArrayList<>();

    public static void main(String[] args)
            throws IOException {
        new CheckACC_STRICTFlagOnDefaultMethodTest().run();
    }

    private void run()
            throws IOException {
        Path src = Path.of("src");
        Path out = Path.of("out");
        ToolBox toolBox = new ToolBox();
        toolBox.writeJavaFiles(src, SOURCE);
        CompilerUtils.compile(src, out, "--release", "16");
        check(out, "StrictfpInterface.class");
        if (!errors.isEmpty()) {
            for (String error: errors) {
                System.err.println(error);
            }
            throw new AssertionError(AssertionErrorMessage);
        }
    }

    void check(Path dir, String... fileNames) throws IOException {
        for (String fileName : fileNames) {
            ClassModel classFileToCheck = ClassFile.of().parse(dir.resolve(fileName));

            for (MethodModel method : classFileToCheck.methods()) {
                if ((method.flags().flagsMask() & ClassFile.ACC_STRICT) == 0) {
                    errors.add(String.format(offendingMethodErrorMessage,
                            method.methodName().stringValue(),
                            classFileToCheck.thisClass().asInternalName()));
                }
            }
        }
    }
}
