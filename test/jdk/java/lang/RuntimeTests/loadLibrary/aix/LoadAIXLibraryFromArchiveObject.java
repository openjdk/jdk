/*
 * Copyright (c) 2024, IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8319516
 * @requires os.family == "aix"
 * @run  main/othervm  LoadAIXLibraryFromArchiveObject
 */

import java.io.*;
import java.lang.Object.*;
import java.nio.file.Files;

public class LoadAIXLibraryFromArchiveObject {
    public static void main(String[] args) throws Exception {
        String libraryName = "dummyarchive";
        String javaHome = System.getProperty("java.home");
        File awtSharedObjectPath = new File(javaHome+"/lib/libawt.so");
        File awtArchivePath = new File(javaHome+"/lib/libdummyarchive.a");
        Files.copy(awtSharedObjectPath.toPath(), awtArchivePath.toPath());
        System.loadLibrary(libraryName);
        if (!awtArchivePath.delete())
            throw new RuntimeException("LoadLibraryFromArchiveObject: Failed to delete dummy archive file.");
    }
}
