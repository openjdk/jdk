/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.compiler.collections.Equivalence;
import org.junit.Assert;
import org.junit.Test;

public class EquivalenceTest {

    private static final String TEST_STRING = "Graal";
    private static final String TEST_STRING2 = "Graal2";

    @Test
    public void testDEFAULT() {
        Assert.assertTrue(Equivalence.DEFAULT.equals(TEST_STRING, new String(TEST_STRING)));
        Assert.assertEquals(Equivalence.DEFAULT.hashCode(TEST_STRING), Equivalence.DEFAULT.hashCode(new String(TEST_STRING)));
        Assert.assertFalse(Equivalence.DEFAULT.equals(TEST_STRING, TEST_STRING2));
        Assert.assertNotEquals(Equivalence.DEFAULT.hashCode(TEST_STRING), Equivalence.DEFAULT.hashCode(TEST_STRING2));
    }

    @Test
    public void testIDENTITY() {
        Assert.assertFalse(Equivalence.IDENTITY.equals(TEST_STRING, new String(TEST_STRING)));
        Assert.assertEquals(Equivalence.IDENTITY.hashCode(TEST_STRING), Equivalence.IDENTITY.hashCode(new String(TEST_STRING)));
        Assert.assertFalse(Equivalence.IDENTITY.equals(TEST_STRING, TEST_STRING2));
        Assert.assertNotEquals(Equivalence.IDENTITY.hashCode(TEST_STRING), Equivalence.IDENTITY.hashCode(TEST_STRING2));
    }

    @Test
    public void testIDENTITYWITHSYSTEMHASHCODE() {
        Assert.assertFalse(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.equals(TEST_STRING, new String(TEST_STRING)));
        Assert.assertNotEquals(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.hashCode(TEST_STRING), Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.hashCode(new String(TEST_STRING)));
        Assert.assertFalse(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.equals(TEST_STRING, TEST_STRING2));
        Assert.assertNotEquals(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.hashCode(TEST_STRING), Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE.hashCode(TEST_STRING2));
    }

}
