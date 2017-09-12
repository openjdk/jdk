/*
 * Copyright (c) 1995, 2003, Oracle and/or its affiliates. All rights reserved.
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


package sun.tools.java;

import java.io.*;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public class BinaryCode implements Constants {
    int maxStack;               // maximum stack used by code
    int maxLocals;              // maximum locals used by code
    BinaryExceptionHandler exceptionHandlers[];
    BinaryAttribute atts;       // code attributes
    BinaryConstantPool cpool;   // constant pool of the class
    byte code[];                // the byte code

    /**
     * Construct the binary code from the code attribute
     */

    public
    BinaryCode(byte data[], BinaryConstantPool cpool, Environment env) {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            this.cpool = cpool;
            // JVM 4.7.4 CodeAttribute.max_stack
            this.maxStack = in.readUnsignedShort();
            // JVM 4.7.4 CodeAttribute.max_locals
            this.maxLocals = in.readUnsignedShort();
            // JVM 4.7.4 CodeAttribute.code_length
            int code_length = in.readInt();
            this.code = new byte[code_length];
            // JVM 4.7.4 CodeAttribute.code[]
            in.read(this.code);
            // JVM 4.7.4 CodeAttribute.exception_table_length
            int exception_count = in.readUnsignedShort();
            this.exceptionHandlers = new BinaryExceptionHandler[exception_count];
            for (int i = 0; i < exception_count; i++) {
                // JVM 4.7.4 CodeAttribute.exception_table.start_pc
                int start = in.readUnsignedShort();
                // JVM 4.7.4 CodeAttribute.exception_table.end_pc
                int end = in.readUnsignedShort();
                // JVM 4.7.4 CodeAttribute.exception_table.handler_pc
                int handler = in.readUnsignedShort();
                // JVM 4.7.4 CodeAttribute.exception_table.catch_type
                ClassDeclaration xclass = cpool.getDeclaration(env, in.readUnsignedShort());
                this.exceptionHandlers[i]  =
                    new BinaryExceptionHandler(start, end, handler, xclass);
            }
            this.atts = BinaryAttribute.load(in, cpool, ~0);
            if (in.available() != 0) {
                System.err.println("Should have exhausted input stream!");
            }
        } catch (IOException e) {
            throw new CompilerError(e);
        }
    }


    /**
     * Accessors
     */

    public BinaryExceptionHandler getExceptionHandlers()[] {
        return exceptionHandlers;
    }

    public byte getCode()[] { return code; }

    public int getMaxStack() { return maxStack; }

    public int getMaxLocals() { return maxLocals; }

    public BinaryAttribute getAttributes() { return atts; }

    /**
     * Load a binary class
     */
    public static
    BinaryCode load(BinaryMember bf, BinaryConstantPool cpool, Environment env) {
        byte code[] = bf.getAttribute(idCode);
        return (code != null) ? new BinaryCode(code, cpool, env) : null;
    }
}
