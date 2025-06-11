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
 * @bug 8344942
 * @summary Test formatting of Integer, Long, Float and Double.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.tests.TestFormat
 */

package template_framework.tests;

import java.util.List;
import java.util.ArrayList;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.*;
import compiler.lib.verify.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

public class TestFormat {
    record FormatInfo(int id, String type, Object value) {}

    public static void main(String[] args) {
        List<FormatInfo> list = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            int v = Generators.G.ints().next();
            list.add(new FormatInfo(i, "int", v));
        }

        for (int i = 1000; i < 2000; i++) {
            long v = Generators.G.longs().next();
            list.add(new FormatInfo(i, "long", v));
        }

        for (int i = 2000; i < 3000; i++) {
            float v = Generators.G.floats().next();
            list.add(new FormatInfo(i, "float", v));
        }

        for (int i = 3000; i < 4000; i++) {
            double v = Generators.G.doubles().next();
            list.add(new FormatInfo(i, "double", v));
        }

        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(list));
        comp.compile();

        // Run each of the "get" methods, and check the result.
        for (FormatInfo info : list) {
            Object ret1 = comp.invoke("p.xyz.InnerTest", "get_let_" + info.id, new Object[] {});
            Object ret2 = comp.invoke("p.xyz.InnerTest", "get_token_" + info.id, new Object[] {});
            System.out.println("id: " + info.id + " -> " + info.value + " == " + ret1 + " == " + ret2);
            Verify.checkEQ(ret1, info.value);
            Verify.checkEQ(ret2, info.value);
        }
    }

    private static String generate(List<FormatInfo> list) {
        // Generate 2 "get" methods, one that formats via "let" (hashtag), the other via direct token.
        var template1 = Template.make("info", (FormatInfo info) -> body(
            let("id", info.id()),
            let("type", info.type()),
            let("value", info.value()),
            """
            public static #type get_let_#id() { return #value; }
            """,
            "public static #type get_token_#id() { return ", info.value(), "; }\n"
        ));

        // For each FormatInfo in list, generate the "get" methods inside InnerTest class.
        var template2 = Template.make(() -> body(
            """
            package p.xyz;
            public class InnerTest {
            """,
            list.stream().map(info -> template1.asToken(info)).toList(),
            """
            }
            """
        ));

        return template2.render();
    }
}
