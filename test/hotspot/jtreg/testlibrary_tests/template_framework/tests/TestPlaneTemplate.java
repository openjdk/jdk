/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Template with no parameter or template hole or variable renaming.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestPlaneTemplate
 */

package template_framework.tests;

import compiler.lib.template_framework.*;

public class TestPlaneTemplate {

    public static void main(String[] args) {
        test1();
        test2();
        test3();
    }

    public static void test1() {
        Template template = new Template("my_template","Hello World!");
        String code = template.instantiate();
        checkEQ(code, "Hello World!");
    }

    public static void test2() {
        Template template = new Template("my_template",
            """
            Code on more
            than a single line
            """
        );
        String code = template.instantiate();
        String expected = 
            """
            Code on more
            than a single line
            """;
        checkEQ(code, expected);
    }

    public static void test3() {
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
	    throw new RuntimeException("Template instantiation mismatch!");
        }
    }
}
