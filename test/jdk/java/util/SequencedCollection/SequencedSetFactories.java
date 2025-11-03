/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8156081
 * @summary Test convenience static factory methods on SequencedSet.
 * @run testng SequencedSetFactories
 */


public class SequencedSetFactories {

    static final int NUM_STRINGS = 20; // should be larger than the largest fixed-arg overload
    static final String[] stringArray;
    static {
        String[] sa = new String[NUM_STRINGS];
        for (int i = 0; i < NUM_STRINGS; i++) {
            sa[i] = String.valueOf((char)('a' + i));
        }
        stringArray = sa;
    }

    static Object[] a(SequencedSet<String> act, SequencedSet<String> exp) {
        return new Object[] { act, exp };
    }

    static SequencedSet<String> linkedHashSetOf(String... args) {
        return new LinkedHashSet<>(Arrays.asList(args));
    }

    static void assertSeqSetsCompatible(SequencedSet<?> one, SequencedSet<?> two) {
        assertEquals(one, two);
        assertEquals(two, one);
        for (Iterator<?> i1 = one.iterator(), i2 = two.iterator(); i1.hasNext() && i2.hasNext();) {
            assertEquals(i1.next(), i2.next());
        }
    }

    @DataProvider(name="empty")
    public Iterator<Object[]> empty() {
        return Collections.singletonList(
            // actual, expected
            a(SequencedSet.of(), Collections.emptySortedSet())
        ).iterator();
    }

