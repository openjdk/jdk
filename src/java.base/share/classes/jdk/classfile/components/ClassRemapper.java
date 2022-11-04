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
 *
 */
package jdk.classfile.components;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import jdk.classfile.Annotation;
import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import jdk.classfile.ClassBuilder;
import jdk.classfile.ClassElement;
import jdk.classfile.ClassModel;
import jdk.classfile.ClassSignature;
import jdk.classfile.ClassTransform;
import jdk.classfile.Classfile;
import jdk.classfile.CodeBuilder;
import jdk.classfile.CodeElement;
import jdk.classfile.CodeModel;
import jdk.classfile.CodeTransform;
import jdk.classfile.FieldBuilder;
import jdk.classfile.FieldElement;
import jdk.classfile.FieldModel;
import jdk.classfile.FieldTransform;
import jdk.classfile.Interfaces;
import jdk.classfile.MethodBuilder;
import jdk.classfile.MethodElement;
import jdk.classfile.impl.TemporaryConstantPool;
import jdk.classfile.instruction.FieldInstruction;
import jdk.classfile.instruction.InvokeDynamicInstruction;
import jdk.classfile.instruction.InvokeInstruction;
import jdk.classfile.instruction.NewMultiArrayInstruction;
import jdk.classfile.instruction.NewObjectInstruction;
import jdk.classfile.instruction.NewReferenceArrayInstruction;
import jdk.classfile.instruction.TypeCheckInstruction;
import jdk.classfile.MethodModel;
import jdk.classfile.MethodSignature;
import jdk.classfile.MethodTransform;
import jdk.classfile.Signature;
import jdk.classfile.Superclass;
import jdk.classfile.TypeAnnotation;
import jdk.classfile.attribute.EnclosingMethodAttribute;
import jdk.classfile.attribute.ExceptionsAttribute;
import jdk.classfile.attribute.InnerClassInfo;
import jdk.classfile.attribute.InnerClassesAttribute;
import jdk.classfile.attribute.ModuleAttribute;
import jdk.classfile.attribute.ModuleProvideInfo;
import jdk.classfile.attribute.RecordAttribute;
import jdk.classfile.attribute.RecordComponentInfo;
import jdk.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.SignatureAttribute;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.instruction.ExceptionCatch;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.LocalVariableType;
import jdk.classfile.impl.Util;
import jdk.classfile.instruction.ConstantInstruction.LoadConstantInstruction;

/**
 * ClassRemapper is a {@link jdk.classfile.ClassTransform}, {@link jdk.classfile.FieldTransform},
 * {@link jdk.classfile.MethodTransform} and {@link jdk.classfile.CodeTransform}
 * deeply re-mapping all class references in any form, according to given map or map function.
 * <p>
 * The re-mapping is applied to superclass, interfaces, all kinds of descriptors and signatures,
 * all attributes referencing classes in any form (including all types of annotations),
 * and to all instructions referencing to classes.
 * <p>
 * Primitive types and arrays are never subjects of mapping and are not allowed targets of mapping.
 * <p>
 * Arrays of reference types are always decomposed, mapped as the base reference types and composed back to arrays.
 */
public sealed interface ClassRemapper extends ClassTransform {

    /**
     * Creates new instance of ClassRemapper instructed with a class map.
     * Map may contain only re-mapping entries, identity mapping is applied by default.
     * @param classMap class map
     * @return new instance of ClassRemapper
     */
    static ClassRemapper of(Map<ClassDesc, ClassDesc> classMap) {
        return of(desc -> classMap.getOrDefault(desc, desc));
    }

    /**
     * Creates new instance of ClassRemapper instructed with a map function.
     * Map function must return valid {@link java.lang.constant.ClassDesc} of an interface
     * or a class, even for identity mappings.
     * @param mapFunction class map function
     * @return new instance of ClassRemapper
     */
    static ClassRemapper of(Function<ClassDesc, ClassDesc> mapFunction) {
        return new ClassRemapperImpl(mapFunction);
    }

    /**
     * Access method to internal class mapping function.
     * @param desc source class
     * @return class target class
     */
    ClassDesc map(ClassDesc desc);

    /**
     * Returns this ClassRemapper as {@link jdk.classfile.FieldTransform} instance
     * @return this ClassRemapper as {@link jdk.classfile.FieldTransform} instance
     */
    FieldTransform asFieldTransform();

