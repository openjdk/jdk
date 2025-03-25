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
import static jdk.jpackage.internal.MacAppImageBuilder.APP_STORE;
import static jdk.jpackage.internal.MacBaseInstallerBundler.SIGNING_KEYCHAIN;
import static jdk.jpackage.internal.MacBaseInstallerBundler.SIGNING_KEY_USER;
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
import jdk.jpackage.internal.SigningConfigBuilder.StandardCertificateSelector;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacLauncher;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.RuntimeLayout;


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

        final var app = createApplicationBuilder(params, toFunction(launcherParams -> {
            var launcher = launcherFromParams.create(launcherParams);
            return MacLauncher.create(launcher);
        }), APPLICATION_LAYOUT, predefinedRuntimeLayout).create();

        final var appBuilder = new MacApplicationBuilder(app);

        if (hasPredefinedAppImage(params)) {
            appBuilder.externalInfoPlistFile(PREDEFINED_APP_IMAGE.fetchFrom(params).resolve("Contents/Info.plist"));
        }

        ICON.copyInto(params, appBuilder::icon);
        MAC_CF_BUNDLE_NAME.copyInto(params, appBuilder::bundleName);
        MAC_CF_BUNDLE_IDENTIFIER.copyInto(params, appBuilder::bundleIdentifier);
        APP_CATEGORY.copyInto(params, appBuilder::category);

        final boolean sign;
        final boolean appStore;

        if (hasPredefinedAppImage(params)) {
            final var appImageFileExtras = new MacAppImageFileExtras(PREDEFINED_APP_IMAGE_FILE.fetchFrom(params));
            sign = appImageFileExtras.signed();
            appStore = APP_STORE.findIn(params).orElse(false);
        } else {
            sign = SIGN_BUNDLE.findIn(params).orElse(false);
            appStore = APP_STORE.findIn(params).orElse(false);
        }

        appBuilder.appStore(appStore);

        if (sign) {
            final var signingBuilder = new SigningConfigBuilder();
            app.mainLauncher().flatMap(Launcher::startupInfo).ifPresent(signingBuilder::signingIdentityPrefix);
            BUNDLE_ID_SIGNING_PREFIX.copyInto(params, signingBuilder::signingIdentityPrefix);
            SIGNING_KEYCHAIN.copyInto(params, signingBuilder::keychain);
            ENTITLEMENTS.copyInto(params, signingBuilder::entitlements);
            APP_IMAGE_SIGN_IDENTITY.copyInto(params, signingBuilder::signingIdentity);

            final var filter = appStore ? StandardCertificateSelector.APP_STORE_APP_IMAGE : StandardCertificateSelector.APP_IMAGE;

            signingBuilder.addCertificateSelectors(StandardCertificateSelector.create(SIGNING_KEY_USER.findIn(params), filter));

            appBuilder.signingBuilder(signingBuilder);
        }

        return appBuilder.create();
    }

    private static MacDmgPackage createMacDmgPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var app = APPLICATION.fetchFrom(params);

        final var superPkgBuilder = createPackageBuilder(params, app, MAC_DMG);

        final var pkgBuilder = new MacDmgPackageBuilder(superPkgBuilder);

        DMG_CONTENT.copyInto(params, pkgBuilder::dmgContent);

        return pkgBuilder.create();
    }

    private static MacPkgPackage createMacPkgPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var app = APPLICATION.fetchFrom(params);

        final var superPkgBuilder = createPackageBuilder(params, app, MAC_PKG);

        final var pkgBuilder = new MacPkgPackageBuilder(superPkgBuilder);

        return pkgBuilder.create();
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

    static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_NAME.getId());

    static final BundlerParamInfo<String> APP_CATEGORY = createStringBundlerParam(
            Arguments.CLIOptions.MAC_CATEGORY.getId());

    static final BundlerParamInfo<Path> ENTITLEMENTS = createPathBundlerParam(
            Arguments.CLIOptions.MAC_ENTITLEMENTS.getId());


    static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId());

    static final BundlerParamInfo<String> BUNDLE_ID_SIGNING_PREFIX = createStringBundlerParam(
            Arguments.CLIOptions.MAC_BUNDLE_SIGNING_PREFIX.getId());

    static final BundlerParamInfo<String> APP_IMAGE_SIGN_IDENTITY = createStringBundlerParam(
            Arguments.CLIOptions.MAC_APP_IMAGE_SIGN_IDENTITY.getId());

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
