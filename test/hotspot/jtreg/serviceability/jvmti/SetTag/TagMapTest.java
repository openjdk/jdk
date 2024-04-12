/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8306843
 * @summary Test that 10M tags doesn't time out.
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:TagMapTest
 *                          -Xlog:jvmti+table
 *                          TagMapTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TagMapTest {
    private static final List<TagMapTest> items = new ArrayList<>();

    private static native void setTag(Object object);
    private static native long getTag(Object object);
    private static native void iterate(boolean tagged);

    public static void main(String[] args) {
        System.loadLibrary("TagMapTest");
        for (int i = 0; i < 10_000_000; i++) {
            items.add(new TagMapTest());
        }

        long startTime = System.nanoTime();
        for (TagMapTest item : items) {
            setTag(item);
        }
        System.out.println("setTag: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");

        startTime = System.nanoTime();
        for (TagMapTest item : items) {
            getTag(item);
        }
        System.out.println("getTag: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");

        startTime = System.nanoTime();
        iterate(true);
        System.out.println("iterate tagged: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");

        startTime = System.nanoTime();
        iterate(false);
        System.out.println("iterate all: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");
    }
}
