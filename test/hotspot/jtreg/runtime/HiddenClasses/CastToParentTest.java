/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that a hidden class can be cast to its parent.
 * @library /test/lib
 * @modules jdk.compiler
 * @run main CastToParentTest
 */

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.*;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

// This test is based on vmTestbase/vm/mlvm/anonloader/func/castToGrandparent/Test.java
public class CastToParentTest {

    static byte klassbuf[] = InMemoryJavaCompiler.compile("TestClass",
        "public class TestClass extends CastToParentTest { " +
        "    public static void concat(String one, String two) throws Throwable { " +
        "        System.out.println(one + two);" +
        " } } ");

    public static void main(String[] args) throws Throwable {
        Lookup lookup = MethodHandles.lookup();
        Class<?> c = lookup.defineHiddenClass(klassbuf, false, NESTMATE).lookupClass();
        Object hiddenClassObj = c.newInstance();

        // Cast hidden class to its parent.
        CastToParentTest parentObj = (CastToParentTest)hiddenClassObj;

        if (!parentObj.equals(hiddenClassObj)) {
            throw new RuntimeException("Hidden class object cannot be cast to parent");
        }

        // Try to cast using a different mechanism.
        new CastToParentTest().getClass().cast(hiddenClassObj);
    }
}
