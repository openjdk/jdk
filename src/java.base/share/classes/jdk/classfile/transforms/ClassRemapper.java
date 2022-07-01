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
package jdk.classfile.transforms;

import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Map;
import java.util.function.Function;
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
import jdk.classfile.instruction.NewPrimitiveArrayInstruction;
import jdk.classfile.instruction.NewReferenceArrayInstruction;
import jdk.classfile.instruction.TypeCheckInstruction;
import jdk.classfile.MethodModel;
import jdk.classfile.MethodSignature;
import jdk.classfile.MethodTransform;
import jdk.classfile.Signature;
import jdk.classfile.Superclass;
import jdk.classfile.attribute.ExceptionsAttribute;
import jdk.classfile.attribute.SignatureAttribute;
import jdk.classfile.instruction.ExceptionCatch;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.LocalVariableType;
import jdk.classfile.impl.Util;

/**
 *
 */
public sealed interface ClassRemapper {

    static ClassRemapper of(Map<ClassDesc, ClassDesc> classMap) {
        return of(desc -> classMap.getOrDefault(desc, desc));
    }

    static ClassRemapper of(Function<ClassDesc, ClassDesc> mapFunction) {
        return new ClassRemapperImpl(mapFunction);
    }

    ClassDesc map(ClassDesc desc);

    ClassTransform classTransform();

    FieldTransform fieldTransform();

    MethodTransform methodTransform();

    CodeTransform codeTransform();

    default byte[] remapClass(ClassModel clm) {
        return Classfile.build(map(clm.thisClass().asSymbol()),
                clb -> clm.forEachElement(classTransform().resolve(clb).consumer()));
    }

    final static class ClassRemapperImpl implements ClassRemapper {

        private final Function<ClassDesc, ClassDesc> mapFunction;

        ClassRemapperImpl(Function<ClassDesc, ClassDesc> mapFunction) {
            this.mapFunction = mapFunction;
        }

        @Override
        public ClassTransform classTransform() {
            return (ClassBuilder clb, ClassElement cle) -> {
                switch (cle) {
                    case FieldModel fm ->
                        clb.withField(fm.fieldName().stringValue(), map(fm.fieldTypeSymbol()), fb -> fm.forEachElement(fieldTransform().resolve(fb).consumer()));
                    case MethodModel mm ->
                        clb.withMethod(mm.methodName().stringValue(), mapMethodDesc(mm.methodTypeSymbol()), mm.flags().flagsMask(), mb -> mm.forEachElement(methodTransform().resolve(mb).consumer()));
                    case Superclass sc ->
                        clb.withSuperclass(map(sc.superclassEntry().asSymbol()));
                    case Interfaces ins ->
                        clb.withInterfaceSymbols(Util.mappedList(ins.interfaces(), in -> map(in.asSymbol())));
                    case SignatureAttribute sa ->
                        clb.with(SignatureAttribute.of(mapClassSignature(sa.asClassSignature())));
                    default ->
                        clb.with(cle);
                }
            };
        }

        @Override
        public FieldTransform fieldTransform() {
            return (FieldBuilder fb, FieldElement fe) -> {
                switch (fe) {
                    case SignatureAttribute sa ->
                        fb.with(SignatureAttribute.of(mapSignature(sa.asTypeSignature())));
                    default ->
                        fb.with(fe);
                }
            };
        }

        @Override
        public MethodTransform methodTransform() {
            return (MethodBuilder mb, MethodElement me) -> {
                switch (me) {
                    case CodeModel com ->
                        mb.transformCode(com, codeTransform());
                    case ExceptionsAttribute ea ->
                        mb.with(ExceptionsAttribute.ofSymbols(ea.exceptions().stream().map(ce -> map(ce.asSymbol())).toList()));
                    case SignatureAttribute sa ->
                        mb.with(SignatureAttribute.of(mapMethodSignature(sa.asMethodSignature())));
                    default ->
                        mb.with(me);
                }
            };
        }

        @Override
        public CodeTransform codeTransform() {
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
                    case NewPrimitiveArrayInstruction c ->
                        cob.newPrimitiveArrayInstruction(c.typeKind());
                    case NewReferenceArrayInstruction c ->
                        cob.anewarray(c.componentType().asSymbol());
                    case NewMultiArrayInstruction c ->
                        cob.multianewarray(c.arrayType().asSymbol(), c.dimensions());
                    case TypeCheckInstruction c ->
                        cob.typeCheckInstruction(c.opcode(), map(c.type().asSymbol()));
                    case ExceptionCatch c ->
                        cob.exceptionCatch(c.tryStart(), c.tryEnd(), c.handler(), c.catchType().map(d -> TemporaryConstantPool.INSTANCE.classEntry(TemporaryConstantPool.INSTANCE.utf8Entry(Util.toInternalName(map(d.asSymbol()))))));
                    case LocalVariable c ->
                        cob.localVariable(c.slot(), c.name().stringValue(), map(c.typeSymbol()), c.startScope(), c.endScope());
                    case LocalVariableType c ->
                        cob.localVariableType(c.slot(), c.name().stringValue(), mapSignature(c.signatureSymbol()), c.startScope(), c.endScope());
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
                    signature.arguments().stream().map(this::mapSignature).toList(),
                    mapSignature(signature.result()),
                    signature.throwableSignatures().stream().map(this::mapSignature).toList());
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
    }
}
