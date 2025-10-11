
/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, NTT DATA
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

import java.lang.invoke.MethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithVirtualThread extends LingeredApp implements Runnable {

    private static final String THREAD_NAME = "target thread";

    private static final MethodHandle hndSleep;

    private static final CountDownLatch signal = new CountDownLatch(1);

    static {
        var linker = Linker.nativeLinker();
        var func = linker.defaultLookup()
                         .find("sleep")
                         .get();
        var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
        hndSleep = linker.downcallHandle(func, desc);
    }

    @Override
    public void run() {
        signal.countDown();
        Thread.yield();
        try {
            hndSleep.invoke(3600);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void main(String[] args) {
        try {
            Thread.ofVirtual()
                  .name(THREAD_NAME)
                  .start(new LingeredAppWithVirtualThread());

            signal.await();
            LingeredApp.main(args);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
