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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.whitebox.WhiteBox;

public class LingeredAppWithUnmountedVirtualThread extends LingeredApp {

    // Referenced only from the virtual thread's stack.
    public static class ChunkReferenced {
    }

    private static final CountDownLatch started = new CountDownLatch(1);

    public static final String ADDR_FILE_PROPERTY = "test.chunk.address.file";

    // only the virtual thread's frame holds the object strongly
    private static WeakReference<ChunkReferenced> chunkReference;

    // runs after LingeredApp's own System.gc(), then hands the address to the driver
    private static void publishAddress(Path addressFile) {
        try {
            while (!isReady()) {
                Thread.sleep(10);
            }
            long address = WhiteBox.getWhiteBox().getObjectAddress(chunkReference.get());
            chunkReference.clear();
            Properties addresses = new Properties();
            addresses.setProperty("chunkReferenced", String.format("0x%x", address));
            Path tmp = Path.of(addressFile + ".tmp");
            try (var out = Files.newOutputStream(tmp)) {
                addresses.store(out, null);
            }
            Files.move(tmp, addressFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            Thread thread = Thread.ofVirtual().name("parked thread").start(() -> {
                ChunkReferenced ref = new ChunkReferenced();
                chunkReference = new WeakReference<>(ref);
                started.countDown();
                try {
                    Thread.sleep(3600_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // keeps ref alive through the sleep, a dead local is dropped from the oop map
                Reference.reachabilityFence(ref);
            });
            started.await();
            while (thread.getState() != Thread.State.TIMED_WAITING) {
                Thread.sleep(10);
            }
            // Transform the chunk so it gets its bitmap.
            System.gc();
            String addressFile = System.getProperty(ADDR_FILE_PROPERTY);
            if (addressFile != null) {
                Thread.ofPlatform().daemon().start(() -> publishAddress(Path.of(addressFile)));
            }
            LingeredApp.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
