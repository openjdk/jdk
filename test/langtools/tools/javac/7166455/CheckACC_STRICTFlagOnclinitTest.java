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
 * @bug 7166455
 * @summary javac doesn't set ACC_STRICT bit on <clinit> for strictfp class
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @compile -source 16 -target 16 CheckACC_STRICTFlagOnclinitTest.java
 * @run main CheckACC_STRICTFlagOnclinitTest
 */

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import jdk.internal.classfile.*;

public strictfp class CheckACC_STRICTFlagOnclinitTest {
    private static final String AssertionErrorMessage =
        "All methods should have the ACC_STRICT access flag " +
        "please check output";
    private static final String offendingMethodErrorMessage =
        "Method %s of class %s doesn't have the ACC_STRICT access flag";

    static {
        class Foo {
            class Bar {
                void m11() {}
            }
            void m1() {}
        }
    }
    void m2() {
        class Any {
            void m21() {}
        }
    }

    private List<String> errors = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        new CheckACC_STRICTFlagOnclinitTest().run();
    }

    private void run() throws IOException {
        String testClasses = System.getProperty("test.classes");
        check(testClasses,
              "CheckACC_STRICTFlagOnclinitTest.class",
              "CheckACC_STRICTFlagOnclinitTest$1Foo.class",
              "CheckACC_STRICTFlagOnclinitTest$1Foo$Bar.class",
              "CheckACC_STRICTFlagOnclinitTest$1Any.class");
        if (errors.size() > 0) {
            for (String error: errors) {
                System.err.println(error);
            }
            throw new AssertionError(AssertionErrorMessage);
        }
    }

    void check(String dir, String... fileNames) throws IOException{
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

}
