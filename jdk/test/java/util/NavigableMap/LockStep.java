/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6420753 6242436 6691185
 * @summary Compare NavigableMap implementations for identical behavior
 * @run main LockStep
 * @run main/othervm -XX:+AggressiveOpts LockStep
 * @run main/othervm -XX:+AggressiveOpts -Dthorough=true LockStep
 * @author  Martin Buchholz
 * @key randomness
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.Collections.*;

@SuppressWarnings("unchecked")
public class LockStep {
    static final int DEFAULT_SIZE = 5;
    static int size;            // Running time is O(size**2)

    static int intArg(String[] args, int i, int defaultValue) {
        return args.length > i ? Integer.parseInt(args[i]) : defaultValue;
    }

    // Pass -Dthorough=true for more exhaustive testing
    static final boolean thorough = Boolean.getBoolean("thorough");

    static boolean maybe(int n) { return rnd.nextInt(n) == 0; }

    static void realMain(String[] args) {
        size = intArg(args, 0, DEFAULT_SIZE);

        lockSteps(new TreeMap(),
                  new ConcurrentSkipListMap());
        lockSteps(new TreeMap(),
                  Collections.checkedNavigableMap(new TreeMap(), Integer.class, Integer.class));
        lockSteps(new TreeMap(),
                  Collections.synchronizedNavigableMap(new TreeMap()));
        lockSteps(new TreeMap(reverseOrder()),
                  new ConcurrentSkipListMap(reverseOrder()));

        lockSteps(new TreeSet(),
                  new ConcurrentSkipListSet());
        lockSteps(new TreeSet(),
                  Collections.checkedNavigableSet(new TreeSet(), Integer.class));
        lockSteps(new TreeSet(),
                  Collections.synchronizedNavigableSet(new TreeSet()));
        lockSteps(new TreeSet(reverseOrder()),
                  new ConcurrentSkipListSet(reverseOrder()));
    }

    static void lockSteps(NavigableMap m1, NavigableMap m2) {
        if (maybe(4)) m1 = serialClone(m1);
        if (maybe(4)) m2 = serialClone(m2);
        lockStep(m1,
                 m2);
        lockStep(m1.descendingMap(),
                 m2.descendingMap());
        lockStep(fullSubMap(m1),
                 fullSubMap(m2));
        lockStep(fullSubMap(m1.descendingMap()),
                 fullSubMap(m2.descendingMap()));
        lockStep(fullHeadMap(m1),
                 fullHeadMap(m2));
        lockStep(fullHeadMap(m1.descendingMap()),
                 fullHeadMap(m2.descendingMap()));
        lockStep(fullTailMap(m1),
                 fullTailMap(m2));
        lockStep(fullTailMap(m1.descendingMap()),
                 fullTailMap(m2.descendingMap()));
    }

    static void lockSteps(NavigableSet s1, NavigableSet s2) {
        if (maybe(4)) s1 = serialClone(s1);
        if (maybe(4)) s2 = serialClone(s2);
        lockStep(s1,
                 s2);
        lockStep(s1.descendingSet(),
                 s2.descendingSet());
        lockStep(fullSubSet(s1),
                 fullSubSet(s2));
        lockStep(fullSubSet(s1.descendingSet()),
                 fullSubSet(s2.descendingSet()));
        lockStep(fullHeadSet(s1),
                 fullHeadSet(s2));
        lockStep(fullHeadSet(s1.descendingSet()),
                 fullHeadSet(s2.descendingSet()));
        lockStep(fullTailSet(s1),
                 fullTailSet(s2));
        lockStep(fullTailSet(s1.descendingSet()),
                 fullTailSet(s2.descendingSet()));
    }

    static boolean isAscending(NavigableMap m) {
        Comparator cmp = m.comparator();
        return (cmp == null || cmp.compare(1, 2) < 0);
    }

