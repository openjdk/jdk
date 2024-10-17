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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

abstract class LinuxPackageBundler extends AbstractBundler {

    LinuxPackageBundler(BundlerParamInfo<? extends LinuxPackage> pkgParam) {
        this.pkgParam = pkgParam;
        appImageBundler = new LinuxAppBundler().setDependentTask(true);
        customActions = List.of(new CustomActionInstance(
                DesktopIntegration::create), new CustomActionInstance(
                LinuxLaunchersAsServices::create));
    }

    @Override
    public final boolean validate(Map<String, ? super Object> params)
            throws ConfigException {

        // Order is important!
        LinuxPackage pkg = pkgParam.fetchFrom(params);
        var workshop = WorkshopFromParams.WORKSHOP.fetchFrom(params);

        // run basic validation to ensure requirements are met
        // we are not interested in return code, only possible exception
        appImageBundler.validate(params);

        FileAssociation.verify(FileAssociation.fetchFrom(params));

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
        doValidate(workshop, pkg);

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
        var workshop = WorkshopFromParams.WORKSHOP.fetchFrom(params);

        Workshop pkgWorkshop = new Workshop.Proxy(workshop) {
            @Override
            public Path appImageDir() {
                return buildRoot().resolve("pkg-image");
            }
        };

        params.put(WorkshopFromParams.WORKSHOP.getID(), pkgWorkshop);

        Function<Path, ApplicationLayout> initAppImageLayout = imageRoot -> {
            ApplicationLayout layout = pkg.app().appLayout();
            layout.pathGroup().setPath(new Object(),
                    AppImageFile.getPathInAppImage(Path.of("")));
            return layout.resolveAt(imageRoot);
        };

        try {
            Path appImage = pkg.predefinedAppImage();

            // we either have an application image or need to build one
            if (appImage != null) {
                initAppImageLayout.apply(appImage).copy(pkg.appLayout().resolveAt(pkgWorkshop
                        .appImageDir()));
            } else {
                Files.createDirectories(workshop.appImageDir().getParent());
                appImageBundler.execute(params, workshop.appImageDir().getParent());
                Files.delete(AppImageFile.getPathInAppImage(workshop.appImageDir()));
                if (pkg.isInstallDirInUsrTree()) {
                    initAppImageLayout.apply(workshop.appImageDir()).copy(pkg.appLayout().resolveAt(
                            pkgWorkshop.appImageDir()));
                }
            }

            for (var ca : customActions) {
                ca.init(pkgWorkshop, pkg);
            }

            Map<String, String> data = createDefaultReplacementData(workshop, pkg);

            for (var ca : customActions) {
                ShellCustomAction.mergeReplacementData(data, ca.instance.
                        create());
            }

            data.putAll(createReplacementData(workshop, pkg));

            Path packageBundle = buildPackageBundle(Collections.unmodifiableMap(
                    data), workshop, pkg, outputParentDir);

            verifyOutputBundle(workshop, pkg, packageBundle).stream()
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

    private List<String> getListOfNeededPackages(Workshop workshop) throws IOException {

        final List<String> caPackages = customActions.stream()
                .map(ca -> ca.instance)
                .map(ShellCustomAction::requiredPackages)
                .flatMap(List::stream).toList();

        final List<String> neededLibPackages;
        if (withFindNeededPackages) {
            LibProvidersLookup lookup = new LibProvidersLookup();
            initLibProvidersLookup(lookup);

            neededLibPackages = lookup.execute(workshop.appImageDir());
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

    private Map<String, String> createDefaultReplacementData(Workshop workshop, LinuxPackage pkg) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_PACKAGE", pkg.packageName());
        data.put("APPLICATION_VENDOR", pkg.app().vendor());
        data.put("APPLICATION_VERSION", pkg.version());
        data.put("APPLICATION_DESCRIPTION", pkg.description());

        String defaultDeps = String.join(", ", getListOfNeededPackages(workshop));
        String customDeps = pkg.additionalDependencies();
        if (!customDeps.isEmpty() && !defaultDeps.isEmpty()) {
            customDeps = ", " + customDeps;
        }
        data.put("PACKAGE_DEFAULT_DEPENDENCIES", defaultDeps);
        data.put("PACKAGE_CUSTOM_DEPENDENCIES", customDeps);

        return data;
    }

    protected abstract List<ConfigException> verifyOutputBundle(
            Workshop workshop, LinuxPackage pkg, Path packageBundle);

    protected abstract void initLibProvidersLookup(LibProvidersLookup libProvidersLookup);

    protected abstract List<ToolValidator> getToolValidators();

    protected abstract void doValidate(Workshop workshop, LinuxPackage pkg)
            throws ConfigException;

    protected abstract Map<String, String> createReplacementData(
            Workshop workshop, LinuxPackage pkg) throws IOException;

    protected abstract Path buildPackageBundle(
            Map<String, String> replacementData,
            Workshop workshop, LinuxPackage pkg, Path outputParentDir) throws
            PackagerException, IOException;

    private final BundlerParamInfo<? extends LinuxPackage> pkgParam;
    private final Bundler appImageBundler;
    private boolean withFindNeededPackages;
    private final List<CustomActionInstance> customActions;

    private static final class CustomActionInstance {

        CustomActionInstance(ShellCustomActionFactory factory) {
            this.factory = factory;
        }

        void init(Workshop workshop, Package pkg) throws IOException {
            instance = factory.create(workshop, pkg);
            Objects.requireNonNull(instance);
        }

        private final ShellCustomActionFactory factory;
        ShellCustomAction instance;
    }
}
