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

/* @test
 * @summary Test of a lazy map application
 * @enablePreview
 * @run junit DemoMapTest
 */

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DemoMapTest {

    static class OrderController{}

    // NEW:
    static final Map<String, OrderController> ORDERS
            = Map.ofLazy(
                    Set.of("Customers", "Internal", "Testing"),
                    _ -> new OrderController()
    );

    public static OrderController orders() {
        String groupName = Thread.currentThread().getThreadGroup().getName();
        return ORDERS.get(groupName);
    }

    @Test
    void orderController() throws InterruptedException {
        Thread t = Thread.ofPlatform()
                .group(new ThreadGroup("Customers"))
                .start(() -> {
                    String groupName = Thread.currentThread().getThreadGroup().getName();
                    OrderController orderController = ORDERS.get(groupName);
                    assertNotNull(orderController);
                });
        t.join();
    }

    private static final Map<Integer, String> SERVER_ERROR_PAGES = Map.ofLazy(
            Set.of(500, 501, 502, 503, 504, 505, 506, 507, 508, 510, 511),
            e -> {
                try {
                    return Files.readString(Path.of("server_error_" + e + ".html"));
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });

    private static String htmlServerErrorPage(int errorCode) {
        return SERVER_ERROR_PAGES.get(errorCode); // Eligible for constant folding
    }

    @Test
    void page500() {
        String page = htmlServerErrorPage(500); // Constant folds
        assertEquals(DEFAULT_FS_MSG, page);
    }

    static final String DEFAULT_FS_MSG = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <title>Internal Server Error (500)</title>
                <link rel="stylesheet" href="style.css">
                <script src="script.js"></script>
              </head>
              <body>
                There was a general problem with the server, code 500
              </body>
            </html>
            """;

    @BeforeAll
    public static void setup() throws IOException {
        var file = Path.of("server_error_500.html");
        if (Files.notExists(file)) {
            Files.createFile(file);
            Files.writeString(file, DEFAULT_FS_MSG);
        }
        assertEquals(DEFAULT_FS_MSG, Files.readString(file));
    }

    @AfterAll
    public static void cleanUp() throws IOException {
        var file = Path.of("server_error_500.html");
        Files.deleteIfExists(file);
    }

}
