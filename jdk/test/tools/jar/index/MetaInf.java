/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4408526
 * @summary Index the non-meta files in META-INF, such as META-INF/services.
 */

import java.io.*;
import java.util.jar.*;
import sun.tools.jar.Main;

public class MetaInf {

    static String jarName = "a.jar";
    static String INDEX = "META-INF/INDEX.LIST";
    static String SERVICES = "META-INF/services";
    static String contents =
        System.getProperty("test.src") + File.separatorChar + "jarcontents";

    // Options passed to "jar" command.
    static String[] jarArgs1 = new String[] {
        "cf", jarName, "-C", contents, SERVICES
    };
    static String[] jarArgs2 = new String[] {
        "i", jarName
    };

    public static void main(String[] args) throws IOException {

        // Create a jar to be indexed.
        Main jarTool = new Main(System.out, System.err, "jar");
        if (!jarTool.run(jarArgs1)) {
            throw new Error("Could not create jar file.");
        }

        // Index the jar.
        jarTool = new Main(System.out, System.err, "jar");
        if (!jarTool.run(jarArgs2)) {
            throw new Error("Could not index jar file.");
        }

        // Read the index.  Verify that META-INF/services is indexed.
        JarFile f = new JarFile(jarName);
        BufferedReader index =
            new BufferedReader(
                    new InputStreamReader(
                            f.getInputStream(f.getJarEntry(INDEX))));
        String line;
        while ((line = index.readLine()) != null) {
            if (line.equals(SERVICES)) {
                return;
            }
        }
        throw new Error(SERVICES + " not indexed.");
    }
}
