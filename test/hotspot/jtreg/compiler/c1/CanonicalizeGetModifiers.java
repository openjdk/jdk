/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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

/*
 * @test
 * @author Yi Yang
 * @summary Canonicalizes Foo.class.getModifiers() with interpreter mode
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -Xint
 *                   -XX:CompileCommand=compileonly,*CanonicalizeGetModifiers.test
 *                   compiler.c1.CanonicalizeGetModifiers
 */

/*
 * @test
 * @author Yi Yang
 * @summary Canonicalizes Foo.class.getModifiers() with C1 mode
 * @modules java.base/jdk.internal.misc
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:TieredStopAtLevel=1 -XX:+TieredCompilation
 *                   -XX:CompileCommand=compileonly,*CanonicalizeGetModifiers.test
 *                   compiler.c1.CanonicalizeGetModifiers
 */

/*
 * @test
 * @author Yi Yang
 * @summary Canonicalizes Foo.class.getModifiers() with C2 mode
 * @modules java.base/jdk.internal.misc
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,*CanonicalizeGetModifiers.test
 *                   compiler.c1.CanonicalizeGetModifiers
 */

package compiler.c1;

import java.lang.classfile.ClassFile;
import java.lang.reflect.Modifier;
import java.lang.reflect.AccessFlag;

import jdk.test.lib.Asserts;
import jdk.internal.misc.PreviewFeatures;

public class CanonicalizeGetModifiers {
    public static class T1 {
    }

    public static final class T2 {
    }

    private static class T3 {
    }

    protected static class T4 {
    }

    class T5 {
    }

    interface T6 {
    }

    static void test(Class poison) {
        Asserts.assertEQ(CanonicalizeGetModifiers.class.getModifiers(), Modifier.PUBLIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(T1.class.getModifiers(), Modifier.PUBLIC | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(T2.class.getModifiers(), Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(T3.class.getModifiers(), Modifier.PRIVATE | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(T4.class.getModifiers(), Modifier.PROTECTED | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(new CanonicalizeGetModifiers().new T5().getClass().getModifiers(), 0/* NONE */ | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(T6.class.getModifiers(), Modifier.ABSTRACT | Modifier.STATIC | Modifier.INTERFACE);

        Asserts.assertEQ(int.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(long.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(double.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(float.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(char.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(byte.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(short.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(void.class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL);
        Asserts.assertEQ(int[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(long[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(double[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(float[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(char[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(byte[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(short[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(Object[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));
        Asserts.assertEQ(CanonicalizeGetModifiers[].class.getModifiers(), Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | (PreviewFeatures.isEnabled() ? ClassFile.ACC_IDENTITY : 0));

        Asserts.assertEQ(new CanonicalizeGetModifiers().getClass().getModifiers(), Modifier.PUBLIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(new T1().getClass().getModifiers(), Modifier.PUBLIC | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(new T2().getClass().getModifiers(), Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(new T3().getClass().getModifiers(), Modifier.PRIVATE | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        Asserts.assertEQ(new T4().getClass().getModifiers(), Modifier.PROTECTED | Modifier.STATIC | ClassFile.ACC_IDENTITY);
        try {
            // null_check
            poison.getModifiers();
        } catch(NullPointerException npe) {
            // got it!
        }
    }

    public static void main(String... args) {
        for (int i = 0; i < 10_000; i++) {
            test(i == 9999 ? null : CanonicalizeGetModifiers.class);
        }
    }
}
