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

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MockupCommand {

    public static void main(String[] args) throws Exception {
        try {
            readTextFile("expected-args-file").ifPresent(content -> {
                if (!List.of(args).equals(content)) {
                    throw new IllegalArgumentException(String.format("Unexpected arguments %s", List.of(args)));
                }
            });

            fillStream("stdout-file", System.out);
            fillStream("stderr-file", System.err);

            System.exit(getProperty("exit").map(Integer::parseInt).orElse(0));
        } catch (Exception ex) {
            getProperty("error-file").map(Path::of).ifPresent(errorResponseFile -> {
                try {
                    Files.createDirectories(errorResponseFile.getParent());
                    try (var w = Files.newBufferedWriter(errorResponseFile)) {
                        ex.printStackTrace(new PrintWriter(w));
                    }
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
            throw ex;
        }
    }

    private static void fillStream(String propertyName, PrintStream sink) {
        readTextFile(propertyName).ifPresent(content -> {
            content.forEach(sink::println);
        });
    }

    private static Optional<List<String>> readTextFile(String propertyName) {
        return getProperty(propertyName).map(Path::of).map(file -> {
            try {
                return Files.readAllLines(file);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private static Optional<String> getProperty(String name) {
        return Optional.ofNullable(System.getProperty("jpackage.test.MockupCommand." + Objects.requireNonNull(name)));
    }
}
