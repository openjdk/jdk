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
 * @modules java.base/jdk.internal.ref
 *          java.base/jdk.internal.classfile.impl
 * @build testdata.*
 * @run testng/othervm TestNullHostile
 *
 */

import jdk.internal.classfile.impl.*;
import org.testng.annotations.*;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.ClassFile.*;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.attribute.*;
import java.lang.classfile.components.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jdk.internal.classfile.impl.ClassPrinterImpl.Style.FLOW;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * This test makes sure that public API classes under {@link java.lang.classfile} throws NPEs whenever
 * nulls are provided. The test looks at all the public methods in all the listed classes, and injects
 * values automatically. If an API takes a reference, the test will try to inject nulls. For APIs taking
 * either reference arrays, or collections, the framework will also generate additional <em>replacements</em>
 * (e.g. other than just replacing the array, or collection with null), such as an array or collection
 * with null elements. The test can be customized by adding/removing classes to the {@link #CLASSES} array,
 * by adding/removing default mappings for standard carrier types (see {@link #DEFAULT_VALUES} or by
 * adding/removing custom replacements (see {@link #REPLACEMENT_VALUES}).
 * <p>
 */
public class TestNullHostile {

    // cuts test runtime by half
    // increment by 1 everytime you add a new default value
    private static final int DEFAULT_MAPPING_NUMBER = 133;
    private static final Set<String> OBJECT_METHODS = Stream.of(Object.class.getMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

    private static final Map<Class<?>, Object> DEFAULT_VALUES = new HashMap<>();
    private static final Map<Class<?>, Object[]> REPLACEMENT_VALUES = new HashMap<>();

    static <Z> void addDefaultMapping(Class<Z> carrier, Z value) {
        DEFAULT_VALUES.putIfAbsent(carrier, value);
    }

    @SafeVarargs
    static <Z> void addReplacements(Class<Z> carrier, Z... value) {
        REPLACEMENT_VALUES.putIfAbsent(carrier, value);
    }

    static final Class<?>[] CLASSES = new Class<?>[]{
            AccessFlags.class,
            Annotation.class,
            AnnotationElement.class,
            AnnotationValue.class,
            AnnotationValue.OfEnum.class,
            AnnotationValue.OfClass.class,
            AnnotationValue.OfString.class,
            AnnotationValue.OfDouble.class,
            AnnotationValue.OfFloat.class,
            AnnotationValue.OfLong.class,
            AnnotationValue.OfInt.class,
            AnnotationValue.OfShort.class,
            AnnotationValue.OfChar.class,
            AnnotationValue.OfByte.class,
            AnnotationValue.OfBoolean.class,
            AnnotationValue.OfArray.class,
            AnnotationValue.OfConstant.class,
            AnnotationValue.OfAnnotation.class,
            Attribute.class,
            AttributeMapper.class,
            AttributeMapper.AttributeStability.class,
            AttributedElement.class,
            Attributes.class,
            BootstrapMethodEntry.class,
            BufWriter.class,
            ClassBuilder.class,
            ClassElement.class,
            ClassFile.class,
            Option.class,
            AttributesProcessingOption.class,
            ClassFile.StackMapsOption.class,
            ClassFile.ShortJumpsOption.class,
            LineNumbersOption.class,
            DebugElementsOption.class,
            DeadLabelsOption.class,
            DeadCodeOption.class,
            ConstantPoolSharingOption.class,
            ClassHierarchyResolverOption.class,
            AttributeMapperOption.class,
            ClassFileBuilder.class,
            ClassFileElement.class,
            ClassFileTransform.class,
            ClassFileVersion.class,
            ClassHierarchyResolver.class,
            ClassHierarchyResolver.ClassHierarchyInfo.class,
            ClassModel.class,
            ClassReader.class,
            ClassSignature.class,
            ClassTransform.class,
            CodeBuilder.class,
            CodeBuilder.CatchBuilder.class,
            CodeBuilder.BlockCodeBuilder.class,
            CodeElement.class,
            CodeModel.class,
            CodeTransform.class,
            CompoundElement.class,
            CustomAttribute.class,
            FieldBuilder.class,
            FieldElement.class,
            FieldModel.class,
            FieldTransform.class,
            Instruction.class,
            Interfaces.class,
            Label.class,
            MethodBuilder.class,
            MethodElement.class,
            MethodModel.class,
            MethodSignature.class,
            MethodTransform.class,
            Opcode.class,
            Opcode.Kind.class,
            PseudoInstruction.class,
            Signature.class,
            Signature.ArrayTypeSig.class,
            Signature.BaseTypeSig.class,
            Signature.TypeArg.class,
            Signature.TypeArg.Bounded.class,
            Signature.TypeArg.Bounded.WildcardIndicator.class,
            Signature.TypeArg.Unbounded.class,
            Signature.ClassTypeSig.class,
            Signature.ThrowableSig.class,
            Signature.TypeParam.class,
            Signature.TypeVarSig.class,
            Signature.RefTypeSig.class,
            Superclass.class,
            TypeAnnotation.class,
            TypeAnnotation.TargetInfo.class,
            TypeAnnotation.TypePathComponent.class,
            TypeAnnotation.TypePathComponent.Kind.class,
            TypeAnnotation.TypeArgumentTarget.class,
            TypeAnnotation.OffsetTarget.class,
            TypeAnnotation.CatchTarget.class,
            TypeAnnotation.LocalVarTargetInfo.class,
            TypeAnnotation.LocalVarTarget.class,
            TypeAnnotation.ThrowsTarget.class,
            TypeAnnotation.FormalParameterTarget.class,
            TypeAnnotation.EmptyTarget.class,
            TypeAnnotation.TypeParameterBoundTarget.class,
            TypeAnnotation.SupertypeTarget.class,
            TypeAnnotation.TypeParameterTarget.class,
            TypeAnnotation.TargetType.class,
            TypeKind.class,
            AnnotationDefaultAttribute.class,
            BootstrapMethodsAttribute.class,
            CharacterRangeInfo.class,
            CharacterRangeTableAttribute.class,
            CodeAttribute.class,
            CompilationIDAttribute.class,
            ConstantValueAttribute.class,
            DeprecatedAttribute.class,
            EnclosingMethodAttribute.class,
            ExceptionsAttribute.class,
            InnerClassInfo.class,
            InnerClassesAttribute.class,
            LineNumberInfo.class,
            LineNumberTableAttribute.class,
            LocalVariableInfo.class,
            LocalVariableTableAttribute.class,
            LocalVariableTypeInfo.class,
            LocalVariableTypeTableAttribute.class,
            MethodParameterInfo.class,
            ModuleAttribute.class,
            ModuleAttribute.ModuleAttributeBuilder.class,
            ModuleExportInfo.class,
            ModuleHashInfo.class,
            ModuleHashesAttribute.class,
            ModuleMainClassAttribute.class,
            ModuleOpenInfo.class,
            ModulePackagesAttribute.class,
            ModuleProvideInfo.class,
            ModuleRequireInfo.class,
            ModuleResolutionAttribute.class,
            ModuleTargetAttribute.class,
            NestHostAttribute.class,
            NestMembersAttribute.class,
            PermittedSubclassesAttribute.class,
            RecordAttribute.class,
            RecordComponentInfo.class,
            RuntimeInvisibleAnnotationsAttribute.class,
            RuntimeInvisibleParameterAnnotationsAttribute.class,
            RuntimeInvisibleTypeAnnotationsAttribute.class,
            RuntimeVisibleAnnotationsAttribute.class,
            RuntimeVisibleParameterAnnotationsAttribute.class,
            RuntimeVisibleTypeAnnotationsAttribute.class,
            SignatureAttribute.class,
            SourceDebugExtensionAttribute.class,
            SourceFileAttribute.class,
            SourceIDAttribute.class,
            StackMapFrameInfo.class,
            StackMapFrameInfo.UninitializedVerificationTypeInfo.class,
            StackMapFrameInfo.ObjectVerificationTypeInfo.class,
            StackMapFrameInfo.SimpleVerificationTypeInfo.class,
            StackMapFrameInfo.VerificationTypeInfo.class,
            StackMapTableAttribute.class,
            SyntheticAttribute.class,
            UnknownAttribute.class,
            ClassPrinter.class,
            ClassPrinter.Verbosity.class,
            ClassPrinter.MapNode.class,
            ClassPrinter.ListNode.class,
            ClassPrinter.LeafNode.class,
            ClassPrinter.Node.class,
            ClassRemapper.class,
            CodeLocalsShifter.class,
            CodeRelabeler.class,
            CodeStackTracker.class,
            AnnotationConstantValueEntry.class,
            ClassEntry.class,
            ConstantDynamicEntry.class,
            ConstantPool.class,
            ConstantPoolBuilder.class,
            ConstantPoolException.class,
            ConstantValueEntry.class,
            DoubleEntry.class,
            DynamicConstantPoolEntry.class,
            FieldRefEntry.class,
            FloatEntry.class,
            IntegerEntry.class,
            InterfaceMethodRefEntry.class,
            InvokeDynamicEntry.class,
            LoadableConstantEntry.class,
            LongEntry.class,
            MemberRefEntry.class,
            MethodHandleEntry.class,
            MethodRefEntry.class,
            MethodTypeEntry.class,
            ModuleEntry.class,
            NameAndTypeEntry.class,
            PackageEntry.class,
            PoolEntry.class,
            StringEntry.class,
            Utf8Entry.class,
            ArrayLoadInstruction.class,
            ArrayStoreInstruction.class,
            BranchInstruction.class,
            CharacterRange.class,
            ConstantInstruction.class,
            ConstantInstruction.LoadConstantInstruction.class,
            ConstantInstruction.ArgumentConstantInstruction.class,
            ConstantInstruction.IntrinsicConstantInstruction.class,
            ConvertInstruction.class,
            DiscontinuedInstruction.class,
            DiscontinuedInstruction.RetInstruction.class,
            DiscontinuedInstruction.JsrInstruction.class,
            ExceptionCatch.class,
            FieldInstruction.class,
            IncrementInstruction.class,
            InvokeDynamicInstruction.class,
            LabelTarget.class,
            LineNumber.class,
            LoadInstruction.class,
            LocalVariable.class,
            LocalVariableType.class,
            LookupSwitchInstruction.class,
            MonitorInstruction.class,
            NewMultiArrayInstruction.class,
            NewObjectInstruction.class,
            NewPrimitiveArrayInstruction.class,
            NewReferenceArrayInstruction.class,
            NopInstruction.class,
            OperatorInstruction.class,
            ReturnInstruction.class,
            StackInstruction.class,
            StoreInstruction.class,
            SwitchCase.class,
            TableSwitchInstruction.class,
            ThrowInstruction.class,
            TypeCheckInstruction.class
    };

    private static final Set<String> EXCLUDE_LIST = Set.of(
            //the implementation of this method in CatchBuilderImpl handles nulls, is this fine?
            "java.lang.classfile.CodeBuilder$CatchBuilder/catching(java.lang.constant.ClassDesc,java.util.function.Consumer)/0/0",

            // making this method null-hostile causes a BootstapMethodError during the the build
            //        java.lang.BootstrapMethodError: bootstrap method initialization exception
            //        Caused by: java.lang.invoke.LambdaConversionException: Exception instantiating lambda object
            "java.lang.classfile.CodeBuilder/loadConstant(java.lang.constant.ConstantDesc)/0/0",
            "java.lang.classfile.CodeBuilder$BlockCodeBuilder/loadConstant(java.lang.constant.ConstantDesc)/0/0",



            //todo  remove from exclude list later, this breaks corpus and advancedtransformation test
            "java.lang.classfile.ClassHierarchyResolver$ClassHierarchyInfo/ofClass(java.lang.constant.ClassDesc)/0/0",
            "java.lang.classfile.attribute.ModuleAttribute$ModuleAttributeBuilder/requires(java.lang.constant.ModuleDesc,int,java.lang.String)/2/0",
            "java.lang.classfile.attribute.ModuleAttribute/of(java.lang.classfile.constantpool.ModuleEntry,int,java.lang.classfile.constantpool.Utf8Entry,java.util.Collection,java.util.Collection,java.util.Collection,java.util.Collection,java.util.Collection)/2/0",
            "java.lang.classfile.attribute.ModuleRequireInfo/of(java.lang.classfile.constantpool.ModuleEntry,int,java.lang.classfile.constantpool.Utf8Entry)/2/0",
            "java.lang.classfile.AttributeMapper/readAttribute(java.lang.classfile.AttributedElement,java.lang.classfile.ClassReader,int)/0/0",

            // todo making this method null-hostile breaks a few tests
            "java.lang.classfile.AttributeMapper/readAttribute(java.lang.classfile.AttributedElement,java.lang.classfile.ClassReader,int)/1/0",

            //todo - removing this breaks jdk/java/lang/invoke/MethodHandleProxies/WithSecurityManagerTest.java
            "java.lang.classfile.ClassHierarchyResolver/ofClassLoading(java.lang.ClassLoader)/0/0",

            //todo revisit these
            "java.lang.classfile.ClassFileTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.ClassFileTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.ClassTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",

            //todo- can we add stronger checks here?
            "java.lang.classfile.components.CodeStackTracker/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.components.CodeStackTracker/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.CodeTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.CodeTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.FieldTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.FieldTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.components.CodeLocalsShifter/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.components.CodeLocalsShifter/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.components.CodeRelabeler/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",
            "java.lang.classfile.components.CodeRelabeler/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.MethodTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/0/0",
            "java.lang.classfile.MethodTransform/accept(java.lang.classfile.ClassFileBuilder,java.lang.classfile.ClassFileElement)/1/0",

            // implementation calls these method with null
            "java.lang.classfile.Signature$ClassTypeSig/of(java.lang.classfile.Signature$ClassTypeSig,java.lang.constant.ClassDesc,java.lang.classfile.Signature$TypeArg[])/0/0",
            "java.lang.classfile.Signature$ClassTypeSig/of(java.lang.classfile.Signature$ClassTypeSig,java.lang.String,java.lang.classfile.Signature$TypeArg[])/0/0",
            "java.lang.classfile.Signature$TypeParam/of(java.lang.String,java.lang.classfile.Signature$RefTypeSig,java.lang.classfile.Signature$RefTypeSig[])/1/0",
            "java.lang.classfile.BufWriter/writeIndexOrZero(java.lang.classfile.constantpool.PoolEntry)/0/0",

            // these are inherited from Map and other superclasses
            "java.lang.classfile.components.ClassPrinter$MapNode/remove(java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/replace(java.lang.Object,java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/replace(java.lang.Object,java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/replace(java.lang.Object,java.lang.Object,java.lang.Object)/2/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/replace(java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/replace(java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/remove(java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/get(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/put(java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/put(java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/merge(java.lang.Object,java.lang.Object,java.util.function.BiFunction)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/putAll(java.util.Map)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/putIfAbsent(java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/remove(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/sort(java.util.Comparator)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/indexOf(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/lastIndexOf(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/add(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/add(int,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/toArray(java.lang.Object[])/0/1",
            "java.lang.classfile.components.ClassPrinter$ListNode/contains(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/addAll(int,java.util.Collection)/1/1",
            "java.lang.classfile.components.ClassPrinter$ListNode/addAll(java.util.Collection)/0/1",
            "java.lang.classfile.components.ClassPrinter$MapNode/remove(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/putIfAbsent(java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/compute(java.lang.Object,java.util.function.BiFunction)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/containsKey(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/computeIfAbsent(java.lang.Object,java.util.function.Function)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/containsValue(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/getOrDefault(java.lang.Object,java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/getOrDefault(java.lang.Object,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$MapNode/computeIfPresent(java.lang.Object,java.util.function.BiFunction)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/set(int,java.lang.Object)/1/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/addFirst(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/addLast(java.lang.Object)/0/0",
            "java.lang.classfile.components.ClassPrinter$ListNode/removeAll(java.util.Collection)/0/1",
            "java.lang.classfile.components.ClassPrinter$ListNode/retainAll(java.util.Collection)/0/1",
            "java.lang.classfile.components.ClassPrinter$ListNode/containsAll(java.util.Collection)/0/1",

            //valueOf
            "java.lang.classfile.ClassFile$AttributesProcessingOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$ShortJumpsOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$LineNumbersOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$StackMapsOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$DebugElementsOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$DeadLabelsOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$DeadCodeOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.ClassFile$ConstantPoolSharingOption/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.TypeKind/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.attribute.StackMapFrameInfo$SimpleVerificationTypeInfo/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.constantpool.ConstantPoolException/initCause(java.lang.Throwable)/0/0",
            "java.lang.classfile.components.ClassPrinter$Verbosity/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.Signature$TypeArg$Bounded$WildcardIndicator/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.TypeAnnotation$TypePathComponent$Kind/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.TypeAnnotation$TargetType/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.Opcode/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.Opcode$Kind/valueOf(java.lang.Class,java.lang.String)/1/0",
            "java.lang.classfile.AttributeMapper$AttributeStability/valueOf(java.lang.Class,java.lang.String)/1/0"
    );

    static {
        addDefaultMapping(char.class, (char) 0);
        addDefaultMapping(byte.class, (byte) 0);
        addDefaultMapping(short.class, (short) 0);
        addDefaultMapping(int.class, 0);
        addDefaultMapping(float.class, 0f);
        addDefaultMapping(long.class, 0L);
        addDefaultMapping(double.class, 0d);
        addDefaultMapping(boolean.class, true);
        addDefaultMapping(Enum.class, ClassFileFormatVersion.RELEASE_24);
        addDefaultMapping(Path.class, Path.of("nonExistent"));
        addDefaultMapping(UnaryOperator.class, UnaryOperator.identity());
        addDefaultMapping(String.class, "Hello!");
        addDefaultMapping(Class.class, String.class);
        addDefaultMapping(Object.class, new Object());
        addDefaultMapping(List.class, List.of());
        addDefaultMapping(Consumer.class, _ -> {});
        addDefaultMapping(Supplier.class, () -> null);
        addDefaultMapping(ClassLoader.class, TestNullHostile.class.getClassLoader());
        addDefaultMapping(Function.class, Function.identity());
        addDefaultMapping(CharSequence.class, "c");
        addDefaultMapping(Collection.class, new ArrayList<>());
        addDefaultMapping(Optional.class, Optional.of(5));
        addDefaultMapping(Map.class, Map.of());
        addDefaultMapping(MethodHandles.Lookup.class, MethodHandles.publicLookup());
        addDefaultMapping(Predicate.class, Predicate.not(_ -> false));
        BiFunction<Object, Object, Object> func = (_, _) -> 0;
        addDefaultMapping(BiFunction.class, func);
        addDefaultMapping(BiConsumer.class, (_, _) -> {});
        addDefaultMapping(IntFunction.class, _ -> 0);
        addDefaultMapping(PrintWriter.class, new PrintWriter(System.out,true));
        addDefaultMapping(PrintStream.class, new PrintStream(System.out,true));
    }

    static {
        addReplacements(Collection.class, null, Stream.of(new Object[]{null}).collect(Collectors.toList()));
        addReplacements(List.class, null, Stream.of(new Object[]{null}).collect(Collectors.toList()));
        addReplacements(Set.class, null, Stream.of(new Object[]{null}).collect(Collectors.toSet()));
    }

    @BeforeSuite
    public void TestNPEs() throws IOException, URISyntaxException {
        FileSystem fs = FileSystems.getFileSystem(new URI("jrt:/"));
        var all = findAllClassFiles();
        int i = 0;
        while (DEFAULT_VALUES.size() <= DEFAULT_MAPPING_NUMBER && i < all.size() - 1) {
            var b = Files.readAllBytes(fs.getPath(all.get(i)));
            i++;
            try {
                populateClassFileMappings(b);
            } catch (Exception e) {
                //do nothing - some complains about Unmatched bit position 0x in some inner final classes
            }
        }
    }

    @Test(dataProvider = "cases")
    public void testNulls(String testName, @NoInjection Method meth, Object receiver, Object[] args) {
        try {
            meth.invoke(receiver, args);
            fail("Method invocation completed normally");
        } catch (InvocationTargetException ex) {
            Class<?> cause = ex.getCause().getClass();
            assertEquals(cause, NullPointerException.class, "got " + cause.getName() + " - expected NullPointerException");
        } catch (Throwable ex) {
            fail("Unexpected exception: " + ex);
        }
    }

    @DataProvider(name = "cases")
    static Iterator<Object[]> cases() {
        List<Object[]> cases = new ArrayList<>();
        for (Class<?> clazz : CLASSES) {
            for (Method m : clazz.getMethods()) {
                if (OBJECT_METHODS.contains(m.getName())) continue;
                boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                List<Integer> refIndices = new ArrayList<>();
                for (int i = 0; i < m.getParameterCount(); i++) {
                    Class<?> param = m.getParameterTypes()[i];
                    if (!param.isPrimitive()) {
                        refIndices.add(i);
                    }
                }
                for (int i : refIndices) {
                    Object[] replacements = replacements(m.getParameterTypes()[i]);
                    for (int r = 0; r < replacements.length; r++) {
                        String testName = clazz.getName() + "/" + shortSig(m) + "/" + i + "/" + r;
                        if (EXCLUDE_LIST.contains(testName)) continue;
                        Object[] args = new Object[m.getParameterCount()];
                        for (int j = 0; j < args.length; j++) {
                            args[j] = defaultValue(m.getParameterTypes()[j]);
                        }
                        args[i] = replacements[r];
                        Object receiver = isStatic ? null : defaultValue(clazz);
                        cases.add(new Object[]{testName, m, receiver, args});
                    }
                }
            }
        }
        return cases.iterator();
    }

    static String shortSig(Method m) {
        StringJoiner sj = new StringJoiner(",", m.getName() + "(", ")");
        for (Class<?> parameterType : m.getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    static Object defaultValue(Class<?> carrier) {
        if (carrier.isArray()) {
            return Array.newInstance(carrier.componentType(), 0);
        }
        Object value = DEFAULT_VALUES.get(carrier);
        if (value == null) {
            throw new UnsupportedOperationException(carrier.getName());
        }
        return value;
    }

    static Object[] replacements(Class<?> carrier) {
        if (carrier.isArray() && !carrier.getComponentType().isPrimitive()) {
            Object arr = Array.newInstance(carrier.componentType(), 1);
            Array.set(arr, 0, null);
            return new Object[]{null, arr};
        }
        return REPLACEMENT_VALUES.getOrDefault(carrier, new Object[]{null});
    }

    public static List<String> findAllClassFiles() throws IOException, URISyntaxException {
        FileSystem fs = FileSystems.getFileSystem(new URI("jrt:/"));
        Path dir = fs.getPath("/modules");
        try (final Stream<Path> paths = Files.walk(dir)) {
            // each path is in the form: /modules/<modname>/<pkg>/<pkg>/.../name.class
            return paths
                    .filter((path) -> path.getNameCount() > 2)
                    .map(Path::toString)
                    .filter((name) -> name.endsWith(".class"))
                    .collect(Collectors.toList());
        }
    }

    private static void populateClassFileMappings(byte[] classFileBytes) throws IOException {

        ClassModel cm = ClassFile.of().parse(classFileBytes);
        addDefaultMapping(ClassFile.class, ClassFile.of());
        addDefaultMapping(ConstantPool.class, cm.constantPool());
        addDefaultMapping(ClassReader.class, (ClassReader) cm.constantPool());

        var node = ClassPrinter.toTree(cm, ClassPrinter.Verbosity.TRACE_ALL);
        addDefaultMapping(ClassPrinter.MapNode.class, node);
        addDefaultMapping(ClassPrinter.Node.class, node);
        addDefaultMapping(ClassPrinter.Verbosity.class, ClassPrinter.Verbosity.MEMBERS_ONLY);
        addDefaultMapping(TypeKind.class, TypeKind.BOOLEAN);
        addDefaultMapping(ConstantPoolBuilder.class, ConstantPoolBuilder.of(cm));
        addDefaultMapping(ClassModel.class, cm);
        addDefaultMapping(ClassRemapper.class, ClassRemapper.of(Map.of()));
        addDefaultMapping(ClassHierarchyResolver.class, ClassHierarchyResolver.defaultResolver());
        addDefaultMapping(ClassTransform.class, ClassFileBuilder::with);
        addDefaultMapping(MethodTransform.class, (_, _) -> {});
        addDefaultMapping(FieldTransform.class, (_, _) -> {});
        addDefaultMapping(ClassPrinter.LeafNode.class, new ClassPrinterImpl.LeafNodeImpl(ConstantDescs.CD_Class, ConstantDescs.INIT_NAME));
        addDefaultMapping(ClassFileTransform.class, (ClassTransform) (_, _) -> {});
        addDefaultMapping(ConstantDesc.class, ConstantDescs.NULL);
        addDefaultMapping(CodeStackTracker.class, CodeStackTracker.of());
        addDefaultMapping(CodeRelabeler.class, CodeRelabeler.of());
        addDefaultMapping(CodeTransform.class, (_, _) -> {});
        addDefaultMapping(AttributesProcessingOption.class, ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES);
        addDefaultMapping(TypeAnnotation.TargetInfo.class, TypeAnnotation.TargetInfo.ofField());
        addDefaultMapping(StackMapsOption.class, ClassFile.StackMapsOption.DROP_STACK_MAPS);
        addDefaultMapping(ShortJumpsOption.class, ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS);
        addDefaultMapping(LineNumbersOption.class, ClassFile.LineNumbersOption.PASS_LINE_NUMBERS);
        addDefaultMapping(DebugElementsOption.class, ClassFile.DebugElementsOption.PASS_DEBUG);
        addDefaultMapping(DeadLabelsOption.class, ClassFile.DeadLabelsOption.FAIL_ON_DEAD_LABELS);
        addDefaultMapping(DeadCodeOption.class, ClassFile.DeadCodeOption.PATCH_DEAD_CODE);
        addDefaultMapping(TypeAnnotation.TargetInfo.class, TypeAnnotation.TargetInfo.ofField());
        addDefaultMapping(TypeAnnotation.TargetType.class, TypeAnnotation.TargetInfo.ofField().targetType());
        addDefaultMapping(TypeAnnotation.TypePathComponent.Kind.class, TypeAnnotation.TypePathComponent.Kind.INNER_TYPE);
        addDefaultMapping(ConstantPoolSharingOption.class, ClassFile.ConstantPoolSharingOption.SHARED_POOL);
        addDefaultMapping(PackageDesc.class, PackageDesc.of("java.lang.classfile"));
        addDefaultMapping(ModuleDesc.class, ModuleDesc.of("java.base"));
        addDefaultMapping(Signature.ClassTypeSig.class, Signature.ClassTypeSig.of(ClassDesc.of("java.util.Iterator")));
        addDefaultMapping(Signature.TypeArg.Bounded.WildcardIndicator.class, Signature.TypeArg.Bounded.WildcardIndicator.NONE);
        addDefaultMapping(Signature.RefTypeSig.class, Signature.ClassTypeSig.of(ClassDesc.of("java.util.Iterator")));
        addDefaultMapping(StackMapFrameInfo.SimpleVerificationTypeInfo.class, StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_DOUBLE);
        addDefaultMapping(MethodSignature.class, MethodSignature.parseFrom("<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)[TT;^Ljava/lang/IOException;^Ljava/lang/IllegalAccessError;"));
        addDefaultMapping(ClassSignature.class, ClassSignature.parseFrom("Ljava/util/LinkedHashMap<TK;TV;>.LinkedHashIterator;Ljava/util/Iterator<Ljava/util/Map$Entry<TK;TV;>;>;"));
        addDefaultMapping(Opcode.Kind.class, Opcode.Kind.CONSTANT);

        cm.flags().flags().stream().findFirst().ifPresent(flag -> addDefaultMapping(AccessFlag.class, flag));
        try {
            Runnable runnable = () -> ClassFile.of().parse(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    0, 0, 0, 0, 0, 2, ClassFile.TAG_METHODREF, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}).thisClass();
            runnable.run();
        } catch (ConstantPoolException e) {
            addDefaultMapping(ConstantPoolException.class, e);
            addDefaultMapping(Throwable.class, e);
        }

        try {
            addDefaultMapping(BootstrapMethodEntry.class, cm.constantPool().bootstrapMethodEntry(0));
            addDefaultMapping(LoadableConstantEntry.class, cm.constantPool().bootstrapMethodEntry(0).arguments().getFirst());
        } catch (Exception e) {
            // skip
        }

        for (ClassFileElement ce : cm) {
            if (ce instanceof AttributedElement ae) {
                addDefaultMapping(ClassFileElement.class, ce);
                addDefaultMapping(AttributedElement.class, ae);

            }
            if (Objects.requireNonNull(ce) instanceof CompoundElement<?> comp) {
                addDefaultMapping(CompoundElement.class, comp);
            }
        }

        ModuleDesc modName = ModuleDesc.of("some.module.structure");
        String modVsn = "ab75";
        ModuleDesc require1 = ModuleDesc.of("1require.some.mod");
        String vsn1 = "1the.best.version";
        ModuleDesc require2 = ModuleDesc.of("2require.some.mod");
        String vsn2 = "2the.best.version";
        ModuleDesc[] et1 = new ModuleDesc[]{ModuleDesc.of("1t1"), ModuleDesc.of("1t2")};
        ModuleDesc[] et2 = new ModuleDesc[]{ModuleDesc.of("2t1")};
        ModuleDesc[] et3 = new ModuleDesc[]{ModuleDesc.of("3t1"), ModuleDesc.of("3t2"), ModuleDesc.of("3t3")};
        ModuleDesc[] ot3 = new ModuleDesc[]{ModuleDesc.of("t1"), ModuleDesc.of("t2")};


        var classFile = ClassFile.of();
        byte[] modInfo = classFile.buildModule(
                ModuleAttribute.of(modName, mb -> {
                    addDefaultMapping(ModuleAttribute.ModuleAttributeBuilder.class, mb);
                    mb.moduleVersion(modVsn)
                            .requires(require1, 77, vsn1)
                            .requires(require2, 99, vsn2)
                            .exports(PackageDesc.of("0"), 0, et1)
                            .exports(PackageDesc.of("1"), 1, et2)
                            .exports(PackageDesc.of("2"), 2, et3)
                            .exports(PackageDesc.of("3"), 3)
                            .exports(PackageDesc.of("4"), 4)
                            .opens(PackageDesc.of("o0"), 0)
                            .opens(PackageDesc.of("o1"), 1)
                            .opens(PackageDesc.of("o2"), 2, ot3)
                            .uses(ClassDesc.of("some.Service"))
                            .uses(ClassDesc.of("another.Service"))
                            .provides(ClassDesc.of("some.nice.Feature"), ClassDesc.of("impl"), ClassDesc.of("another.impl"));
                }),
                clb -> clb.with(ModuleMainClassAttribute.of(ClassDesc.of("main.Class")))
                        .with(ModulePackagesAttribute.ofNames(PackageDesc.of("foo.bar.baz"), PackageDesc.of("quux")))
                        .with(ModuleMainClassAttribute.of(ClassDesc.of("overwritten.main.Class")))
        );

        final ClassModel moduleModel = classFile.parse(modInfo);
        final ModuleAttribute moduleAttribute = ((ModuleAttribute) moduleModel.attributes().stream()
                .filter(a -> a.attributeMapper() == Attributes.module())
                .findFirst()
                .orElseThrow());
        moduleModel.attributes().stream()
                .filter(a -> a.attributeMapper() == Attributes.module())
                .forEach(_ -> addDefaultMapping(AttributeMapper.AttributeStability.class, moduleAttribute.attributeMapper().stability()));

        addDefaultMapping(ModuleAttribute.class, moduleAttribute);
        addDefaultMapping(ModuleOpenInfo.class, moduleAttribute.opens().getFirst());
        addDefaultMapping(ModuleRequireInfo.class, moduleAttribute.requires().getFirst());
        addDefaultMapping(ModuleExportInfo.class, moduleAttribute.exports().getFirst());
        addDefaultMapping(ModuleProvideInfo.class, moduleAttribute.provides().getFirst());


        ClassFile.of().transformClass(cm, (cb, ce) -> {
            addDefaultMapping(ClassBuilder.class, cb);
            addDefaultMapping(ClassFileBuilder.class, cb);
            switch (ce) {
                case MethodModel m -> cb.transformMethod(m, (mb, me) -> {
                    addDefaultMapping(MethodBuilder.class, mb);
                    if (!(me instanceof RuntimeVisibleAnnotationsAttribute || me instanceof RuntimeInvisibleAnnotationsAttribute))
                        mb.with(me);
                });
                case FieldModel f -> cb.transformField(f, (fb, fe) -> {
                    addDefaultMapping(FieldBuilder.class, fb);
                    if (!(fe instanceof RuntimeVisibleAnnotationsAttribute || fe instanceof RuntimeInvisibleAnnotationsAttribute))
                        fb.with(fe);
                });
                default -> cb.with(ce);
            }
        });


        var annotation = cm.findAttribute(Attributes.runtimeVisibleAnnotations());
        if (annotation.isPresent()) {
            addDefaultMapping(Annotation.class, annotation.get().annotations().getFirst());
            addDefaultMapping(ClassPrinter.ListNode.class, new ClassPrinterImpl.ListNodeImpl(FLOW, "array", cm.findAttribute(Attributes.runtimeVisibleAnnotations()).stream().map(
                    _ -> new ClassPrinterImpl.MapNodeImpl(FLOW, "value"))));
        }

        for (var cpe : cm.constantPool()) {
            switch (cpe) {
                case DoubleEntry de -> {
                    addDefaultMapping(DoubleEntry.class, de);
                    addDefaultMapping(ConstantValueEntry.class, de);
                }
                case FloatEntry fe -> addDefaultMapping(FloatEntry.class, fe);
                case IntegerEntry ie -> {
                    addDefaultMapping(IntegerEntry.class, ie);
                    addDefaultMapping(PoolEntry.class, ie);
                }
                case LongEntry le -> addDefaultMapping(LongEntry.class, le);
                case Utf8Entry ue -> addDefaultMapping(Utf8Entry.class, ue);
                case ConstantDynamicEntry cde -> addDefaultMapping(ConstantDynamicEntry.class, cde);
                case InvokeDynamicEntry ide -> addDefaultMapping(InvokeDynamicEntry.class, ide);
                case ClassEntry ce -> addDefaultMapping(ClassEntry.class, ce);
                case MethodHandleEntry mhe -> {
                    addDefaultMapping(MethodHandleEntry.class, mhe);
                    addDefaultMapping(MemberRefEntry.class, mhe.reference());
                    addDefaultMapping(DirectMethodHandleDesc.class, mhe.asSymbol());
                    addDefaultMapping(DynamicConstantDesc.class, DynamicConstantDesc.of(mhe.asSymbol()));
                    addDefaultMapping(DynamicCallSiteDesc.class, DynamicCallSiteDesc.of(mhe.asSymbol(), cm.methods().getFirst().methodTypeSymbol()));
                }
                case FieldRefEntry fre -> addDefaultMapping(FieldRefEntry.class, fre);
                case InterfaceMethodRefEntry imre -> addDefaultMapping(InterfaceMethodRefEntry.class, imre);
                case MethodRefEntry mre -> addDefaultMapping(MethodRefEntry.class, mre);
                case ModuleEntry me -> addDefaultMapping(ModuleEntry.class, me);
                case NameAndTypeEntry nate -> addDefaultMapping(NameAndTypeEntry.class, nate);
                case PackageEntry pe -> addDefaultMapping(PackageEntry.class, pe);
                default -> {}
            }
        }
        for (ClassElement ce : cm) {
            switch (ce) {
                case Attribute<?> a -> {
                    addDefaultMapping(AttributeMapper.class, a.attributeMapper());
                    addDefaultMapping(Attribute.class, a);
                }
                case AccessFlags af -> addDefaultMapping(AccessFlags.class, af);
                case FieldModel fm -> {
                    addDefaultMapping(FieldModel.class, fm);
                    addDefaultMapping(Utf8Entry.class, fm.fieldName());
                    addDefaultMapping(ClassDesc.class, fm.fieldTypeSymbol());
                    addDefaultMapping(Signature.class, Signature.of(fm.fieldTypeSymbol()));
                    addDefaultMapping(TypeDescriptor.OfField.class, fm.fieldTypeSymbol());
                    addDefaultMapping(AnnotationValue.class, AnnotationValue.of(fm.fieldTypeSymbol()));
                }
                case MethodModel mm -> {
                    var shifter = CodeLocalsShifter.of(mm.flags(), mm.methodTypeSymbol());
                    addDefaultMapping(CodeLocalsShifter.class, shifter);
                    addDefaultMapping(MethodTypeDesc.class, mm.methodTypeSymbol());
                    addDefaultMapping(MethodModel.class, mm);
                    for (MethodElement me : mm) {
                        if (me instanceof CodeModel xm) {
                            addDefaultMapping(CodeModel.class, xm);
                            addDefaultMapping(CodeAttribute.class, (CodeAttribute) xm);
                            for (CodeElement e : xm) {
                                switch (e) {
                                    case BranchInstruction ii -> addDefaultMapping(Label.class, ii.target());
                                    case NewReferenceArrayInstruction c -> addDefaultMapping(Opcode.class, c.opcode());
                                    default -> {}
                                }
                            }
                        }
                    }
                }
                default -> {}
            }
        }

        for (Attribute<?> a : cm.attributes()) {
            switch (a) {
                case ModuleAttribute attr -> addDefaultMapping(ModuleAttribute.class, attr);
                case RuntimeVisibleTypeAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeVisibleTypeAnnotationsAttribute.class, attr);
                case RuntimeInvisibleTypeAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeInvisibleTypeAnnotationsAttribute.class, attr);
                case RuntimeVisibleParameterAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeVisibleParameterAnnotationsAttribute.class, attr);
                case RuntimeInvisibleParameterAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeInvisibleParameterAnnotationsAttribute.class, attr);
                case SourceDebugExtensionAttribute attr -> addDefaultMapping(SourceDebugExtensionAttribute.class, attr);
                case SourceIDAttribute attr -> addDefaultMapping(SourceIDAttribute.class, attr);
                default -> {}
            }
        }

        ClassFile.of().build(ClassDesc.of("C"), cb -> cb.withMethod("main", MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_String.arrayType()),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, mb -> mb.withCode(xb -> {
                    int stringSlot = xb.allocateLocal(TypeKind.REFERENCE);
                    xb.loadConstant("S");
                    xb.astore(stringSlot);
                    xb.trying(tb -> {
                        tb.aload(0);
                        tb.loadConstant(0);
                        // IndexOutOfBoundsException
                        tb.aaload();
                        // NullPointerException
                        tb.invokevirtual(ConstantDescs.CD_String, "toString", MethodType.methodType(String.class).describeConstable().get());
                        tb.astore(stringSlot);
                    }, catchBuilder -> {
                        catchBuilder.catching(IndexOutOfBoundsException.class.describeConstable().get(), tb -> {
                            tb.pop();
                            tb.ldc("IndexOutOfBoundsException");
                            tb.areturn();
                        }).catchingAll(tb -> {
                            tb.pop();
                            tb.ldc("any");
                            tb.areturn();
                        });
                        addDefaultMapping(CodeBuilder.CatchBuilder.class, catchBuilder);
                    });
                    xb.aload(stringSlot);
                    xb.areturn();
                })));


        ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(cm.thisClass().asSymbol(), clb -> {
            for (var cle : cm) {
                switch (cle) {
                    case AccessFlags af -> clb.withFlags(af.flagsMask());
                    case Interfaces i ->
                            clb.withInterfaceSymbols(i.interfaces().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                    case ClassFileVersion v -> clb.withVersion(v.majorVersion(), v.minorVersion());
                    case MethodModel mm ->
                            clb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(), mm.flags().flagsMask(), _ -> {
                                for (var me : mm) {
                                    if (Objects.requireNonNull(me) instanceof MethodParametersAttribute a) {
                                        var r = MethodParametersAttribute.of(
                                                        a.parameters().stream().map(
                                                                mp -> MethodParameterInfo.ofParameter(mp.name().map(Utf8Entry::stringValue), mp.flagsMask())
                                                        ).toArray(MethodParameterInfo[]::new)
                                                )
                                                .parameters()
                                                .getFirst();
                                        addDefaultMapping(MethodParameterInfo.class, r);
                                    }
                                }
                            });
                    case InnerClassesAttribute a ->
                            addDefaultMapping(InnerClassInfo.class, InnerClassesAttribute.of(a.classes().stream().map(ici -> InnerClassInfo.of(
                                            ici.innerClass().asSymbol(),
                                            ici.outerClass().map(ClassEntry::asSymbol),
                                            ici.innerName().map(Utf8Entry::stringValue),
                                            ici.flagsMask())).toArray(InnerClassInfo[]::new))
                                    .classes().getFirst());
                    case ModuleAttribute a -> clb.with(
                            ModuleAttribute.of(a.moduleName().asSymbol(), mob -> {
                                mob.moduleFlags(a.moduleFlagsMask());
                                a.moduleVersion().ifPresent(v -> mob.moduleVersion(v.stringValue()));
                                for (var req : a.requires())

                                    mob.requires(req.requires().asSymbol(), req.requiresFlagsMask(), req.requiresVersion().map(Utf8Entry::stringValue).orElse("placeholder"));
                                for (var exp : a.exports())
                                    mob.exports(exp.exportedPackage().asSymbol(), exp.exportsFlagsMask(), exp.exportsTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                                for (var opn : a.opens())
                                    mob.opens(opn.openedPackage().asSymbol(), opn.opensFlagsMask(), opn.opensTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                                for (var use : a.uses()) mob.uses(use.asSymbol());
                                for (var prov : a.provides())
                                    mob.provides(prov.provides().asSymbol(), prov.providesWith().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                            }));
                    case RecordAttribute a -> RecordAttribute.of(a.components().stream().map(rci -> {
                        var y = RecordComponentInfo.of(rci.name().stringValue(), rci.descriptorSymbol(), rci.attributes().stream().mapMulti((_, _) -> {
                        }).toArray(Attribute[]::new));
                        addDefaultMapping(RecordComponentInfo.class, y);
                        return RecordComponentInfo.of(rci.name().stringValue(), rci.descriptorSymbol(), rci.attributes().stream().mapMulti((_, _) -> {
                        }).toArray(Attribute[]::new));
                    }).toArray(RecordComponentInfo[]::new));
                    default -> {}
                }
            }
        });


        var testClass = "JsrAndRetSample";
        var testMethod = "testMethod";
        var cd_list = ArrayList.class.describeConstable().get();
        var ccnever = ClassFile.of();
        ccnever.build(ClassDesc.of(testClass), clb -> clb
                .withVersion(ClassFile.JAVA_5_VERSION, 0)
                .withMethodBody(testMethod, MethodTypeDesc.of(ConstantDescs.CD_void, cd_list), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cob -> cob
                        .block(bb -> {
                            addDefaultMapping(CodeBuilder.BlockCodeBuilder.class, bb);

                            bb.loadConstant("Hello")
                                    .with(DiscontinuedInstruction.JsrInstruction.of(bb.breakLabel()));
                            bb.loadConstant("World")
                                    .with(DiscontinuedInstruction.JsrInstruction.of(Opcode.JSR_W, bb.breakLabel()))
                                    .return_();
                        })
                        .astore(355)
                        .aload(0)
                        .swap()
                        .invokevirtual(cd_list, "add", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object))
                        .pop()
                        .with(DiscontinuedInstruction.RetInstruction.of(355))));


        var root = Paths.get(URI.create(TestNullHostile.class.getResource("TestNullHostile.class").toString())).getParent();
        var cc = ClassFile.of();
        Files.write(root.resolve("testdata/Pattern2-split.class"), cc.transformClass(cc.parse(root.resolve("testdata/Pattern2.class")), ClassTransform.transformingMethodBodies((cob, coe) -> {
            addDefaultMapping(CodeBuilder.class, cob);
            var dcob = (DirectCodeBuilder) cob;
            var curPc = dcob.curPc();
            switch (coe) {
                case LineNumber ln ->
                        dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.lineNumberTable()) {
                            @Override
                            public void writeBody(BufWriterImpl b) {
                                addDefaultMapping(BufWriter.class, b);
                                b.writeU2(1);
                                b.writeU2(curPc);
                                b.writeU2(ln.line());
                            }
                        });
                case LocalVariable lv ->
                        dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTable()) {
                            @Override
                            public void writeBody(BufWriterImpl b) {
                                b.writeU2(1);
                                Util.writeLocalVariable(b, lv);
                            }
                        });
                case LocalVariableType lvt ->
                        dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTypeTable()) {
                            @Override
                            public void writeBody(BufWriterImpl b) {
                                b.writeU2(1);
                                Util.writeLocalVariable(b, lvt);
                            }
                        });
                default -> cob.with(coe);
            }
        })));
    }
}
