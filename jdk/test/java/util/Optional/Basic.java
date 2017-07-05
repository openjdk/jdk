/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic functional test of Optional
 * @author Mike Duigou
 * @run testng Basic
 */

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.testng.Assert.*;
import org.testng.annotations.Test;


public class Basic {

    @Test(groups = "unit")
    public void testEmpty() {
        Optional<Boolean> empty = Optional.empty();
        Optional<String> presentEmptyString = Optional.of("");
        Optional<Boolean> present = Optional.of(Boolean.TRUE);

        // empty
        assertTrue(empty.equals(empty));
        assertTrue(empty.equals(Optional.empty()));
        assertTrue(!empty.equals(present));
        assertTrue(0 == empty.hashCode());
        assertTrue(!empty.toString().isEmpty());
        assertTrue(!empty.toString().equals(presentEmptyString.toString()));
        assertTrue(!empty.isPresent());
        empty.ifPresent(v -> { fail(); });
        assertSame(null, empty.orElse(null));
        RuntimeException orElse = new RuntimeException() { };
        assertSame(Boolean.FALSE, empty.orElse(Boolean.FALSE));
        assertSame(null, empty.orElseGet(()-> null));
        assertSame(Boolean.FALSE, empty.orElseGet(()-> Boolean.FALSE));
    }

        @Test(expectedExceptions=NoSuchElementException.class)
        public void testEmptyGet() {
            Optional<Boolean> empty = Optional.empty();

            Boolean got = empty.get();
        }

        @Test(expectedExceptions=NullPointerException.class)
        public void testEmptyOrElseGetNull() {
            Optional<Boolean> empty = Optional.empty();

            Boolean got = empty.orElseGet(null);
        }

        @Test(expectedExceptions=NullPointerException.class)
        public void testEmptyOrElseThrowNull() throws Throwable {
            Optional<Boolean> empty = Optional.empty();

            Boolean got = empty.orElseThrow(null);
        }

        @Test(expectedExceptions=ObscureException.class)
        public void testEmptyOrElseThrow() throws Exception {
            Optional<Boolean> empty = Optional.empty();

            Boolean got = empty.orElseThrow(ObscureException::new);
        }

        @Test(groups = "unit")
        public void testPresent() {
        Optional<Boolean> empty = Optional.empty();
        Optional<String> presentEmptyString = Optional.of("");
        Optional<Boolean> present = Optional.of(Boolean.TRUE);

        // present
        assertTrue(present.equals(present));
        assertTrue(present.equals(Optional.of(Boolean.TRUE)));
        assertTrue(!present.equals(empty));
        assertTrue(Boolean.TRUE.hashCode() == present.hashCode());
        assertTrue(!present.toString().isEmpty());
        assertTrue(!present.toString().equals(presentEmptyString.toString()));
        assertTrue(-1 != present.toString().indexOf(Boolean.TRUE.toString()));
        assertSame(Boolean.TRUE, present.get());
        try {
            present.ifPresent(v -> { throw new ObscureException(); });
            fail();
        } catch(ObscureException expected) {

        }
        assertSame(Boolean.TRUE, present.orElse(null));
        assertSame(Boolean.TRUE, present.orElse(Boolean.FALSE));
        assertSame(Boolean.TRUE, present.orElseGet(null));
        assertSame(Boolean.TRUE, present.orElseGet(()-> null));
        assertSame(Boolean.TRUE, present.orElseGet(()-> Boolean.FALSE));
        assertSame(Boolean.TRUE, present.<RuntimeException>orElseThrow( null));
        assertSame(Boolean.TRUE, present.<RuntimeException>orElseThrow(ObscureException::new));
    }

    private static class ObscureException extends RuntimeException {

    }
}
