/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxPackage;

abstract class LinuxPackager<T extends LinuxPackage> implements Consumer<PackagingPipeline.Builder> {

    LinuxPackager(BuildEnv env, T pkg, Path outputDir, LinuxSystemEnvironment sysEnv) {
        this.env = Objects.requireNonNull(env);
        this.pkg = Objects.requireNonNull(pkg);
        this.outputDir = Objects.requireNonNull(outputDir);
        this.withRequiredPackagesLookup = sysEnv.soLookupAvailable() && sysEnv.nativePackageType().equals(pkg.type());

        customActions = List.of(
                DesktopIntegration.create(env, pkg),
                LinuxLaunchersAsServices.create(env, pkg));
    }

    enum LinuxPackageTaskID implements TaskID {
        INIT_REQUIRED_PACKAGES,
        VERIFY_PACKAGE
    }

    @Override
    public void accept(PackagingPipeline.Builder pipelineBuilder) {
        pipelineBuilder
                .task(PackageTaskID.CREATE_CONFIG_FILES)
                        .action(this::buildConfigFiles)
                        .add()
                .task(LinuxPackageTaskID.INIT_REQUIRED_PACKAGES)
                        .addDependencies(PrimaryTaskID.BUILD_APPLICATION_IMAGE, PrimaryTaskID.COPY_APP_IMAGE)
                        .addDependent(PackageTaskID.CREATE_CONFIG_FILES)
                        .action(this::initRequiredPackages)
                        .add()
                .task(LinuxPackageTaskID.VERIFY_PACKAGE)
                        .addDependencies(PackageTaskID.CREATE_PACKAGE_FILE)
                        .addDependent(PrimaryTaskID.PACKAGE)
                        .action(this::verifyOutputPackage)
                        .add()
                .task(PackageTaskID.CREATE_PACKAGE_FILE)
                        .action(this::buildPackage)
                        .add();
    }

    protected final Path outputPackageFile() {
        return outputDir.resolve(pkg.packageFileNameWithSuffix());
    }

    protected abstract void buildPackage() throws IOException;

    protected abstract List<? extends Exception> findErrorsInOutputPackage() throws IOException;

    protected abstract void createConfigFiles(Map<String, String> replacementData) throws IOException;

    protected abstract Map<String, String> createReplacementData() throws IOException;

    protected abstract void initLibProvidersLookup(LibProvidersLookup libProvidersLookup);

    private void buildConfigFiles() throws IOException {

        final var data = createDefaultReplacementData();

        for (var ca : customActions) {
            ShellCustomAction.mergeReplacementData(data, ca.create());
        }

        data.putAll(createReplacementData());

        createConfigFiles(Collections.unmodifiableMap(data));
    }

    private Map<String, String> createDefaultReplacementData() {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_PACKAGE", pkg.packageName());
        data.put("APPLICATION_VENDOR", pkg.app().vendor());
        data.put("APPLICATION_VERSION", pkg.version());
        data.put("APPLICATION_DESCRIPTION", pkg.description());

        String defaultDeps = String.join(", ", requiredPackages);
        String customDeps = pkg.additionalDependencies().orElse("");
        if (!customDeps.isEmpty() && !defaultDeps.isEmpty()) {
            customDeps = ", " + customDeps;
        }
        data.put("PACKAGE_DEFAULT_DEPENDENCIES", defaultDeps);
        data.put("PACKAGE_CUSTOM_DEPENDENCIES", customDeps);

        return data;
    }

    private void initRequiredPackages() throws IOException {

        final List<String> caPackages = customActions.stream()
                .map(ShellCustomAction::requiredPackages)
                .flatMap(List::stream).toList();

        final List<String> neededLibPackages;
        if (withRequiredPackagesLookup) {
            neededLibPackages = findRequiredPackages();
        } else {
            neededLibPackages = Collections.emptyList();
            Log.info(I18N.getString("warning.foreign-app-image"));
        }

        // Merge all package lists together.
        // Filter out empty names, sort and remove duplicates.
        Stream.of(caPackages, neededLibPackages)
                .flatMap(List::stream)
                .filter(Predicate.not(String::isEmpty))
                .sorted().distinct().forEach(requiredPackages::add);

        Log.verbose(String.format("Required packages: %s", requiredPackages));
    }

    private List<String> findRequiredPackages() throws IOException {
        var lookup = new LibProvidersLookup();
        initLibProvidersLookup(lookup);
        return lookup.execute(env.appImageDir());
    }

    private void verifyOutputPackage() {
        final List<? extends Exception> errors;
        try {
            errors = findErrorsInOutputPackage();
        } catch (Exception ex) {
            // Ignore error as it is not critical. Just report it.
            Log.verbose(ex);
            return;
        }

        for (var ex : errors) {
            Log.verbose(ex.getLocalizedMessage());
            if (ex instanceof ConfigException cfgEx) {
                Log.verbose(cfgEx.getAdvice());
            }
        }
    }

    protected final BuildEnv env;
    protected final T pkg;
    protected final Path outputDir;
    private final boolean withRequiredPackagesLookup;
    private final List<String> requiredPackages = new ArrayList<>();
    private final List<ShellCustomAction> customActions;
}
