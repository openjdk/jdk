/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8336010 8336588
 * @summary Testing ClassFile transformations.
 * @run junit TransformTests
 */

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import helpers.ByteArrayClassLoader;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
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
        assertEquals(invoke(cc.transformClass(cm, transformCode(foo2foo))), "foo");
        assertEquals(invoke(cc.transformClass(cm, transformCode(foo2bar))), "bar");
    }

    @Test
    void testSeq2() throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals(invoke(bytes), "foo");
        ClassTransform transform = transformCode(foo2bar.andThen(bar2baz));
        assertEquals(invoke(cc.transformClass(cm, transform)), "baz");
    }

    @Test
    void testSeqN() throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals(invoke(bytes), "foo");
        assertEquals(invoke(cc.transformClass(cm, transformCode(foo2bar.andThen(bar2baz).andThen(baz2foo)))), "foo");
        assertEquals(invoke(cc.transformClass(cm, transformCode(foo2bar.andThen(bar2baz).andThen(baz2quux)))), "quux");
        assertEquals(invoke(cc.transformClass(cm, transformCode(foo2foo.andThen(foo2bar).andThen(bar2baz)))), "baz");
    }

    /**
     * Test to ensure class elements, such as field and
     * methods defined with transform/with, are visible
     * to next transforms.
     */
    @Test
    void testClassChaining() throws Exception {
        var bytes = Files.readAllBytes(testClassPath);
        var cf = ClassFile.of();
        var cm = cf.parse(bytes);
        var otherCm = cf.parse(cf.build(ClassDesc.of("Temp"), clb -> clb
            .withMethodBody("baz", MTD_void, ACC_STATIC, CodeBuilder::return_)
            .withField("baz", CD_long, ACC_STATIC)));

        var methodBaz = otherCm.methods().getFirst();
        var fieldBaz = otherCm.fields().getFirst();

        ClassTransform transform1 = ClassTransform.endHandler(cb -> {
            ClassBuilder ret;
            ret = cb.withMethodBody("bar", MTD_void, ACC_STATIC, CodeBuilder::return_);
            assertSame(cb, ret);
            ret = cb.transformMethod(methodBaz, MethodTransform.ACCEPT_ALL);
            assertSame(cb, ret);
            ret = cb.withField("bar", CD_int, ACC_STATIC);
            assertSame(cb, ret);
            ret = cb.transformField(fieldBaz, FieldTransform.ACCEPT_ALL);
            assertSame(cb, ret);
        });

        Set<String> methodNames = new HashSet<>();
        Set<String> fieldNames = new HashSet<>();
        ClassTransform transform2 = (cb, ce) -> {
            if (ce instanceof MethodModel mm) {
                methodNames.add(mm.methodName().stringValue());
            }
            if (ce instanceof FieldModel fm) {
                fieldNames.add(fm.fieldName().stringValue());
            }
            cb.with(ce);
        };

        cf.transformClass(cm, transform1.andThen(transform2));

        assertEquals(Set.of(INIT_NAME, "foo", "bar", "baz"), methodNames);
        assertEquals(Set.of("bar", "baz"), fieldNames);
    }

    /**
     * Test to ensure method elements, such as generated
     * or transformed code, are visible to transforms.
     */
    @Test
    void testMethodChaining() throws Exception {
        var mtd = MethodTypeDesc.of(CD_String);

        var cf = ClassFile.of();

        // withCode
        var cm = cf.parse(cf.build(ClassDesc.of("Temp"), clb -> clb
            .withMethod("baz", mtd, ACC_STATIC | ACC_NATIVE, _ -> {})));

        MethodTransform transform1 = MethodTransform.endHandler(mb -> {
            var ret = mb.withCode(cob -> cob.loadConstant("foo").areturn());
            assertSame(mb, ret);
        });

        boolean[] sawWithCode = { false };
        MethodTransform transform2 = (mb, me) -> {
            if (me instanceof CodeModel) {
                sawWithCode[0] = true;
            }
            mb.with(me);
        };

        cf.transformClass(cm, ClassTransform.transformingMethods(transform1.andThen(transform2)));

        assertTrue(sawWithCode[0], "Code attribute generated not visible");

        // transformCode
        var outerCm = cf.parse(testClassPath);
        var foo = outerCm.methods().stream()
            .filter(m -> m.flags().has(AccessFlag.STATIC))
            .findFirst().orElseThrow();

        MethodTransform transform3 = MethodTransform.endHandler(mb -> {
            var ret = mb.transformCode(foo.code().orElseThrow(), CodeTransform.ACCEPT_ALL);
            assertSame(mb, ret);
        });

        boolean[] sawTransformCode = { false };
        MethodTransform transform4 = (mb, me) -> {
            if (me instanceof CodeModel) {
                sawTransformCode[0] = true;
            }
            mb.with(me);
        };

        cf.transformClass(cm, ClassTransform.transformingMethods(transform3.andThen(transform4)));

        assertTrue(sawTransformCode[0], "Code attribute transformed not visible");
    }

    /**
     * Test to ensure code elements, such as code block
     * begin and end labels, are visible to transforms.
     */
    @Test
    void testCodeChaining() throws Exception {
        var bytes = Files.readAllBytes(testClassPath);
        var cf = ClassFile.of();
        var cm = cf.parse(bytes);

        CodeTransform transform1 = new CodeTransform() {
            @Override
            public void atStart(CodeBuilder builder) {
                builder.block(bcb -> {
                    bcb.loadConstant(9876L);
                    bcb.goto_(bcb.endLabel());
                });
            }

            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                builder.with(element);
            }
        };
        Set<Label> leaveLabels = new HashSet<>();
        Set<Label> targetedLabels = new HashSet<>();
        CodeTransform transform2 = (cb, ce) -> {
            if (ce instanceof BranchInstruction bi) {
                leaveLabels.add(bi.target());
            }
            if (ce instanceof LabelTarget lt) {
                targetedLabels.add(lt.label());
            }
            cb.with(ce);
        };

        cf.transformClass(cm, ClassTransform.transformingMethods(MethodTransform
            .transformingCode(transform1.andThen(transform2))));

        leaveLabels.removeIf(targetedLabels::contains);
        assertTrue(leaveLabels.isEmpty(), () -> "Some labels are not bounded: " + leaveLabels);
    }

    @Test
    void testStateOrder() throws Exception {
        var bytes = Files.readAllBytes(testClassPath);
        var cf = ClassFile.of();
        var cm = cf.parse(bytes);

        int[] counter = {0};

        enum TransformState { START, ONGOING, ENDED }

        var ct = ClassTransform.ofStateful(() -> new ClassTransform() {
            TransformState state = TransformState.START;

            @Override
            public void atStart(ClassBuilder builder) {
                assertSame(TransformState.START, state);
                builder.withField("f" + counter[0]++, CD_int, 0);
                state = TransformState.ONGOING;
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                assertSame(TransformState.ONGOING, state);
                builder.withField("f" + counter[0]++, CD_int, 0);
                state = TransformState.ENDED;
            }

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                assertSame(TransformState.ONGOING, state);
                builder.with(element);
            }
        });

        cf.transformClass(cm, ct);
        cf.transformClass(cm, ct.andThen(ct));
        cf.transformClass(cm, ct.andThen(ct).andThen(ct));
    }

    public static class TestClass {
        static public String foo() {
            return "foo";
        }
    }
}
