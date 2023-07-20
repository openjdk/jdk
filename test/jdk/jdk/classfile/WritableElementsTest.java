/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing WritableElement API consistency.
 * @run junit WritableElementsTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import jdk.internal.classfile.Annotation;
import jdk.internal.classfile.AnnotationElement;
import jdk.internal.classfile.AnnotationValue;
import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.AttributedElement;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CompoundElement;
import jdk.internal.classfile.FieldTransform;
import jdk.internal.classfile.MethodTransform;
import jdk.internal.classfile.TypeAnnotation;
import jdk.internal.classfile.WritableElement;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.components.ClassPrinter;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.ClassfileImpl;
import jdk.internal.classfile.impl.DirectFieldBuilder;
import jdk.internal.classfile.impl.DirectMethodBuilder;
import jdk.internal.classfile.impl.SplitConstantPool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
class WritableElementsTest {

    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    static Path[] corpus() throws IOException, URISyntaxException {
        return Stream.of(
                Files.walk(JRT.getPath("modules/java.base/java")),
                Files.walk(JRT.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")),
                Files.walk(Paths.get(URI.create(CorpusTest.class.getResource("WritableElementsTest.class").toString())).getParent()))
                .flatMap(p -> p)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                .toArray(Path[]::new);
    }


    @ParameterizedTest
    @MethodSource("corpus")
    void testBoundWritableElements(Path path) throws Exception {
        var cc = Classfile.of();
        var clm = cc.parse(path);
        var cp = clm.constantPool();
        var cpb = ConstantPoolBuilder.of(clm);
        var buf = new BufWriterImpl(cpb, (ClassfileImpl)cc, 64, clm.thisClass(), clm.majorVersion());

        //test shared CP builder
        testWriteableElement(path, buf, clm, cpb);

        //test CP entries
        for (int i = 1; i < cp.entryCount();) {
            var cpe = cp.entryByIndex(i);
            testWriteableElement(path, buf, clm, cpe);
            i += cpe.width();
        }

        //test BM entries
        for (int i = 0; i < cpb.bootstrapMethodCount(); i++) {
            testWriteableElement(path, buf, clm, cpb.bootstrapMethodEntry(i));
        }

        //test model
        testCompoundElement(path, buf, clm);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void testUnboundWritableElements(Path path) throws Exception {
        var cc = (ClassfileImpl)Classfile.of();
        var clm = cc.parse(path);
        var cp = clm.constantPool();
        var cpb = (SplitConstantPool)ConstantPoolBuilder.of();
        var buf = new BufWriterImpl(cpb, (ClassfileImpl)cc, 64, clm.thisClass(), clm.majorVersion());

        //test clonned CP entries
        for (int i = 1; i < cp.entryCount();) {
            var cpe = AbstractPoolEntry.maybeClone(cpb, cp.entryByIndex(i));
            testWriteableElement(path, buf, clm, cpe);
            i += cpe.width();
        }

        //test CP builder
        testWriteableElement(path, buf, clm, cpb);

        //test field builder
        for (var fm : clm.fields()) {
            var fb = new DirectFieldBuilder(cpb, cc, fm.fieldName(), fm.fieldType(), fm);
            fb.transform(fm, FieldTransform.ACCEPT_ALL);
            testWriteableElement(path, buf, clm, fb);
        }

        //test method builder
        for (var mm : clm.methods()) {
            var mb = new DirectMethodBuilder(cpb, cc, mm.methodName(), mm.methodType(), mm.flags().flagsMask(), mm);
            mb.transform(mm, MethodTransform.dropping(me -> me instanceof CodeAttribute));
            testWriteableElement(path, buf, clm, mb);
        }

        //test unbound attributes
        unboundAndTestAttributes(path, buf, clm);
    }

    void testCompoundElement(Path path, BufWriterImpl buf, CompoundElement<?> ce) {
        for (var e : ce) {
            if (e instanceof CompoundElement cce) {
                testCompoundElement(path, buf, cce);
            }
            if (e instanceof WritableElement we) {
                testWriteableElement(path, buf, ce, we);
            }
        }
    }

    void testWriteableElement(Path path, BufWriterImpl buf, CompoundElement<?> parent, WritableElement<?> we) {
        var len = we.sizeInBytes();
        assertTrue(len.isPresent(), path + ": " + we + " - size info is missing");
        buf.reset();
        we.writeTo(buf);
        assertEquals(buf.size(), len.getAsInt(), () -> {
            var sb = new StringBuilder(path + ": " + we + " - size info does not match to written bytes\n");
            var orig = new byte[buf.size()];
            buf.copyTo(orig, 0);
            for (var b : orig) sb.append(0xff & b).append(' ');
            ClassPrinter.toYaml(we instanceof CompoundElement ce ? ce : parent, ClassPrinter.Verbosity.TRACE_ALL, sb::append);
            return sb.toString();
        });
    }

    void unboundAndTestAttributes(Path path, BufWriterImpl buf, CompoundElement<?> ce) {
        for (var e : ce) {
            if (e instanceof CompoundElement cce) {
                unboundAndTestAttributes(path, buf, cce);
            }
            if (e instanceof AttributedElement ae) {
                for (var a : ae.attributes()) {
                    var ua = unbound(a);
                    if (ua != null) {
                        assertEquals(a.sizeInBytes(), ua.sizeInBytes(), path + ": " + ce + " - unbound attribute size mismatch");
                        testWriteableElement(path, buf, ce, ua);
                    }
                }
            }
        }
    }

    Attribute<?> unbound(Attribute<?> attr) {
        return switch (attr) {
            case AnnotationDefaultAttribute a -> AnnotationDefaultAttribute.of(transformAnnotationValue(a.defaultValue()));
            case CompilationIDAttribute a -> CompilationIDAttribute.of(a.compilationId().stringValue());
            case ConstantValueAttribute a -> ConstantValueAttribute.of(a.constant().constantValue());
            case DeprecatedAttribute a -> DeprecatedAttribute.of();
            case EnclosingMethodAttribute a -> EnclosingMethodAttribute.of(a.enclosingClass().asSymbol(), a.enclosingMethodName().map(Utf8Entry::stringValue), a.enclosingMethodTypeSymbol());
            case ExceptionsAttribute a -> ExceptionsAttribute.ofSymbols(a.exceptions().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
            case InnerClassesAttribute a -> InnerClassesAttribute.of(a.classes().stream().map(ici -> InnerClassInfo.of(
                    ici.innerClass().asSymbol(),
                    ici.outerClass().map(ClassEntry::asSymbol),
                    ici.innerName().map(Utf8Entry::stringValue),
                    ici.flagsMask())).toArray(InnerClassInfo[]::new));
            case MethodParametersAttribute a -> MethodParametersAttribute.of(a.parameters().stream().map(mp ->
                    MethodParameterInfo.ofParameter(mp.name().map(Utf8Entry::stringValue), mp.flagsMask())).toArray(MethodParameterInfo[]::new));
            case ModuleAttribute a -> ModuleAttribute.of(a.moduleName().asSymbol(), mob -> {
                    mob.moduleFlags(a.moduleFlagsMask());
                    a.moduleVersion().ifPresent(v -> mob.moduleVersion(v.stringValue()));
                    for (var req : a.requires()) mob.requires(req.requires().asSymbol(), req.requiresFlagsMask(), req.requiresVersion().map(Utf8Entry::stringValue).orElse(null));
                    for (var exp : a.exports()) mob.exports(exp.exportedPackage().asSymbol(), exp.exportsFlagsMask(), exp.exportsTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                    for (var opn : a.opens()) mob.opens(opn.openedPackage().asSymbol(), opn.opensFlagsMask(), opn.opensTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                    for (var use : a.uses()) mob.uses(use.asSymbol());
                    for (var prov : a.provides()) mob.provides(prov.provides().asSymbol(), prov.providesWith().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                });
            case ModuleHashesAttribute a -> ModuleHashesAttribute.of(a.algorithm().stringValue(),
                    a.hashes().stream().map(mh -> ModuleHashInfo.of(mh.moduleName().asSymbol(), mh.hash())).toArray(ModuleHashInfo[]::new));
            case ModuleMainClassAttribute a -> ModuleMainClassAttribute.of(a.mainClass().asSymbol());
            case ModulePackagesAttribute a -> ModulePackagesAttribute.ofNames(a.packages().stream().map(PackageEntry::asSymbol).toArray(PackageDesc[]::new));
            case ModuleResolutionAttribute a -> ModuleResolutionAttribute.of(a.resolutionFlags());
            case ModuleTargetAttribute a -> ModuleTargetAttribute.of(a.targetPlatform().stringValue());
            case NestHostAttribute a -> NestHostAttribute.of(a.nestHost().asSymbol());
            case NestMembersAttribute a -> NestMembersAttribute.ofSymbols(a.nestMembers().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
            case PermittedSubclassesAttribute a -> PermittedSubclassesAttribute.ofSymbols(a.permittedSubclasses().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
            case RecordAttribute a -> RecordAttribute.of(a.components().stream().map(rci ->
                    RecordComponentInfo.of(rci.name().stringValue(), rci.descriptorSymbol(), rci.attributes().stream().mapMulti((rca, rcac) -> {
                            switch(rca) {
                                case RuntimeInvisibleAnnotationsAttribute riaa -> rcac.accept(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(riaa.annotations())));
                                case RuntimeInvisibleTypeAnnotationsAttribute ritaa -> rcac.accept(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(ritaa.annotations())));
                                case RuntimeVisibleAnnotationsAttribute rvaa -> rcac.accept(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(rvaa.annotations())));
                                case RuntimeVisibleTypeAnnotationsAttribute rvtaa -> rcac.accept(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(rvtaa.annotations())));
                                case SignatureAttribute sa -> rcac.accept(SignatureAttribute.of(sa.signature()));
                                default -> throw new AssertionError("Unexpected record component attribute: " + rca.attributeName());
                            }}).toArray(Attribute[]::new))).toArray(RecordComponentInfo[]::new));
            case RuntimeInvisibleAnnotationsAttribute a -> RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations()));
            case RuntimeInvisibleParameterAnnotationsAttribute a -> RuntimeInvisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream()
                    .map(pas -> List.of(transformAnnotations(pas))).toList());
            case RuntimeInvisibleTypeAnnotationsAttribute a -> RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations()));
            case RuntimeVisibleAnnotationsAttribute a -> RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations()));
            case RuntimeVisibleParameterAnnotationsAttribute a -> RuntimeVisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream()
                    .map(pas -> List.of(transformAnnotations(pas))).toList());
            case RuntimeVisibleTypeAnnotationsAttribute a -> RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations()));
            case SignatureAttribute a -> SignatureAttribute.of(a.signature());
            case SourceDebugExtensionAttribute a -> SourceDebugExtensionAttribute.of(a.contents());
            case SourceFileAttribute a -> SourceFileAttribute.of(a.sourceFile().stringValue());
            case SourceIDAttribute a -> SourceIDAttribute.of(a.sourceId().stringValue());
            case SyntheticAttribute a -> SyntheticAttribute.of();
            default -> null;
        };
    }

    static Annotation[] transformAnnotations(List<Annotation> annotations) {
        return annotations.stream().map(a -> transformAnnotation(a)).toArray(Annotation[]::new);
    }

    static Annotation transformAnnotation(Annotation a) {
        return Annotation.of(a.classSymbol(), a.elements().stream()
                .map(ae -> AnnotationElement.of(ae.name().stringValue(), transformAnnotationValue(ae.value()))).toArray(AnnotationElement[]::new));
    }

    static AnnotationValue transformAnnotationValue(AnnotationValue av) {
        return switch (av) {
            case AnnotationValue.OfAnnotation oa -> AnnotationValue.ofAnnotation(transformAnnotation(oa.annotation()));
            case AnnotationValue.OfArray oa -> AnnotationValue.ofArray(oa.values().stream().map(v -> transformAnnotationValue(v)).toArray(AnnotationValue[]::new));
            case AnnotationValue.OfString v -> AnnotationValue.of(v.stringValue());
            case AnnotationValue.OfDouble v -> AnnotationValue.of(v.doubleValue());
            case AnnotationValue.OfFloat v -> AnnotationValue.of(v.floatValue());
            case AnnotationValue.OfLong v -> AnnotationValue.of(v.longValue());
            case AnnotationValue.OfInteger v -> AnnotationValue.of(v.intValue());
            case AnnotationValue.OfShort v -> AnnotationValue.of(v.shortValue());
            case AnnotationValue.OfCharacter v -> AnnotationValue.of(v.charValue());
            case AnnotationValue.OfByte v -> AnnotationValue.of(v.byteValue());
            case AnnotationValue.OfBoolean v -> AnnotationValue.of(v.booleanValue());
            case AnnotationValue.OfClass oc -> AnnotationValue.of(oc.classSymbol());
            case AnnotationValue.OfEnum oe -> AnnotationValue.ofEnum(oe.classSymbol(), oe.constantName().stringValue());
        };
    }

    static TypeAnnotation[] transformTypeAnnotations(List<TypeAnnotation> annotations) {
        return annotations.stream().map(ta -> TypeAnnotation.of(
                        transformTargetInfo(ta.targetInfo()),
                        ta.targetPath().stream().map(tpc -> TypeAnnotation.TypePathComponent.of(tpc.typePathKind(), tpc.typeArgumentIndex())).toList(),
                        ta.classSymbol(),
                        ta.elements().stream().map(ae -> AnnotationElement.of(ae.name().stringValue(),
                                transformAnnotationValue(ae.value()))).toList())).toArray(TypeAnnotation[]::new);
    }

    static TypeAnnotation.TargetInfo transformTargetInfo(TypeAnnotation.TargetInfo ti) {
        return switch (ti) {
            case TypeAnnotation.CatchTarget t -> TypeAnnotation.TargetInfo.ofExceptionParameter(t.exceptionTableIndex());
            case TypeAnnotation.EmptyTarget t -> TypeAnnotation.TargetInfo.of(t.targetType());
            case TypeAnnotation.FormalParameterTarget t -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(t.formalParameterIndex());
            case TypeAnnotation.SupertypeTarget t -> TypeAnnotation.TargetInfo.ofClassExtends(t.supertypeIndex());
            case TypeAnnotation.ThrowsTarget t -> TypeAnnotation.TargetInfo.ofThrows(t.throwsTargetIndex());
            case TypeAnnotation.TypeParameterBoundTarget t -> TypeAnnotation.TargetInfo.ofTypeParameterBound(t.targetType(), t.typeParameterIndex(), t.boundIndex());
            case TypeAnnotation.TypeParameterTarget t -> TypeAnnotation.TargetInfo.ofTypeParameter(t.targetType(), t.typeParameterIndex());
            case TypeAnnotation.LocalVarTarget t -> TypeAnnotation.TargetInfo.ofVariable(t.targetType(), t.table().stream().map(lvti ->
                            TypeAnnotation.LocalVarTargetInfo.of(lvti.startLabel(), lvti.endLabel(), lvti.index())).toList());
            case TypeAnnotation.OffsetTarget t -> TypeAnnotation.TargetInfo.ofOffset(t.targetType(), t.target());
            case TypeAnnotation.TypeArgumentTarget t -> TypeAnnotation.TargetInfo.ofTypeArgument(t.targetType(), t.target(), t.typeArgumentIndex());
        };
    }

    static List<StackMapFrameInfo.VerificationTypeInfo> transformFrameTypeInfos(List<StackMapFrameInfo.VerificationTypeInfo> infos) {
        return infos.stream().map(ti -> {
            return switch (ti) {
                case StackMapFrameInfo.SimpleVerificationTypeInfo i -> i;
                case StackMapFrameInfo.ObjectVerificationTypeInfo i -> StackMapFrameInfo.ObjectVerificationTypeInfo.of(i.classSymbol());
                case StackMapFrameInfo.UninitializedVerificationTypeInfo i -> StackMapFrameInfo.UninitializedVerificationTypeInfo.of(i.newTarget());
            };
        }).toList();
    }

}
