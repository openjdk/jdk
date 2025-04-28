/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8335935 8336588
 * @summary Testing ClassFile transformations.
 * @run junit TransformTests
 */

import java.lang.classfile.*;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    enum CodeTransformLifter {
        MB_TRANSFORM_CODE {
            @Override
            public ClassTransform transformCode(CodeTransform x) {
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
        },
        COB_TRANSFORM_CODE_MODEL {
            @Override
            public ClassTransform transformCode(CodeTransform x) {
                return (cb, ce) -> {
                    if (ce instanceof MethodModel mm) {
                        cb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel xm) {
                                mb.withCode(cob -> cob.transform(xm, x));
                            }
                            else
                                mb.with(me);
                        });
                    }
                    else
                        cb.with(ce);
                };
            }
        },
        COB_TRANSFORM_CODE_HANDLER {
            @Override
            public ClassTransform transformCode(CodeTransform x) {
                return (cb, ce) -> {
                    if (ce instanceof MethodModel mm) {
                        cb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel xm) {
                                mb.withCode(cob -> cob.transforming(x, xm::forEach));
                            }
                            else
                                mb.with(me);
                        });
                    }
                    else
                        cb.with(ce);
                };
            }
        };
        public abstract ClassTransform transformCode(CodeTransform x);
    }

    static String invoke(byte[] bytes) throws Exception {
        return (String)
                new ByteArrayClassLoader(AdaptCodeTest.class.getClassLoader(), testClassName, bytes)
                        .getMethod(testClassName, "foo")
                        .invoke(null);
    }

    @EnumSource
    @ParameterizedTest
    void testSingleTransform(CodeTransformLifter lifter) throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals("foo", invoke(bytes));
        assertEquals("foo", invoke(cc.transformClass(cm, lifter.transformCode(foo2foo))));
        assertEquals("bar", invoke(cc.transformClass(cm, lifter.transformCode(foo2bar))));
    }

    @EnumSource
    @ParameterizedTest
    void testSeq2(CodeTransformLifter lifter) throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals("foo", invoke(bytes));
        ClassTransform transform = lifter.transformCode(foo2bar.andThen(bar2baz));
        assertEquals("baz", invoke(cc.transformClass(cm, transform)));
    }

    @EnumSource
    @ParameterizedTest
    void testSeqN(CodeTransformLifter lifter) throws Exception {

        byte[] bytes = Files.readAllBytes(testClassPath);
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(bytes);

        assertEquals("foo", invoke(bytes));
        assertEquals("foo", invoke(cc.transformClass(cm, lifter.transformCode(foo2bar.andThen(bar2baz).andThen(baz2foo)))));
        assertEquals("quux", invoke(cc.transformClass(cm, lifter.transformCode(foo2bar.andThen(bar2baz).andThen(baz2quux)))));
        assertEquals("baz", invoke(cc.transformClass(cm, lifter.transformCode(foo2foo.andThen(foo2bar).andThen(bar2baz)))));
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

    private static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);

    @Test
    void testHandlerTransforms() throws Exception {
        var cf = ClassFile.of();
        byte[] bytes;
        // ClassBuilder
        bytes = cf.build(ClassDesc.of(testClassName), clb -> clb
                .transforming((clb2, cle) -> {
                              if (cle instanceof MethodModel mm) {
                                  assertEquals("bar", mm.methodName().stringValue());
                                  assertEquals(MTD_String, mm.methodTypeSymbol());
                                  assertEquals(0, mm.flags().flagsMask());
                                  clb2.withMethod("foo", MTD_String, ACC_PUBLIC | ACC_STATIC, mm::forEach);
                              } else {
                                  clb2.with(cle);
                              }
                          }, clb2 -> clb2.withMethodBody("bar", MTD_String, 0, cob -> cob
                                .loadConstant("foo")
                                .areturn())));
        assertEquals("foo", invoke(bytes));
        // MethodBuilder
        bytes = cf.build(ClassDesc.of(testClassName), clb -> clb
                .withMethod("foo", MTD_String, ACC_PUBLIC | ACC_STATIC, mb -> mb
                        .transforming((mb1, me) -> {
                            if (me instanceof CodeModel cm) {
                                var list = cm.elementList()
                                        .stream()
                                        .<Instruction>mapMulti((e, sink) -> {
                                            if (e instanceof Instruction inst) {
                                                sink.accept(inst);
                                            }
                                        }).toList();
                                assertEquals(2, list.size());
                                assertInstanceOf(ConstantInstruction.class, list.getFirst());
                                assertEquals(Opcode.ARETURN, ((Instruction) list.get(1)).opcode());

                                mb1.withCode(cob -> cob
                                        .loadConstant("baz")
                                        .areturn());
                            } else {
                                mb1.with(me);
                            }
                        }, mb1 -> mb1.withCode(cob -> cob.loadConstant("bar").areturn()))));
        assertEquals("baz", invoke(bytes));
        // CodeBuilder
        bytes = cf.build(ClassDesc.of(testClassName), clb -> clb
                .withMethodBody("foo", MTD_String, ACC_PUBLIC | ACC_STATIC, cob -> cob
                        .transforming((cob1, coe) -> {
                                    if (coe instanceof ConstantInstruction.LoadConstantInstruction inst) {
                                        assertEquals("dub", inst.constantValue());
                                        cob1.loadConstant("hot");
                                    } else {
                                        cob1.with(coe);
                                    }
                                }, cob1 -> cob1.loadConstant("dub").areturn())));
        assertEquals("hot", invoke(bytes));
    }

    public static class TestClass {
        static public String foo() {
            return "foo";
        }
    }
}
