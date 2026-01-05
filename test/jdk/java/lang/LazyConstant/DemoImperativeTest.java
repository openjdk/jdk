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

/* @test
 * @summary Test of a demo of an imperative stable value based on a lazy constant
 * @enablePreview
 * @run junit DemoImperativeTest
 */

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class DemoImperativeTest {

    interface ImperativeStableValue<T> {
        T orElse(T other);
        boolean isSet();
        boolean trySet(T t);
        T get();

        static <T> ImperativeStableValue<T> of() {
            var scratch = new AtomicReference<T>();
            return new Impl<>(scratch, LazyConstant.of(scratch::get));
        }

    }


    private record Impl<T>(AtomicReference<T> scratch,
                           LazyConstant<T> underlying) implements ImperativeStableValue<T> {

        @Override
        public boolean trySet(T t) {
            final boolean result = scratch.compareAndSet(null, t);
            if (result) {
                // Actually set the value
                get();
            }
            return result;
        }

        @Override public T       orElse(T other) { return underlying.orElse(other); }
        @Override public boolean isSet() { return underlying.isInitialized(); }
        @Override public T       get() { return underlying.get(); }

    }

    @Test
    void basic() {
        var stableValue = ImperativeStableValue.<Integer>of();
        assertFalse(stableValue.isSet());
        assertEquals(13, stableValue.orElse(13));
        assertTrue(stableValue.trySet(42));
        assertFalse(stableValue.trySet(13));
        assertTrue(stableValue.isSet());
        assertEquals(42, stableValue.get());
        assertEquals(42, stableValue.orElse(13));
    }

}
