/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test ClassResolutionTest
 * @bug 8144874
 * @library /testlibrary
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run driver ClassResolutionTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

public class ClassResolutionTest {

    public static class ClassResolutionTestMain {

        public static class Thing1 {
            public static int getThingNumber() {
                return 1;
            }
        };
        public static class Thing1Handler {
            public static int getThingNumber() {
                return Thing1.getThingNumber();
            }
        };

        public static void main(String... args) throws Exception {
            int x = Thing1Handler.getThingNumber();
            System.out.println("ThingNumber: "+Integer.toString(x));
        }
    }

    public static void main(String... args) throws Exception {

        // (1) class+resolve should turn on.
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:class+resolve=debug",
                                                                  ClassResolutionTestMain.class.getName());
        OutputAnalyzer o = new OutputAnalyzer(pb.start());
        o.shouldContain("[class,resolve] ClassResolutionTest$ClassResolutionTestMain$Thing1Handler ClassResolutionTest$ClassResolutionTestMain$Thing1");
        o.shouldContain("[class,resolve] resolve JVM_CONSTANT_MethodHandle");

        // (2) class+resolve should turn off.
        pb = ProcessTools.createJavaProcessBuilder("-Xlog:class+resolve=debug",
                                                   "-Xlog:class+resolve=off",
                                                   ClassResolutionTestMain.class.getName());
        o = new OutputAnalyzer(pb.start());
        o.shouldNotContain("[class,resolve]");

        // (3) TraceClassResolution should turn on.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+TraceClassResolution",
                                                   ClassResolutionTestMain.class.getName());
        o = new OutputAnalyzer(pb.start());
        o.shouldContain("[class,resolve] ClassResolutionTest$ClassResolutionTestMain$Thing1Handler ClassResolutionTest$ClassResolutionTestMain$Thing1");

        // (4) TraceClassResolution should turn off.
        pb = ProcessTools.createJavaProcessBuilder("-Xlog:class+resolve=debug",
                                                   "-XX:-TraceClassResolution",
                                                   ClassResolutionTestMain.class.getName());
        o = new OutputAnalyzer(pb.start());
        o.shouldNotContain("[class,resolve]");

    };

}
