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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jdk.internal.util.OSVersion;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

final class TempKeychain implements Closeable {

    static void withKeychains(ThrowingConsumer<List<Keychain>> keychainConsumer, List<Keychain> keychains) throws Throwable {
        keychains.forEach(Objects::requireNonNull);
        if (keychains.isEmpty() || OSVersion.current().compareTo(new OSVersion(10, 12)) < 0) {
            keychainConsumer.accept(keychains);
        } else {
            // we need this for OS X 10.12+
            try (var tempKeychain = new TempKeychain(keychains)) {
                keychainConsumer.accept(tempKeychain.keychains);
            }
        }
    }

    static void withKeychain(ThrowingConsumer<Keychain> keychainConsumer, Keychain keychain) throws Throwable {
        Objects.requireNonNull(keychainConsumer);
        withKeychains(keychains -> {
            keychainConsumer.accept(keychains.getFirst());
        }, List.of(keychain));
    }

    TempKeychain(List<Keychain> keychains) throws IOException {
        this.keychains = Objects.requireNonNull(keychains);

        final var currentKeychains = Keychain.listKeychains();

        final var currentKeychainPaths = currentKeychains.stream().map(Keychain::path).toList();

        final var missingKeychains = keychains.stream().filter(k -> {
            return !currentKeychainPaths.contains(k.path());
        }).toList();

        if (missingKeychains.isEmpty()) {
            restoreKeychainsCmd = List.of();
        } else {
            List<String> args = new ArrayList<>();
            args.add("/usr/bin/security");
            args.add("list-keychains");
            args.add("-s");
            args.addAll(currentKeychains.stream().map(Keychain::asCliArg).toList());

            restoreKeychainsCmd = List.copyOf(args);

            args.addAll(missingKeychains.stream().map(Keychain::asCliArg).toList());

            Executor.of(args.toArray(String[]::new)).executeExpectSuccess();
        }
    }

    List<Keychain> keychains() {
        return keychains;
    }

    @Override
    public void close() throws IOException {
        if (!restoreKeychainsCmd.isEmpty()) {
            Executor.of(restoreKeychainsCmd.toArray(String[]::new)).executeExpectSuccess();
        }
    }

    private final List<Keychain> keychains;
    private final List<String> restoreKeychainsCmd;
}
