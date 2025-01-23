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
import jdk.jpackage.internal.AppImageBuilder.AppImageItem;
import jdk.jpackage.internal.AppImageBuilder.AppImageItemGroup;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.SigningConfig;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

final class MacAppImageBuilder2 {

    static AppImageBuilder.Builder build() {
        return AppImageBuilder.build()
                .itemGroup(AppImageItemGroup.RUNTIME)
                .addItem(MacAppImageBuilder2::writeRuntimeInfoPlist)
                .addItem(MacAppImageBuilder2::copyJliLib)
                .itemGroup(AppImageItemGroup.BEGIN)
                .addItem(new ApplicationIcon())
                .addItem(MacAppImageBuilder2::writePkgInfoFile)
                .addItem(MacAppImageBuilder2::writeFileAssociationIcons)
                .addItem(MacAppImageBuilder2::writeAppInfoPlist)
                .itemGroup(AppImageItemGroup.END)
                .addItem(MacAppImageBuilder2::sign);
    }

    private static void copyJliLib(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {

        final var runtimeMacOSDir = ((MacApplicationLayout)appLayout).runtimeRootDirectory().resolve("Contents/MacOS");

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

    private static void writeRuntimeInfoPlist(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {

        final var macApp = ((MacApplication)app);

        Map<String, String> data = new HashMap<>();
        data.put("CF_BUNDLE_IDENTIFIER", macApp.bundleIdentifier());
        data.put("CF_BUNDLE_NAME", macApp.bundleName());
        data.put("CF_BUNDLE_VERSION", macApp.version());
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", macApp.shortVersion().toString());

        env.createResource("Runtime-Info.plist.template")
                .setPublicName("Runtime-Info.plist")
                .setCategory(I18N.getString("resource.runtime-info-plist"))
                .setSubstitutionData(data)
                .saveToFile(((MacApplicationLayout)appLayout).runtimeRootDirectory().resolve("Contents/Info.plist"));
    }

    private static void writeAppInfoPlist(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {

        final var macApp = ((MacApplication)app);

        final String faXml = toSupplier(() -> {
            var buf = new StringWriter();
            var xml = XMLOutputFactory.newInstance().createXMLStreamWriter(buf);
            writeCFBundleDocumentTypes(xml, macApp);
            writeUTExportedTypeDeclarations(xml, macApp);
            xml.flush();
            xml.close();
            return buf.toString();
        }).get();

        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ICON_FILE", ApplicationIcon.getPath(app, appLayout).getFileName().toString());
        data.put("DEPLOY_BUNDLE_COPYRIGHT", app.copyright());
        data.put("DEPLOY_LAUNCHER_NAME", app.mainLauncher().orElseThrow().executableNameWithSuffix());
        data.put("DEPLOY_BUNDLE_SHORT_VERSION", macApp.shortVersion().toString());
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION", app.version());
        data.put("DEPLOY_BUNDLE_NAME", macApp.bundleName());
        data.put("DEPLOY_BUNDLE_IDENTIFIER", macApp.bundleIdentifier());
        data.put("DEPLOY_APP_CATEGORY", macApp.category());
        data.put("DEPLOY_FILE_ASSOCIATIONS", faXml);

        env.createResource("Info-lite.plist.template")
                .setCategory(I18N.getString("resource.app-info-plist"))
                .setSubstitutionData(data)
                .setPublicName("Info.plist")
                .saveToFile(appLayout.contentDirectory().resolve("Info.plist"));
    }

    private static void sign(BuildEnv env, Application app, ApplicationLayout appLayout) throws IOException {

        final var macApp = ((MacApplication)app);

        final var codesignConfigBuilder = CodesignConfig.build();
        macApp.signingConfig().ifPresent(codesignConfigBuilder::from);

        if (macApp.sign() && macApp.signingConfig().flatMap(SigningConfig::entitlements).isEmpty()) {
            final var entitlementsDefaultResource = macApp.signingConfig().map(
                    SigningConfig::entitlementsResourceName).orElseThrow();

            final var entitlementsFile = env.configDir().resolve(app.name() + ".entitlements");

            env.createResource(entitlementsDefaultResource)
                    .setCategory(I18N.getString("resource.entitlements"))
                    .saveToFile(entitlementsFile);

            codesignConfigBuilder.entitlements(entitlementsFile);
        }

        final Runnable signAction = () -> {
            AppImageSigner.createSigner(macApp, codesignConfigBuilder.create()).accept(env.appImageDir());
        };

        macApp.signingConfig().flatMap(SigningConfig::keyChain).ifPresentOrElse(keyChain -> {
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
        writeString(xml, "LSTypeIsPackage", fa.lsTypeIsPackage());
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

    private static class ApplicationIcon implements AppImageItem {
        static Path getPath(Application app, ApplicationLayout appLayout) {
            return appLayout.destktopIntegrationDirectory().resolve(app.name() + ".icns");
        }

        @Override
        public void write(BuildEnv env, Application app, ApplicationLayout appLayout)
                throws IOException {
            final var resource = env.createResource("JavaApp.icns").setCategory("icon");

            ((MacApplication)app).icon().ifPresent(resource::setExternal);

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

    private static void writePkgInfoFile(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {
        Files.write(appLayout.contentDirectory().resolve("PkgInfo"),
                "APPL????".getBytes(StandardCharsets.ISO_8859_1));
    }

    final static MacApplicationLayout APPLICATION_LAYOUT = MacApplicationLayout.create(
            ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT, Path.of("Contents/runtime"));
}
