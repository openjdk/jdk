/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.classfile.impl;

import java.util.List;
import java.util.function.Function;
import java.util.function.Consumer;

import java.lang.classfile.AttributeMapper;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFile.*;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.verifier.VerifierImpl;

public record ClassFileImpl(StackMapsOption stackMapsOption,
                            DebugElementsOption debugElementsOption,
                            LineNumbersOption lineNumbersOption,
                            AttributesProcessingOption attributesProcessingOption,
                            ConstantPoolSharingOption constantPoolSharingOption,
                            ShortJumpsOption shortJumpsOption,
                            DeadCodeOption deadCodeOption,
                            DeadLabelsOption deadLabelsOption,
                            ClassHierarchyResolverOption classHierarchyResolverOption,
                            AttributeMapperOption attributeMapperOption) implements ClassFile {

    public static final ClassFileImpl DEFAULT_CONTEXT = new ClassFileImpl(
            StackMapsOption.STACK_MAPS_WHEN_REQUIRED,
            DebugElementsOption.PASS_DEBUG,
            LineNumbersOption.PASS_LINE_NUMBERS,
            AttributesProcessingOption.PASS_ALL_ATTRIBUTES,
            ConstantPoolSharingOption.SHARED_POOL,
            ShortJumpsOption.FIX_SHORT_JUMPS,
            DeadCodeOption.PATCH_DEAD_CODE,
            DeadLabelsOption.FAIL_ON_DEAD_LABELS,
            new ClassHierarchyResolverOptionImpl(ClassHierarchyResolver.defaultResolver()),
            new AttributeMapperOptionImpl(new Function<>() {
                @Override
                public AttributeMapper<?> apply(Utf8Entry k) {
                    return null;
                }
            }));

    @SuppressWarnings("unchecked")
    @Override
    public ClassFileImpl withOptions(Option... options) {
        var smo = stackMapsOption;
        var deo = debugElementsOption;
        var lno = lineNumbersOption;
        var apo = attributesProcessingOption;
        var cpso = constantPoolSharingOption;
        var sjo = shortJumpsOption;
        var dco = deadCodeOption;
        var dlo = deadLabelsOption;
        var chro = classHierarchyResolverOption;
        var amo = attributeMapperOption;
        for (var o : options) {
            switch (o) {
                case StackMapsOption oo -> smo = oo;
                case DebugElementsOption oo -> deo = oo;
                case LineNumbersOption oo -> lno = oo;
                case AttributesProcessingOption oo -> apo = oo;
                case ConstantPoolSharingOption oo -> cpso = oo;
                case ShortJumpsOption oo -> sjo = oo;
                case DeadCodeOption oo -> dco = oo;
                case DeadLabelsOption oo -> dlo = oo;
                case ClassHierarchyResolverOption oo -> chro = oo;
                case AttributeMapperOption oo -> amo = oo;
            }
        }
        return new ClassFileImpl(smo, deo, lno, apo, cpso, sjo, dco, dlo, chro, amo);
    }

    @Override
    public ClassModel parse(byte[] bytes) {
        return new ClassImpl(bytes, this);
    }

    @Override
    public byte[] build(ClassEntry thisClassEntry,
                         ConstantPoolBuilder constantPool,
                         Consumer<? super ClassBuilder> handler) {
        thisClassEntry = AbstractPoolEntry.maybeClone(constantPool, thisClassEntry);
        DirectClassBuilder builder = new DirectClassBuilder((SplitConstantPool)constantPool, this, thisClassEntry);
        handler.accept(builder);
        return builder.build();
    }

    @Override
    public byte[] transformClass(ClassModel model, ClassEntry newClassName, ClassTransform transform) {
        ConstantPoolBuilder constantPool = constantPoolSharingOption() == ConstantPoolSharingOption.SHARED_POOL
                                                                     ? ConstantPoolBuilder.of(model)
                                                                     : ConstantPoolBuilder.of();
        return build(newClassName, constantPool,
                new Consumer<ClassBuilder>() {
                    @Override
                    public void accept(ClassBuilder builder) {
                        ((DirectClassBuilder) builder).setOriginal((ClassImpl)model);
                        ((DirectClassBuilder) builder).setSizeHint(((ClassImpl)model).classfileLength());
                        builder.transform((ClassImpl)model, transform);
                    }
                });
    }

    @Override
    public List<VerifyError> verify(ClassModel model) {
        try {
            return VerifierImpl.verify(model, classHierarchyResolverOption().classHierarchyResolver(), null);
        } catch (IllegalArgumentException verifierInitializationError) {
            return List.of(new VerifyError(verifierInitializationError.getMessage()));
        }
    }

    @Override
    public List<VerifyError> verify(byte[] bytes) {
        try {
            return verify(parse(bytes));
        } catch (IllegalArgumentException parsingError) {
            return List.of(new VerifyError(parsingError.getMessage()));
        }
    }

    public record AttributeMapperOptionImpl(Function<Utf8Entry, AttributeMapper<?>> attributeMapper)
            implements AttributeMapperOption {
    }

    public record ClassHierarchyResolverOptionImpl(ClassHierarchyResolver classHierarchyResolver)
            implements ClassHierarchyResolverOption {
    }
}
