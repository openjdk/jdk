/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static jdk.test.lib.Asserts.assertTrue;

/*
 * @test
 * @bug 8392031
 * @summary Make sure that ThreadSnapshot::initialize does not crash JVM
 * @requires vm.continuations
 * @library /test/lib
 * @modules java.management
 * @run main/othervm -Djdk.virtualThreadScheduler.parallelism=8 ThreadSnapshotRaceTest
 */
public class ThreadSnapshotRaceTest {

    private static volatile boolean SHOULD_STOP = false;

    public static void main(String[] args) throws Exception {

        Thread producer = new Thread(() -> {
            AtomicLong counter = new AtomicLong();
            while (!SHOULD_STOP) {
                long c = counter.incrementAndGet();
                Thread.ofVirtual().name("vthread").start(() -> {
                    counter.decrementAndGet();
                });
                if (c >= 1_000_000) {
                    do {
                        try {
                            Thread.sleep(50);
                        } catch (Exception e) {
                        }
                    } while (!SHOULD_STOP && counter.get() > 0);
                }
            }
        });
        producer.start();

        Thread consumer = new Thread(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            long[] ids = carrierIds(bean);
            while (!SHOULD_STOP && ids.length == 0) {
                ids = carrierIds(bean);
            }
            while (!SHOULD_STOP) {
                ThreadInfo[] infos = bean.getThreadInfo(ids);
                assertTrue(infos.length > 0);
            }
        });
        consumer.start();

        Thread.sleep(10_000);
        SHOULD_STOP = true;
        consumer.join();
        producer.join();
    }

    static long[] carrierIds(ThreadMXBean bean) {
        long[] all = bean.getAllThreadIds();
        long[] carriers = Arrays.stream(bean.getThreadInfo(all))
                .filter(Objects::nonNull)
                .filter(ti -> ti.getThreadName().startsWith("ForkJoinPool-1-worker"))
                .mapToLong(ThreadInfo::getThreadId)
                .toArray();
        return carriers;
    }
}
