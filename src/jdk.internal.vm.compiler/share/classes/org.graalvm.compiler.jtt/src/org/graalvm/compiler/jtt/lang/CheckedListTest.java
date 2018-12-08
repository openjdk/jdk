/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.jtt.lang;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CheckedListTest extends JTTTest {
    @Test
    public void run0() throws Throwable {
        runTest("testCast");
    }

    @SuppressWarnings({"unchecked", "rawtypes", "serial"})
    public static void testCast() {
        final AtomicBoolean addAllWasCalled = new AtomicBoolean();
        ArrayList orig = new ArrayList() {
            @Override
            public boolean addAll(Collection c) {
                addAllWasCalled.set(true);
                return super.addAll(c);
            }
        };
        Collection checked = Collections.checkedList(orig, String.class);
        ArrayList passed = new ArrayList(Arrays.asList("a", "b", 5678, "d"));
        try {
            checked.addAll(passed);
            System.out.println(checked);
            throw new RuntimeException("not good");
        } catch (ClassCastException e) {
            // OKK
        }
        Assert.assertFalse(addAllWasCalled.get());
    }

    @Test
    public void run1() throws Throwable {
        runTest("testCopyOf");
    }

    public static void testCopyOf() {
        Object[] mixed = new Object[]{"a", "b", 18};
        try {
            Arrays.copyOf(mixed, 4, String[].class);
        } catch (ArrayStoreException e) {
            return;
        }
        throw new RuntimeException("should not reach here");
    }

    @Test
    public void run2() throws Throwable {
        runTest("testArraycopy");
    }

    public static void testArraycopy() {
        Object[] mixed = new Object[]{"a", "b", 18};
        try {
            String[] strings = new String[4];
            System.arraycopy(mixed, 0, strings, 0, 3);
        } catch (ArrayStoreException e) {
            return;
        }
        throw new RuntimeException("should not reach here");
    }
}
