/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;

public final class RuntimeReleaseFile {

    public RuntimeReleaseFile(Path releaseFilePath) throws IOException {
        // The implementation is based on the behavior of
        // jdk.tools.jlink.internal.plugins.ReleaseInfoPlugin, which
        // uses java.util.Properties to read/write the "release" file.
        try (Reader reader = Files.newBufferedReader(releaseFilePath)) {
            props = new Properties();
            props.load(reader);
        }
    }

    /**
     * Returns path to the "runtime" file in the specified runtime directory.
     *
     * @param runtimeDir the path to a directory with the standard Java runtime
     *                   structure
     */
    public static Path releaseFilePathInRuntime(Path runtimeDir) {
        return runtimeDir.resolve("release");
    }

    /**
     * Creates the instance form the "runtime" file in the specified runtime
     * directory.
     * <p>
     * Uses {@link #releaseFilePathInRuntime(Path)} to get the path to the "runtime"
     * file in the specified runtime directory.
     *
     * @param runtimeDir the path to a directory with the standard Java runtime
     *                   structure
     */
    public static RuntimeReleaseFile loadFromRuntime(Path runtimeDir) throws IOException {
        return new RuntimeReleaseFile(releaseFilePathInRuntime(runtimeDir));
    }

    /**
     * Returns verbatim value of the property with the specified name or an empty
     * {@code Optional} if there is no property with the specified name.
     * <p>
     * Property values in the "release" file are enclosed in double quotes.
     * This method returns the value with the double quotes.
     *
     * @param propertyName the property name
     */
    public Optional<String> findRawProperty(String propertyName) {
        return Optional.ofNullable(props.getProperty(propertyName));
    }

    /**
     * Returns unquoted value of the property with the specified name or an empty
     * {@code Optional} if there is no property with the specified name.
     * <p>
     * Property values in the "release" file are enclosed in double quotes. This
     * method returns the value without the double quotes.
     *
     * @param propertyName the property name
     */
    public Optional<String> findProperty(String propertyName) {
        return findRawProperty(propertyName).map(v -> {
            if (v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
                return v.substring(1, v.length() - 1);
            } else {
                return v;
            }
        });
    }

    /**
     * Returns the value of the "JAVA_VERSION" property.
     * <p>
     * Will throw {@code NoSuchElementException} if there is no such property. Will
     * use {@link Runtime.Version#parse(String)} method to parse version string. Any
     * exception that it may yield will be passed to the caller verbatim.
     *
     * @throws NoSuchElementException if there is no such property
     */
    public Runtime.Version getJavaVersion() {
        return findProperty("JAVA_VERSION").map(Runtime.Version::parse).orElseThrow(NoSuchElementException::new);
    }

    /**
     * Returns the value of the "MODULES" property.
     *
     * @throws NoSuchElementException if there is no such property
     */
    public List<String> getModules() {
        return findProperty("MODULES").map(v -> {
            return List.of(v.split("\\s+"));
        }).orElseThrow(NoSuchElementException::new);
    }

    private final Properties props;
}
