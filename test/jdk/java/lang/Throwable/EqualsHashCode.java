/*
 * Copyright 2022 Victor Toni. All Rights Reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/*
 * @test
 * @bug 8203035
 * @summary Basic tests for Throwable hashCode() and equals()
 * @author  Victor Toni
 */

public class EqualsHashCode {

    private static final String MESSAGE = "Throwable test message";
    private static final int TEST_THROWABLES_COUNT = 5;

    void doTests() throws Exception {
        constructingWithDefaultsTest();
        withMessageTest();
        withDifferentMessageTest();
        sameCauseTypeWithDifferentMessageTest();
        withDifferentCauseTypeTest();
    }

    void constructingWithDefaultsTest() throws Exception {
        Throwable t1 = new Throwable();
        // assert reflexive equals and hashCode
        assertHashCodeAndEqualsAreEqual(t1, t1);

        Throwable t2 = new Throwable();
        // assert reflexive equals and hashCode
        assertHashCodeAndEqualsAreEqual(t2, t2);

        // Throwables created on different lines should not be equal
        assertHashCodeAndEqualsAreNotEqual(t1, t2);

        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < TEST_THROWABLES_COUNT; i++) {
            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract
            Throwable newThrowable = new Throwable();
            // assert reflexive equals and hashCode
            assertHashCodeAndEqualsAreEqual(newThrowable, newThrowable);

            for (Throwable existingThrowable : throwables) {
                // assert stable equals and hashCode
                assertHashCodeAndEqualsAreEqual(existingThrowable, newThrowable);
            }
            throwables.add(newThrowable);
        }
    }

    void withMessageTest() throws Exception {
        Throwable t1 = new Throwable(MESSAGE);
        // assert reflexive equals and hashCode
        assertHashCodeAndEqualsAreEqual(t1, t1);

        Throwable t2 = new Throwable(MESSAGE);
        // assert reflexive equals and hashCode
        assertHashCodeAndEqualsAreEqual(t2, t2);

        // Throwables created on different lines should not be equal.
        assertHashCodeAndEqualsAreNotEqual(t1, t2);

        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < TEST_THROWABLES_COUNT; i++) {
            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract.
            Throwable newThrowable = new Throwable(MESSAGE);
            // assert reflexive equals and hashCode
            assertHashCodeAndEqualsAreEqual(newThrowable, newThrowable);

            for (Throwable existingThrowable : throwables) {
                // assert stable equals and hashCode
                assertHashCodeAndEqualsAreEqual(existingThrowable, newThrowable);
            }
            throwables.add(newThrowable);
        }
    }

    void withDifferentMessageTest() throws Exception {
        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < TEST_THROWABLES_COUNT; i++) {
            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract.
            // But now each Throwable has a different message
            Throwable newThrowable = new Throwable(MESSAGE + i);
            // assert reflexive equals and hashCode
            assertHashCodeAndEqualsAreEqual(newThrowable, newThrowable);

            for (Throwable existingThrowable : throwables) {
                assertHashCodeAndEqualsAreNotEqual(existingThrowable, newThrowable);
            }
            throwables.add(newThrowable);
        }
    }

    void sameCauseTypeWithDifferentMessageTest() throws Exception {
        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < TEST_THROWABLES_COUNT; i++) {
            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract.
            // But now each Throwable has a different message
            Throwable cause = new Throwable(MESSAGE + i);

            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract.
            // But now each Throwable has a different cause (due to the
            // different messages).
            Throwable newThrowable = new Throwable(cause);
            // assert reflexive equals and hashCode
            assertHashCodeAndEqualsAreEqual(newThrowable, newThrowable);

            for (Throwable existingThrowable : throwables) {
                assertHashCodeAndEqualsAreNotEqual(existingThrowable, newThrowable);
            }
            throwables.add(newThrowable);
        }
    }

    void withDifferentCauseTypeTest() throws Exception {
        List<Supplier<Throwable>> causeSuppliers = Arrays.asList(
            Exception::new,
            NullPointerException::new,
            IOException::new,
            RuntimeException::new
        );

        List<Throwable> throwables = new ArrayList<>();
        for (Supplier<Throwable> causeSupplier : causeSuppliers) {
            // Creating a Throwable in the same way on the same line
            // that should always be equal by contract.
            // But now each Throwable has a different exception type.
            Throwable cause = causeSupplier.get();

            Throwable newThrowable = new Throwable(cause);
            // assert reflexive equals and hashCode
            assertHashCodeAndEqualsAreEqual(newThrowable, newThrowable);

            for (Throwable existingThrowable : throwables) {
                assertHashCodeAndEqualsAreNotEqual(existingThrowable, newThrowable);
            }
            throwables.add(newThrowable);
        }
    }

    // --------------------- Infrastructure ---------------------------

    void assertHashCodeAndEqualsAreNotEqual(Throwable t1, Throwable t2) {
        assertHashCodeNotEquals(t1, t2);

        assertNotEquals(t1, t2);
    }

    void assertHashCodeAndEqualsAreEqual(Throwable t1, Throwable t2) {
        assertHashCodeEquals(t1, t2);

        assertEquals(t1, t2);
    }

    void assertHashCodeNotEquals(Throwable t1, Throwable t2) {
        int h1 = t1.hashCode();
        int h2 = t2.hashCode();

        if (h1 == h2) {
            throw new AssertionError("Throwables hashCode() are equal: " + h1);
        }
    }

    void assertHashCodeEquals(Throwable t1, Throwable t2) {
        int h1 = t1.hashCode();
        int h2 = t2.hashCode();

        if (h1 != h2) {
            throw new AssertionError("Throwables hashCode() are not equal: " +
                    h1 + " != " + h2);
        }
    }

    void assertNotEquals(Throwable t1, Throwable t2) {
        if (t1.equals(t2)) {
            throw new AssertionError("Throwables are equal");
        }
        // assert symmetry of equals
        if (t2.equals(t1)) {
            throw new AssertionError("Throwables are reflexive equal");
        }
    }

    void assertEquals(Throwable t1, Throwable t2) {
        if (!t1.equals(t2)) {
            throw new AssertionError("Throwables are not equal");
        }
        // assert symmetry of equals
        if (!t2.equals(t1)) {
            throw new AssertionError("Throwables are reflexively not equal");
        }
    }

    public static void main(String[] args) throws Throwable {
        new EqualsHashCode().doTests();
    }

}
