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

import static jdk.jpackage.internal.ApplicationImageUtils.createWriteAppImageFileAction;
import static jdk.jpackage.internal.util.PListWriter.writeArray;
import static jdk.jpackage.internal.util.PListWriter.writeBoolean;
import static jdk.jpackage.internal.util.PListWriter.writeBooleanOptional;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writeKey;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.PListWriter.writeStringArray;
import static jdk.jpackage.internal.util.PListWriter.writeStringOptional;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.internal.util.function.ThrowingBiConsumer.toBiConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.PackagingPipeline.AppImageBuildEnv;
import jdk.jpackage.internal.PackagingPipeline.ApplicationImageTaskAction;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.CopyAppImageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PackageBuildEnv;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskAction;
import jdk.jpackage.internal.PackagingPipeline.TaskContext;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.AppImageSigningConfig;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

final class MacPackagingPipeline {

    enum MacBuildApplicationTaskID implements TaskID {
        RUNTIME_INFO_PLIST,
        COPY_JLILIB,
        APP_ICON,
        PKG_INFO_FILE,
        FA_ICONS,
        APP_INFO_PLIST,
        PACKAGE_FILE,
        SIGN
    }

    enum MacCopyAppImageTaskID implements TaskID {
        COPY_PACKAGE_FILE,
        COPY_RUNTIME_INFO_PLIST,
        COPY_RUNTIME_JLILIB,
        REPLACE_APP_IMAGE_FILE,
        COPY_SIGN
    }

    static AppImageLayout packagingLayout(Package pkg) {
        return pkg.appImageLayout().resolveAt(pkg.relativeInstallDir().getFileName());
    }

