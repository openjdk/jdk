/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.stream.Collectors;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Unit test for CharSequence default methods
 * @bug 8012665 8025002
 * @run testng DefaultTest
 */

@Test(groups = "lib")
public class DefaultTest {

    @Test(expectedExceptions = NoSuchElementException.class)
    public void testEmptyChars() {
        PrimitiveIterator.OfInt s = "".chars().iterator();
        assertFalse(s.hasNext());
        int ch = s.nextInt();
    }

    public void testSimpleChars() {
        List<Integer> list = "abc".chars().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList((int) 'a', (int) 'b', (int) 'c'));
    }

    public void testCodePointsCharacteristics() {
        Spliterator.OfInt s = "".codePoints().spliterator();
        assertFalse(s.hasCharacteristics(Spliterator.SIZED | Spliterator.SUBSIZED));
        assertTrue(s.hasCharacteristics(Spliterator.ORDERED));
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public void testEmptyCodePoints() {
        PrimitiveIterator.OfInt s = "".codePoints().iterator();
        assertFalse(s.hasNext());
        int cp = s.nextInt();
    }

    public void testSimpleCodePoints() {
        List<Integer> list = "abc".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList((int)'a', (int)'b', (int)'c'));
    }

    public void testUndefCodePoints() {
        List<Integer> list = "X\ufffeY".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList((int)'X', 0xFFFE, (int)'Y'));
    }

    public void testSurrogatePairing() {
        // U+1D11E = MUSICAL SYMBOL G CLEF
        // equivalent to surrogate pair U+D834 U+DD1E
        List<Integer> list;
        final int GCLEF = 0x1d11e;

        list = "\ud834\udd1e".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList(GCLEF));
        list = "A\ud834\udd1e".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList((int)'A', GCLEF));
        list = "\ud834\udd1eB".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList(GCLEF, (int)'B'));
        list = "X\ud834\udd1eY".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList((int)'X', GCLEF, (int)'Y'));
    }

    public void testUndefUnpaired() {
        List<Integer> list = "W\udd1eX\ud834Y\ufffeZ".codePoints().boxed().collect(Collectors.toList());
        assertEquals(list, Arrays.asList(
            (int)'W', 0xdd1e, (int)'X', 0xd834, (int)'Y', 0xfffe, (int)'Z'));
    }
}