    /**
     * Returns this ClassRemapper as {@link jdk.classfile.MethodTransform} instance
     * @return this ClassRemapper as {@link jdk.classfile.MethodTransform} instance
     */
    MethodTransform asMethodTransform();

    /**
     * Returns this ClassRemapper as {@link jdk.classfile.CodeTransform} instance
     * @return this ClassRemapper as {@link jdk.classfile.CodeTransform} instance
     */
    CodeTransform asCodeTransform();

    /**
     * Remaps the whole ClassModel into a new class file, including the class name.
     * @param clm class model to re-map
     * @return re-mapped class file bytes
     */
    default byte[] remapClass(ClassModel clm) {
        return Classfile.build(map(clm.thisClass().asSymbol()),
                clb -> clm.forEachElement(resolve(clb).consumer()));
    }

    record ClassRemapperImpl(Function<ClassDesc, ClassDesc> mapFunction) implements ClassRemapper {

        @Override
        public void accept(ClassBuilder clb, ClassElement cle) {
            switch (cle) {
                case FieldModel fm ->
                    clb.withField(fm.fieldName().stringValue(), map(fm.fieldTypeSymbol()), fb -> fm.forEachElement(asFieldTransform().resolve(fb).consumer()));
                case MethodModel mm ->
                    clb.withMethod(mm.methodName().stringValue(), mapMethodDesc(mm.methodTypeSymbol()), mm.flags().flagsMask(), mb -> mm.forEachElement(asMethodTransform().resolve(mb).consumer()));
                case Superclass sc ->
                    clb.withSuperclass(map(sc.superclassEntry().asSymbol()));
                case Interfaces ins ->
                    clb.withInterfaceSymbols(Util.mappedList(ins.interfaces(), in -> map(in.asSymbol())));
                case SignatureAttribute sa ->
                    clb.with(SignatureAttribute.of(mapClassSignature(sa.asClassSignature())));
                case InnerClassesAttribute ica ->
                    clb.with(InnerClassesAttribute.of(ica.classes().stream().map(ici ->
                            InnerClassInfo.of(map(ici.innerClass().asSymbol()),
                                    ici.outerClass().map(oc -> map(oc.asSymbol())),
                                    ici.innerName().map(Utf8Entry::stringValue),
                                    ici.flagsMask())).toList()));
                case EnclosingMethodAttribute ema ->
                    clb.with(EnclosingMethodAttribute.of(map(ema.enclosingClass().asSymbol()),
                            ema.enclosingMethodName().map(Utf8Entry::stringValue),
                            ema.enclosingMethodTypeSymbol().map(this::mapMethodDesc)));
                case RecordAttribute ra ->
                    clb.with(RecordAttribute.of(ra.components().stream().map(this::mapRecordComponent).toList()));
                case ModuleAttribute ma ->
                    clb.with(ModuleAttribute.of(ma.moduleName(), ma.moduleFlagsMask(), ma.moduleVersion().orElse(null),
                            ma.requires(), ma.exports(), ma.opens(),
                            ma.uses().stream().map(ce -> clb.constantPool().classEntry(map(ce.asSymbol()))).toList(),
                            ma.provides().stream().map(mp -> ModuleProvideInfo.of(map(mp.provides().asSymbol()),
                                    mp.providesWith().stream().map(pw -> map(pw.asSymbol())).toList())).toList()));
                case RuntimeVisibleAnnotationsAttribute aa ->
                    clb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                case RuntimeInvisibleAnnotationsAttribute aa ->
                    clb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                case RuntimeVisibleTypeAnnotationsAttribute aa ->
                    clb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                    clb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                default ->
                    clb.with(cle);
            }
        }

        @Override
        public FieldTransform asFieldTransform() {
            return (FieldBuilder fb, FieldElement fe) -> {
                switch (fe) {
                    case SignatureAttribute sa ->
                        fb.with(SignatureAttribute.of(mapSignature(sa.asTypeSignature())));
                    case RuntimeVisibleAnnotationsAttribute aa ->
                        fb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                    case RuntimeInvisibleAnnotationsAttribute aa ->
                        fb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                    case RuntimeVisibleTypeAnnotationsAttribute aa ->
                        fb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                        fb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    default ->
                        fb.with(fe);
                }
            };
        }

