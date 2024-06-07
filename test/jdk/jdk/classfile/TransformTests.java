/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile transformations.
 * @run junit TransformTests
 */
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import helpers.ByteArrayClassLoader;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransformTests
 */
class TransformTests {
    static final String testClassName = "TransformTests$TestClass";
    static final Path testClassPath = Paths.get(URI.create(ArrayTest.class.getResource(testClassName + ".class").toString()));
    static CodeTransform
            foo2foo = swapLdc("foo", "foo"),
            foo2bar = swapLdc("foo", "bar"),
            bar2baz = swapLdc("bar", "baz"),
            baz2quux = swapLdc("baz", "quux"),
            baz2foo = swapLdc("baz", "foo");

    static CodeTransform swapLdc(String x, String y) {
        return (b, e) -> {
            if (e instanceof ConstantInstruction ci && ci.constantValue().equals(x)) {
                b.loadConstant(y);
            }
            else
                b.with(e);
        };
    }

    static ClassTransform transformCode(CodeTransform x) {
        return (cb, ce) -> {
            if (ce instanceof MethodModel mm) {
                cb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof CodeModel xm) {
                        mb.transformCode(xm, x);
                    }
                    else
                        mb.with(me);
                });
            }
            else
                cb.with(ce);
        };
    }

    static String invoke(byte[] bytes) throws Exception {
        return (String)
                new ByteArrayClassLoader(AdaptCodeTest.class.getClassLoader(), testClassName, bytes)
                        .getMethod(testClassName, "foo")
                        .invoke(null);
    }

    @Test
    void testSingleTransform() throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals(invoke(bytes), "foo");
        assertEquals(invoke(cc.transform(cm, transformCode(foo2foo))), "foo");
        assertEquals(invoke(cc.transform(cm, transformCode(foo2bar))), "bar");
    }

    @Test
    void testSeq2() throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals(invoke(bytes), "foo");
        ClassTransform transform = transformCode(foo2bar.andThen(bar2baz));
        assertEquals(invoke(cc.transform(cm, transform)), "baz");
    }

    @Test
    void testSeqN() throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals(invoke(bytes), "foo");
        assertEquals(invoke(cc.transform(cm, transformCode(foo2bar.andThen(bar2baz).andThen(baz2foo)))), "foo");
        assertEquals(invoke(cc.transform(cm, transformCode(foo2bar.andThen(bar2baz).andThen(baz2quux)))), "quux");
        assertEquals(invoke(cc.transform(cm, transformCode(foo2foo.andThen(foo2bar).andThen(bar2baz)))), "baz");
    }

    public static class TestClass {
        static public String foo() {
            return "foo";
        }
    }
}
