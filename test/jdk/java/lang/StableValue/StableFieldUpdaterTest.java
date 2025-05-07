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
 * @summary Basic tests for StableFieldUpdater implementations
 * @modules java.base/jdk.internal.lang.stable
 * @modules java.base/jdk.internal.invoke
 * @run junit StableFieldUpdaterTest
 */

import jdk.internal.invoke.MhUtil;
import jdk.internal.lang.stable.StableFieldUpdater;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

final class StableFieldUpdaterTest {

    private static final int ZERO_REPLACEMENT = 42;
    private static final String STRING = "Abc";
    private static final ToIntFunction<String> STRING_HASH_CODE = String::hashCode;

    @Test
    void invariants() {
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.ofInt(null, "a", _ -> 0, ZERO_REPLACEMENT));
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.ofInt(String.class, null, _ -> 0, ZERO_REPLACEMENT));
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.ofInt(Foo.class, "hash", null, ZERO_REPLACEMENT));
        var x = assertThrows(IllegalArgumentException.class, () -> StableFieldUpdater.ofInt(Foo.class, "dummy", _ -> 0, ZERO_REPLACEMENT));
        assertEquals("Only fields of type 'int' are supported. The provided field is 'long StableFieldUpdaterTest$Foo.dummy'", x.getMessage());
    }

    @Test
    void foo() {
        var foo = new Foo(STRING);
        assertEquals(0, foo.hash);
        assertEquals(STRING.hashCode(), foo.hashCode());
        assertEquals(STRING.hashCode(), foo.hash);

    }

    @Test
    void mhFoo() {
        var foo = new MhFoo(STRING);
        assertEquals(0, foo.hash);
        assertEquals(STRING.hashCode(), foo.hashCode());
        assertEquals(STRING.hashCode(), foo.hash);
    }

    @Test
    void recordFoo() {
        var recordFoo = new RecordFoo(STRING, 0);
        // The field is `final`
        var x = assertThrows(IllegalArgumentException.class,
                () -> StableFieldUpdater.ofInt(RecordFoo.class, "hash", _ -> 0, ZERO_REPLACEMENT));
        assertEquals("Only non final fields are supported. The provided field is 'private final int StableFieldUpdaterTest$RecordFoo.hash'", x.getMessage());
    }

    @Test
    void barInherit() {
        var bar = new Bar(STRING);
        assertEquals(0, bar.hash);
        assertEquals(STRING.hashCode(), bar.hashCode());
        assertEquals(STRING.hashCode(), bar.hash);
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void uncheckedCall() {
        // Use a raw type
        ToIntFunction updater = StableFieldUpdater.ofInt(Foo.class, "hash", f -> f.string.hashCode(), ZERO_REPLACEMENT);
        var object = new Object();
        var x = assertThrows(IllegalArgumentException.class, () -> updater.applyAsInt(object));
        assertEquals("The provided t is not an instance of class StableFieldUpdaterTest$Foo", x.getMessage());
    }

    static final class Foo {

        private static final ToIntFunction<Foo> UPDATER =
                StableFieldUpdater.ofInt(Foo.class, "hash", f -> f.string.hashCode(), ZERO_REPLACEMENT);
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
                StableFieldUpdater.ofInt(Bar.class, "hash", f -> f.string.hashCode(), ZERO_REPLACEMENT);

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

    static final class MhFoo {

        private static final MethodHandle HASH_MH = MhUtil.findVirtual(MethodHandles.lookup(), "hash0", MethodType.methodType(int.class));

        private static final ToIntFunction<MhFoo> UPDATER =
                StableFieldUpdater.ofInt(MhUtil.findVarHandle(MethodHandles.lookup(), "hash", int.class), HASH_MH, ZERO_REPLACEMENT);
        private final String string;

        int hash;
        long dummy;

        public MhFoo(String string) {
            this.string = string;
        }

        @Override
        public int hashCode() {
            return UPDATER.applyAsInt(this);
        }

        public int hash0() {
            return string.hashCode();
        }

    }

}
