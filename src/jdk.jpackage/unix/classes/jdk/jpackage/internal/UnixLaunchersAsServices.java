/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Application;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper to install launchers as services for Unix installers.
 */
class UnixLaunchersAsServices extends ShellCustomAction {

    UnixLaunchersAsServices(Path outputRoot, Application app, List<String> requiredPackages,
            Function<Launcher, UnixLauncherAsService> factory) {

        Objects.requireNonNull(factory);
        this.outputRoot = Objects.requireNonNull(outputRoot);
        this.requiredPackages = Objects.requireNonNull(requiredPackages);

        // Read launchers information
        launchers = app.launchers().stream().filter(Launcher::isService).map(factory::apply).toList();
    }

    @Override
    final List<String> requiredPackages() {
        if (launchers.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requiredPackages;
        }
    }

    @Override
    protected List<String> replacementStringIds() {
        return REPLACEMENT_STRING_IDS;
    }

    @Override
    protected Map<String, String> createImpl() throws IOException {
        Map<String, String> data = new HashMap<>();

        if (launchers.isEmpty()) {
            return data;
        }

        var installedDescriptorFiles = launchers.stream().map(
                launcher -> enqouter.applyTo(launcher.descriptorFilePath(
                        Path.of("/")).toString())).toList();

        Function<String, String> strigifier = cmd -> {
            return stringifyShellCommands(Stream.of(List.of(
                    cmd), installedDescriptorFiles).flatMap(x -> x.stream()).collect(
                    Collectors.joining(" ")));
        };

        data.put(SCRIPTS, stringifyTextFile("services_utils.sh"));

        data.put(COMMANDS_INSTALL, strigifier.apply("register_services"));
        data.put(COMMANDS_UNINSTALL, strigifier.apply("unregister_services"));

        for (var launcher : launchers) {
            launcher.getResource().saveToFile(launcher.descriptorFilePath(outputRoot));
        }

        return data;
    }

    boolean isEmpty() {
        return launchers.isEmpty();
    }

    abstract static class UnixLauncherAsService extends LauncherAsService {

        UnixLauncherAsService(Application app, Launcher launcher, OverridableResource resource) {
            super(app, launcher, resource);
        }

        abstract Path descriptorFilePath(Path root);
    }

    private final Path outputRoot;
    private final List<String> requiredPackages;
    private final List<UnixLauncherAsService> launchers;
    private final Enquoter enqouter = Enquoter.forShellLiterals();

    private static final String COMMANDS_INSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_INSTALL";
    private static final String COMMANDS_UNINSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_UNINSTALL";
    private static final String SCRIPTS = "LAUNCHER_AS_SERVICE_SCRIPTS";

    protected static final List<String> REPLACEMENT_STRING_IDS = List.of(
            COMMANDS_INSTALL, COMMANDS_UNINSTALL, SCRIPTS);
}
