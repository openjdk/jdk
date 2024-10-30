
/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithFFMUpcall extends LingeredApp {

    public static final String THREAD_NAME = "Upcall thread";

    private static final Object lockObj = new Object();

    private static final CountDownLatch signal = new CountDownLatch(1);

    static {
        System.loadLibrary("upcall");
    }

    public static void upcall() {
        signal.countDown();
        synchronized(lockObj) {
        }
    }

    public static long createFunctionPointerForUpcall() throws NoSuchMethodException, IllegalAccessException {
        var mh = MethodHandles.lookup()
                              .findStatic(LingeredAppWithFFMUpcall.class, "upcall", MethodType.methodType(void.class));
        var stub = Linker.nativeLinker()
                         .upcallStub(mh, FunctionDescriptor.ofVoid(), Arena.global());
        return stub.address();
    }

    public static native void callJNI(long upcallAddr);

    public static void main(String[] args) {
        try {
            long upcallAddr = createFunctionPointerForUpcall();
            var upcallThread = new Thread(() -> callJNI(upcallAddr), THREAD_NAME);
            synchronized(lockObj) {
                upcallThread.start();
                signal.await();
                LingeredApp.main(args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
