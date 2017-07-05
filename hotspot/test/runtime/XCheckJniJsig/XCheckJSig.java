/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @library /testlibrary
 * @run main XCheckJSig
 */

import java.util.*;
import com.oracle.java.testlibrary.*;

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
            libjsig = jdk_path + "/jre/lib/server/libjsig.dylib";
            env_var = "DYLD_INSERT_LIBRARIES";
        } else {
            libjsig = jdk_path + "/jre/lib/" + os_arch + "/libjsig.so";
            env_var = "LD_PRELOAD";
        }
        String java_program;
        if (Platform.isSolaris()) {
            // On Solaris, need to call the 64-bit Java directly in order for
            // LD_PRELOAD to work because libjsig.so is 64-bit.
            java_program = jdk_path + "/jre/bin/" + os_arch + "/java";
        } else {
            java_program = JDKToolFinder.getJDKTool("java");
        }
        // If this test fails, these might be useful to know.
        System.out.println("libjsig: " + libjsig);
        System.out.println("osArch: " + os_arch);
        System.out.println("java_program: " + java_program);

        ProcessBuilder pb = new ProcessBuilder(java_program, "-Xcheck:jni", "-version");
        Map<String, String> env = pb.environment();
        env.put(env_var, libjsig);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("libjsig is activated");
        output.shouldHaveExitValue(0);

        pb = new ProcessBuilder(java_program, "-Xcheck:jni", "-verbose:jni", "-version");
        env = pb.environment();
        env.put(env_var, libjsig);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("libjsig is activated");
        output.shouldHaveExitValue(0);
    }
}
