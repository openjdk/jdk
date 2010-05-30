/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5071352
 * @summary Make sure compiler closes all open files on Windows
 * @author Martin Buchholz
 *
 * @run main/othervm -Xms200m -Xmx200m CompileClose
 */

// -Xms120m is sufficient to inhibit a gc during a compile.
// -Xms200m leaves room for expansion and was used by the customer.

import java.io.*;

public class CompileClose {
    static void writeFile(String f, String contents) throws IOException {
        PrintStream s = new PrintStream(new FileOutputStream(f));
        s.println(contents);
        s.close();
    }

    static void rm(String filename) throws Exception {
        File f = new File(filename);
        f.delete();
        if (f.exists())
            throw new Exception(filename + ": couldn't remove");
    }

    static void clean() throws Exception {
        rm("tmpCompileClose.java");
        rm("tmpCompileClose.class");
        rm("tmpCompileClose.jar");
    }

    public static void main(String args[]) throws Exception {
        try {
            clean();
            main1();
        } finally {
            clean();
        }
    }

    static void main1() throws Exception {
        writeFile("tmpCompileClose.java",
                  "public class tmpCompileClose {}");
        // Any old jar file will do
        SameJVM.jar("cf", "tmpCompileClose.jar", "tmpCompileClose.java");
        System.gc(); // Inhibit gc during next compile
        SameJVM.javac("-cp", "tmpCompileClose.jar", "tmpCompileClose.java");
        // The following rm is the actual test!
        rm("tmpCompileClose.jar");
    }
}