    static NavigableMap fullSubMap(NavigableMap m) {
        return isAscending(m)
            ? m.subMap(Integer.MIN_VALUE, true, Integer.MAX_VALUE, true)
            : m.subMap(Integer.MAX_VALUE, true, Integer.MIN_VALUE, true);
    }

    static NavigableMap fullHeadMap(NavigableMap m) {
        return isAscending(m)
            ? m.headMap(Integer.MAX_VALUE, true)
            : m.headMap(Integer.MIN_VALUE, true);
    }

    static NavigableMap fullTailMap(NavigableMap m) {
        return isAscending(m)
            ? m.tailMap(Integer.MIN_VALUE, true)
            : m.tailMap(Integer.MAX_VALUE, true);
    }

    static boolean isAscending(NavigableSet s) {
        Comparator cmp = s.comparator();
        return (cmp == null || cmp.compare(1, 2) < 0);
    }

    static NavigableSet fullSubSet(NavigableSet s) {
        return isAscending(s)
            ? s.subSet(Integer.MIN_VALUE, true, Integer.MAX_VALUE, true)
            : s.subSet(Integer.MAX_VALUE, true, Integer.MIN_VALUE, true);
    }

    static NavigableSet fullHeadSet(NavigableSet s) {
        return isAscending(s)
            ? s.headSet(Integer.MAX_VALUE, true)
            : s.headSet(Integer.MIN_VALUE, true);
    }

    static NavigableSet fullTailSet(NavigableSet s) {
        return isAscending(s)
            ? s.tailSet(Integer.MIN_VALUE, true)
            : s.tailSet(Integer.MAX_VALUE, true);
    }

    static void testEmptyCollection(Collection<?> c) {
        check(c.isEmpty());
        equal(c.size(), 0);
        equal(c.toString(),"[]");
        equal(c.toArray().length, 0);
        equal(c.toArray(new Object[0]).length, 0);

        Object[] a = new Object[1]; a[0] = Boolean.TRUE;
        equal(c.toArray(a), a);
        equal(a[0], null);
        check(! c.iterator().hasNext());
    }

    static void testEmptySet(Set<?> c) {
        testEmptyCollection(c);
        equal(c.hashCode(), 0);
        equal2(c, Collections.<Integer>emptySet());
    }

    static void testEmptyMap(final Map<?,?> m) {
        check(m.isEmpty());
        equal(m.size(), 0);
        equal(m.toString(),"{}");
        equal(m.hashCode(), 0);
        testEmptySet(m.keySet());
        testEmptySet(m.entrySet());
        testEmptyCollection(m.values());
    }

    static final Random rnd;

    static {
        // sufficiently random for this test
        long seed = System.nanoTime();
        System.out.println(LockStep.class.getCanonicalName() + ": Trial random seed: " + seed );

        rnd = new Random(seed);
    }

    static void equalNext(final Iterator<?> it, Object expected) {
        if (maybe(2))
            check(it.hasNext());
        equal(it.next(), expected);
    }

