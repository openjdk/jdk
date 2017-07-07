/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7025809 8028543 6415644 8028544 8029942
 * @summary Test latest, latestSupported, underscore as keyword, etc.
 * @author  Joseph D. Darcy
 * @modules java.compiler
 *          jdk.compiler
 */

import java.util.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

/**
 * Verify latest[Supported] behavior.
 */
public class TestSourceVersion {
    public static void main(String... args) {
        testLatestSupported();
        testVersionVaryingKeywords();
    }

    private static void testLatestSupported() {
        if (SourceVersion.latest() != RELEASE_10 ||
            SourceVersion.latestSupported() != RELEASE_10)
            throw new RuntimeException("Unexpected release value(s) found:\n" +
                                       "latest:\t" + SourceVersion.latest() + "\n" +
                                       "latestSupported:\t" + SourceVersion.latestSupported());
    }

    private static void testVersionVaryingKeywords() {
        Map<String, SourceVersion> keyWordStart =
            Map.of("strictfp", RELEASE_2,
                   "assert",   RELEASE_4,
                   "enum",     RELEASE_5,
                   "_",        RELEASE_9);

        for (Map.Entry<String, SourceVersion> entry : keyWordStart.entrySet()) {
            String key = entry.getKey();
            SourceVersion value = entry.getValue();

            check(true, isKeyword(key), "keyword", latest());
            check(false, isName(key),   "name",    latest());

            for(SourceVersion version : SourceVersion.values()) {
                boolean isKeyword = version.compareTo(value) >= 0;

                check(isKeyword,  isKeyword(key, version), "keyword", version);
                check(!isKeyword, isName(key, version),    "name",    version);
            }
        }
    }

    private static void check(boolean result, boolean expected,
                              String message, SourceVersion version) {
        if (result != expected) {
            throw new RuntimeException("Unexpected " + message +  "-ness of _ on " + version);
        }
    }
}
