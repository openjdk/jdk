/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8003255
 * @compile -XDignore.symbol.file AddAndUpdateProfile.java
 * @run main AddAndUpdateProfile
 * @summary Basic test of jar tool "p" option to add or update the Profile
 *    attribute in the main manifest of a JAR file
 */

import java.util.jar.*;
import static java.util.jar.Attributes.Name.*;
import java.nio.file.*;
import java.io.IOException;

import sun.tools.jar.Main;

public class AddAndUpdateProfile {
    static boolean doJar(String... args) {
        System.out.print("jar");
        for (String arg: args)
            System.out.print(" " + arg);
        System.out.println("");

        Main jartool = new Main(System.out, System.err, "jar");
        return jartool.run(args);
    }

    static void jar(String... args) {
        if (!doJar(args))
            throw new RuntimeException("jar command failed");
    }

    static void jarExpectingFail(String... args) {
        if (doJar(args))
            throw new RuntimeException("jar command not expected to succeed");
    }

    static void checkMainAttribute(String jarfile, Attributes.Name name,
                                   String expectedValue)
        throws IOException
    {
        try (JarFile jf = new JarFile(jarfile)) {
            Manifest mf = jf.getManifest();
            if (mf == null && expectedValue != null)
                throw new RuntimeException("Manifest not found");
            if (mf != null) {
                String actual = mf.getMainAttributes().getValue(name);
                if (actual != null) {
                    if (!actual.equals(expectedValue))
                        throw new RuntimeException("Profile attribute has unexpected value");
                } else {
                    if (expectedValue != null)
                        throw new RuntimeException("Profile attribute should not be present");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path entry = Files.createFile(Paths.get("xfoo"));
        String jarfile = "xFoo.jar";
        try {

            // create JAR file with Profile attribute
            jar("cfp", jarfile, "compact1", entry.toString());
            checkMainAttribute(jarfile, PROFILE, "compact1");

            // attempt to create JAR file with Profile attribute and bad value
            jarExpectingFail("cfp", jarfile, "garbage", entry.toString());
            jarExpectingFail("cfp", jarfile, "Compact1", entry.toString());
            jarExpectingFail("cfp", jarfile, "COMPACT1", entry.toString());

            // update value of Profile attribute
            jar("ufp", jarfile, "compact2");
            checkMainAttribute(jarfile, PROFILE, "compact2");

            // attempt to update value of Profile attribute to bad value
            // (update should not change the JAR file)
            jarExpectingFail("ufp", jarfile, "garbage");
            checkMainAttribute(jarfile, PROFILE, "compact2");
            jarExpectingFail("ufp", jarfile, "COMPACT1");
            checkMainAttribute(jarfile, PROFILE, "compact2");

            // create JAR file with both a Main-Class and Profile attribute
            jar("cfep", jarfile, "Foo", "compact1", entry.toString());
            checkMainAttribute(jarfile, MAIN_CLASS, "Foo");
            checkMainAttribute(jarfile, PROFILE, "compact1");

            // update value of Profile attribute
            jar("ufp", jarfile, "compact2");
            checkMainAttribute(jarfile, PROFILE, "compact2");

            // create JAR file without Profile attribute
            jar("cf", jarfile, entry.toString());
            checkMainAttribute(jarfile, PROFILE, null);

            // update value of Profile attribute
            jar("ufp", jarfile, "compact3");
            checkMainAttribute(jarfile, PROFILE, "compact3");

        } finally {
            Files.deleteIfExists(Paths.get(jarfile));
            Files.delete(entry);
        }
    }

}
