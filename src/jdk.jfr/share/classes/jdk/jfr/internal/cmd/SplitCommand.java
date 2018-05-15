/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.cmd;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;

final class SplitCommand extends Command {

    @Override
    public String getOptionSyntax() {
        return "[--maxchunks <chunks>] <file>";
    }

    @Override
    public void displayOptionUsage() {
        println("  --maxchunks <chunks>   Maximum number of chunks per splitted file (default 5).");
        println("                         The chunk size varies, but is typically around 15 MB.");
        println();
        println("  <file>                 Location of recording file (.jfr) to split");
    }

    @Override
    public String getName() {
        return "split";
    }

    @Override
    public String getDescription() {
        return "Splits a recording file into smaller files";
    }

    @Override
    public void execute(Deque<String> options) {
        if (options.isEmpty()) {
            userFailed("Missing file");
        }
        ensureMaxArgumentCount(options, 3);
        Path file = Paths.get(options.removeLast());
        ensureFileExist(file);
        ensureJFRFile(file);
        int maxchunks = 5;
        if (!options.isEmpty()) {
            String option = options.pop();
            if (!"--maxchunks".equals(option)) {
                userFailed("Unknown option " + option);
            }
            if (options.isEmpty()) {
                userFailed("Missing value for --maxChunks");
            }
            String value = options.pop();
            try {
                maxchunks = Integer.parseInt(value);
                if (maxchunks < 1) {
                    userFailed("Must be at least one chunk per file.");
                }
            } catch (NumberFormatException nfe) {
                userFailed("Not a valid value for --maxchunks.");
            }
        }
        ensureMaxArgumentCount(options, 0);
        println();
        println("Examining recording " + file + " ...");
        List<Long> sizes;

        try {
            sizes = findChunkSizes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error. " + e.getMessage());
        }
        if (sizes.size() <= maxchunks) {
            throw new IllegalStateException("Number of chunks in recording (" + sizes.size() + ") doesn't exceed max chunks (" + maxchunks + ")");
        }
        println();

        println();
        if (sizes.size() > 0) {
            print("File consists of " + sizes.size() + " chunks. The recording will be split into ");
            sizes = combineChunkSizes(sizes, maxchunks);
            println(sizes.size() + " files with at most " + maxchunks + " chunks per file.");
            println();

            try {
                splitFile(file, sizes);
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected error. " + e.getMessage());
            }
        } else {
            println("No JFR chunks found in file. ");
        }
    }

    private List<Long> findChunkSizes(Path p) throws IOException {
        try (RecordingInput input = new RecordingInput(p.toFile())) {
            List<Long> sizes = new ArrayList<>();
            ChunkHeader ch = new ChunkHeader(input);
            sizes.add(ch.getSize());
            while (!ch.isLastChunk()) {
                ch = ch.nextHeader();
                sizes.add(ch.getSize());
            }
            return sizes;
        }
    }

    private List<Long> combineChunkSizes(List<Long> sizes, int chunksPerFile) {
        List<Long> reduced = new ArrayList<Long>();
        long size = sizes.get(0);
        for (int n = 1; n < sizes.size(); n++) {
            if (n % chunksPerFile == 0) {
                reduced.add(size);
                size = 0;
            }
            size += sizes.get(n);
        }
        reduced.add(size);
        return reduced;
    }

    private void splitFile(Path file, List<Long> splitPositions) throws IOException {

        int padAmountZeros = String.valueOf(splitPositions.size() - 1).length();
        String fileName = file.toString();
        String fileFormatter = fileName.subSequence(0, fileName.length() - 4) + "_%0" + padAmountZeros + "d.jfr";
        for (int i = 0; i < splitPositions.size(); i++) {
            Path p = Paths.get(String.format(fileFormatter, i));
            if (Files.exists(p)) {
                throw new IllegalStateException("Can't create split file " + p + ", a file with that name already exist");
            }
        }
        DataInputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile())));

        for (int i = 0; i < splitPositions.size(); i++) {
            Long l = splitPositions.get(i);
            byte[] bytes = readBytes(stream, l.intValue());
            Path p = Paths.get(String.format(fileFormatter, i));
            File splittedFile = p.toFile();
            println("Writing " + splittedFile + " ...");
            FileOutputStream fos = new FileOutputStream(splittedFile);
            fos.write(bytes);
            fos.close();
        }
        stream.close();
    }

    private byte[] readBytes(InputStream stream, int count) throws IOException {
        byte[] data = new byte[count];
        int totalRead = 0;
        while (totalRead < data.length) {
            int read = stream.read(data, totalRead, data.length - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of data.");
            }
            totalRead += read;
        }
        return data;
    }
}
