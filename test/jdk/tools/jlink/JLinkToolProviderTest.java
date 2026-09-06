/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.spi.ToolProvider;

/*
 * @test
 * @modules jdk.jlink
 * @run main/othervm JLinkToolProviderTest
 */
public class JLinkToolProviderTest {
    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );

    private static void checkJlinkOptions(String... options) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        JLINK_TOOL.run(pw, pw, options);
    }

    private static void checkConcurrentAccess(int count) throws Exception {
        Phaser startBarrier = new Phaser(count);

        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<String>> futures = IntStream.range(0, count).mapToObj(idx -> executor.submit(() -> {
                startBarrier.arriveAndAwaitAdvance();

                StringWriter out = new StringWriter();
                StringWriter err = new StringWriter();
                int code = JLINK_TOOL.run(new PrintWriter(out), new PrintWriter(err),
                        "--add-modules", "java.base",
                        "--output", Path.of(".").resolve("image-" + idx).toString());
                return code + ":" + out.toString().trim() + ":" + err.toString().trim();
            })).toList();

            for (Future<String> future : futures) {
                String result = future.get();
                if (!"0::".equals(result)) {
                    throw new AssertionError(result);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        checkJlinkOptions("--help");
        checkJlinkOptions("--list-plugins");
        checkConcurrentAccess(Math.max(2, Runtime.getRuntime().availableProcessors() / 4));
    }
}