    @DataProvider(name="nonempty")
    public Iterator<Object[]> nonempty() {
        return Arrays.asList(
            // actual, expected
            a(   SequencedSet.of("a"),
              linkedHashSetOf("a")),
            a(   SequencedSet.of("a", "b"),
              linkedHashSetOf("a", "b")),
            a(   SequencedSet.of("a", "b", "c"),
              linkedHashSetOf("a", "b", "c")),
            a(   SequencedSet.of("a", "b", "c", "d"),
              linkedHashSetOf("a", "b", "c", "d")),
            a(   SequencedSet.of("a", "b", "c", "d", "e"),
              linkedHashSetOf("a", "b", "c", "d", "e")),
            a(   SequencedSet.of("a", "b", "c", "d", "e", "f"),
              linkedHashSetOf("a", "b", "c", "d", "e", "f")),
            a(   SequencedSet.of("a", "b", "c", "d", "e", "f", "g"),
              linkedHashSetOf("a", "b", "c", "d", "e", "f", "g")),
            a(   SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h"),
              linkedHashSetOf("a", "b", "c", "d", "e", "f", "g", "h")),
            a(   SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i"),
              linkedHashSetOf("a", "b", "c", "d", "e", "f", "g", "h", "i")),
            a(   SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
              linkedHashSetOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")),
            a(   SequencedSet.of("j", "i", "h", "g", "f", "e", "d", "c", "b", "a"),
              SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").reversed()),
            a(   SequencedSet.of(stringArray),
              linkedHashSetOf(stringArray))
        ).iterator();
    }

    @DataProvider(name="all")
    public Iterator<Object[]> all() {
        List<Object[]> all = new ArrayList<>();
        empty().forEachRemaining(all::add);
        nonempty().forEachRemaining(all::add);
        return all.iterator();
    }

    @Test(dataProvider="all", expectedExceptions=UnsupportedOperationException.class)
    public void cannotAdd(SequencedSet<String> act, SequencedSet<String> exp) {
        act.add("x");
    }

    @Test(dataProvider="nonempty", expectedExceptions=UnsupportedOperationException.class)
    public void cannotRemove(SequencedSet<String> act, SequencedSet<String> exp) {
        act.remove(act.iterator().next());
    }

    @Test(dataProvider="all")
    public void contentsMatch(SequencedSet<String> act, SequencedSet<String> exp) {
        assertEquals(act, exp);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed2() {
        SequencedSet<String> set = SequencedSet.of("a", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed3() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed4() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed5() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed6() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "e", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed7() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "e", "f", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed8() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed9() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowed10() {
        SequencedSet<String> set = SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "a");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupsDisallowedN() {
        String[] array = stringArray.clone();
        array[0] = array[1];
        SequencedSet<String> set = SequencedSet.of(array);
    }

    @Test(dataProvider="all")
    public void hashCodeEqual(SequencedSet<String> act, SequencedSet<String> exp) {
        assertEquals(act.hashCode(), exp.hashCode());
    }

    @Test(dataProvider="all")
    public void containsAll(SequencedSet<String> act, SequencedSet<String> exp) {
        assertTrue(act.containsAll(exp));
        assertTrue(exp.containsAll(act));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed1() {
        SequencedSet.of((String)null); // force one-arg overload
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed2a() {
        SequencedSet.of("a", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed2b() {
        SequencedSet.of(null, "b");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed3() {
        SequencedSet.of("a", "b", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed4() {
        SequencedSet.of("a", "b", "c", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed5() {
        SequencedSet.of("a", "b", "c", "d", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed6() {
        SequencedSet.of("a", "b", "c", "d", "e", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed7() {
        SequencedSet.of("a", "b", "c", "d", "e", "f", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed8() {
        SequencedSet.of("a", "b", "c", "d", "e", "f", "g", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed9() {
        SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowed10() {
        SequencedSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullDisallowedN() {
        String[] array = stringArray.clone();
        array[0] = null;
        SequencedSet.of(array);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullArrayDisallowed() {
        SequencedSet.of((Object[])null);
    }

    @Test(dataProvider="all", expectedExceptions=NullPointerException.class)
    public void containsNullShouldThrowNPE(SequencedSet<String> act, SequencedSet<String> exp) {
        act.contains(null);
    }

    @Test(dataProvider="all")
    public void serialEquality(SequencedSet<String> act, SequencedSet<String> exp) {
        // assume that act.equals(exp) tested elsewhere
        SequencedSet<String> copy = serialClone(act);
        assertSeqSetsCompatible(act, copy);
    }

    @SuppressWarnings("unchecked")
    static <T> T serialClone(T obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(obj);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    SequencedSet<Integer> genSet() {
        return new LinkedHashSet<>(Arrays.asList(1, 2, 3));
    }

    @Test
    public void copyOfResultsEqual() {
        SequencedSet<Integer> orig = genSet();
        SequencedSet<Integer> copy = SequencedSet.copyOf(orig);

        assertSeqSetsCompatible(orig, copy);
    }

    @Test
    public void copyOfModifiedUnequal() {
        SequencedSet<Integer> orig = genSet();
        SequencedSet<Integer> copy = SequencedSet.copyOf(orig);
        orig.add(4);

        assertNotEquals(orig, copy);
        assertNotEquals(copy, orig);
    }

    @Test
    public void copyOfIdentity() {
        SequencedSet<Integer> orig = genSet();
        SequencedSet<Integer> copy1 = SequencedSet.copyOf(orig);
        SequencedSet<Integer> copy2 = SequencedSet.copyOf(copy1);

        assertNotSame(orig, copy1);
        assertSame(copy1, copy2);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void copyOfRejectsNullCollection() {
        SequencedSet<Integer> set = SequencedSet.copyOf(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void copyOfRejectsNullElements() {
        SequencedSet<Integer> set = SequencedSet.copyOf(Arrays.asList(1, null, 3));
    }

    @Test
    public void copyOfAcceptsDuplicates() {
        SequencedSet<Integer> set = SequencedSet.copyOf(Arrays.asList(1, 1, 2, 3, 3, 3));
        assertSeqSetsCompatible(set, SequencedSet.of(1, 2, 3));
    }

    @Test
    public void copyOfAcceptsDeduplicate() {
        record Foo(int a) {}
        var a = new Foo(1);
        var b = new Foo(1);
        var c = new Foo(1);
        SequencedSet<Foo> set = SequencedSet.copyOf(Arrays.asList(a, b, c));
        assertSeqSetsCompatible(set, SequencedSet.of(a));
        assertSame(a, set.getFirst());
    }
}
