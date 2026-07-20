/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.AttributeMapper;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.classfile.impl.verifier.VerifierImpl;

import static java.util.Objects.requireNonNull;

public final class ClassFileImpl implements ClassFile {

    private Option stackMapsOption;
    private Option debugElementsOption;
    private Option lineNumbersOption;
    private Option attributesProcessingOption;
    private Option constantPoolSharingOption;
    private Option shortJumpsOption;
    private Option deadCodeOption;
    private Option deadLabelsOption;
    private Option classHierarchyResolverOption;
    private Option attributeMapperOption;

    private ClassFileImpl(Option stackMapsOption,
                          Option debugElementsOption,
                          Option lineNumbersOption,
                          Option attributesProcessingOption,
                          Option constantPoolSharingOption,
                          Option shortJumpsOption,
                          Option deadCodeOption,
                          Option deadLabelsOption,
                          Option classHierarchyResolverOption,
                          Option attributeMapperOption) {
        this.stackMapsOption              = stackMapsOption;
        this.debugElementsOption          = debugElementsOption;
        this.lineNumbersOption            = lineNumbersOption;
        this.attributesProcessingOption   = attributesProcessingOption;
        this.constantPoolSharingOption    = constantPoolSharingOption;
        this.shortJumpsOption             = shortJumpsOption;
        this.deadCodeOption               = deadCodeOption;
        this.deadLabelsOption             = deadLabelsOption;
        this.classHierarchyResolverOption = classHierarchyResolverOption;
        this.attributeMapperOption        = attributeMapperOption;
    }

    public static final ClassFileImpl DEFAULT_CONTEXT = new ClassFileImpl(
            null, // StackMapsOption.STACK_MAPS_WHEN_REQUIRED
            null, // DebugElementsOption.PASS_DEBUG,
            null, // LineNumbersOption.PASS_LINE_NUMBERS,
            null, // AttributesProcessingOption.PASS_ALL_ATTRIBUTES,
            null, // ConstantPoolSharingOption.SHARED_POOL,
            null, // ShortJumpsOption.FIX_SHORT_JUMPS,
            null, // DeadCodeOption.PATCH_DEAD_CODE,
            null, // DeadLabelsOption.FAIL_ON_DEAD_LABELS,
            null, // new ClassHierarchyResolverOptionImpl(ClassHierarchyResolver.defaultResolver()),
            null  // _ -> null
        );

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
            if (o instanceof StackMapsOption oo) {
                smo = oo;
            } else if (o instanceof ClassHierarchyResolverOption oo) {
                chro = oo;
            } else if (o instanceof DebugElementsOption oo) {
                deo = oo;
            } else if (o instanceof LineNumbersOption oo) {
                lno = oo;
            } else if (o instanceof AttributesProcessingOption oo) {
                apo = oo;
            } else if (o instanceof ConstantPoolSharingOption oo) {
                cpso = oo;
            } else if (o instanceof ShortJumpsOption oo) {
                sjo = oo;
            } else if (o instanceof DeadCodeOption oo) {
                dco = oo;
            } else if (o instanceof DeadLabelsOption oo) {
                dlo = oo;
            } else if (o instanceof AttributeMapperOption oo) {
                amo = oo;
            } else { // null or unknown Option type
                throw new IllegalArgumentException("Invalid option: " + requireNonNull(o));
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
        ConstantPoolBuilder constantPool = sharedConstantPool() ? ConstantPoolBuilder.of(model)
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

    public boolean sharedConstantPool() {
        return constantPoolSharingOption == null || constantPoolSharingOption == ConstantPoolSharingOption.SHARED_POOL;
    }

    @Override
    public List<VerifyError> verify(ClassModel model) {
        try {
            return VerifierImpl.verify(model, classHierarchyResolver(), null);
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

    public Function<Utf8Entry, AttributeMapper<?>> attributeMapper() {
        if (attributeMapperOption == null) {
            return _ -> null;
        } else {
            return ((AttributeMapperOption)attributeMapperOption).attributeMapper();
        }
    }

    public ClassHierarchyResolver classHierarchyResolver() {
        if (classHierarchyResolverOption == null) {
            return ClassHierarchyImpl.DEFAULT_RESOLVER;
        } else {
            return ((ClassHierarchyResolverOption)classHierarchyResolverOption).classHierarchyResolver();
        }
    }

    public boolean dropDeadLabels() {
        return (deadLabelsOption != null && deadLabelsOption == DeadLabelsOption.DROP_DEAD_LABELS);
    }

    public boolean passDebugElements() {
        return (debugElementsOption == null || debugElementsOption == DebugElementsOption.PASS_DEBUG);
    }

    public boolean passLineNumbers() {
        return (lineNumbersOption == null || lineNumbersOption == LineNumbersOption.PASS_LINE_NUMBERS);
    }

    public AttributesProcessingOption attributesProcessingOption() {
        return (attributesProcessingOption == null) ? AttributesProcessingOption.PASS_ALL_ATTRIBUTES : (AttributesProcessingOption)attributesProcessingOption;
    }

    public boolean fixShortJumps() {
        return (shortJumpsOption == null || shortJumpsOption == ShortJumpsOption.FIX_SHORT_JUMPS);
    }

    public boolean stackMapsWhenRequired() {
        return (stackMapsOption == null || stackMapsOption == StackMapsOption.STACK_MAPS_WHEN_REQUIRED);
    }

    public boolean generateStackMaps() {
        return (stackMapsOption == StackMapsOption.GENERATE_STACK_MAPS);
    }

    public boolean dropStackMaps() {
        return (stackMapsOption == StackMapsOption.DROP_STACK_MAPS);
    }

    public boolean patchDeadCode() {
        return (deadCodeOption == null || deadCodeOption == DeadCodeOption.PATCH_DEAD_CODE);
    }

    public record AttributeMapperOptionImpl(Function<Utf8Entry, AttributeMapper<?>> attributeMapper)
            implements AttributeMapperOption {
    }

    public record ClassHierarchyResolverOptionImpl(ClassHierarchyResolver classHierarchyResolver)
            implements ClassHierarchyResolverOption {
    }
}
