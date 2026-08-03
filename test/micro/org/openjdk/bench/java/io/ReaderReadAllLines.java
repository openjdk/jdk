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
package org.openjdk.bench.java.io;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ReaderReadAllLines {

    private char[] chars = null;

    @Setup
    public void setup() throws IOException {
        final int len = 128_000;
        chars = new char[len];
        Random rnd = new Random(System.nanoTime());
        int off = 0;
        while (off < len) {
            int lineLen = 40 + rnd.nextInt(8192);
            if (lineLen > len - off) {
                off = len;
            } else {
                chars[off + lineLen] = '\n';
                off += lineLen;
            }
        }
    }

    @Benchmark
    public List<String> readAllLines() throws IOException {
        List<String> lines;
        try (Reader reader = new CharArrayReader(chars)) {
            lines = reader.readAllLines();
        }
        return lines;
    }
}
