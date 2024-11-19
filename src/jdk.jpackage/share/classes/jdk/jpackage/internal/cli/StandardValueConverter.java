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

package jdk.jpackage.internal.cli;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;


final class StandardValueConverter {
    final static ValueConverter<String> IDENTITY_CONV = new ValueConverter<>() {
        @Override
        public String convert(String value) {
            return Objects.requireNonNull(value);
        }

        @Override
        public Class<? extends String> valueType() {
            return String.class;
        }
    };

    final static ValueConverter<Path> PATH_CONV = new ValueConverter<>() {
        @Override
        public Path convert(String value) {
            return Path.of(value);
        }

        @Override
        public Class<? extends Path> valueType() {
            return Path.class;
        }
    };

    final static ValueConverter<String[]> STRING_ARRAY_CONV = new ValueConverter<>() {
        @Override
        public String[] convert(String value) {
            return value.split("[,\\s]");
        }

        @Override
        public Class<? extends String[]> valueType() {
            return String[].class;
        }
    };

    final static ValueConverter<Path[]> PATH_ARRAY_CONV = new ValueConverter<>() {
        @Override
        public Path[] convert(String value) {
            return Stream.of(value.split(File.pathSeparator)).map(Path::of).toArray(Path[]::new);
        }

        @Override
        public Class<? extends Path[]> valueType() {
            return Path[].class;
        }
    };
}
