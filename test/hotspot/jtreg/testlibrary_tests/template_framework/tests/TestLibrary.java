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
 * @run driver template_framework.tests.TestLibrary
 */

package template_framework.tests;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.*;
import compiler.lib.verify.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;

import static compiler.lib.template_framework.Library.format;

public class TestLibrary {
    public static void main(String[] args) {
        testFormatInt();
    }

    private static void testFormatInt() {
        int gold = 1;
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("p.xyz.InnerTest", generate("int", format(gold)));
        comp.compile();
        Object ret = comp.invoke("p.xyz.InnerTest", "get", new Object[] {});
        int result = (int)ret;
        Verify.checkEQ(result, gold);
    }

    private static String generate(String type, String value) {
        var template1 = Template.make("type", "value", (String t, String v) -> body(
            """
            package p.xyz;
            public class InnerTest {
                public static #type get() { return #value; }
            }
            """
        ));

        return template1.withArgs(type, value).render();
    }
}
