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
 * An interface for accessing the bytecode properties of a {@link ResolvedJavaMethod} that allows
 * for different properties than those returned by {@link ResolvedJavaMethod}. Since the bytecode
 * accessed directly from {@link ResolvedJavaMethod} may have been subject to bytecode
 * instrumentation and VM rewriting, this indirection can be used to enable access to the original
 * bytecode of a method (i.e., as defined in a class file).
 */
public interface Bytecode {

    /**
     * Gets the method this object supplies bytecode for.
     */
    ResolvedJavaMethod getMethod();

    byte[] getCode();

    int getCodeSize();

    int getMaxStackSize();

    int getMaxLocals();

    ConstantPool getConstantPool();

    LineNumberTable getLineNumberTable();

    LocalVariableTable getLocalVariableTable();

    StackTraceElement asStackTraceElement(int bci);

    ProfilingInfo getProfilingInfo();

    ExceptionHandler[] getExceptionHandlers();

    static String toLocation(Bytecode bytecode, int bci) {
        return appendLocation(new StringBuilder(), bytecode, bci).toString();
    }

    static StringBuilder appendLocation(StringBuilder sb, Bytecode bytecode, int bci) {
        if (bytecode != null) {
            StackTraceElement ste = bytecode.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() > 0) {
                sb.append(ste);
            } else {
                sb.append(bytecode.getMethod().format("%H.%n(%p)"));
            }
        } else {
            sb.append("Null method");
        }
        return sb.append(" [bci: ").append(bci).append(']');
    }
}
