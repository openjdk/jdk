/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test for value class redefinition
 * @comment The code is based on test/jdk/java/lang/instrument/RedefineNestmateAttr
 * @comment modified for value classes.
 *
 * @library /test/lib
 * @modules java.compiler
 *          java.instrument
 * @enablePreview
 * @run main RedefineClassHelper
 * @compile Host/Host.java
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class*=trace RedefineValueClass Host
 * @compile HostA/Host.java
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class*=trace RedefineValueClass HostA
 * @compile HostAB/Host.java
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class*=trace RedefineValueClass HostAB
 * @compile HostI/Host.java
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class*=trace RedefineValueClass HostI
 */

/* Test Description

The basic test class is called Host.
Each variant of the class is defined in source code in its own directory i.e.

Host/Host.java defines zero fields
Class HostA/Host.java has field "int A"
Class HostAB/Host.java has fields "int A" and "long B" (in that order)
Class HostI/Host.java is an instance class with zero fields
etc.

Each Host class has the form:

  public value class Host {
    // fields here
    public static String getID() { return "<directory name>/Host.java"; }

    public int m() {
        return 1; // original class
    }

    public Host(int A, long B, char C) {
         ...
    }
  }

The only exception is class in HostI dir which is instance class.

Under each directory is a directory "redef" with a modified version of the Host
class that changes the ID to e.g. Host/redef/Host.java, and the method m()
returns 2. This allows us to check we have the redefined class loaded.

Using Host' to represent the redefined version we test different redefinition combinations.

We can only directly load one class Host per classloader, so to run all the
groups we either need to use new classloaders, or we reinvoke the test
requesting a different primary directory. We chose the latter using
multiple @run tags. So we preceed as follows:

 @compile Host/Host.java
 @run RedefineValueClass Host
 @compile HostA/Host.java  - replaces previous Host.class
 @run RedefineValueClass HostA
 @compile HostAB/Host.java  - replaces previous Host.class
 @run RedefineValueClass HostAB
etc.

Within the test we directly compile redefined versions of the classes,
using CompilerUtil, and then read the .class file directly as a byte[]
to use with the RedefineClassHelper.
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.compiler.CompilerUtils;
import static jdk.test.lib.Asserts.assertTrue;

public class RedefineValueClass {

    static final Path SRC = Paths.get(System.getProperty("test.src"));
    static final Path DEST = Paths.get(System.getProperty("test.classes"));

    public static void main(String[] args) throws Throwable {
        String origin = args[0];
        System.out.println("Testing original Host class from " + origin);

        // Make sure the Host class loaded directly is an original version
        // and from the expected location. Use a ctor common to all Host
        // classes.
        Host h = new Host(3, 4, 'a');
        assertTrue(h.m() == 1);
        assertTrue(Host.getID().startsWith(origin + "/"));

        String[] badTransforms = null;  // directories of bad classes
        String[] goodTransforms = null; // directories of good classes

        switch (origin) {
        case "Host":
            badTransforms = new String[] {
                "HostI", // value class to instance class
                "HostA"  // add field
            };
            goodTransforms = new String[] {
                origin
            };
            break;

        case "HostA":
            badTransforms = new String[] {
                "Host",   // remove field
                "HostAB", // add field
                "HostB"   // change field
            };
            goodTransforms = new String[] {
                origin
            };
            break;

        case "HostAB":
            badTransforms = new String[] {
                "HostA",   // remove field
                "HostABC", // add field
                "HostAC",  // change fields
                "HostBA"   // reorder fields
            };
            goodTransforms = new String[] {
                origin,
            };
            break;

        case "HostI":  // instance class
            badTransforms = new String[] {
                "Host",  // instance class to value class
            };
            break;

        default:
            throw new RuntimeException("Unknown test directory: " + origin);
        }

        // Compile and check bad transformations
        checkBadTransforms(Host.class, badTransforms);

        // Compile and check good transformations
        if (goodTransforms != null) {
            checkGoodTransforms(Host.class, goodTransforms);
        }
    }

    static void checkGoodTransforms(Class<?> c, String[] dirs) throws Throwable {
        for (String dir : dirs) {
            dir += "/redef";
            System.out.println("Trying good retransform from " + dir);
            byte[] buf = bytesForHostClass(dir);
            RedefineClassHelper.redefineClass(c, buf);

            // Test redefintion worked
            Host h = new Host(3, 4, 'a');
            assertTrue(h.m() == 2);
            System.out.println("Redefined ID: " + Host.getID());
            assertTrue(Host.getID().startsWith(dir));
        }
    }

    static void checkBadTransforms(Class<?> c, String[] dirs) throws Throwable {
        for (String dir : dirs) {
            dir += "/redef";
            System.out.println("Trying bad retransform from " + dir);
            byte[] buf = bytesForHostClass(dir);
            try {
                RedefineClassHelper.redefineClass(c, buf);
                throw new RuntimeException("Retransformation from directory " + dir +
                                " succeeded unexpectedly");
            }
            catch (UnsupportedOperationException uoe) {
                System.out.println("Got expected UnsupportedOperationException " + uoe);
            }
        }
    }

    static byte[] bytesForHostClass(String dir) throws Throwable {
        compile(dir);
        Path clsfile = DEST.resolve(dir).resolve("Host.class");
        System.out.println("Reading bytes from " + clsfile);
        return Files.readAllBytes(clsfile);
    }

    static void compile(String dir) throws Throwable {
        Path src = SRC.resolve(dir);
        Path dst = DEST.resolve(dir);
        System.out.println("Compiling from: " + src + "\n" +
                           "            to: " + dst);
        CompilerUtils.compile(src, dst,
                              false /* don't recurse */,
                              "--enable-preview",
                              "--source", String.valueOf(Runtime.version().feature()));
    }
}
