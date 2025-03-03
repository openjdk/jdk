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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jdk.jpackage.internal.model.FileAssociation;

final class FileAssociationScaner {

    record Result(Map<MimeType, List<FileAssociation>> mimeTypesWithMultipleExtensions,
            Map<Extension, List<FileAssociation>> extensionsWithMultipleMimeTypes,
            Map<MimeTypeWithExtension, List<FileAssociation>> duplicates) {
    }

    Result scan(Collection<FileAssociation> fileAssociations) {
        if (fileAssociations.stream().map(Box::new).distinct().count() != fileAssociations.size()) {
            throw new IllegalArgumentException("Multiple occurrences of the same fa instance in the given container");
        }

        final var mimeTypesWithMultipleExtensions = fileAssociations.stream().collect(
                collectingAndThen(groupingBy(MimeType::new), FileAssociationScaner::filterDuplicates));
        final var extensionsWithMultipleMimeTypes = fileAssociations.stream().collect(
                collectingAndThen(groupingBy(Extension::new), FileAssociationScaner::filterDuplicates));
        final var duplicates = fileAssociations.stream().collect(
                collectingAndThen(groupingBy(MimeTypeWithExtension::new), FileAssociationScaner::filterDuplicates));

        return new Result(mimeTypesWithMultipleExtensions, extensionsWithMultipleMimeTypes, duplicates);
    }

    private static <T> Map<T, List<FileAssociation>> filterDuplicates(Map<T, List<FileAssociation>> v) {
        return v.entrySet().stream().filter(e -> {
            return e.getValue().size() > 1;
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    record MimeTypeWithExtension(String mimeType, String extension) {
        MimeTypeWithExtension(FileAssociation fa) {
            this(fa.mimeType(), fa.extension());
        }
    }

    record MimeType(String value) {
        MimeType(FileAssociation fa) {
            this(fa.mimeType());
        }
    }

    record Extension(String value) {
        Extension(FileAssociation fa) {
            this(fa.extension());
        }
    }

    private static class Box {
        Box(FileAssociation value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return value == ((Box)other).value;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        private final FileAssociation value;
    }
}
