/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7051189 8023393
 * @summary Need to suppress info message if -Xcheck:jni is used with libjsig.so
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main XCheckJSig
 */

import java.io.File;
import java.util.Map;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

public class XCheckJSig {
    public static void main(String args[]) throws Throwable {

        System.out.println("Regression test for bugs 7051189 and 8023393");
        if (!Platform.isSolaris() && !Platform.isLinux() && !Platform.isOSX()) {
            System.out.println("Test only applicable on Solaris, Linux, and Mac OSX, skipping");
            return;
        }

        String jdk_path = System.getProperty("test.jdk");
        String os_arch = Platform.getOsArch();
        String libjsig;
        String env_var;
        if (Platform.isOSX()) {
            env_var = "DYLD_INSERT_LIBRARIES";
            libjsig = jdk_path + "/jre/lib/libjsig.dylib"; // jdk location
            if (!(new File(libjsig).exists())) {
                libjsig = jdk_path + "/lib/libjsig.dylib"; // jre location
            }
        } else {
            env_var = "LD_PRELOAD";
            libjsig = jdk_path + "/jre/lib/" + os_arch + "/libjsig.so"; // jdk location
            if (!(new File(libjsig).exists())) {
                libjsig = jdk_path + "/lib/" + os_arch + "/libjsig.so"; // jre location
            }
        }
        // If this test fails, these might be useful to know.
        System.out.println("libjsig: " + libjsig);
        System.out.println("osArch: " + os_arch);

        // Make sure the libjsig file exists.
        if (!(new File(libjsig).exists())) {
            System.out.println("File " + libjsig + " not found, skipping");
            return;
        }

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xcheck:jni", "-version");
        Map<String, String> env = pb.environment();
        env.put(env_var, libjsig);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("libjsig is activated");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder("-Xcheck:jni", "-verbose:jni", "-version");
        env = pb.environment();
        env.put(env_var, libjsig);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("libjsig is activated");
        output.shouldHaveExitValue(0);
    }
}
