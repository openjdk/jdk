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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import jdk.internal.util.OSVersion;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

final class TempKeychain implements Closeable {

    static void withKeychain(Path keyChain, ThrowingConsumer<Path> keyChainConsumer) throws Throwable {
        Objects.requireNonNull(keyChain);
        if (OSVersion.current().compareTo(new OSVersion(10, 12)) < 0) {
            // we need this for OS X 10.12+
            try (var tempKeychain = new TempKeychain(keyChain)) {
                keyChainConsumer.accept(tempKeychain.keyChain());
            }
        } else {
            keyChainConsumer.accept(PathUtils.normalizedAbsolutePath(keyChain));
        }
    }

    TempKeychain(Path keyChain) throws IOException {
        this.keyChain = PathUtils.normalizedAbsolutePath(keyChain);

        // Get the current keychain list
        final List<String> cmdOutput;
        try {
            cmdOutput = Executor.of("/usr/bin/security", "list-keychains").saveOutput(true).executeExpectSuccess().getOutput();
        } catch (IOException ex) {
            throw I18N.buildException().message("message.keychain.error").cause(ex).create(IOException::new); // FIXME: should be PackagerException
        }

        // Typical output of /usr/bin/security command is:
        //        "/Users/foo/Library/Keychains/login.keychain-db"
        //        "/Library/Keychains/System.keychain"
        final var origKeychains = cmdOutput.stream().map(String::trim).map(str -> {
            // Strip enclosing double quotes
            return str.substring(1, str.length() - 1);
        }).toList();

        final var keychainMissing = origKeychains.stream().map(Path::of).filter(Predicate.isEqual(keyChain)).findAny().isEmpty();
        if (keychainMissing) {
            List<String> args = new ArrayList<>();
            args.add("/usr/bin/security");
            args.add("list-keychains");
            args.add("-s");
            args.addAll(origKeychains);

            restoreKeychainsCmd = List.copyOf(args);

            args.add(keyChain.toString());

            Executor.of(args.toArray(String[]::new)).executeExpectSuccess();
        } else {
            restoreKeychainsCmd = List.of();
        }
    }

    Path keyChain() {
        return keyChain;
    }

    @Override
    public void close() throws IOException {
        if (restoreKeychainsCmd.isEmpty()) {
            Executor.of(restoreKeychainsCmd.toArray(String[]::new)).executeExpectSuccess();
        }
    }

    private final Path keyChain;
    private final List<String> restoreKeychainsCmd;
}
