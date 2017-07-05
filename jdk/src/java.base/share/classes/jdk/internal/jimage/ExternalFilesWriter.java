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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import jdk.internal.jimage.Archive.Entry;

/**
 * A Consumer suitable for processing non resources Archive Entry and writing it to the
 * appropriate location.
 */
class ExternalFilesWriter implements Consumer<Entry> {
    private final Path root;

    ExternalFilesWriter(Path root) {
        this.root = root;
    }

    @Override
    public void accept(Entry entry) {
        String name = entry.path();
        try {
            String filename = entry.path();
            try (InputStream in = entry.stream()) {
                switch (entry.type()) {
                    case NATIVE_LIB:
                        writeEntry(in, destFile(nativeDir(filename), filename));
                        break;
                    case NATIVE_CMD:
                        Path path = destFile("bin", filename);
                        writeEntry(in, path);
                        path.toFile().setExecutable(true, false);
                        break;
                    case CONFIG:
                        writeEntry(in, destFile("conf", filename));
                        break;
                    case MODULE_NAME:
                        // skip
                        break;
                    case SERVICE:
                        //throw new UnsupportedOperationException(name + " in " + zipfile.toString()); //TODO
                        throw new UnsupportedOperationException(name + " in " + name);
                    default:
                        //throw new InternalError("unexpected entry: " + name + " " + zipfile.toString()); //TODO
                        throw new InternalError("unexpected entry: " + name + " " + name);
                }
            }
        } catch (FileAlreadyExistsException x) {
            System.err.println("File already exists (skipped) " + name);
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    private Path destFile(String dir, String filename) {
        return root.resolve(dir).resolve(filename);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    private static String nativeDir(String filename) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            if (filename.endsWith(".dll") || filename.endsWith(".diz")
                || filename.endsWith(".pdb") || filename.endsWith(".map")) {
                return "bin";
            } else {
                return "lib";
            }
        } else {
            return "lib";
        }
    }
}
