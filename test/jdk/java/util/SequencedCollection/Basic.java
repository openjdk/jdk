/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CopyOnWriteArrayList;
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

// TODO test that remove(obj) with duplicates removes the right element

public class Basic {

    // ========== Data Providers ==========

    static final List<String> ORIGINAL = List.of("a", "b", "c", "d", "e", "f", "g");

    static List<String> cklist(List<String> contents) {
        return Collections.checkedList(contents, String.class);
    }

    static NavigableSet<String> cknav(NavigableSet<String> set) {
        return Collections.checkedNavigableSet(set, String.class);
    }

    static SortedSet<String> cksorted(SortedSet<String> set) {
        return Collections.checkedSortedSet(set, String.class);
    }

    static SequencedSet<String> setFromMap(List<String> contents) {
        var lhm = new LinkedHashMap<String, Boolean>();
        var ss = Collections.newSequencedSetFromMap(lhm);
        ss.addAll(contents);
        return ss;
    }

    static SequencedCollection<String> ucoll(SequencedCollection<String> coll) {
        return Collections.unmodifiableSequencedCollection(coll);
    }

    static List<String> sylist(List<String> list) {
        return Collections.synchronizedList(list);
    }

    static List<String> ulist(List<String> list) {
        return Collections.unmodifiableList(list);
    }

    static NavigableSet<String> unav(NavigableSet<String> set) {
        return Collections.unmodifiableNavigableSet(set);
    }

    static SequencedSet<String> uset(SequencedSet<String> set) {
        return Collections.unmodifiableSequencedSet(set);
    }

    static SortedSet<String> usorted(SortedSet<String> set) {
        return Collections.unmodifiableSortedSet(set);
    }

    static <T> List<T> copyReversed(List<T> list) {
        var r = new ArrayList<T>(list);
        Collections.reverse(r);
        return r;
    }

    @DataProvider(name="all")
    public Iterator<Object[]> all() {
        var result = new ArrayList<Object[]>();
        populated().forEachRemaining(result::add);
        empties().forEachRemaining(result::add);
        return result.iterator();
    }

