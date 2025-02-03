/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test some basic Template instantiations. We do not necessarily generate correct
 *          java code, we just test that the code generation deterministically creates the
 *          expected String.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.template_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;

public class TestTemplate {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testSingleLine();
        //testMultiLine();
        //testMultiLineWithParameters();
        //testCustomLibrary();
        //testClassInstantiator();
        //testRepeat();
        //testDispatch();
        //testClassInstantiatorAndDispatch();
        //testChoose();
        //testFieldsAndVariables();
        //testFieldsAndVariablesDispatch();
        //testIntCon();
        //testLongCon();
        //testFuel();
        //testRecursiveCalls();
    }

    public static void testSingleLine() {
        var template = Template.make(() -> body("Hello World!"));
        String code = template.withArgs().render();
        checkEQ(code, "Hello World!");
    }


    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template rendering mismatch!");
        }
    }
}
