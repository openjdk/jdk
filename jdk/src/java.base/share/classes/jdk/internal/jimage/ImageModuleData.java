/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/*
 * Manage module meta data.
 *
 * NOTE: needs revision.
 * Each loader requires set of module meta data to identify which modules and
 * packages are managed by that loader.  Currently, there is one image file per
 * loader, so only one  module meta data resource per file.
 *
 * Each element in the module meta data is a native endian 4 byte integer.  Note
 * that entries with zero offsets for string table entries should be ignored (
 * padding for hash table lookup.)
 *
 * Format:
 *    Count of package to module entries
 *    Count of module to package entries
 *    Perfect Hash redirect table[Count of package to module entries]
 *    Package to module entries[Count of package to module entries]
 *        Offset to package name in string table
 *        Offset to module name in string table
 *    Perfect Hash redirect table[Count of module to package entries]
 *    Module to package entries[Count of module to package entries]
 *        Offset to module name in string table
 *        Count of packages in module
 *        Offset to first package in packages table
 *    Packages[]
 *        Offset to package name in string table
 */

public final class ImageModuleData {
    public static final String META_DATA_EXTENSION = ".jdata";
    public static final String SEPARATOR = "\t";
    public static final int NOT_FOUND = -1;
    private static final int ptmCountOffset = 0;
    private static final int mtpCountOffset = 1;
    private static final int ptmRedirectOffset = 2;
    private static final int dataNameOffset = 0;
    private static final int ptmDataWidth = 2;
    private static final int ptmDataModuleOffset = 1;
    private static final int mtpDataWidth = 3;
    private static final int mtpDataCountOffset = 1;
    private static final int mtpDataOffsetOffset = 2;

    private final BasicImageReader reader;
    private final IntBuffer intBuffer;
    private final int ptmRedirectLength;
    private final int mtpRedirectLength;
    private final int ptmDataOffset;
    private final int mtpRedirectOffset;
    private final int mtpDataOffset;
    private final int mtpPackagesOffset;

    public ImageModuleData(BasicImageReader reader) {
         this(reader, getBytes(reader));
    }

    public ImageModuleData(BasicImageReader reader, byte[] bytes) {
        this.reader = reader;

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(reader.getByteOrder());
        this.intBuffer = byteBuffer.asIntBuffer();

        this.ptmRedirectLength = get(ptmCountOffset);
        this.mtpRedirectLength = get(mtpCountOffset);

        this.ptmDataOffset = ptmRedirectOffset + ptmRedirectLength;
        this.mtpRedirectOffset = ptmDataOffset + ptmRedirectLength * ptmDataWidth;
        this.mtpDataOffset = mtpRedirectOffset + mtpRedirectLength;
        this.mtpPackagesOffset = mtpDataOffset + mtpRedirectLength * mtpDataWidth;
    }

    private static byte[] getBytes(BasicImageReader reader) {
        String loaderName = reader.imagePathName();

        if (loaderName.endsWith(BasicImageWriter.IMAGE_EXT)) {
            loaderName = loaderName.substring(0, loaderName.length() -
                    BasicImageWriter.IMAGE_EXT.length());
        }

        byte[] bytes = reader.getResource(getModuleDataName(loaderName));

        if (bytes == null) {
            throw new InternalError("module data missing");
        }

        return bytes;
    }

    public List<String> fromModulePackages() {
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < mtpRedirectLength; i++) {
            int index = mtpDataOffset + i * mtpDataWidth;
            int offset = get(index + dataNameOffset);

            if (offset != 0) {
                StringBuilder sb = new StringBuilder();

                sb.append(getString(offset));

                int count = get(index + mtpDataCountOffset);
                int base = get(index + mtpDataOffsetOffset) + mtpPackagesOffset;

                for (int j = 0; j < count; j++) {
                    sb.append(SEPARATOR);
                    sb.append(stringAt(base + j));
                }

                lines.add(sb.toString());
            }
        }

        return lines;
    }

    public static String getModuleDataName(String loaderName) {
        return loaderName + META_DATA_EXTENSION;
    }

    private int get(int index) {
        return intBuffer.get(index);
    }

    private String getString(int offset) {
        return reader.getString(offset);
    }

    private String stringAt(int index) {
        return reader.getString(get(index));
    }

    private UTF8String getUTF8String(int offset) {
        return reader.getUTF8String(offset);
    }

    private UTF8String utf8StringAt(int index) {
        return reader.getUTF8String(get(index));
    }

    private int find(UTF8String name, int baseOffset, int length, int width) {
        if (length == 0) {
            return NOT_FOUND;
        }

        int hashCode = name.hashCode();
        int index = hashCode % length;
        int value = get(baseOffset + index);

        if (value > 0 ) {
            hashCode = name.hashCode(value);
            index = hashCode % length;
        } else if (value < 0) {
            index = -1 - value;
        } else {
            return NOT_FOUND;
        }

        index = baseOffset + length + index * width;

        if (!utf8StringAt(index + dataNameOffset).equals(name)) {
            return NOT_FOUND;
        }

        return index;
    }

    public String packageToModule(String packageName) {
        UTF8String moduleName = packageToModule(new UTF8String(packageName));

        return moduleName != null ? moduleName.toString() : null;
    }

    public UTF8String packageToModule(UTF8String packageName) {
        int index = find(packageName, ptmRedirectOffset, ptmRedirectLength, ptmDataWidth);

        if (index != NOT_FOUND) {
            return utf8StringAt(index + ptmDataModuleOffset);
        }

        return null;
    }

    public List<String> moduleToPackages(String moduleName) {
        int index = find(new UTF8String(moduleName), mtpRedirectOffset,
                mtpRedirectLength, mtpDataWidth);

        if (index != NOT_FOUND) {
            int count = get(index + mtpDataCountOffset);
            int base = get(index + mtpDataOffsetOffset) + mtpPackagesOffset;
            List<String> packages = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                packages.add(stringAt(base + i));
            }

            return packages;
        }

        return null;
    }

    public List<String> allPackageNames() {
        List<String> packages = new ArrayList<>();

        for (int i = 0; i < ptmRedirectLength; i++) {
            int offset = get(ptmDataOffset + i * ptmDataWidth + dataNameOffset);

            if (offset != 0) {
                packages.add(getString(offset));
            }
        }

        return packages;
    }

    public Set<String> allModuleNames() {
        Set<String> modules = new HashSet<>();

        for (int i = 0; i < mtpRedirectLength; i++) {
            int index = mtpDataOffset + i * mtpDataWidth;
            int offset = get(index + dataNameOffset);

            if (offset != 0) {
                modules.add(getString(offset));
            }
        }

        return modules;
    }

    public Map<String, String> packageModuleMap() {
        Map<String, String> map = new HashMap<>();

        for (int i = 0; i < mtpRedirectLength; i++) {
            int index = mtpDataOffset + i * mtpDataWidth;
            int offset = get(index + dataNameOffset);

            if (offset != 0) {
                String moduleName = getString(offset);

                int count = get(index + mtpDataCountOffset);
                int base = get(index + mtpDataOffsetOffset) + mtpPackagesOffset;

                for (int j = 0; j < count; j++) {
                    map.put(stringAt(base + j), moduleName);
                }
            }
        }

        return map;
    }
}
