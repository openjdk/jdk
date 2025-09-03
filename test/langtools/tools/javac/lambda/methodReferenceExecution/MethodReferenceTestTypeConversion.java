/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8003639
 * @summary convert lambda testng tests to jtreg and add them
 * @run junit MethodReferenceTestTypeConversion
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * @author Robert Field
 */

class MethodReferenceTestTypeConversion_E<T> {
    T xI(T t) { return t; }
}

public class MethodReferenceTestTypeConversion {

    interface ISi { int m(Short a); }

    interface ICc { char m(Character a); }

    @Test
    public void testUnboxObjectToNumberWiden() {
        ISi q = (new MethodReferenceTestTypeConversion_E<Short>())::xI;
        assertEquals((short)77, q.m((short)77));
    }

    @Test
    public void testUnboxObjectToChar() {
        ICc q = (new MethodReferenceTestTypeConversion_E<Character>())::xI;
        assertEquals('@', q.m('@'));
    }

}
