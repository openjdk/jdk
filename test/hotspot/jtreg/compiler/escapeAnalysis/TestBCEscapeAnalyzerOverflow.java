/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8216486
 * @summary Verify BCEscapeAnalyzer handles methods where
 *          (numblocks+1)*(max_stack+max_locals) overflows a 32-bit int.
 *          On a UBSAN build the signed overflow would be caught as UB;
 *          on a normal build the test verifies no crash from the bogus
 *          allocation size that resulted from the overflow.
 *
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      compiler.escapeAnalysis.TestBCEscapeAnalyzerOverflow
 */

package compiler.escapeAnalysis;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

public class TestBCEscapeAnalyzerOverflow {

    // Number of goto instructions in the generated method.
    // Creates NUM_GOTOS + 1 basic blocks. With max_stack=0xFFFF and
    // max_locals=0xFFFF the product (numblocks+1)*(max_stack+max_locals)
    // is 16386 * 131070 = 2,147,713,020 which exceeds Integer.MAX_VALUE.
    static final int NUM_GOTOS = 16384;

    public static void main(String[] args) throws Throwable {
        byte[] classBytes = buildClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> cls = lookup.defineClass(classBytes);

        // caller() allocates an Object and passes it to bigMethod() via
        // invokestatic.  Under -Xcomp -XX:-TieredCompilation, C2 compiles
        // caller() and invokes BCEscapeAnalyzer on bigMethod to determine
        // whether the argument escapes.  Without the fix the 32-bit
        // overflow in iterate_blocks leads to undefined behavior.
        var mh = lookup.findStatic(cls, "caller",
                     MethodType.methodType(void.class));
        mh.invoke();
    }

    // Builds a minimal class (version 50, no StackMapTable needed) with:
    //   public static void bigMethod(Object o)  -- pathological method
    //   public static void caller()              -- calls bigMethod
    static byte[] buildClass() throws IOException {
        int bigCodeLength = 2 + NUM_GOTOS * 3 + 1;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // ---- header ----
        dos.writeInt(0xCAFEBABE);
        dos.writeShort(0);       // minor version
        dos.writeShort(50);      // major version (Java 6)

        // ---- constant pool (14 entries, count = 15) ----
        dos.writeShort(15);
        writeUtf8(dos, "compiler/escapeAnalysis/BCEscapeOverflowHelper"); // #1
        writeUtf8(dos, "java/lang/Object");            // #2
        writeUtf8(dos, "bigMethod");                   // #3
        writeUtf8(dos, "(Ljava/lang/Object;)V");       // #4
        writeUtf8(dos, "Code");                        // #5
        writeUtf8(dos, "caller");                      // #6
        writeUtf8(dos, "()V");                         // #7
        writeUtf8(dos, "<init>");                      // #8
        dos.writeByte(7);  dos.writeShort(1);          // #9  Class -> #1
        dos.writeByte(7);  dos.writeShort(2);          // #10 Class -> #2
        dos.writeByte(12); dos.writeShort(8);          // #11 NameAndType <init>:()V
                           dos.writeShort(7);
        dos.writeByte(10); dos.writeShort(10);         // #12 MethodRef Object.<init>
                           dos.writeShort(11);
        dos.writeByte(12); dos.writeShort(3);          // #13 NameAndType bigMethod:(L..;)V
                           dos.writeShort(4);
        dos.writeByte(10); dos.writeShort(9);          // #14 MethodRef this.bigMethod
                           dos.writeShort(13);

        // ---- class header ----
        dos.writeShort(0x0021);  // ACC_PUBLIC | ACC_SUPER
        dos.writeShort(9);       // this_class
        dos.writeShort(10);      // super_class
        dos.writeShort(0);       // interfaces_count
        dos.writeShort(0);       // fields_count

        // ---- methods (2) ----
        dos.writeShort(2);

        // -- Method 1: public static void bigMethod(Object o) --
        dos.writeShort(0x0009);              // ACC_PUBLIC | ACC_STATIC
        dos.writeShort(3);                   // name -> "bigMethod"
        dos.writeShort(4);                   // descriptor
        dos.writeShort(1);                   // attributes_count
        dos.writeShort(5);                   // Code attribute name
        dos.writeInt(12 + bigCodeLength);    // attribute_length
        dos.writeShort(0xFFFF);              // max_stack  = 65535
        dos.writeShort(0xFFFF);              // max_locals = 65535
        dos.writeInt(bigCodeLength);         // code_length
        // bytecode: aload_0, pop, goto chain, return
        dos.writeByte(0x2A);                 // aload_0
        dos.writeByte(0x57);                 // pop
        for (int i = 0; i < NUM_GOTOS; i++) {
            dos.writeByte(0xA7);             // goto
            dos.writeShort(3);               // offset +3 (next instruction)
        }
        dos.writeByte(0xB1);                 // return
        dos.writeShort(0);                   // exception_table_length
        dos.writeShort(0);                   // code attributes_count

        // -- Method 2: public static void caller() --
        //    new Object, dup, invokespecial <init>, invokestatic bigMethod, return
        int callerCodeLength = 11;
        dos.writeShort(0x0009);              // ACC_PUBLIC | ACC_STATIC
        dos.writeShort(6);                   // name -> "caller"
        dos.writeShort(7);                   // descriptor -> "()V"
        dos.writeShort(1);                   // attributes_count
        dos.writeShort(5);                   // Code attribute name
        dos.writeInt(12 + callerCodeLength); // attribute_length
        dos.writeShort(2);                   // max_stack
        dos.writeShort(1);                   // max_locals
        dos.writeInt(callerCodeLength);      // code_length
        dos.writeByte(0xBB); dos.writeShort(10);   // new #10 (Object)
        dos.writeByte(0x59);                       // dup
        dos.writeByte(0xB7); dos.writeShort(12);   // invokespecial #12
        dos.writeByte(0xB8); dos.writeShort(14);   // invokestatic #14
        dos.writeByte(0xB1);                       // return
        dos.writeShort(0);                   // exception_table_length
        dos.writeShort(0);                   // code attributes_count

        // ---- class attributes ----
        dos.writeShort(0);

        dos.flush();
        return baos.toByteArray();
    }

    static void writeUtf8(DataOutputStream dos, String s) throws IOException {
        dos.writeByte(1);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }
}
