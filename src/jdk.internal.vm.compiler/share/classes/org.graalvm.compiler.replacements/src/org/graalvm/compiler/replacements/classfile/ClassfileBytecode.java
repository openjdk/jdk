/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.replacements.classfile.ClassfileConstant.Utf8;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

/**
 * The bytecode properties of a method as parsed directly from a class file without any
 * {@linkplain java.lang.instrument.Instrumentation instrumentation} or other rewriting performed on
 * the bytecode.
 */
public class ClassfileBytecode implements Bytecode {

    private static final int EXCEPTION_HANDLER_TABLE_SIZE_IN_BYTES = 8;
    private static final int LINE_NUMBER_TABLE_ENTRY_SIZE_IN_BYTES = 4;
    private static final int LOCAL_VARIABLE_TABLE_SIZE_IN_BYTES = 10;

    private final ResolvedJavaMethod method;

    private final ClassfileConstantPool constantPool;

    private byte[] code;
    private int maxLocals;
    private int maxStack;

    private byte[] exceptionTableBytes;
    private byte[] lineNumberTableBytes;
    private byte[] localVariableTableBytes;

    public ClassfileBytecode(ResolvedJavaMethod method, DataInputStream stream, ClassfileConstantPool constantPool) throws IOException {
        this.method = method;
        this.constantPool = constantPool;
        maxStack = stream.readUnsignedShort();
        maxLocals = stream.readUnsignedShort();
        int codeLength = stream.readInt();
        code = new byte[codeLength];
        stream.readFully(code);
        int exceptionTableLength = stream.readUnsignedShort();
        exceptionTableBytes = new byte[exceptionTableLength * EXCEPTION_HANDLER_TABLE_SIZE_IN_BYTES];
        stream.readFully(exceptionTableBytes);
        readCodeAttributes(stream);
    }

    @Override
    public BytecodeProvider getOrigin() {
        return constantPool.context;
    }

    private void readCodeAttributes(DataInputStream stream) throws IOException {
        int count = stream.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String attributeName = constantPool.get(Utf8.class, stream.readUnsignedShort()).value;
            int attributeLength = stream.readInt();
            switch (attributeName) {
                case "LocalVariableTable": {
                    int length = stream.readUnsignedShort();
                    localVariableTableBytes = new byte[length * LOCAL_VARIABLE_TABLE_SIZE_IN_BYTES];
                    stream.readFully(localVariableTableBytes);
                    break;
                }
                case "LineNumberTable": {
                    int length = stream.readUnsignedShort();
                    lineNumberTableBytes = new byte[length * LINE_NUMBER_TABLE_ENTRY_SIZE_IN_BYTES];
                    stream.readFully(lineNumberTableBytes);
                    break;
                }
                default: {
                    Classfile.skipFully(stream, attributeLength);
                    break;
                }
            }
        }
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    @Override
    public int getCodeSize() {
        return code.length;
    }

    @Override
    public int getMaxLocals() {
        return maxLocals;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        if (exceptionTableBytes == null) {
            return new ExceptionHandler[0];
        }

        final int exceptionTableLength = exceptionTableBytes.length / EXCEPTION_HANDLER_TABLE_SIZE_IN_BYTES;
        ExceptionHandler[] handlers = new ExceptionHandler[exceptionTableLength];
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(exceptionTableBytes));

        for (int i = 0; i < exceptionTableLength; i++) {
            try {
                final int startPc = stream.readUnsignedShort();
                final int endPc = stream.readUnsignedShort();
                final int handlerPc = stream.readUnsignedShort();
                int catchTypeIndex = stream.readUnsignedShort();

                JavaType catchType;
                if (catchTypeIndex == 0) {
                    catchType = null;
                } else {
                    final int opcode = -1;  // opcode is not used
                    catchType = constantPool.lookupType(catchTypeIndex, opcode);

                    // Check for Throwable which catches everything.
                    if (catchType.toJavaName().equals("java.lang.Throwable")) {
                        catchTypeIndex = 0;
                        catchType = null;
                    }
                }
                handlers[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        return handlers;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        int line = getLineNumberTable().getLineNumber(bci);
        return new StackTraceElement(method.getDeclaringClass().toJavaName(), method.getName(), method.getDeclaringClass().getSourceFileName(), line);
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        if (lineNumberTableBytes == null) {
            return null;
        }

        final int lineNumberTableLength = lineNumberTableBytes.length / LINE_NUMBER_TABLE_ENTRY_SIZE_IN_BYTES;
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(lineNumberTableBytes));
        int[] bci = new int[lineNumberTableLength];
        int[] line = new int[lineNumberTableLength];

        for (int i = 0; i < lineNumberTableLength; i++) {
            try {
                bci[i] = stream.readUnsignedShort();
                line[i] = stream.readUnsignedShort();
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        return new LineNumberTable(line, bci);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        if (localVariableTableBytes == null) {
            return null;
        }

        final int localVariableTableLength = localVariableTableBytes.length / LOCAL_VARIABLE_TABLE_SIZE_IN_BYTES;
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(localVariableTableBytes));
        Local[] locals = new Local[localVariableTableLength];

        for (int i = 0; i < localVariableTableLength; i++) {
            try {
                final int startBci = stream.readUnsignedShort();
                final int endBci = startBci + stream.readUnsignedShort();
                final int nameCpIndex = stream.readUnsignedShort();
                final int typeCpIndex = stream.readUnsignedShort();
                final int slot = stream.readUnsignedShort();

                String localName = constantPool.lookupUtf8(nameCpIndex);
                String localType = constantPool.lookupUtf8(typeCpIndex);

                ClassfileBytecodeProvider context = constantPool.context;
                Class<?> c = context.resolveToClass(localType);
                locals[i] = new Local(localName, context.metaAccess.lookupJavaType(c), startBci, endBci, slot);
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        return new LocalVariableTable(locals);
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public ProfilingInfo getProfilingInfo() {
        return DefaultProfilingInfo.get(TriState.FALSE);
    }

    @Override
    public String toString() {
        return getClass().getName() + method.format("<%H.%n(%p)>");
    }
}
