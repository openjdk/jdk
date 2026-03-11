/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.parallel;

/*
 * @test TestObjectCountAfterGC
 * @bug 8375314
 * @summary Verifies that the HeapInspection VM operation for the ObjectCountAfterGC JFR event does not crash the VM.
 *          Creates a set of custom classes that are about to be unloaded to cause metaspace to uncommit pages. When
 *          the execution of the heap inspection iterates over the heap, it will come across these unloaded classes
 *          referencing uncommitted memory, crashing.
 * @requires vm.gc.Parallel
 * @requires vm.opt.final.ClassUnloading
 * @library /test/lib
 * @library /testlibrary/asm
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:+UseParallelGC -Xlog:gc=debug,metaspace=info -XX:StartFlightRecording:gc=all,duration=1s,filename=myrecording.jfr
 *                   gc.parallel.TestObjectCountAfterGC
 */

import java.lang.ref.Reference;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TestObjectCountAfterGC {

    static final String className = "ClassToLoadUnload";

    public static void main(String args[]) throws Exception {
        final int KEEPALIVE_LENGTH = 100;

        Object[] keepalive = new Object[KEEPALIVE_LENGTH];

        for (int i = 1; i < 1000; i++) {
            ClassLoader cl = new MyClassLoader();
            Object o = null;
            // Create some random kept alive objects so that the
            // compaction regions are not totally empty and the
            // heap inspection VM operation needs to iterate them.
            keepalive[(i / KEEPALIVE_LENGTH) % KEEPALIVE_LENGTH] = new int[100];
            o = cl.loadClass(className + i).newInstance();

            cl = null;
            o = null;
        }

        // There is heap inspection VM operation for the ObjectCountAfterGC event
        // when JFR stops recording.

        Reference.reachabilityFence(keepalive);
    }
}

class MyClassLoader extends ClassLoader {

    // Create a class of the given name with a default constructor.
    public byte[] createClass(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        // Add default constructor that just calls the super class constructor.
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        return cw.toByteArray();
    }

    // If the given name starts with "TestObjectCountAfterGC" create a new class on the fly,
    // delegate otherwise.
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (!name.startsWith(TestObjectCountAfterGC.className)) {
            return super.loadClass(name);
        }
        byte[] cls = createClass(name);
        return defineClass(name, cls, 0, cls.length, null);
    }
  }

