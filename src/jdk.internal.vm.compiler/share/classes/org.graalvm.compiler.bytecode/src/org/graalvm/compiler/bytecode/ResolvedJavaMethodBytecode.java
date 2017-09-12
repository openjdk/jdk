/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.bytecode;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Direct access to the bytecode of a {@link ResolvedJavaMethod} that will reflect any
 * instrumentation and rewriting performed on the {@link ResolvedJavaMethod}.
 */
public class ResolvedJavaMethodBytecode implements Bytecode {

    private final ResolvedJavaMethod method;
    private final BytecodeProvider origin;

    public ResolvedJavaMethodBytecode(ResolvedJavaMethod method) {
        this(method, ResolvedJavaMethodBytecodeProvider.INSTANCE);
    }

    public ResolvedJavaMethodBytecode(ResolvedJavaMethod method, BytecodeProvider origin) {
        this.method = method;
        this.origin = origin;
    }

    @Override
    public BytecodeProvider getOrigin() {
        return origin;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public byte[] getCode() {
        return method.getCode();
    }

    @Override
    public int getCodeSize() {
        return method.getCodeSize();
    }

    @Override
    public int getMaxStackSize() {
        return method.getMaxStackSize();
    }

    @Override
    public int getMaxLocals() {
        return method.getMaxLocals();
    }

    @Override
    public ConstantPool getConstantPool() {
        return method.getConstantPool();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return method.getLineNumberTable();
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return method.getLocalVariableTable();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return method.getExceptionHandlers();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return method.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo() {
        return method.getProfilingInfo();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + method.format("<%h.%n(%p)>");
    }
}
