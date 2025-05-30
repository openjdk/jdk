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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.FileAssociation;

final record FileAssociationGroup(List<FileAssociation> items) {

    FileAssociationGroup {
        Objects.requireNonNull(items);
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    static Stream<FileAssociation> flatMap(Stream<FileAssociationGroup> groups) {
        return groups.map(FileAssociationGroup::items).flatMap(List::stream);
    }

    static UnaryOperator<FileAssociationGroup> map(UnaryOperator<FileAssociation> mapper) {
        return group -> {
            return new FileAssociationGroup(group.items.stream().map(mapper).filter(Objects::nonNull).toList());
        };
    }

    static UnaryOperator<FileAssociationGroup> filter(Predicate<FileAssociation> filter) {
        return group -> {
            return new FileAssociationGroup(group.items.stream().filter(filter).toList());
        };
    }

    static Builder build() {
        return new Builder();
    }

    static class FileAssociationException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    static final class FileAssociationNoMimesException extends FileAssociationException {

        private static final long serialVersionUID = 1L;
    }

    static final class FileAssociationNoExtensionsException extends FileAssociationException {

        private static final long serialVersionUID = 1L;
    }

    static final class Builder {

        private Builder() {
        }

        FileAssociationGroup create() throws FileAssociationException {
            if (mimeTypes == null || mimeTypes.isEmpty()) {
                throw new FileAssociationNoMimesException();
            }

            if (extensions == null || extensions.isEmpty()) {
                throw new FileAssociationNoExtensionsException();
            }

            return new FileAssociationGroup(mimeTypes.stream().map(mimeType -> {
                return extensions.stream().map(ext -> {
                    return createFileAssociation(mimeType, ext);
                });
            }).flatMap(x -> x).toList());
        }

        Builder icon(Path v) {
            icon = v;
            return this;
        }

        Builder description(String v) {
            description = v;
            return this;
        }

        Builder mimeTypes(Collection<String> v) {
            mimeTypes = Set.copyOf(v);
            return this;
        }

        Builder extensions(Collection<String> v) {
            extensions = Set.copyOf(v);
            return this;
        }

        private FileAssociation createFileAssociation(String mimeType, String ext) {
            Objects.requireNonNull(ext);
            Objects.requireNonNull(mimeType);
            return new FileAssociation.Stub(Optional.ofNullable(description), Optional.ofNullable(icon), mimeType, ext);
        }

        private Path icon;
        private String description;
        private Set<String> mimeTypes;
        private Set<String> extensions;
    }
}
