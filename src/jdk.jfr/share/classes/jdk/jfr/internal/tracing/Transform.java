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

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.constant.ClassDesc;

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
    private static final ClassDesc METHOD_TRACER_CLASS = ClassDesc.of(MethodTracer.class.getName());
    private static final MethodDesc TRACE_METHOD = MethodDesc.of("trace", "(JJ)V");
    private static final MethodDesc TIMING_METHOD = MethodDesc.of("timing", "(JJ)V");
    private static final MethodDesc TRACE_TIMING_METHOD = MethodDesc.of("traceTiming", "(JJ)V");
    private static final MethodDesc TIMESTAMP_METHOD = MethodDesc.of("timestamp", "()J");

    private final Method method;
    private int timestampSlot = -1;

    Transform(Method method) {
        this.method = method;
    }

    @Override
    public final void accept(CodeBuilder builder, CodeElement element) {
        if (timestampSlot == -1) {
            timestampSlot = invokeTimestamp(builder);
            builder.lstore(timestampSlot);
        }
        if (element instanceof ReturnInstruction || element instanceof ThrowInstruction) {
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
        builder.with(element);
    }

    public static void invokeTiming(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TIMING_METHOD.name() + suffix, TIMING_METHOD.descriptor());
    }

    public static void invokeTrace(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TRACE_METHOD.name() + suffix, TRACE_METHOD.descriptor());
    }

    public static void invokeTraceTiming(CodeBuilder builder, String suffix) {
        builder.invokestatic(METHOD_TRACER_CLASS, TRACE_TIMING_METHOD.name() + suffix, TRACE_TIMING_METHOD.descriptor());
    }

    public static int invokeTimestamp(CodeBuilder builder) {
        Bytecode.invokestatic(builder, METHOD_TRACER_CLASS, TIMESTAMP_METHOD);
        return builder.allocateLocal(TypeKind.LONG);
    }
}
