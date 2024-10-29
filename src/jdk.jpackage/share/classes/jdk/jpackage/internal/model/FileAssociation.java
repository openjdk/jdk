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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

public interface FileAssociation {

    String description();

    Path icon();

    String mimeType();

    String extension();

    record Impl(String description, Path icon, String mimeType, String extension) implements FileAssociation {

        public Impl {
            Objects.requireNonNull(description);
        }
    }

    static class Proxy<T extends FileAssociation> extends ProxyBase<T> implements FileAssociation {

        protected Proxy(T target) {
            super(target);
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public Path icon() {
            return target.icon();
        }

        @Override
        public String mimeType() {
            return target.mimeType();
        }

        @Override
        public String extension() {
            return target.extension();
        }
    }

    static Optional<FileAssociation> create(FileAssociation src) {
        var mimeType = src.mimeType();
        if (mimeType == null) {
            return Optional.empty();
        }

        var extension = src.extension();
        if (extension == null) {
            return Optional.empty();
        }

        return Optional.of(new Impl(src.description(), src.icon(), mimeType, extension));
    }

    static Stream<FileAssociation> create(Stream<FileAssociation> sources) throws ConfigException {
        var fas = sources.map(FileAssociation::create).filter(Optional::isPresent).map(
                Optional::get).toList();

        // Check extension to mime type relationship is 1:1
        var mimeTypeToExtension = fas.stream().collect(groupingBy(
                FileAssociation::extension, mapping(FileAssociation::mimeType,
                        toList())));
        for (var entry : mimeTypeToExtension.entrySet()) {
            if (entry.getValue().size() != 1) {
                var extension = entry.getKey();
                throw ConfigException.build().message(
                        "error.fa-extension-with-multiple-mime-types", extension).create();
            }
        }

        return fas.stream();
    }
}