    static PackagingPipeline.Builder build(Optional<Package> pkg) {
        final var builder = PackagingPipeline.buildStandard()
                .appContextMapper(appContext -> {
                    return new TaskContextProxy(appContext, true, false);
                })
                .pkgContextMapper(appContext -> {
                    final var isRuntimeInstaller = pkg.map(Package::isRuntimeInstaller).orElse(false);
                    final var withPredefinedAppImage = pkg.flatMap(Package::predefinedAppImage).isPresent();
                    return new TaskContextProxy(appContext, false, isRuntimeInstaller || withPredefinedAppImage);
                })
                .appImageLayoutForPackaging(MacPackagingPipeline::packagingLayout)
                .task(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .packageAction(MacPackagingPipeline::runPostAppImageUserScript).add()
                .task(CopyAppImageTaskID.COPY)
                        .copyAction(MacPackagingPipeline::copyAppImage).add()
                .task(MacBuildApplicationTaskID.RUNTIME_INFO_PLIST)
                        .appImageAction(MacPackagingPipeline::writeRuntimeInfoPlist)
                        .addDependent(BuildApplicationTaskID.CONTENT).add()
                .task(MacBuildApplicationTaskID.COPY_JLILIB)
                        .appImageAction(MacPackagingPipeline::copyJliLib)
                        .addDependency(BuildApplicationTaskID.RUNTIME)
                        .addDependent(BuildApplicationTaskID.CONTENT).add()
                .task(MacBuildApplicationTaskID.APP_ICON)
                        .applicationAction(new ApplicationIcon())
                        .addDependent(BuildApplicationTaskID.CONTENT).add()
                .task(MacBuildApplicationTaskID.PKG_INFO_FILE)
                        .applicationAction(MacPackagingPipeline::writePkgInfoFile)
                        .addDependent(BuildApplicationTaskID.CONTENT).add()
                .task(MacBuildApplicationTaskID.PACKAGE_FILE)
                        .packageAction(MacPackagingPipeline::writePackageFile)
                        .addDependents(BuildApplicationTaskID.CONTENT).add()
                .task(MacCopyAppImageTaskID.REPLACE_APP_IMAGE_FILE)
                        .addDependent(PrimaryTaskID.COPY_APP_IMAGE)
                        .noaction().add()
                .task(MacCopyAppImageTaskID.COPY_PACKAGE_FILE)
                        .packageAction(MacPackagingPipeline::writePackageFile)
                        .addDependencies(CopyAppImageTaskID.COPY)
                        .addDependents(PrimaryTaskID.COPY_APP_IMAGE).add()
                .task(MacCopyAppImageTaskID.COPY_RUNTIME_INFO_PLIST)
                        .appImageAction(MacPackagingPipeline::writeRuntimeInfoPlist)
                        .addDependencies(CopyAppImageTaskID.COPY)
                        .addDependents(PrimaryTaskID.COPY_APP_IMAGE).add()
                .task(MacCopyAppImageTaskID.COPY_RUNTIME_JLILIB)
                        .noaction()
                        .addDependencies(CopyAppImageTaskID.COPY)
                        .addDependents(PrimaryTaskID.COPY_APP_IMAGE).add()
                .task(MacBuildApplicationTaskID.FA_ICONS)
                        .applicationAction(MacPackagingPipeline::writeFileAssociationIcons)
                        .addDependent(BuildApplicationTaskID.CONTENT).add()
                .task(MacBuildApplicationTaskID.APP_INFO_PLIST)
                        .applicationAction(MacPackagingPipeline::writeApplicationInfoPlist)
                        .addDependent(BuildApplicationTaskID.CONTENT).add();

        builder.task(MacBuildApplicationTaskID.SIGN)
                .appImageAction(MacPackagingPipeline::sign)
                .addDependencies(builder.taskGraphSnapshot().getAllTailsOf(PrimaryTaskID.BUILD_APPLICATION_IMAGE))
                .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE)
                .add();

        builder.task(MacCopyAppImageTaskID.COPY_SIGN)
                .appImageAction(MacPackagingPipeline::sign)
                .addDependencies(builder.taskGraphSnapshot().getAllTailsOf(PrimaryTaskID.COPY_APP_IMAGE))
                .addDependent(PrimaryTaskID.COPY_APP_IMAGE)
                .add();

        pkg.ifPresent(p -> {
            final List<TaskID> disabledTasks = new ArrayList<>();

            if (p.type() instanceof SignAppImagePackageType) {
                // This is a phony package signing predefined app image.
                // Don't create ".package" file.
                // Don't copy predefined app image, update it in place.
                // Disable running user script after app image ready.
                // Replace ".jpackage.xml" file.
                // Use app image layout.
                disabledTasks.add(MacCopyAppImageTaskID.COPY_PACKAGE_FILE);
                disabledTasks.add(CopyAppImageTaskID.COPY);
                disabledTasks.add(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT);
                builder.task(MacCopyAppImageTaskID.REPLACE_APP_IMAGE_FILE)
                        .applicationAction(createWriteAppImageFileAction()).add();
                builder.appImageLayoutForPackaging(Package::appImageLayout);
            } else if (p.isRuntimeInstaller()) {

                builder.task(MacCopyAppImageTaskID.COPY_RUNTIME_JLILIB)
                        .appImageAction(MacPackagingPipeline::copyJliLib).add();

                final var predefinedRuntimeBundle = Optional.of(
                        new MacBundle(p.predefinedAppImage().orElseThrow())).filter(MacBundle::isValid);

                // Don't create ".package" file.
                disabledTasks.add(MacCopyAppImageTaskID.COPY_PACKAGE_FILE);

                if (predefinedRuntimeBundle.isPresent()) {
                    // The predefined app image is a macOS bundle.
                    // Disable all alterations of the input bundle, but keep the signing enabled.
                    disabledTasks.addAll(List.of(MacCopyAppImageTaskID.values()));
                    disabledTasks.remove(MacCopyAppImageTaskID.COPY_SIGN);
                }

                if (predefinedRuntimeBundle.map(MacBundle::isSigned).orElse(false) && !((MacPackage)p).app().sign()) {
                    // The predefined app image is a signed bundle; explicit signing is not requested for the package.
                    // Disable the signing, i.e. don't re-sign the input bundle.
                    disabledTasks.add(MacCopyAppImageTaskID.COPY_SIGN);
                }
            } else if (((MacPackage)p).predefinedAppImageSigned().orElse(false)) {
                // This is a signed predefined app image.
                // Don't create ".package" file.
                disabledTasks.add(MacCopyAppImageTaskID.COPY_PACKAGE_FILE);
                // Don't sign the image.
                disabledTasks.add(MacCopyAppImageTaskID.COPY_SIGN);
            }

            for (final var taskId : disabledTasks) {
                builder.task(taskId).noaction().add();
            }
        });

        return builder;
    }

    enum SignAppImagePackageType implements PackageType {
        VALUE;
    }

