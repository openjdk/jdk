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

/*
 * @test
 * @bug 7197071
 * @summary Makefiles for various security providers aren't including
 *          the default manifest.
 */
import java.net.*;
import java.io.*;

/**
 * When the Java specification version is incremented, all of the providers
 * must be recompiled with the proper implementation version to match.
 */
public class CheckManifestForRelease {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        checkFileManifests();
    }

    /*
     * Iterate over the files of interest: JCE framework and providers
     */
    static private void checkFileManifests() throws Exception {
        System.out.println("=============");
        String libDirName = System.getProperty("java.home", ".") + "/lib";
        String extDirName = libDirName + "/ext";

        System.out.println("Checking Manifest in directory: \n    " +
            extDirName);

        /*
         * Current list of JCE providers, all of which currently live in
         * the extensions directory.  Add if more are created.
         */
        String[] providers = new String[]{
            "sunjce_provider.jar",
            "sunec.jar",
            "sunmscapi.jar",
            "sunpkcs11.jar",
            "ucrypto.jar"
        };

        checkManifest(libDirName, "jce.jar");
        for (String provider : providers) {
            checkManifest(extDirName, provider);
        }
        System.out.println("Passed.");
    }

    // Helper method to format the URL properly.
    static private String formatURL(String dir, String file) {
        return "jar:file:///" + dir + "/" + file + "!/";
    }

    static private String specVersion =
        System.getProperty("java.specification.version");

    /*
     * Test the root cause, which is that there were no manifest values
     * for many of the providers, and for those that had them, there was
     * no test to make sure that the impl version was appropriate for
     * the spec version.
     */
    static private void checkManifest(String dir, String file)
            throws Exception {

        System.out.println("Checking: " + file);

        String url = formatURL(dir, file);
        JarURLConnection urlc =
            (JarURLConnection) (new URL(url).openConnection());

        String implVersion;
        try {
            implVersion = urlc.getManifest().getMainAttributes().getValue(
                "Implementation-Version");
        } catch (FileNotFoundException e) {
            /*
             * If the file doesn't exist (e.g. mscapi on solaris),
             * skip it. If there are other problems, fail out.
             */
            System.out.println("    " + file + " not found, skipping...");
            return;
        }

        if (implVersion == null) {
            throw new Exception(
                "Implementation-Version not found in Manifest");
        }

        if (!implVersion.startsWith(specVersion)) {
            throw new Exception(
                "Implementation-Version does not match " +
                "Specification-Version");
        }
    }
}
