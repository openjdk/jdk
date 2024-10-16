/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jnativescan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

sealed interface ClassFileSource {
    String moduleName();
    Path path();

    Stream<byte[]> classFiles(Runtime.Version version) throws IOException;

    record Module(ModuleReference reference) implements ClassFileSource {
        @Override
        public String moduleName() {
            return reference.descriptor().name();
        }

        @Override
        public Path path() {
            URI location = reference.location().orElseThrow();
            return Path.of(location);
        }

        @Override
        public Stream<byte[]> classFiles(Runtime.Version version) throws IOException {
            ModuleReader reader = reference().open();
            return reader.list()
                .filter(resourceName -> resourceName.endsWith(".class"))
                .map(resourceName -> {
                    try (InputStream stream = reader.open(resourceName).orElseThrow()) {
                        return stream.readAllBytes();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    record ClassPathJar(Path path) implements ClassFileSource {
        @Override
        public String moduleName() {
            return "ALL-UNNAMED";
        }

        @Override
        public Stream<byte[]> classFiles(Runtime.Version version) throws IOException {
            JarFile jf = new JarFile(path().toFile(), false, ZipFile.OPEN_READ, version);
            return jf.versionedStream()
                .filter(je -> je.getName().endsWith(".class"))
                .map(je -> {
                    try (InputStream stream = jf.getInputStream(je)){
                        return stream.readAllBytes();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).onClose(() -> {
                    try {
                        jf.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    record ClassPathDirectory(Path path) implements ClassFileSource {
        @Override
        public String moduleName() {
            return "ALL-UNNAMED";
        }

        @Override
        public Stream<byte[]> classFiles(Runtime.Version version) throws IOException {
            return Files.walk(path)
                .filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".class"))
                .map(file -> {
                    try (InputStream stream = Files.newInputStream(file)){
                        return stream.readAllBytes();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }
}