    static Package createSignAppImagePackage(MacApplication app, BuildEnv env) {
        if (!app.sign()) {
            throw new IllegalArgumentException();
        }
        return toSupplier(() -> {
            return new PackageBuilder(app, SignAppImagePackageType.VALUE).predefinedAppImage(
                    Objects.requireNonNull(env.appImageDir())).installDir(Path.of("/foo")).create();
        }).get();
    }

    private static void copyAppImage(MacPackage pkg, AppImageDesc srcAppImage,
            AppImageDesc dstAppImage) throws IOException {

        boolean predefinedAppImageSigned = pkg.predefinedAppImageSigned().orElse(false);

        var inputRootDirectory = srcAppImage.resolvedAppImagelayout().rootDirectory();

        if (pkg.isRuntimeInstaller() && MacBundle.isDirectoryMacBundle(inputRootDirectory)) {
            // Building runtime package from the input runtime bundle.
            // Copy the input bundle verbatim.
            FileUtils.copyRecursive(
                    inputRootDirectory,
                    dstAppImage.resolvedAppImagelayout().rootDirectory(),
                    LinkOption.NOFOLLOW_LINKS);
        } else {
            PackagingPipeline.copyAppImage(srcAppImage, dstAppImage, !predefinedAppImageSigned);
        }
    }

    private static void copyJliLib(
            AppImageBuildEnv<MacApplication, AppImageLayout> env) throws IOException {

        final var runtimeBundle = runtimeBundle(env);

        final var jliName = Path.of("libjli.dylib");

        try (var walk = Files.walk(env.resolvedLayout().runtimeDirectory().resolve("lib"))) {
            final var jli = walk
                    .filter(file -> file.getFileName().equals(jliName))
                    .findFirst()
                    .orElseThrow();
            Files.createDirectories(runtimeBundle.macOsDir());
            Files.copy(jli, runtimeBundle.macOsDir().resolve(jliName));
        }
    }

    private static void runPostAppImageUserScript(PackageBuildEnv<Package, AppImageLayout> env) throws IOException {
        PackagingPipeline.runPostAppImageUserScript(new PackageBuildEnv<>(
                BuildEnv.withAppImageDir(env.env(), env.env().appImageDir().resolve(env.envLayout().rootDirectory())),
                env.pkg(), env.pkg().appImageLayout(), env.outputDir()));
    }

    private static void writePackageFile(PackageBuildEnv<Package, ApplicationLayout> env) throws IOException {
        new PackageFile(env.pkg().packageName()).save(env.resolvedLayout());
    }

