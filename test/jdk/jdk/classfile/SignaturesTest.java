/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing Signatures.
 * @run junit SignaturesTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.Signature.*;
import java.lang.classfile.Attributes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static helpers.ClassRecord.assertEqualsDeep;
import static java.lang.constant.ConstantDescs.*;

class SignaturesTest {

    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    @Test
    void testBuildingSignatures() {
        assertEqualsDeep(
                ClassSignature.of(
                        ClassTypeSig.of(
                                ClassTypeSig.of(ClassDesc.of("java.util.LinkedHashMap"), TypeArg.of(TypeVarSig.of("K")), TypeArg.of(TypeVarSig.of("V"))),
                                ClassDesc.of("LinkedHashIterator")),
                        ClassTypeSig.of(ClassDesc.of("java.util.Iterator"),
                                TypeArg.of(ClassTypeSig.of(ClassDesc.of("java.util.Map$Entry"), TypeArg.of(TypeVarSig.of("K")), TypeArg.of(TypeVarSig.of("V")))))),
                ClassSignature.parseFrom("Ljava/util/LinkedHashMap<TK;TV;>.LinkedHashIterator;Ljava/util/Iterator<Ljava/util/Map$Entry<TK;TV;>;>;"));

        assertEqualsDeep(
                ClassSignature.of(
                        List.of(
                                TypeParam.of("K", ClassTypeSig.of(CD_Object)),
                                TypeParam.of("V", ClassTypeSig.of(CD_Object))),
                        ClassTypeSig.of(ClassDesc.of("java.util.AbstractMap"), TypeArg.of(TypeVarSig.of("K")), TypeArg.of(TypeVarSig.of("V"))),
                        ClassTypeSig.of(ClassDesc.of("java.util.concurrent.ConcurrentMap"), TypeArg.of(TypeVarSig.of("K")), TypeArg.of(TypeVarSig.of("V"))),
                        ClassTypeSig.of(ClassDesc.of("java.io.Serializable"))),
                ClassSignature.parseFrom("<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/util/concurrent/ConcurrentMap<TK;TV;>;Ljava/io/Serializable;"));

        assertEqualsDeep(
                MethodSignature.of(
                        ClassTypeSig.of(
                                CD_Map,
                                TypeArg.of(ClassTypeSig.of(
                                        CD_Class,
                                            TypeArg.extendsOf(
                                                    ClassTypeSig.of(ClassDesc.of("java.lang.annotation.Annotation"))))),
                                TypeArg.of(ClassTypeSig.of(ClassDesc.of("java.lang.annotation.Annotation")))),
                        Signature.of(CD_byte.arrayType()),
                        ClassTypeSig.of(ClassDesc.of("jdk.internal.reflect.ConstantPool")),
                        ClassTypeSig.of(CD_Class, TypeArg.unbounded()),
                        ArrayTypeSig.of(
                                ClassTypeSig.of(CD_Class,
                                        TypeArg.extendsOf(
                                                ClassTypeSig.of(ClassDesc.of("java.lang.annotation.Annotation")))))),
                MethodSignature.parseFrom("([BLjdk/internal/reflect/ConstantPool;Ljava/lang/Class<*>;[Ljava/lang/Class<+Ljava/lang/annotation/Annotation;>;)Ljava/util/Map<Ljava/lang/Class<+Ljava/lang/annotation/Annotation;>;Ljava/lang/annotation/Annotation;>;"));

        assertEqualsDeep(
                MethodSignature.of(
                        List.of(
                                TypeParam.of("T", Optional.empty(), ClassTypeSig.of(ClassDesc.of("java.lang.annotation.Annotation")))),
                        List.of(
                                ClassTypeSig.of(ClassDesc.of("java.lang.IOException")),
                                ClassTypeSig.of(ClassDesc.of("java.lang.IllegalAccessError"))),
                        ArrayTypeSig.of(TypeVarSig.of("T")),
                        ClassTypeSig.of(CD_Class, TypeArg.of(TypeVarSig.of("T")))),
                MethodSignature.parseFrom("<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)[TT;^Ljava/lang/IOException;^Ljava/lang/IllegalAccessError;"));

        assertEqualsDeep(
                ClassTypeSig.of(
                        CD_Set,
                        TypeArg.extendsOf(
                                ClassTypeSig.of(ClassDesc.of("java.nio.file.WatchEvent$Kind"), TypeArg.unbounded()))),
                Signature.parseFrom("Ljava/util/Set<+Ljava/nio/file/WatchEvent$Kind<*>;>;"));

        assertEqualsDeep(
                ArrayTypeSig.of(2, TypeVarSig.of("E")),
                Signature.parseFrom("[[TE;"));
    }

