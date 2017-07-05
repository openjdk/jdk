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
 * @summary Basic functional test of OptionalDouble
 * @author Mike Duigou
 * @run testng BasicDouble
 */

import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.DoubleStream;

import static org.testng.Assert.*;
import org.testng.annotations.Test;


public class BasicDouble {

    @Test(groups = "unit")
    public void testEmpty() {
        OptionalDouble empty = OptionalDouble.empty();
        OptionalDouble present = OptionalDouble.of(1.0);

        // empty
        assertTrue(empty.equals(empty));
        assertTrue(empty.equals(OptionalDouble.empty()));
        assertTrue(!empty.equals(present));
        assertTrue(0 == empty.hashCode());
        assertTrue(!empty.toString().isEmpty());
        assertTrue(!empty.isPresent());

        empty.ifPresent(v -> { fail(); });

        AtomicBoolean emptyCheck = new AtomicBoolean();
        empty.ifPresentOrElse(v -> fail(), () -> emptyCheck.set(true));
        assertTrue(emptyCheck.get());

        try {
            empty.ifPresentOrElse(v -> fail(), () -> { throw new ObscureException(); });
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }

        assertEquals(2.0, empty.orElse(2.0));
        assertEquals(2.0, empty.orElseGet(()-> 2.0));
    }

    @Test(groups = "unit")
    public void testIfPresentAndOrElseAndNull() {
        OptionalDouble empty = OptionalDouble.empty();
        OptionalDouble present = OptionalDouble.of(1.0);

        // No NPE
        present.ifPresentOrElse(v -> {}, null);
        empty.ifPresent(null);
        empty.ifPresentOrElse(null, () -> {});

        // NPE
        try {
            present.ifPresent(null);
            fail();
        } catch (NullPointerException ex) {}
        try {
            present.ifPresentOrElse(null, () -> {});
            fail();
        } catch (NullPointerException ex) {}
        try {
            empty.ifPresentOrElse(v -> {}, null);
            fail();
        } catch (NullPointerException ex) {}
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testEmptyGet() {
        OptionalDouble empty = OptionalDouble.empty();

        double got = empty.getAsDouble();
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testEmptyOrElseGetNull() {
        OptionalDouble empty = OptionalDouble.empty();

        double got = empty.orElseGet(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testEmptyOrElseThrowNull() throws Throwable {
        OptionalDouble empty = OptionalDouble.empty();

        double got = empty.orElseThrow(null);
    }

    @Test(expectedExceptions=ObscureException.class)
    public void testEmptyOrElseThrow() throws Exception {
        OptionalDouble empty = OptionalDouble.empty();

        double got = empty.orElseThrow(ObscureException::new);
    }

    @Test(groups = "unit")
    public void testPresent() {
        OptionalDouble empty = OptionalDouble.empty();
        OptionalDouble present = OptionalDouble.of(1.0);

        // present
        assertTrue(present.equals(present));
        assertFalse(present.equals(OptionalDouble.of(0.0)));
        assertTrue(present.equals(OptionalDouble.of(1.0)));
        assertTrue(!present.equals(empty));
        assertTrue(Double.hashCode(1.0) == present.hashCode());
        assertFalse(present.toString().isEmpty());
        assertTrue(-1 != present.toString().indexOf(Double.toString(present.getAsDouble()).toString()));
        assertEquals(1.0, present.getAsDouble());

        AtomicBoolean presentCheck = new AtomicBoolean();
        present.ifPresent(v -> presentCheck.set(true));
        assertTrue(presentCheck.get());
        presentCheck.set(false);
        present.ifPresentOrElse(v -> presentCheck.set(true), () -> fail());
        assertTrue(presentCheck.get());

        try {
            present.ifPresent(v -> { throw new ObscureException(); });
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }
        try {
            present.ifPresentOrElse(v -> { throw new ObscureException(); }, () -> fail());
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }

        assertEquals(1.0, present.orElse(2.0));
        assertEquals(1.0, present.orElseGet(null));
        assertEquals(1.0, present.orElseGet(()-> 2.0));
        assertEquals(1.0, present.orElseGet(()-> 3.0));
        assertEquals(1.0, present.<RuntimeException>orElseThrow(null));
        assertEquals(1.0, present.<RuntimeException>orElseThrow(ObscureException::new));
    }

    @Test(groups = "unit")
    public void testStream() {
        {
            DoubleStream s = OptionalDouble.empty().stream();
            assertFalse(s.isParallel());

            double[] es = s.toArray();
            assertEquals(es.length, 0);
        }

        {
            DoubleStream s = OptionalDouble.of(42.0).stream();
            assertFalse(s.isParallel());

            double[] es = s.toArray();
            assertEquals(es.length, 1);
            assertEquals(es[0], 42.0);
        }
    }

    private static class ObscureException extends RuntimeException {

    }
}
