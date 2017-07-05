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
 * @bug 7167142
 * @summary Warn if unused .hotspot_compiler file is present
 * @library /testlibrary
 */

import java.io.PrintWriter;
import com.oracle.java.testlibrary.*;

public class CompilerConfigFileWarning {
    public static void main(String[] args) throws Exception {
        String vmVersion = System.getProperty("java.vm.version");
        if (vmVersion.toLowerCase().contains("debug") || vmVersion.toLowerCase().contains("jvmg")) {
            System.out.println("Skip on debug builds since we'll always read the file there");
            return;
        }

        PrintWriter pw = new PrintWriter(".hotspot_compiler");
        pw.println("aa");
        pw.close();

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("warning: .hotspot_compiler file is present but has been ignored.  Run with -XX:CompileCommandFile=.hotspot_compiler to load the file.");
    }
}