    @Test
    void testParseAndPrintSignatures() throws Exception {
        var csc = new AtomicInteger();
        var msc = new AtomicInteger();
        var fsc = new AtomicInteger();
        var rsc = new AtomicInteger();
        Stream.of(
                Files.walk(JRT.getPath("modules/java.base")),
                Files.walk(JRT.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")),
                Files.walk(Path.of(SignaturesTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())))
                .flatMap(p -> p)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class")).forEach(path -> {
            try {
                var cm = ClassFile.of().parse(path);
                cm.findAttribute(Attributes.SIGNATURE).ifPresent(csig -> {
                    assertEquals(
                            ClassSignature.parseFrom(csig.signature().stringValue()).signatureString(),
                            csig.signature().stringValue(),
                            cm.thisClass().asInternalName());
                    csc.incrementAndGet();
                });
                for (var m : cm.methods()) {
                    m.findAttribute(Attributes.SIGNATURE).ifPresent(msig -> {
                        assertEquals(
                                MethodSignature.parseFrom(msig.signature().stringValue()).signatureString(),
                                msig.signature().stringValue(),
                                cm.thisClass().asInternalName() + "::" + m.methodName().stringValue() + m.methodType().stringValue());
                        msc.incrementAndGet();
                    });
                }
                for (var f : cm.fields()) {
                    f.findAttribute(Attributes.SIGNATURE).ifPresent(fsig -> {
                        assertEquals(
                                Signature.parseFrom(fsig.signature().stringValue()).signatureString(),
                                fsig.signature().stringValue(),
                                cm.thisClass().asInternalName() + "." + f.fieldName().stringValue());
                        fsc.incrementAndGet();
                    });
                }
                cm.findAttribute(Attributes.RECORD).ifPresent(reca
                        -> reca.components().forEach(rc -> rc.findAttribute(Attributes.SIGNATURE).ifPresent(rsig -> {
                    assertEquals(
                            Signature.parseFrom(rsig.signature().stringValue()).signatureString(),
                            rsig.signature().stringValue(),
                            cm.thisClass().asInternalName() + "." + rc.name().stringValue());
                    rsc.incrementAndGet();
                })));
            } catch (Exception e) {
                throw new AssertionError(path.toString(), e);
            }
        });
        System.out.println("SignaturesTest - tested signatures of " + csc + " classes, " + msc + " methods, " + fsc + " fields and " + rsc + " record components");
    }

    static class Outer<T1> {
        class Inner<T2> {}
    }

    static class Observer extends ArrayList<Outer<String>.Inner<Long>>{}

    @Test
    void testClassSignatureClassDesc() throws IOException {
        var observerCf = ClassFile.of().parse(Path.of(System.getProperty("test.classes"), "SignaturesTest$Observer.class"));
        var sig = observerCf.findAttribute(Attributes.SIGNATURE).orElseThrow().asClassSignature();
        var innerSig = (ClassTypeSig) ((ClassTypeSig) sig.superclassSignature()) // ArrayList
                .typeArgs().getFirst() // Outer<String>.Inner<Long>
                .boundType().orElseThrow(); // assert it's exact bound
        assertEquals("Inner", innerSig.className(), "simple name in signature");
        assertEquals(Outer.Inner.class.describeConstable().orElseThrow(), innerSig.classDesc(),
                "ClassDesc derived from signature");
    }
}
