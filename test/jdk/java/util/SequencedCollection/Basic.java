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

import java.io.*;
import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

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

    static SequencedSet<String> setFromMap(List<String> contents) {
        var lhm = new LinkedHashMap<String, Boolean>();
        var ss = Collections.newSequencedSetFromMap(lhm);
        ss.addAll(contents);
        return ss;
    }

    static SequencedCollection<String> ucoll(SequencedCollection<String> coll) {
        return Collections.unmodifiableSequencedCollection(coll);
    }

    static SequencedSet<String> uset(SequencedSet<String> set) {
        return Collections.unmodifiableSequencedSet(set);
    }

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
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    public Iterator<Object[]> empties() {
        return Arrays.asList(
            new Object[] { "EmptyArrayDeque", new ArrayDeque<>(), List.of() },
            new Object[] { "EmptyArrayList", new ArrayList<>(), List.of() },
            new Object[] { "AsList", Arrays.asList(new String[0]), List.of() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(), List.of() },
            new Object[] { "LinkedList", new LinkedList<>(), List.of() },
            new Object[] { "ListOf", List.of(), List.of() },
            new Object[] { "SetFromMap", setFromMap(List.of()), List.of() },
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
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
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
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="serializable")
    public Iterator<Object[]> serializable() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="notserializable")
    public Iterator<Object[]> notSerializable() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL).reversed() },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL).reversed() },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()).reversed() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL).reversed() },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL).reversed() },
            new Object[] { "ListOf", ORIGINAL.reversed() },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL).reversed() },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)).reversed() },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)).reversed() }
        ).iterator();
    }

    @DataProvider(name="doublereverse")
    public Iterator<Object[]> doubleReverse() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL) },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL) },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()) },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL) },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL) },
            new Object[] { "ListOf", ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL) },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL) },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL) }
        ).iterator();
    }

    @DataProvider(name="unmodifiable")
    public Iterator<Object[]> unmodifiable() {
        return Arrays.asList(
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "ListOf", ORIGINAL.subList(1, 3), ORIGINAL.subList(1, 3) },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    // ========== Assertions ==========

    /**
     * Basic checks over the contents of a SequencedCollection,
     * compared to a reference List, in one direction.
     *
     * @param seq the SequencedCollection under test
     * @param ref the reference List
     */
    public void checkOneWay(SequencedCollection<String> seq, List<String> ref) {
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
            assertThrows(NoSuchElementException.class, () -> seq.getFirst());
            assertThrows(NoSuchElementException.class, () -> seq.getLast());
            assertThrows(NoSuchElementException.class, () -> seq.removeFirst());
            assertThrows(NoSuchElementException.class, () -> seq.removeLast());
        } else {
            assertTrue(seq.size() > 0);
            assertEquals(seq.getFirst(), ref.get(0));
            assertEquals(seq.getLast(), ref.get(ref.size() - 1));
        }
    }

    /**
     * Check the contents of a SequencedCollection against a reference List,
     * in both directions.
     *
     * @param seq the SequencedCollection under test
     * @param ref the reference List
     */
    public void checkContents(SequencedCollection<String> seq, List<String> ref) {
        checkOneWay(seq, ref);

        var rref = new ArrayList<>(ref);
        Collections.reverse(rref);
        var rseq = seq.reversed();
        checkOneWay(rseq, rref);

        var rreq = rseq.reversed();
        checkOneWay(rreq, ref);
    }

    /**
     * Check that modification operations will throw UnsupportedOperationException.
     *
     * @param seq the SequencedCollection under test
     */
    public void checkUnmodifiable(SequencedCollection<String> seq) {
        final var UOE = UnsupportedOperationException.class;

        assertThrows(UOE, () -> seq.addFirst("x"));
        assertThrows(UOE, () -> seq.addLast("x"));
        assertThrows(UOE, () -> seq.removeFirst());
        assertThrows(UOE, () -> seq.removeLast());

        assertThrows(UOE, () -> seq.add("x"));
        assertThrows(UOE, () -> seq.addAll(List.of()));
        assertThrows(UOE, () -> seq.clear());
        assertThrows(UOE, () -> { var it = seq.iterator(); it.next(); it.remove(); });
        assertThrows(UOE, () -> seq.remove("x"));
        assertThrows(UOE, () -> seq.removeAll(List.of()));
        assertThrows(UOE, () -> seq.removeIf(x -> false));
        assertThrows(UOE, () -> seq.retainAll(seq));
    }

    // ========== Tests ==========

    @Test(dataProvider="all")
    public void testFundamentals(String label, SequencedCollection<String> seq, List<String> ref) {
        checkContents(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddFirst(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(0, "x");
        seq.addFirst("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.addLast("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="removes")
    public void testRemoveFirst(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(0);
        var act = seq.removeFirst();
        assertEquals(act, exp);
        checkContents(seq, ref);
    }

    @Test(dataProvider="removes")
    public void testRemoveLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(ref.size() - 1);
        var act = seq.removeLast();
        assertEquals(act, exp);
        checkContents(seq, ref);
    }

    @Test(dataProvider="serializable")
    public void testSerializable(String label, SequencedCollection<String> seq, List<String> ref)
        throws ClassNotFoundException, IOException
    {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(seq);
        }

        try (var bais = new ByteArrayInputStream(baos.toByteArray());
             var ois = new ObjectInputStream(bais)) {
            var seq2 = (SequencedCollection<String>) ois.readObject();
            checkContents(seq2, ref);
        }
    }

    @Test(dataProvider="notserializable")
    public void testNotSerializable(String label, SequencedCollection<String> seq)
        throws ClassNotFoundException, IOException
    {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            assertThrows(ObjectStreamException.class, () -> oos.writeObject(seq));
        }
    }

    @Test(dataProvider="doublereverse")
    public void testDoubleReverse(String label, SequencedCollection<String> seq) {
        var rrseq = seq.reversed().reversed();
        assertSame(rrseq, seq);
    }

    @Test(dataProvider="unmodifiable")
    public void testUnmodifiable(String label, SequencedCollection<String> seq, List<String> ref) {
        checkUnmodifiable(seq);
        checkUnmodifiable(seq.reversed());
        checkContents(seq, ref);
    }
}
