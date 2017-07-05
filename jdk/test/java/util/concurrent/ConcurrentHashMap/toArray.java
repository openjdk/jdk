/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4486658
 * @summary thread safety of toArray methods of subCollections
 * @author Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;

public class toArray {

    public static void main(String[] args) throws Throwable {
        final Throwable throwable[] = new Throwable[1];
        final int maxSize = 1000;
        final ConcurrentHashMap<Integer, Integer> m
            = new ConcurrentHashMap<Integer, Integer>();

        final Thread t1 = new Thread() { public void run() {
            for (int i = 0; i < maxSize; i++)
                m.put(i,i);}};

        final Thread t2 = new Thread() {
            public Throwable exception = null;
            private int prevSize = 0;

            private boolean checkProgress(Object[] a) {
                int size = a.length;
                if (size < prevSize) throw new RuntimeException("WRONG WAY");
                if (size > maxSize)  throw new RuntimeException("OVERSHOOT");
                if (size == maxSize) return true;
                prevSize = size;
                return false;
            }

            public void run() {
                try {
                    Integer[] empty = new Integer[0];
                    while (true) {
                        if (checkProgress(m.values().toArray())) return;
                        if (checkProgress(m.keySet().toArray())) return;
                        if (checkProgress(m.values().toArray(empty))) return;
                        if (checkProgress(m.keySet().toArray(empty))) return;
                    }
                } catch (Throwable t) {
                   throwable[0] = t;
                }}};

        t2.start();
        t1.start();

        t1.join();
        t2.join();

        if (throwable[0] != null)
            throw throwable[0];
    }
}
