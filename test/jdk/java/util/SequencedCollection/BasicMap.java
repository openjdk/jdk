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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @bug     8266571
 * @summary Basic tests for SequencedMap
 * @modules java.base/java.util:open
 * @build   SimpleSortedMap
 * @run     testng BasicMap
 */

public class BasicMap {

    // ========== Data Providers ==========

    static final Class<? extends Throwable> CCE  = ClassCastException.class;
    static final Class<? extends Throwable> NSEE = NoSuchElementException.class;
    static final Class<? extends Throwable> UOE  = UnsupportedOperationException.class;

    static final List<Map.Entry<String, Integer>> ORIGINAL =
        List.of(Map.entry("a", 1),
                Map.entry("b", 2),
                Map.entry("c", 3),
                Map.entry("d", 4),
                Map.entry("e", 5));

    static <M extends SequencedMap<String, Integer>>
    M load(M map, List<Map.Entry<String, Integer>> mappings) {
        for (var e : mappings)
            map.put(e.getKey(), e.getValue());
        return map;
    }

    static NavigableMap<String, Integer> cknav(NavigableMap<String, Integer> map) {
        return Collections.checkedNavigableMap(map, String.class, Integer.class);
    }

    static SortedMap<String, Integer> cksorted(SortedMap<String, Integer> map) {
        return Collections.checkedSortedMap(map, String.class, Integer.class);
    }

    static SequencedMap<String, Integer> umap(SequencedMap<String, Integer> map) {
        return Collections.unmodifiableSequencedMap(map);
    }

    static SortedMap<String, Integer> usorted(SortedMap<String, Integer> map) {
        return Collections.unmodifiableSortedMap(map);
    }

