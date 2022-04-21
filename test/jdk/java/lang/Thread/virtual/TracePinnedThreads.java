/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Basic test of debugging option to trace pinned threads
 * @compile --enable-preview -source ${jdk.version} TracePinnedThreads.java
 * @run main/othervm --enable-preview -Djdk.tracePinnedThreads=full TracePinnedThreads
 * @run main/othervm --enable-preview -Djdk.tracePinnedThreads=short TracePinnedThreads
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public class TracePinnedThreads {
    static final Object lock = new Object();

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    long nanos = Duration.ofSeconds(1).toNanos();
                    LockSupport.parkNanos(nanos);
                }
            }).join();
            System.out.flush();
        } finally {
            System.setOut(original);
        }

        String output = new String(baos.toByteArray()); // default charset
        System.out.println(output);

        String expected = "<== monitors:1";
        if (!output.contains(expected)) {
            throw new RuntimeException("expected: \"" + expected + "\"");
        }
    }
}
