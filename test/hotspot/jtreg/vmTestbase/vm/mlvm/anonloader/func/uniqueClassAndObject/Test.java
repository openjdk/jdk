/*
 * Copyright (c) 2010, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @modules java.base/jdk.internal.misc java.base/jdk.internal.org.objectweb.asm
 *
 * @summary converted from VM Testbase vm/mlvm/anonloader/func/uniqueClassAndObject.
 * VM Testbase keywords: [feature_mlvm]
 * VM Testbase readme:
 * DESCRIPTION
 *     Create two anonymous classes and instantiate an object from each of them.
 *     Verify that the references to these objects are different and references
 *     to their classes are not equal too.
 *
 * @library /vmTestbase
 *          /test/lib
 *
 * @comment build test class and indify classes
 * @build vm.mlvm.anonloader.func.uniqueClassAndObject.Test
 * @run driver vm.mlvm.share.IndifiedClassesBuilder
 *
 * @run main/othervm vm.mlvm.anonloader.func.uniqueClassAndObject.Test
 */

package vm.mlvm.anonloader.func.uniqueClassAndObject;

import jdk.internal.org.objectweb.asm.ClassReader;
import vm.mlvm.anonloader.share.AnonkTestee01;
import vm.mlvm.share.MlvmTest;
import vm.share.FileUtils;
import vm.share.UnsafeAccess;

public class Test extends MlvmTest {
    private static final Class<?> PARENT = AnonkTestee01.class;

    @Override
    public boolean run() throws Exception {
        byte[] classBytes = FileUtils.readClass(PARENT.getName());
        ClassReader reader = new ClassReader(classBytes);
        Object o1 = UnsafeAccess.unsafe.defineAnonymousClass(PARENT,
                    classBytes, null).newInstance();
        int cpLength = reader.getItemCount();
        Object cpPatch[] = new Object[cpLength];
        Object o2 = UnsafeAccess.unsafe.defineAnonymousClass(PARENT,
                    classBytes, cpPatch).newInstance();
        if ( o1 == o2 ) {
            getLog().complain("Error: The objects are equal");
            return false;
        }

        if ( o1.getClass() == o2.getClass() ) {
            getLog().complain("Error: The classes are equal");
            return false;
        }

        return true;
    }

    public static void main(String[] args) { MlvmTest.launch(args); }
}
