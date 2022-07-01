/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Testing Classfile advanced transformations.
 * @run testng AdvancedTransformationsTest
 */
import helpers.ByteArrayClassLoader;
import java.util.Map;
import java.util.Set;
import jdk.classfile.ClassHierarchyResolver;
import jdk.classfile.Classfile;
import jdk.classfile.CodeModel;
import jdk.classfile.CodeTransform;
import jdk.classfile.MethodModel;
import jdk.classfile.TypeKind;
import jdk.classfile.impl.StackMapGenerator;
import jdk.classfile.transforms.ClassRemapper;
import jdk.classfile.transforms.CodeLocalsShifter;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static helpers.TestUtil.assertEmpty;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jdk.classfile.Attributes;
import jdk.classfile.ClassModel;
import jdk.classfile.CodeElement;
import jdk.classfile.FieldModel;
import jdk.classfile.Signature;
import jdk.classfile.impl.AbstractInstruction;
import jdk.classfile.impl.RawBytecodeHelper;
import jdk.classfile.impl.Util;
import jdk.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import jdk.classfile.transforms.LabelsRemapper;

public class AdvancedTransformationsTest {

    @Test
    public void testShiftLocals() throws Exception {
        try (var in = StackMapGenerator.class.getResourceAsStream("StackMapGenerator.class")) {
            var clm = Classfile.parse(in.readAllBytes());
            var remapped = Classfile.parse(clm.transform((clb, cle) -> {
                if (cle instanceof MethodModel mm) {
                    clb.transformMethod(mm, (mb, me) -> {
                        if (me instanceof CodeModel com) {
                            var shifter = new CodeLocalsShifter(mm.flags(), mm.methodTypeSymbol());
                            shifter.addLocal(TypeKind.ReferenceType);
                            shifter.addLocal(TypeKind.LongType);
                            shifter.addLocal(TypeKind.IntType);
                            shifter.addLocal(TypeKind.DoubleType);
                            mb.transformCode(com, shifter);
                        }
                        mb.with(me);
                    });
                }
                else
                    clb.with(cle);
            }));
            remapped.verify(null); //System.out::print);
        }
    }

    @Test
    public void testRemapClass() throws Exception {
        var map = Map.of(
                ConstantDescs.CD_List, ClassDesc.of("remapped.List"),
                ClassDesc.ofDescriptor(AbstractInstruction.ExceptionCatchImpl.class.descriptorString()), ClassDesc.of("remapped.ExceptionCatchImpl"),
                ClassDesc.ofDescriptor(RawBytecodeHelper.class.descriptorString()), ClassDesc.of("remapped.RemappedBytecode"),
                ClassDesc.ofDescriptor(StackMapGenerator.class.descriptorString()), ClassDesc.of("remapped.StackMapGenerator")
        );
        try (var in = StackMapGenerator.class.getResourceAsStream("StackMapGenerator.class")) {
            var clm = Classfile.parse(in.readAllBytes());
            var remapped = Classfile.parse(ClassRemapper.of(map).remapClass(clm));
            assertEmpty(remapped.verify(
                    ClassHierarchyResolver.of(Set.of(), Map.of(
                            ClassDesc.of("remapped.RemappedBytecode"), ConstantDescs.CD_Object,
                            ClassDesc.ofDescriptor(RawBytecodeHelper.class.descriptorString()), ClassDesc.of("remapped.RemappedBytecode")))
                                          .orElse(ClassHierarchyResolver.DEFAULT_CLASS_HIERARCHY_RESOLVER)
                    , null)); //System.out::print));
            remapped.fields().forEach(f -> f.findAttribute(Attributes.SIGNATURE).ifPresent(sa ->
                    verifySignature(f.fieldTypeSymbol(), sa.asTypeSignature())));
            remapped.methods().forEach(m -> m.findAttribute(Attributes.SIGNATURE).ifPresent(sa -> {
                    var md = m.methodTypeSymbol();
                    var ms = sa.asMethodSignature();
                    verifySignature(md.returnType(), ms.result());
                    var args = ms.arguments();
                    assertEquals(md.parameterCount(), args.size());
                    for (int i=0; i<args.size(); i++)
                        verifySignature(md.parameterType(i), args.get(i));
                }));
        }
    }

