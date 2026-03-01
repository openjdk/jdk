/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tracing;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.Bytecode.MethodDesc;
import jdk.jfr.tracing.MethodTracer;

/**
 * Class that transforms the bytecode of a method so it can call the appropriate
 * methods in the jdk.jfr.tracing.MethodTracer class.
 * <p>
 * The method ID is determined by native code.
 */
final class Transform implements CodeTransform {
    private static class TryBlock {
        Label start;
        Label end;
    }
    private static final ClassDesc METHOD_TRACER_CLASS = ClassDesc.of(MethodTracer.class.getName());
    private static final MethodDesc TRACE_METHOD = MethodDesc.of("trace", "(JJ)V");
    private static final MethodDesc TIMING_METHOD = MethodDesc.of("timing", "(JJ)V");
    private static final MethodDesc TRACE_TIMING_METHOD = MethodDesc.of("traceTiming", "(JJ)V");
    private static final MethodDesc TIMESTAMP_METHOD = MethodDesc.of("timestamp", "()J");

    private final List<TryBlock> tryBlocks = new ArrayList<>();
    private final boolean simplifiedInstrumentation;
    private final ClassModel classModel;
    private final Method method;
    private int timestampSlot = -1;

    Transform(ClassModel classModel, CodeModel model, Method method) {
        this.method = method;
        this.classModel = classModel;
        // The JVMS (not the JLS) allows multiple mutually exclusive super/this.<init>
        // invocations in a constructor body as long as only one lies on any given
        // execution path. For example, this is valid bytecode:
        //
        // Foo(boolean value) {
        //   if (value) {
        //     staticMethodThatMayThrow();
        //     super();
        //   } else {
        //     try {
        //       if (value == 0) {
        //         throw new Exception("");
        //       }
        //     } catch (Throwable t) {
        //       throw t;
        //     }
        //     super();
        //   }
        // }
        //
        // If such a method is found, instrumentation falls back to instrumenting only
        // RET and ATHROW. This can cause exceptions to be missed or counted twice.
        //
        // An effect of this heuristic is that constructors like the one below
        // will also trigger simplified instrumentation.
        //
        // class Bar {
        // }
        //
        // class Foo extends Bar {
        //   Foo() {
        //     new Bar();
        //   }
        // }
        //
        // java.lang.Object::<init> with zero constructor invocations should use simplified instrumentation
        this.simplifiedInstrumentation = method.constructor() && constructorInvocations(model.elementList()) != 1;
    }

    private int constructorInvocations(List<CodeElement> elementList) {
        int count = 0;
        for (CodeElement e : elementList) {
            if (isConstructorInvocation(e)) {
                count++;
            }
        }
        return count;
    }

    private boolean isConstructorInvocation(CodeElement element) {
        if (element instanceof InvokeInstruction inv && inv.name().equalsString("<init>")) {
            if (classModel.thisClass().equals(inv.owner())) {
                return true;
            }
            if (classModel.superclass().isPresent()) {
                return classModel.superclass().get().equals(inv.owner());
            }
        }
        return false;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
        if (simplifiedInstrumentation) {
            acceptSimplifiedInstrumentation(builder, element);
            return;
        }
        if (method.constructor()) {
            acceptConstructor(builder, element, isConstructorInvocation(element));
        } else {
            acceptMethod(builder, element);
        }
    }

    @Override
    public void atEnd(CodeBuilder builder) {
        endTryBlock(builder);
        for (TryBlock block : tryBlocks) {
            addCatchHandler(block, builder);
        }
    }

    private void acceptConstructor(CodeBuilder builder, CodeElement element, boolean isConstructorInvocation) {
        if (timestampSlot == -1) {
            timestampSlot = invokeTimestamp(builder);
            builder.lstore(timestampSlot);
            if (!isConstructorInvocation) {
                beginTryBlock(builder);
            }
        }
        if (isConstructorInvocation) {
            endTryBlock(builder);
            builder.with(element);
            beginTryBlock(builder);
            return;
        }
        if (element instanceof ReturnInstruction) {
            addTracing(builder);
        }
        builder.with(element);
    }

    private void endTryBlock(CodeBuilder builder) {
        if (tryBlocks.isEmpty()) {
            return;
        }
        TryBlock last = tryBlocks.getLast();
        if (last.end == null) {
            last.end = builder.newBoundLabel();
        }
    }

    private void beginTryBlock(CodeBuilder builder) {
        TryBlock block = new TryBlock();
        block.start = builder.newBoundLabel();
        tryBlocks.add(block);
    }

    private void acceptSimplifiedInstrumentation(CodeBuilder builder, CodeElement element) {
        if (timestampSlot == -1) {
            timestampSlot = invokeTimestamp(builder);
            builder.lstore(timestampSlot);
        }
        if (element instanceof ReturnInstruction || element instanceof ThrowInstruction) {
            addTracing(builder);
        }
        builder.with(element);
    }

    private void acceptMethod(CodeBuilder builder, CodeElement element) {
        if (timestampSlot == -1) {
            timestampSlot = invokeTimestamp(builder);
            builder.lstore(timestampSlot);
            beginTryBlock(builder);
        }
        if (element instanceof ReturnInstruction) {
            addTracing(builder);
        }
        builder.with(element);
    }

    private void addCatchHandler(TryBlock block, CodeBuilder builder) {
        Label catchHandler = builder.newBoundLabel();
        int exceptionSlot = builder.allocateLocal(TypeKind.REFERENCE);
        builder.astore(exceptionSlot);
        addTracing(builder);
        builder.aload(exceptionSlot);
        builder.athrow();
        builder.exceptionCatchAll(block.start, block.end, catchHandler);
    }

    private void addTracing(CodeBuilder builder) {
        builder.lload(timestampSlot);
        builder.ldc(method.methodId());
        Modification modification = method.modification();
        boolean objectInit = method.name().equals("java.lang.Object::<init>");
        String suffix = objectInit ? "ObjectInit" : "";
        if (modification.timing()) {
            if (modification.tracing()) {
                invokeTraceTiming(builder, suffix);
            } else {
                invokeTiming(builder, suffix);
            }
        } else {
            if (modification.tracing()) {
                invokeTrace(builder, suffix);
            }
        }
    }

    private static void invokeTiming(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TIMING_METHOD.name() + suffix, TIMING_METHOD.descriptor());
    }

    private static void invokeTrace(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TRACE_METHOD.name() + suffix, TRACE_METHOD.descriptor());
    }

    private static void invokeTraceTiming(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TRACE_TIMING_METHOD.name() + suffix, TRACE_TIMING_METHOD.descriptor());
    }

    private static int invokeTimestamp(CodeBuilder builder) {
        Bytecode.invokestatic(builder, METHOD_TRACER_CLASS, TIMESTAMP_METHOD);
        return builder.allocateLocal(TypeKind.LONG);
    }
}
