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
 *
 */

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

public class RedefineAllAgent implements ClassFileTransformer {
    public static void premain(String agentArguments, Instrumentation inst) {
        inst.addTransformer(new RedefineAllAgent(), /*canRetransform=*/true);

        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (!c.isArray() && !c.isHidden() && inst.isModifiableClass(c)) {
                try {
                    // Note: we cannot use test/lib/RedefineClassHelper.java because we may
                    // have a regenerated class (see regeneratedClasses.cpp) such as
                    // java/lang/invoke/DirectMethodHandle$Holder, whose bytecodes are different than
                    // the DirectMethodHandle$Holder.class file stored in the JDK's modules file.
                    //
                    // Therefore, we cannot use RedefineClassHelper.getBytecodes(). We must call into
                    // inst.retransformClasses(), which will give us the correct bytecodes using
                    // JvmtiClassFileReconstituter.
                    inst.retransformClasses(c);
                    System.out.println("========= Success: " + c.getName());
                } catch (UnmodifiableClassException e) {
                    System.out.println("========== Failed: " + c.getName());
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    public byte[] transform(ClassLoader loader, String name, Class<?> classBeingRedefined,
                            ProtectionDomain pd, byte[] buffer) throws IllegalClassFormatException {
        try {
            System.out.println((classBeingRedefined == null ? "retransforming " : "redefining ") + name);
            ClassFile cf = ClassFile.of();
            ClassModel model = cf.parse(buffer);
            ConstantPoolBuilder cp = ConstantPoolBuilder.of(model);
            cp.utf8Entry("Hello");
            buffer = cf.build(model.thisClass(), cp,
                              cb -> cb.transform(model, ClassTransform.ACCEPT_ALL));
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unexpected", t);
        }
        return buffer;
    }
}
