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

/*
 * @test
 * @bug 8303882
 * @summary Verify that Iterators method work as expected
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @run junit IteratorsTest
 */

import com.sun.tools.javac.util.Iterators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class IteratorsTest {

    @Test
    public void consistentNext() {
        Iterator<?> emptyCompoundIterator = Iterators.createCompoundIterator(List.of(), Function.identity());
        Assertions.assertThrows(NoSuchElementException.class, emptyCompoundIterator::next);
        Assertions.assertThrows(NoSuchElementException.class, emptyCompoundIterator::next);
    }

    // different ways of obtaining an empty iterator are used to make sure
    // the compound iterator doesn't depend on (checking) the identity of
    // any one of them
    @Test
    public void intermediateEmptyIterators() {
        List<Iterator<String>> inputs = List.of(
                Collections.<String>emptyList().iterator(),
                Collections.emptyListIterator(),
                Collections.emptyIterator(),
                List.of("1").iterator(),
                List.of("2", "3").iterator(),
                List.<String>of().iterator(),
                Collections.<String>emptySet().iterator(),
                List.of("4", "5").iterator(),
                com.sun.tools.javac.util.List.<String>nil().iterator());
        Iterator<String> emptyCompoundIterator = Iterators.createCompoundIterator(inputs, Function.identity());
        var actual = new ArrayList<String>();
        emptyCompoundIterator.forEachRemaining(actual::add);
        assertEquals(List.of("1", "2", "3", "4", "5"), actual);
    }

    @Test
    public void recursiveEmpty() {
        Iterable<Iterator<Object>> inner = () -> Iterators.createCompoundIterator(List.of(), i -> Collections.emptyIterator());
        Iterator<Object> outer = Iterators.createCompoundIterator(inner, Function.identity());
        assertFalse(outer.hasNext());
    }

    @Test
    public void compoundIterator() {
        TestConverter<String> c = new TestConverter<>(it -> it);
        TestIterator<String> test1 = new TestIterator<>(List.of("1").iterator());
        TestIterator<String> test2 = new TestIterator<>(List.of("2").iterator());
        Iterator<String> compound = Iterators.createCompoundIterator(List.of(test1, test2), c);

        //nothing should be called before the hasNext or next is called:
        assertAndResetMaxCalls(c, 0);
        assertAndResetMaxCalls(test1, 0, 0);
        assertAndResetMaxCalls(test2, 0, 0);

        //when hasNext is called, should invoke the hasNext delegate once:
        Assertions.assertTrue(compound.hasNext());

        assertAndResetMaxCalls(c, 1);
        assertAndResetMaxCalls(test1, 1, 0);
        assertAndResetMaxCalls(test2, 0, 0);

        Assertions.assertTrue(compound.hasNext());

        assertAndResetMaxCalls(c, 0);
        assertAndResetMaxCalls(test1, 1, 0);
        assertAndResetMaxCalls(test2, 0, 0);

        //next may invoke hasNext once:
        Assertions.assertEquals("1", compound.next());

        assertAndResetMaxCalls(c, 0);
        assertAndResetMaxCalls(test1, 1, 1);
        assertAndResetMaxCalls(test2, 0, 0);
    }

    private void assertAndResetMaxCalls(TestIterator<?> test, int maxExpectedHasNextCalls, int maxExpectedNextCalls) {
        if (test.hasNextCalls > maxExpectedHasNextCalls) {
            Assertions.fail("too many hasNext invocations: " + test.hasNextCalls +
                            ", expected: " + maxExpectedHasNextCalls);
        }
        test.hasNextCalls = 0;
        if (test.nextCalls > maxExpectedNextCalls) {
            Assertions.fail("too many next invocations: " + test.nextCalls +
                            ", expected: " + maxExpectedNextCalls);
        }
        test.nextCalls = 0;
    }

    private void assertAndResetMaxCalls(TestConverter<?> test, int maxExpectedApplyCalls) {
        if (test.applyCalls > maxExpectedApplyCalls) {
            Assertions.fail("too many apply invocations: " + test.applyCalls +
                            ", expected: " + maxExpectedApplyCalls);
        }
        test.applyCalls = 0;
    }

    static class TestIterator<T> implements Iterator<T> {
        int hasNextCalls;
        int nextCalls;
        final Iterator<T> delegate;

        public TestIterator(Iterator<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            hasNextCalls++;
            return delegate.hasNext();
        }

        @Override
        public T next() {
            nextCalls++;
            return delegate.next();
        }
    }

    static class TestConverter<T> implements Function<TestIterator<T>, Iterator<T>> {
        int applyCalls;
        final Function<TestIterator<T>, Iterator<T>> delegate;

        public TestConverter(Function<TestIterator<T>, Iterator<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<T> apply(TestIterator<T> t) {
            applyCalls++;
            return delegate.apply(t);
        }
    }
}
