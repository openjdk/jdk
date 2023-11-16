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
 * @summary Testing Classfile advanced transformations.
 * @run junit AdvancedTransformationsTest
 */
import helpers.ByteArrayClassLoader;
import java.util.Map;
import java.util.Set;
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.impl.StackMapGenerator;
import jdk.internal.classfile.components.ClassRemapper;
import jdk.internal.classfile.components.CodeLocalsShifter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static helpers.TestUtil.assertEmpty;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.Signature;
import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.impl.RawBytecodeHelper;
import jdk.internal.classfile.instruction.InvokeInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.components.CodeRelabeler;
import java.lang.constant.ModuleDesc;
import jdk.internal.classfile.components.ClassPrinter;
import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import jdk.internal.classfile.impl.AbstractPseudoInstruction;

class AdvancedTransformationsTest {

    @Test
    void testShiftLocals() throws Exception {
        try (var in = StackMapGenerator.class.getResourceAsStream("StackMapGenerator.class")) {
            var cc = Classfile.of();
            var clm = cc.parse(in.readAllBytes());
            var remapped = cc.parse(cc.transform(clm, (clb, cle) -> {
                if (cle instanceof MethodModel mm) {
                    clb.transformMethod(mm, (mb, me) -> {
                        if (me instanceof CodeModel com) {
                            var shifter = CodeLocalsShifter.of(mm.flags(), mm.methodTypeSymbol());
                            mb.transformCode(com, new CodeTransform() {
                                @Override
                                public void atStart(CodeBuilder builder) {
                                    builder.allocateLocal(TypeKind.ReferenceType);
                                    builder.allocateLocal(TypeKind.LongType);
                                    builder.allocateLocal(TypeKind.IntType);
                                    builder.allocateLocal(TypeKind.DoubleType);
                                }
                                @Override
                                public void accept(CodeBuilder builder, CodeElement element) {
                                    builder.with(element);
                                }
                            }.andThen(shifter));
                        } else mb.with(me);
                    });
                }
                else
                    clb.with(cle);
            }));
            remapped.verify(null);
        }
    }