    static NavigableMap<String, Integer> unav(NavigableMap<String, Integer> map) {
        return Collections.unmodifiableNavigableMap(map);
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
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedMap", load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "TreeMap", load(new TreeMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "UnmodMap", umap(load(new LinkedHashMap<>(), ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="empties")
    public Iterator<Object[]> empties() {
        return Arrays.asList(
            new Object[] { "EmptyNavigableMap", Collections.emptyNavigableMap(), List.of() },
            new Object[] { "EmptySortedMap", Collections.emptySortedMap(), List.of() },
            new Object[] { "LinkedHashMap", new LinkedHashMap<>(), List.of() },
            new Object[] { "SimpleSortedMap", new SimpleSortedMap<>(), List.of() },
            new Object[] { "TreeMap", new TreeMap<>(), List.of() },
            new Object[] { "UnmodMap", umap(new LinkedHashMap<>()), List.of() }
        ).iterator();
    }

    @DataProvider(name="polls")
    public Iterator<Object[]> polls() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedMap", load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "TreeMap", load(new TreeMap<>(), ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="emptyPolls")
    public Iterator<Object[]> emptyPolls() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", new LinkedHashMap<>(), List.of() },
            new Object[] { "SimpleSortedMap", new SimpleSortedMap<>(), List.of() },
            new Object[] { "TreeMap", new TreeMap<>(), List.of() }
        ).iterator();
    }

    @DataProvider(name="puts")
    public Iterator<Object[]> puts() {
        return Arrays.<Object[]>asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="putUnpositioned")
    public Iterator<Object[]> putUnpositioned() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", false, load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "LinkedHashMap", true,  load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="putThrows")
    public Iterator<Object[]> putThrows() {
        return Arrays.asList(
            new Object[] { "SimpleSortedMap", load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "TreeMap", load(new TreeMap<>(), ORIGINAL), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="serializable")
    public Iterator<Object[]> serializable() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "TreeMap", load(new TreeMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "UnmodMap", umap(load(new LinkedHashMap<>(), ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="notSerializable")
    public Iterator<Object[]> notSerializable() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL).reversed() },
            new Object[] { "UnmodMap", umap(load(new LinkedHashMap<>(), ORIGINAL)).reversed() }
        ).iterator();
    }

    @DataProvider(name="doubleReverse")
    public Iterator<Object[]> doubleReverse() {
        return Arrays.<Object[]>asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL) }
        ).iterator();
    }

    @DataProvider(name="unmodifiable")
    public Iterator<Object[]> unmodifible() {
        return Arrays.<Object[]>asList(
            new Object[] { "UnmodMap", umap(load(new LinkedHashMap<>(), ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodNav", unav(load(new TreeMap<>(), ORIGINAL)), ORIGINAL },
            new Object[] { "UnmodSorted", usorted(load(new TreeMap<>(), ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    @DataProvider(name="checked")
    public Iterator<Object[]> checked() {
        return Arrays.<Object[]>asList(
            new Object[] { "ChkNav", cknav(load(new TreeMap<>(), ORIGINAL)), ORIGINAL },
            new Object[] { "ChkSorted", cksorted(load(new TreeMap<>(), ORIGINAL)), ORIGINAL }
        ).iterator();
    }

    // mode bit tests

    boolean reverseMap(int mode)  { return (mode & 1) != 0; }
    boolean reverseView(int mode) { return (mode & 2) != 0; }
    boolean callLast(int mode)    { return (mode & 4) != 0; }

    boolean refLast(int mode) { return reverseMap(mode) ^ reverseView(mode) ^ callLast(mode); }

    /**
     * Generate cases for testing the removeFirst and removeLast methods of map views. For each
     * different map implementation, generate 8 cases from the three bits of the testing mode
     * int value:
     *
     *  (bit 1) if true, the backing map is reversed
     *  (bit 2) if true, the view is reversed
     *  (bit 4) if true, the last element of the view is to be removed, otherwise the first
     *
     * The three bits XORed together (by refLast(), above) indicate (if true) the last
     * or (if false) the first element of the reference entry list is to be removed.
     *
     * @return the generated cases
     */
    @DataProvider(name="viewRemoves")
    public Iterator<Object[]> viewRemoves() {
        var cases = new ArrayList<Object[]>();
        for (int mode = 0; mode < 8; mode++) {
            cases.addAll(Arrays.asList(
                new Object[] { "LinkedHashMap", mode, load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
                new Object[] { "SimpleSortedMap", mode, load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
                new Object[] { "TreeMap", mode, load(new TreeMap<>(), ORIGINAL), ORIGINAL }
            ));
        }
        return cases.iterator();
    }

    @DataProvider(name="emptyViewRemoves")
    public Iterator<Object[]> emptyViewRemoves() {
        var cases = new ArrayList<Object[]>();
        for (int mode = 0; mode < 8; mode++) {
            cases.addAll(Arrays.asList(
                new Object[] { "LinkedHashMap", mode, new LinkedHashMap<>(), List.of() },
                new Object[] { "SimpleSortedMap", mode, new SimpleSortedMap<>(), List.of() },
                new Object[] { "TreeMap", mode, new TreeMap<>(), List.of() }
            ));
        }
        return cases.iterator();
    }

    @DataProvider(name="viewAddThrows")
    public Iterator<Object[]> viewAddThrows() {
        var cases = new ArrayList<Object[]>();
        for (int mode = 0; mode < 8; mode++) {
            cases.addAll(Arrays.asList(
                new Object[] { "LinkedHashMap", mode, load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
                new Object[] { "SimpleSortedMap", mode, load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
                new Object[] { "TreeMap", mode, load(new TreeMap<>(), ORIGINAL), ORIGINAL }
            ));
        }
        return cases.iterator();
    }

    @DataProvider(name="nullableEntries")
    public Iterator<Object[]> nullableEntries() {
        return Arrays.asList(
            new Object[] { "firstEntry" },
            new Object[] { "lastEntry" },
            new Object[] { "pollFirstEntry" },
            new Object[] { "pollLastEntry" }
        ).iterator();
    }

    // ========== Assertions ==========

    /**
     * Basic checks over the contents of a SequencedMap, compared to a reference List of entries,
     * in one direction.
     *
     * @param map the SequencedMap under test
     * @param ref the reference list of entries
     */
    public void checkContents1(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        var list1 = new ArrayList<Map.Entry<String, Integer>>();
        map.forEach((k, v) -> list1.add(Map.entry(k, v)));
        assertEquals(list1, ref);

        assertEquals(map.size(), ref.size());
        assertEquals(map.isEmpty(), ref.isEmpty());

        for (var e : ref) {
            assertTrue(map.containsKey(e.getKey()));
            assertTrue(map.containsValue(e.getValue()));
            assertEquals(map.get(e.getKey()), e.getValue());
        }
    }

    public void checkContents(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkContents1(map, ref);

        var rref = new ArrayList<>(ref);
        Collections.reverse(rref);
        var rmap = map.reversed();
        checkContents1(rmap, rref);

        var rrmap = rmap.reversed();
        checkContents1(rrmap, ref);
    }

    /**
     * Check the entrySet, keySet, or values view of a SequencedMap in one direction. The view
     * collection is ordered even though the collection type is not sequenced.
     *
     * @param <T> the element type of the view
     * @param mapView the actual map view
     * @param expElements list of the expected elements
     */
    public <T> void checkView1(Collection<T> mapView, List<T> expElements) {
        var list1 = new ArrayList<T>();
        for (var k : mapView)
            list1.add(k);
        assertEquals(list1, expElements);

        var list2 = new ArrayList<T>();
        mapView.forEach(list2::add);
        assertEquals(list2, expElements);

        var list3 = Arrays.asList(mapView.toArray());
        assertEquals(list3, expElements);

        var list4 = Arrays.asList(mapView.toArray(new Object[0]));
        assertEquals(list4, expElements);

        var list5 = Arrays.asList(mapView.toArray(Object[]::new));
        assertEquals(list5, expElements);

        var list6 = mapView.stream().toList();
        assertEquals(list6, expElements);

        var list7 = mapView.parallelStream().toList();
        assertEquals(list7, expElements);

        assertEquals(mapView.size(), expElements.size());
        assertEquals(mapView.isEmpty(), expElements.isEmpty());

        for (var k : expElements) {
            assertTrue(mapView.contains(k));
        }

        var it = mapView.iterator();
        if (expElements.isEmpty()) {
            assertFalse(it.hasNext());
        } else {
            assertTrue(it.hasNext());
            assertEquals(it.next(), expElements.get(0));
        }
    }

    /**
     * Check the sequenced entrySet, keySet, or values view of a SequencedMap in one direction.
     *
     * @param <T> the element type of the view
     * @param mapView the actual map view
     * @param expElements list of the expected elements
     */
    public <T> void checkSeqView1(SequencedCollection<T> mapView, List<T> expElements) {
        checkView1(mapView, expElements);

        if (expElements.isEmpty()) {
            assertThrows(NoSuchElementException.class, () -> mapView.getFirst());
            assertThrows(NoSuchElementException.class, () -> mapView.getLast());
        } else {
            assertEquals(mapView.getFirst(), expElements.get(0));
            assertEquals(mapView.getLast(), expElements.get(expElements.size() - 1));
        }
    }

    /**
     * Check the keySet and sequencedKeySet views of a map. It's possible to unify this with
     * the corresponding checks for values and entrySet views, but doing this adds a bunch
     * of generics and method references that tend to obscure more than they help.
     *
     * @param map the SequencedMap under test
     * @param refEntries expected contents of the map
     */
    public void checkKeySet(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> refEntries) {
        List<String> refKeys = refEntries.stream().map(Map.Entry::getKey).toList();
        List<String> rrefKeys = new ArrayList<>(refKeys);
        Collections.reverse(rrefKeys);
        SequencedMap<String, Integer> rmap = map.reversed();

        checkView1(map.keySet(), refKeys);
        checkSeqView1(map.sequencedKeySet(), refKeys);
        checkSeqView1(map.sequencedKeySet().reversed(), rrefKeys);

        checkView1(rmap.keySet(), rrefKeys);
        checkSeqView1(rmap.sequencedKeySet(), rrefKeys);
        checkSeqView1(rmap.sequencedKeySet().reversed(), refKeys);

        checkView1(rmap.reversed().keySet(), refKeys);
        checkSeqView1(rmap.reversed().sequencedKeySet(), refKeys);
        checkSeqView1(rmap.reversed().sequencedKeySet().reversed(), rrefKeys);

        assertEquals(map.keySet().hashCode(), rmap.keySet().hashCode());
        assertEquals(map.keySet().hashCode(), map.sequencedKeySet().hashCode());
        assertEquals(rmap.keySet().hashCode(), rmap.sequencedKeySet().hashCode());

        // Don't use assertEquals(), as we really want to test the equals() methods.
        assertTrue(map.keySet().equals(map.sequencedKeySet()));
        assertTrue(map.sequencedKeySet().equals(map.keySet()));
        assertTrue(rmap.keySet().equals(map.sequencedKeySet()));
        assertTrue(rmap.sequencedKeySet().equals(map.keySet()));
        assertTrue(map.keySet().equals(rmap.sequencedKeySet()));
        assertTrue(map.sequencedKeySet().equals(rmap.keySet()));
        assertTrue(rmap.keySet().equals(rmap.sequencedKeySet()));
        assertTrue(rmap.sequencedKeySet().equals(rmap.keySet()));
    }

    /**
     * Check the values and sequencedValues views of a map.
     *
     * @param map the SequencedMap under test
     * @param refEntries expected contents of the map
     */
    public void checkValues(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> refEntries) {
        List<Integer> refValues = refEntries.stream().map(Map.Entry::getValue).toList();
        List<Integer> rrefValues = new ArrayList<>(refValues);
        Collections.reverse(rrefValues);
        SequencedMap<String, Integer> rmap = map.reversed();

        checkView1(map.values(), refValues);
        checkSeqView1(map.sequencedValues(), refValues);
        checkSeqView1(map.sequencedValues().reversed(), rrefValues);

        checkView1(rmap.values(), rrefValues);
        checkSeqView1(rmap.sequencedValues(), rrefValues);
        checkSeqView1(rmap.sequencedValues().reversed(), refValues);

        checkView1(rmap.reversed().values(), refValues);
        checkSeqView1(rmap.reversed().sequencedValues(), refValues);
        checkSeqView1(rmap.reversed().sequencedValues().reversed(), rrefValues);

        // No assertions over hashCode(), as Collection inherits Object.hashCode
        // which is usually but not guaranteed to give unequal results.

        // It's permissible for an implementation to return the same instance for values()
        // as for sequencedValues(). Either they're the same instance, or they must be
        // unequal, because distinct collections should always be unequal.

        var v = map.values();
        var sv = map.sequencedValues();
        assertTrue((v == sv) || ! (v.equals(sv) || sv.equals(v)));

        var rv = rmap.values();
        var rsv = rmap.sequencedValues();
        assertTrue((rv == rsv) || ! (rv.equals(rsv) || rsv.equals(rv)));
    }

    /**
     * Check the entrySet and sequencedEntrySet views of a map.
     *
     * @param map the SequencedMap under test
     * @param refEntries expected contents of the map
     */
    public void checkEntrySet(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> refEntries) {
        List<Map.Entry<String, Integer>> rref = new ArrayList<>(refEntries);
        Collections.reverse(rref);
        SequencedMap<String, Integer> rmap = map.reversed();

        checkView1(map.entrySet(), refEntries);
        checkSeqView1(map.sequencedEntrySet(), refEntries);
        checkSeqView1(map.sequencedEntrySet().reversed(), rref);

        checkView1(rmap.entrySet(), rref);
        checkSeqView1(rmap.sequencedEntrySet(), rref);
        checkSeqView1(rmap.sequencedEntrySet().reversed(), refEntries);

        checkView1(rmap.reversed().entrySet(), refEntries);
        checkSeqView1(rmap.reversed().sequencedEntrySet(), refEntries);
        checkSeqView1(rmap.reversed().sequencedEntrySet().reversed(), rref);

        assertEquals(map.entrySet().hashCode(), rmap.entrySet().hashCode());
        assertEquals(map.entrySet().hashCode(), map.sequencedEntrySet().hashCode());
        assertEquals(map.sequencedEntrySet().hashCode(), map.entrySet().hashCode());

        assertTrue(map.entrySet().equals(map.sequencedEntrySet()));
        assertTrue(map.sequencedEntrySet().equals(map.entrySet()));
        assertTrue(rmap.entrySet().equals(map.sequencedEntrySet()));
        assertTrue(rmap.sequencedEntrySet().equals(map.entrySet()));
        assertTrue(map.entrySet().equals(rmap.sequencedEntrySet()));
        assertTrue(map.sequencedEntrySet().equals(rmap.entrySet()));
        assertTrue(rmap.entrySet().equals(rmap.sequencedEntrySet()));
        assertTrue(rmap.sequencedEntrySet().equals(rmap.entrySet()));
    }

    /**
     * Test attempted modifications to unmodifiable map views. The only mutating operation
     * map views can support is removal.
     *
     * @param <T> element type of the map view
     * @param view the map view
     */
    public <T> void checkUnmodifiableView(Collection<T> view) {
        assertThrows(UOE, () -> view.clear());
        assertThrows(UOE, () -> { var it = view.iterator(); it.next(); it.remove(); });
        assertThrows(UOE, () -> { var t = view.iterator().next(); view.remove(t); });

// TODO these ops should throw unconditionally, but they don't in some implementations
     // assertThrows(UOE, () -> view.removeAll(List.of()));
     // assertThrows(UOE, () -> view.removeIf(x -> false));
     // assertThrows(UOE, () -> view.retainAll(view));
        assertThrows(UOE, () -> view.removeAll(view));
        assertThrows(UOE, () -> view.removeIf(x -> true));
        assertThrows(UOE, () -> view.retainAll(List.of()));
    }

    /**
     * Test removal methods on unmodifiable sequenced map views.
     *
     * @param <T> element type of the map view
     * @param view the map view
     */
    public <T> void checkUnmodifiableSeqView(SequencedCollection<T> view) {
        checkUnmodifiableView(view);
        assertThrows(UOE, () -> view.removeFirst());
        assertThrows(UOE, () -> view.removeLast());

        var rview = view.reversed();
        checkUnmodifiableView(rview);
        assertThrows(UOE, () -> rview.removeFirst());
        assertThrows(UOE, () -> rview.removeLast());
    }

    public void checkUnmodifiableEntry(SequencedMap<String, Integer> map) {
        assertThrows(UOE, () -> { map.firstEntry().setValue(99); });
        assertThrows(UOE, () -> { map.lastEntry().setValue(99); });
        assertThrows(UOE, () -> { map.sequencedEntrySet().getFirst().setValue(99); });
        assertThrows(UOE, () -> { map.sequencedEntrySet().getLast().setValue(99); });
        assertThrows(UOE, () -> { map.sequencedEntrySet().reversed().getFirst().setValue(99); });
        assertThrows(UOE, () -> { map.sequencedEntrySet().reversed().getLast().setValue(99); });
    }

    public void checkUnmodifiable1(SequencedMap<String, Integer> map) {
        assertThrows(UOE, () -> map.putFirst("x", 99));
        assertThrows(UOE, () -> map.putLast("x", 99));
        assertThrows(UOE, () -> { map.pollFirstEntry(); });
        assertThrows(UOE, () -> { map.pollLastEntry(); });

        checkUnmodifiableEntry(map);
        checkUnmodifiableView(map.keySet());
        checkUnmodifiableView(map.values());
        checkUnmodifiableView(map.entrySet());
        checkUnmodifiableSeqView(map.sequencedKeySet());
        checkUnmodifiableSeqView(map.sequencedValues());
        checkUnmodifiableSeqView(map.sequencedEntrySet());
    }

    public void checkUnmodifiable(SequencedMap<String, Integer> map) {
        checkUnmodifiable1(map);
        checkUnmodifiable1(map.reversed());
    }

    // The putFirst/putLast operations aren't tested here, because the only instances of
    // checked, sequenced maps are SortedMap and NavigableMap, which don't support them.
    public void checkChecked(SequencedMap<String, Integer> map) {
        SequencedMap<Object, Object> objMap = (SequencedMap<Object, Object>)(SequencedMap)map;
        assertThrows(CCE, () -> { objMap.put(new Object(), 99); });
        assertThrows(CCE, () -> { objMap.put("x", new Object()); });
        assertThrows(CCE, () -> { objMap.sequencedEntrySet().iterator().next().setValue(new Object()); });
        assertThrows(CCE, () -> { objMap.sequencedEntrySet().reversed().iterator().next().setValue(new Object()); });
        assertThrows(CCE, () -> { objMap.reversed().put(new Object(), 99); });
        assertThrows(CCE, () -> { objMap.reversed().put("x", new Object()); });
        assertThrows(CCE, () -> { objMap.reversed().sequencedEntrySet().iterator().next().setValue(new Object()); });
        assertThrows(CCE, () -> { objMap.reversed().sequencedEntrySet().reversed().iterator().next().setValue(new Object()); });
    }

    public void checkEntry(Map.Entry<String, Integer> entry, String key, Integer value) {
        assertEquals(entry.getKey(), key);
        assertEquals(entry.getValue(), value);
    }

    // ========== Tests ==========

    @Test(dataProvider="all")
    public void testFundamentals(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkContents(map, ref);
        checkEntrySet(map, ref);
        checkKeySet(map, ref);
        checkValues(map, ref);
    }

    @Test(dataProvider="populated")
    public void testFirstEntry(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertEquals(map.firstEntry(), ref.get(0));
        assertEquals(map.reversed().firstEntry(), ref.get(ref.size() - 1));
        assertThrows(UOE, () -> { map.firstEntry().setValue(99); });
        assertThrows(UOE, () -> { map.reversed().firstEntry().setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="populated")
    public void testLastEntry(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertEquals(map.lastEntry(), ref.get(ref.size() - 1));
        assertEquals(map.reversed().lastEntry(), ref.get(0));
        assertThrows(UOE, () -> { map.lastEntry().setValue(99); });
        assertThrows(UOE, () -> { map.reversed().lastEntry().setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="empties")
    public void testEmptyFirstEntry(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertNull(map.firstEntry());
        assertNull(map.reversed().firstEntry());
        checkContents(map, ref);
    }

    @Test(dataProvider="empties")
    public void testEmptyLastEntry(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertNull(map.lastEntry());
        assertNull(map.reversed().lastEntry());
        checkContents(map, ref);
    }

    @Test(dataProvider="puts")
    public void testPutFirst(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(0, Map.entry("x", 99));
        map.putFirst("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="puts")
    public void testPutFirstRev(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        map.reversed().putFirst("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="puts")
    public void testPutLast(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        map.putLast("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="puts")
    public void testPutLastRev(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(0, Map.entry("x", 99));
        map.reversed().putLast("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposPut(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).put("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposPutAll(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).putAll(Map.of("x", 99));
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposPutIfAbsent(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).putIfAbsent("x", 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposCompute(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).compute("x", (k, v) -> 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposComputeIfAbsent(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).computeIfAbsent("x", k -> 99);
        checkContents(map, ref);
    }

    @Test(dataProvider="putUnpositioned")
    public void testUnposMerge(String label, boolean rev, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        ref.add(Map.entry("x", 99));
        (rev ? map.reversed() : map).merge("x", 99, /*unused*/ (k, v) -> -1);
        checkContents(map, ref);
    }

    @Test(dataProvider="putThrows")
    public void testPutThrows(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        assertThrows(UOE, () -> map.putFirst("x", 99));
        assertThrows(UOE, () -> map.putLast("x", 99));
        assertThrows(UOE, () -> map.reversed().putFirst("x", 99));
        assertThrows(UOE, () -> map.reversed().putLast("x", 99));
        checkContents(map, baseref);
    }

    @Test(dataProvider="polls")
    public void testPollFirst(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var act = map.pollFirstEntry();
        assertEquals(act, ref.remove(0));
        assertThrows(UOE, () -> { act.setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="polls")
    public void testPollFirstRev(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var act = map.reversed().pollFirstEntry();
        assertEquals(act, ref.remove(ref.size() - 1));
        assertThrows(UOE, () -> { act.setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="polls")
    public void testPollLast(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var act = map.pollLastEntry();
        assertEquals(act, ref.remove(ref.size() - 1));
        assertThrows(UOE, () -> { act.setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="polls")
    public void testPollLastRev(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var act = map.reversed().pollLastEntry();
        assertEquals(act, ref.remove(0));
        assertThrows(UOE, () -> { act.setValue(99); });
        checkContents(map, ref);
    }

    @Test(dataProvider="emptyPolls")
    public void testEmptyPollFirst(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertNull(map.pollFirstEntry());
        assertNull(map.reversed().pollFirstEntry());
        checkContents(map, ref);
    }

    @Test(dataProvider="emptyPolls")
    public void testEmptyPollLast(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        assertNull(map.pollLastEntry());
        assertNull(map.reversed().pollLastEntry());
        checkContents(map, ref);
    }

    @Test(dataProvider="serializable")
    public void testSerializable(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref)
        throws ClassNotFoundException, IOException
    {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(map);
        }

        try (var bais = new ByteArrayInputStream(baos.toByteArray());
             var ois = new ObjectInputStream(bais)) {
            var map2 = (SequencedMap<String, Integer>) ois.readObject();
            checkContents(map2, ref);
        }
    }

    @Test(dataProvider="notSerializable")
    public void testNotSerializable(String label, SequencedMap<String, Integer> map)
        throws ClassNotFoundException, IOException
    {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            assertThrows(ObjectStreamException.class, () -> oos.writeObject(map));
        }
    }

    @Test(dataProvider="doubleReverse")
    public void testDoubleReverse(String label, SequencedMap<String, Integer> map) {
        var rrmap = map.reversed().reversed();
        assertSame(rrmap, map);
    }

    @Test(dataProvider="unmodifiable")
    public void testUnmodifiable(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkUnmodifiable(map);
        checkContents(map, ref);
    }

    @Test(dataProvider="checked")
    public void testChecked(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkChecked(map);
        checkContents(map, ref);
    }

    /**
     * Test that a removal from the sequenedKeySet view is properly reflected in the original
     * backing map. The mode value indicates whether the backing map is reversed, whether the
     * sequencedKeySet view is reversed, and whether the removeFirst or removeLast is called
     * on the view. See the viewRemoves() dataProvider for details.
     *
     * @param label the implementation label
     * @param mode reversed and first/last modes
     * @param map the original map instance
     * @param baseref reference contents of the original map
     */
    @Test(dataProvider="viewRemoves")
    public void testKeySetRemoves(String label,
                                  int mode,
                                  SequencedMap<String, Integer> map,
                                  List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = (refLast(mode) ? ref.remove(ref.size() - 1) : ref.remove(0)).getKey();
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var keySet = reverseView(mode) ? tempmap.sequencedKeySet().reversed() : tempmap.sequencedKeySet();
        var act = callLast(mode) ? keySet.removeLast() : keySet.removeFirst();
        assertEquals(act, exp);
        checkContents(map, ref);
    }

    // As above, but for the sequencedValues view.
    @Test(dataProvider="viewRemoves")
    public void testValuesRemoves(String label,
                                  int mode,
                                  SequencedMap<String, Integer> map,
                                  List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = (refLast(mode) ? ref.remove(ref.size() - 1) : ref.remove(0)).getValue();
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var values = reverseView(mode) ? tempmap.sequencedValues().reversed() : tempmap.sequencedValues();
        var act = callLast(mode) ? values.removeLast() : values.removeFirst();
        assertEquals(act, exp);
        checkContents(map, ref);
    }

    // As above, but for the sequencedEntrySet view.
    @Test(dataProvider="viewRemoves")
    public void testEntrySetRemoves(String label,
                                    int mode,
                                    SequencedMap<String, Integer> map,
                                    List<Map.Entry<String, Integer>> baseref) {
        var ref = new ArrayList<>(baseref);
        var exp = refLast(mode) ? ref.remove(ref.size() - 1) : ref.remove(0);
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var entrySet = reverseView(mode) ? tempmap.sequencedEntrySet().reversed() : tempmap.sequencedEntrySet();
        var act = callLast(mode) ? entrySet.removeLast() : entrySet.removeFirst();
        assertEquals(act, exp);
        checkContents(map, ref);
    }

    // As above, but for the sequencedKeySet of an empty map.
    @Test(dataProvider="emptyViewRemoves")
    public void testEmptyKeySetRemoves(String label,
                                       int mode,
                                       SequencedMap<String, Integer> map,
                                       List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var keySet = reverseView(mode) ? tempmap.sequencedKeySet().reversed() : tempmap.sequencedKeySet();
        if (callLast(mode))
            assertThrows(NSEE, () -> keySet.removeLast());
        else
            assertThrows(NSEE, () -> keySet.removeFirst());
        checkContents(map, baseref);

    }

    // As above, but for the sequencedValues view.
    @Test(dataProvider="emptyViewRemoves")
    public void testEmptyValuesRemoves(String label,
                                       int mode,
                                       SequencedMap<String, Integer> map,
                                       List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var values = reverseView(mode) ? tempmap.sequencedValues().reversed() : tempmap.sequencedValues();
        if (callLast(mode))
            assertThrows(NSEE, () -> values.removeLast());
        else
            assertThrows(NSEE, () -> values.removeFirst());
        checkContents(map, baseref);
    }

    // As above, but for the sequencedEntrySet view.
    @Test(dataProvider="emptyViewRemoves")
    public void testEmptyEntrySetRemoves(String label,
                                         int mode,
                                         SequencedMap<String, Integer> map,
                                         List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var entrySet = reverseView(mode) ? tempmap.sequencedEntrySet().reversed() : tempmap.sequencedEntrySet();
        if (callLast(mode))
            assertThrows(NSEE, () -> entrySet.removeLast());
        else
            assertThrows(NSEE, () -> entrySet.removeFirst());
        checkContents(map, baseref);
    }

    // Test that addFirst/addLast on the sequencedKeySetView throw UnsupportedOperationException.
    @Test(dataProvider="viewAddThrows")
    public void testKeySetAddThrows(String label,
                                    int mode,
                                    SequencedMap<String, Integer> map,
                                    List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var keySet = reverseView(mode) ? tempmap.sequencedKeySet().reversed() : tempmap.sequencedKeySet();
        if (callLast(mode))
            assertThrows(UOE, () -> keySet.addLast("x"));
        else
            assertThrows(UOE, () -> keySet.addFirst("x"));
        checkContents(map, baseref);
    }

    // As above, but for the sequencedValues view.
    @Test(dataProvider="viewAddThrows")
    public void testValuesAddThrows(String label,
                                    int mode,
                                    SequencedMap<String, Integer> map,
                                    List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var values = reverseView(mode) ? tempmap.sequencedValues().reversed() : tempmap.sequencedValues();
        if (callLast(mode))
            assertThrows(UOE, () -> values.addLast(99));
        else
            assertThrows(UOE, () -> values.addFirst(99));
        checkContents(map, baseref);
    }

    // As above, but for the sequencedEntrySet view.
    @Test(dataProvider="viewAddThrows")
    public void testEntrySetAddThrows(String label,
                                      int mode,
                                      SequencedMap<String, Integer> map,
                                      List<Map.Entry<String, Integer>> baseref) {
        var tempmap = reverseMap(mode) ? map.reversed() : map;
        var entrySet = reverseView(mode) ? tempmap.sequencedEntrySet().reversed() : tempmap.sequencedEntrySet();
        if (callLast(mode))
            assertThrows(UOE, () -> entrySet.addLast(Map.entry("x", 99)));
        else
            assertThrows(UOE, () -> entrySet.addFirst(Map.entry("x", 99)));
        checkContents(map, baseref);
    }

    @Test(dataProvider="nullableEntries")
    public void testNullableKeyValue(String mode) {
        // TODO this relies on LHM to inherit SequencedMap default
        // methods which are actually being tested here.
        SequencedMap<String, Integer> map = new LinkedHashMap<>();
        map.put(null, 1);
        map.put("two", null);

        switch (mode) {
            case "firstEntry"     -> checkEntry(map.firstEntry(), null, 1);
            case "lastEntry"      -> checkEntry(map.lastEntry(), "two", null);
            case "pollFirstEntry" -> checkEntry(map.pollFirstEntry(), null, 1);
            case "pollLastEntry"  -> checkEntry(map.pollLastEntry(), "two", null);
            default               -> throw new AssertionError("illegal mode " + mode);
        }
    }
}
