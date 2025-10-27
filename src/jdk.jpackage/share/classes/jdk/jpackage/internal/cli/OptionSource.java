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
package jdk.jpackage.internal.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Source where command line options come from.
 */
sealed interface OptionSource {

    static boolean isCommandLine(OptionSource v) {
        return v.equals(COMMAND_LINE);
    }

    static Optional<Path> asFile(OptionSource v) {
        return Optional.of(v)
                .filter(Details.FileOptionSource.class::isInstance)
                .map(Details.FileOptionSource.class::cast)
                .map(Details.FileOptionSource::path);
    }

    static OptionSource fromFile(Path v) {
        return new Details.FileOptionSource(v);
    }

    static final class Details {

        private Details() {
        }

        private static final class CommandLineOptionSource implements OptionSource {
            private CommandLineOptionSource() {
            }
        }

        private record FileOptionSource(Path path) implements OptionSource {
            FileOptionSource {
                Objects.requireNonNull(path);
            }
        }
    }

    static final OptionSource COMMAND_LINE = new Details.CommandLineOptionSource();
}
