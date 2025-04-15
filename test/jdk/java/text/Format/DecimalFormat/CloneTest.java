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
 * @summary Check parseStrict correctness for DecimalFormat.equals()
 * @run junit CloneTest
 */

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CloneTest {
    // Specifically tests that when DecimalFormat is cloned after use with
    // a long double/BigDecimal, clones will be independent. This is not an
    // exhaustive test.
    @Test
    public void testCloneIndependence() {
        AtomicInteger mismatchCount = new AtomicInteger(0);
        DecimalFormat df = new DecimalFormat("#");
        String _ = df.format(Math.PI); // initial use of formatter
        try (var ex = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory())) {
            for (int i = 0; i < 50; i++) {
                // each thread gets its own clone of df
                DecimalFormat threadDf = (DecimalFormat) df.clone();
                Runnable task = () -> {
                    for (int j = 0; j < 1000000; j++) {
                        String dfString = threadDf.format(BigDecimal.valueOf(j));
                        String str1 = String.valueOf(j);
                        if (!str1.equals(dfString)) {
                            System.err.println("mismatch: str = " + str1 + " dfString = " + dfString);
                            mismatchCount.incrementAndGet();
                            break;
                        }
                    }
                };
                ex.execute(task);
            }
        }
        assertEquals(0, mismatchCount.get());
    }
}
