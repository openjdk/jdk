/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8284877
 * @summary Check type compatibility before looking up method from receiver's vtable
 * @modules java.base/jdk.internal.misc:+open
 * @library /test/lib
 * @run main/othervm TestErrorReceiverType
 */

import jdk.internal.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class TestErrorReceiverType {
    public static void main(String[] args) throws Throwable {
        // 8284877
        // Commenting out the following line crashes JVM at assert(resolved_method->method_holder()->is_linked(), "must be linked");
        Class.forName("TestErrorReceiverType$LongMapSupportArrayList");

        try {
            Unsafe unsafe = getUnsafe();
            long offset = unsafe.objectFieldOffset(
                A.class.getDeclaredField("list")
            );

            A a = new A();
            unsafe.putObject(a, offset, new ArrayList<>());
            a.list.toMap();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IncompatibleClassChangeError e1) {
            e1.printStackTrace();
            System.out.println("Expected");
        } finally {
        }
    }

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
        }
        return null;
    }

    private static class LongMapSupportArrayList<T> extends ArrayList {
        public Map<?, ?> toMap() {
            return new HashMap<>();
        }
    }

    private static class A {
        private String s = "a";
        private LongMapSupportArrayList<String> list;
    }
}