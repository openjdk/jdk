/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Provides uniform way to configure {@code JPackageCommand} and
 * {@code PackageTest} instances.
 */
public record ConfigurationTarget(Optional<JPackageCommand> cmd, Optional<PackageTest> test) {

    public ConfigurationTarget {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(test);
        if (cmd.isEmpty() == test.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    public ConfigurationTarget(JPackageCommand target) {
        this(Optional.of(target), Optional.empty());
    }

    public ConfigurationTarget(PackageTest target) {
        this(Optional.empty(), Optional.of(target));
    }

    public ConfigurationTarget apply(Consumer<JPackageCommand> a, Consumer<PackageTest> b) {
        cmd.ifPresent(Objects.requireNonNull(a));
        test.ifPresent(Objects.requireNonNull(b));
        return this;
    }

    public ConfigurationTarget addInitializer(Consumer<JPackageCommand> initializer) {
        cmd.ifPresent(Objects.requireNonNull(initializer));
        test.ifPresent(v -> {
            v.addInitializer(initializer::accept);
        });
        return this;
    }

    public ConfigurationTarget addInstallVerifier(Consumer<JPackageCommand> verifier) {
        cmd.ifPresent(Objects.requireNonNull(verifier));
        test.ifPresent(v -> {
            v.addInstallVerifier(verifier::accept);
        });
        return this;
    }

    public ConfigurationTarget addRunOnceInitializer(Consumer<ConfigurationTarget> initializer) {
        Objects.requireNonNull(initializer);
        cmd.ifPresent(_ -> {
            initializer.accept(this);
        });
        test.ifPresent(v -> {
            v.addRunOnceInitializer(() -> {
                initializer.accept(this);
            });
        });
        return this;
    }

    public ConfigurationTarget add(AdditionalLauncher addLauncher) {
        return apply(addLauncher::applyTo, addLauncher::applyTo);
    }
}
