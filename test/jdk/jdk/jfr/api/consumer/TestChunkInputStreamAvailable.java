/*
 * Copyright (c) 2021 Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package jdk.jfr.api.consumer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;

/**
 * @test TestChunkInputStreamAvailable
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.TestChunkInputStreamAvailable
 */
public class TestChunkInputStreamAvailable {

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EventNames.JavaThreadStatistics)
             .withPeriod(Duration.ofMillis(100));
            r.start();
            File repository = Path.of(System.getProperty("jdk.jfr.repository")).toFile();
            for (int i = 0; i < 5; i++) {
                TimeUnit.SECONDS.sleep(1);
                try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
                    InputStream stream = snapshot.getStream(null, null);
                    File[] jfrs = repository.listFiles(f -> f.getName().endsWith(".jfr"));
                    Arrays.sort(jfrs);

                    int size = 0;
                    // the last file isn't counted in
                    for (int j = 0; j < jfrs.length - 1; j++) {
                        size += (int)jfrs[j].length();
                    }
                    Asserts.assertEquals(stream.available(), size);
                    int rc = new Random().nextInt(size);
                    int left = size - rc;
                    while (rc-- > 0) {
                         stream.read();
                    }
                    Asserts.assertEquals(stream.available(), left);
                }
            }
        }
    }
}
