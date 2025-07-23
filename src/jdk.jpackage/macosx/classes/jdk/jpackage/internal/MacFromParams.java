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

import static jdk.jpackage.internal.BundlerParamInfo.createBooleanBundlerParam;
import static jdk.jpackage.internal.BundlerParamInfo.createPathBundlerParam;
import static jdk.jpackage.internal.BundlerParamInfo.createStringBundlerParam;
import static jdk.jpackage.internal.FromParams.createApplicationBuilder;
import static jdk.jpackage.internal.FromParams.createApplicationBundlerParam;
import static jdk.jpackage.internal.FromParams.createPackageBuilder;
import static jdk.jpackage.internal.FromParams.createPackageBundlerParam;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.StandardBundlerParam.DMG_CONTENT;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;
import static jdk.jpackage.internal.StandardBundlerParam.hasPredefinedAppImage;
import static jdk.jpackage.internal.model.MacPackage.RUNTIME_PACKAGE_LAYOUT;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import jdk.jpackage.internal.ApplicationBuilder.MainLauncherStartupInfo;
import jdk.jpackage.internal.SigningIdentityBuilder.ExpiredCertificateException;
import jdk.jpackage.internal.SigningIdentityBuilder.StandardCertificateSelector;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacLauncher;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class MacFromParams {

    private static MacApplication createMacApplication(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var predefinedRuntimeLayout = PREDEFINED_RUNTIME_IMAGE.findIn(params).map(predefinedRuntimeImage -> {
            if (Files.isDirectory(RUNTIME_PACKAGE_LAYOUT.resolveAt(predefinedRuntimeImage).runtimeDirectory())) {
                return RUNTIME_PACKAGE_LAYOUT;
            } else {
                return RuntimeLayout.DEFAULT;
            }
        });

        final var launcherFromParams = new LauncherFromParams(Optional.of(MacFromParams::createMacFa));

        final var superAppBuilder = createApplicationBuilder(params, toFunction(launcherParams -> {
            var launcher = launcherFromParams.create(launcherParams);
            return MacLauncher.create(launcher);
        }), APPLICATION_LAYOUT, predefinedRuntimeLayout);

        if (hasPredefinedAppImage(params)) {
            // Set the main launcher start up info.
            // AppImageFile assumes the main launcher start up info is available when
            // it is constructed from Application instance.
            // This happens when jpackage signs predefined app image.
            final var mainLauncherStartupInfo = new MainLauncherStartupInfo(PREDEFINED_APP_IMAGE_FILE.fetchFrom(params).getMainClass());
            final var launchers = superAppBuilder.launchers().orElseThrow();
            final var mainLauncher = ApplicationBuilder.overrideLauncherStartupInfo(launchers.mainLauncher(), mainLauncherStartupInfo);
            superAppBuilder.launchers(new ApplicationLaunchers(MacLauncher.create(mainLauncher), launchers.additionalLaunchers()));
        }

        final var app = superAppBuilder.create();

        final var appBuilder = new MacApplicationBuilder(app);

        if (hasPredefinedAppImage(params)) {
            appBuilder.externalInfoPlistFile(PREDEFINED_APP_IMAGE.findIn(params).orElseThrow().resolve("Contents/Info.plist"));
        }

        ICON.copyInto(params, appBuilder::icon);
        MAC_CF_BUNDLE_NAME.copyInto(params, appBuilder::bundleName);
        MAC_CF_BUNDLE_IDENTIFIER.copyInto(params, appBuilder::bundleIdentifier);
        APP_CATEGORY.copyInto(params, appBuilder::category);

        final boolean sign;
        final boolean appStore;

        if (hasPredefinedAppImage(params) && PACKAGE_TYPE.findIn(params).filter(Predicate.isEqual("app-image")).isEmpty()) {
            final var appImageFileExtras = new MacAppImageFileExtras(PREDEFINED_APP_IMAGE_FILE.fetchFrom(params));
            sign = appImageFileExtras.signed();
            appStore = appImageFileExtras.appStore();
        } else {
            sign = SIGN_BUNDLE.findIn(params).orElse(false);
            appStore = APP_STORE.findIn(params).orElse(false);
        }

        appBuilder.appStore(appStore);

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(params);
            APP_IMAGE_SIGN_IDENTITY.copyInto(params, signingIdentityBuilder::signingIdentity);
            SIGNING_KEY_USER.findIn(params).ifPresent(userName -> {
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

            final var bundleIdentifier = appBuilder.create().bundleIdentifier();
            app.mainLauncher().flatMap(Launcher::startupInfo).ifPresentOrElse(
                signingBuilder::signingIdentifierPrefix,
                () -> {
                    // Runtime installer does not have main launcher, so use
                    // 'bundleIdentifier' as prefix by default.
                    signingBuilder.signingIdentifierPrefix(
                        bundleIdentifier + ".");
                });
            SIGN_IDENTIFIER_PREFIX.copyInto(params, signingBuilder::signingIdentifierPrefix);

            ENTITLEMENTS.copyInto(params, signingBuilder::entitlements);

            appBuilder.signingBuilder(signingBuilder);
        }

        return appBuilder.create();
    }

    private static MacPackageBuilder createMacPackageBuilder(
            Map<String, ? super Object> params, MacApplication app,
            PackageType type) throws ConfigException {
        final var builder = new MacPackageBuilder(createPackageBuilder(params, app, type));

        PREDEFINED_APP_IMAGE_FILE.findIn(params)
                .map(MacAppImageFileExtras::new)
                .map(MacAppImageFileExtras::signed)
                .ifPresent(builder::predefinedAppImageSigned);

        PREDEFINED_RUNTIME_IMAGE.findIn(params)
                .map(MacBundle::new)
                .filter(MacBundle::isValid)
                .map(MacBundle::isSigned)
                .ifPresent(builder::predefinedAppImageSigned);

        return builder;
    }

    private static MacDmgPackage createMacDmgPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var app = APPLICATION.fetchFrom(params);

        final var superPkgBuilder = createMacPackageBuilder(params, app, MAC_DMG);

        final var pkgBuilder = new MacDmgPackageBuilder(superPkgBuilder);

        DMG_CONTENT.copyInto(params, pkgBuilder::dmgContent);

        return pkgBuilder.create();
    }

    private record WithExpiredCertificateException<T>(Optional<T> obj, Optional<ExpiredCertificateException> certEx) {
        WithExpiredCertificateException {
            if (obj.isEmpty() == certEx.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        static <U> WithExpiredCertificateException<U> of(Callable<U> callable) {
            try {
                return new WithExpiredCertificateException<>(Optional.of(callable.call()), Optional.empty());
            } catch (ExpiredCertificateException ex) {
                return new WithExpiredCertificateException<>(Optional.empty(), Optional.of(ex));
            } catch (ExceptionBox ex) {
                if (ex.getCause() instanceof ExpiredCertificateException certEx) {
                    return new WithExpiredCertificateException<>(Optional.empty(), Optional.of(certEx));
                }
                throw ex;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Throwable t) {
                throw ExceptionBox.rethrowUnchecked(t);
            }
        }
    }

    private static MacPkgPackage createMacPkgPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        // This is over complicated to make "MacSignTest.testExpiredCertificate" test pass.

        final boolean sign = SIGN_BUNDLE.findIn(params).orElse(false);
        final boolean appStore = APP_STORE.findIn(params).orElse(false);

        final var appOrExpiredCertEx = WithExpiredCertificateException.of(() -> {
            return APPLICATION.fetchFrom(params);
        });

        final Optional<MacPkgPackageBuilder> pkgBuilder;
        if (appOrExpiredCertEx.obj().isPresent()) {
            final var superPkgBuilder = createMacPackageBuilder(params, appOrExpiredCertEx.obj().orElseThrow(), MAC_PKG);
            pkgBuilder = Optional.of(new MacPkgPackageBuilder(superPkgBuilder));
        } else {
            pkgBuilder = Optional.empty();
        }

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(params);
            INSTALLER_SIGN_IDENTITY.copyInto(params, signingIdentityBuilder::signingIdentity);
            SIGNING_KEY_USER.findIn(params).ifPresent(userName -> {
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
                final var expiredPkgCert = WithExpiredCertificateException.of(() -> {
                    return signingIdentityBuilder.create();
                }).certEx();
                expiredPkgCert.map(ConfigException::getMessage).ifPresent(Log::error);
                throw appOrExpiredCertEx.certEx().orElseThrow();
            }
        }

        return pkgBuilder.orElseThrow().create();
    }

    private static SigningIdentityBuilder createSigningIdentityBuilder(Map<String, ? super Object> params) {
        final var builder = new SigningIdentityBuilder();
        SIGNING_KEYCHAIN.copyInto(params, builder::keychain);
        return builder;
    }

    private static MacFileAssociation createMacFa(FileAssociation fa, Map<String, ? super Object> params) {

        final var builder = new MacFileAssociationBuilder();

        FA_MAC_CFBUNDLETYPEROLE.copyInto(params, builder::cfBundleTypeRole);
        FA_MAC_LSHANDLERRANK.copyInto(params, builder::lsHandlerRank);
        FA_MAC_NSSTORETYPEKEY.copyInto(params, builder::nsPersistentStoreTypeKey);
        FA_MAC_NSDOCUMENTCLASS.copyInto(params, builder::nsDocumentClass);
        FA_MAC_LSTYPEISPACKAGE.copyInto(params, builder::lsTypeIsPackage);
        FA_MAC_LSDOCINPLACE.copyInto(params, builder::lsSupportsOpeningDocumentsInPlace);
        FA_MAC_UIDOCBROWSER.copyInto(params, builder::uiSupportsDocumentBrowser);
        FA_MAC_NSEXPORTABLETYPES.copyInto(params, builder::nsExportableTypes);
        FA_MAC_UTTYPECONFORMSTO.copyInto(params, builder::utTypeConformsTo);

        return toFunction(builder::create).apply(fa);
    }

    static final BundlerParamInfo<MacApplication> APPLICATION = createApplicationBundlerParam(
            MacFromParams::createMacApplication);

    static final BundlerParamInfo<MacDmgPackage> DMG_PACKAGE = createPackageBundlerParam(
            MacFromParams::createMacDmgPackage);

    static final BundlerParamInfo<MacPkgPackage> PKG_PACKAGE = createPackageBundlerParam(
            MacFromParams::createMacPkgPackage);

    private static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_NAME.getId());

    private static final BundlerParamInfo<String> APP_CATEGORY = createStringBundlerParam(
            Arguments.CLIOptions.MAC_CATEGORY.getId());

    private static final BundlerParamInfo<Path> ENTITLEMENTS = createPathBundlerParam(
            Arguments.CLIOptions.MAC_ENTITLEMENTS.getId());

    private static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId());

    private static final BundlerParamInfo<String> SIGN_IDENTIFIER_PREFIX = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_SIGNING_PREFIX.getId());

    private static final BundlerParamInfo<String> APP_IMAGE_SIGN_IDENTITY = createStringBundlerParam(
            Arguments.CLIOptions.MAC_APP_IMAGE_SIGN_IDENTITY.getId());

    private static final BundlerParamInfo<String> INSTALLER_SIGN_IDENTITY = createStringBundlerParam(
            Arguments.CLIOptions.MAC_INSTALLER_SIGN_IDENTITY.getId());

    private static final BundlerParamInfo<String> SIGNING_KEY_USER = createStringBundlerParam(
            Arguments.CLIOptions.MAC_SIGNING_KEY_NAME.getId());

    private static final BundlerParamInfo<String> SIGNING_KEYCHAIN = createStringBundlerParam(
            Arguments.CLIOptions.MAC_SIGNING_KEYCHAIN.getId());

    private static final BundlerParamInfo<String> PACKAGE_TYPE = createStringBundlerParam(
            Arguments.CLIOptions.PACKAGE_TYPE.getId());

    private static final BundlerParamInfo<Boolean> APP_STORE = createBooleanBundlerParam(
            Arguments.CLIOptions.MAC_APP_STORE.getId());

    private static final BundlerParamInfo<String> FA_MAC_CFBUNDLETYPEROLE = createStringBundlerParam(
            Arguments.MAC_CFBUNDLETYPEROLE);

    private static final BundlerParamInfo<String> FA_MAC_LSHANDLERRANK = createStringBundlerParam(
            Arguments.MAC_LSHANDLERRANK);

    private static final BundlerParamInfo<String> FA_MAC_NSSTORETYPEKEY = createStringBundlerParam(
            Arguments.MAC_NSSTORETYPEKEY);

    private static final BundlerParamInfo<String> FA_MAC_NSDOCUMENTCLASS = createStringBundlerParam(
            Arguments.MAC_NSDOCUMENTCLASS);

    private static final BundlerParamInfo<Boolean> FA_MAC_LSTYPEISPACKAGE = createBooleanBundlerParam(
            Arguments.MAC_LSTYPEISPACKAGE);

    private static final BundlerParamInfo<Boolean> FA_MAC_LSDOCINPLACE = createBooleanBundlerParam(
            Arguments.MAC_LSDOCINPLACE);

    private static final BundlerParamInfo<Boolean> FA_MAC_UIDOCBROWSER = createBooleanBundlerParam(
            Arguments.MAC_UIDOCBROWSER);

    @SuppressWarnings("unchecked")
    private static final BundlerParamInfo<List<String>> FA_MAC_NSEXPORTABLETYPES =
            new BundlerParamInfo<>(
                    Arguments.MAC_NSEXPORTABLETYPES,
                    (Class<List<String>>) (Object) List.class,
                    null,
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    @SuppressWarnings("unchecked")
    private static final BundlerParamInfo<List<String>> FA_MAC_UTTYPECONFORMSTO =
            new BundlerParamInfo<>(
                    Arguments.MAC_UTTYPECONFORMSTO,
                    (Class<List<String>>) (Object) List.class,
                    null,
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );
}
