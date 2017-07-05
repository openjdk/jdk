/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.testng.Assert.fail;

/**
 * NullArgsTestCase -- Given a Consumer&ltObject[]&gt, and an Object[] array of args, call the block with the args,
 * assert success, and then call the consumer N times, each time setting one of the args to null, and assert that
 * all these throw NPE.
 *
 * Typically this would be combined with a DataProvider that serves up combinations of things to be tested, as in
 * IteratorsNullTest.
 */
public abstract class NullArgsTestCase {
    public final String name;
    public final Consumer<Object[]> sink;
    public final Object[] args;

    protected NullArgsTestCase(String name, Consumer<Object[]> sink, Object[] args) {
        this.name = name;
        this.sink = sink;
        this.args = args;
    }

    @Test
    public void goodNonNull() {
        sink.accept(args);
    }

    @Test
    public void throwWithNull() {
        for (int i=0; i<args.length; i++) {
            Object[] temp = Arrays.copyOf(args, args.length);
            temp[i] = null;
            try {
                sink.accept(temp);
                fail(String.format("Expected NullPointerException for argument %d of test case %s", i, name));
            }
            catch (NullPointerException e) {
                // Success
            }
        }
    }
}
