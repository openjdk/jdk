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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;

/*
 * @test id=defaults
 * @bug 8352075
 * @library /test/lib
 * @library /testlibrary/asm
 * @run main/othervm LocalFieldLookupTest
 */
/*
 * @test id=custom-threshold
 * @bug 8352075
 * @library /test/lib
 * @library /testlibrary/asm
 * @requires vm.debug == true
 * @run main/othervm LocalFieldLookupTest
 * @run main/othervm -XX:BinarySearchThreshold=0 LocalFieldLookupTest
 * @run main/othervm -XX:BinarySearchThreshold=1 LocalFieldLookupTest
 * @run main/othervm -XX:BinarySearchThreshold=15 LocalFieldLookupTest
 * @run main/othervm -XX:BinarySearchThreshold=100000 LocalFieldLookupTest
 */
public class LocalFieldLookupTest {
    private static final String TEST_CLASS_NAME = "Test";
    private static final int MAX_FIELDS_IN_METHOD = 10000;

    public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        // Test small classes, covering the tested thresholds
        for (int i = 0; i <= 33; ++i) {
            makeClass(i).newInstance();
        }
        // Test classes around 256 fields (index encoding 1/2 bytes) to check off-by-one errors
        for (int i = 254; i <= 259; ++i) {
            makeClass(255).newInstance();
        }
        // We would like to test #fields that create have the stream about 65536 bytes long;
        // this value is not exposed, though, so these are rather experimentally found values,
        // hence fragile. Moreover, since the stream length is incremented by about 8 bytes
        // for each field we cannot test for off-by-one errors reliably.
        for (int i = 8433; i <= 8437; ++i) {
            makeClass(i).newInstance();
        }
        // The largest class we can create - this one has 65533 entries in the constant pool
        makeClass(26205).newInstance();
    }

    public static Class<?> makeClass(int fields) throws ClassNotFoundException {
        ClassWriter writer = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
        writer.visit(49, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, TEST_CLASS_NAME,null, "java/lang/Object", null);

        for (int i = 0; i < fields; i += 2) {
            writer.visitField(ACC_PUBLIC, "f" + i, "I",  null, null);
            // Let's use duplicate names to confirm search takes signatures into account
            if (i + 1 < fields) {
                writer.visitField(ACC_PUBLIC, "f" + i, "J",  null, null);
            }
        }
        // We initialize fields in multiple methods to avoid running into bytecode limit per method
        MethodVisitor fi = null;
        for (int i = 0; i < fields; i+= 2) {
            if (fi == null) {
                fi = writer.visitMethod(ACC_PRIVATE, "init" + i, "()V", null, null);
                fi.visitCode();
            }
            fi.visitVarInsn(Opcodes.ALOAD, 0);
            fi.visitInsn(Opcodes.ICONST_2);
            fi.visitFieldInsn(PUTFIELD, TEST_CLASS_NAME, "f" + i, "I");
            if (i + 1 < fields) {
                fi.visitVarInsn(Opcodes.ALOAD, 0);
                fi.visitInsn(Opcodes.LCONST_1);
                fi.visitFieldInsn(PUTFIELD, TEST_CLASS_NAME, "f" + i, "J");
            }
            if (i % MAX_FIELDS_IN_METHOD == MAX_FIELDS_IN_METHOD - 2) {
                fi.visitInsn(Opcodes.RETURN);
                fi.visitMaxs(0, 0);
                fi.visitEnd();
                fi = null;
            }
        }
        if (fi != null) {
            fi.visitInsn(Opcodes.RETURN);
            fi.visitMaxs(0, 0);
            fi.visitEnd();
        }
        {
            MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            for (int i = 0; i < fields; i += MAX_FIELDS_IN_METHOD) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, TEST_CLASS_NAME, "init" + i, "()V", false);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        writer.visitEnd();

        byte[] bytecode = writer.toByteArray();
        ClassLoader cl = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!TEST_CLASS_NAME.equals(name)) {
                    throw new ClassNotFoundException();
                }
                return defineClass(TEST_CLASS_NAME, bytecode, 0, bytecode.length);
            }
        };
        return cl.loadClass(TEST_CLASS_NAME);
    }
}
