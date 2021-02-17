/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6323374
 * @run testng WrappedUnmodifiableCollections
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


@Test
public class WrappedUnmodifiableCollections {

    static final Class<?> UNMODIFIABLESET;
    static final Class<?> UNMODIFIABLESORTEDSET;
    static final Class<?> UNMODIFIABLENAVIGABLESET;
    static final Class<?> UNMODIFIABLELIST;
    static final Class<?> UNMODIFIABLERANDOMACCESSLIST;
    static final Class<?> UNMODIFIABLEMAP;
    static final Class<?> UNMODIFIABLESORTEDMAP;
    static final Class<?> UNMODIFIABLENAVIGABLEMAP;
    static final Class<?> UNMODIFIABLECOLLECTION;

    static {
        try {
            UNMODIFIABLESET = Class.forName("java.util.Collections$UnmodifiableSet");
            UNMODIFIABLESORTEDSET = Class.forName("java.util.Collections$UnmodifiableSortedSet");
            UNMODIFIABLENAVIGABLESET = Class.forName("java.util.Collections$UnmodifiableNavigableSet");

            UNMODIFIABLELIST = Class.forName("java.util.Collections$UnmodifiableList");
            UNMODIFIABLERANDOMACCESSLIST = Class.forName("java.util.Collections$UnmodifiableRandomAccessList");

            UNMODIFIABLEMAP = Class.forName("java.util.Collections$UnmodifiableMap");
            UNMODIFIABLESORTEDMAP = Class.forName("java.util.Collections$UnmodifiableSortedMap");
            UNMODIFIABLENAVIGABLEMAP = Class.forName("java.util.Collections$UnmodifiableNavigableMap");

            UNMODIFIABLECOLLECTION= Class.forName("java.util.Collections$UnmodifiableCollection");


        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class Not found: " + e.getMessage());
        }
    }


