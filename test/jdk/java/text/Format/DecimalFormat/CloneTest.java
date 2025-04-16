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

/*
 * @test
 * @bug 8354522
 * @summary Check for cloning interference
 * @run junit/othervm --add-opens=java.base/java.text=ALL-UNNAMED CloneTest
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CloneTest {
    private static Field DIGIT_LIST_FIELD;
    private static Class<?> DIGIT_LIST_CLASS;

    @BeforeAll
    static void setup() throws Exception {
        DIGIT_LIST_FIELD = DecimalFormat.class.getDeclaredField("digitList");
        DIGIT_LIST_FIELD.setAccessible(true);

        DecimalFormat df = new DecimalFormat();
        Object digitList = DIGIT_LIST_FIELD.get(df);

        DIGIT_LIST_CLASS = digitList.getClass();
    }

    // Tests that when DecimalFormat is cloned after use with
    // a long double/BigDecimal, clones will be independent. This is not an
    // exhaustive test. This tests for the issue of the same DigitList.data
    // array being reused across clones of DecimalFormat.

    @Test
    public void testClone() throws Exception {
        DecimalFormat df = new DecimalFormat("#");
        assertCloneValidity(df);
    }

    @Test
    public void testCloneAfterInit() throws Exception {
        DecimalFormat df = new DecimalFormat("#");

        // This initial use of the formatter initialises its internal state, which could
        // subsequently be shared across clones. This is key to reproducing this specific
        // issue.
        String _ = df.format(Math.PI);
        assertCloneValidity(df);
    }

    private static void assertCloneValidity(DecimalFormat df) throws Exception {
        DecimalFormat dfClone = (DecimalFormat) df.clone();

        Object digits = valFromDigitList(df, "digits");
        assertNotSame(digits, valFromDigitList(dfClone, "digits"));


        Object data = valFromDigitList(df, "data");
        if (data != null) {
            assertNotSame(data, valFromDigitList(dfClone, "data"));
        }

        Object tempBuilder = valFromDigitList(df, "tempBuilder");
        if (tempBuilder != null) {
            assertNotSame(data, valFromDigitList(dfClone, "data"));
        }

        assertEquals(DIGIT_LIST_FIELD.get(df), DIGIT_LIST_FIELD.get(dfClone));
    }

    private static Object valFromDigitList(DecimalFormat df, String fieldName) {
        try {
            Object digitList = DIGIT_LIST_FIELD.get(df);
            Field field = DIGIT_LIST_CLASS.getDeclaredField(fieldName);
            field.setAccessible(true);

            return field.get(digitList);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCloneIndependence() {
        AtomicInteger mismatchCount = new AtomicInteger(0);
        DecimalFormat df = new DecimalFormat("#");
        CountDownLatch startSignal = new CountDownLatch(1);

        // This initial use of the formatter initialises its internal state, which could
        // subsequently be shared across clones. This is key to reproducing this specific
        // issue.
        String _ = df.format(Math.PI);

        try (var ex = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory())) {
            for (int i = 0; i < 5; i++) {
                final int finalI = i;
                // Each thread gets its own clone of df
                DecimalFormat threadDf = (DecimalFormat) df.clone();
                Runnable task = () -> {
                    try {
                        startSignal.await();
                        for (int j = 0; j < 1_000; j++) {
                            if (mismatchCount.get() > 0) {
                                // Exit early if mismatch has already occurred
                                break;
                            }

                            int value = finalI * j;
                            String dfString = threadDf.format(BigDecimal.valueOf(value));
                            String str = String.valueOf(value);
                            if (!str.equals(dfString)) {
                                mismatchCount.getAndIncrement();
                                System.err.println("mismatch: str = " + str + " dfString = " + dfString);
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                };
                ex.execute(task);
            }
            startSignal.countDown(); // let all tasks start working at the same time
        }
        assertEquals(0, mismatchCount.get());
    }
}
