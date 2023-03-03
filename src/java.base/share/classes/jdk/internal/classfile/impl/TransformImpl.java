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
package jdk.internal.classfile.impl;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.ClassfileTransform;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.FieldBuilder;
import jdk.internal.classfile.FieldElement;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.FieldTransform;
import jdk.internal.classfile.MethodBuilder;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.MethodTransform;

/**
 * TransformImpl
 */
public class TransformImpl {
    // ClassTransform

    private TransformImpl() {
    }

    private static Runnable chainRunnable(Runnable a, Runnable b) {
        return () -> { a.run(); b.run(); };
    }

    private static final Runnable NOTHING = () -> { };

    interface UnresolvedClassTransform extends ClassTransform {
        @Override
        default void accept(ClassBuilder builder, ClassElement element) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atEnd(ClassBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atStart(ClassBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }
    }

    public record ClassTransformImpl(Consumer<ClassElement> consumer,
                                     Runnable endHandler,
                                     Runnable startHandler)
            implements ClassfileTransform.ResolvedTransform<ClassElement> {

        public ClassTransformImpl(Consumer<ClassElement> consumer) {
            this(consumer, NOTHING, NOTHING);
        }
    }

    public record ChainedClassTransform(ClassTransform t,
                                        ClassTransform next)
            implements UnresolvedClassTransform {
        @Override
        public ClassTransformImpl resolve(ClassBuilder builder) {
            ResolvedTransform<ClassElement> downstream = next.resolve(builder);
            ClassBuilder chainedBuilder = new ChainedClassBuilder(builder, downstream.consumer());
            ResolvedTransform<ClassElement> upstream = t.resolve(chainedBuilder);
            return new ClassTransformImpl(upstream.consumer(),
                                          chainRunnable(upstream.endHandler(), downstream.endHandler()),
                                          chainRunnable(upstream.startHandler(), downstream.startHandler()));
        }
    }

    public record SupplierClassTransform(Supplier<ClassTransform> supplier)
            implements UnresolvedClassTransform {
        @Override
        public ResolvedTransform<ClassElement> resolve(ClassBuilder builder) {
            return supplier.get().resolve(builder);
        }
    }

    public record ClassMethodTransform(MethodTransform transform,
                                       Predicate<MethodModel> filter)
            implements UnresolvedClassTransform {
        @Override
        public ClassTransformImpl resolve(ClassBuilder builder) {
            return new ClassTransformImpl(ce -> {
                if (ce instanceof MethodModel mm && filter.test(mm))
                    builder.transformMethod(mm, transform);
                else
                    builder.with(ce);
            });
        }

        @Override
        public ClassTransform andThen(ClassTransform next) {
            if (next instanceof ClassMethodTransform cmt)
                return new ClassMethodTransform(transform.andThen(cmt.transform),
                                                mm -> filter.test(mm) && cmt.filter.test(mm));
            else
                return UnresolvedClassTransform.super.andThen(next);
        }
    }

    public record ClassFieldTransform(FieldTransform transform,
                                      Predicate<FieldModel> filter)
            implements UnresolvedClassTransform {
        @Override
        public ClassTransformImpl resolve(ClassBuilder builder) {
            return new ClassTransformImpl(ce -> {
                if (ce instanceof FieldModel fm && filter.test(fm))
                    builder.transformField(fm, transform);
                else
                    builder.with(ce);
            });
        }

        @Override
        public ClassTransform andThen(ClassTransform next) {
            if (next instanceof ClassFieldTransform cft)
                return new ClassFieldTransform(transform.andThen(cft.transform),
                                               mm -> filter.test(mm) && cft.filter.test(mm));
            else
                return UnresolvedClassTransform.super.andThen(next);
        }
    }

    // MethodTransform

    interface UnresolvedMethodTransform extends MethodTransform {
        @Override
        default void accept(MethodBuilder builder, MethodElement element) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atEnd(MethodBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atStart(MethodBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }
    }

    public record MethodTransformImpl(Consumer<MethodElement> consumer,
                                      Runnable endHandler,
                                      Runnable startHandler)
            implements ClassfileTransform.ResolvedTransform<MethodElement> {
    }

