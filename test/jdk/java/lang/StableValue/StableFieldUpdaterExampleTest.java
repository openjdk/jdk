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
 * @modules java.base/jdk.internal.invoke
 * @run junit StableFieldUpdaterExampleTest
 */

import jdk.internal.invoke.MhUtil;
import jdk.internal.lang.stable.StableFieldUpdater;
import org.junit.jupiter.api.Test;

import java.lang.Override;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test is to make sure the example in the documentation of
 * {@linkplain StableFieldUpdater} remains correct and compilable.
 */
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
            return o instanceof Foo that
                    && Objects.equals(this.bar, that.bar)
                    && Objects.equals(this.baz, that.baz);
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

        private static final MethodHandle HASH_UPDATER;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                VarHandle accessor = lookup.findVarHandle(LazyFoo.class, "hash", int.class);
                MethodHandle underlying = lookup.findStatic(LazyFoo.class, "hashCodeFor", MethodType.methodType(int.class, LazyFoo.class));
                HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        @Stable
        private int hash;

        public LazyFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Foo that
                    && Objects.equals(this.bar, that.bar)
                    && Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            try {
                return (int) HASH_UPDATER.invokeExact(this);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        private static int hashCodeFor(LazyFoo foo) {
            return Objects.hash(foo.bar, foo.baz);
        }
    }

    static

    public final class LazySpecifiedFoo {

        private final Bar bar;
        private final Baz baz;

        private static final MethodHandle HASH_UPDATER;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                VarHandle accessor = lookup.findVarHandle(LazySpecifiedFoo.class, "hash", long.class);
                MethodHandle underlying = lookup.findStatic(LazySpecifiedFoo.class, "hashCodeFor", MethodType.methodType(long.class, LazySpecifiedFoo.class));

                // Replaces zero with 2^32. Bits 32-63 will then be masked away by the
                // `hashCode()` method using an (int) cast.
                underlying = StableFieldUpdater.replaceLongZero(underlying, 1L << 32);

                HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        @Stable
        private long hash;

        public LazySpecifiedFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Foo that
                    && Objects.equals(this.bar, that.bar)
                    && Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            try {
                return (int) (long) HASH_UPDATER.invokeExact(this);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        private static long hashCodeFor(LazySpecifiedFoo foo) {
            return Objects.hash(foo.bar, foo.baz);
        }
    }


    @Test
    void lazy() {
        var bar = new Bar(1);
        var baz = new Baz(2);

        var foo = new LazyFoo(bar, baz);

        assertEquals(Objects.hash(1, 2), foo.hashCode());
    }

}
