/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ThreadFactory;

@SuppressWarnings("removal")
public class Virtual implements ThreadFactory {

    static {
        try {
            // This property is used by ProcessTools and some tests
            System.setProperty("test.thread.factory", "Virtual");
        } catch (Throwable t) {
            // might be thrown by security manager
        }
    }

    static final ThreadFactory VIRTUAL_TF = Thread.ofVirtual().factory();

    private static volatile Throwable uncaughtException;

    private static synchronized void handleException(Throwable e) {
        if (e instanceof ThreadDeath) {
            return;
        }
        e.printStackTrace(System.err);
        uncaughtException = e;
    }

    @Override
    public Thread newThread(Runnable task) {
        return VIRTUAL_TF.newThread(() -> {
                // Temporary workaround until CODETOOLS-7903526 is fixed in jtreg.
                // The jtreg run main() in the ThreadGroup and catch only exceptions in this group.
                // However with test thread factory all platform threads started from virtual main thread
                // don't belong to any group and need global handler.
                try {
                    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                            handleException(e);
                        });
                } catch (Throwable t) {
                    // might be thrown by security manager
                }


                try {
                    task.run();

                    if (uncaughtException != null) {
                        throw new RuntimeException(uncaughtException);
                    }
                } finally {
                    synchronized (Virtual.class) {
                        uncaughtException = null;
                        Thread.setDefaultUncaughtExceptionHandler(null);
                    }
                }
            });
    }

}
