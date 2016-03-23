/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8148748
 * @summary Spliterator last-binding and fail-fast tests
 * @run testng SpliteratorLateBindingFailFastTest
 */

@Test
public class SpliteratorLateBindingFailFastTest {

    private interface Source<T> {
        Collection<T> asCollection();
        void update();
    }

    private static class SpliteratorDataBuilder<T> {
        final List<Object[]> data;

        final T newValue;

        final List<T> exp;

        final Map<T, T> mExp;

        SpliteratorDataBuilder(List<Object[]> data, T newValue, List<T> exp) {
            this.data = data;
            this.newValue = newValue;
            this.exp = exp;
            this.mExp = createMap(exp);
        }

        Map<T, T> createMap(List<T> l) {
            Map<T, T> m = new LinkedHashMap<>();
            for (T t : l) {
                m.put(t, t);
            }
            return m;
        }

        void add(String description, Supplier<Source<?>> s) {
            description = joiner(description).toString();
            data.add(new Object[]{description, s});
        }

        void addCollection(Function<Collection<T>, ? extends Collection<T>> f) {
            class CollectionSource implements Source<T> {
                final Collection<T> c = f.apply(exp);

                final Consumer<Collection<T>> updater;

                CollectionSource(Consumer<Collection<T>> updater) {
                    this.updater = updater;
                }

                @Override
                public Collection<T> asCollection() {
                    return c;
                }

                @Override
                public void update() {
                    updater.accept(c);
                }
            }

            String description = "new " + f.apply(Collections.<T>emptyList()).getClass().getName() + ".spliterator() ";
            add(description + "ADD", () -> new CollectionSource(c -> c.add(newValue)));
            add(description + "REMOVE", () -> new CollectionSource(c -> c.remove(c.iterator().next())));
        }

        void addList(Function<Collection<T>, ? extends List<T>> l) {
            addCollection(l);
            addCollection(l.andThen(list -> list.subList(0, list.size())));
        }

        void addMap(Function<Map<T, T>, ? extends Map<T, T>> mapConstructor) {
            class MapSource<U> implements Source<U> {
                final Map<T, T> m = mapConstructor.apply(mExp);

                final Collection<U> c;

                final Consumer<Map<T, T>> updater;

                MapSource(Function<Map<T, T>, Collection<U>> f, Consumer<Map<T, T>> updater) {
                    this.c = f.apply(m);
                    this.updater = updater;
                }

                @Override
                public Collection<U> asCollection() {
                    return c;
                }

                @Override
                public void update() {
                    updater.accept(m);
                }
            }

            Map<String, Consumer<Map<T, T>>> actions = new HashMap<>();
            actions.put("ADD", m -> m.put(newValue, newValue));
            actions.put("REMOVE", m -> m.remove(m.keySet().iterator().next()));

            String description = "new " + mapConstructor.apply(Collections.<T, T>emptyMap()).getClass().getName();
            for (Map.Entry<String, Consumer<Map<T, T>>> e : actions.entrySet()) {
                add(description + ".keySet().spliterator() " + e.getKey(),
                    () -> new MapSource<T>(m -> m.keySet(), e.getValue()));
                add(description + ".values().spliterator() " + e.getKey(),
                    () -> new MapSource<T>(m -> m.values(), e.getValue()));
                add(description + ".entrySet().spliterator() " + e.getKey(),
                    () -> new MapSource<Map.Entry<T, T>>(m -> m.entrySet(), e.getValue()));
            }
        }

        StringBuilder joiner(String description) {
            return new StringBuilder(description).
                    append(" {").
                    append("size=").append(exp.size()).
                    append("}");
        }
    }

    static Object[][] spliteratorDataProvider;

