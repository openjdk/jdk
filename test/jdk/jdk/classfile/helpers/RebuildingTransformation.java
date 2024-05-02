/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package helpers;

import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.classfile.components.CodeStackTracker;

class RebuildingTransformation {

    static private Random pathSwitch = new Random(1234);

    static byte[] transform(ClassModel clm) {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(clm.thisClass().asSymbol(), clb -> {
            for (var cle : clm) {
                switch (cle) {
                    case AccessFlags af -> clb.withFlags(af.flagsMask());
                    case Superclass sc -> clb.withSuperclass(sc.superclassEntry().asSymbol());
                    case Interfaces i -> clb.withInterfaceSymbols(i.interfaces().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                    case ClassFileVersion v -> clb.withVersion(v.majorVersion(), v.minorVersion());
                    case FieldModel fm ->
                        clb.withField(fm.fieldName().stringValue(), fm.fieldTypeSymbol(), fb -> {
                            for (var fe : fm) {
                                switch (fe) {
                                    case AccessFlags af -> fb.withFlags(af.flagsMask());
                                    case ConstantValueAttribute a -> fb.with(ConstantValueAttribute.of(a.constant().constantValue()));
                                    case DeprecatedAttribute a -> fb.with(DeprecatedAttribute.of());
                                    case RuntimeInvisibleAnnotationsAttribute a -> fb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeInvisibleTypeAnnotationsAttribute a -> fb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case RuntimeVisibleAnnotationsAttribute a -> fb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeVisibleTypeAnnotationsAttribute a -> fb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case SignatureAttribute a -> fb.with(SignatureAttribute.of(Signature.parseFrom(a.asTypeSignature().signatureString())));
                                    case SyntheticAttribute a -> fb.with(SyntheticAttribute.of());
                                    case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                                    case UnknownAttribute a -> throw new AssertionError("Unexpected unknown attribute: " + a.attributeName());
                                }
                            }
                        });
                    case MethodModel mm -> {
                        clb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> {
                            for (var me : mm) {
                                switch (me) {
                                    case AccessFlags af -> mb.withFlags(af.flagsMask());
                                    case CodeModel com -> mb.withCode(cob1 ->
                                            cob1.transforming(CodeStackTracker.of(), cob2 ->
                                            // second pass transforms unbound to unbound instructions
                                            cob2.transforming(new CodeRebuildingTransform(), cob3 ->
                                            // first pass transforms bound to unbound instructions
                                            cob3.transforming(new CodeRebuildingTransform(), cob4 -> {
                                                com.forEachElement(cob4::with);
                                                com.findAttribute(Attributes.stackMapTable()).ifPresent(cob4::with);
                                            }))));
                                    case AnnotationDefaultAttribute a -> mb.with(AnnotationDefaultAttribute.of(transformAnnotationValue(a.defaultValue())));
                                    case DeprecatedAttribute a -> mb.with(DeprecatedAttribute.of());
                                    case ExceptionsAttribute a -> mb.with(ExceptionsAttribute.ofSymbols(a.exceptions().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                                    case MethodParametersAttribute a -> mb.with(MethodParametersAttribute.of(a.parameters().stream().map(mp ->
                                            MethodParameterInfo.ofParameter(mp.name().map(Utf8Entry::stringValue), mp.flagsMask())).toArray(MethodParameterInfo[]::new)));
                                    case RuntimeInvisibleAnnotationsAttribute a -> mb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeInvisibleParameterAnnotationsAttribute a -> mb.with(RuntimeInvisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream().map(pas -> List.of(transformAnnotations(pas))).toList()));
                                    case RuntimeInvisibleTypeAnnotationsAttribute a -> mb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case RuntimeVisibleAnnotationsAttribute a -> mb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeVisibleParameterAnnotationsAttribute a -> mb.with(RuntimeVisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream().map(pas -> List.of(transformAnnotations(pas))).toList()));
                                    case RuntimeVisibleTypeAnnotationsAttribute a -> mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case SignatureAttribute a -> mb.with(SignatureAttribute.of(MethodSignature.parseFrom(a.asMethodSignature().signatureString())));
                                    case SyntheticAttribute a -> mb.with(SyntheticAttribute.of());
                                    case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                                    case UnknownAttribute a -> throw new AssertionError("Unexpected unknown attribute: " + a.attributeName());
                                }
                            }
                        });
                    }
                    case CompilationIDAttribute a -> clb.with(CompilationIDAttribute.of(a.compilationId().stringValue()));
                    case DeprecatedAttribute a -> clb.with(DeprecatedAttribute.of());
                    case EnclosingMethodAttribute a -> clb.with(EnclosingMethodAttribute.of(a.enclosingClass().asSymbol(), a.enclosingMethodName().map(Utf8Entry::stringValue), a.enclosingMethodTypeSymbol()));
                    case InnerClassesAttribute a -> clb.with(InnerClassesAttribute.of(a.classes().stream().map(ici -> InnerClassInfo.of(
                            ici.innerClass().asSymbol(),
                            ici.outerClass().map(ClassEntry::asSymbol),
                            ici.innerName().map(Utf8Entry::stringValue),
                            ici.flagsMask())).toArray(InnerClassInfo[]::new)));
                    case ModuleAttribute a -> clb.with(ModuleAttribute.of(a.moduleName().asSymbol(), mob -> {
                        mob.moduleFlags(a.moduleFlagsMask());
                        a.moduleVersion().ifPresent(v -> mob.moduleVersion(v.stringValue()));
                        for (var req : a.requires()) mob.requires(req.requires().asSymbol(), req.requiresFlagsMask(), req.requiresVersion().map(Utf8Entry::stringValue).orElse(null));
                        for (var exp : a.exports()) mob.exports(exp.exportedPackage().asSymbol(), exp.exportsFlagsMask(), exp.exportsTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                        for (var opn : a.opens()) mob.opens(opn.openedPackage().asSymbol(), opn.opensFlagsMask(), opn.opensTo().stream().map(ModuleEntry::asSymbol).toArray(ModuleDesc[]::new));
                        for (var use : a.uses()) mob.uses(use.asSymbol());
                        for (var prov : a.provides()) mob.provides(prov.provides().asSymbol(), prov.providesWith().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                    }));
                    case ModuleHashesAttribute a -> clb.with(ModuleHashesAttribute.of(a.algorithm().stringValue(),
                            a.hashes().stream().map(mh -> ModuleHashInfo.of(mh.moduleName().asSymbol(), mh.hash())).toArray(ModuleHashInfo[]::new)));
                    case ModuleMainClassAttribute a -> clb.with(ModuleMainClassAttribute.of(a.mainClass().asSymbol()));
                    case ModulePackagesAttribute a -> clb.with(ModulePackagesAttribute.ofNames(a.packages().stream().map(PackageEntry::asSymbol).toArray(PackageDesc[]::new)));
                    case ModuleResolutionAttribute a -> clb.with(ModuleResolutionAttribute.of(a.resolutionFlags()));
                    case ModuleTargetAttribute a -> clb.with(ModuleTargetAttribute.of(a.targetPlatform().stringValue()));
                    case NestHostAttribute a -> clb.with(NestHostAttribute.of(a.nestHost().asSymbol()));
                    case NestMembersAttribute a -> clb.with(NestMembersAttribute.ofSymbols(a.nestMembers().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                    case PermittedSubclassesAttribute a -> clb.with(PermittedSubclassesAttribute.ofSymbols(a.permittedSubclasses().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                    case RecordAttribute a -> clb.with(RecordAttribute.of(a.components().stream().map(rci ->
                            RecordComponentInfo.of(rci.name().stringValue(), rci.descriptorSymbol(), rci.attributes().stream().mapMulti((rca, rcac) -> {
                                    switch(rca) {
                                        case RuntimeInvisibleAnnotationsAttribute riaa -> rcac.accept(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(riaa.annotations())));
                                        case RuntimeInvisibleTypeAnnotationsAttribute ritaa -> rcac.accept(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(ritaa.annotations(), null, null)));
                                        case RuntimeVisibleAnnotationsAttribute rvaa -> rcac.accept(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(rvaa.annotations())));
                                        case RuntimeVisibleTypeAnnotationsAttribute rvtaa -> rcac.accept(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(rvtaa.annotations(), null, null)));
                                        case SignatureAttribute sa -> rcac.accept(SignatureAttribute.of(Signature.parseFrom(sa.asTypeSignature().signatureString())));
                                        default -> throw new AssertionError("Unexpected record component attribute: " + rca.attributeName());
                                    }}).toArray(Attribute[]::new))).toArray(RecordComponentInfo[]::new)));
                    case RuntimeInvisibleAnnotationsAttribute a -> clb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                    case RuntimeInvisibleTypeAnnotationsAttribute a -> clb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                    case RuntimeVisibleAnnotationsAttribute a -> clb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                    case RuntimeVisibleTypeAnnotationsAttribute a -> clb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                    case SignatureAttribute a -> clb.with(SignatureAttribute.of(ClassSignature.parseFrom(a.asClassSignature().signatureString())));
                    case SourceDebugExtensionAttribute a -> clb.with(SourceDebugExtensionAttribute.of(a.contents()));
                    case SourceFileAttribute a -> clb.with(SourceFileAttribute.of(a.sourceFile().stringValue()));
                    case SourceIDAttribute a -> clb.with(SourceIDAttribute.of(a.sourceId().stringValue()));
                    case SyntheticAttribute a -> clb.with(SyntheticAttribute.of());
                    case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                    case UnknownAttribute a -> throw new AssertionError("Unexpected unknown attribute: " + a.attributeName());
                }
            }
        });
    }

    static Annotation[] transformAnnotations(List<Annotation> annotations) {
        return annotations.stream().map(a -> transformAnnotation(a)).toArray(Annotation[]::new);
    }

    static Annotation transformAnnotation(Annotation a) {
        return Annotation.of(a.classSymbol(), a.elements().stream().map(ae -> AnnotationElement.of(ae.name().stringValue(), transformAnnotationValue(ae.value()))).toArray(AnnotationElement[]::new));
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

    static TypeAnnotation[] transformTypeAnnotations(List<TypeAnnotation> annotations, CodeBuilder cob, HashMap<Label, Label> labels) {
        return annotations.stream().map(ta -> TypeAnnotation.of(
                        transformTargetInfo(ta.targetInfo(), cob, labels),
                        ta.targetPath().stream().map(tpc -> TypeAnnotation.TypePathComponent.of(tpc.typePathKind(), tpc.typeArgumentIndex())).toList(),
                        ta.classSymbol(),
                        ta.elements().stream().map(ae -> AnnotationElement.of(ae.name().stringValue(), transformAnnotationValue(ae.value()))).toList())).toArray(TypeAnnotation[]::new);
    }

    static TypeAnnotation.TargetInfo transformTargetInfo(TypeAnnotation.TargetInfo ti, CodeBuilder cob, HashMap<Label, Label> labels) {
        return switch (ti) {
            case TypeAnnotation.CatchTarget t -> TypeAnnotation.TargetInfo.ofExceptionParameter(t.exceptionTableIndex());
            case TypeAnnotation.EmptyTarget t -> TypeAnnotation.TargetInfo.of(t.targetType());
            case TypeAnnotation.FormalParameterTarget t -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(t.formalParameterIndex());
            case TypeAnnotation.SupertypeTarget t -> TypeAnnotation.TargetInfo.ofClassExtends(t.supertypeIndex());
            case TypeAnnotation.ThrowsTarget t -> TypeAnnotation.TargetInfo.ofThrows(t.throwsTargetIndex());
            case TypeAnnotation.TypeParameterBoundTarget t -> TypeAnnotation.TargetInfo.ofTypeParameterBound(t.targetType(), t.typeParameterIndex(), t.boundIndex());
            case TypeAnnotation.TypeParameterTarget t -> TypeAnnotation.TargetInfo.ofTypeParameter(t.targetType(), t.typeParameterIndex());
            case TypeAnnotation.LocalVarTarget t -> TypeAnnotation.TargetInfo.ofVariable(t.targetType(), t.table().stream().map(lvti ->
                            TypeAnnotation.LocalVarTargetInfo.of(labels.computeIfAbsent(lvti.startLabel(), l -> cob.newLabel()),
                            labels.computeIfAbsent(lvti.endLabel(), l -> cob.newLabel()), lvti.index())).toList());
            case TypeAnnotation.OffsetTarget t -> TypeAnnotation.TargetInfo.ofOffset(t.targetType(), labels.computeIfAbsent(t.target(), l -> cob.newLabel()));
            case TypeAnnotation.TypeArgumentTarget t -> TypeAnnotation.TargetInfo.ofTypeArgument(t.targetType(),
                            labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
        };
    }

    static List<StackMapFrameInfo.VerificationTypeInfo> transformFrameTypeInfos(List<StackMapFrameInfo.VerificationTypeInfo> infos, CodeBuilder cob, HashMap<Label, Label> labels) {
        return infos.stream().map(ti -> {
            return switch (ti) {
                case StackMapFrameInfo.SimpleVerificationTypeInfo i -> i;
                case StackMapFrameInfo.ObjectVerificationTypeInfo i -> StackMapFrameInfo.ObjectVerificationTypeInfo.of(i.classSymbol());
                case StackMapFrameInfo.UninitializedVerificationTypeInfo i -> StackMapFrameInfo.UninitializedVerificationTypeInfo.of(labels.computeIfAbsent(i.newTarget(), l -> cob.newLabel()));
            };
        }).toList();
    }

    static class CodeRebuildingTransform implements CodeTransform {

        final HashMap<Label, Label> labels = new HashMap<>();

        @Override
        public void accept(CodeBuilder cob, CodeElement coe) {
            switch (coe) {
                case ArrayLoadInstruction i -> {
                    switch (i.typeKind()) {
                        case ByteType -> cob.baload();
                        case ShortType -> cob.saload();
                        case IntType -> cob.iaload();
                        case FloatType -> cob.faload();
                        case LongType -> cob.laload();
                        case DoubleType -> cob.daload();
                        case ReferenceType -> cob.aaload();
                        case CharType -> cob.caload();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case ArrayStoreInstruction i -> {
                    switch (i.typeKind()) {
                        case ByteType -> cob.bastore();
                        case ShortType -> cob.sastore();
                        case IntType -> cob.iastore();
                        case FloatType -> cob.fastore();
                        case LongType -> cob.lastore();
                        case DoubleType -> cob.dastore();
                        case ReferenceType -> cob.aastore();
                        case CharType -> cob.castore();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case BranchInstruction i -> {
                    var target = labels.computeIfAbsent(i.target(), l -> cob.newLabel());
                    switch (i.opcode()) {
                        case GOTO -> cob.goto_(target);
                        case GOTO_W -> cob.goto_w(target);
                        case IF_ACMPEQ -> cob.if_acmpeq(target);
                        case IF_ACMPNE -> cob.if_acmpne(target);
                        case IF_ICMPEQ -> cob.if_icmpeq(target);
                        case IF_ICMPGE -> cob.if_icmpge(target);
                        case IF_ICMPGT -> cob.if_icmpgt(target);
                        case IF_ICMPLE -> cob.if_icmple(target);
                        case IF_ICMPLT -> cob.if_icmplt(target);
                        case IF_ICMPNE -> cob.if_icmpne(target);
                        case IFNONNULL -> cob.if_nonnull(target);
                        case IFNULL -> cob.if_null(target);
                        case IFEQ -> cob.ifeq(target);
                        case IFGE -> cob.ifge(target);
                        case IFGT -> cob.ifgt(target);
                        case IFLE -> cob.ifle(target);
                        case IFLT -> cob.iflt(target);
                        case IFNE -> cob.ifne(target);
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case ConstantInstruction i -> {
                    if (i.constantValue() == null)
                        if (pathSwitch.nextBoolean()) cob.aconst_null();
                        else cob.loadConstant(null);
                    else switch (i.constantValue()) {
                        case Integer iVal -> {
                            if (iVal == 1 && pathSwitch.nextBoolean()) cob.iconst_1();
                            else if (iVal == 2 && pathSwitch.nextBoolean()) cob.iconst_2();
                            else if (iVal == 3 && pathSwitch.nextBoolean()) cob.iconst_3();
                            else if (iVal == 4 && pathSwitch.nextBoolean()) cob.iconst_4();
                            else if (iVal == 5 && pathSwitch.nextBoolean()) cob.iconst_5();
                            else if (iVal == -1 && pathSwitch.nextBoolean()) cob.iconst_m1();
                            else if (iVal >= -128 && iVal <= 127 && pathSwitch.nextBoolean()) cob.bipush(iVal);
                            else if (iVal >= -32768 && iVal <= 32767 && pathSwitch.nextBoolean()) cob.sipush(iVal);
                            else cob.loadConstant(iVal);
                        }
                        case Long lVal -> {
                            if (lVal == 0 && pathSwitch.nextBoolean()) cob.lconst_0();
                            else if (lVal == 1 && pathSwitch.nextBoolean()) cob.lconst_1();
                            else cob.loadConstant(lVal);
                        }
                        case Float fVal -> {
                            if (fVal == 0.0 && pathSwitch.nextBoolean()) cob.fconst_0();
                            else if (fVal == 1.0 && pathSwitch.nextBoolean()) cob.fconst_1();
                            else if (fVal == 2.0 && pathSwitch.nextBoolean()) cob.fconst_2();
                            else cob.loadConstant(fVal);
                        }
                        case Double dVal -> {
                            if (dVal == 0.0d && pathSwitch.nextBoolean()) cob.dconst_0();
                            else if (dVal == 1.0d && pathSwitch.nextBoolean()) cob.dconst_1();
                            else cob.loadConstant(dVal);
                        }
                        default -> cob.loadConstant(i.constantValue());
                    }
                }
                case ConvertInstruction i -> {
                    switch (i.fromType()) {
                        case DoubleType -> {
                            switch (i.toType()) {
                                case FloatType -> cob.d2f();
                                case IntType -> cob.d2i();
                                case LongType -> cob.d2l();
                                default -> throw new AssertionError("Should not reach here");
                            }
                        }
                        case FloatType -> {
                            switch (i.toType()) {
                                case DoubleType -> cob.f2d();
                                case IntType -> cob.f2i();
                                case LongType -> cob.f2l();
                                default -> throw new AssertionError("Should not reach here");
                            }
                        }
                        case IntType -> {
                            switch (i.toType()) {
                                case ByteType -> cob.i2b();
                                case CharType -> cob.i2c();
                                case DoubleType -> cob.i2d();
                                case FloatType -> cob.i2f();
                                case LongType -> cob.i2l();
                                case ShortType -> cob.i2s();
                                default -> throw new AssertionError("Should not reach here");
                            }
                        }
                        case LongType -> {
                            switch (i.toType()) {
                                case DoubleType -> cob.l2d();
                                case FloatType -> cob.l2f();
                                case IntType -> cob.l2i();
                                default -> throw new AssertionError("Should not reach here");
                            }
                        }
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case DiscontinuedInstruction.JsrInstruction i ->
                    cob.with(DiscontinuedInstruction.JsrInstruction.of(i.opcode(), labels.computeIfAbsent(i.target(), l -> cob.newLabel())));
                case DiscontinuedInstruction.RetInstruction i ->
                    cob.with(DiscontinuedInstruction.RetInstruction.of(i.opcode(), i.slot()));
                case FieldInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        switch (i.opcode()) {
                            case GETFIELD -> cob.getfield(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                            case GETSTATIC -> cob.getstatic(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                            case PUTFIELD -> cob.putfield(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                            case PUTSTATIC -> cob.putstatic(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                            default -> throw new AssertionError("Should not reach here");
                        }
                    } else {
                        switch (i.opcode()) {
                            case GETFIELD -> cob.getfield(i.field());
                            case GETSTATIC -> cob.getstatic(i.field());
                            case PUTFIELD -> cob.putfield(i.field());
                            case PUTSTATIC -> cob.putstatic(i.field());
                            default -> throw new AssertionError("Should not reach here");
                        }
                    }
                }
                case InvokeDynamicInstruction i -> {
                    if (pathSwitch.nextBoolean()) cob.invokedynamic(i.invokedynamic().asSymbol());
                    else cob.invokedynamic(i.invokedynamic());
                }
                case InvokeInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        if (i.isInterface()) {
                            switch (i.opcode()) {
                                case INVOKEINTERFACE -> cob.invokeinterface(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                                case INVOKESPECIAL -> cob.invokespecial(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol(), true);
                                case INVOKESTATIC -> cob.invokestatic(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol(), true);
                                default -> throw new AssertionError("Should not reach here");
                            }
                        } else {
                            switch (i.opcode()) {
                                case INVOKESPECIAL -> cob.invokespecial(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                                case INVOKESTATIC -> cob.invokestatic(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                                case INVOKEVIRTUAL -> cob.invokevirtual(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                                default -> throw new AssertionError("Should not reach here");
                            }
                        }
                    } else {
                        switch (i.method()) {
                            case InterfaceMethodRefEntry en -> {
                                switch (i.opcode()) {
                                        case INVOKEINTERFACE -> cob.invokeinterface(en);
                                        case INVOKESPECIAL -> cob.invokespecial(en);
                                        case INVOKESTATIC -> cob.invokestatic(en);
                                        default -> throw new AssertionError("Should not reach here");
                                }
                            }
                            case MethodRefEntry en -> {
                                switch (i.opcode()) {
                                        case INVOKESPECIAL -> cob.invokespecial(en);
                                        case INVOKESTATIC -> cob.invokestatic(en);
                                        case INVOKEVIRTUAL -> cob.invokevirtual(en);
                                        default -> throw new AssertionError("Should not reach here");
                                }
                            }
                            default -> throw new AssertionError("Should not reach here");
                        }
                    }
                }
                case LoadInstruction i -> {
                    switch (i.typeKind()) {
                        case IntType -> cob.iload(i.slot());
                        case FloatType -> cob.fload(i.slot());
                        case LongType -> cob.lload(i.slot());
                        case DoubleType -> cob.dload(i.slot());
                        case ReferenceType -> cob.aload(i.slot());
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case StoreInstruction i -> {
                    switch (i.typeKind()) {
                        case IntType -> cob.istore(i.slot());
                        case FloatType -> cob.fstore(i.slot());
                        case LongType -> cob.lstore(i.slot());
                        case DoubleType -> cob.dstore(i.slot());
                        case ReferenceType -> cob.astore(i.slot());
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case IncrementInstruction i ->
                    cob.iinc(i.slot(), i.constant());
                case LookupSwitchInstruction i ->
                    cob.lookupswitch(labels.computeIfAbsent(i.defaultTarget(), l -> cob.newLabel()),
                                     i.cases().stream().map(sc ->
                                             SwitchCase.of(sc.caseValue(), labels.computeIfAbsent(sc.target(), l -> cob.newLabel()))).toList());
                case MonitorInstruction i -> {
                    switch (i.opcode()) {
                        case MONITORENTER -> cob.monitorenter();
                        case MONITOREXIT -> cob.monitorexit();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case NewMultiArrayInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        cob.multianewarray(i.arrayType().asSymbol(), i.dimensions());
                    } else {
                        cob.multianewarray(i.arrayType(), i.dimensions());
                    }
                }
                case NewObjectInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        cob.new_(i.className().asSymbol());
                    } else {
                        cob.new_(i.className());
                    }
                }
                case NewPrimitiveArrayInstruction i ->
                    cob.newarray(i.typeKind());
                case NewReferenceArrayInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        cob.anewarray(i.componentType().asSymbol());
                    } else {
                        cob.anewarray(i.componentType());
                    }
                }
                case NopInstruction i ->
                    cob.nop();
                case OperatorInstruction i -> {
                    switch (i.opcode()) {
                        case IADD -> cob.iadd();
                        case LADD -> cob.ladd();
                        case FADD -> cob.fadd();
                        case DADD -> cob.dadd();
                        case ISUB -> cob.isub();
                        case LSUB -> cob.lsub();
                        case FSUB -> cob.fsub();
                        case DSUB -> cob.dsub();
                        case IMUL -> cob.imul();
                        case LMUL -> cob.lmul();
                        case FMUL -> cob.fmul();
                        case DMUL -> cob.dmul();
                        case IDIV -> cob.idiv();
                        case LDIV -> cob.ldiv();
                        case FDIV -> cob.fdiv();
                        case DDIV -> cob.ddiv();
                        case IREM -> cob.irem();
                        case LREM -> cob.lrem();
                        case FREM -> cob.frem();
                        case DREM -> cob.drem();
                        case INEG -> cob.ineg();
                        case LNEG -> cob.lneg();
                        case FNEG -> cob.fneg();
                        case DNEG -> cob.dneg();
                        case ISHL -> cob.ishl();
                        case LSHL -> cob.lshl();
                        case ISHR -> cob.ishr();
                        case LSHR -> cob.lshr();
                        case IUSHR -> cob.iushr();
                        case LUSHR -> cob.lushr();
                        case IAND -> cob.iand();
                        case LAND -> cob.land();
                        case IOR -> cob.ior();
                        case LOR -> cob.lor();
                        case IXOR -> cob.ixor();
                        case LXOR -> cob.lxor();
                        case LCMP -> cob.lcmp();
                        case FCMPL -> cob.fcmpl();
                        case FCMPG -> cob.fcmpg();
                        case DCMPL -> cob.dcmpl();
                        case DCMPG -> cob.dcmpg();
                        case ARRAYLENGTH -> cob.arraylength();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case ReturnInstruction i -> {
                    switch (i.typeKind()) {
                        case IntType -> cob.ireturn();
                        case FloatType -> cob.freturn();
                        case LongType -> cob.lreturn();
                        case DoubleType -> cob.dreturn();
                        case ReferenceType -> cob.areturn();
                        case VoidType -> cob.return_();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case StackInstruction i -> {
                    switch (i.opcode()) {
                        case POP -> cob.pop();
                        case POP2 -> cob.pop2();
                        case DUP -> cob.dup();
                        case DUP_X1 -> cob.dup_x1();
                        case DUP_X2 -> cob.dup_x2();
                        case DUP2 -> cob.dup2();
                        case DUP2_X1 -> cob.dup2_x1();
                        case DUP2_X2 -> cob.dup2_x2();
                        case SWAP -> cob.swap();
                        default -> throw new AssertionError("Should not reach here");
                    }
                }
                case TableSwitchInstruction i ->
                    cob.tableswitch(i.lowValue(), i.highValue(),
                                    labels.computeIfAbsent(i.defaultTarget(), l -> cob.newLabel()),
                                    i.cases().stream().map(sc ->
                                            SwitchCase.of(sc.caseValue(), labels.computeIfAbsent(sc.target(), l -> cob.newLabel()))).toList());
                case ThrowInstruction i -> cob.athrow();
                case TypeCheckInstruction i -> {
                    if (pathSwitch.nextBoolean()) {
                        switch (i.opcode()) {
                            case CHECKCAST -> cob.checkcast(i.type().asSymbol());
                            case INSTANCEOF -> cob.instanceOf(i.type().asSymbol());
                            default -> throw new AssertionError("Should not reach here");
                        }
                    } else {
                        switch (i.opcode()) {
                            case CHECKCAST -> cob.checkcast(i.type());
                            case INSTANCEOF -> cob.instanceOf(i.type());
                            default -> throw new AssertionError("Should not reach here");
                        }
                    }
                }
                case CharacterRange pi ->
                    cob.characterRange(labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()),
                                       labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()),
                                       pi.characterRangeStart(), pi.characterRangeEnd(), pi.flags());
                case ExceptionCatch pi ->
                    pi.catchType().ifPresentOrElse(
                            catchType -> cob.exceptionCatch(labels.computeIfAbsent(pi.tryStart(), l -> cob.newLabel()),
                                                            labels.computeIfAbsent(pi.tryEnd(), l -> cob.newLabel()),
                                                            labels.computeIfAbsent(pi.handler(), l -> cob.newLabel()),
                                                            catchType.asSymbol()),
                            () -> cob.exceptionCatchAll(labels.computeIfAbsent(pi.tryStart(), l -> cob.newLabel()),
                                                        labels.computeIfAbsent(pi.tryEnd(), l -> cob.newLabel()),
                                                        labels.computeIfAbsent(pi.handler(), l -> cob.newLabel())));
                case LabelTarget pi ->
                    cob.labelBinding(labels.computeIfAbsent(pi.label(), l -> cob.newLabel()));
                case LineNumber pi ->
                    cob.lineNumber(pi.line());
                case LocalVariable pi ->
                    cob.localVariable(pi.slot(), pi.name().stringValue(), pi.typeSymbol(),
                                      labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()),
                                       labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()));
                case LocalVariableType pi ->
                    cob.localVariableType(pi.slot(), pi.name().stringValue(),
                                          Signature.parseFrom(pi.signatureSymbol().signatureString()),
                                          labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()),
                                          labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()));
                case RuntimeInvisibleTypeAnnotationsAttribute a ->
                    cob.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), cob, labels)));
                case RuntimeVisibleTypeAnnotationsAttribute a ->
                    cob.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), cob, labels)));
                case StackMapTableAttribute a ->
                    cob.with(StackMapTableAttribute.of(a.entries().stream().map(fr ->
                            StackMapFrameInfo.of(labels.computeIfAbsent(fr.target(), l -> cob.newLabel()),
                                    transformFrameTypeInfos(fr.locals(), cob, labels),
                                    transformFrameTypeInfos(fr.stack(), cob, labels))).toList()));
                case CustomAttribute a ->
                    throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
            }
        }
    }
}
