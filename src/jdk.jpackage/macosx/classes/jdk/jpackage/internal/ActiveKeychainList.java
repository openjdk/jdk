/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OSVersion;

final class ActiveKeychainList implements Closeable {

    static Optional<ActiveKeychainList> createForPlatform(List<Keychain> keychains) throws IOException {
        if (!keychains.isEmpty() && Globals.instance().findBooleanProperty(ActiveKeychainList.class).orElseGet(ActiveKeychainList::isRequired)) {
            return Optional.of(new ActiveKeychainList(keychains));
        } else {
            return Optional.empty();
        }
    }

    static Optional<ActiveKeychainList> createForPlatform(Keychain... keychains) throws IOException {
        return createForPlatform(List.of(keychains));
    }

    @SuppressWarnings("try")
    static void withKeychains(Consumer<List<Keychain>> keychainConsumer, List<Keychain> keychains) throws IOException {
        var keychainList = createForPlatform(keychains);
        if (keychainList.isEmpty()) {
            keychainConsumer.accept(keychains);
        } else {
            try (var kl = keychainList.get()) {
                keychainConsumer.accept(keychains);
            }
        }
    }

    static void withKeychain(Consumer<Keychain> keychainConsumer, Keychain keychain) throws IOException {

        Objects.requireNonNull(keychainConsumer);
        withKeychains(keychains -> {
            keychainConsumer.accept(keychains.getFirst());
        }, List.of(keychain));
    }

    ActiveKeychainList(List<Keychain> requestedKeychains, List<Keychain> currentKeychains, boolean force) throws IOException {
        this.requestedKeychains = List.copyOf(requestedKeychains);
        this.oldKeychains = List.copyOf(currentKeychains);

        final List<String> cmdline = new ArrayList<>(LIST_KEYCHAINS_CMD_PREFIX);
        addKeychains(cmdline, oldKeychains);

        if (force) {
            this.currentKeychains = requestedKeychains;
            restoreKeychainsCmd = List.copyOf(cmdline);
            cmdline.subList(LIST_KEYCHAINS_CMD_PREFIX.size(), cmdline.size()).clear();
            addKeychains(cmdline, requestedKeychains);
        } else {
            final var currentKeychainPaths = oldKeychains.stream().map(Keychain::path).toList();

            final var missingKeychains = requestedKeychains.stream().filter(k -> {
                return !currentKeychainPaths.contains(k.path());
            }).toList();

            if (missingKeychains.isEmpty()) {
                this.currentKeychains = oldKeychains;
                restoreKeychainsCmd = List.of();
            } else {
                this.currentKeychains = Stream.of(oldKeychains, missingKeychains)
                        .flatMap(List::stream).collect(Collectors.toUnmodifiableList());
                restoreKeychainsCmd = List.copyOf(cmdline);
                addKeychains(cmdline, missingKeychains);
            }
        }

        Executor.of(cmdline).executeExpectSuccess();
    }

    ActiveKeychainList(List<Keychain> keychains) throws IOException {
        this(keychains, Keychain.listKeychains(), false);
    }

    List<Keychain> requestedKeychains() {
        return requestedKeychains;
    }

    List<Keychain> currentKeychains() {
        return currentKeychains;
    }

    List<Keychain> restoreKeychains() {
        return oldKeychains;
    }

    @Override
    public void close() throws IOException {
        if (!restoreKeychainsCmd.isEmpty()) {
            Executor.of(restoreKeychainsCmd).executeExpectSuccess();
        }
    }

    private static void addKeychains(List<String> cmdline, List<Keychain> keychains) {
        cmdline.addAll(keychains.stream().map(Keychain::asCliArg).toList());
    }

    private static boolean isRequired() {
        // Required for OS X 10.12+
        return 0 <= OSVersion.current().compareTo(new OSVersion(10, 12));
    }

    private final List<Keychain> requestedKeychains;
    private final List<Keychain> currentKeychains;
    private final List<Keychain> oldKeychains;
    private final List<String> restoreKeychainsCmd;

    private final static List<String> LIST_KEYCHAINS_CMD_PREFIX = List.of("/usr/bin/security", "list-keychains", "-s");
}
