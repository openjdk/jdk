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
package helpers;

import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.HashMap;
import java.util.List;
import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.constantpool.*;
import jdk.classfile.instruction.*;
import jdk.classfile.jdktypes.ModuleDesc;
import jdk.classfile.jdktypes.PackageDesc;

class RebuildingTransformation {

    static byte[] transform(ClassModel clm) {
        return Classfile.build(clm.thisClass().asSymbol(), clb -> {
            for (var cle : clm) {
                switch (cle) {
                    case AccessFlags af -> clb.withFlags(af.flagsMask());
                    case Superclass sc -> clb.withSuperclass(sc.superclassEntry().asSymbol());
                    case Interfaces i -> clb.withInterfaceSymbols(i.interfaces().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new));
                    case ClassfileVersion v -> clb.withVersion(v.majorVersion(), v.minorVersion());
                    case FieldModel fm ->
                        clb.withField(fm.fieldName().stringValue(), fm.descriptorSymbol(), fb -> {
                            for (var fe : fm) {
                                switch (fe) {
                                    case AccessFlags af -> fb.withFlags(af.flagsMask());
                                    case ConstantValueAttribute a -> fb.with(ConstantValueAttribute.of(a.constant())); //missing ConstantValueAttribute factory method accepting ConstantDesc or individual types
                                    case DeprecatedAttribute a -> fb.with(DeprecatedAttribute.of());
                                    case RuntimeInvisibleAnnotationsAttribute a -> fb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeInvisibleTypeAnnotationsAttribute a -> fb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case RuntimeVisibleAnnotationsAttribute a -> fb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeVisibleTypeAnnotationsAttribute a -> fb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case SignatureAttribute a -> fb.with(SignatureAttribute.of(a.asTypeSignature()));
                                    case SyntheticAttribute a -> fb.with(SyntheticAttribute.of());
                                    case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                                    case UnknownAttribute a -> throw new AssertionError("Unexpected unknown attribute: " + a.attributeName());
                                }
                            }
                        });
                    case MethodModel mm -> {
                        clb.withMethod(mm.methodName().stringValue(), mm.descriptorSymbol(), mm.flags().flagsMask(), mb -> {
                            for (var me : mm) {
                                switch (me) {
                                    case AccessFlags af -> mb.withFlags(af.flagsMask());
                                    case CodeModel com -> mb.withCode(cob -> {
                                        var labels = new HashMap<Label, Label>();
                                        for (var coe : com) {
                                            switch (coe) {
                                                case ArrayLoadInstruction i -> cob.arrayLoadInstruction(i.typeKind());
                                                case ArrayStoreInstruction i -> cob.arrayStoreInstruction(i.typeKind());
                                                case BranchInstruction i -> cob.branchInstruction(i.opcode(), labels.computeIfAbsent(i.target(), l -> cob.newLabel()));
                                                case ConstantInstruction i -> cob.constantInstruction(i.constantValue());
                                                case ConvertInstruction i -> cob.convertInstruction(i.fromType(), i.toType());
                                                case FieldInstruction i -> cob.fieldInstruction(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                                                case InvokeDynamicInstruction i -> cob.invokeDynamicInstruction(i.invokedynamic().asSymbol());
                                                case InvokeInstruction i -> cob.invokeInstruction(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol(), i.isInterface());
                                                case LoadInstruction i -> cob.loadInstruction(i.typeKind(), i.slot());
                                                case StoreInstruction i -> cob.storeInstruction(i.typeKind(), i.slot());
                                                case IncrementInstruction i -> cob.incrementInstruction(i.slot(), i.constant());
                                                case LookupSwitchInstruction i -> cob.lookupSwitchInstruction(labels.computeIfAbsent(i.defaultTarget(), l -> cob.newLabel()),
                                                        i.cases().stream().map(sc -> SwitchCase.of(sc.caseValue(), labels.computeIfAbsent(sc.target(), l -> cob.newLabel()))).toList());
                                                case MonitorInstruction i -> cob.monitorInstruction(i.opcode());
                                                case NewMultiArrayInstruction i -> cob.newMultidimensionalArrayInstruction(i.dimensions(), i.arrayType().asSymbol());
                                                case NewObjectInstruction i -> cob.newObjectInstruction(i.className().asSymbol());
                                                case NewPrimitiveArrayInstruction i -> cob.newPrimitiveArrayInstruction(i.typeKind());
                                                case NewReferenceArrayInstruction i -> cob.newReferenceArrayInstruction(i.componentType().asSymbol());
                                                case NopInstruction i -> cob.nopInstruction();
                                                case OperatorInstruction i -> cob.operatorInstruction(i.opcode());
                                                case ReturnInstruction i -> cob.returnInstruction(i.typeKind());
                                                case StackInstruction i -> cob.stackInstruction(i.opcode());
                                                case TableSwitchInstruction i -> cob.tableSwitchInstruction(i.lowValue(), i.highValue(), labels.computeIfAbsent(i.defaultTarget(), l -> cob.newLabel()),
                                                        i.cases().stream().map(sc -> SwitchCase.of(sc.caseValue(), labels.computeIfAbsent(sc.target(), l -> cob.newLabel()))).toList());
                                                case ThrowInstruction i -> cob.throwInstruction();
                                                case TypeCheckInstruction i -> cob.typeCheckInstruction(i.opcode(), i.type().asSymbol());
                                                case CharacterRange pi -> cob.characterRange(labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()), labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()),
                                                        pi.characterRangeStart(), pi.characterRangeEnd(), pi.flags());
                                                case ExceptionCatch pi -> pi.catchType().ifPresentOrElse(
                                                        catchType -> cob.exceptionCatch(labels.computeIfAbsent(pi.tryStart(), l -> cob.newLabel()), labels.computeIfAbsent(pi.tryEnd(), l -> cob.newLabel()),
                                                                labels.computeIfAbsent(pi.handler(), l -> cob.newLabel()), catchType.asSymbol()),
                                                        () -> cob.exceptionCatchAll(labels.computeIfAbsent(pi.tryStart(), l -> cob.newLabel()), labels.computeIfAbsent(pi.tryEnd(), l -> cob.newLabel()),
                                                                labels.computeIfAbsent(pi.handler(), l -> cob.newLabel())));
                                                case LabelTarget pi -> cob.labelBinding(labels.computeIfAbsent(pi.label(), l -> cob.newLabel()));
                                                case LineNumber pi -> cob.lineNumber(pi.line());
                                                case LocalVariable pi -> cob.localVariable(pi.slot(), pi.name().stringValue(), pi.typeSymbol(), labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()),
                                                        labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()));
                                                case LocalVariableType pi -> cob.localVariableType(pi.slot(), pi.name().stringValue(), pi.signatureSymbol(), labels.computeIfAbsent(pi.startScope(), l -> cob.newLabel()),
                                                        labels.computeIfAbsent(pi.endScope(), l -> cob.newLabel()));
                                                case RuntimeInvisibleTypeAnnotationsAttribute a -> cob.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), cob, labels)));
                                                case RuntimeVisibleTypeAnnotationsAttribute a -> cob.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), cob, labels)));
                                                case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                                            }
                                        }
                                    });
                                    case AnnotationDefaultAttribute a -> mb.with(AnnotationDefaultAttribute.of(transformAnnotationValue(a.defaultValue())));
                                    case DeprecatedAttribute a -> mb.with(DeprecatedAttribute.of());
                                    case ExceptionsAttribute a -> mb.with(ExceptionsAttribute.ofSymbols(a.exceptions().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                                    case MethodParametersAttribute a -> mb.with(MethodParametersAttribute.of(a.parameters().stream().map(mp ->
                                            MethodParameterInfo.of(mp.name().map(Utf8Entry::stringValue).orElse(null), mp.flags().toArray(AccessFlag[]::new))).toArray(MethodParameterInfo[]::new))); //missing MethodParameterInfo factory method accepting String and int
                                    case RuntimeInvisibleAnnotationsAttribute a -> mb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeInvisibleParameterAnnotationsAttribute a -> mb.with(RuntimeInvisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream().map(pas -> List.of(transformAnnotations(pas))).toList()));
                                    case RuntimeInvisibleTypeAnnotationsAttribute a -> mb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case RuntimeVisibleAnnotationsAttribute a -> mb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                                    case RuntimeVisibleParameterAnnotationsAttribute a -> mb.with(RuntimeVisibleParameterAnnotationsAttribute.of(a.parameterAnnotations().stream().map(pas -> List.of(transformAnnotations(pas))).toList()));
                                    case RuntimeVisibleTypeAnnotationsAttribute a -> mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                                    case SignatureAttribute a -> mb.with(SignatureAttribute.of(a.asMethodSignature()));
                                    case SyntheticAttribute a -> mb.with(SyntheticAttribute.of());
                                    case CustomAttribute a -> throw new AssertionError("Unexpected custom attribute: " + a.attributeName());
                                    case UnknownAttribute a -> throw new AssertionError("Unexpected unknown attribute: " + a.attributeName());
                                }
                            }
                        });
                    }
                    case CompilationIDAttribute a -> clb.with(CompilationIDAttribute.of(a.compilationId())); //missing attribute factory method accepting String
                    case DeprecatedAttribute a -> clb.with(DeprecatedAttribute.of());
                    case EnclosingMethodAttribute a -> clb.with(EnclosingMethodAttribute.of(a.enclosingClass(), a.enclosingMethod().orElse(null))); //missing attribute factory method accepting symbols
                    case InnerClassesAttribute a -> clb.with(InnerClassesAttribute.of(a.classes().stream().map(ici -> InnerClassInfo.of(
                            ici.innerClass().asSymbol(),
                            ici.outerClass().map(ClassEntry::asSymbol).orElse(null),
                            ici.innerName().map(Utf8Entry::stringValue).orElse(null),
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
                    case NestHostAttribute a -> clb.with(NestHostAttribute.of(a.nestHost())); //missing attribute factory method accpeting ClassDesc
                    case NestMembersAttribute a -> clb.with(NestMembersAttribute.ofSymbols(a.nestMembers().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                    case PermittedSubclassesAttribute a -> clb.with(PermittedSubclassesAttribute.ofSymbols(a.permittedSubclasses().stream().map(ClassEntry::asSymbol).toArray(ClassDesc[]::new)));
                    case RecordAttribute a -> clb.with(RecordAttribute.of(a.components().stream().map(rci ->
                            RecordComponentInfo.of(rci.name().stringValue(), rci.descriptorSymbol(), rci.attributes().stream().mapMulti((rca, rcac) -> {
                                    switch(rca) {
                                        case RuntimeInvisibleAnnotationsAttribute riaa -> rcac.accept(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(riaa.annotations())));
                                        case RuntimeInvisibleTypeAnnotationsAttribute ritaa -> rcac.accept(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(ritaa.annotations(), null, null)));
                                        case RuntimeVisibleAnnotationsAttribute rvaa -> rcac.accept(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(rvaa.annotations())));
                                        case RuntimeVisibleTypeAnnotationsAttribute rvtaa -> rcac.accept(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(rvtaa.annotations(), null, null)));
                                        case SignatureAttribute sa -> rcac.accept(SignatureAttribute.of(sa.asTypeSignature()));
                                        default -> throw new AssertionError("Unexpected record component attribute: " + rca.attributeName());
                                    }}).toArray(Attribute[]::new))).toArray(RecordComponentInfo[]::new)));
                    case RuntimeInvisibleAnnotationsAttribute a -> clb.with(RuntimeInvisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                    case RuntimeInvisibleTypeAnnotationsAttribute a -> clb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                    case RuntimeVisibleAnnotationsAttribute a -> clb.with(RuntimeVisibleAnnotationsAttribute.of(transformAnnotations(a.annotations())));
                    case RuntimeVisibleTypeAnnotationsAttribute a -> clb.with(RuntimeVisibleTypeAnnotationsAttribute.of(transformTypeAnnotations(a.annotations(), null, null)));
                    case SignatureAttribute a -> clb.with(SignatureAttribute.of(a.asClassSignature()));
                    case SourceDebugExtensionAttribute a -> clb.with(SourceDebugExtensionAttribute.of(a.contents()));
                    case SourceFileAttribute a -> clb.with(SourceFileAttribute.of(a.sourceFile().stringValue()));
                    case SourceIDAttribute a -> clb.with(SourceIDAttribute.of(a.sourceId()));  //missing attribute factory method accepting String
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
            case AnnotationValue.OfConstant oc -> //missing distinction between constant annotation value types
                switch (oc.tag()) {
                    case 's' -> AnnotationValue.of((String)oc.constantValue());
                    case 'D' -> AnnotationValue.of((double)oc.constantValue());
                    case 'F' -> AnnotationValue.of((float)oc.constantValue());
                    case 'J' -> AnnotationValue.of((long)oc.constantValue());
                    case 'I' -> AnnotationValue.of((int)oc.constantValue());
                    case 'S' -> AnnotationValue.of((short)(int)oc.constantValue());
                    case 'C' -> AnnotationValue.of((char)(int)oc.constantValue());
                    case 'B' -> AnnotationValue.of((byte)(int)oc.constantValue());
                    case 'Z' -> AnnotationValue.of(1 == (int)oc.constantValue());
                    default ->  throw new AssertionError("Unexpected annotation value tag: " + oc.tag());
                };
            case AnnotationValue.OfClass oc -> AnnotationValue.ofClass(ClassDesc.ofDescriptor(oc.className().stringValue())); //missing AnnotationValue factory method accepting ClassDesc
            case AnnotationValue.OfEnum oe -> AnnotationValue.ofEnum(ClassDesc.ofDescriptor(oe.className().stringValue()), oe.constantName().stringValue());  //missing AnnotationValue factory method accepting ClassDesc
        };
    }

    static TypeAnnotation[] transformTypeAnnotations(List<TypeAnnotation> annotations, CodeBuilder cob, HashMap<Label, Label> labels) {
        return annotations.stream().map(ta -> TypeAnnotation.of( //missing TypeAnnotation factory method accepting ClassDesc and AnnotationElement vararg
                        transformTargetInfo(ta.targetInfo(), cob, labels),
                        ta.targetPath().stream().map(tpc -> TypeAnnotation.TypePathComponent.of(tpc.typePathKind().tag(), tpc.typeArgumentIndex())).toList(),
                        ta.className(),
                        ta.elements().stream().map(ae -> AnnotationElement.of(ae.name().stringValue(), transformAnnotationValue(ae.value()))).toList())).toArray(TypeAnnotation[]::new);
    }

    static TypeAnnotation.TargetInfo transformTargetInfo(TypeAnnotation.TargetInfo ti, CodeBuilder cob, HashMap<Label, Label> labels) {
        return switch (ti) { //missing flat decompositions to individual target types
            case TypeAnnotation.CatchTarget t -> TypeAnnotation.TargetInfo.ofExceptionParameter(t.exceptionTableIndex());
            case TypeAnnotation.EmptyTarget t ->
                switch (t.targetType()) {
                    case FIELD -> TypeAnnotation.TargetInfo.ofField();
                    case METHOD_RETURN -> TypeAnnotation.TargetInfo.ofMethodReturn();
                    case METHOD_RECEIVER -> TypeAnnotation.TargetInfo.ofMethodReceiver();
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
            case TypeAnnotation.FormalParameterTarget t -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(t.formalParameterIndex());
            case TypeAnnotation.SupertypeTarget t -> TypeAnnotation.TargetInfo.ofClassExtends(t.supertypeIndex());
            case TypeAnnotation.ThrowsTarget t -> TypeAnnotation.TargetInfo.ofThrows(t.throwsTargetIndex());
            case TypeAnnotation.TypeParameterBoundTarget t ->
                switch (t.targetType()) {
                    case CLASS_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofClassTypeParameterBound(t.typeParameterIndex(), t.boundIndex());
                    case METHOD_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofMethodTypeParameterBound(t.typeParameterIndex(), t.boundIndex());
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
            case TypeAnnotation.TypeParameterTarget t ->
                switch (t.targetType()) {
                    case CLASS_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofClassTypeParameter(t.typeParameterIndex());
                    case METHOD_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofMethodTypeParameter(t.typeParameterIndex());
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
            case TypeAnnotation.LocalVarTarget t ->
                switch (t.targetType()) {
                    case LOCAL_VARIABLE -> TypeAnnotation.TargetInfo.ofLocalVariable(t.table().stream().map(lvti ->
                            TypeAnnotation.LocalVarTargetInfo.of(labels.computeIfAbsent(lvti.startLabel(), l -> cob.newLabel()),
                            labels.computeIfAbsent(lvti.endLabel(), l -> cob.newLabel()), lvti.index())).toList());
                    case RESOURCE_VARIABLE -> TypeAnnotation.TargetInfo.ofResourceVariable(t.table().stream().map(lvti ->
                            TypeAnnotation.LocalVarTargetInfo.of(labels.computeIfAbsent(lvti.startLabel(), l -> cob.newLabel()),
                            labels.computeIfAbsent(lvti.endLabel(), l -> cob.newLabel()), lvti.index())).toList());
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
            case TypeAnnotation.OffsetTarget t ->
                switch (t.targetType()) {
                    case INSTANCEOF -> TypeAnnotation.TargetInfo.ofInstanceofExpr(labels.computeIfAbsent(t.target(), l -> cob.newLabel()));
                    case NEW -> TypeAnnotation.TargetInfo.ofNewExpr(labels.computeIfAbsent(t.target(), l -> cob.newLabel()));
                    case CONSTRUCTOR_REFERENCE -> TypeAnnotation.TargetInfo.ofConstructorReference(labels.computeIfAbsent(t.target(), l -> cob.newLabel()));
                    case METHOD_REFERENCE -> TypeAnnotation.TargetInfo.ofMethodReference(labels.computeIfAbsent(t.target(), l -> cob.newLabel()));
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
            case TypeAnnotation.TypeArgumentTarget t ->
                switch (t.targetType()) {
                    case CAST -> TypeAnnotation.TargetInfo.ofCastExpr(labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
                    case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorInvocationTypeArgument(labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
                    case METHOD_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodInvocationTypeArgument(labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
                    case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorReferenceTypeArgument(labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
                    case METHOD_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodReferenceTypeArgument(labels.computeIfAbsent(t.target(), l -> cob.newLabel()), t.typeArgumentIndex());
                    default ->  throw new AssertionError("Unexpected type annotation target type: " + t.targetType());
                };
        };
    }
}
