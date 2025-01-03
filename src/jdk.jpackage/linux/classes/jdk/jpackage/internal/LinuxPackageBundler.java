/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.ConfigException;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ApplicationLayout;

abstract class LinuxPackageBundler extends AbstractBundler {

    LinuxPackageBundler(BundlerParamInfo<? extends LinuxPackage> pkgParam) {
        this.pkgParam = pkgParam;
        customActions = List.of(new CustomActionInstance(
                DesktopIntegration::create), new CustomActionInstance(
                LinuxLaunchersAsServices::create));
    }

    @Override
    public final boolean validate(Map<String, ? super Object> params)
            throws ConfigException {

        // Order is important!
        LinuxPackage pkg = pkgParam.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        for (var validator: getToolValidators()) {
            ConfigException ex = validator.validate();
            if (ex != null) {
                throw ex;
            }
        }

        if (!isDefault()) {
            withFindNeededPackages = false;
            Log.verbose(MessageFormat.format(I18N.getString(
                    "message.not-default-bundler-no-dependencies-lookup"),
                    getName()));
        } else {
            withFindNeededPackages = LibProvidersLookup.supported();
            if (!withFindNeededPackages) {
                final String advice;
                if ("deb".equals(getID())) {
                    advice = "message.deb-ldd-not-available.advice";
                } else {
                    advice = "message.rpm-ldd-not-available.advice";
                }
                // Let user know package dependencies will not be generated.
                Log.error(String.format("%s\n%s", I18N.getString(
                        "message.ldd-not-available"), I18N.getString(advice)));
            }
        }

        // Packaging specific validation
        doValidate(env, pkg);

        return true;
    }

    @Override
    public final String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public final Path execute(Map<String, ? super Object> params,
            Path outputParentDir) throws PackagerException {
        IOUtils.writableOutputDir(outputParentDir);

        // Order is important!
        LinuxPackage pkg = pkgParam.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        BuildEnv pkgEnv = BuildEnv.withAppImageDir(env,
                env.buildRoot().resolve("image"));

        try {
            // We either have an application image or need to build one.
            if (pkg.app().runtimeBuilder().isPresent()) {
                // Runtime builder is present, build app image.
                LinuxAppImageBuilder.build()
                        .excludeDirFromCopying(outputParentDir)
                        .create(pkg).execute(pkgEnv);
            } else {
                Path srcAppImageDir = pkg.predefinedAppImage().orElseGet(() -> {
                    // No predefined app image and no runtime builder.
                    // This should be runtime packaging.
                    if (pkg.isRuntimeInstaller()) {
                        return env.appImageDir();
                    } else {
                        // Can't create app image without runtime builder.
                        throw new UnsupportedOperationException();
                    }
                });

                var srcLayout = pkg.appImageLayout().resolveAt(srcAppImageDir);
                var srcLayoutPathGroup = AppImageLayout.toPathGroup(srcLayout);

                if (srcLayout instanceof ApplicationLayout appLayout) {
                    // Copy app layout omitting application image info file.
                    srcLayoutPathGroup.ghostPath(AppImageFile2.getPathInAppImage(appLayout));
                }

                srcLayoutPathGroup.copy(AppImageLayout.toPathGroup(
                        pkg.packageLayout().resolveAt(pkgEnv.appImageDir())));
            }

            for (var ca : customActions) {
                ca.init(pkgEnv, pkg);
            }

            Map<String, String> data = createDefaultReplacementData(pkgEnv, pkg);

            for (var ca : customActions) {
                ShellCustomAction.mergeReplacementData(data, ca.instance.create());
            }

            data.putAll(createReplacementData(pkgEnv, pkg));

            Path packageBundle = buildPackageBundle(Collections.unmodifiableMap(
                    data), pkgEnv, pkg, outputParentDir);

            verifyOutputBundle(pkgEnv, pkg, packageBundle).stream()
                    .filter(Objects::nonNull)
                    .forEachOrdered(ex -> {
                Log.verbose(ex.getLocalizedMessage());
                Log.verbose(ex.getAdvice());
            });

            return packageBundle;
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private List<String> getListOfNeededPackages(BuildEnv env) throws IOException {

        final List<String> caPackages = customActions.stream()
                .map(ca -> ca.instance)
                .map(ShellCustomAction::requiredPackages)
                .flatMap(List::stream).toList();

        final List<String> neededLibPackages;
        if (withFindNeededPackages) {
            LibProvidersLookup lookup = new LibProvidersLookup();
            initLibProvidersLookup(lookup);

            neededLibPackages = lookup.execute(env.appImageDir());
        } else {
            neededLibPackages = Collections.emptyList();
            Log.info(I18N.getString("warning.foreign-app-image"));
        }

        // Merge all package lists together.
        // Filter out empty names, sort and remove duplicates.
        List<String> result = Stream.of(caPackages, neededLibPackages).flatMap(
                List::stream).filter(Predicate.not(String::isEmpty)).sorted().distinct().toList();

        Log.verbose(String.format("Required packages: %s", result));

        return result;
    }

    private Map<String, String> createDefaultReplacementData(BuildEnv env, LinuxPackage pkg) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_PACKAGE", pkg.packageName());
        data.put("APPLICATION_VENDOR", pkg.app().vendor());
        data.put("APPLICATION_VERSION", pkg.version());
        data.put("APPLICATION_DESCRIPTION", pkg.description());

        String defaultDeps = String.join(", ", getListOfNeededPackages(env));
        String customDeps = pkg.additionalDependencies().orElse("");
        if (!customDeps.isEmpty() && !defaultDeps.isEmpty()) {
            customDeps = ", " + customDeps;
        }
        data.put("PACKAGE_DEFAULT_DEPENDENCIES", defaultDeps);
        data.put("PACKAGE_CUSTOM_DEPENDENCIES", customDeps);

        return data;
    }

    protected abstract List<ConfigException> verifyOutputBundle(
            BuildEnv env, LinuxPackage pkg, Path packageBundle);

    protected abstract void initLibProvidersLookup(LibProvidersLookup libProvidersLookup);

    protected abstract List<ToolValidator> getToolValidators();

    protected abstract void doValidate(BuildEnv env, LinuxPackage pkg)
            throws ConfigException;

    protected abstract Map<String, String> createReplacementData(
            BuildEnv env, LinuxPackage pkg) throws IOException;

    protected abstract Path buildPackageBundle(
            Map<String, String> replacementData,
            BuildEnv env, LinuxPackage pkg, Path outputParentDir) throws
            PackagerException, IOException;

    private final BundlerParamInfo<? extends LinuxPackage> pkgParam;
    private boolean withFindNeededPackages;
    private final List<CustomActionInstance> customActions;

    private static final class CustomActionInstance {

        CustomActionInstance(ShellCustomActionFactory factory) {
            this.factory = factory;
        }

        void init(BuildEnv env, Package pkg) throws IOException {
            instance = factory.create(env, pkg);
            Objects.requireNonNull(instance);
        }

        private final ShellCustomActionFactory factory;
        ShellCustomAction instance;
    }
}
