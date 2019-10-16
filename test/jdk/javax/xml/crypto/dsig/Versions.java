/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8232357
 * @library /test/lib
 * @summary Compare version info of Santuario to legal notice
 */

import jdk.test.lib.Asserts;

import java.nio.file.Files;
import java.nio.file.Path;

public class Versions {

    public static void main(String[] args) throws Exception {

        Path src = Path.of(System.getProperty("test.root"),
                "../../src/java.xml.crypto");
        Path legal = Path.of(System.getProperty("test.jdk"), "legal");

        Path provider = src.resolve(
                "share/classes/org/jcp/xml/dsig/internal/dom/XMLDSigRI.java");

        Path mdInSrc = src.resolve(
                "share/legal/santuario.md");
        Path mdInImage = legal.resolve(
                "java.xml.crypto/santuario.md");

        // Files in src should either both exist or not
        if (!Files.exists(provider) && !Files.exists(mdInSrc)) {
            System.out.println("Source not available. Cannot proceed.");
            return;
        }

        // The line containing the version number looks like
        // // Apache Santuario XML Security for Java, version n.n.n
        String s1 = Files.lines(provider)
                .filter(s -> s.contains(
                        "// Apache Santuario XML Security for Java, version "))
                .findFirst()
                .get()
                .replaceFirst(".* ", ""); // keep chars after the last space

        // The first line of this file should look like
        // ## Apache Santuario v2.1.3
        String s2 = Files.lines(mdInSrc)
                .findFirst()
                .get()
                .replace("## Apache Santuario v", "");

        Asserts.assertEQ(s1, s2);

        if (Files.exists(legal)) {
            Asserts.assertTrue(Files.mismatch(mdInSrc, mdInImage) == -1);
        } else {
            System.out.println("Warning: skip image compare. Exploded build?");
        }
    }
}