    @DataProvider(name="populated")
    public Iterator<Object[]> populated() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()), ORIGINAL },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="empties")
    public Iterator<Object[]> empties() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(), List.of() },
            new Object[] { "ArrayList", new ArrayList<>(), List.of() },
            new Object[] { "AsList", Arrays.asList(new String[0]), List.of() },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(), List.of() },
            new Object[] { "EmptyList", Collections.emptyList(), List.of() },
            new Object[] { "EmptyNavigableSet", Collections.emptyNavigableSet(), List.of() },
            new Object[] { "EmptySortedSet", Collections.emptySortedSet(), List.of() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(), List.of() },
            new Object[] { "LinkedList", new LinkedList<>(), List.of() },
            new Object[] { "ListOf", List.of(), List.of() },
            new Object[] { "SetFromMap", setFromMap(List.of()), List.of() },
            new Object[] { "SimpleDeque", new SimpleDeque<>(), List.of() },
            new Object[] { "SimpleList", new SimpleList<>(), List.of() },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(), List.of() },
            new Object[] { "SynchList", sylist(new ArrayList<>()), List.of() },
            new Object[] { "TreeSet", new TreeSet<>(), List.of() },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>()), List.of() },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>()), List.of() }
        ).iterator();
    }

    @DataProvider(name="adds")
    public Iterator<Object[]> adds() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="unpositionedAdd")
    public Iterator<Object[]> unpositionedAdd() {
        return Arrays.<Object[]>asList(
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="removes")
    public Iterator<Object[]> removes() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="emptyRemoves")
    public Iterator<Object[]> emptyRemoves() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(), List.of() },
            new Object[] { "ArrayList", new ArrayList<>(), List.of() },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(), List.of() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(), List.of() },
            new Object[] { "LinkedList", new LinkedList<>(), List.of() },
            new Object[] { "SetFromMap", setFromMap(List.of()), List.of() },
            new Object[] { "SimpleDeque", new SimpleDeque<>(), List.of() },
            new Object[] { "SimpleList", new SimpleList<>(), List.of() },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(), List.of() },
            new Object[] { "SynchList", sylist(new ArrayList<>()), List.of() },
            new Object[] { "TreeSet", new TreeSet<>(), List.of() }
        ).iterator();
    }

    @DataProvider(name="serializable")
    public Iterator<Object[]> serializable() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()), ORIGINAL },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL), ORIGINAL },
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL), ORIGINAL },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="notSerializable")
    public Iterator<Object[]> notSerializable() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL).reversed() },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL).reversed() },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()).reversed() },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL).reversed() },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL).reversed() },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL).reversed() },
            new Object[] { "ListOf", ORIGINAL.reversed() },
            new Object[] { "SetFromMap", setFromMap(ORIGINAL).reversed() },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)).reversed() },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)).reversed() },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)).reversed() }
        ).iterator();
    }

    @DataProvider(name="doubleReverse")
    public Iterator<Object[]> doubleReverse() {
        return Arrays.asList(
            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL) },
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL) },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()) },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL) },
            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL) },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL) },
            new Object[] { "ListOf", ORIGINAL },
            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL) },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL) },
            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL) },
            new Object[] { "SynchList", sylist(new ArrayList<>(ORIGINAL)) }
        ).iterator();
    }

    @DataProvider(name="unmodifiable")
    public Iterator<Object[]> unmodifiable() {
        return Arrays.asList(
            new Object[] { "ListOf", ORIGINAL, ORIGINAL },
            new Object[] { "ListOfSub", ORIGINAL.subList(1, 3), ORIGINAL.subList(1, 3) },
            new Object[] { "SingleList", Collections.singletonList("a"), List.of("a") },
            new Object[] { "UnmodColl", ucoll(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodList", ulist(new ArrayList<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodNav", unav(new TreeSet<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSet", uset(new LinkedHashSet<>(ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSorted", usorted(new TreeSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="checkedList")
    public Iterator<Object[]> checkedList() {
        return Arrays.<Object[]>asList(
            new Object[] { "ChkList", cklist(new ArrayList<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="checkedNavSet")
    public Iterator<Object[]> checkedNavSet() {
        return Arrays.<Object[]>asList(
            new Object[] { "ChkNav", cknav(new TreeSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="checkedSortedSet")
    public Iterator<Object[]> checkedSortedSet() {
        return Arrays.<Object[]>asList(
            new Object[] { "ChkSorted", cksorted(new TreeSet<>(ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    // These Lists don't all implement RandomAccess; they're Lists that should be
    // tested for proper propagation of the RandomAccess markers.
    @DataProvider(name="randomAccessLists")
    public Iterator<Object[]> randomAccessLists() {
        return Arrays.asList(
            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL) },
            new Object[] { "AsList", Arrays.asList(ORIGINAL.toArray()) },
            new Object[] { "COWAL", new CopyOnWriteArrayList<>(ORIGINAL) },
            new Object[] { "LinkedList", new LinkedList<>(ORIGINAL) },
            new Object[] { "ListOf", ORIGINAL },
            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL) }
        ).iterator();
    }

    // mode bit tests for subList testing

    boolean reverseList(int mode) { return (mode & 1) != 0; }
    boolean reverseSub(int mode)  { return (mode & 2) != 0; }
    boolean isReversed(int mode) { return reverseList(mode) ^ reverseSub(mode); }

    List<String> applyMode(int mode, List<String> base) {
        var list = reverseList(mode) ? base.reversed() : base;
        var sub = list.subList(2, 5);
        return reverseSub(mode) ? sub.reversed() : sub;
    }

    /**
     * Generate cases for testing subLists. For each different List implementation, generate 4
     * cases from the two bits of the testing mode int value:
     *
     *  (bit 1) if true, the List is reversed
     *  (bit 2) if true, the subList is reversed
     *
     * @return the generated cases
     */
    @DataProvider(name="subListMods")
    public Iterator<Object[]> subListMods() {
        var cases = new ArrayList<Object[]>();
        for (int mode = 0; mode < 4; mode++) {
            cases.addAll(Arrays.asList(
                new Object[] { "ArrayList", mode, new ArrayList<>(ORIGINAL), ORIGINAL },
                new Object[] { "COWAL", mode, new CopyOnWriteArrayList<>(ORIGINAL), ORIGINAL },
                new Object[] { "LinkedList", mode, new LinkedList<>(ORIGINAL), ORIGINAL },
                new Object[] { "SimpleList", mode, new SimpleList<>(ORIGINAL), ORIGINAL }
            ));
        }
        return cases.iterator();
    }

    @DataProvider(name="iteratorMods")
    public Iterator<Object[]> iteratorMods() {
        var cases = new ArrayList<Object[]>();
        for (boolean rev : List.of(false, true)) {
            cases.addAll(Arrays.asList(
                new Object[] { "ArrayList", rev, new ArrayList<>(ORIGINAL), ORIGINAL },
                new Object[] { "LinkedList", rev, new LinkedList<>(ORIGINAL), ORIGINAL },
                new Object[] { "SimpleList", rev, new SimpleList<>(ORIGINAL), ORIGINAL }
            ));
        }
        return cases.iterator();
    }

    @DataProvider(name="subListIteratorMods")
    public Iterator<Object[]> subListIteratorMods() {
        var cases = new ArrayList<Object[]>();
        for (int mode = 0; mode < 4; mode++) {
            cases.addAll(Arrays.asList(
                new Object[] { "ArrayList", mode, new ArrayList<>(ORIGINAL), ORIGINAL },
                new Object[] { "LinkedList", mode, new LinkedList<>(ORIGINAL), ORIGINAL },
                new Object[] { "SimpleList", mode, new SimpleList<>(ORIGINAL), ORIGINAL }
            ));
        }
        return cases.iterator();
    }

    // ========== Assertions ==========

    /**
     * Basic checks over the contents of a SequencedCollection,
     * compared to a reference List, in one direction.
     *
     * @param seq the SequencedCollection under test
     * @param ref the reference List
     */
    public void checkContents1(SequencedCollection<String> seq, List<String> ref) {
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
        assertEquals(seq.isEmpty(), ref.isEmpty());

        for (var s : ref) {
            assertTrue(seq.contains(s));
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
        checkContents1(seq, ref);

        var rref = copyReversed(ref);
        var rseq = seq.reversed();
        checkContents1(rseq, rref);

        var rrseq = rseq.reversed();
        checkContents1(rrseq, ref);
    }

    /**
     * Check that modification operations will throw UnsupportedOperationException,
     * in one direction.
     *
     * @param seq the SequencedCollection under test
     */
    public void checkUnmodifiable1(SequencedCollection<String> seq) {
        final var UOE = UnsupportedOperationException.class;

        assertThrows(UOE, () -> seq.add("x"));
        assertThrows(UOE, () -> seq.clear());
        assertThrows(UOE, () -> { var it = seq.iterator(); it.next(); it.remove(); });
        assertThrows(UOE, () -> seq.removeIf(x -> true));

        assertThrows(UOE, () -> seq.addFirst("x"));
        assertThrows(UOE, () -> seq.addLast("x"));
        assertThrows(UOE, () -> seq.removeFirst());
        assertThrows(UOE, () -> seq.removeLast());

// TODO these ops should throw unconditionally, but they don't in some implementations
     // assertThrows(UOE, () -> seq.addAll(List.of()));
     // assertThrows(UOE, () -> seq.remove("x"));
     // assertThrows(UOE, () -> seq.removeAll(List.of()));
     // assertThrows(UOE, () -> seq.removeIf(x -> false));
     // assertThrows(UOE, () -> seq.retainAll(seq));
        assertThrows(UOE, () -> seq.addAll(seq));
        assertThrows(UOE, () -> seq.remove(seq.iterator().next()));
        assertThrows(UOE, () -> seq.removeAll(seq));
        assertThrows(UOE, () -> seq.retainAll(List.of()));
    }

    /**
     * Check that modification operations will throw UnsupportedOperationException,
     * in both directions.
     *
     * @param seq the SequencedCollection under test
     */
    public void checkUnmodifiable(SequencedCollection<String> seq) {
        checkUnmodifiable1(seq);
        checkUnmodifiable1(seq.reversed());
    }

    static final Class<? extends Throwable> CCE = ClassCastException.class;

    public void checkCheckedList(List<String> list) {
        List<Object> objList = (List<Object>)(List)list;
        assertThrows(CCE, () -> { objList.addFirst(new Object()); });
        assertThrows(CCE, () -> { objList.addLast(new Object()); });
        assertThrows(CCE, () -> { objList.reversed().addFirst(new Object()); });
        assertThrows(CCE, () -> { objList.reversed().addLast(new Object()); });
    }

    public void checkCheckedNavSet(NavigableSet<String> set) {
        NavigableSet<Object> objSet = (NavigableSet<Object>)(NavigableSet)set;
        assertThrows(CCE, () -> { objSet.add(new Object()); });
        assertThrows(CCE, () -> { objSet.reversed().add(new Object()); });
    }

    public void checkCheckedSortedSet(SortedSet<String> set) {
        SortedSet<Object> objSet = (SortedSet<Object>)(SortedSet)set;
        assertThrows(CCE, () -> { objSet.add(new Object()); });
        assertThrows(CCE, () -> { objSet.reversed().add(new Object()); });
    }

    // ========== Tests ==========

    @Test(dataProvider="all")
    public void testFundamentals(String label, SequencedCollection<String> seq, List<String> ref) {
        checkContents(seq, ref);
    }

    @Test(dataProvider="populated")
    public void testGetFirst(String label, SequencedCollection<String> seq, List<String> ref) {
        assertEquals(seq.getFirst(), ref.get(0));
        assertEquals(seq.reversed().getFirst(), ref.get(ref.size() - 1));
        checkContents(seq, ref);
    }

    @Test(dataProvider="populated")
    public void testGetLast(String label, SequencedCollection<String> seq, List<String> ref) {
        assertEquals(seq.getLast(), ref.get(ref.size() - 1));
        assertEquals(seq.reversed().getLast(), ref.get(0));
        checkContents(seq, ref);
    }

    @Test(dataProvider="empties")
    public void testEmptyGetFirst(String label, SequencedCollection<String> seq, List<String> ref) {
        assertThrows(NoSuchElementException.class, () -> seq.getFirst());
        assertThrows(NoSuchElementException.class, () -> seq.reversed().getFirst());
        checkContents(seq, ref);
    }

    @Test(dataProvider="empties")
    public void testEmptyGetLast(String label, SequencedCollection<String> seq, List<String> ref) {
        assertThrows(NoSuchElementException.class, () -> seq.getLast());
        assertThrows(NoSuchElementException.class, () -> seq.reversed().getLast());
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
    public void testAddFirstRev(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.reversed().addFirst("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.addLast("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="adds")
    public void testAddLastRev(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(0, "x");
        seq.reversed().addLast("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="unpositionedAdd")
    public void testUnpositionedAdd(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.add("x");
        checkContents(seq, ref);
    }

    @Test(dataProvider="unpositionedAdd")
    public void testUnpositionedAddRev(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add("x");
        seq.reversed().add("x");
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
    public void testRemoveFirstRev(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(ref.size() - 1);
        var act = seq.reversed().removeFirst();
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

    @Test(dataProvider="removes")
    public void testRemoveLastRev(String label, SequencedCollection<String> seq, List<String> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = ref.remove(0);
        var act = seq.reversed().removeLast();
        assertEquals(act, exp);
        checkContents(seq, ref);
    }

    @Test(dataProvider="emptyRemoves")
    public void testEmptyRemoveFirst(String label, SequencedCollection<String> seq, List<String> baseref) {
        assertThrows(NoSuchElementException.class, () -> seq.removeFirst());
        assertThrows(NoSuchElementException.class, () -> seq.reversed().removeFirst());
        checkContents(seq, baseref);
    }

    @Test(dataProvider="emptyRemoves")
    public void testEmptyRemoveLast(String label, SequencedCollection<String> seq, List<String> baseref) {
        assertThrows(NoSuchElementException.class, () -> seq.removeLast());
        assertThrows(NoSuchElementException.class, () -> seq.reversed().removeLast());
        checkContents(seq, baseref);
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

    @Test(dataProvider="notSerializable")
    public void testNotSerializable(String label, SequencedCollection<String> seq)
        throws ClassNotFoundException, IOException
    {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            assertThrows(ObjectStreamException.class, () -> oos.writeObject(seq));
        }
    }

    @Test(dataProvider="doubleReverse")
    public void testDoubleReverse(String label, SequencedCollection<String> seq) {
        var rrseq = seq.reversed().reversed();
        assertSame(rrseq, seq);
    }

    @Test(dataProvider="unmodifiable")
    public void testUnmodifiable(String label, SequencedCollection<String> seq, List<String> ref) {
        checkUnmodifiable(seq);
        checkContents(seq, ref);
    }

    @Test(dataProvider="checkedList")
    public void testCheckedList(String label, List<String> list, List<String> ref) {
        checkCheckedList(list);
        checkContents(list, ref);
    }

    @Test(dataProvider="checkedNavSet")
    public void testCheckedNavSet(String label, NavigableSet<String> set, List<String> ref) {
        checkCheckedNavSet(set);
        checkContents(set, ref);
    }

    @Test(dataProvider="checkedSortedSet")
    public void testCheckedSortedSet(String label, SortedSet<String> set, List<String> ref) {
        checkCheckedSortedSet(set);
        checkContents(set, ref);
    }

    // Indexes for subList modification tests:
    // 0  1  2  3  4  5  6
    // a, b, c, d, e, f, g
    //       c, d, e

    @Test(dataProvider="subListMods")
    public void testSubListGet(String label, int mode, List<String> list, List<String> base) {
        var sub = applyMode(mode, list);
        assertEquals(sub.getFirst(), isReversed(mode) ? "e" : "c");
        assertEquals(sub.getLast(),  isReversed(mode) ? "c" : "e");
    }

    @Test(dataProvider="subListMods")
    public void testSubListAddFirst(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        refList.add(isReversed(mode) ? 5 : 2, "x");
        sub.addFirst("x");
        refSub.add(0, "x");

        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListMods")
    public void testSubListAddLast(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        refList.add(isReversed(mode) ? 2 : 5, "x");
        sub.addLast("x");
        refSub.add("x");

        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListMods")
    public void testSubListRemoveFirst(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        refList.remove(isReversed(mode) ? 4 : 2);
        var act = sub.removeFirst();
        var exp = refSub.remove(0);

        assertEquals(act, exp);
        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListMods")
    public void testSubListRemoveLast(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        refList.remove(isReversed(mode) ? 2 : 4);
        var act = sub.removeLast();
        var exp = refSub.remove(refSub.size() - 1);

        assertEquals(act, exp);
        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListMods")
    public void testSubListAddAllFirst(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        if (isReversed(mode))
            refList.addAll(5, List.of("y", "x"));
        else
            refList.addAll(2, List.of("x", "y"));
        sub.addAll(0, List.of("x", "y"));
        refSub.addAll(0, List.of("x", "y"));

        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListMods")
    public void testSubListAddAllLast(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        if (isReversed(mode))
            refList.addAll(2, List.of("y", "x"));
        else
            refList.addAll(5, List.of("x", "y"));
        sub.addAll(List.of("x", "y"));
        refSub.addAll(List.of("x", "y"));

        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="iteratorMods")
    public void testListIteratorAdd(String label, boolean rev, List<String> list, List<String> base) {
        var ref = new ArrayList<>(base);
        var it = (rev ? list.reversed() : list).listIterator();

        ref.add(rev ? 5 : 2, "x");
        it.next();
        it.next();
        it.add("x");

        assertEquals(it.next(), rev ? "e" : "c");
        checkContents(list, ref);
    }

    @Test(dataProvider="iteratorMods")
    public void testListIteratorSet(String label, boolean rev, List<String> list, List<String> base) {
        var ref = new ArrayList<>(base);
        var it = (rev ? list.reversed() : list).listIterator();

        ref.set(rev ? 5 : 1, "x");
        it.next();
        it.next();
        it.set("x");

        assertEquals(it.next(), rev ? "e" : "c");
        checkContents(list, ref);
    }

    @Test(dataProvider="iteratorMods")
    public void testListIteratorRemove(String label, boolean rev, List<String> list, List<String> base) {
        var ref = new ArrayList<>(base);
        var it = (rev ? list.reversed() : list).listIterator();

        ref.remove(rev ? 5 : 1);
        it.next();
        it.next();
        it.remove();

        assertEquals(it.next(), rev ? "e" : "c");
        checkContents(list, ref);
    }

    // SubList ListIterator modification tests.

    @Test(dataProvider="subListIteratorMods")
    public void testSubListIteratorAdd(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        var it = sub.listIterator();
        it.next();
        it.add("x");
        refList.add(isReversed(mode) ? 4 : 3, "x");
        refSub.add(1, "x");

        assertEquals(it.next(), "d");
        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListIteratorMods")
    public void testSubListIteratorSet(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        var it = sub.listIterator();
        it.next();
        it.set("x");
        refList.set(isReversed(mode) ? 4 : 2, "x");
        refSub.set(0, "x");

        assertEquals(it.next(), "d");
        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test(dataProvider="subListIteratorMods")
    public void testSubListIteratorRemove(String label, int mode, List<String> list, List<String> base) {
        var refList = new ArrayList<>(base);
        var sub = applyMode(mode, list);
        var refSub = new ArrayList<>(sub);

        var it = sub.listIterator();
        it.next();
        it.remove();
        refList.remove(isReversed(mode) ? 4 : 2);
        refSub.remove(0);

        assertEquals(it.next(), "d");
        checkContents(sub, refSub);
        checkContents(list, refList);
    }

    @Test
    public void testSynchListReversedAlwaysReturnsSameObject() {
        var list = sylist(ORIGINAL);
        var rev1 = list.reversed();
        var rev2 = list.reversed();
        assertSame(rev1, rev2);
    }

    @Test(dataProvider="randomAccessLists")
    public void testRandomAccess(String label, List<String> list) {
        assertEquals(list.reversed() instanceof RandomAccess, list instanceof RandomAccess);

        // Collections.synchronizedList() preservation of RandomAccess is somewhat
        // tested by java/util/Collection/MOAT.java but it might not have all these cases.
        assertEquals(sylist(list) instanceof RandomAccess, list instanceof RandomAccess);
        assertEquals(sylist(list).reversed() instanceof RandomAccess, sylist(list) instanceof RandomAccess);
    }
}
