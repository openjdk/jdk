/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/*
 * @test
 * @bug     8266571
 * @summary Basic tests for SequencedCollection
 * @modules java.base/java.util:open
 * @build   SimpleDeque SimpleList SimpleSortedSet
 * @run     testng Basic
 */

public class Basic {

    // ========== Data Providers ==========

    static final List<String> ORIGINAL = List.of("a", "b", "c", "d", "e", "f", "g");

    @DataProvider(name="all")
    public Iterator<Object[]> all() {
        var result = new ArrayList<Object[]>();
        populated().forEachRemaining(result::add);
        empties().forEachRemaining(result::add);
        return result.iterator();
    }

    public Iterator<Object[]> populated() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    public Iterator<Object[]> empties() {
        return Arrays.asList(
            new Object[] { "EmptyArrayDeque", new ArrayDeque<>(), List.of() },
            new Object[] { "EmptyArrayList", new ArrayList<>(), List.of() },
            new Object[] { "AsList", Arrays.asList(new String[0]), List.of() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(), List.of() },
            new Object[] { "ListOf", List.of(), List.of() },
            new Object[] { "SimpleDeque", new SimpleDeque<>(), List.of() },
            new Object[] { "SimpleList", new SimpleList<>(), List.of() },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(), List.of() },
            new Object[] { "TreeSet", new TreeSet<>(), List.of() }
        ).iterator();
    }

    @DataProvider(name="adds")
    public Iterator<Object[]> adds() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="removes")
    public Iterator<Object[]> removes() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    // ========== Assertions ==========

    public void checkForward(SequencedCollection<String> seq, List<String> ref) {
        var list1 = new ArrayList<String>();
        for (var s : seq)
            list1.add(s);
        assertEquals(list1, ref);

        var list2 = new ArrayList<String>();
        seq.forEach(list2::add);
        assertEquals(list2, ref);

        var list3 = Arrays.asList(seq.toArray());
        assertEquals(list3, ref);

        var list4 = Arrays.asList(seq.toArray(new String[0]));
        assertEquals(list4, ref);

        var list5 = Arrays.asList(seq.toArray(String[]::new));
        assertEquals(list5, ref);

        var list6 = seq.stream().toList();
        assertEquals(list6, ref);

        var list7 = seq.parallelStream().toList();
        assertEquals(list7, ref);

        assertEquals(seq.size(), ref.size());

        for (var s : ref)
            assertTrue(seq.contains(s));

        if (seq.isEmpty()) {
            assertEquals(seq.size(), 0);
        } else {
            assertTrue(seq.size() > 0);
            assertEquals(seq.getFirst(), ref.get(0));
            assertEquals(seq.getLast(), ref.get(ref.size() - 1));
        }
    }

    public void checkFundamentals(SequencedCollection<String> seq, List<String> ref) {
        checkForward(seq, ref);

        var rref = new ArrayList<>(ref);
        Collections.reverse(rref);
        var rseq = seq.reversed();
        checkForward(rseq, rref);

        var seq1 = rseq.reversed();
        // assertSame(seq1, seq); // not all reversed implementations simply unwrap
        checkForward(seq1, ref);
    }

    // ========== Tests ==========

    @Test(dataProvider="all")
    public void testFundamentals(String label, SequencedCollection<String> seq, List<String> ref) {
        checkFundamentals(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddFirst(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(0, "x");
        seq.addFirst("x");
        checkFundamentals(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.addLast("x");
        checkFundamentals(seq, ref);
    }

    @Test(dataProvider="removes")
    public void testRemoveFirst(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(0);
        var act = seq.removeFirst();
        assertEquals(act, exp);
        checkFundamentals(seq, ref);
    }

    @Test(dataProvider="removes")
    public void testRemoveLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(ref.size() - 1);
        var act = seq.removeLast();
        assertEquals(act, exp);
        checkFundamentals(seq, ref);
    }
}