    static Comparator comparator(NavigableSet s) {
        Comparator cmp = s.comparator();
        return cmp != null ? cmp : new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) o1).compareTo(o2); }};
    }

    static Comparator comparator(NavigableMap m) {
        Comparator cmp = m.comparator();
        return cmp != null ? cmp : new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) o1).compareTo(o2); }};
    }

    static void checkNavigableSet(final NavigableSet s) {
        if (s.comparator() == null)
            check(s.descendingSet().descendingSet().comparator() == null);
        equal(s.isEmpty(), s.size() == 0);
        equal2(s, s.descendingSet());
        if (maybe(4) && s instanceof Serializable) {
            try {
                equal2(s, serialClone(s));
            } catch(RuntimeException uhoh) {
                if(!(uhoh.getCause() instanceof NotSerializableException)) {
                    throw uhoh;
                }
            }
        }
        Comparator cmp = comparator(s);
        if (s.isEmpty()) {
            THROWS(NoSuchElementException.class,
                   () -> s.first(),
                   () -> s.last());
            equal(null, s.lower(1));
            equal(null, s.floor(1));
            equal(null, s.ceiling(1));
            equal(null, s.higher(1));
        } else {
            Object a = s.first();
            Object z = s.last();
            equal(s.lower(a), null);
            equal(s.higher(z), null);
            equal2(s, s.tailSet(a));
            equal2(s, s.headSet(z, true));
            equal2(s, s.subSet(a, true, z, true));

            testEmptySet(s.subSet(a, true, a, false));
            testEmptySet(s.subSet(z, true, z, false));
            testEmptySet(s.headSet(a, false));
            testEmptySet(s.tailSet(z, false));

            equal2(s.headSet(a, true), singleton(a));
            equal2(s.tailSet(z, true), singleton(z));
        }
        Iterator[] its = new Iterator[] {
            s.iterator(),
            s.descendingSet().descendingSet().iterator(),
        };
        for (final Iterator it : its)
            if (maybe(4))
                THROWS(IllegalStateException.class, () -> it.remove());
        Object prev = null;
        for (Object e : s) {
            check(s.contains(e));
            for (Iterator it : its) equalNext(it, e);
            equal(e, s.ceiling(e));
            equal(e, s.floor(e));
            check(s.higher(e) == null || cmp.compare(e, s.higher(e)) < 0);
            equal(s.lower(e), prev);
            if (prev == null) {
            } else {
                check(cmp.compare(prev, e) < 0);
            }
            prev = e;
        }
        for (final Iterator it : its) {
            if (maybe(2))
                check(! it.hasNext());
            Fun fun = () -> it.next();
            THROWS(NoSuchElementException.class, fun, fun, fun);
        }
    }

    static void equalIterators(final Iterator<?> it1,
                               final Iterator<?> it2) {
        while (it1.hasNext()) {
            if (maybe(2))
                check(it2.hasNext());
            equal(it1.next(), it2.next());
        }
        check(! it2.hasNext());
    }

    static void equalSetsLeaf(final Set s1, final Set s2) {
        equal2(s1,            s2);
        equal( s1.size(),     s2.size());
        equal( s1.isEmpty(),  s2.isEmpty());
        equal( s1.hashCode(), s2.hashCode());
        equal( s1.toString(), s2.toString());
        equal( s1.containsAll(s2), s2.containsAll(s1));
    }

    static void equalNavigableSetsLeaf(final NavigableSet s1,
                                       final NavigableSet s2) {
        equal2(s1,            s2);
        equal( s1.size(),     s2.size());
        equal( s1.isEmpty(),  s2.isEmpty());
        equal( s1.hashCode(), s2.hashCode());
        equal( s1.toString(), s2.toString());
        if (! s1.isEmpty()) {
            equal(s1.first(), s2.first());
            equal(s1.last(),  s2.last());
        }
        equalIterators(s1.iterator(), s2.iterator());
        equalIterators(s1.descendingIterator(), s2.descendingIterator());
        checkNavigableSet(s1);
        checkNavigableSet(s2);
    }

    static void equalNavigableSets(final NavigableSet s1,
                                   final NavigableSet s2) {
        equalNavigableSetsLeaf(s1, s2);
        equalNavigableSetsLeaf(s1.descendingSet(), s2.descendingSet());
        equalNavigableSetsLeaf(s1.descendingSet().descendingSet(), s2);
        Object min = s1.isEmpty() ? Integer.MIN_VALUE : s1.first();
        Object max = s2.isEmpty() ? Integer.MAX_VALUE : s2.last();
        if (s1.comparator() != null &&
            s1.comparator().compare(min, max) > 0) {
            Object tmp = min; min = max; max = tmp;
        }

        equalNavigableSetsLeaf(s1.subSet(min, true, max, true),
                               s2.subSet(min, true, max, true));
        equalNavigableSetsLeaf(s1.tailSet(min, true),
                               s2.tailSet(min, true));
        equalNavigableSetsLeaf(s1.headSet(max, true),
                               s2.headSet(max, true));
        equalNavigableSetsLeaf((NavigableSet) s1.subSet(min, max),
                               (NavigableSet) s2.subSet(min, max));
        equalNavigableSetsLeaf((NavigableSet) s1.tailSet(min),
                               (NavigableSet) s2.tailSet(min));
        equalNavigableSetsLeaf((NavigableSet) s1.headSet(max),
                               (NavigableSet) s2.headSet(max));
    }

    // Destined for a Collections.java near you?
    static <T> T[] concat(T[]... arrays) {
        int len = 0;
        for (int i = 0; i < arrays.length; i++)
            len += arrays[i].length;
        T[] a = (T[])java.lang.reflect.Array
            .newInstance(arrays[0].getClass().getComponentType(), len);
        int k = 0;
        for (int i = 0; i < arrays.length; i++) {
            T[] array = arrays[i];
            System.arraycopy(array, 0, a, k, array.length);
            k += array.length;
        }
        return a;
    }

    static void checkNavigableMap(final NavigableMap m) {
        if (m.comparator() == null) {
            check(m.descendingMap().descendingMap().comparator() == null);
            check(m.descendingKeySet().descendingSet().comparator() == null);
        }
        equal(m.isEmpty(), m.size() == 0);
        equal2(m, m.descendingMap());
        if (maybe(4))
            equal2(m, serialClone(m));
        equal2(m.keySet(), m.descendingKeySet());
        Comparator cmp = comparator(m);
        if (m.isEmpty()) {
            THROWS(NoSuchElementException.class,
                   () -> m.firstKey(),
                   () -> m.lastKey());
            equal(null, m.firstEntry());
            equal(null, m.lastEntry());
            equal(null, m.pollFirstEntry());
            equal(null, m.pollLastEntry());
            equal(null, m.lowerKey(1));
            equal(null, m.floorKey(1));
            equal(null, m.ceilingKey(1));
            equal(null, m.higherKey(1));
            equal(null, m.lowerEntry(1));
            equal(null, m.floorEntry(1));
            equal(null, m.ceilingEntry(1));
            equal(null, m.higherEntry(1));
        } else {
            Object a = m.firstKey();
            Object z = m.lastKey();
            equal(m.lowerKey(a), null);
            equal(m.higherKey(z), null);
            equal(a, m.firstEntry().getKey());
            equal(z, m.lastEntry().getKey());
            equal2(m, m.tailMap(a));
            equal2(m, m.headMap(z, true));
            equal2(m, m.subMap(a, true, z, true));

            testEmptyMap(m.subMap(a, true, a, false));
            testEmptyMap(m.subMap(z, true, z, false));
            testEmptyMap(m.headMap(a, false));
            testEmptyMap(m.tailMap(z, false));

            equal2(m.headMap(a, true), singletonMap(a, m.get(a)));
            equal2(m.tailMap(z, true), singletonMap(z, m.get(z)));
        }

        Iterator[] kits = new Iterator[] {
            m.keySet().iterator(),
            m.descendingMap().descendingKeySet().iterator(),
            m.descendingKeySet().descendingSet().iterator(),
        };
        Iterator[] vits = new Iterator[] {
            m.values().iterator(),
            m.descendingMap().descendingMap().values().iterator(),
        };
        Iterator[] eits = new Iterator[] {
            m.entrySet().iterator(),
            m.descendingMap().descendingMap().entrySet().iterator(),
        };
        Iterator[] its = concat(kits, vits, eits);
        for (final Iterator it : its)
            if (maybe(4))
                THROWS(IllegalStateException.class, () -> it.remove());
        Map.Entry prev = null;
        for (Map.Entry e : (Set<Map.Entry>) m.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            check(m.containsKey(k));
            check(m.containsValue(v));
            for (Iterator kit : kits) equalNext(kit, k);
            for (Iterator vit : vits) equalNext(vit, v);
            for (Iterator eit : eits) equalNext(eit, e);
            equal(k, m.ceilingKey(k));
            equal(k, m.ceilingEntry(k).getKey());
            equal(k, m.floorKey(k));
            equal(k, m.floorEntry(k).getKey());
            check(m.higherKey(k) == null || cmp.compare(k, m.higherKey(k)) < 0);
            check(m.lowerKey(k)  == null || cmp.compare(k, m.lowerKey(k))  > 0);
            equal(m.lowerEntry(k), prev);
            if (prev == null) {
                equal(m.lowerKey(k), null);
            } else {
                equal(m.lowerKey(k), prev.getKey());
                check(cmp.compare(prev.getKey(), e.getKey()) < 0);
            }
            prev = e;
        }
        for (final Iterator it : its) {
            if (maybe(2))
                check(! it.hasNext());
            Fun fun = () -> it.next();
            THROWS(NoSuchElementException.class, fun, fun, fun);
        }
    }

    static void equalNavigableMapsLeaf(final NavigableMap m1,
                                       final NavigableMap m2) {
        equal2(m1,              m2);
        equal( m1.size(),       m2.size());
        equal( m1.isEmpty(),    m2.isEmpty());
        equal( m1.hashCode(),   m2.hashCode());
        equal( m1.toString(),   m2.toString());
        equal2(m1.firstEntry(), m2.firstEntry());
        equal2(m1.lastEntry(),  m2.lastEntry());
        checkNavigableMap(m1);
        checkNavigableMap(m2);
    }

    static void equalNavigableMaps(NavigableMap m1,
                                   NavigableMap m2) {
        equalNavigableMapsLeaf(m1, m2);
        equalSetsLeaf(m1.keySet(), m2.keySet());
        equalNavigableSets(m1.navigableKeySet(),
                           m2.navigableKeySet());
        equalNavigableSets(m1.descendingKeySet(),
                           m2.descendingKeySet());
        equal2(m1.entrySet(),
               m2.entrySet());
        equalNavigableMapsLeaf(m1.descendingMap(),
                               m2.descendingMap());
        equalNavigableMapsLeaf(m1.descendingMap().descendingMap(),
                               m2);
        equalNavigableSetsLeaf((NavigableSet) m1.descendingMap().keySet(),
                               (NavigableSet) m2.descendingMap().keySet());
        equalNavigableSetsLeaf(m1.descendingMap().descendingKeySet(),
                               m2.descendingMap().descendingKeySet());
        equal2(m1.descendingMap().entrySet(),
               m2.descendingMap().entrySet());

        //----------------------------------------------------------------
        // submaps
        //----------------------------------------------------------------
        Object min = Integer.MIN_VALUE;
        Object max = Integer.MAX_VALUE;
        if (m1.comparator() != null
            && m1.comparator().compare(min, max) > 0) {
            Object tmp = min; min = max; max = tmp;
        }
        switch (rnd.nextInt(6)) {
        case 0:
            equalNavigableMapsLeaf(m1.subMap(min, true, max, true),
                                   m2.subMap(min, true, max, true));
            break;
        case 1:
            equalNavigableMapsLeaf(m1.tailMap(min, true),
                                   m2.tailMap(min, true));
            break;
        case 2:
            equalNavigableMapsLeaf(m1.headMap(max, true),
                                   m2.headMap(max, true));
            break;
        case 3:
            equalNavigableMapsLeaf((NavigableMap) m1.subMap(min, max),
                                   (NavigableMap) m2.subMap(min, max));
            break;
        case 4:
            equalNavigableMapsLeaf((NavigableMap) m1.tailMap(min),
                                   (NavigableMap) m2.tailMap(min));
            break;
        case 5:
            equalNavigableMapsLeaf((NavigableMap) m1.headMap(max),
                                   (NavigableMap) m2.headMap(max));
            break;
        }
    }

    abstract static class MapFrobber { abstract void frob(NavigableMap m); }
    abstract static class SetFrobber { abstract void frob(NavigableSet m); }

    static MapFrobber randomAdder(NavigableMap m) {
        final Integer k = unusedKey(m);
        final MapFrobber[] randomAdders = {
            new MapFrobber() {void frob(NavigableMap m) {
                equal(m.put(k, k+1), null);
                equal(m.get(k), k+1);
                if (maybe(4)) {
                    equal(m.put(k, k+1), k+1);
                    equal(m.get(k), k+1);}}},
            new MapFrobber() {void frob(NavigableMap m) {
                m.descendingMap().put(k, k+1);
                equal(m.get(k), k+1);}},
            new MapFrobber() {void frob(NavigableMap m) {
                m.tailMap(k,true).headMap(k,true).put(k,k+1);}},
            new MapFrobber() {void frob(NavigableMap m) {
                m.tailMap(k,true).headMap(k,true).descendingMap().put(k,k+1);}}
        };
        return new MapFrobber() {void frob(NavigableMap m) {
            randomAdders[rnd.nextInt(randomAdders.length)].frob(m);
            if (maybe(2)) equal(m.get(k), k+1);
            if (maybe(4)) {
                equal(m.put(k, k+1), k+1);
                equal(m.get(k), k+1);}}};
    }

    static SetFrobber randomAdder(NavigableSet s) {
        final Integer e = unusedElt(s);
        final SetFrobber[] randomAdders = {
            new SetFrobber() {void frob(NavigableSet s) {
                check(s.add(e));}},
            new SetFrobber() {void frob(NavigableSet s) {
                s.descendingSet().add(e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                s.tailSet(e,true).headSet(e,true).add(e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                s.descendingSet().tailSet(e,true).headSet(e,true).add(e);}}
        };
        return new SetFrobber() {void frob(NavigableSet s) {
            if (maybe(2)) check(! s.contains(e));
            randomAdders[rnd.nextInt(randomAdders.length)].frob(s);
            if (maybe(2)) check(! s.add(e));
            if (maybe(2)) check(s.contains(e));}};
    }

    static Integer unusedElt(NavigableSet s) {
        Integer e;
        do { e = rnd.nextInt(1024); }
        while (s.contains(e));
        return e;
    }

    static Integer unusedKey(NavigableMap m) {
        Integer k;
        do { k = rnd.nextInt(1024); }
        while (m.containsKey(k));
        return k;
    }

    static Integer usedKey(NavigableMap m) {
        Integer x = rnd.nextInt(1024);
        Integer floor   = (Integer) m.floorKey(x);
        Integer ceiling = (Integer) m.ceilingKey(x);
        if (floor != null) return floor;
        check(ceiling != null);
        return ceiling;
    }

    static Integer usedElt(NavigableSet s) {
        Integer x = rnd.nextInt(1024);
        Integer floor   = (Integer) s.floor(x);
        Integer ceiling = (Integer) s.ceiling(x);
        if (floor != null) return floor;
        check(ceiling != null);
        return ceiling;
    }

    static void checkUnusedKey(NavigableMap m, Object k) {
        check(! m.containsKey(k));
        equal(m.get(k), null);
        if (maybe(2))
            equal(m.remove(k), null);
    }

    static void checkUnusedElt(NavigableSet s, Object e) {
        if (maybe(2))
            check(! s.contains(e));
        if (maybe(2)) {
            check(s.ceiling(e) != e);
            check(s.floor(e)   != e);
        }
        if (maybe(2))
            check(! s.remove(e));
    }

    static Fun remover(final Iterator it) {
        return () -> it.remove();
    }

    static MapFrobber randomRemover(NavigableMap m) {
        final Integer k = usedKey(m);
        final MapFrobber[] randomRemovers = {
            new MapFrobber() {void frob(NavigableMap m) {
                Map.Entry e = m.firstEntry();
                equal(m.pollFirstEntry(), e);
                checkUnusedKey(m, e.getKey());}},
            new MapFrobber() {void frob(NavigableMap m) {
                Map.Entry e = m.lastEntry();
                equal(m.pollLastEntry(), e);
                checkUnusedKey(m, e.getKey());}},
            new MapFrobber() {void frob(NavigableMap m) {
                check(m.remove(k) != null);
                checkUnusedKey(m, k);}},
            new MapFrobber() {void frob(NavigableMap m) {
                m.subMap(k, true, k, true).clear();
                checkUnusedKey(m, k);}},
            new MapFrobber() {void frob(NavigableMap m) {
                m.descendingMap().subMap(k, true, k, true).clear();
                checkUnusedKey(m, k);}},
            new MapFrobber() {void frob(NavigableMap m) {
                final Iterator it = m.keySet().iterator();
                while (it.hasNext())
                    if (it.next().equals(k)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class,
                                   () -> it.remove());
                    }
                checkUnusedKey(m, k);}},
            new MapFrobber() {void frob(NavigableMap m) {
                final Iterator it = m.navigableKeySet().descendingIterator();
                while (it.hasNext())
                    if (it.next().equals(k)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class,
                                   () -> it.remove());
                    }
                checkUnusedKey(m, k);}},
            new MapFrobber() {void frob(NavigableMap m) {
                final Iterator<Map.Entry> it = m.entrySet().iterator();
                while (it.hasNext())
                    if (it.next().getKey().equals(k)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class, remover(it));
                    }
                checkUnusedKey(m, k);}},
        };

        return randomRemovers[rnd.nextInt(randomRemovers.length)];
    }

    static SetFrobber randomRemover(NavigableSet s) {
        final Integer e = usedElt(s);

        final SetFrobber[] randomRemovers = {
            new SetFrobber() {void frob(NavigableSet s) {
                Object e = s.first();
                equal(s.pollFirst(), e);
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                Object e = s.last();
                equal(s.pollLast(), e);
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                check(s.remove(e));
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                s.subSet(e, true, e, true).clear();
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                s.descendingSet().subSet(e, true, e, true).clear();
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                final Iterator it = s.iterator();
                while (it.hasNext())
                    if (it.next().equals(e)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class,
                                   () -> it.remove());
                    }
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                final Iterator it = s.descendingSet().iterator();
                while (it.hasNext())
                    if (it.next().equals(e)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class,
                                   () -> it.remove());
                    }
                checkUnusedElt(s, e);}},
            new SetFrobber() {void frob(NavigableSet s) {
                final Iterator it = s.descendingIterator();
                while (it.hasNext())
                    if (it.next().equals(e)) {
                        it.remove();
                        if (maybe(2))
                            THROWS(IllegalStateException.class,
                                   () -> it.remove());
                    }
                checkUnusedElt(s, e);}}
        };

        return randomRemovers[rnd.nextInt(randomRemovers.length)];
    }

    static void lockStep(NavigableMap m1,
                         NavigableMap m2) {
        if (! (thorough || maybe(3))) return;
        if (maybe(4)) m1 = serialClone(m1);
        if (maybe(4)) m2 = serialClone(m2);

        List<NavigableMap> maps = Arrays.asList(m1, m2);
        for (NavigableMap m : maps) testEmptyMap(m);
        final Set<Integer> ints = new HashSet<Integer>();
        while (ints.size() < size)
            ints.add(rnd.nextInt(1024));
        final Integer[] elts = ints.toArray(new Integer[size]);
        for (int i = 0; i < size; i++) {
            MapFrobber adder = randomAdder(m1);
            for (final NavigableMap m : maps) {
                adder.frob(m);
                equal(m.size(), i+1);
            }
            equalNavigableMaps(m1, m2);
        }
        for (final NavigableMap m : maps) {
            final Object e = usedKey(m);
            THROWS(IllegalArgumentException.class,
                   () -> {m.subMap(e,true,e,false)
                           .subMap(e,true,e,true);},
                   () -> {m.subMap(e,false,e,true)
                           .subMap(e,true,e,true);},
                   () -> m.tailMap(e,false).tailMap(e,true),
                   () -> m.headMap(e,false).headMap(e,true));
        }
        //System.out.printf("%s%n", m1);
        for (int i = size; i > 0; i--) {
            MapFrobber remover = randomRemover(m1);
            for (final NavigableMap m : maps) {
                remover.frob(m);
                equal(m.size(), i-1);
            }
            equalNavigableMaps(m1, m2);
        }
        for (NavigableMap m : maps) testEmptyMap(m);
    }

    static void lockStep(NavigableSet s1,
                         NavigableSet s2) {
        if (! (thorough || maybe(3))) return;
        if (maybe(4)) s1 = serialClone(s1);
        if (maybe(4)) s2 = serialClone(s2);

        List<NavigableSet> sets = Arrays.asList(s1, s2);
        for (NavigableSet s : sets) testEmptySet(s);
        final Set<Integer> ints = new HashSet<Integer>();
        while (ints.size() < size)
            ints.add(rnd.nextInt(1024));
        final Integer[] elts = ints.toArray(new Integer[size]);
        for (int i = 0; i < size; i++) {
            SetFrobber adder = randomAdder(s1);
            for (final NavigableSet s : sets) {
                adder.frob(s);
                equal(s.size(), i+1);
            }
            equalNavigableSets(s1, s2);
        }
        for (final NavigableSet s : sets) {
            final Object e = usedElt(s);
            THROWS(IllegalArgumentException.class,
                   () -> {s.subSet(e,true,e,false)
                           .subSet(e,true,e,true);},
                   () -> {s.subSet(e,false,e,true)
                           .subSet(e,true,e,true);},
                   () -> s.tailSet(e,false).tailSet(e,true),
                   () -> s.headSet(e,false).headSet(e,true));
        }
        //System.out.printf("%s%n", s1);
        for (int i = size; i > 0; i--) {
            SetFrobber remover = randomRemover(s1);
            for (final NavigableSet s : sets) {
                remover.frob(s);
                equal(s.size(), i-1);
            }
            equalNavigableSets(s1, s2);
        }
        for (NavigableSet s : sets) testEmptySet(s);
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() { passed++; }
    static void fail() { failed++; Thread.dumpStack(); }
    static void fail(String msg) { System.out.println(msg); fail(); }
    static void unexpected(Throwable t) { failed++; t.printStackTrace(); }
    static void check(boolean cond) { if (cond) pass(); else fail(); }
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else {System.out.println(x + " not equal to " + y); fail();}}
    static void equal2(Object x, Object y) {equal(x, y); equal(y, x);}
    public static void main(String[] args) throws Throwable {
        try { realMain(args); } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Exception("Some tests failed");
    }
    interface Fun {void f() throws Throwable;}
    static void THROWS(Class<? extends Throwable> k, Fun... fs) {
          for (Fun f : fs)
              try { f.f(); fail("Expected " + k.getName() + " not thrown"); }
              catch (Throwable t) {
                  if (k.isAssignableFrom(t.getClass())) pass();
                  else unexpected(t);}}
    static byte[] serializedForm(Object obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }}
    static Object readObject(byte[] bytes)
        throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        return new ObjectInputStream(is).readObject();}
    @SuppressWarnings("unchecked")
    static <T> T serialClone(T obj) {
        try { return (T) readObject(serializedForm(obj)); }
        catch (Error|RuntimeException e) { throw e; }
        catch (Throwable e) { throw new RuntimeException(e); }
    }
}
