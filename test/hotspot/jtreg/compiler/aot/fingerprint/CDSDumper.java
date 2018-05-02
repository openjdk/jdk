/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package compiler.aot.fingerprint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

// Usage:
// java CDSDumper <classpath> <classlist> <archive> <heapsize> <class1> <class2> ...
public class CDSDumper {
    public static void main(String[] args) throws Exception {
        String classpath = args[0];
        String classlist = args[1];
        String archive = args[2];
        String heapsize = args[3];

        // Prepare the classlist
        FileOutputStream fos = new FileOutputStream(classlist);
        PrintStream ps = new PrintStream(fos);

        for (int i=4; i<args.length; i++) {
            ps.println(args[i].replace('.', '/'));
        }
        ps.close();
        fos.close();

        // Dump the archive
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            heapsize,
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-cp", classpath,
            "-XX:ExtraSharedClassListFile=" + classlist,
            "-XX:SharedArchiveFile=" + archive,
            "-Xshare:dump",
            "-Xlog:gc+heap+coops",
            "-Xlog:cds");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println("[stdout = " + output.getStdout() + "]");
        System.out.println("[stderr = " + output.getStderr() + "]");
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);
    }
}
