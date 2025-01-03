/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.PListUtils.writeArray;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacFileAssociation;
import static jdk.jpackage.internal.PListUtils.writeBoolean;
import static jdk.jpackage.internal.PListUtils.writeDict;
import static jdk.jpackage.internal.PListUtils.writeKey;
import static jdk.jpackage.internal.PListUtils.writeString;
import static jdk.jpackage.internal.PListUtils.writeStringArray;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.FileAssociation;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

final class MacAppImageBuilder2 {

    static AppImageBuilder.Builder build() {
        return new AppImageBuilder.Builder()
                .itemGroup(AppImageItemGroup.RUNTIME)
                .addItem(MacAppImageBuilder2::writeRuntimeInfoPlist)
                .addItem(MacAppImageBuilder2::copyJliLib)
                .itemGroup(AppImageItemGroup.BEGIN)
                .addItem(new ApplicationIcon())
                .addItem(MacAppImageBuilder2::writePkgInfoFile)
                .addItem(MacAppImageBuilder2::writeFileAssociationIcons)
                .addItem(MacAppImageBuilder2::writeAppInfoPlist);
    }

    private static void copyJliLib(BuildEnv env, Application app,
            ApplicationLayout appLayout) throws IOException {

        final Path jliName = Path.of("libjli.dylib");
        try (var walk = Files.walk(appLayout.runtimeDirectory().resolve("Contents/Home/lib"))) {
            final Path jli = walk
                    .filter(file -> file.getFileName().equals(jliName))
                    .findFirst()
                    .get();
            Files.copy(jli, appLayout.launchersDirectory().resolve(jliName));
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
                .saveToFile(appLayout.runtimeDirectory().resolve("Contents/Info.plist"));
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
            env.createResource("JavaApp.icns")
                    .setCategory("icon")
                    .setExternal(((MacApplication)app).icon())
                    .saveToFile(getPath(app, appLayout));
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
}
