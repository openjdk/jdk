/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio.file;

import java.nio.file.Path;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class PathOfString {
    @Param({"C:\\Users\\foo\\bar\\gus.txt",              // absolute
            "C:\\Users\\\\foo\\\\bar\\gus.txt",          // ... with extra '\\'s
            "\\\\.\\UNC\\localhost\\C$\\Users\\foo",     // UNC
            "\\\\.\\UNC\\localhost\\C$\\\\Users\\\\foo", // ... with extra '\\'s
            "\\\\?\\C:\\Users\\foo\\bar\\gus.txt",       // long path prefix
            "\\\\?\\C:\\Users\\\\foo\\bar\\\\gus.txt",   // ... with extra '\\'s
            ".\\foo\\bar\\gus.txt",                      // relative
            ".\\foo\\\\bar\\\\gus.txt",                  // ... with extra '\\'s
            "\\foo\\bar\\gus.txt",                     // current drive-relative
            "\\foo\\\\bar\\\\gus.txt",                 // ... with extra '\\'s
            "C:foo\\bar\\gus.txt",         // drive's current directory-relative
            "C:foo\\\\bar\\\\gus.txt"})    // ... with extra '\\'s

    public String path;

    @Benchmark
    public Path parse() {
        return Path.of(path);
    }
}
