/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.util.jar;

import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class VersionedStream {
    private static final String META_INF_VERSIONS = "META-INF/versions/";

    /**
     * Returns a stream of versioned entries, derived from the base names of
     * all entries in a multi-release {@code JarFile} that are present either in
     * the base directory or in any versioned directory with a version number
     * less than or equal to the {@code Runtime.Version::major} that the
     * {@code JarFile} was opened with.  These versioned entries are aliases
     * for the real entries -- i.e. the names are base names and the content
     * may come from a versioned directory entry.  If the {@code jarFile} is not
     * a multi-release jar, a stream of all entries is returned.
     *
     * @param jf the input JarFile
     * @return stream of entries
     * @since 9
     */
    public static Stream<JarEntry> stream(JarFile jf) {
        if (jf.isMultiRelease()) {
            int version = jf.getVersion().major();
            return jf.stream()
                    .map(je -> getBaseSuffix(je, version))
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(jf::getJarEntry);
        }
        return jf.stream();
    }

    private static String getBaseSuffix(JarEntry je, int version) {
        String name = je.getName();
        if (name.startsWith(META_INF_VERSIONS)) {
            int len = META_INF_VERSIONS.length();
            int index = name.indexOf('/', len);
            if (index == -1 || index == (name.length() - 1)) {
                // filter out META-INF/versions/* and META-INF/versions/*/
                return null;
            }
            try {
                if (Integer.parseInt(name, len, index, 10) > version) {
                    // not an integer
                    return null;
                }
            } catch (NumberFormatException x) {
                // silently remove malformed entries
                return null;
            }
            // We know name looks like META-INF/versions/*/*
            return name.substring(index + 1);
        }
        return name;
    }
}
