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
 * @bug 8396943
 * @summary Redefine default interface methods during class loading
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar RedefineDefaultMethods
 */

import java.util.concurrent.atomic.AtomicInteger;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

public class RedefineDefaultMethods {

    // Repeated as a string for convenience so we don't have to
    // read the classfile bytes from disk.
    static final String original = """
        public interface RedefineIntf {
            int ORIGINAL_RETURN = 1;
            int NEW_RETURN = 2;
            public default int defaultMethod() {
                return ORIGINAL_RETURN;
            }
        }
        """;

    static final String redefined = """
        public interface RedefineIntf {
            int ORIGINAL_RETURN = 1;
            int NEW_RETURN = 2;
            public default int defaultMethod() {
                return NEW_RETURN;
            }
        }
        """;

    static final String IMPL = "Impl";

    static final String implClass = "public class Impl implements RedefineIntf {}";

    static final int LOADER_THREADS = 4;
    static volatile boolean stopped = false;
    static int redefineCount;
    static AtomicInteger loadCount = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        byte[] impl = InMemoryJavaCompiler.compile(IMPL, implClass);
        byte[] version1 = InMemoryJavaCompiler.compile("RedefineIntf", original);
        byte[] version2 = InMemoryJavaCompiler.compile("RedefineIntf", redefined);

        LoaderThread[] loaders = new LoaderThread[LOADER_THREADS];
        for (int i = 0; i < LOADER_THREADS; i++) {
            loaders[i] = new LoaderThread("loader-" + i, impl);
            loaders[i].start();
        }
        Redefiner redefiner = new Redefiner("redefiner", version1, version2);
        redefiner.start();
        Thread.sleep(60_000);
        stopped = true;
        for (int i = 0; i < LOADER_THREADS; i++) {
            loaders[i].join();
            if (loaders[i].failure != null) {
                throw new RuntimeException(loaders[i].getName() + " failed", loaders[i].failure);
            }
        }
        redefiner.join();
        if (redefiner.failure != null) {
            throw new RuntimeException("Redefiner failed", redefiner.failure);
        }
        System.out.println("Test completed with " + loadCount.get() + " loads, and " +
                           redefineCount + " redefinitions");
    }

    private static class LoaderThread extends Thread {
        Throwable failure;
        final byte[] bytes;
        LoaderThread(String name, byte[] bytes) {
            super(name);
            this.bytes = bytes;
        }
        @Override
        public void run() {
            try {
                while (!stopped) {
                    Class.forName(IMPL, false, new Loader(bytes));
                    loadCount.getAndIncrement();
                }
            }
            catch (Throwable t) {
                failure = t;
            }
        }
    }

    private static final class Redefiner extends Thread {
        Throwable failure;
        final byte[] version1;
        final byte[] version2;
        Redefiner(String name, byte[] version1, byte[] version2) {
            super(name);
            this.version1 = version1;
            this.version2 = version2;
        }
        @Override
        public void run() {
            try {
                while (!stopped) {
                    RedefineClassHelper.redefineClass(RedefineIntf.class, version2);
                    redefineCount++;
                    RedefineClassHelper.redefineClass(RedefineIntf.class, version1);
                    redefineCount++;
                }
            }
            catch (Throwable t) {
                failure = t;
            }
        }
    }

    private static final class Loader extends ClassLoader {
        private final byte[] bytes;

        Loader(byte[] bytes) {
            super(RedefineDefaultMethods.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(IMPL)) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.loadClass(name, resolve);
        }
    }
}