    private static void writePkgInfoFile(
            AppImageBuildEnv<MacApplication, MacApplicationLayout> env) throws IOException {

        final var dir = env.resolvedLayout().contentDirectory();
        Files.createDirectories(dir);
        Files.write(dir.resolve("PkgInfo"),
                "APPL????".getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void writeRuntimeInfoPlist(
            AppImageBuildEnv<MacApplication, AppImageLayout> env) throws IOException {

        final var app = env.app();

        Map<String, String> data = new HashMap<>();
        data.put("CF_BUNDLE_IDENTIFIER", app.bundleIdentifier());
        data.put("CF_BUNDLE_NAME", app.bundleName());
        data.put("CF_BUNDLE_VERSION", app.version());
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", app.shortVersion().toString());
        if (app.isRuntime()) {
            data.put("CF_BUNDLE_VENDOR", app.vendor());
        }

        final String template;
        final String publicName;
        final String category;

        if (app.isRuntime()) {
            template = "Runtime-Info.plist.template";
            publicName = "Info.plist";
            category = "resource.runtime-info-plist";
        } else {
            template = "ApplicationRuntime-Info.plist.template";
            publicName = "Runtime-Info.plist";
            category = "resource.app-runtime-info-plist";
        }

        env.env().createResource(template)
                .setPublicName(publicName)
                .setCategory(I18N.getString(category))
                .setSubstitutionData(data)
                .saveToFile(runtimeBundle(env).infoPlistFile());
    }

    private static void writeApplicationInfoPlist(
            AppImageBuildEnv<MacApplication, MacApplicationLayout> env) throws IOException {

        final var app = env.app();

        final var infoPlistFile = MacBundle.fromAppImageLayout(env.resolvedLayout()).infoPlistFile();

        Log.verbose(I18N.format("message.preparing-info-plist", PathUtils.normalizedAbsolutePathString(infoPlistFile)));

        final String faXml = toSupplier(() -> {
            var buf = new StringWriter();
            var xml = XMLOutputFactory.newInstance().createXMLStreamWriter(buf);
            writeCFBundleDocumentTypes(xml, app);
            writeUTExportedTypeDeclarations(xml, app);
            xml.flush();
            xml.close();
            return buf.toString();
        }).get();

        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ICON_FILE", ApplicationIcon.getPath(app, env.resolvedLayout()).getFileName().toString());
        data.put("DEPLOY_BUNDLE_COPYRIGHT", app.copyright());
        data.put("DEPLOY_LAUNCHER_NAME", app.mainLauncher().orElseThrow().executableNameWithSuffix());
        data.put("DEPLOY_BUNDLE_SHORT_VERSION", app.shortVersion().toString());
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION", app.version());
        data.put("DEPLOY_BUNDLE_NAME", app.bundleName());
        data.put("DEPLOY_BUNDLE_IDENTIFIER", app.bundleIdentifier());
        data.put("DEPLOY_APP_CATEGORY", app.category());
        data.put("DEPLOY_FILE_ASSOCIATIONS", faXml);

        env.env().createResource("Info-lite.plist.template")
                .setCategory(I18N.getString("resource.app-info-plist"))
                .setSubstitutionData(data)
                .setPublicName("Info.plist")
                .saveToFile(infoPlistFile);
    }

    private static void sign(AppImageBuildEnv<MacApplication, AppImageLayout> env) throws IOException {

        final var app = env.app();

        final var codesignConfigBuilder = CodesignConfig.build();
        app.signingConfig().ifPresent(codesignConfigBuilder::from);

        if (app.sign() && app.signingConfig().flatMap(AppImageSigningConfig::entitlements).isEmpty()) {
            final var entitlementsDefaultResource = app.signingConfig().map(
                    AppImageSigningConfig::entitlementsResourceName).orElseThrow();

            final var entitlementsFile = env.env().configDir().resolve(app.name() + ".entitlements");

            env.env().createResource(entitlementsDefaultResource)
                    .setCategory(I18N.getString("resource.entitlements"))
                    .saveToFile(entitlementsFile);

            codesignConfigBuilder.entitlements(entitlementsFile);
        }

        final Runnable signAction = () -> {
            final var appImageDir = env.resolvedLayout().rootDirectory();
            AppImageSigner.createSigner(app, codesignConfigBuilder.create()).accept(appImageDir);
        };

        app.signingConfig().flatMap(AppImageSigningConfig::keychain).map(Keychain::new).ifPresentOrElse(keychain -> {
            toBiConsumer(TempKeychain::withKeychain).accept(unused -> signAction.run(), keychain);
        }, signAction);
    }

    private static void writeCFBundleDocumentTypes(XMLStreamWriter xml,
            MacApplication app) throws XMLStreamException, IOException {
        final var fas = app.fileAssociations().toList();
        if (!fas.isEmpty()) {
            writeKey(xml, "CFBundleDocumentTypes");
            writeArray(xml, toXmlConsumer(() -> {
                for (var fa : fas) {
                    writeDict(xml, toXmlConsumer(() -> {
                        addFaToCFBundleDocumentTypes(xml, app, (MacFileAssociation) fa);
                    }));
                }
            }));
        }
    }

    private static void writeUTExportedTypeDeclarations(XMLStreamWriter xml,
            MacApplication app) throws XMLStreamException, IOException {
        final var fas = app.fileAssociations().toList();
        if (!fas.isEmpty()) {
            writeKey(xml, "UTExportedTypeDeclarations");
            writeArray(xml, toXmlConsumer(() -> {
                for (var fa : fas) {
                    writeDict(xml, toXmlConsumer(() -> {
                        addFaToUTExportedTypeDeclarations(xml, app, (MacFileAssociation) fa);
                    }));
                }
            }));
        }
    }

    private static String faContentType(MacApplication app, MacFileAssociation fa) {
        return String.format("%s.%s", app.bundleIdentifier(), fa.extension());
    }

    private static void faWriteIcon(XMLStreamWriter xml, String key, FileAssociation fa)
            throws XMLStreamException {
        fa.icon().ifPresent(ThrowingConsumer.toConsumer(icon -> {
            writeString(xml, key, icon.getFileName());
        }));
    }

    private static void addFaToCFBundleDocumentTypes(XMLStreamWriter xml,
            MacApplication app, MacFileAssociation fa) throws XMLStreamException, IOException {

        writeStringArray(xml, "LSItemContentTypes", List.of(faContentType(app, fa)));
        writeStringOptional(xml, "CFBundleTypeName", fa.description());
        writeString(xml, "LSHandlerRank", fa.lsHandlerRank());
        writeString(xml, "CFBundleTypeRole", fa.cfBundleTypeRole());
        writeStringOptional(xml, "NSPersistentStoreTypeKey", fa.nsPersistentStoreTypeKey());
        writeStringOptional(xml, "NSDocumentClass", fa.nsDocumentClass());
        writeBoolean(xml, "LSIsAppleDefaultForType", true);
        writeBooleanOptional(xml, "LSTypeIsPackage", fa.lsTypeIsPackage());
        writeBooleanOptional(xml, "LSSupportsOpeningDocumentsInPlace", fa.lsSupportsOpeningDocumentsInPlace());
        writeBooleanOptional(xml, "UISupportsDocumentBrowser", fa.uiSupportsDocumentBrowser());
        faWriteIcon(xml, "CFBundleTypeIconFile", fa);
    }

    private static void addFaToUTExportedTypeDeclarations(XMLStreamWriter xml,
            MacApplication app, MacFileAssociation fa) throws XMLStreamException, IOException {
        writeString(xml, "UTTypeIdentifier", faContentType(app, fa));
        writeStringOptional(xml, "UTTypeDescription", fa.description());
        writeStringArray(xml, "UTTypeConformsTo", fa.utTypeConformsTo());
        faWriteIcon(xml, "UTTypeIconFile", fa);

        writeKey(xml, "UTTypeTagSpecification");
        writeDict(xml, toXmlConsumer(() -> {
            writeStringArray(xml, "public.filename-extension", List.of(fa.extension()));
            writeStringArray(xml, "public.mime-type", List.of(fa.mimeType()));
            writeStringArray(xml, "NSExportableTypes", fa.nsExportableTypes());
        }));
    }

    private static MacBundle runtimeBundle(AppImageBuildEnv<MacApplication, AppImageLayout> env) {
        if (env.app().isRuntime()) {
            return new MacBundle(env.resolvedLayout().rootDirectory());
        } else {
            return new MacBundle(((MacApplicationLayout)env.resolvedLayout()).runtimeRootDirectory());
        }
    }

    private static class ApplicationIcon implements ApplicationImageTaskAction<MacApplication, MacApplicationLayout> {
        static Path getPath(Application app, ApplicationLayout appLayout) {
            return appLayout.desktopIntegrationDirectory().resolve(app.name() + ".icns");
        }

        @Override
        public void execute(AppImageBuildEnv<MacApplication, MacApplicationLayout> env)
                throws IOException {
            final var resource = env.env().createResource("JavaApp.icns").setCategory("icon");

            env.app().icon().ifPresent(resource::setExternal);

            resource.saveToFile(getPath(env.app(), env.resolvedLayout()));
        }
    }

    private static void writeFileAssociationIcons(AppImageBuildEnv<MacApplication, MacApplicationLayout> env) throws IOException {
        for (var faIcon : env.app().fileAssociations()
                .filter(FileAssociation::hasIcon)
                .map(FileAssociation::icon)
                .map(Optional::get).toList()) {
            Files.copy(faIcon, env.resolvedLayout().desktopIntegrationDirectory().resolve(faIcon.getFileName()));
        }
    }

    private record TaskContextProxy(TaskContext delegate, boolean forApp, boolean copyAppImage) implements TaskContext {

        @Override
        public boolean test(TaskID taskID) {
            if (!delegate.test(taskID)) {
                return false;
            } else if (taskID == MacBuildApplicationTaskID.PACKAGE_FILE) {
                // Don't create files relevant for package bundling when bundling app image
                return !forApp;
            } else {
                return true;
            }
        }

        @Override
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            delegate.execute(taskAction);
        }
    }

    static final MacApplicationLayout APPLICATION_LAYOUT = MacApplicationLayout.create(
            ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT, Path.of("Contents/runtime"));
}
