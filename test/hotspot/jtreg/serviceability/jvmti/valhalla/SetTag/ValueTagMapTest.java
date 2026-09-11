/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests SetTag/GetTag functionality for value objects.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueTagMapTest
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+PrintInlineLayout
 *                          -XX:+PrintFlatArrayLayout
 *                          -Xlog:jvmti+table
 *                          ValueTagMapTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jdk.internal.vm.annotation.NullRestricted;

public class ValueTagMapTest {

    private static value class ValueClass {
        public int f;
        public ValueClass(int v) { f = v + 1; }
        public String toString() {
            return String.valueOf(f);
        }
    }

    private static value class ValueHolder {
        @NullRestricted
        public ValueClass f0;

        public ValueHolder(int v) {
            f0 = new ValueClass(v);
        }
        public String toString() {
            return "holder{" + f0 + "}";
        }
    }

    private static value class ValueHolder2 {
        public ValueHolder f1;
        @NullRestricted
        public ValueHolder f2;

        public ValueHolder2(int v, int v2) {
            f1 = new ValueHolder(v);
            f2 = new ValueHolder(v2);
        }
        public ValueHolder2(ValueHolder h1, int v2) {
            f1 = h1;
            f2 = new ValueHolder(v2);
        }
        public String toString() {
            return "holder2{" + f1 + ", " + f2 + "}";
        }
    }


    private static native void setTag0(Object object, long tag);
    private static void setTag(Object object, long tag) {
        setTag0(object, tag);
    }
    private static native long getTag0(Object object);
    private static long getTag(Object object) {
        long tag = getTag0(object);
        if (tag == 0) {
            throw new RuntimeException("Zero tag for object " + object);
        }
        return tag;
    }

    private static void testGetTag(Object o1, Object o2) {
        long tag1 = getTag(o1);
        long tag2 = getTag(o2);
        if (o1 == o2) {
            if (tag1 != tag2) {
                throw new RuntimeException("different tags for equal objects: "
                                           + o1 + " (tag " + tag1 + "), "
                                           + o2 + " (tag " + tag2 + ")");
            }
        } else {
            if (tag1 == tag2) {
                throw new RuntimeException("equal tags for different objects: "
                                           + o1 + " (tag " + tag1 + "), "
                                           + o2 + " (tag " + tag2 + ")");
            }
        }
    }

    public static void main(String[] args) {
        System.loadLibrary("ValueTagMapTest");
        List<ValueHolder2> items = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            items.add(new ValueHolder2(i % 4, i % 8));
        }

        long startTime = System.nanoTime();
        long tag = 1;
        for (ValueHolder2 item : items) {
            setTag(item, tag++);
            setTag(item.f1, tag++);
            setTag(item.f2, tag++);
        }

        for (ValueHolder2 item: items) {
            long tag0 = getTag(item);
            long tag1 = getTag(item.f1);
            long tag2 = getTag(item.f2);
            System.out.println("getTag (" + item + "): " + tag0 + ", f1: " + tag1 + ", f2:" + tag2);
        }

        startTime = System.nanoTime();
        for (ValueHolder2 item1: items) {
            for (ValueHolder2 item2 : items) {
                testGetTag(item1, item2);
                testGetTag(item1.f1, item2.f1);
                testGetTag(item1.f2, item2.f2);
                testGetTag(item1.f1, item2.f2);
                testGetTag(item1.f2, item2.f1);
            }
        }
    }
}
