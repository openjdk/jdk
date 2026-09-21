/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8383525
 * @modules jdk.localedata
 * @summary Make sure that input skeleton init code is thread safe
 * @run junit SkeletonRaceTest
 */

import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class SkeletonRaceTest {
    @Test
    void testSkeletonRace() {
        // Without the fix, LocaleResources throws an NPE
        for (int run = 0; run < 10; run++) {
            assertDoesNotThrow(this::doRaceTest);
        }
    }

    private void doRaceTest() throws InterruptedException, ExecutionException {
        Locale[] locales = Locale.getAvailableLocales();
        int threads = 50;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                Locale locale = locales[i % locales.length];
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                        "yMd", IsoChronology.INSTANCE, locale);
                    return null;
                }));
            }

            ready.await();
            go.countDown();
            for (Future<?> f : futures) f.get();
        }
    }
}
