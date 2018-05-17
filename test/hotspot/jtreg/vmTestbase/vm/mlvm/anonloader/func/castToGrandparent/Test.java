/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.misc
 *
 * @summary converted from VM Testbase vm/mlvm/anonloader/func/castToGrandparent.
 * VM Testbase keywords: [feature_mlvm]
 * VM Testbase readme:
 * DESCRIPTION
 *     Cast an object loaded with Unsafe.defineAnonymousClass to superclass of its parent class. This cast should succeed.
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 *
 * @comment build test class and indify classes
 * @build vm.mlvm.anonloader.func.castToGrandparent.Test
 * @run driver vm.mlvm.share.IndifiedClassesBuilder
 *
 * @run main/othervm vm.mlvm.anonloader.func.castToGrandparent.Test
 */

package vm.mlvm.anonloader.func.castToGrandparent;

import vm.mlvm.anonloader.share.AnonkTestee01;
import vm.mlvm.anonloader.share.AnonkTestee02;
import vm.mlvm.share.Env;
import vm.mlvm.share.MlvmTest;
import vm.share.FileUtils;
import vm.share.UnsafeAccess;

public class Test extends MlvmTest {
    private static final Class<?> PARENT = AnonkTestee02.class;

    @Override
    public boolean run() throws Exception {
        byte[] classBytes = FileUtils.readClass(PARENT.getName());
        Class<?> cls = UnsafeAccess.unsafe.defineAnonymousClass(
                PARENT, classBytes, null);
        Object anonObject = cls.newInstance();
        // Try to cast anonymous class to its grandparent
        AnonkTestee01 anonCastToParent = (AnonkTestee01) anonObject;
        if ( anonCastToParent.equals(anonObject) )
            Env.traceNormal("Anonymous object can be cast to original one");

        // Try to cast using another method
        new AnonkTestee01().getClass().cast(anonObject);

        Env.traceNormal("Anonymous class can be cast to original one");

        return true;
    }

    public static void main(String[] args) { MlvmTest.launch(args); }
}
