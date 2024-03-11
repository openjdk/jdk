/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.mixed.func.regression.b7127687;


import vm.mlvm.share.MlvmTest;
import vm.mlvm.share.Env;

import java.lang.invoke.MethodType;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import java.util.List;
import java.util.ArrayList;

public class Test extends MlvmTest {

    final static int CLASSES_COUNT = 1000;

    public static void main(String[] args) { MlvmTest.launch(args); }

    @Override
    public boolean run() throws Throwable {
        List<ClassDesc> classes = new ArrayList<>();

        //generating list of unique classes
        for (int i = 0; i < CLASSES_COUNT; i++) {
            classes.add(generateClass("Class" + i));
        }

        for (ClassDesc a : classes) {
            for (ClassDesc b : classes) {
                Env.traceNormal("Perform call MethodType.methodType(" + a + ", " + b + ")");
                MethodType.methodType(a.getClass(), b.getClass());
            }
        }

        return true;
    }


    private static ClassDesc generateClass(String name) throws ClassNotFoundException{
        byte[] bytes = ClassFile.of().build(ClassDesc.of(name),
                ClassBuilder -> ClassBuilder
                        .withFlags(ClassFile.ACC_PUBLIC)
                        .withSuperclass(ClassDesc.ofInternalName("java/lang/Object")));

        return ClassFile.of().parse(bytes).thisClass().asSymbol();
    }

}
