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
 * @bug 8315447
 * @summary Container annotations for type annotations in lambdas should be
 *          placed on the lambda method
 * @library /test/lib
 * @run junit RepeatableInLambdaTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.Map;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatableInLambdaTest {
    static final String src = """
            import java.lang.annotation.Repeatable;
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE_USE;
            import java.util.function.Supplier;

            @Target(TYPE_USE)
            @Repeatable(AC.class)
            @interface A {
            }

            @Target(TYPE_USE)
            @interface AC {
                A[] value();
            }

            @Target(TYPE_USE)
            @interface B {}

            class Test {
                void test() {
                    Supplier<Integer> s = () -> (@A @A @B Integer) 1;
                }
            }
            """;

    @Test
    void test() {
        var codes = InMemoryJavaCompiler.compile(Map.of("Test", src));
        var bytes = codes.get("Test");
        var cf = ClassFile.of().parse(bytes);
        var lambdaMethod = cf.methods().stream().filter(mm -> mm.flags().has(AccessFlag.SYNTHETIC))
                .findFirst().orElseThrow();
        System.err.println(lambdaMethod);
        var ritva = lambdaMethod.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
        var annoList = ritva.annotations();
        assertEquals(2, annoList.size());
        assertEquals(ClassDesc.of("AC"), annoList.getFirst().annotation().classSymbol());
        assertEquals(ClassDesc.of("B"), annoList.get(1).annotation().classSymbol());
    }
}
