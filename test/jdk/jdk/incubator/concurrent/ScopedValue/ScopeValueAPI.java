/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ScopedValue API
 * @modules jdk.incubator.concurrent
 * @run testng ScopeValueAPI
 */

import jdk.incubator.concurrent.ScopedValue;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ScopeValueAPI {

    /**
     * Test that the run method is invoked.
     */
    public void testRun() {
        var executed = new AtomicBoolean();
        ScopedValue<String> name = ScopedValue.newInstance();
        ScopedValue.where(name, "duke", () -> executed.set(true));
        assertTrue(executed.get());
    }

    /**
     * Test the run method throwing an exception.
     */
    public void testRunThrows() {
        class FooException extends RuntimeException { }
        ScopedValue<String> name = ScopedValue.newInstance();
        Runnable op = () -> { throw new FooException(); };
        assertThrows(FooException.class, () -> ScopedValue.where(name, "duke", op));
        assertFalse(name.isBound());
    }

    /**
     * Test that the call method is invoked.
     */
    public void testCall() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String result = ScopedValue.where(name, "duke", name::get);
        assertEquals(result, "duke");
    }

    /**
     * Test the call method throwing an exception.
     */
    public void testCallThrows() {
        class FooException extends RuntimeException { }
        ScopedValue<String> name = ScopedValue.newInstance();
        Callable<Void> op = () -> { throw new FooException(); };
        assertThrows(FooException.class, () -> ScopedValue.where(name, "duke", op));
        assertFalse(name.isBound());
    }

    /**
     * Test get method.
     */
    public void testGet() throws Exception {
        ScopedValue<String> name1 = ScopedValue.newInstance();
        ScopedValue<String> name2 = ScopedValue.newInstance();
        assertThrows(NoSuchElementException.class, name1::get);
        assertThrows(NoSuchElementException.class, name2::get);

        // run
        ScopedValue.where(name1, "duke", () -> {
            assertEquals(name1.get(), "duke");
            assertThrows(NoSuchElementException.class, name2::get);

        });
        assertThrows(NoSuchElementException.class, name1::get);
        assertThrows(NoSuchElementException.class, name2::get);

        // call
        ScopedValue.where(name1, "duke", () -> {
            assertEquals(name1.get(), "duke");
            assertThrows(NoSuchElementException.class, name2::get);
            return null;
        });
        assertThrows(NoSuchElementException.class, name1::get);
        assertThrows(NoSuchElementException.class, name2::get);
    }

    /**
     * Test isBound method.
     */
    public void testIsBound() throws Exception {
        ScopedValue<String> name1 = ScopedValue.newInstance();
        ScopedValue<String> name2 = ScopedValue.newInstance();
        assertFalse(name1.isBound());
        assertFalse(name2.isBound());

        // run
        ScopedValue.where(name1, "duke", () -> {
            assertTrue(name1.isBound());
            assertFalse(name2.isBound());
        });
        assertFalse(name1.isBound());
        assertFalse(name2.isBound());

        // call
        ScopedValue.where(name1, "duke", () -> {
            assertTrue(name1.isBound());
            assertFalse(name2.isBound());
            return null;
        });
        assertFalse(name1.isBound());
        assertFalse(name2.isBound());
    }

    /**
     * Test orElse method.
     */
    public void testOrElse() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        assertTrue(name.orElse(null) == null);
        assertEquals(name.orElse("default"), "default");

        // run
        ScopedValue.where(name, "duke", () -> {
            assertEquals(name.orElse(null), "duke");
            assertEquals(name.orElse("default"), "duke");
        });

        // call
        var ignore = ScopedValue.where(name, "duke", () -> {
            assertEquals(name.orElse(null), "duke");
            assertEquals(name.orElse("default"), "duke");
            return null;
        });
    }

    /**
     * Test orElseThrow method.
     */
    public void testOrElseThrow() throws Exception {
        class FooException extends RuntimeException { }
        ScopedValue<String> name = ScopedValue.newInstance();
        assertThrows(FooException.class, () -> name.orElseThrow(FooException::new));

        // run
        ScopedValue.where(name, "duke", () -> {
            assertEquals(name.orElseThrow(FooException::new), "duke");
        });

        // call
        var ignore = ScopedValue.where(name, "duke", () -> {
            assertEquals(name.orElseThrow(FooException::new), "duke");
            return null;
        });
    }

    /**
     * Test two bindings.
     */
    public void testTwoBindings() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        ScopedValue<Integer> age = ScopedValue.newInstance();

        // run
        ScopedValue.where(name, "duke").where(age, 100).run(() -> {
            assertTrue(name.isBound());
            assertTrue(age.isBound());
            assertEquals(name.get(), "duke");
            assertEquals((int) age.get(), 100);
        });
        assertFalse(name.isBound());
        assertFalse(age.isBound());

        // call
        var ignore = ScopedValue.where(name, "duke").where(age, 100).call(() -> {
            assertTrue(name.isBound());
            assertTrue(age.isBound());
            assertEquals(name.get(), "duke");
            assertEquals((int) age.get(), 100);
            return null;
        });
        assertFalse(name.isBound());
        assertFalse(age.isBound());
    }

    /**
     * Test rebinding.
     */
    public void testRebinding() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();

        // run
        ScopedValue.where(name, "duke", () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");

            ScopedValue.where(name, "duchess", () -> {
                assertTrue(name.isBound());
                assertTrue("duchess".equals(name.get()));
            });

            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");
        });
        assertFalse(name.isBound());

        // call
        var ignore1 = ScopedValue.where(name, "duke", () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");

            var ignore2 = ScopedValue.where(name, "duchess", () -> {
                assertTrue(name.isBound());
                assertTrue("duchess".equals(name.get()));
                return null;
            });

            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");
            return null;
        });
        assertFalse(name.isBound());
    }

    /**
     * Test rebinding from null vaue to another value.
     */
    public void testRebindingFromNull() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();

        // run
        ScopedValue.where(name, null, () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), null);

            ScopedValue.where(name, "duchess", () -> {
                assertTrue(name.isBound());
                assertTrue("duchess".equals(name.get()));
            });

            assertTrue(name.isBound());
            assertTrue(name.get() == null);
        });
        assertFalse(name.isBound());

        // call
        var ignore1 = ScopedValue.where(name, null, () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), null);

            var ignore2 = ScopedValue.where(name, "duchess", () -> {
                assertTrue(name.isBound());
                assertTrue("duchess".equals(name.get()));
                return null;
            });

            assertTrue(name.isBound());
            assertTrue(name.get() == null);
            return null;
        });
        assertFalse(name.isBound());
    }

    /**
     * Test rebinding to null value.
     */
    public void testRebindingToNull() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();

        // run
        ScopedValue.where(name, "duke", () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");

            ScopedValue.where(name, null, () -> {
                assertTrue(name.isBound());
                assertTrue(name.get() == null);
            });

            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");
        });
        assertFalse(name.isBound());

        // call
        var ignore1 = ScopedValue.where(name, "duke", () -> {
            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");

            var ignore2 = ScopedValue.where(name, null, () -> {
                assertTrue(name.isBound());
                assertTrue(name.get() == null);
                return null;
            });

            assertTrue(name.isBound());
            assertEquals(name.get(), "duke");
            return null;
        });
        assertFalse(name.isBound());
    }

    /**
     * Test Carrier.get.
     */
    public void testCarrierGet() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        ScopedValue<Integer> age = ScopedValue.newInstance();

        // one scoped value
        var carrier1 = ScopedValue.where(name, "duke");
        assertEquals(carrier1.get(name), "duke");
        assertThrows(NoSuchElementException.class, () -> carrier1.get(age));

        // two scoped values
        var carrier2 = carrier1.where(age, 20);
        assertEquals(carrier2.get(name), "duke");
        assertEquals((int) carrier2.get(age), 20);
    }

    /**
     * Test NullPointerException.
     */
    public void testNullPointerException() {
        ScopedValue<String> name = ScopedValue.newInstance();

        assertThrows(NullPointerException.class, () -> ScopedValue.where(null, "value"));
        assertThrows(NullPointerException.class, () -> ScopedValue.where(null, "value", () -> { }));
        assertThrows(NullPointerException.class, () -> ScopedValue.where(null, "value", () -> null));

        assertThrows(NullPointerException.class, () -> name.orElseThrow(null));

        var carrier = ScopedValue.where(name, "duke");
        assertThrows(NullPointerException.class, () -> carrier.where(null, "value"));
        assertThrows(NullPointerException.class, () -> carrier.get(null));
        assertThrows(NullPointerException.class, () -> carrier.run(null));
        assertThrows(NullPointerException.class, () -> carrier.call(null));
    }
}
