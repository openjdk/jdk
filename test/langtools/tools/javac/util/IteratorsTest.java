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
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.*;

public class IteratorsTest {

    @Test
    public void compoundIterator() {
        TestConverter<TestIterator<String>, String> c = new TestConverter<>(it -> it);
        TestIterator<String> test1 = new TestIterator<>(List.of("1").iterator());
        TestIterator<String> test2 = new TestIterator<>(List.of("2").iterator());
        Iterator<String> compound = Iterators.createCompoundIterator(List.of(test1, test2), c);

        //nothing should be called before the hasNext or next is called:
        assertMaxCalls(c, 0);
        assertMaxCalls(test1, 0, 0);
        assertMaxCalls(test2, 0, 0);

        //when hasNext is called, should invoke the hasNext delegate once:
        Assertions.assertTrue(compound.hasNext());

        assertMaxCalls(c, 1);
        assertMaxCalls(test1, 1, 0);
        assertMaxCalls(test2, 0, 0);

        Assertions.assertTrue(compound.hasNext());

        assertMaxCalls(c, 0);
        assertMaxCalls(test1, 1, 0);
        assertMaxCalls(test2, 0, 0);

        //next may invoke hasNext once:
        Assertions.assertEquals("1", compound.next());

        assertMaxCalls(c, 0);
        assertMaxCalls(test1, 1, 1);
        assertMaxCalls(test2, 0, 0);
    }

    private void assertMaxCalls(TestIterator<?> test, int maxExpectedHasNextCalls, int maxExpectedNextCalls) {
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

    private void assertMaxCalls(TestConverter<?, ?> test, int maxExpectedApplyCalls) {
        if (test.applyCalls > maxExpectedApplyCalls) {
            Assertions.fail("too many apply invocations: " + test.applyCalls +
                            ", expected: " + maxExpectedApplyCalls);
        }
        test.applyCalls = 0;
    }

    class TestIterator<T> implements Iterator<T> {
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
    class TestConverter<I, O>  implements Function<I, Iterator<O>> {
        int applyCalls;
        final Function<I, Iterator<O>> delegate;

        public TestConverter(Function<I, Iterator<O>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<O> apply(I t) {
            applyCalls++;
            return delegate.apply(t);
        }
    }
}