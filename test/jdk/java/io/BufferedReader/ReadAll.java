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
 * @summary Test BufferedReader readAllLines and readString methods
 * @library .. /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.RandomFactory
 * @run junit ReadAll
 * @key randomness
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @BeforeAll
    public static void setup() throws IOException {
        file = File.createTempFile("foo", "bar", new File("."));
        path = file.toPath();

        Random rnd = RandomFactory.getRandom();
        int size = rnd.nextInt(2, 16386);

        FileWriter fw = new FileWriter(file);

        int len = 0;
        int plen = PHRASE.length();
        while (len < size) {
            int fromIndex = rnd.nextInt(0, plen / 2);
            int toIndex = rnd.nextInt(fromIndex, plen);
            String str = PHRASE.substring(fromIndex, toIndex);
            fw.write(str);
            fw.write(System.lineSeparator());
            len += toIndex - fromIndex;
        }
        fw.close();
    }

    @AfterAll
    public static void cleanup() throws IOException {
        file.delete();
    }

    @Test
    public void readAllLines() throws IOException {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        List<String> lines = br.readAllLines();
        br.close();

        List<String> linesExpected = Files.readAllLines(path);

        assertEquals(linesExpected, lines);
    }

    @Test
    public void readString() throws IOException {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String string = br.readString();
        br.close();

        String stringExpected = Files.readString(path);

        assertEquals(stringExpected, string);
    }
}