    @Test
    void testRemapClass() throws Exception {
        var map = Map.of(
                ConstantDescs.CD_List, ClassDesc.of("remapped.List"),
                ClassDesc.ofDescriptor(AbstractPseudoInstruction.ExceptionCatchImpl.class.descriptorString()), ClassDesc.of("remapped.ExceptionCatchImpl"),
                ClassDesc.ofDescriptor(RawBytecodeHelper.class.descriptorString()), ClassDesc.of("remapped.RemappedBytecode"),
                ClassDesc.ofDescriptor(StackMapGenerator.class.descriptorString()), ClassDesc.of("remapped.StackMapGenerator")
        );
        try (var in = StackMapGenerator.class.getResourceAsStream("StackMapGenerator.class")) {
            var cc = Classfile.of();
            var clm = cc.parse(in.readAllBytes());
            var remapped = cc.parse(ClassRemapper.of(map).remapClass(cc, clm));
            assertEmpty(remapped.verify(
                    ClassHierarchyResolver.of(Set.of(ClassDesc.of("remapped.List")), Map.of(
                            ClassDesc.of("remapped.RemappedBytecode"), ConstantDescs.CD_Object,
                            ClassDesc.ofDescriptor(RawBytecodeHelper.class.descriptorString()), ClassDesc.of("remapped.RemappedBytecode")))
                                          .orElse(ClassHierarchyResolver.defaultResolver())
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

    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, TYPE_PARAMETER, TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface FooAnno {
    }

    @interface BarAnno {
    }

    public static class Foo {
        public static Foo fooField;
        public static Foo fooMethod(Foo arg) {
            return null;
        }

    };
    public static class Bar {};

    @FooAnno
    public static record Rec(@FooAnno Foo foo) {
        @FooAnno
        public Rec(Foo foo) {
            this.foo = new @FooAnno Foo();
            Foo local @FooAnno [] = new Foo @FooAnno [0];
            Foo.fooField = foo;
            Foo.fooMethod(foo);
        }
    }

    @Test
    void testRemapModule() throws Exception {
        var foo = ClassDesc.ofDescriptor(Foo.class.descriptorString());
        var bar = ClassDesc.ofDescriptor(Bar.class.descriptorString());
        var cc = Classfile.of();
        var ma = cc.parse(
                ClassRemapper.of(Map.of(foo, bar)).remapClass(
                        cc,
                        cc.parse(
                                cc.buildModule(
                                        ModuleAttribute.of(ModuleDesc.of("MyModule"), mab ->
                                                mab.uses(foo).provides(foo, foo)))))).findAttribute(Attributes.MODULE).get();
        assertEquals(ma.uses().get(0).asSymbol(), bar);
        var provides = ma.provides().get(0);
        assertEquals(provides.provides().asSymbol(), bar);
        assertEquals(provides.providesWith().get(0).asSymbol(), bar);
    }

    @Test
    void testRemapDetails() throws Exception {
        var foo = ClassDesc.ofDescriptor(Foo.class.descriptorString());
        var bar = ClassDesc.ofDescriptor(Bar.class.descriptorString());
        var fooAnno = ClassDesc.ofDescriptor(FooAnno.class.descriptorString());
        var barAnno = ClassDesc.ofDescriptor(BarAnno.class.descriptorString());
        var rec = ClassDesc.ofDescriptor(Rec.class.descriptorString());
        var cc = Classfile.of();
        var remapped = cc.parse(
                ClassRemapper.of(Map.of(foo, bar, fooAnno, barAnno)).remapClass(
                        cc,
                        cc.parse(
                                Rec.class.getResourceAsStream(Rec.class.getName() + ".class")
                                        .readAllBytes())));
        var sb = new StringBuilder();
        ClassPrinter.toYaml(remapped, ClassPrinter.Verbosity.TRACE_ALL, sb::append);
        String out = sb.toString();
        assertContains(out,
                "annotation class: LAdvancedTransformationsTest$BarAnno;",
                "type: LAdvancedTransformationsTest$Bar;",
                "inner class: AdvancedTransformationsTest$Bar",
                "inner class: AdvancedTransformationsTest$BarAnno",
                "field type: LAdvancedTransformationsTest$Bar;",
                "method type: (LAdvancedTransformationsTest$Bar;)V",
                "stack map frame @0: {locals: [THIS, AdvancedTransformationsTest$Bar",
                "[{annotation class: LAdvancedTransformationsTest$BarAnno;",
                "INVOKESPECIAL, owner: AdvancedTransformationsTest$Bar",
                "ANEWARRAY, dimensions: 1, descriptor: AdvancedTransformationsTest$Bar",
                "PUTSTATIC, owner: AdvancedTransformationsTest$Bar, field name: fooField, field type: LAdvancedTransformationsTest$Bar;",
                "INVOKESTATIC, owner: AdvancedTransformationsTest$Bar, method name: fooMethod, method type: (LAdvancedTransformationsTest$Bar;)LAdvancedTransformationsTest$Bar",
                "method type: ()LAdvancedTransformationsTest$Bar;",
                "GETFIELD, owner: AdvancedTransformationsTest$Rec, field name: foo, field type: LAdvancedTransformationsTest$Bar;");
        assertFalse(out.contains("bootstrap method arguments indexes: []"), "bootstrap arguments lost");
    }

    private static void assertContains(String actual, String... expected) {
        for (String exp : expected)
            assertTrue(actual.contains(exp), "expected text: \"" + exp + "\" not found in:\n" + actual);
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
    void testInstrumentClass() throws Exception {
        var cc = Classfile.of();
        var instrumentor = cc.parse(AdvancedTransformationsTest.class.getResourceAsStream("AdvancedTransformationsTest$InstrumentorClass.class").readAllBytes());
        var target = cc.parse(AdvancedTransformationsTest.class.getResourceAsStream("AdvancedTransformationsTest$TargetClass.class").readAllBytes());
        var instrumentedBytes = instrument(target, instrumentor, mm -> mm.methodName().stringValue().equals("instrumentedMethod"));
        assertEmpty(cc.parse(instrumentedBytes).verify(null)); //System.out::print));
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
                                              .collect(Collectors.toMap(mm -> mm.methodName().stringValue() + mm.methodType().stringValue(), mm -> mm.code().orElseThrow()));
        var targetFieldNames = target.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
        var targetMethods = target.methods().stream().map(m -> m.methodName().stringValue() + m.methodType().stringValue()).collect(Collectors.toSet());
        var instrumentorClassRemapper = ClassRemapper.of(Map.of(instrumentor.thisClass().asSymbol(), target.thisClass().asSymbol()));
        return Classfile.of().transform(target,
                ClassTransform.transformingMethods(
                        instrumentedMethodsFilter,
                        (mb, me) -> {
                            if (me instanceof CodeModel targetCodeModel) {
                                var mm = targetCodeModel.parent().get();
                                //instrumented methods code is taken from instrumentor
                                mb.transformCode(instrumentorCodeMap.get(mm.methodName().stringValue() + mm.methodType().stringValue()),
                                        //all references to the instrumentor class are remapped to target class
                                        instrumentorClassRemapper.asCodeTransform()
                                        .andThen((codeBuilder, instrumentorCodeElement) -> {
                                            //all invocations of target methods from instrumentor are inlined
                                            if (instrumentorCodeElement instanceof InvokeInstruction inv
                                                && target.thisClass().asInternalName().equals(inv.owner().asInternalName())
                                                && mm.methodName().stringValue().equals(inv.name().stringValue())
                                                && mm.methodType().stringValue().equals(inv.type().stringValue())) {

                                                //store stacked method parameters into locals
                                                var storeStack = new ArrayDeque<StoreInstruction>();
                                                int slot = 0;
                                                if (!mm.flags().has(AccessFlag.STATIC))
                                                    storeStack.push(StoreInstruction.of(TypeKind.ReferenceType, slot++));
                                                for (var pt : mm.methodTypeSymbol().parameterList()) {
                                                    var tk = TypeKind.from(pt);
                                                    storeStack.push(StoreInstruction.of(tk, slot));
                                                    slot += tk.slotSize();
                                                }
                                                storeStack.forEach(codeBuilder::with);

                                                //inlined target locals must be shifted based on the actual instrumentor locals
                                                codeBuilder.block(inlinedBlockBuilder -> inlinedBlockBuilder
                                                        .transform(targetCodeModel, CodeLocalsShifter.of(mm.flags(), mm.methodTypeSymbol())
                                                        .andThen(CodeRelabeler.of())
                                                        .andThen((innerBuilder, shiftedTargetCode) -> {
                                                            //returns must be replaced with jump to the end of the inlined method
                                                            if (shiftedTargetCode instanceof ReturnInstruction)
                                                                innerBuilder.goto_(inlinedBlockBuilder.breakLabel());
                                                            else
                                                                innerBuilder.with(shiftedTargetCode);
                                                        })));
                                            } else
                                                codeBuilder.with(instrumentorCodeElement);
                                        }));
                            } else
                                mb.with(me);
                        })
                .andThen(ClassTransform.endHandler(clb ->
                    //remaining instrumentor fields and methods are injected at the end
                    clb.transform(instrumentor,
                            ClassTransform.dropping(cle ->
                                    !(cle instanceof FieldModel fm
                                            && !targetFieldNames.contains(fm.fieldName().stringValue()))
                                    && !(cle instanceof MethodModel mm
                                            && !"<init>".equals(mm.methodName().stringValue())
                                            && !targetMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue())))
                            //and instrumentor class references remapped to target class
                            .andThen(instrumentorClassRemapper)))));
    }
}