    private static void verifySignature(ClassDesc desc, Signature sig) {
        switch (sig) {
            case Signature.ClassTypeSig cts ->
                assertEquals(desc.descriptorString(), cts.classDesc().descriptorString());
            case Signature.ArrayTypeSig ats ->
                verifySignature(desc.componentType(), ats.componentSignature());
            case Signature.BaseTypeSig bts ->
                assertEquals(desc.descriptorString(), bts.signatureString());
            default -> {}
        }
    }

    @Test
    public void testInstrumentClass() throws Exception {
        var instrumentor = Classfile.parse(AdvancedTransformationsTest.class.getResourceAsStream("AdvancedTransformationsTest$InstrumentorClass.class").readAllBytes());
        var target = Classfile.parse(AdvancedTransformationsTest.class.getResourceAsStream("AdvancedTransformationsTest$TargetClass.class").readAllBytes());
        var instrumentedBytes = instrument(target, instrumentor, mm -> mm.methodName().stringValue().equals("instrumentedMethod"));
        assertEmpty(Classfile.parse(instrumentedBytes).verify(null)); //System.out::print));
        var targetClass = new ByteArrayClassLoader(AdvancedTransformationsTest.class.getClassLoader(), "AdvancedTransformationsTest$TargetClass", instrumentedBytes).loadClass("AdvancedTransformationsTest$TargetClass");
        assertEquals(targetClass.getDeclaredMethod("instrumentedMethod", Boolean.class).invoke(targetClass.getDeclaredConstructor().newInstance(), false), 34);
    }

    public static class InstrumentorClass {

        //matching fields are mapped
        private String privateField;
        //non-matching fields are added, however not initialized
        int instrumentorField = 8;

        //matching methods are instrumenting frames
        public int instrumentedMethod(Boolean instrumented) {
//            System.out.println("instrumentor start");
            assertEquals(privateField, "hi");
            int local = 42;
            instrumented = true;
            //matching method call is inlined
            instrumentedMethod(instrumented);
            instrumentedMethod(instrumented);
            assertEquals(local, 42);
            assertEquals(privateField, "hello");
            assertEquals(instrumentorField, 0);
            assertEquals(insHelper(), 77);
//            System.out.println("instrumentor end");
            return 34;
        }

        //non-matching methods are added
        private static int insHelper() {
            return 77;
        }
    }

    public static class TargetClass {

        private String privateField = "hi";

        public int instrumentedMethod(Boolean instrumented) {
//            System.out.println("target called");
            assertTrue(instrumented);
            anotherTargetMethod();
            privateField = "hello";
            int local = 13;
            return local;
        }

        public void anotherTargetMethod() {
//            System.out.println("anotherTargetMethod called");
        }
    }

