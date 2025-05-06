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
 * @summary Basic tests for StableFieldUpdaterExampleTest examples in javadoc
 * @modules java.base/jdk.internal.lang.stable
 * @run junit StableFieldUpdaterExampleTest
 */

import jdk.internal.lang.stable.StableFieldUpdater;
import org.junit.jupiter.api.Test;

import java.lang.Override;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StableFieldUpdaterExampleTest {

    @interface Stable {} // No access to the real @Stable

    public static final class Bar extends Base {
        public Bar(int hash) {
            super(hash);
        }
    }

    public static final class Baz extends Base {
        public Baz(int hash) {
            super(hash);
        }
    }

    public static abstract class Base {

        final int hash;

        public Base(int hash) {
            this.hash = hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Base that &&
                    this.getClass().equals(that.getClass()) &&
                    this.hash == that.hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[hash=" + hash + ']';
        }
    }


    static // Intentionally in unblessed order to allow the example to look nice

    public final class Foo {

        private final Bar bar;
        private final Baz baz;

        public Foo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Foo that &&
                    Objects.equals(this.bar, that.bar) &&
                    Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bar, baz);
        }
    }

    static

    public final class LazyFoo {

        private final Bar bar;
        private final Baz baz;

        private static final ToIntFunction<LazyFoo> HASH_UPDATER =
                StableFieldUpdater.ofInt(LazyFoo.class, "hash",
                        l -> Objects.hash(l.bar, l.baz), -1);

        @Stable
        private int hash;

        public LazyFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Foo that &&
                    Objects.equals(this.bar, that.bar) &&
                    Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            return HASH_UPDATER.applyAsInt(this);
        }
    }

    static

    public final class LazySpecifiedFoo {

        private final Bar bar;
        private final Baz baz;

        private static final ToLongFunction<LazySpecifiedFoo> HASH_UPDATER =
                StableFieldUpdater.ofLong(LazySpecifiedFoo.class, "hash",
                        l -> (l.bar == null && l.baz == null) ? 0 : Objects.hash(l.bar, l.baz), 1L << 32);

        @Stable
        private long hash;

        public LazySpecifiedFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Foo that) &&
                    Objects.equals(this.bar, that.bar) &&
                    Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            return (int) HASH_UPDATER.applyAsLong(this);
        }
    }

    @Test
    void lazy() {
        var bar = new Bar(1);
        var baz = new Baz(2);

        var foo = new LazyFoo(bar, baz);

        assertEquals(Objects.hash(1, 2), foo.hashCode());
    }

    @Test
    void lazySpec() {
        var foo = new LazySpecifiedFoo(null, null);
        assertEquals(0, foo.hashCode());
        assertEquals(0, foo.hashCode());
        assertEquals(1L << 32, foo.hash);
    }

}
