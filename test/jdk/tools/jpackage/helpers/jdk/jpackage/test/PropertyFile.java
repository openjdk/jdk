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
package jdk.jpackage.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

public final class PropertyFile {

    PropertyFile(Map<String, String> data) {
        this.data = new Properties();
        this.data.putAll(data);
        path = Optional.empty();
    }

    PropertyFile(Path path) {
        data = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            data.load(reader);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        this.path = Optional.of(path);
    }

    public Optional<String> findProperty(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(data.getProperty(name));
    }

    public Optional<Boolean> findBooleanProperty(String name) {
        return findProperty(name).map(Boolean::parseBoolean);
    }

    public Optional<Path> path() {
        return path;
    }

    public Path getPath() {
        return path().orElseThrow();
    }

    public Map<String, String> toMap() {
        return data.entrySet().stream().collect(Collectors.toUnmodifiableMap(e -> {
            return (String)e.getKey();
        }, e -> {
            return (String)e.getValue();
        }));
    }

    private final Properties data;
    private final Optional<Path> path;
}