    public void testUnmodifiableListsDontWrap() {
        List<Integer> list = List.of(1,2,3);
        assertNotSame(list.getClass(), UNMODIFIABLERANDOMACCESSLIST);
        List<Integer> result = Collections.unmodifiableList(list);
        assertSame(result.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        //Empty List
        List<?> list2 = List.of();
        assertNotSame(list2.getClass(), UNMODIFIABLERANDOMACCESSLIST);
        List<?> result2 = Collections.unmodifiableList(list2);
        assertSame(result2.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        //ImmutableCollections.List12
        List<?> list12 = List.of(1);
        assertNotSame(list12.getClass(), UNMODIFIABLERANDOMACCESSLIST);
        List<?> result3 = Collections.unmodifiableList(list12);
        assertSame(result3.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        //ImmutableCollections.ListN
        List<?> listN = List.of(1,2,3,4,5,6);
        assertNotSame(listN.getClass(), UNMODIFIABLERANDOMACCESSLIST);
        List<?> result4 = Collections.unmodifiableList(listN);
        assertSame(result4.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        //ImmutableCollections.Sublist
        List<?> subList = list.subList(0,1);
        assertNotSame(subList.getClass(), UNMODIFIABLERANDOMACCESSLIST);
        List<?> subListResult = Collections.unmodifiableList(subList);
        assertSame(subListResult.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        //Collections.UnmodifiableList
        List<Integer> linkedList = new LinkedList<>();
        linkedList.add(1);
        linkedList.add(2);
        linkedList.add(3);
        List<?> resultLinkedList = Collections.unmodifiableList(linkedList);
        assertNotSame(linkedList, resultLinkedList);
        assertSame(resultLinkedList.getClass(), UNMODIFIABLELIST);

        List<?> rewrappedLinkedListAttempt = Collections.unmodifiableList(resultLinkedList);
        assertSame(resultLinkedList, rewrappedLinkedListAttempt);

        //Collections.UnmodifiableRandomAccessList
        List<Integer> arrayList = new ArrayList<>();
        linkedList.add(1);
        linkedList.add(2);
        linkedList.add(3);

        List<?> resultArrayList = Collections.unmodifiableList(arrayList);
        assertNotSame(arrayList, resultArrayList);
        assertSame(resultArrayList.getClass(), UNMODIFIABLERANDOMACCESSLIST);

        List<?> rewrappedLinkedListAttempt2 = Collections.unmodifiableList(resultLinkedList);
        assertSame(resultLinkedList, rewrappedLinkedListAttempt2);

    }


    public void testUnmodifiableCollectionsDontWrap() {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        Collection<Integer> unmodifiableCollection = Collections.unmodifiableCollection(list);

        assertSame(unmodifiableCollection.getClass(), UNMODIFIABLECOLLECTION);
        Collection<?> unmodifiableCollection2 = Collections.unmodifiableCollection(unmodifiableCollection);

        assertSame(UNMODIFIABLECOLLECTION, unmodifiableCollection2.getClass());
        assertSame(unmodifiableCollection2, unmodifiableCollection);

    }

    public void testUnmodifiableSetsDontWrap() {

        TreeSet<Integer> treeSet = new TreeSet<>();

        //Collections.UnmodifiableSet
        Set<Integer> unmodifiableSet = Collections.unmodifiableSet(treeSet);
        assertSame(unmodifiableSet.getClass(), UNMODIFIABLESET);

        Set<Integer> rewrappedUnmodifiableSet = Collections.unmodifiableSet(unmodifiableSet);
        assertSame(rewrappedUnmodifiableSet.getClass(), UNMODIFIABLESET);
        assertSame(rewrappedUnmodifiableSet, unmodifiableSet);

        //Collections.UnmodifiableSortedSet
        SortedSet<Integer> unmodifiableSortedSet = Collections.unmodifiableSortedSet(treeSet);
        assertSame(unmodifiableSortedSet.getClass(), UNMODIFIABLESORTEDSET);


        SortedSet<Integer> reWrappedUmodifiableSortedSet = Collections.unmodifiableSortedSet(unmodifiableSortedSet);
        assertSame(reWrappedUmodifiableSortedSet.getClass(), UNMODIFIABLESORTEDSET);
        assertSame(reWrappedUmodifiableSortedSet, unmodifiableSortedSet);

        //Collections.UnmodifiableNavigableSet
        NavigableSet<Integer> unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(treeSet);
        assertSame(unmodifiableNavigableSet.getClass(), UNMODIFIABLENAVIGABLESET);

        NavigableSet<Integer> reWrappedUnmodifiableNavigableSet =
                Collections.unmodifiableNavigableSet(unmodifiableNavigableSet);
        assertSame(reWrappedUnmodifiableNavigableSet.getClass(), UNMODIFIABLENAVIGABLESET);
        assertSame(reWrappedUnmodifiableNavigableSet, unmodifiableNavigableSet);

        //SET12
        Set<Integer> set12 = Set.of(1,2);
        assertNotSame(set12.getClass(), UNMODIFIABLESET);
        Set<Integer> reWrappedSet12 = Collections.unmodifiableSet(set12);
        assertSame(reWrappedSet12.getClass(), UNMODIFIABLESET);

        //SETN
        Set<Integer> setN = Set.of(1,2,3,4,5,6);
        assertNotSame(setN.getClass(), UNMODIFIABLESET);
        Set<Integer> reWrappedSetN = Collections.unmodifiableSet(setN);
        assertSame(reWrappedSetN.getClass(), UNMODIFIABLESET);


    }

    public void testUnmodifiableMapsDontWrap() {
        TreeMap<Integer,Integer> treeMap = new TreeMap<>();
        treeMap.put(1,1);
        treeMap.put(2,2);
        treeMap.put(3,3);

        //Collections.UnModifiableMap
        Map<Integer,Integer> unmodifiableMap = Collections.unmodifiableMap(treeMap);
        assertSame(unmodifiableMap.getClass(), UNMODIFIABLEMAP);
        assertNotSame(unmodifiableMap, treeMap);

        Map<Integer,Integer> reWrappedUnmodifiableMap = Collections.unmodifiableMap(unmodifiableMap);
        assertSame(reWrappedUnmodifiableMap, unmodifiableMap);

        //Collections.UnModifiableSortedMap
        SortedMap<Integer,Integer> unmodifiableSortedMap = Collections.unmodifiableSortedMap(treeMap);
        assertSame(unmodifiableSortedMap.getClass(), UNMODIFIABLESORTEDMAP);
        assertNotSame(unmodifiableSortedMap, treeMap);

        Map<Integer,Integer> reWrappedUnmodifiableSortedMap = Collections.unmodifiableSortedMap(unmodifiableSortedMap);
        assertSame(reWrappedUnmodifiableSortedMap, unmodifiableSortedMap);

        //Collections.UnModifiableNavigableMap
        NavigableMap<Integer,Integer> unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(treeMap);
        assertSame(unmodifiableNavigableMap.getClass(), UNMODIFIABLENAVIGABLEMAP);

        NavigableMap<Integer,Integer> reWrappedUnmodifiableNavigableMap =
                Collections.unmodifiableNavigableMap(unmodifiableNavigableMap);
        assertSame(unmodifiableNavigableMap, reWrappedUnmodifiableNavigableMap);

        //ImmutableCollections.Map1
        Map<Integer,Integer> map1 = Map.of(1,1);
        assertNotSame(map1.getClass(), UNMODIFIABLEMAP);

        Map<Integer,Integer> reWrappedMap1 = Collections.unmodifiableMap(map1);
        assertSame(reWrappedMap1.getClass(), UNMODIFIABLEMAP);

        //ImmutableCollections.MapN
        Map<Integer,Integer> mapN = Map.of(1,1, 2, 2, 3, 3, 4, 4);
        assertNotSame(mapN.getClass(), UNMODIFIABLEMAP);

        Map<Integer,Integer> reWrappedMapN = Collections.unmodifiableMap(mapN);
        assertSame(reWrappedMapN.getClass(), UNMODIFIABLEMAP);




    }

}