    public record ChainedMethodTransform(MethodTransform t,
                                         MethodTransform next)
            implements TransformImpl.UnresolvedMethodTransform {
        @Override
        public ResolvedTransform<MethodElement> resolve(MethodBuilder builder) {
            ResolvedTransform<MethodElement> downstream = next.resolve(builder);
            MethodBuilder chainedBuilder = new ChainedMethodBuilder(builder, downstream.consumer());
            ResolvedTransform<MethodElement> upstream = t.resolve(chainedBuilder);
            return new MethodTransformImpl(upstream.consumer(),
                                           chainRunnable(upstream.endHandler(), downstream.endHandler()),
                                           chainRunnable(upstream.startHandler(), downstream.startHandler()));
        }
    }

    public record SupplierMethodTransform(Supplier<MethodTransform> supplier)
            implements TransformImpl.UnresolvedMethodTransform {
        @Override
        public ResolvedTransform<MethodElement> resolve(MethodBuilder builder) {
            return supplier.get().resolve(builder);
        }
    }

    public record MethodCodeTransform(CodeTransform xform)
            implements TransformImpl.UnresolvedMethodTransform {
        @Override
        public ResolvedTransform<MethodElement> resolve(MethodBuilder builder) {
            return new MethodTransformImpl(me -> {
                if (me instanceof CodeModel cm) {
                    builder.transformCode(cm, xform);
                }
                else {
                    builder.with(me);
                }
            }, NOTHING, NOTHING);
        }

        @Override
        public MethodTransform andThen(MethodTransform next) {
            return (next instanceof TransformImpl.MethodCodeTransform mct)
                   ? new TransformImpl.MethodCodeTransform(xform.andThen(mct.xform))
                   : UnresolvedMethodTransform.super.andThen(next);

        }
    }

    // FieldTransform

    interface UnresolvedFieldTransform extends FieldTransform {
        @Override
        default void accept(FieldBuilder builder, FieldElement element) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atEnd(FieldBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atStart(FieldBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }
    }

    public record FieldTransformImpl(Consumer<FieldElement> consumer,
                                     Runnable endHandler,
                                     Runnable startHandler)
            implements ClassfileTransform.ResolvedTransform<FieldElement> {
    }

    public record ChainedFieldTransform(FieldTransform t, FieldTransform next)
            implements UnresolvedFieldTransform {
        @Override
        public FieldTransformImpl resolve(FieldBuilder builder) {
            ResolvedTransform<FieldElement> downstream = next.resolve(builder);
            FieldBuilder chainedBuilder = new ChainedFieldBuilder(builder, downstream.consumer());
            ResolvedTransform<FieldElement> upstream = t.resolve(chainedBuilder);
            return new FieldTransformImpl(upstream.consumer(),
                                           chainRunnable(upstream.endHandler(), downstream.endHandler()),
                                           chainRunnable(upstream.startHandler(), downstream.startHandler()));
        }
    }

    public record SupplierFieldTransform(Supplier<FieldTransform> supplier)
            implements UnresolvedFieldTransform {
        @Override
        public ResolvedTransform<FieldElement> resolve(FieldBuilder builder) {
            return supplier.get().resolve(builder);
        }
    }

    // CodeTransform

    interface UnresolvedCodeTransform extends CodeTransform {
        @Override
        default void accept(CodeBuilder builder, CodeElement element) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atEnd(CodeBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }

        @Override
        default void atStart(CodeBuilder builder) {
            throw new UnsupportedOperationException("transforms must be resolved before running");
        }
    }

    public record CodeTransformImpl(Consumer<CodeElement> consumer,
                                    Runnable endHandler,
                                    Runnable startHandler)
            implements ClassfileTransform.ResolvedTransform<CodeElement> {
    }

    public record ChainedCodeTransform(CodeTransform t, CodeTransform next)
            implements UnresolvedCodeTransform {
        @Override
        public CodeTransformImpl resolve(CodeBuilder builder) {
            ResolvedTransform<CodeElement> downstream = next.resolve(builder);
            CodeBuilder chainedBuilder = new ChainedCodeBuilder(builder, downstream.consumer());
            ResolvedTransform<CodeElement> upstream = t.resolve(chainedBuilder);
            return new CodeTransformImpl(upstream.consumer(),
                                         chainRunnable(upstream.endHandler(), downstream.endHandler()),
                                         chainRunnable(upstream.startHandler(), downstream.startHandler()));
        }
    }

    public record SupplierCodeTransform(Supplier<CodeTransform> supplier)
            implements UnresolvedCodeTransform {
        @Override
        public ResolvedTransform<CodeElement> resolve(CodeBuilder builder) {
            return supplier.get().resolve(builder);
        }
    }
}