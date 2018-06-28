/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.vm.compiler.collections.test;

import jdk.internal.vm.compiler.collections.Pair;
import org.junit.Assert;
import org.junit.Test;

public class PairTest {

    @Test
    public void testCreate() {
        Assert.assertEquals(Pair.create(null, null), Pair.empty());
        Assert.assertNotEquals(Pair.create(null, null), null);
        Assert.assertEquals(Pair.createLeft(null), Pair.empty());
        Assert.assertEquals(Pair.createRight(null), Pair.empty());
        Assert.assertEquals(Pair.create(1, null), Pair.createLeft(1));
        Assert.assertEquals(Pair.create(null, 1), Pair.createRight(1));
    }

    @Test
    public void testUtilities() {
        Pair<Integer, Integer> pair = Pair.create(1, null);
        Assert.assertEquals(pair.getLeft(), Integer.valueOf(1));
        Assert.assertEquals(pair.getRight(), null);
        Assert.assertEquals(pair.toString(), "(1, null)");
        Assert.assertEquals(pair.hashCode(), Pair.createLeft(1).hashCode());
    }

}
