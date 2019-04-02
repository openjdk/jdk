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
 * @bug 8221801
 * @library /test/lib
 * @summary Update src/java.base/share/legal/public_suffix.md
 */

import jdk.test.lib.Asserts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Versions {

    public static void main(String[] args) throws Exception {

        Path root = Path.of(System.getProperty("test.root"));
        Path jdk = Path.of(System.getProperty("test.jdk"));

        Path version = root.resolve("../../make/data/publicsuffixlist/VERSION");
        Path mdSrc = root.resolve("../../src/java.base/share/legal/public_suffix.md");
        Path mdImage = jdk.resolve("legal/java.base/public_suffix.md");

        // Files in src should either both exist or not
        if (!Files.exists(version) && !Files.exists(mdSrc)) {
            System.out.println("Source not available. Cannot proceed.");
            return;
        }

        String s1 = findURL(version);
        String s2 = findURL(mdSrc);

        Asserts.assertEQ(s1, s2);

        String s3 = findURL(mdImage);
        Asserts.assertEQ(s2, s3);
    }

    static Pattern URL_PATTERN = Pattern.compile(
            "(https://raw.githubusercontent.com.*?public_suffix_list.dat)");

    static String findURL(Path p) throws IOException  {
        return Files.lines(p)
                .map(Versions::matchURL)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    static String matchURL(String input) {
        Matcher m = URL_PATTERN.matcher(input);
        return m.find() ? m.group(1) : null;
    }
}
