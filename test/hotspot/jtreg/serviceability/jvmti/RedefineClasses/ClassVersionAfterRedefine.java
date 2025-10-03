/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8267555
 * @requires vm.jvmti
 * @summary Class redefinition with a different class file version
 * @library /test/lib
 * @compile TestClassOld.jasm TestClassNew.jasm
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar ClassVersionAfterRedefine
 */

import java.lang.reflect.Method;

import static jdk.test.lib.Asserts.assertTrue;

public class ClassVersionAfterRedefine extends ClassLoader {

    public static void main(String[] s) throws Exception {

        ClassVersionAfterRedefine cvar = new ClassVersionAfterRedefine();

        byte[] buf = RedefineClassHelper.replaceClassName(cvar, "TestClassOld", "TestClassXXX");
        Class<?> old = cvar.defineClass(null, buf, 0, buf.length);
        Method foo = old.getMethod("foo");
        Object result = foo.invoke(null);
        assertTrue("java-lang-String".equals(result));
        System.out.println(old.getSimpleName() + ".foo() = " + result);

        // Rename class "TestClassNew" to "TestClassXXX" so we can use it for
        // redefining the original version of "TestClassXXX" (i.e. "TestClassOld").
        buf = RedefineClassHelper.replaceClassName(cvar, "TestClassNew", "TestClassXXX");
        // Now redefine the original version of "TestClassXXX" (i.e. "TestClassOld").
        RedefineClassHelper.redefineClass(old, buf);
        result = foo.invoke(null);
        assertTrue("java.lang.String".equals(result));
        System.out.println(old.getSimpleName() + ".foo() = " + result);
    }
}
