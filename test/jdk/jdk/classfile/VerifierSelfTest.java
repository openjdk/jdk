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
 * @summary Testing Classfile Verifier.
 * @enablePreview
 * @run junit VerifierSelfTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
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
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.components.ClassPrinter;
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
                            Classfile.of().parse(path).verify(null);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    });
    }

    @Test
    void testFailedDump() throws IOException {
        Path path = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util/HashMap.class");
        var cc = Classfile.of(Classfile.ClassHierarchyResolverOption.of(
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
        if (Classfile.of().parse(brokenClassBytes).verify(sb::append).isEmpty()) {
            throw new AssertionError("expected verification failure");
        }
        String output = sb.toString();
        if (!output.contains("- method name: ")) {
            System.out.println(output);
            throw new AssertionError("failed method not dumped to output");
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

    private static <B extends ClassfileBuilder> B patch(B b, Attribute... attrs) {
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

    @Test
    void testParserVerifier() {

        var cc = Classfile.of();
        var cd_test = ClassDesc.of("TestParserVerifier");
        var clm = cc.parse(cc.build(cd_test, clb -> patch(clb,
                DeprecatedAttribute.of(),
                EnclosingMethodAttribute.of(cd_test, Optional.empty(), Optional.empty()),
                InnerClassesAttribute.of(InnerClassInfo.of(cd_test, Optional.empty(), Optional.empty(), 0)),
                NestHostAttribute.of(cd_test),
                NestMembersAttribute.ofSymbols(cd_test),
                PermittedSubclassesAttribute.ofSymbols(cd_test),
                RecordAttribute.of(RecordComponentInfo.of("c", CD_String, patch(
                        SignatureAttribute.of(Signature.of(CD_String))))),
                SignatureAttribute.of(ClassSignature.of(Signature.ClassTypeSig.of(cd_test))),
                SourceFileAttribute.of("TestParserVerifier.java"),
                SyntheticAttribute.of())
                    .withField("f", CD_String, fb -> patch(fb,
                            ConstantValueAttribute.of(""),
                            DeprecatedAttribute.of(),
                            SignatureAttribute.of(Signature.of(CD_String)),
                            SyntheticAttribute.of()))
                    .withMethod("m", MTD_void, 0, mb -> patch(mb,
                            DeprecatedAttribute.of(),
                            ExceptionsAttribute.ofSymbols(CD_Exception),
                            MethodParametersAttribute.of(MethodParameterInfo.ofParameter(Optional.empty(), 0)),
                            SignatureAttribute.of(MethodSignature.of(MTD_void)),
                            SyntheticAttribute.of())
                            .withCode(cob -> cob.return_()))

        ));
        var found = clm.verify(null).stream().map(VerifyError::getMessage).collect(Collectors.toCollection(LinkedList::new));
        var expected = """
                Wrong Deprecated attribute length in class TestParserVerifier
                Multiple EnclosingMethod attributes in class TestParserVerifier
                Wrong EnclosingMethod attribute length in class TestParserVerifier
                Multiple InnerClasses attributes in class TestParserVerifier
                Wrong InnerClasses attribute length in class TestParserVerifier
                Multiple NestHost attributes in class TestParserVerifier
                Wrong NestHost attribute length in class TestParserVerifier
                Multiple NestMembers attributes in class TestParserVerifier
                Wrong NestMembers attribute length in class TestParserVerifier
                Multiple PermittedSubclasses attributes in class TestParserVerifier
                Wrong PermittedSubclasses attribute length in class TestParserVerifier
                Multiple Record attributes in class TestParserVerifier
                Wrong Record attribute length in class TestParserVerifier
                Multiple Signature attributes in class TestParserVerifier
                Wrong Signature attribute length in class TestParserVerifier
                Multiple SourceFile attributes in class TestParserVerifier
                Wrong SourceFile attribute length in class TestParserVerifier
                Wrong Synthetic attribute length in class TestParserVerifier
                Multiple ConstantValue attributes in field TestParserVerifier.f
                Wrong ConstantValue attribute length in field TestParserVerifier.f
                Wrong Deprecated attribute length in field TestParserVerifier.f
                Multiple Signature attributes in field TestParserVerifier.f
                Wrong Signature attribute length in field TestParserVerifier.f
                Wrong Synthetic attribute length in field TestParserVerifier.f
                Wrong Deprecated attribute length in method TestParserVerifier::m()
                Multiple Exceptions attributes in method TestParserVerifier::m()
                Wrong Exceptions attribute length in method TestParserVerifier::m()
                Multiple MethodParameters attributes in method TestParserVerifier::m()
                Wrong MethodParameters attribute length in method TestParserVerifier::m()
                Multiple Signature attributes in method TestParserVerifier::m()
                Wrong Signature attribute length in method TestParserVerifier::m()
                Wrong Synthetic attribute length in method TestParserVerifier::m()
                Multiple Signature attributes in Record component c of class TestParserVerifier
                Wrong Signature attribute length in Record component c of class TestParserVerifier
                Multiple Signature attributes in Record component c of class TestParserVerifier
                Wrong Signature attribute length in Record component c of class TestParserVerifier
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
}
