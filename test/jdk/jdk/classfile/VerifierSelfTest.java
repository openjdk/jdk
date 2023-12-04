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
 * @summary Testing ClassFile Verifier.
 * @enablePreview
 * @run junit VerifierSelfTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.invoke.MethodHandleInfo;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.components.ClassPrinter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

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
                            ClassFile.of().parse(path).verify(null);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    });
    }

    @Test
    void testFailedDump() throws IOException {
        Path path = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util/HashMap.class");
        var cc = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                className -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(null)));
        var classModel = cc.parse(path);
        byte[] brokenClassBytes = cc.transform(classModel,
                (clb, cle) -> {
                    if (cle instanceof MethodModel mm) {
                        clb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cob -> cm.forEachElement(cob));
                            }
                            else
                                mb.with(me);
                        });
                    }
                    else
                        clb.with(cle);
                });
        StringBuilder sb = new StringBuilder();
        if (ClassFile.of().parse(brokenClassBytes).verify(sb::append).isEmpty()) {
            throw new AssertionError("expected verification failure");
        }
        String output = sb.toString();
        if (!output.contains("- method name: ")) {
            System.out.println(output);
            throw new AssertionError("failed method not dumped to output");
        }
    }

    @Test
    void testParserVerification() {
        var cc = ClassFile.of();
        var cd_test = ClassDesc.of("ParserVerificationTestClass");
        var indexes = new int[9];
        var clm = cc.parse(cc.build(cd_test, clb -> {
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
                DeprecatedAttribute.of(),
                EnclosingMethodAttribute.of(cd_test, Optional.empty(), Optional.empty()),
                InnerClassesAttribute.of(InnerClassInfo.of(cd_test, Optional.empty(), Optional.empty(), 0)),
                NestHostAttribute.of(cd_test),
                NestMembersAttribute.ofSymbols(cd_test),
                PermittedSubclassesAttribute.ofSymbols(cd_test),
                RecordAttribute.of(RecordComponentInfo.of("c", CD_String, patch(
                        SignatureAttribute.of(Signature.of(CD_String))))),
                SignatureAttribute.of(ClassSignature.of(Signature.ClassTypeSig.of(cd_test))),
                SourceFileAttribute.of("ParserVerificationTestClass.java"),
                SyntheticAttribute.of())
                    .withInterfaceSymbols(CD_List, CD_List)
                    .withField("f", CD_String, fb -> patch(fb,
                            ConstantValueAttribute.of(""),
                            DeprecatedAttribute.of(),
                            SignatureAttribute.of(Signature.of(CD_String)),
                            SyntheticAttribute.of()))
                    .withField("/", CD_int, 0)
                    .withField("/", CD_int, 0)
                    .withMethod("m", MTD_void, 0, mb -> patch(mb,
                            DeprecatedAttribute.of(),
                            ExceptionsAttribute.ofSymbols(CD_Exception),
                            MethodParametersAttribute.of(MethodParameterInfo.ofParameter(Optional.empty(), 0)),
                            SignatureAttribute.of(MethodSignature.of(MTD_void)),
                            SyntheticAttribute.of())
                            .withCode(cob -> cob.return_()))
                    .withMethod("<>", MTD_void, ClassFile.ACC_NATIVE, mb -> {})
                    .withMethod("<>", MTD_void, ClassFile.ACC_NATIVE, mb -> {});
        }));
        var found = clm.verify(null).stream().map(VerifyError::getMessage).collect(Collectors.toCollection(LinkedList::new));
        var expected = STR."""
                Invalid class name: invalid.class.name at constant pool index \{ indexes[0] } in class ParserVerificationTestClass
                Bad method descriptor: invalid method type at constant pool index \{ indexes[1] } in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index \{ indexes[2] } in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index \{ indexes[3] } in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index \{ indexes[4] } in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index \{ indexes[4] } in class ParserVerificationTestClass
                Illegal field name method; in class ParserVerificationTestClass at constant pool index \{ indexes[4] } in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index \{ indexes[5] } in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index \{ indexes[5] } in class ParserVerificationTestClass
                Illegal method name field; in class ParserVerificationTestClass at constant pool index \{ indexes[5] } in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index \{ indexes[6] } in class ParserVerificationTestClass
                Invalid class name: invalid.class.name at constant pool index \{ indexes[6] } in class ParserVerificationTestClass
                Illegal method name field; in class ParserVerificationTestClass at constant pool index \{ indexes[6] } in class ParserVerificationTestClass
                not a valid reference type descriptor: ()V at constant pool index \{ indexes[7] } in class ParserVerificationTestClass
                Bad method descriptor: I at constant pool index \{ indexes[8] } in class ParserVerificationTestClass
                Duplicate interface List in class ParserVerificationTestClass
                Illegal field name / in class ParserVerificationTestClass
                Duplicate field name / with signature I in class ParserVerificationTestClass
                Illegal field name / in class ParserVerificationTestClass
                Illegal method name <> in class ParserVerificationTestClass
                Duplicate method name <> with signature ()V in class ParserVerificationTestClass
                Illegal method name <> in class ParserVerificationTestClass
                Wrong Deprecated attribute length in class ParserVerificationTestClass
                Multiple EnclosingMethod attributes in class ParserVerificationTestClass
                Wrong EnclosingMethod attribute length in class ParserVerificationTestClass
                Multiple InnerClasses attributes in class ParserVerificationTestClass
                Wrong InnerClasses attribute length in class ParserVerificationTestClass
                Multiple NestHost attributes in class ParserVerificationTestClass
                Wrong NestHost attribute length in class ParserVerificationTestClass
                Multiple NestMembers attributes in class ParserVerificationTestClass
                Wrong NestMembers attribute length in class ParserVerificationTestClass
                Multiple PermittedSubclasses attributes in class ParserVerificationTestClass
                Wrong PermittedSubclasses attribute length in class ParserVerificationTestClass
                Multiple Record attributes in class ParserVerificationTestClass
                Wrong Record attribute length in class ParserVerificationTestClass
                Multiple Signature attributes in class ParserVerificationTestClass
                Wrong Signature attribute length in class ParserVerificationTestClass
                Multiple SourceFile attributes in class ParserVerificationTestClass
                Wrong SourceFile attribute length in class ParserVerificationTestClass
                Wrong Synthetic attribute length in class ParserVerificationTestClass
                Multiple ConstantValue attributes in field ParserVerificationTestClass.f
                Wrong ConstantValue attribute length in field ParserVerificationTestClass.f
                Wrong Deprecated attribute length in field ParserVerificationTestClass.f
                Multiple Signature attributes in field ParserVerificationTestClass.f
                Wrong Signature attribute length in field ParserVerificationTestClass.f
                Wrong Synthetic attribute length in field ParserVerificationTestClass.f
                Wrong Deprecated attribute length in method ParserVerificationTestClass::m()
                Multiple Exceptions attributes in method ParserVerificationTestClass::m()
                Wrong Exceptions attribute length in method ParserVerificationTestClass::m()
                Multiple MethodParameters attributes in method ParserVerificationTestClass::m()
                Wrong MethodParameters attribute length in method ParserVerificationTestClass::m()
                Multiple Signature attributes in method ParserVerificationTestClass::m()
                Wrong Signature attribute length in method ParserVerificationTestClass::m()
                Wrong Synthetic attribute length in method ParserVerificationTestClass::m()
                Multiple Signature attributes in Record component c of class ParserVerificationTestClass
                Wrong Signature attribute length in Record component c of class ParserVerificationTestClass
                Multiple Signature attributes in Record component c of class ParserVerificationTestClass
                Wrong Signature attribute length in Record component c of class ParserVerificationTestClass
                """.lines().filter(exp -> !found.remove(exp)).toList();
        if (!found.isEmpty() || !expected.isEmpty()) {
            ClassPrinter.toYaml(clm, ClassPrinter.Verbosity.TRACE_ALL, System.out::print);
            fail(STR."""

                 Expected:
                   \{ expected.stream().collect(Collectors.joining("\n  ")) }

                 Found:
                   \{ found.stream().collect(Collectors.joining("\n  ")) }
                 """);
        }
    }

    private static class CloneAttribute extends CustomAttribute<CloneAttribute> {
        CloneAttribute(Attribute a) {
            super(new AttributeMapper<CloneAttribute>(){
                @Override
                public String name() {
                    return a.attributeName();
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
}
