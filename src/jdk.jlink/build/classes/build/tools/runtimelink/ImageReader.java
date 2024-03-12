/*
 * Copyright (c) 2024, Red Hat, Inc.
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
package build.tools.runtimelink;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jdk.internal.jimage.BasicImageReader;

public class ImageReader extends BasicImageReader implements JimageDiffGenerator.ImageResource {

    public ImageReader(Path path) throws IOException {
        super(path);
    }

    public static boolean isNotTreeInfoResource(String path) {
        return !(path.startsWith("/packages") || path.startsWith("/modules"));
    }

    @Override
    public List<String> getEntries() {
        return Arrays.asList(getEntryNames()).stream()
                .filter(ImageReader::isNotTreeInfoResource)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public byte[] getResourceBytes(String name) {
        return getResource(name);
    }

}