    //synchronized copy of instrumentation code from jdk.jfr jdk.jfr.internal.instrument.JIClassInstrumentation for testing purposes
    private static byte[] instrument(ClassModel target, ClassModel instrumentor, Predicate<MethodModel> instrumentedMethodsFilter) {
        var instrumentorCodeMap = instrumentor.methods().stream()
                                              .filter(instrumentedMethodsFilter)
                                              .collect(Collectors.toMap(mm -> mm.methodName().stringValue() + mm.methodType().stringValue(), mm -> mm.code().orElse(null)));
        var targetFieldNames = target.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
        var targetMethods = target.methods().stream().map(m -> m.methodName().stringValue() + m.methodType().stringValue()).collect(Collectors.toSet());
        var instrumentorClassRemapper = ClassRemapper.of(Map.of(instrumentor.thisClass().asSymbol(), target.thisClass().asSymbol()));
        return Classfile.build(target.thisClass().asSymbol(), clb -> {
            target.forEachElement(cle -> {
                CodeModel instrumentorCodeModel;
                if (cle instanceof MethodModel mm && ((instrumentorCodeModel = instrumentorCodeMap.get(mm.methodName().stringValue() + mm.methodType().stringValue())) != null)) {
                    clb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(), mm.flags().flagsMask(),
                                   mb -> mm.forEachElement(me -> {
                        if (me instanceof CodeModel targetCodeModel) {
                            //instrumented methods are merged
                            var instrumentorLocalsShifter = new CodeLocalsShifter(mm.flags(), mm.methodTypeSymbol());
                            var instrumentorCodeRemapperAndShifter =
                                    instrumentorClassRemapper.codeTransform()
                                                             .andThen(instrumentorLocalsShifter);
                            CodeTransform invokeInterceptor
                                    = (codeBuilder, instrumentorCodeElement) -> {
                                if (instrumentorCodeElement instanceof InvokeInstruction inv
                                    && instrumentor.thisClass().asInternalName().equals(inv.owner().asInternalName())
                                    && mm.methodName().stringValue().equals(inv.name().stringValue())
                                    && mm.methodType().stringValue().equals(inv.type().stringValue())) {
                                    //store stacked arguments (in reverse order)
                                    record Arg(TypeKind tk, int slot) {}
                                    var storeStack = new LinkedList<Arg>();
                                    int slot = 0;
                                    if (!mm.flags().has(AccessFlag.STATIC)) {
                                        storeStack.add(new Arg(TypeKind.ReferenceType, slot++));
                                    }
                                    var it = Util.parameterTypes(mm.methodType().stringValue());
                                    while (it.hasNext()) {
                                        var tk = TypeKind.fromDescriptor(it.next());
                                        storeStack.add(new Arg(tk, slot));
                                        slot += tk.slotSize();
                                    }
                                    while (!storeStack.isEmpty()) {
                                        var arg = storeStack.removeLast();
                                        codeBuilder.storeInstruction(arg.tk, arg.slot);
                                    }
                                    var endLabel = codeBuilder.newLabel();
                                    //inlined target locals must be shifted based on the actual instrumentor locals shifter next free slot, relabeled and returns must be replaced with goto
                                    var sequenceTransform =
                                            instrumentorLocalsShifter.fork()
                                                                     .andThen(LabelsRemapper.remapLabels())
                                                                     .andThen((innerBuilder, shiftedRelabeledTargetCode) -> {
                                                                         if (shiftedRelabeledTargetCode.codeKind() == CodeElement.Kind.RETURN) {
                                                                             innerBuilder.goto_w(endLabel);
                                                                         }
                                                                         else
                                                                             innerBuilder.with(shiftedRelabeledTargetCode);
                                                                     })
                                                                     .andThen(CodeTransform.endHandler(b -> codeBuilder.labelBinding(endLabel)));
                                    codeBuilder.transform(targetCodeModel, sequenceTransform);
                                }
                                else
                                    codeBuilder.with(instrumentorCodeElement);
                            };
                            mb.transformCode(instrumentorCodeModel,
                                             invokeInterceptor.andThen(instrumentorCodeRemapperAndShifter));
                        }
                        else {
                            mb.with(me);
                        }
                    }));
                }
                else {
                    clb.with(cle);
                }
            });
            var remapperConsumer = instrumentorClassRemapper.classTransform().resolve(clb).consumer();
            instrumentor.forEachElement(cle -> {
                //remaining instrumentor fields and methods are remapped and moved
                if (cle instanceof FieldModel fm && !targetFieldNames.contains(fm.fieldName().stringValue())) {
                   remapperConsumer.accept(cle);
                }
                else if (cle instanceof MethodModel mm && !"<init>".equals(mm.methodName().stringValue()) && !targetMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue())) {
                   remapperConsumer.accept(cle);
                }
            });
        });
    }
}
