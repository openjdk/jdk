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

import static jdk.jpackage.internal.FromOptions.buildApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.MacRuntimeValidator.validateRuntimeHasJliLib;
import static jdk.jpackage.internal.MacRuntimeValidator.validateRuntimeHasNoBinDir;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.APPCLASS;
import static jdk.jpackage.internal.cli.StandardOption.ICON;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_CATEGORY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_IMAGE_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_STORE;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_IDENTIFIER;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_NAME;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_SIGNING_PREFIX;
import static jdk.jpackage.internal.cli.StandardOption.MAC_DMG_CONTENT;
import static jdk.jpackage.internal.cli.StandardOption.MAC_ENTITLEMENTS;
import static jdk.jpackage.internal.cli.StandardOption.MAC_INSTALLER_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGN;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGNING_KEYCHAIN;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGNING_KEY_NAME;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.model.MacPackage.RUNTIME_BUNDLE_LAYOUT;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.ApplicationBuilder.MainLauncherStartupInfo;
import jdk.jpackage.internal.SigningIdentityBuilder.SigningConfigException;
import jdk.jpackage.internal.SigningIdentityBuilder.StandardCertificateSelector;
import jdk.jpackage.internal.cli.OptionValue;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardFaOption;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacLauncher;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.RootedPath;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class MacFromOptions {

    static MacApplication createMacApplication(Options options) {
        return createMacApplicationInternal(options).app();
    }

    static MacDmgPackage createMacDmgPackage(Options options) {

        final var app = createMacApplicationInternal(options);

        final var superPkgBuilder = createMacPackageBuilder(options, app, MAC_DMG);

        final var pkgBuilder = new MacDmgPackageBuilder(superPkgBuilder);

        MAC_DMG_CONTENT.findIn(options).map((List<Collection<RootedPath>> v) -> {
            // Reverse the order of content sources.
            // If there are multiple source files for the same
            // destination file, only the first will be used.
            // Reversing the order of content sources makes it use the last file
            // from the original list of source files for the given destination file.
            return v.reversed().stream().flatMap(Collection::stream).toList();
        }).ifPresent(pkgBuilder::dmgRootDirSources);

        return pkgBuilder.create();
    }

    static MacPkgPackage createMacPkgPackage(Options options) {

        //
        // One of "MacSignTest.testExpiredCertificate" test cases expects
        // two error messages about expired certificates in the output: one for
        // certificate for signing an app image, another certificate for signing a PKG.
        // So creation of a PKG package is a bit messy.
        //

        final boolean sign = MAC_SIGN.findIn(options).orElse(false);
        final boolean appStore = MAC_APP_STORE.findIn(options).orElse(false);

        final Optional<SigningIdentityBuilder> pkgSigningIdentityBuilder;

        if (sign && (MAC_INSTALLER_SIGN_IDENTITY.containsIn(options) || MAC_SIGNING_KEY_NAME.containsIn(options))) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(options);
            MAC_INSTALLER_SIGN_IDENTITY.ifPresentIn(options, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(options).ifPresent(userName -> {
                final StandardCertificateSelector domain;
                if (appStore) {
                    domain = StandardCertificateSelector.APP_STORE_PKG_INSTALLER;
                } else {
                    domain = StandardCertificateSelector.PKG_INSTALLER;
                }

                signingIdentityBuilder.certificateSelector(StandardCertificateSelector.create(userName, domain));
            });

            pkgSigningIdentityBuilder = Optional.of(signingIdentityBuilder);
        } else {
            pkgSigningIdentityBuilder = Optional.empty();
        }

        ApplicationWithDetails app = null;
        try {
            app = createMacApplicationInternal(options);
        } catch (RuntimeException appEx) {
            rethrowIfNotSigningConfigException(appEx);
            try {
                pkgSigningIdentityBuilder.ifPresent(SigningIdentityBuilder::create);
            } catch (RuntimeException pkgEx) {

                if (Objects.equals(appEx.getMessage(), pkgEx.getMessage())) {
                    // Don't report the same error twice.
                    throw appEx;
                }

                // Use suppressed exceptions to communicate multiple exceptions to the caller.
                // The top-level error handling code assumes there is a causal connection
                // between the exception and its suppressed exceptions.
                // Based on this assumption and following the principle "Present cause before consequence",
                // it will report the suppressed exceptions before reporting the caught exception.
                pkgEx.addSuppressed(appEx);
                throw pkgEx;
            }

            throw appEx;
        }

        final var superPkgBuilder = createMacPackageBuilder(options, app, MAC_PKG);
        final var pkgBuilder = new MacPkgPackageBuilder(superPkgBuilder);

        pkgSigningIdentityBuilder.ifPresent(pkgBuilder::signingBuilder);

        return pkgBuilder.create();
    }

    private record ApplicationWithDetails(MacApplication app, Optional<ExternalApplication> externalApp) {
        ApplicationWithDetails {
            Objects.requireNonNull(app);
            Objects.requireNonNull(externalApp);
        }
    }

    private static ApplicationWithDetails createMacApplicationInternal(Options options) {

        final var predefinedRuntimeLayout = PREDEFINED_RUNTIME_IMAGE.findIn(options)
                .map(MacPackage::guessRuntimeLayout);

        predefinedRuntimeLayout.ifPresent(layout -> {
            validateRuntimeHasJliLib(layout);
            if (MAC_APP_STORE.containsIn(options)) {
                validateRuntimeHasNoBinDir(layout);
            }
        });

        final var launcherFromOptions = new LauncherFromOptions().faMapper(MacFromOptions::createMacFa);

        final var superAppBuilder = buildApplicationBuilder()
                .runtimeLayout(RUNTIME_BUNDLE_LAYOUT)
                .predefinedRuntimeLayout(predefinedRuntimeLayout.map(RuntimeLayout::unresolve).orElse(null))
                .create(options, launcherOptions -> {
                    var launcher = launcherFromOptions.create(launcherOptions);
                    return MacLauncher.create(launcher);
                }, (MacLauncher _, Launcher launcher) -> {
                    return MacLauncher.create(launcher);
                }, APPLICATION_LAYOUT);

        if (PREDEFINED_APP_IMAGE.containsIn(options)) {
            // Set the main launcher start up info.
            // AppImageFile assumes the main launcher start up info is available when
            // it is constructed from Application instance.
            // This happens when jpackage signs predefined app image.
            final var appImageFileOptions = superAppBuilder.externalApplication().orElseThrow().extra();
            final var mainLauncherStartupInfo = new MainLauncherStartupInfo(APPCLASS.getFrom(appImageFileOptions));
            final var launchers = superAppBuilder.launchers().orElseThrow();
            final var mainLauncher = ApplicationBuilder.overrideLauncherStartupInfo(launchers.mainLauncher(), mainLauncherStartupInfo);
            superAppBuilder.launchers(new ApplicationLaunchers(MacLauncher.create(mainLauncher), launchers.additionalLaunchers()));
        }

        final var app = superAppBuilder.create();

        final var appBuilder = new MacApplicationBuilder(app);

        PREDEFINED_APP_IMAGE.findIn(options)
                .map(MacBundle::new)
                .map(MacBundle::infoPlistFile)
                .ifPresent(appBuilder::externalInfoPlistFile);

        ICON.ifPresentIn(options, appBuilder::icon);
        MAC_BUNDLE_NAME.ifPresentIn(options, appBuilder::bundleName);
        MAC_BUNDLE_IDENTIFIER.ifPresentIn(options, appBuilder::bundleIdentifier);
        MAC_APP_CATEGORY.ifPresentIn(options, appBuilder::category);

        final boolean sign;
        final boolean appStore;

        if (PREDEFINED_APP_IMAGE.containsIn(options) && OptionUtils.bundlingOperation(options) != SIGN_MAC_APP_IMAGE) {
            final var appImageFileOptions = superAppBuilder.externalApplication().orElseThrow().extra();
            sign = MAC_SIGN.getFrom(appImageFileOptions);
            appStore = MAC_APP_STORE.getFrom(appImageFileOptions);
        } else {
            sign = MAC_SIGN.getFrom(options);
            appStore = MAC_APP_STORE.getFrom(options);
        }

        appBuilder.appStore(appStore);

        if (sign && (MAC_APP_IMAGE_SIGN_IDENTITY.containsIn(options) || MAC_SIGNING_KEY_NAME.containsIn(options))) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(options);
            MAC_APP_IMAGE_SIGN_IDENTITY.ifPresentIn(options, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(options).ifPresent(userName -> {
                final StandardCertificateSelector domain;
                if (appStore) {
                    domain = StandardCertificateSelector.APP_STORE_APP_IMAGE;
                } else {
                    domain = StandardCertificateSelector.APP_IMAGE;
                }

                signingIdentityBuilder.certificateSelector(StandardCertificateSelector.create(userName, domain));
            });

            final var signingBuilder = new AppImageSigningConfigBuilder(signingIdentityBuilder);
            if (appStore) {
                signingBuilder.entitlementsResourceName("sandbox.plist");
            }

            app.mainLauncher().flatMap(Launcher::startupInfo).ifPresentOrElse(
                signingBuilder::signingIdentifierPrefix,
                () -> {
                    // Runtime installer does not have the main launcher, use
                    // 'bundleIdentifier' as the prefix by default.
                    var bundleIdentifier = appBuilder.create().bundleIdentifier();
                    signingBuilder.signingIdentifierPrefix(bundleIdentifier + ".");
                });
            MAC_BUNDLE_SIGNING_PREFIX.ifPresentIn(options, signingBuilder::signingIdentifierPrefix);

            MAC_ENTITLEMENTS.ifPresentIn(options, signingBuilder::entitlements);

            appBuilder.signingBuilder(signingBuilder);
        }

        return new ApplicationWithDetails(appBuilder.create(), superAppBuilder.externalApplication());
    }

    private static MacPackageBuilder createMacPackageBuilder(Options options, ApplicationWithDetails app, PackageType type) {

        final var builder = new MacPackageBuilder(createPackageBuilder(options, app.app(), type));

        for (OptionValue<Path> ov : List.of(PREDEFINED_APP_IMAGE, PREDEFINED_RUNTIME_IMAGE)) {
            ov.findIn(options)
                    .flatMap(MacBundle::fromPath)
                    .map(MacPackagingPipeline::isSigned)
                    .ifPresent(builder::predefinedAppImageSigned);
        }

        return builder;
    }

    private static void rethrowIfNotSigningConfigException(Exception ex) {
        var signingConfigExceptionFound = Slot.<Boolean>createEmpty();

        ExceptionBox.visitUnboxedExceptionsRecursively(ex, visited -> {
            if (visited instanceof SigningConfigException) {
                signingConfigExceptionFound.set(true);
            }
        });

        if (!signingConfigExceptionFound.find().orElse(false)) {
            throw toUnchecked(ex);
        }
    }

    private static SigningIdentityBuilder createSigningIdentityBuilder(Options options) {
        final var builder = new SigningIdentityBuilder();
        MAC_SIGNING_KEYCHAIN.findIn(options).map(Path::toString).ifPresent(builder::keychain);
        return builder;
    }

    private static MacFileAssociation createMacFa(Options options, FileAssociation fa) {

        final var builder = new MacFileAssociationBuilder();

        StandardFaOption.MAC_CFBUNDLETYPEROLE.ifPresentIn(options, builder::cfBundleTypeRole);
        StandardFaOption.MAC_LSHANDLERRANK.ifPresentIn(options, builder::lsHandlerRank);
        StandardFaOption.MAC_NSSTORETYPEKEY.ifPresentIn(options, builder::nsPersistentStoreTypeKey);
        StandardFaOption.MAC_NSDOCUMENTCLASS.ifPresentIn(options, builder::nsDocumentClass);
        StandardFaOption.MAC_LSTYPEISPACKAGE.ifPresentIn(options, builder::lsTypeIsPackage);
        StandardFaOption.MAC_LSDOCINPLACE.ifPresentIn(options, builder::lsSupportsOpeningDocumentsInPlace);
        StandardFaOption.MAC_UIDOCBROWSER.ifPresentIn(options, builder::uiSupportsDocumentBrowser);
        StandardFaOption.MAC_NSEXPORTABLETYPES.ifPresentIn(options, builder::nsExportableTypes);
        StandardFaOption.MAC_UTTYPECONFORMSTO.ifPresentIn(options, builder::utTypeConformsTo);

        return builder.create(fa);
    }
}
