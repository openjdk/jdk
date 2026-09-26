/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PrintIdeal {
    private final String logFile;
    private final List<String> idealGraph;

    public PrintIdeal(String logFile) {
        this.logFile = logFile;
        this.idealGraph = new ArrayList<>();
    }

    public List<String> vmFlags() {
        return new ArrayList<>(List.of(
                "-XX:LogFile='" + logFile + "'",
                "-XX:+LogCompilation",
                "-XX:+PrintIdeal"
        ));
    }

    public void parse() {
        idealGraph.clear();
        try (var br = Files.newBufferedReader(Paths.get(logFile))) {
            String line;
            boolean printIdealLine = false;
            while ((line = br.readLine()) != null) {
                if (printIdealLine) {
                    if (line.startsWith("</ideal")) {
                        break;
                    }
                    idealGraph.add(line);
                } else {
                    printIdealLine = line.startsWith("<ideal");
                }
            }
        } catch (IOException e) {
            throw new Error("Failed to read " + logFile + " data: " + e, e);
        }
        Asserts.assertFalse(idealGraph.isEmpty(), "did not find PrintIdeal output");
    }

    public String find(String toMatch) {
        return idealGraph.stream()
                .filter(line -> line.contains(toMatch))
                .findFirst()
                .orElse("");
    }

    public int count(String toMatch) {
        return (int) idealGraph.stream()
                .filter(line -> line.contains(toMatch))
                .count();
    }
}
