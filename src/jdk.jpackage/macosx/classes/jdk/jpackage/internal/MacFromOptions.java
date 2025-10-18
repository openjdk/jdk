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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.FromOptions.buildApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.MacRuntimeValidator.validateRuntimeHasJliLib;
import static jdk.jpackage.internal.MacRuntimeValidator.validateRuntimeHasNoBinDir;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
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
import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.ApplicationBuilder.MainLauncherStartupInfo;
import jdk.jpackage.internal.SigningIdentityBuilder.ExpiredCertificateException;
import jdk.jpackage.internal.SigningIdentityBuilder.StandardCertificateSelector;
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
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class MacFromOptions {

    static MacApplication createMacApplication(Options optionValues) {
        return createMacApplicationInternal(optionValues).app();
    }

    static MacDmgPackage createMacDmgPackage(Options optionValues) {

        final var app = createMacApplicationInternal(optionValues);

        final var superPkgBuilder = createMacPackageBuilder(optionValues, app, MAC_DMG);

        final var pkgBuilder = new MacDmgPackageBuilder(superPkgBuilder);

        MAC_DMG_CONTENT.ifPresentIn(optionValues, pkgBuilder::dmgContent);

        return pkgBuilder.create();
    }

    static MacPkgPackage createMacPkgPackage(Options optionValues) {

        //
        // One of "MacSignTest.testExpiredCertificate" test cases expects
        // two error messages about expired certificates in the output: one for
        // certificate for signing an app image, another certificate for signing a PKG.
        // So creation of a PKG package is a bit messy.
        //

        final boolean sign = MAC_SIGN.findIn(optionValues).orElse(false);
        final boolean appStore = MAC_APP_STORE.findIn(optionValues).orElse(false);

        final var appResult = Result.create(() -> createMacApplicationInternal(optionValues));

        final Optional<MacPkgPackageBuilder> pkgBuilder;
        if (appResult.hasValue()) {
            final var superPkgBuilder = createMacPackageBuilder(optionValues, appResult.orElseThrow(), MAC_PKG);
            pkgBuilder = Optional.of(new MacPkgPackageBuilder(superPkgBuilder));
        } else {
            // Failed to create an app. Is it because of the expired certificate?
            rethrowIfNotExpiredCertificateException(appResult);
            // Yes, the certificate for signing the app image has expired.
            // Keep going, try to create a signing config for the package.
            pkgBuilder = Optional.empty();
        }

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(optionValues);
            MAC_INSTALLER_SIGN_IDENTITY.ifPresentIn(optionValues, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(optionValues).ifPresent(userName -> {
                final StandardCertificateSelector domain;
                if (appStore) {
                    domain = StandardCertificateSelector.APP_STORE_PKG_INSTALLER;
                } else {
                    domain = StandardCertificateSelector.PKG_INSTALLER;
                }

                signingIdentityBuilder.certificateSelector(StandardCertificateSelector.create(userName, domain));
            });

            if (pkgBuilder.isPresent()) {
                pkgBuilder.orElseThrow().signingBuilder(signingIdentityBuilder);
            } else {
                //
                // The certificate for signing the app image has expired. Can not create a
                // package because there is no app.
                // Try to create a signing config for the package and see if the certificate for
                // signing the package is also expired.
                //

                final var expiredAppCertException = appResult.firstError().orElseThrow();

                final var pkgSignConfigResult = Result.create(signingIdentityBuilder::create);
                try {
                    rethrowIfNotExpiredCertificateException(pkgSignConfigResult);
                    // The certificate for the package signing config is also expired!
                } catch (RuntimeException ex) {
                    // Some error occurred trying to configure the signing config for the package.
                    // Ignore it, bail out with the first error.
                    rethrowUnchecked(expiredAppCertException);
                }

                Log.error(pkgSignConfigResult.firstError().orElseThrow().getMessage());
                rethrowUnchecked(expiredAppCertException);
            }
        }

        return pkgBuilder.orElseThrow().create();
    }

    private record ApplicationWithDetails(MacApplication app, Optional<ExternalApplication> externalApp) {
        ApplicationWithDetails {
            Objects.requireNonNull(app);
            Objects.requireNonNull(externalApp);
        }
    }

    private static ApplicationWithDetails createMacApplicationInternal(Options optionValues) {

        final var predefinedRuntimeLayout = PREDEFINED_RUNTIME_IMAGE.findIn(optionValues)
                .map(MacPackage::guessRuntimeLayout);

        predefinedRuntimeLayout.ifPresent(layout -> {
            validateRuntimeHasJliLib(predefinedRuntimeLayout.orElseThrow());
            if (MAC_APP_STORE.containsIn(optionValues)) {
                validateRuntimeHasNoBinDir(predefinedRuntimeLayout.orElseThrow());
            }
        });

        final var launcherFromOptions = new LauncherFromOptions().faMapper(MacFromOptions::createMacFa);

        final var superAppBuilder = buildApplicationBuilder()
                .runtimeLayout(RUNTIME_BUNDLE_LAYOUT)
                .predefinedRuntimeLayout(predefinedRuntimeLayout.map(RuntimeLayout::unresolve).orElse(null))
                .create(optionValues, launcherOptionValues -> {
                    var launcher = launcherFromOptions.create(launcherOptionValues);
                    return MacLauncher.create(launcher);
                }, APPLICATION_LAYOUT);

        if (PREDEFINED_APP_IMAGE.containsIn(optionValues)) {
            // Set the main launcher start up info.
            // AppImageFile assumes the main launcher start up info is available when
            // it is constructed from Application instance.
            // This happens when jpackage signs predefined app image.
            final var mainLauncherStartupInfo = new MainLauncherStartupInfo(superAppBuilder.mainLauncherClassName().orElseThrow());
            final var launchers = superAppBuilder.launchers().orElseThrow();
            final var mainLauncher = ApplicationBuilder.overrideLauncherStartupInfo(launchers.mainLauncher(), mainLauncherStartupInfo);
            superAppBuilder.launchers(new ApplicationLaunchers(MacLauncher.create(mainLauncher), launchers.additionalLaunchers()));
        }

        final var app = superAppBuilder.create();

        final var appBuilder = new MacApplicationBuilder(app);

        PREDEFINED_APP_IMAGE.findIn(optionValues)
                .map(MacBundle::new)
                .map(MacBundle::infoPlistFile)
                .ifPresent(appBuilder::externalInfoPlistFile);

        ICON.ifPresentIn(optionValues, appBuilder::icon);
        MAC_BUNDLE_NAME.ifPresentIn(optionValues, appBuilder::bundleName);
        MAC_BUNDLE_IDENTIFIER.ifPresentIn(optionValues, appBuilder::bundleIdentifier);
        MAC_APP_CATEGORY.ifPresentIn(optionValues, appBuilder::category);

        final boolean sign;
        final boolean appStore;

        if (PREDEFINED_APP_IMAGE.containsIn(optionValues) && OptionUtils.bundlingOperation(optionValues) != SIGN_MAC_APP_IMAGE) {
            final var appImageFileOptionValues = superAppBuilder.externalApplication().orElseThrow().getExtra();
            sign = MAC_SIGN.getFrom(appImageFileOptionValues);
            appStore = MAC_APP_STORE.getFrom(appImageFileOptionValues);
        } else {
            sign = MAC_SIGN.getFrom(optionValues);
            appStore = MAC_APP_STORE.getFrom(optionValues);
        }

        appBuilder.appStore(appStore);

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(optionValues);
            MAC_APP_IMAGE_SIGN_IDENTITY.ifPresentIn(optionValues, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(optionValues).ifPresent(userName -> {
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
            MAC_BUNDLE_SIGNING_PREFIX.ifPresentIn(optionValues, signingBuilder::signingIdentifierPrefix);

            MAC_ENTITLEMENTS.ifPresentIn(optionValues, signingBuilder::entitlements);

            appBuilder.signingBuilder(signingBuilder);
        }

        return new ApplicationWithDetails(appBuilder.create(), superAppBuilder.externalApplication());
    }

    private static MacPackageBuilder createMacPackageBuilder(Options optionValues, ApplicationWithDetails app, PackageType type) {

        final var builder = new MacPackageBuilder(createPackageBuilder(optionValues, app.app(), type));

        app.externalApp()
                .map(ExternalApplication::getExtra)
                .flatMap(MAC_SIGN::findIn)
                .ifPresent(builder::predefinedAppImageSigned);

        PREDEFINED_RUNTIME_IMAGE.findIn(optionValues)
                .map(MacBundle::new)
                .filter(MacBundle::isValid)
                .map(MacBundle::isSigned)
                .ifPresent(builder::predefinedAppImageSigned);

        return builder;
    }

    private static void rethrowIfNotExpiredCertificateException(Result<?> result) {
        final var ex = result.firstError().orElseThrow();

        if (ex instanceof ExpiredCertificateException) {
            return;
        }

        if (ex instanceof ExceptionBox box) {
            if (box.getCause() instanceof Exception cause) {
                rethrowIfNotExpiredCertificateException(Result.ofError(cause));
            }
        }

        rethrowUnchecked(ex);
    }

    private static SigningIdentityBuilder createSigningIdentityBuilder(Options optionValues) {
        final var builder = new SigningIdentityBuilder();
        MAC_SIGNING_KEYCHAIN.findIn(optionValues).map(Path::toString).ifPresent(builder::keychain);
        return builder;
    }

    private static MacFileAssociation createMacFa(Options optionValues, FileAssociation fa) {

        final var builder = new MacFileAssociationBuilder();

        StandardFaOption.MAC_CFBUNDLETYPEROLE.ifPresentIn(optionValues, builder::cfBundleTypeRole);
        StandardFaOption.MAC_LSHANDLERRANK.ifPresentIn(optionValues, builder::lsHandlerRank);
        StandardFaOption.MAC_NSSTORETYPEKEY.ifPresentIn(optionValues, builder::nsPersistentStoreTypeKey);
        StandardFaOption.MAC_NSDOCUMENTCLASS.ifPresentIn(optionValues, builder::nsDocumentClass);
        StandardFaOption.MAC_LSTYPEISPACKAGE.ifPresentIn(optionValues, builder::lsTypeIsPackage);
        StandardFaOption.MAC_LSDOCINPLACE.ifPresentIn(optionValues, builder::lsSupportsOpeningDocumentsInPlace);
        StandardFaOption.MAC_UIDOCBROWSER.ifPresentIn(optionValues, builder::uiSupportsDocumentBrowser);
        StandardFaOption.MAC_NSEXPORTABLETYPES.ifPresentIn(optionValues, builder::nsExportableTypes);
        StandardFaOption.MAC_UTTYPECONFORMSTO.ifPresentIn(optionValues, builder::utTypeConformsTo);

        return builder.create(fa);
    }
}
