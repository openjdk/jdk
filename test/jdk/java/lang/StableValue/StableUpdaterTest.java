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
 * @summary Basic tests for StableUpdaters implementations
 * @modules java.base/jdk.internal.lang.stable
 * @run junit StableUpdatersTest
 */

import jdk.internal.lang.stable.StableUpdater;
import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

final class StableUpdaterTest {

    private static final int ZERO_REPLACEMENT = 42;
    private static final String STRING = "Abc";
    private static final ToIntFunction<String> STRING_HASH_CODE = String::hashCode;

    @Test
    void invariants() {
        assertThrows(NullPointerException.class, () -> StableUpdater.ofInt(null, "a", _ -> 0, ZERO_REPLACEMENT));
        assertThrows(NullPointerException.class, () -> StableUpdater.ofInt(String.class, null, _ -> 0, ZERO_REPLACEMENT));
        assertThrows(NullPointerException.class, () -> StableUpdater.ofInt(Foo.class, "hash", null, ZERO_REPLACEMENT));
        var x = assertThrows(IllegalArgumentException.class, () -> StableUpdater.ofInt(Foo.class, "dummy", _ -> 0, ZERO_REPLACEMENT));
        assertEquals("Only fields of type 'int' are supported. The provided field is 'long StableUpdatersTest$Foo.dummy'", x.getMessage());
    }

    @Test
    void foo() {
        var foo = new Foo(STRING);
        assertEquals(0, foo.hash);
        assertEquals(STRING.hashCode(), foo.hashCode());
        assertEquals(STRING.hashCode(), foo.hash);
    }

    @Test
    void recordFoo() {
        var recordFoo = new RecordFoo(STRING, 0);
        // The field is `final`
        var x = assertThrows(IllegalArgumentException.class,
                () -> StableUpdater.ofInt(RecordFoo.class, "hash", _ -> 0, ZERO_REPLACEMENT));
        assertEquals("Only non final fields are supported. " +
                "The provided field is 'private final int StableUpdatersTest$RecordFoo.hash'", x.getMessage());
    }

    @Test
    void barInherit() {
        var bar = new Bar(STRING);
        assertEquals(0, bar.hash);
        assertEquals(STRING.hashCode(), bar.hashCode());
        assertEquals(STRING.hashCode(), bar.hash);
    }


    // Todo: Cast to T

    static final class Foo {

        private static final ToIntFunction<Foo> UPDATER =
                StableUpdater.ofInt(Foo.class, "hash", f -> f.string.hashCode(), ZERO_REPLACEMENT);
        private final String string;

        int hash;
        long dummy;

        public Foo(String string) {
            this.string = string;
        }
        @Override
        public int hashCode() {
            return UPDATER.applyAsInt(this);
        }

    }

    record RecordFoo(String string, int hash) {}

    static final class Bar extends AbstractBar {
        private static final ToIntFunction<Bar> UPDATER =
                StableUpdater.ofInt(Bar.class, "hash", f -> f.string.hashCode(), ZERO_REPLACEMENT);

        public Bar(String string) {
            super(string);
        }

        @Override
        public int hashCode() {
            return UPDATER.applyAsInt(this);
        }
    }

    static abstract class AbstractBar {
        final String string;
        int hash;

        public AbstractBar(String string) {
            this.string = string;
        }
    }


}
