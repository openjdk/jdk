/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 6857159
 * @summary local schedule failed with checkcast of Thread.currentThread()
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.*;

public class Test6857159 {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xbatch", "-XX:+PrintCompilation",
                                                                  "-XX:CompileOnly=Test$ct.run", "Test");
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldNotContain("COMPILE SKIPPED");
        analyzer.shouldContain("Test$ct0::run (16 bytes)");
    }
}

class Test extends Thread {
    static class ct0 extends Test {
        public void message() { }

        public void run() {
             message();
             ct0 ct = (ct0) Thread.currentThread();
             ct.message();
        }
    }
    static class ct1 extends ct0 {
        public void message() { }
    }
    static class ct2 extends ct0 {
        public void message() { }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 20000; i++) {
            Thread t = null;
            switch (i % 3) {
              case 0: t = new ct0(); break;
              case 1: t = new ct1(); break;
              case 2: t = new ct2(); break;
            }
            t.start();
            t.join();
        }
    }
}
