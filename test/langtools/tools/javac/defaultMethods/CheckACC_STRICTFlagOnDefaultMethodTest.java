/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @compile -source 16 -target 16 CheckACC_STRICTFlagOnDefaultMethodTest.java
 * @run main CheckACC_STRICTFlagOnDefaultMethodTest
 */

import jdk.internal.classfile.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CheckACC_STRICTFlagOnDefaultMethodTest {
    private static final String AssertionErrorMessage =
        "All methods should have the ACC_STRICT access flag " +
        "please check output";
    private static final String offendingMethodErrorMessage =
        "Method %s of class %s doesn't have the ACC_STRICT access flag";

    private List<String> errors = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        new CheckACC_STRICTFlagOnDefaultMethodTest().run();
    }

    private void run() throws IOException {
        String testClasses = System.getProperty("test.classes");
        check(testClasses,
                "CheckACC_STRICTFlagOnDefaultMethodTest$StrictfpInterface.class");
        if (errors.size() > 0) {
            for (String error: errors) {
                System.err.println(error);
            }
            throw new AssertionError(AssertionErrorMessage);
        }
    }

    void check(String dir, String... fileNames) throws IOException {
        for (String fileName : fileNames) {
            ClassModel classFileToCheck = Classfile.of().parse(new File(dir, fileName).toPath());

            for (MethodModel method : classFileToCheck.methods()) {
                if ((method.flags().flagsMask() & Classfile.ACC_STRICT) == 0) {
                    errors.add(String.format(offendingMethodErrorMessage,
                            method.methodName().stringValue(),
                            classFileToCheck.thisClass().asInternalName()));
                }
            }
        }
    }

    strictfp interface StrictfpInterface {
        default void default_interface_method() {}
        static void static_interface_method() {}
    }

}
