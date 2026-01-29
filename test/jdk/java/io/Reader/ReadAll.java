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

/* @test
 * @bug 8354724
 * @summary Test Reader readAllLines and readAllAstring methods
 * @library .. /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.RandomFactory
 * @run junit ReadAll
 * @key randomness
 */

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadAll {
    private static final String PHRASE =
        "Ange plein de gaieté, connaissez-vous l'angoisse";

    private static File file;
    private static Path path;
    private static Random rnd;

    @BeforeAll
    public static void setup() throws IOException {
        path = Files.createTempFile(Path.of("."), "foo", "bar");
        file = path.toFile();

        rnd = RandomFactory.getRandom();
        int size = rnd.nextInt(2, 16386);

        int plen = PHRASE.length();
        StringBuilder sb = new StringBuilder(plen);
        List<String> strings = new ArrayList<>(size);
        while (strings.size() < size) {
            int fromIndex = rnd.nextInt(0, plen / 2);
            int toIndex = rnd.nextInt(fromIndex, plen);
            String s = PHRASE.substring(fromIndex, toIndex);
            sb.append(s);
            int bound = toIndex - fromIndex;
            if (bound > 0) {
                int offset = bound/2;
                int n = rnd.nextInt(0, bound);
                for (int i = 0; i < n; i++) {
                    String f = null;
                    switch (rnd.nextInt(7)) {
                    case 0 -> f = "";
                    case 1 -> f = "\r";
                    case 2 -> f = "\n";
                    case 3 -> f = "\r\n";
                    case 4 -> f = "\r\r";
                    case 5 -> f = "\n\n";
                    case 6 -> f = " ";
                    }
                    sb.insert(offset, f);
                }
            }
            strings.add(sb.toString());
            sb.setLength(0);
        }

        String p4096  = PHRASE.repeat((4096 + plen - 1)/plen);
        String p8192  = PHRASE.repeat((8192 + plen - 1)/plen);
        String p16384 = PHRASE.repeat((16384 + plen - 1)/plen);

        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 32; j++) {
                switch (rnd.nextInt(8)) {
                case 0 -> sb.append("");
                case 1 -> sb.append(" ");
                case 2 -> sb.append("\n");
                case 3 -> sb.append(PHRASE);
                case 5 -> sb.append(p4096);
                case 6 -> sb.append(p8192);
                case 7 -> sb.append(p16384);
                }
            }
            strings.add(sb.toString());
            sb.setLength(0);
        }

        Files.write(path, strings);
        System.out.println(strings.size() + " lines written");
    }

    @AfterAll
    public static void cleanup() throws IOException {
        if (file != null)
            file.delete();
    }

    @Test
    public void readAllLines() throws IOException {
        // Reader implementation
        System.out.println("Reader implementation");
        List<String> lines;
        try (FileReader fr = new FileReader(file)) {
            lines = fr.readAllLines();
        }
        System.out.println(lines.size() + " lines read");

        List<String> linesExpected = Files.readAllLines(path);
        int count = linesExpected.size();
        if (lines.size() != count)
            throw new RuntimeException("Size mismatch: " + lines.size() + " != " + count);
        for (int i = 0; i < count; i++) {
            String expected = linesExpected.get(i);
            String actual = lines.get(i);
            if (!actual.equals(expected)) {
                String msg = String.format("%d: \"%s\" != \"%s\"",
                                           i, actual, expected);
                throw new RuntimeException(msg);
            }
        }

        // Reader.of implementation
        System.out.println("Reader.of implementation");
        String stringExpected = Files.readString(path);
        int n = rnd.nextInt(stringExpected.length()/2);
        String substringExpected = stringExpected.substring(n);
        linesExpected = substringExpected.lines().toList();
        try (Reader r = new StringReader(stringExpected)) {
            r.skip(n);
            lines = r.readAllLines();
        }
        count = linesExpected.size();
        if (lines.size() != count)
            throw new RuntimeException("Size mismatch: " + lines.size() + " != " + count);
        for (int i = 0; i < count; i++) {
            String expected = linesExpected.get(i);
            String actual = lines.get(i);
            if (!actual.equals(expected)) {
                String msg = String.format("%d: \"%s\" != \"%s\"",
                                           i, actual, expected);
                throw new RuntimeException(msg);
            }
        }
    }

    @Test
    public void readAllAsString() throws IOException {
        // Reader implementation
        String string;
        try (FileReader fr = new FileReader(file)) {
            string = fr.readAllAsString();
        }
        String stringExpected = Files.readString(path);
        assertEquals(stringExpected, string);

        // Reader.of implementation
        int n = rnd.nextInt(stringExpected.length()/2);
        try (Reader r = Reader.of(stringExpected)) {
            r.skip(n);
            string = r.readAllAsString();
        }
        assertEquals(stringExpected.substring(n), string);
    }
}
