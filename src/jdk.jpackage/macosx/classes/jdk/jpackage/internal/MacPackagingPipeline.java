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

import static jdk.jpackage.internal.util.PListUtils.writeArray;
import static jdk.jpackage.internal.util.PListUtils.writeBoolean;
import static jdk.jpackage.internal.util.PListUtils.writeDict;
import static jdk.jpackage.internal.util.PListUtils.writeKey;
import static jdk.jpackage.internal.util.PListUtils.writeString;
import static jdk.jpackage.internal.util.PListUtils.writeStringArray;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.internal.util.function.ThrowingBiConsumer.toBiConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.PackagingPipeline.AppImageTaskID;
import jdk.jpackage.internal.PackagingPipeline.ApplicationImageTaskAction;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskAction;
import jdk.jpackage.internal.PackagingPipeline.TaskContext;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.SigningConfig;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

final class MacPackagingPipeline {

    enum MacAppImageTaskID implements TaskID {
        RUNTIME_INFO_PLIST,
        COPY_JLILIB,
        APP_ICON,
        PKG_INFO_FILE,
        PKG_FILE,
        FA_ICONS,
        APP_INFO_PLIST,
        SIGN
    }

    static PackagingPipeline.Builder build() {
        final var builder = PackagingPipeline.buildStandard()
                .appContextMapper(appContext -> {
                    return new TaskContextProxy(appContext, true);
                })
                .pkgContextMapper(appContext -> {
                    return new TaskContextProxy(appContext, false);
                })
                .task(PrimaryTaskID.COPY_APP_IMAGE)
                        .copyAction(MacPackagingPipeline::copyAppImage).add()
                .task(MacAppImageTaskID.RUNTIME_INFO_PLIST)
                        .action(conv(MacPackagingPipeline::writeRuntimeInfoPlist))
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.COPY_JLILIB)
                        .action(conv(MacPackagingPipeline::copyJliLib))
                        .addDependency(AppImageTaskID.RUNTIME)
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.APP_ICON)
                        .action(conv(new ApplicationIcon()))
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.PKG_INFO_FILE)
                        .action(MacPackagingPipeline::writePkgInfoFile)
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.PKG_FILE)
                        .action(MacPackagingPipeline::writePackageFile)
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.FA_ICONS)
                        .action(MacPackagingPipeline::writeFileAssociationIcons)
                        .addDependent(AppImageTaskID.CONTENT).add()
                .task(MacAppImageTaskID.APP_INFO_PLIST)
                        .action(conv(MacPackagingPipeline::writeAppInfoPlist))
                        .addDependent(AppImageTaskID.CONTENT).add();

        builder.task(MacAppImageTaskID.SIGN)
                .action(conv(MacPackagingPipeline::sign))
                .addDependencies(builder.taskGraphSnapshot().getAllTailsOf(PrimaryTaskID.BUILD_APPLICATION_IMAGE))
                .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE).add();

        return builder;
    }

    @FunctionalInterface
    private interface MacApplicationImageTaskAction extends TaskAction {
        void execute(BuildEnv env, MacApplication app, MacApplicationLayout appLayout)
                throws IOException, PackagerException;
    }

    private static ApplicationImageTaskAction conv(MacApplicationImageTaskAction v) {
        return (env, app, appLayout) -> {
            v.execute(env, (MacApplication)app, (MacApplicationLayout)appLayout);
        };
    }

    private static void copyAppImage(Package pkg, Path srcAppImageRoot,
            Path dstAppImageRoot) throws IOException {
        FileUtils.copyRecursive(srcAppImageRoot, dstAppImageRoot);
    }

    private static void copyJliLib(BuildEnv env, MacApplication app,
            MacApplicationLayout appLayout) throws IOException {

        final var runtimeMacOSDir = appLayout.runtimeRootDirectory().resolve("Contents/MacOS");

        final var jliName = Path.of("libjli.dylib");

        try (var walk = Files.walk(appLayout.runtimeDirectory().resolve("lib"))) {
            final var jli = walk
                    .filter(file -> file.getFileName().equals(jliName))
                    .findFirst()
                    .orElseThrow();
            Files.createDirectories(runtimeMacOSDir);
            Files.copy(jli, runtimeMacOSDir.resolve(jliName));
        }
    }

    private static void writePackageFile(BuildEnv env, Package pkg) throws IOException {
        new PackageFile(pkg.packageName()).save(pkg.asApplicationLayout().orElseThrow().resolveAt(env.appImageDir()));
    }

    private static void writePkgInfoFile(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {
        final var dir = appLayout.contentDirectory();
        Files.createDirectories(dir);
        Files.write(dir.resolve("PkgInfo"),
                "APPL????".getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void writeRuntimeInfoPlist(BuildEnv env, MacApplication app,
            ApplicationLayout appLayout) throws IOException {

        Map<String, String> data = new HashMap<>();
        data.put("CF_BUNDLE_IDENTIFIER", app.bundleIdentifier());
        data.put("CF_BUNDLE_NAME", app.bundleName());
        data.put("CF_BUNDLE_VERSION", app.version());
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", app.shortVersion().toString());

        env.createResource("Runtime-Info.plist.template")
                .setPublicName("Runtime-Info.plist")
                .setCategory(I18N.getString("resource.runtime-info-plist"))
                .setSubstitutionData(data)
                .saveToFile(((MacApplicationLayout)appLayout).runtimeRootDirectory().resolve("Contents/Info.plist"));
    }

    private static void writeAppInfoPlist(BuildEnv env, MacApplication app,
            MacApplicationLayout appLayout) throws IOException {

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
        data.put("DEPLOY_ICON_FILE", ApplicationIcon.getPath(app, appLayout).getFileName().toString());
        data.put("DEPLOY_BUNDLE_COPYRIGHT", app.copyright());
        data.put("DEPLOY_LAUNCHER_NAME", app.mainLauncher().orElseThrow().executableNameWithSuffix());
        data.put("DEPLOY_BUNDLE_SHORT_VERSION", app.shortVersion().toString());
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION", app.version());
        data.put("DEPLOY_BUNDLE_NAME", app.bundleName());
        data.put("DEPLOY_BUNDLE_IDENTIFIER", app.bundleIdentifier());
        data.put("DEPLOY_APP_CATEGORY", app.category());
        data.put("DEPLOY_FILE_ASSOCIATIONS", faXml);

        env.createResource("Info-lite.plist.template")
                .setCategory(I18N.getString("resource.app-info-plist"))
                .setSubstitutionData(data)
                .setPublicName("Info.plist")
                .saveToFile(appLayout.contentDirectory().resolve("Info.plist"));
    }

    private static void sign(BuildEnv env, MacApplication app,
            MacApplicationLayout appLayout) throws IOException {

        final var codesignConfigBuilder = CodesignConfig.build();
        app.signingConfig().ifPresent(codesignConfigBuilder::from);

        if (app.sign() && app.signingConfig().flatMap(SigningConfig::entitlements).isEmpty()) {
            final var entitlementsDefaultResource = app.signingConfig().map(
                    SigningConfig::entitlementsResourceName).orElseThrow();

            final var entitlementsFile = env.configDir().resolve(app.name() + ".entitlements");

            env.createResource(entitlementsDefaultResource)
                    .setCategory(I18N.getString("resource.entitlements"))
                    .saveToFile(entitlementsFile);

            codesignConfigBuilder.entitlements(entitlementsFile);
        }

        final Runnable signAction = () -> {
            AppImageSigner.createSigner(app, codesignConfigBuilder.create()).accept(env.appImageDir());
        };

        app.signingConfig().flatMap(SigningConfig::keyChain).ifPresentOrElse(keyChain -> {
            toBiConsumer(TempKeychain::withKeychain).accept(keyChain, unused -> signAction.run());
        }, signAction);
    }

    private static void writeCFBundleDocumentTypes(XMLStreamWriter xml,
            MacApplication app) throws XMLStreamException, IOException {
        writeKey(xml, "CFBundleDocumentTypes");
        for (var fa : app.fileAssociations().toList()) {
            writeArray(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    addFaToCFBundleDocumentTypes(xml, app, (MacFileAssociation) fa);
                }));
            }));
        }
    }

    private static void writeUTExportedTypeDeclarations(XMLStreamWriter xml,
            MacApplication app) throws XMLStreamException, IOException {
        writeKey(xml, "UTExportedTypeDeclarations");
        for (var fa : app.fileAssociations().toList()) {
            writeArray(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    addFaToUTExportedTypeDeclarations(xml, app, (MacFileAssociation) fa);
                }));
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
        writeString(xml, "CFBundleTypeName", fa.description());
        writeString(xml, "LSHandlerRank", fa.lsHandlerRank());
        writeString(xml, "CFBundleTypeRole", fa.cfBundleTypeRole());
        writeString(xml, "NSPersistentStoreTypeKey",
                fa.nsPersistentStoreTypeKey());
        writeString(xml, "NSDocumentClass", fa.nsDocumentClass());
        writeBoolean(xml, "LSIsAppleDefaultForType", true);
        writeBoolean(xml, "LSTypeIsPackage", fa.lsTypeIsPackage());
        writeBoolean(xml, "LSSupportsOpeningDocumentsInPlace",
                fa.lsSupportsOpeningDocumentsInPlace());
        writeBoolean(xml, "UISupportsDocumentBrowser",
                fa.uiSupportsDocumentBrowser());
        faWriteIcon(xml, "CFBundleTypeIconFile", fa);
    }

    private static void addFaToUTExportedTypeDeclarations(XMLStreamWriter xml,
            MacApplication app, MacFileAssociation fa) throws XMLStreamException,
            IOException {
        writeString(xml, "UTTypeIdentifier", List.of(faContentType(app, fa)));
        writeString(xml, "UTTypeDescription", fa.description());
        writeStringArray(xml, "UTTypeConformsTo", fa.utTypeConformsTo());
        faWriteIcon(xml, "UTTypeIconFile", fa);

        writeKey(xml, "UTTypeTagSpecification");
        writeDict(xml, toXmlConsumer(() -> {
            writeStringArray(xml, "public.filename-extension", List.of(fa.extension()));
            writeStringArray(xml, "public.mime-type", List.of(fa.mimeType()));
            writeStringArray(xml, "NSExportableTypes", fa.nsExportableTypes());
        }));
    }

    private static class ApplicationIcon implements MacApplicationImageTaskAction {
        static Path getPath(Application app, ApplicationLayout appLayout) {
            return appLayout.destktopIntegrationDirectory().resolve(app.name() + ".icns");
        }

        @Override
        public void execute(BuildEnv env, MacApplication app, MacApplicationLayout appLayout)
                throws IOException {
            final var resource = env.createResource("JavaApp.icns").setCategory("icon");

            app.icon().ifPresent(resource::setExternal);

            resource.saveToFile(getPath(app, appLayout));
        }
    }

    private static void writeFileAssociationIcons(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {
        for (var faIcon : app.fileAssociations()
                .filter(FileAssociation::hasIcon)
                .map(FileAssociation::icon)
                .map(Optional::get).toList()) {
            Files.copy(faIcon, appLayout.destktopIntegrationDirectory().resolve(faIcon.getFileName()));
        }
    }

    private record TaskContextProxy(TaskContext delegate, boolean forApp) implements TaskContext {

        @Override
        public boolean test(TaskID taskID) {
            if (forApp && taskID == MacAppImageTaskID.PKG_FILE) {
                // Don't create files relevant for package bundling when bundling app image
                return false;
            } else if (!forApp && taskID == AppImageTaskID.APP_IMAGE_FILE) {
                // Always create ".jpackage.xml" for compatibility with tests
                // TODO: Don't create ".jpackage.xml" when bundling a package like on other platforms
                return true;
            } else {
                return delegate.test(taskID);
            }
        }

        @Override
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            delegate.execute(taskAction);
        }
    }

    final static MacApplicationLayout APPLICATION_LAYOUT = MacApplicationLayout.create(
            ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT, Path.of("Contents/runtime"));
}
