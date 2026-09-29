/*
 * Copyright (c) 2023, Red Hat, Inc.
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


import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.internal.jimage.BasicImageReader;
import jdk.internal.jimage.ImageLocation;

/**
 *
 * JDK Modular image iterator
 */
public class JImageHelper {

    private JImageHelper() {
        // Don't instantiate
    }

    public static List<String> listContents(Path jimage) throws IOException {
        try(BasicImageReader reader = BasicImageReader.open(jimage)) {
            List<String> entries = new ArrayList<>();
            for (String s : reader.getEntryNames()) {
                entries.add(s);
            }
            Collections.sort(entries);
            return entries;
        }
    }

    public static byte[] getLocationBytes(String location, Path jimage) throws IOException {
        try(BasicImageReader reader = BasicImageReader.open(jimage)) {
            ImageLocation il = reader.findLocation(location);
            byte[] r = reader.getResource(il);
            if (r == null) {
                throw new IllegalStateException(String.format("bytes for %s not found!", location));
            }
            return r;
        }
    }
}
