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
 * @run junit VerifierSelfTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

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

    @Test
    void testParserVerifier() {
        record PatchBuilder<B extends ClassfileBuilder>(B b) {
            public <A extends Attribute> PatchBuilder<B> patch(A a) {
                class CloneAttribute extends CustomAttribute<CloneAttribute> {
                    CloneAttribute() {
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
                                buf.writeU1(0);
                                buf.patchInt(start + 2, 4, buf.size() - start - 6);
                            }

                            @Override
                            public AttributeMapper.AttributeStability stability() {
                                return a.attributeMapper().stability();
                            }
                        });
                    }
                }
                b.with(a);
                b.with(new CloneAttribute());
                return this;
            }
        }

        var cc = Classfile.of();
        var cd_test = ClassDesc.of("TestParserVerifier");
        var clm = cc.parse(cc.build(cd_test, clb -> new PatchBuilder<>(clb)
                .patch(DeprecatedAttribute.of())
                .patch(SignatureAttribute.of(ClassSignature.of(Signature.ClassTypeSig.of(cd_test))))
                .b().withField("f", cd_test, fb -> new PatchBuilder<>(fb)
                            .patch(DeprecatedAttribute.of())
                            .patch(SignatureAttribute.of(Signature.of(cd_test))))

        ));

        assertLinesMatch("""
        Wrong Deprecated attribute length in class
        Duplicate Signature attribute in class
        Wrong Signature attribute length in class
        Wrong Deprecated attribute length in field f
        Duplicate Signature attribute in field f
        Wrong Signature attribute length in field f
        """.lines().toList(), clm.verify(null).stream().map(VerifyError::getMessage).toList());
    }
}
