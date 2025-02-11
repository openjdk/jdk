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

import java.util.List;
import java.util.ArrayList;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.*;
import compiler.lib.verify.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

import static compiler.lib.template_framework.Library.format;

public class TestLibrary {
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();

    public static void main(String[] args) {
        testFormat();
    }

    record FormatInfo(int id, String type, Object value, String formatted) {}

    private static void testFormat() {
        List<FormatInfo> list = new ArrayList();

        for (int i = 0; i < 1000; i++) {
            int v = GEN_INT.next();
            list.add(new FormatInfo(i, "int", v, format(v)));
        }

        for (int i = 1000; i < 2000; i++) {
            long v = GEN_LONG.next();
            list.add(new FormatInfo(i, "long", v, format(v)));
        }

        for (int i = 2000; i < 3000; i++) {
            float v = GEN_FLOAT.next();
            list.add(new FormatInfo(i, "float", v, format(v)));
        }

        for (int i = 3000; i < 4000; i++) {
            double v = GEN_DOUBLE.next();
            list.add(new FormatInfo(i, "double", v, format(v)));
        }

        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(list));
        comp.compile();

        for (FormatInfo info : list) {
            Object ret = comp.invoke("p.xyz.InnerTest", "get_" + info.id, new Object[] {});
            System.out.println("id: " + info.id + " -> " + info.value + " == " + ret);
            Verify.checkEQ(ret, info.value);
        }
    }

    private static String generate(List<FormatInfo> list) {
        var template1 = Template.make("info", (FormatInfo info) -> body(
            let("id", info.id()),
            let("type", info.type()),
            let("formatted", info.formatted()),
            """
            public static #type get_#id() { return #formatted; }
            """
        ));

        var template2 = Template.make(() -> body(
            """
            package p.xyz;
            public class InnerTest {
            """,
            list.stream().map(info -> template1.withArgs(info)).toList(),
            """
            }
            """
        ));

        return template2.withArgs().render();
    }
}
