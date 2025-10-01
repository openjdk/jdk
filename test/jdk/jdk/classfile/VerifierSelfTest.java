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
 * @summary Testing ClassFile Verifier.
 * @bug 8333812 8361526
 * @run junit VerifierSelfTest
 */
import java.io.IOException;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.JAVA_8_VERSION;
import static java.lang.constant.ConstantDescs.*;

import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandleInfo;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import jdk.internal.classfile.components.ClassPrinter;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ModuleDesc;

import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.DirectClassBuilder;
import jdk.internal.classfile.impl.UnboundAttribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerifierSelfTest {

    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    @Test
    void testVerify() throws IOException {
        Stream.of(
                Files.walk(JRT.getPath("modules/java.base")),
                Files.walk(JRT.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")))
                    .flatMap(p -> p)
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class")).forEach(path -> {
                        try {
                            ClassFile.of().verify(path);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    });
    }

    @Test
    void testFailed() throws IOException {
        Path path = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util/HashMap.class");
        var cc = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                className -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(null)));
        var classModel = cc.parse(path);
        byte[] brokenClassBytes = cc.transformClass(classModel,
                (clb, cle) -> {
                    if (cle instanceof MethodModel mm) {
                        clb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cob -> cm.forEach(cob));
                            }
                            else
                                mb.with(me);
                        });
                    }
                    else
                        clb.with(cle);
                });
        StringBuilder sb = new StringBuilder();
        if (ClassFile.of().verify(brokenClassBytes).isEmpty()) {
            throw new AssertionError("expected verification failure");
        }
    }

    @Test
    void testInvalidAttrLocation() {
        var cc = ClassFile.of();
        var bytes = cc.build(ClassDesc.of("InvalidAttrLocationClass"), cb ->
            ((DirectClassBuilder)cb).writeAttribute(new UnboundAttribute.AdHocAttribute<LocalVariableTableAttribute>(Attributes.localVariableTable()) {
                @Override
                public void writeBody(BufWriterImpl b) {
                    b.writeU2(0);
                }

                @Override
                public Utf8Entry attributeName() {
                    return cb.constantPool().utf8Entry(Attributes.NAME_LOCAL_VARIABLE_TABLE);
                }
            }));
        assertTrue(cc.verify(bytes).stream().anyMatch(e -> e.getMessage().contains("Invalid LocalVariableTable attribute location")));
    }

    @Test
    void testInvalidClassNameEntry() {
        var cc = ClassFile.of();
        var bytes = cc.parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
            0, 0, 0, 0, 0, 2, PoolEntry.TAG_INTEGER, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        assertTrue(cc.verify(bytes).stream().anyMatch(e -> e.getMessage().contains("expected ClassEntry")));
    }

    @Test
    void testParserVerification() {
        var cc = ClassFile.of();
        var cd_test = ClassDesc.of("ParserVerificationTestClass");
        var indexes = new Object[9];
        var clm = cc.parse(cc.build(cd_test, clb -> {
            clb.withFlags(ClassFile.ACC_INTERFACE | ClassFile.ACC_FINAL);
            var cp = clb.constantPool();
            var ce_valid = cp.classEntry(cd_test);
            var ce_invalid = cp.classEntry(cp.utf8Entry("invalid.class.name"));
            indexes[0] = ce_invalid.index();
            var nate_invalid_field = cp.nameAndTypeEntry("field;", CD_int);
            var nate_invalid_method = cp.nameAndTypeEntry("method;", MTD_void);
            var bsme = cp.bsmEntry(BSM_INVOKE, List.of());
            indexes[1] = cp.methodTypeEntry(cp.utf8Entry("invalid method type")).index();
            indexes[2] = cp.constantDynamicEntry(bsme, nate_invalid_method).index();
            indexes[3] = cp.invokeDynamicEntry(bsme, nate_invalid_field).index();
            indexes[4] = cp.fieldRefEntry(ce_invalid, nate_invalid_method).index();
            indexes[5] = cp.methodRefEntry(ce_invalid, nate_invalid_field).index();
            indexes[6] = cp.interfaceMethodRefEntry(ce_invalid, nate_invalid_field).index();
            indexes[7] = cp.methodHandleEntry(MethodHandleInfo.REF_getField, cp.methodRefEntry(cd_test, "method", MTD_void)).index();
            indexes[8] = cp.methodHandleEntry(MethodHandleInfo.REF_invokeVirtual, cp.fieldRefEntry(cd_test, "field", CD_int)).index();
            patch(clb,
                CompilationIDAttribute.of("12345"),
                DeprecatedAttribute.of(),
                EnclosingMethodAttribute.of(cd_test, Optional.empty(), Optional.empty()),
                InnerClassesAttribute.of(InnerClassInfo.of(cd_test, Optional.of(cd_test), Optional.of("inner"), 0)),
                ModuleAttribute.of(ModuleDesc.of("m"), mab -> {}),
                ModuleHashesAttribute.of("alg", List.of()),
                ModuleMainClassAttribute.of(cd_test),
                ModulePackagesAttribute.of(),
                ModuleResolutionAttribute.of(0),
                ModuleTargetAttribute.of("t"),
                NestHostAttribute.of(cd_test),
                NestMembersAttribute.ofSymbols(cd_test),
                PermittedSubclassesAttribute.ofSymbols(cd_test),
                RecordAttribute.of(RecordComponentInfo.of("c", CD_String, patch(
                        SignatureAttribute.of(Signature.of(CD_String)),
                        RuntimeVisibleAnnotationsAttribute.of(),
                        RuntimeInvisibleAnnotationsAttribute.of(),
                        RuntimeVisibleTypeAnnotationsAttribute.of(),
                        RuntimeInvisibleTypeAnnotationsAttribute.of()))),
                RuntimeVisibleAnnotationsAttribute.of(),
                RuntimeInvisibleAnnotationsAttribute.of(),
                RuntimeVisibleTypeAnnotationsAttribute.of(),
                RuntimeInvisibleTypeAnnotationsAttribute.of(),
                SignatureAttribute.of(ClassSignature.of(Signature.ClassTypeSig.of(cd_test))),
                SourceDebugExtensionAttribute.of("sde".getBytes()),
                SourceFileAttribute.of("ParserVerificationTestClass.java"),
                SourceIDAttribute.of("sID"),
                SyntheticAttribute.of())
                    .withInterfaceSymbols(CD_List, CD_List)
                    .withField("f", CD_String, fb -> patch(fb,
                            ConstantValueAttribute.of(0),
                            DeprecatedAttribute.of(),
                            RuntimeVisibleAnnotationsAttribute.of(),
                            RuntimeInvisibleAnnotationsAttribute.of(),
                            RuntimeVisibleTypeAnnotationsAttribute.of(),
                            RuntimeInvisibleTypeAnnotationsAttribute.of(),
                            SignatureAttribute.of(Signature.of(CD_String)),
                            SyntheticAttribute.of()))
                    .withField("/", CD_int, 0)
                    .withField("/", CD_int, 0)
                    .withMethod("m", MTD_void, ClassFile.ACC_ABSTRACT | ClassFile.ACC_STATIC, mb -> patch(mb,
                            AnnotationDefaultAttribute.of(AnnotationValue.ofInt(0)),
                            DeprecatedAttribute.of(),
                            ExceptionsAttribute.ofSymbols(CD_Exception),
                            MethodParametersAttribute.of(MethodParameterInfo.ofParameter(Optional.empty(), 0)),
                            RuntimeVisibleAnnotationsAttribute.of(),
                            RuntimeInvisibleAnnotationsAttribute.of(),
                            RuntimeVisibleParameterAnnotationsAttribute.of(List.of()),
                            RuntimeInvisibleParameterAnnotationsAttribute.of(List.of()),
                            SignatureAttribute.of(MethodSignature.of(MTD_void)),
                            SyntheticAttribute.of())
                            .withCode(cob ->
                                cob.iconst_0()
                                   .ifThen(CodeBuilder::nop)
                                   .return_()
                                   .with(new CloneAttribute(StackMapTableAttribute.of(List.of())))
                                   .with(new CloneAttribute(CharacterRangeTableAttribute.of(List.of())))
                                   .with(new CloneAttribute(LineNumberTableAttribute.of(List.of())))
                                   .with(new CloneAttribute(LocalVariableTableAttribute.of(List.of())))
                                   .with(new CloneAttribute(LocalVariableTypeTableAttribute.of(List.of())))))
                    .withMethod("<>", MTD_void, ClassFile.ACC_NATIVE, mb -> {})
                    .withMethod("<>", MTD_void, ClassFile.ACC_NATIVE, mb -> {})
                    .withMethod(INIT_NAME, MTD_void, 0, mb -> {})
                    .withMethod(CLASS_INIT_NAME, MTD_void, 0, mb -> {});
                }));
        var found = cc.verify(clm).stream().map(VerifyError::getMessage).collect(Collectors.toCollection(LinkedList::new));
        var expected = """
                Invalid class name: invalid.class.name at constant pool index %1$d in class ParserVerificationTestClass
                Bad method descriptor: invalid method type at constant pool index %2$d in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index %3$d in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index %4$d in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index %5$d in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index %5$d in class ParserVerificationTestClass
                Illegal field name method; in class ParserVerificationTestClass at constant pool index %5$d in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index %6$d in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index %6$d in class ParserVerificationTestClass
                Illegal method name field; in class ParserVerificationTestClass at constant pool index %6$d in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index %7$d in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index %7$d in class ParserVerificationTestClass
                Illegal method name field; in class ParserVerificationTestClass at constant pool index %7$d in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index %8$d in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index %9$d in class ParserVerificationTestClass
                Duplicate interface List in class ParserVerificationTestClass
                Illegal field name / in class ParserVerificationTestClass
                Duplicate field name / with signature I in class ParserVerificationTestClass
                Illegal field name / in class ParserVerificationTestClass
                Illegal method name <> in class ParserVerificationTestClass
                Duplicate method name <> with signature ()V in class ParserVerificationTestClass
                Illegal method name <> in class ParserVerificationTestClass
                Interface cannot have a method named <init> in class ParserVerificationTestClass
                Method <clinit> is not static in class ParserVerificationTestClass
                Multiple CompilationID attributes in class ParserVerificationTestClass
                Wrong CompilationID attribute length in class ParserVerificationTestClass
                Wrong Deprecated attribute length in class ParserVerificationTestClass
                Multiple EnclosingMethod attributes in class ParserVerificationTestClass
                Wrong EnclosingMethod attribute length in class ParserVerificationTestClass
                Class is both outer and inner class in class ParserVerificationTestClass
                Multiple InnerClasses attributes in class ParserVerificationTestClass
                Class is both outer and inner class in class ParserVerificationTestClass
                Wrong InnerClasses attribute length in class ParserVerificationTestClass
                Multiple Module attributes in class ParserVerificationTestClass
                Wrong Module attribute length in class ParserVerificationTestClass
                Multiple ModuleHashes attributes in class ParserVerificationTestClass
                Wrong ModuleHashes attribute length in class ParserVerificationTestClass
                Multiple ModuleMainClass attributes in class ParserVerificationTestClass
                Wrong ModuleMainClass attribute length in class ParserVerificationTestClass
                Multiple ModulePackages attributes in class ParserVerificationTestClass
                Wrong ModulePackages attribute length in class ParserVerificationTestClass
                Multiple ModuleResolution attributes in class ParserVerificationTestClass
                Wrong ModuleResolution attribute length in class ParserVerificationTestClass
                Multiple ModuleTarget attributes in class ParserVerificationTestClass
                Wrong ModuleTarget attribute length in class ParserVerificationTestClass
                Multiple NestHost attributes in class ParserVerificationTestClass
                Wrong NestHost attribute length in class ParserVerificationTestClass
                Conflicting NestHost and NestMembers attributes in class ParserVerificationTestClass
                Multiple NestMembers attributes in class ParserVerificationTestClass
                Conflicting NestHost and NestMembers attributes in class ParserVerificationTestClass
                Wrong NestMembers attribute length in class ParserVerificationTestClass
                PermittedSubclasses attribute in final class ParserVerificationTestClass
                Multiple PermittedSubclasses attributes in class ParserVerificationTestClass
                PermittedSubclasses attribute in final class ParserVerificationTestClass
                Wrong PermittedSubclasses attribute length in class ParserVerificationTestClass
                Multiple Record attributes in class ParserVerificationTestClass
                Wrong Record attribute length in class ParserVerificationTestClass
                Multiple RuntimeVisibleAnnotations attributes in class ParserVerificationTestClass
                Wrong RuntimeVisibleAnnotations attribute length in class ParserVerificationTestClass
                Multiple RuntimeInvisibleAnnotations attributes in class ParserVerificationTestClass
                Wrong RuntimeInvisibleAnnotations attribute length in class ParserVerificationTestClass
                Multiple RuntimeVisibleTypeAnnotations attributes in class ParserVerificationTestClass
                Wrong RuntimeVisibleTypeAnnotations attribute length in class ParserVerificationTestClass
                Multiple RuntimeInvisibleTypeAnnotations attributes in class ParserVerificationTestClass
                Wrong RuntimeInvisibleTypeAnnotations attribute length in class ParserVerificationTestClass
                Multiple Signature attributes in class ParserVerificationTestClass
                Wrong Signature attribute length in class ParserVerificationTestClass
                Multiple SourceDebugExtension attributes in class ParserVerificationTestClass
                Multiple SourceFile attributes in class ParserVerificationTestClass
                Wrong SourceFile attribute length in class ParserVerificationTestClass
                Multiple SourceID attributes in class ParserVerificationTestClass
                Wrong SourceID attribute length in class ParserVerificationTestClass
                Wrong Synthetic attribute length in class ParserVerificationTestClass
                Bad constant value type in field ParserVerificationTestClass.f
                Multiple ConstantValue attributes in field ParserVerificationTestClass.f
                Bad constant value type in field ParserVerificationTestClass.f
                Wrong ConstantValue attribute length in field ParserVerificationTestClass.f
                Wrong Deprecated attribute length in field ParserVerificationTestClass.f
                Multiple RuntimeVisibleAnnotations attributes in field ParserVerificationTestClass.f
                Wrong RuntimeVisibleAnnotations attribute length in field ParserVerificationTestClass.f
                Multiple RuntimeInvisibleAnnotations attributes in field ParserVerificationTestClass.f
                Wrong RuntimeInvisibleAnnotations attribute length in field ParserVerificationTestClass.f
                Multiple RuntimeVisibleTypeAnnotations attributes in field ParserVerificationTestClass.f
                Wrong RuntimeVisibleTypeAnnotations attribute length in field ParserVerificationTestClass.f
                Multiple RuntimeInvisibleTypeAnnotations attributes in field ParserVerificationTestClass.f
                Wrong RuntimeInvisibleTypeAnnotations attribute length in field ParserVerificationTestClass.f
                Multiple Signature attributes in field ParserVerificationTestClass.f
                Wrong Signature attribute length in field ParserVerificationTestClass.f
                Wrong Synthetic attribute length in field ParserVerificationTestClass.f
                Multiple AnnotationDefault attributes in method ParserVerificationTestClass::m()
                Wrong AnnotationDefault attribute length in method ParserVerificationTestClass::m()
                Wrong Deprecated attribute length in method ParserVerificationTestClass::m()
                Multiple Exceptions attributes in method ParserVerificationTestClass::m()
                Wrong Exceptions attribute length in method ParserVerificationTestClass::m()
                Multiple MethodParameters attributes in method ParserVerificationTestClass::m()
                Wrong MethodParameters attribute length in method ParserVerificationTestClass::m()
                Multiple RuntimeVisibleAnnotations attributes in method ParserVerificationTestClass::m()
                Wrong RuntimeVisibleAnnotations attribute length in method ParserVerificationTestClass::m()
                Multiple RuntimeInvisibleAnnotations attributes in method ParserVerificationTestClass::m()
                Wrong RuntimeInvisibleAnnotations attribute length in method ParserVerificationTestClass::m()
                Multiple RuntimeVisibleParameterAnnotations attributes in method ParserVerificationTestClass::m()
                Wrong RuntimeVisibleParameterAnnotations attribute length in method ParserVerificationTestClass::m()
                Multiple RuntimeInvisibleParameterAnnotations attributes in method ParserVerificationTestClass::m()
                Wrong RuntimeInvisibleParameterAnnotations attribute length in method ParserVerificationTestClass::m()
                Multiple Signature attributes in method ParserVerificationTestClass::m()
                Wrong Signature attribute length in method ParserVerificationTestClass::m()
                Wrong Synthetic attribute length in method ParserVerificationTestClass::m()
                Code attribute in native or abstract method ParserVerificationTestClass::m()
                Wrong StackMapTable attribute length in Code attribute for method ParserVerificationTestClass::m()
                Wrong CharacterRangeTable attribute length in Code attribute for method ParserVerificationTestClass::m()
                Wrong LineNumberTable attribute length in Code attribute for method ParserVerificationTestClass::m()
                Wrong LocalVariableTable attribute length in Code attribute for method ParserVerificationTestClass::m()
                Wrong LocalVariableTypeTable attribute length in Code attribute for method ParserVerificationTestClass::m()
                Multiple StackMapTable attributes in Code attribute for method ParserVerificationTestClass::m()
                Multiple Signature attributes in Record component c of class ParserVerificationTestClass
                Wrong Signature attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeVisibleAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeVisibleAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeInvisibleAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeInvisibleAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeVisibleTypeAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeVisibleTypeAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeInvisibleTypeAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeInvisibleTypeAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple Signature attributes in Record component c of class ParserVerificationTestClass
                Wrong Signature attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeVisibleAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeVisibleAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeInvisibleAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeInvisibleAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeVisibleTypeAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeVisibleTypeAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Multiple RuntimeInvisibleTypeAnnotations attributes in Record component c of class ParserVerificationTestClass
                Wrong RuntimeInvisibleTypeAnnotations attribute length in Record component c of class ParserVerificationTestClass
                Missing Code attribute in ParserVerificationTestClass::<init>() @0
                Missing Code attribute in ParserVerificationTestClass::<clinit>() @0
                """.formatted(indexes).lines().filter(exp -> !found.remove(exp)).toList();
        if (!found.isEmpty() || !expected.isEmpty()) {
            ClassPrinter.toYaml(clm, ClassPrinter.Verbosity.TRACE_ALL, System.out::print);
            fail("""

                 Expected:
                   %s

                 Found:
                   %s
                 """.formatted(expected.stream().collect(Collectors.joining("\n  ")), found.stream().collect(Collectors.joining("\n  "))));
        }
    }

    private static class CloneAttribute extends CustomAttribute<CloneAttribute> {
        CloneAttribute(Attribute a) {
            super(new AttributeMapper<CloneAttribute>(){
                @Override
                public String name() {
                    return a.attributeName().stringValue();
                }

                @Override
                public CloneAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter buf, CloneAttribute attr) {
                    int start = buf.size();
                    a.attributeMapper().writeAttribute(buf, a);
                    buf.writeU1(0); //writes additional byte to the attribute payload
                    buf.patchInt(start + 2, 4, buf.size() - start - 6);
                }

                @Override
                public AttributeMapper.AttributeStability stability() {
                    return a.attributeMapper().stability();
                }
            });
        }
    }

    private static <B extends ClassFileBuilder> B patch(B b, Attribute... attrs) {
        for (var a : attrs) {
            b.with(a).with(new CloneAttribute(a));
        }
        return b;
    }

    private static List<Attribute<?>> patch(Attribute... attrs) {
        var lst = new ArrayList<Attribute<?>>(attrs.length * 2);
        for (var a : attrs) {
            lst.add(a);
            lst.add(new CloneAttribute(a));
        }
        return lst;
    }

    @Test // JDK-8350029
    void testInvokeSpecialInterfacePatch() {
        var runClass = ClassDesc.of("Run");
        var testClass = ClassDesc.of("Test");
        var runnableClass = Runnable.class.describeConstable().orElseThrow();
        var chr = ClassHierarchyResolver.of(List.of(), Map.of(runClass, CD_Object))
                .orElse(ClassHierarchyResolver.defaultResolver()).cached();
        var context = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(chr));

        for (var isInterface : new boolean[] {true, false}) {
            var bytes = context.build(testClass, clb -> clb
                    .withVersion(JAVA_8_VERSION, 0)
                    .withSuperclass(runClass)
                    .withMethodBody("test", MethodTypeDesc.of(CD_void, testClass), ACC_STATIC, cob -> cob
                            .aload(0)
                            .invokespecial(runnableClass, "run", MTD_void, isInterface)
                            .return_()));
            var errors = context.verify(bytes);
            assertNotEquals(List.of(), errors, "invokespecial, isInterface = " + isInterface);
            assertTrue(errors.getFirst().getMessage().contains("interface method to invoke is not in a direct superinterface"), errors.getFirst().getMessage());
        }
    }
}
