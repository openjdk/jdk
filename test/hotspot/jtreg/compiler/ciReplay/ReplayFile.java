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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ReplayFile {
    private final Path replayFilePath;
    private final List<String> replayFile;

    public ReplayFile(String replayFileName) {
        try {
            this.replayFilePath = Paths.get(replayFileName);
            this.replayFile = Files.readAllLines(replayFilePath);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }
    }

    public void removeLineStartingWith(String oldLine) {
        replaceLineStartingWith(oldLine, "");
    }

    public String findLineStartingWith(String toFind) {
        return replayFile.stream()
                .filter(line -> line.startsWith(toFind))
                .findFirst()
                .orElse("");
    }

    public void replaceLineStartingWith(String oldLine, String newLine) {
        boolean foundOldLine = false;
        List<String> newReplayFile = new ArrayList<>();
        for (String line : replayFile) {
            if (line.startsWith(oldLine)) {
                foundOldLine = true;
                if (!newLine.isEmpty()) {
                    // Only add if non-empty. Otherwise, line removal.
                    newReplayFile.add(newLine);
                }
            } else {
                newReplayFile.add(line);
            }
        }
        Asserts.assertTrue(foundOldLine, "Did not find oldLine \"" + oldLine + "\" in " + replayFilePath);
        try {
            Files.write(replayFilePath, newReplayFile, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }
    }
}
