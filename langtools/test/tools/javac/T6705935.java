/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6705935
 * @summary javac reports path name of entry in ZipFileIndex incorectly
 */

import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.tools.javac.file.*;

public class T6705935 {
    public static void main(String... args) throws Exception {
        new T6705935().run();
    }

    public void run() throws Exception {
        File java_home = new File(System.getProperty("java.home"));
        if (java_home.getName().equals("jre"))
            java_home = java_home.getParentFile();

        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        JavaFileManager fm = c.getStandardFileManager(null, null, null);
        for (JavaFileObject fo: fm.list(StandardLocation.PLATFORM_CLASS_PATH,
                                        "java.lang",
                                        Collections.singleton(JavaFileObject.Kind.CLASS),
                                        false)) {
            String p = fo.getName();
            int bra = p.indexOf("(");
            int ket = p.indexOf(")");
            //System.err.println(bra + "," + ket + "," + p.length());
            if (bra == -1 || ket != p.length() -1)
                throw new Exception("unexpected path: " + p + "[" + bra + "," + ket + "," + p.length());
            String part1 = p.substring(0, bra);
            String part2 = p.substring(bra + 1, ket);
            //System.err.println("[" + part1 + "|" + part2 + "]" + " " + java_home);
            if (part1.equals(part2) || !part1.startsWith(java_home.getPath()))
                throw new Exception("bad path: " + p);

        }
    }
}