    @DataProvider(name = "Source")
    public static Object[][] spliteratorDataProvider() {
        if (spliteratorDataProvider != null) {
            return spliteratorDataProvider;
        }

        List<Object[]> data = new ArrayList<>();
        SpliteratorDataBuilder<Integer> db = new SpliteratorDataBuilder<>(data, 5, Arrays.asList(1, 2, 3, 4));

        // Collections

        db.addList(ArrayList::new);

        db.addList(LinkedList::new);

        db.addList(Vector::new);


        db.addCollection(HashSet::new);

        db.addCollection(LinkedHashSet::new);

        db.addCollection(TreeSet::new);


        db.addCollection(c -> { Stack<Integer> s = new Stack<>(); s.addAll(c); return s;});

        db.addCollection(PriorityQueue::new);

        // ArrayDeque fails some tests since its fail-fast support is weaker
        // than other collections and limited to detecting most, but not all,
        // removals.  It probably requires its own test since it is difficult
        // to abstract out the conditions under which it fails-fast.
//        db.addCollection(ArrayDeque::new);

        // Maps

        db.addMap(HashMap::new);

        db.addMap(LinkedHashMap::new);

        // This fails when run through jtreg but passes when run through
        // ant
//        db.addMap(IdentityHashMap::new);

        db.addMap(WeakHashMap::new);

        // @@@  Descending maps etc
        db.addMap(TreeMap::new);

        return spliteratorDataProvider = data.toArray(new Object[0][]);
    }

    @Test(dataProvider = "Source")
    public <T> void lateBindingTestWithForEach(String description, Supplier<Source<T>> ss) {
        Source<T> source = ss.get();
        Collection<T> c = source.asCollection();
        Spliterator<T> s = c.spliterator();

        source.update();

        Set<T> r = new HashSet<>();
        s.forEachRemaining(r::add);

        assertEquals(r, new HashSet<>(c));
    }

    @Test(dataProvider = "Source")
    public <T> void lateBindingTestWithTryAdvance(String description, Supplier<Source<T>> ss) {
        Source<T> source = ss.get();
        Collection<T> c = source.asCollection();
        Spliterator<T> s = c.spliterator();

        source.update();

        Set<T> r = new HashSet<>();
        while (s.tryAdvance(r::add)) { }

        assertEquals(r, new HashSet<>(c));
    }

    @Test(dataProvider = "Source")
    public <T> void lateBindingTestWithCharacteritics(String description, Supplier<Source<T>> ss) {
        Source<T> source = ss.get();
        Collection<T> c = source.asCollection();
        Spliterator<T> s = c.spliterator();
        s.characteristics();

        Set<T> r = new HashSet<>();
        s.forEachRemaining(r::add);

        assertEquals(r, new HashSet<>(c));
    }


    @Test(dataProvider = "Source")
    public <T> void testFailFastTestWithTryAdvance(String description, Supplier<Source<T>> ss) {
        {
            Source<T> source = ss.get();
            Collection<T> c = source.asCollection();
            Spliterator<T> s = c.spliterator();

            s.tryAdvance(e -> {
            });
            source.update();

            executeAndCatch(() -> s.tryAdvance(e -> { }));
        }

        {
            Source<T> source = ss.get();
            Collection<T> c = source.asCollection();
            Spliterator<T> s = c.spliterator();

            s.tryAdvance(e -> {
            });
            source.update();

            executeAndCatch(() -> s.forEachRemaining(e -> {
            }));
        }
    }

    @Test(dataProvider = "Source")
    public <T> void testFailFastTestWithForEach(String description, Supplier<Source<T>> ss) {
        Source<T> source = ss.get();
        Collection<T> c = source.asCollection();
        Spliterator<T> s = c.spliterator();

        executeAndCatch(() -> s.forEachRemaining(e -> {
            source.update();
        }));
    }

    @Test(dataProvider = "Source")
    public <T> void testFailFastTestWithEstimateSize(String description, Supplier<Source<T>> ss) {
        {
            Source<T> source = ss.get();
            Collection<T> c = source.asCollection();
            Spliterator<T> s = c.spliterator();

            s.estimateSize();
            source.update();

            executeAndCatch(() -> s.tryAdvance(e -> { }));
        }

        {
            Source<T> source = ss.get();
            Collection<T> c = source.asCollection();
            Spliterator<T> s = c.spliterator();

            s.estimateSize();
            source.update();

            executeAndCatch(() -> s.forEachRemaining(e -> {
            }));
        }
    }

    private void executeAndCatch(Runnable r) {
        executeAndCatch(ConcurrentModificationException.class, r);
    }

    private void executeAndCatch(Class<? extends Exception> expected, Runnable r) {
        Exception caught = null;
        try {
            r.run();
        }
        catch (Exception e) {
            caught = e;
        }

        assertNotNull(caught,
                      String.format("No Exception was thrown, expected an Exception of %s to be thrown",
                                    expected.getName()));
        assertTrue(expected.isInstance(caught),
                   String.format("Exception thrown %s not an instance of %s",
                                 caught.getClass().getName(), expected.getName()));
    }

}
