/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nio.zipfs;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Formatter;
import static jdk.nio.zipfs.ZipUtils.*;

/**
 *
 * @author  Xueming Shen, Rajendra Gutupalli,Jaya Hangal
 */

class ZipFileAttributes implements BasicFileAttributes
{
    private final ZipFileSystem.Entry e;

    ZipFileAttributes(ZipFileSystem.Entry e) {
        this.e = e;
    }

    ///////// basic attributes ///////////
    @Override
    public FileTime creationTime() {
        if (e.ctime != -1)
            return FileTime.fromMillis(e.ctime);
        return null;
    }

    @Override
    public boolean isDirectory() {
        return e.isDir();
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public boolean isRegularFile() {
        return !e.isDir();
    }

    @Override
    public FileTime lastAccessTime() {
        if (e.atime != -1)
            return FileTime.fromMillis(e.atime);
        return null;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(e.mtime);
    }

    @Override
    public long size() {
        return e.size;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public Object fileKey() {
        return null;
    }

    ///////// zip entry attributes ///////////
    public long compressedSize() {
        return e.csize;
    }

    public long crc() {
        return e.crc;
    }

    public int method() {
        return e.method;
    }

    public byte[] extra() {
        if (e.extra != null)
            return Arrays.copyOf(e.extra, e.extra.length);
        return null;
    }

    public byte[] comment() {
        if (e.comment != null)
            return Arrays.copyOf(e.comment, e.comment.length);
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        Formatter fm = new Formatter(sb);
        if (creationTime() != null)
            fm.format("    creationTime    : %tc%n", creationTime().toMillis());
        else
            fm.format("    creationTime    : null%n");

        if (lastAccessTime() != null)
            fm.format("    lastAccessTime  : %tc%n", lastAccessTime().toMillis());
        else
            fm.format("    lastAccessTime  : null%n");
        fm.format("    lastModifiedTime: %tc%n", lastModifiedTime().toMillis());
        fm.format("    isRegularFile   : %b%n", isRegularFile());
        fm.format("    isDirectory     : %b%n", isDirectory());
        fm.format("    isSymbolicLink  : %b%n", isSymbolicLink());
        fm.format("    isOther         : %b%n", isOther());
        fm.format("    fileKey         : %s%n", fileKey());
        fm.format("    size            : %d%n", size());
        fm.format("    compressedSize  : %d%n", compressedSize());
        fm.format("    crc             : %x%n", crc());
        fm.format("    method          : %d%n", method());
        fm.close();
        return sb.toString();
    }
}
