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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import jdk.internal.util.OperatingSystem;

/**
 * The standard context for parsing command line options.
 */
record StandardOptionContext(OperatingSystem os, OptionSource src) {

    StandardOptionContext {
        Objects.requireNonNull(os);
        Objects.requireNonNull(src);
    }

    StandardOptionContext(OperatingSystem os) {
        this(os, OptionSource.COMMAND_LINE);
    }

    StandardOptionContext() {
        this(OperatingSystem.current());
    }

    StandardOptionContext forFile(Path file) {
        return new StandardOptionContext(os, OptionSource.fromFile(file));
    }

    Optional<Path> asFileSource() {
        return OptionSource.asFile(src);
    }

    <T> OptionSpec<T> mapOptionSpec(OptionSpec<T> optionSpec) {
        return OptionSpecMapperOptionScope.mapOptionSpec(optionSpec, this);
    }

    static <T> Consumer<OptionSpecBuilder<T>> createOptionSpecBuilderMutator(
            BiConsumer<OptionSpecBuilder<T>, StandardOptionContext> mutator) {
        return OptionSpecMapperOptionScope.createOptionSpecBuilderMutator(StandardOptionContext.class, mutator);
    }
}