        @Override
        public MethodTransform asMethodTransform() {
            return (MethodBuilder mb, MethodElement me) -> {
                switch (me) {
                    case CodeModel com ->
                        mb.transformCode(com, asCodeTransform());
                    case ExceptionsAttribute ea ->
                        mb.with(ExceptionsAttribute.ofSymbols(ea.exceptions().stream().map(ce -> map(ce.asSymbol())).toList()));
                    case SignatureAttribute sa ->
                        mb.with(SignatureAttribute.of(mapMethodSignature(sa.asMethodSignature())));
                    case RuntimeVisibleAnnotationsAttribute aa ->
                        mb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                    case RuntimeInvisibleAnnotationsAttribute aa ->
                        mb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations())));
                    case RuntimeVisibleParameterAnnotationsAttribute paa ->
                        mb.with(RuntimeVisibleParameterAnnotationsAttribute.of(paa.parameterAnnotations().stream().map(this::mapAnnotations).toList()));
                    case RuntimeInvisibleParameterAnnotationsAttribute paa ->
                        mb.with(RuntimeInvisibleParameterAnnotationsAttribute.of(paa.parameterAnnotations().stream().map(this::mapAnnotations).toList()));
                    case RuntimeVisibleTypeAnnotationsAttribute aa ->
                        mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                        mb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    default ->
                        mb.with(me);
                }
            };
        }

        @Override
        public CodeTransform asCodeTransform() {
            return (CodeBuilder cob, CodeElement coe) -> {
                switch (coe) {
                    case FieldInstruction fai ->
                        cob.fieldInstruction(fai.opcode(), map(fai.owner().asSymbol()), fai.name().stringValue(), map(fai.typeSymbol()));
                    case InvokeInstruction ii ->
                        cob.invokeInstruction(ii.opcode(), map(ii.owner().asSymbol()), ii.name().stringValue(), mapMethodDesc(ii.typeSymbol()), ii.isInterface());
                    case InvokeDynamicInstruction idi ->
                        cob.invokeDynamicInstruction(DynamicCallSiteDesc.of(idi.bootstrapMethod(), idi.name().stringValue(), mapMethodDesc(idi.typeSymbol())));
                    case NewObjectInstruction c ->
                        cob.newObjectInstruction(map(c.className().asSymbol()));
                    case NewReferenceArrayInstruction c ->
                        cob.anewarray(map(c.componentType().asSymbol()));
                    case NewMultiArrayInstruction c ->
                        cob.multianewarray(map(c.arrayType().asSymbol()), c.dimensions());
                    case TypeCheckInstruction c ->
                        cob.typeCheckInstruction(c.opcode(), map(c.type().asSymbol()));
                    case ExceptionCatch c ->
                        cob.exceptionCatch(c.tryStart(), c.tryEnd(), c.handler(), c.catchType().map(d -> TemporaryConstantPool.INSTANCE.classEntry(map(d.asSymbol()))));
                    case LocalVariable c ->
                        cob.localVariable(c.slot(), c.name().stringValue(), map(c.typeSymbol()), c.startScope(), c.endScope());
                    case LocalVariableType c ->
                        cob.localVariableType(c.slot(), c.name().stringValue(), mapSignature(c.signatureSymbol()), c.startScope(), c.endScope());
                    case LoadConstantInstruction ldc ->
                        cob.constantInstruction(ldc.opcode(), mapConstantValue(ldc.constantValue()));
                    case RuntimeVisibleTypeAnnotationsAttribute aa ->
                        cob.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                        cob.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations())));
                    default ->
                        cob.with(coe);
                }
            };
        }

        @Override
        public ClassDesc map(ClassDesc desc) {
            if (desc == null) return null;
            if (desc.isArray()) return map(desc.componentType()).arrayType();
            if (desc.isPrimitive()) return desc;
            return mapFunction.apply(desc);
        }

        MethodTypeDesc mapMethodDesc(MethodTypeDesc desc) {
            return MethodTypeDesc.of(map(desc.returnType()), desc.parameterList().stream().map(this::map).toArray(ClassDesc[]::new));
        }

        ClassSignature mapClassSignature(ClassSignature signature) {
            return ClassSignature.of(signature.typeParameters(),
                    mapSignature(signature.superclassSignature()),
                    signature.superinterfaceSignatures().stream().map(this::mapSignature).toArray(Signature.RefTypeSig[]::new));
        }

        MethodSignature mapMethodSignature(MethodSignature signature) {
            return MethodSignature.of(signature.typeParameters(),
                    signature.throwableSignatures().stream().map(this::mapSignature).toList(),
                    mapSignature(signature.result()),
                    signature.arguments().stream().map(this::mapSignature).toArray(Signature[]::new));
        }

        RecordComponentInfo mapRecordComponent(RecordComponentInfo component) {
            return RecordComponentInfo.of(component.name().stringValue(), map(component.descriptorSymbol()),
                    component.attributes().stream().map(atr ->
                        switch (atr) {
                            case SignatureAttribute sa ->
                                SignatureAttribute.of(mapSignature(sa.asTypeSignature()));
                            case RuntimeVisibleAnnotationsAttribute aa ->
                                RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations()));
                            case RuntimeInvisibleAnnotationsAttribute aa ->
                                RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(aa.annotations()));
                            case RuntimeVisibleTypeAnnotationsAttribute aa ->
                                RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations()));
                            case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                                RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(aa.annotations()));
                            default -> atr;
                        }).toList());
        }

        DirectMethodHandleDesc mapDirectMethodHandle(DirectMethodHandleDesc dmhd) {
            return switch (dmhd.kind()) {
                case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER ->
                    MethodHandleDesc.ofField(dmhd.kind(), map(dmhd.owner()), dmhd.methodName(), map(ClassDesc.ofDescriptor(dmhd.lookupDescriptor())));
                default ->
                    MethodHandleDesc.ofMethod(dmhd.kind(), map(dmhd.owner()), dmhd.methodName(), mapMethodDesc(MethodTypeDesc.ofDescriptor(dmhd.lookupDescriptor())));
            };
        }

        ConstantDesc mapConstantValue(ConstantDesc value) {
            return switch (value) {
                case ClassDesc cd ->
                    map(cd);
                case DynamicConstantDesc<?> dcd ->
                    mapDynamicConstant(dcd);
                case DirectMethodHandleDesc dmhd ->
                    mapDirectMethodHandle(dmhd);
                case MethodTypeDesc mtd ->
                    mapMethodDesc(mtd);
                default -> value;
            };
        }

        DynamicConstantDesc<?> mapDynamicConstant(DynamicConstantDesc<?> dcd) {
            return DynamicConstantDesc.ofNamed(mapDirectMethodHandle(dcd.bootstrapMethod()),
                    dcd.constantName(),
                    map(dcd.constantType()),
                    dcd.bootstrapArgsList().stream().map(this::mapConstantValue).toArray(ConstantDesc[]::new));
        }

        @SuppressWarnings("unchecked")
        <S extends Signature> S mapSignature(S signature) {
            return (S) switch (signature) {
                case Signature.ArrayTypeSig ats ->
                    Signature.ArrayTypeSig.of(mapSignature(ats.componentSignature()));
                case Signature.ClassTypeSig cts ->
                    Signature.ClassTypeSig.of(cts.outerType().map(this::mapSignature).orElse(null),
                            map(cts.classDesc()), cts.typeArgs().stream().map(this::mapSignature).toArray(Signature[]::new));
                default -> signature;
            };
        }

        List<Annotation> mapAnnotations(List<Annotation> annotations) {
            return annotations.stream().map(this::mapAnnotation).toList();
        }

        Annotation mapAnnotation(Annotation a) {
            return Annotation.of(map(a.classSymbol()), a.elements().stream().map(el -> AnnotationElement.of(el.name(), mapAnnotationValue(el.value()))).toList());
        }

        AnnotationValue mapAnnotationValue(AnnotationValue val) {
            return switch (val) {
                case AnnotationValue.OfAnnotation oa -> AnnotationValue.ofAnnotation(mapAnnotation(oa.annotation()));
                case AnnotationValue.OfArray oa -> AnnotationValue.ofArray(oa.values().stream().map(this::mapAnnotationValue).toList());
                case AnnotationValue.OfConstant oc -> oc;
                case AnnotationValue.OfClass oc -> AnnotationValue.ofClass(map(oc.classSymbol()));
                case AnnotationValue.OfEnum oe -> AnnotationValue.ofEnum(map(oe.classSymbol()), oe.constantName().stringValue());
            };
        }

        List<TypeAnnotation> mapTypeAnnotations(List<TypeAnnotation> typeAnnotations) {
            return typeAnnotations.stream().map(a -> TypeAnnotation.of(a.targetInfo(), a.targetPath(), map(a.classSymbol()),
                    a.elements().stream().map(el -> AnnotationElement.of(el.name(), mapAnnotationValue(el.value()))).toList())).toList();
        }
    }
}
