/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record CannedFormattedString(BiFunction<String, Object[], String> formatter, String key, Object[] args) {

    @FunctionalInterface
    public interface CannedArgument {
        public String value();
    }

    public static Object cannedArgument(Supplier<Object> supplier, String label) {
        Objects.requireNonNull(supplier);
        Objects.requireNonNull(label);
        return new CannedArgument() {

            @Override
            public String value() {
                return supplier.get().toString();
            }

            @Override
            public String toString( ) {
                return label;
            }
        };
    }

    public static Object cannedAbsolutePath(Path v) {
        return cannedArgument(() -> v.toAbsolutePath(), String.format("AbsolutePath(%s)", v));
    }

    public static Object cannedAbsolutePath(String v) {
        return cannedAbsolutePath(Path.of(v));
    }

    public CannedFormattedString {
        Objects.requireNonNull(formatter);
        Objects.requireNonNull(key);
        Objects.requireNonNull(args);
        List.of(args).forEach(Objects::requireNonNull);
    }

    public String getValue() {
        return formatter.apply(key, Stream.of(args).map(arg -> {
            if (arg instanceof CannedArgument cannedArg) {
                return cannedArg.value();
            } else {
                return arg;
            }
        }).toArray());
    }

    @Override
    public String toString() {
        if (args.length == 0) {
            return String.format("%s", key);
        } else {
            return String.format("%s+%s", key, List.of(args));
        }
    }
}
