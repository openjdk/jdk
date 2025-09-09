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
package jdk.jpackage.internal.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility class to manage application launchers.
 * <p>
 * Use {@link #asList} to get application launchers as a list.
 * <p>
 * Use {@link #mainLauncher()} to get the main application launcher.
 * <p>
 * Use {@link #additionalLaunchers()} to get additional application launchers.
 * <p>
 * Use {@link #fromList} to convert the list of application launchers into {@link ApplicationLaunchers} instance.
 */
public record ApplicationLaunchers(Launcher mainLauncher, List<Launcher> additionalLaunchers) {

    public ApplicationLaunchers {
        Objects.requireNonNull(mainLauncher);
        Objects.requireNonNull(additionalLaunchers);
    }

    public ApplicationLaunchers(Launcher mainLauncher) {
        this(mainLauncher, List.of());
    }

    public List<Launcher> asList() {
        return Optional.ofNullable(mainLauncher).map(v -> {
            return Stream.concat(Stream.of(v), additionalLaunchers.stream()).toList();
        }).orElseGet(List::of);
    }

    public static Optional<ApplicationLaunchers> fromList(List<Launcher> launchers) {
        if (launchers == null || launchers.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new ApplicationLaunchers(launchers.getFirst(),
                    launchers.subList(1, launchers.size())));
        }
    }
}
