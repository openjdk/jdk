/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;

record Keychain(String name) {
    Keychain {
        Objects.requireNonNull(name);
    }

    Path path() {
        final var path = Path.of(name);
        if (path.isAbsolute()) {
            return path;
        } else {
            final var dir = Path.of(System.getProperty("user.home")).resolve("Library/Keychains");
            final var files = filenames(name).map(dir::resolve).toList();
            return files.stream().filter(Files::exists).findFirst().orElseGet(() -> {
                // Can't find keychain file in "$HOME/Library/Keychains" folder.
                // Detect keychain file name from the name of the login keychain file.
                return files.stream().filter(f -> {
                    final var loginKeycahinFile = f.getParent().resolve("login.keychain" + f.getFileName().toString().substring(name.length()));
                    return Files.exists(loginKeycahinFile);
                }).findFirst().orElseGet(() -> {
                    // login keychain file doesn't exist, fallback to "$HOME/Library/Keychains/<name>-db" keychain file.
                    return files.getFirst();
                });
            });
        }
    }

    String asCliArg() {
        final var path = Path.of(name);
        if (path.isAbsolute()) {
            return PathUtils.normalizedAbsolutePathString(path);
        } else {
            return name;
        }
    }

    static List<Keychain> listKeychains() {
        // Get the current keychain list
        final List<String> cmdOutput;
        try {
            cmdOutput = Executor.of("/usr/bin/security", "list-keychains").saveOutput(true).executeExpectSuccess().getOutput();
        } catch (IOException ex) {
            throw I18N.buildException().message("message.keychain.error").cause(ex).create(KeychainException::new);
        }

        // Typical output of /usr/bin/security command is:
        //        "/Users/foo/Library/Keychains/login.keychain-db"
        //        "/Library/Keychains/System.keychain"
        return cmdOutput.stream().map(String::trim).map(str -> {
            // Strip enclosing double quotes
            return str.substring(1, str.length() - 1);
        }).map(Keychain::new).toList();
    }

    static final class KeychainException extends RuntimeException {

        KeychainException(String msg) {
            super(msg);
        }

        KeychainException(String msg, Throwable cause) {
            super(msg, cause);
        }

        private static final long serialVersionUID = 1L;
    }

    private static Stream<String> filenames(String name) {
        return Stream.of(name + "-db", name);
    }
}
