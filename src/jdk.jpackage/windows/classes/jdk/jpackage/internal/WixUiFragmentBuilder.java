/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.WixAppImageFragmentBuilder.ShortcutsFolder;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.model.WinMsiPackage;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.XmlConsumer;
import jdk.jpackage.internal.wixui.Dialog;
import jdk.jpackage.internal.wixui.DialogPair;
import jdk.jpackage.internal.wixui.Publish;
import jdk.jpackage.internal.wixui.ShowActionSuppresser;
import jdk.jpackage.internal.wixui.UIConfig;
import jdk.jpackage.internal.wixui.UISpec;

/**
 * Creates UI WiX fragment.
 */
final class WixUiFragmentBuilder extends WixFragmentBuilder {

    @Override
    void initFromParams(BuildEnv env, WinMsiPackage pkg) {
        super.initFromParams(env, pkg);

        final var shortcutFolders = ShortcutsFolder.getForPackage(pkg);

        uiConfig = UIConfig.build()
                .withLicenseDlg(pkg.licenseFile().isPresent())
                .withInstallDirChooserDlg(pkg.withInstallDirChooser())
                .withShortcutPromptDlg(!shortcutFolders.isEmpty() && pkg.withShortcutPrompt())
                .create();

        if (!uiConfig.equals(UIConfig.build().create()) || pkg.withUI()) {
            uiSpec = Optional.of(UISpec.create(uiConfig));
        } else {
            uiSpec = Optional.empty();
        }

        if (uiConfig.isWithLicenseDlg()) {
            Path licenseFileName = pkg.licenseFile().orElseThrow().getFileName();
            Path destFile = getConfigRoot().resolve(licenseFileName);
            setWixVariable("JpLicenseRtf", destFile.toAbsolutePath().toString());
        }

        customDialogs = new ArrayList<>();

        if (uiConfig.isWithShortcutPromptDlg()) {
            CustomDialog dialog = new CustomDialog(
                    env::createResource,
                    I18N.getString("resource.shortcutpromptdlg-wix-file"),
                    "ShortcutPromptDlg.wxs");
            for (var shortcutFolder : shortcutFolders) {
                dialog.wixVariables.define(
                        shortcutFolder.getWixVariableName());
            }
            customDialogs.add(dialog);
        }

        if (uiConfig.isWithInstallDirChooserDlg()) {
            CustomDialog dialog = new CustomDialog(
                    env::createResource,
                    I18N.getString("resource.installdirnotemptydlg-wix-file"),
                    "InstallDirNotEmptyDlg.wxs");
            customDialogs.add(dialog);
        }

    }

    @Override
    void configureWixPipeline(WixPipeline.Builder wixPipeline) {
        super.configureWixPipeline(wixPipeline);

        // Only needed if we using CA dll, so Wix can find it
        if (withCustomActionsDll) {
            wixPipeline.addLightOptions("-b",
                    getConfigRoot().toAbsolutePath().toString());
        }

        if (uiSpec.isEmpty()) {
            return;
        }

        var extName = switch (getWixType()) {
            case Wix3 -> "WixUIExtension";
            case Wix4 -> "WixToolset.UI.wixext";
        };
        wixPipeline.addLightOptions("-ext", extName);
        wixPipeline.putWixVariables(uiSpec.get().wixVariables());

        if (!uiSpec.get().hideDialogs().isEmpty() && getWixType() == WixToolsetType.Wix3) {
            // Older WiX doesn't support multiple overrides of a "ShowAction" element.
            // Have to run a script to alter the msi.
            var removeActions = uiSpec.get().hideDialogs().stream()
                    .map(ShowActionSuppresser::dialog)
                    .sorted(Dialog.DEFAULT_COMPARATOR)
                    .map(Dialog::id);
            wixPipeline.addMsiMutator(
                    new MsiMutator("msi-disable-actions.js"),
                    Stream.concat(Stream.of("InstallUISequence"), removeActions).toList());
        }

        for (var customDialog : customDialogs) {
            customDialog.addToWixPipeline(wixPipeline);
        }
    }

    @Override
    void addFilesToConfigRoot() throws IOException {
        super.addFilesToConfigRoot();

        if (withCustomActionsDll) {
            String fname = "msica.dll"; // CA dll
            try (InputStream is = ResourceLocator.class.getResourceAsStream(fname)) {
                Files.copy(is, getConfigRoot().resolve(fname));
            }
        }
    }

    @Override
    protected Collection<XmlConsumer> getFragmentWriters() {
        return List.of(this::addUI);
    }

    private void addUI(XMLStreamWriter xml) throws XMLStreamException, IOException {

        if (uiConfig.isWithInstallDirChooserDlg()) {
            xml.writeStartElement("Property");
            xml.writeAttribute("Id", "WIXUI_INSTALLDIR");
            xml.writeAttribute("Value", "INSTALLDIR");
            xml.writeEndElement(); // Property
        }

        if (uiConfig.isWithLicenseDlg()) {
            xml.writeStartElement("WixVariable");
            xml.writeAttribute("Id", "WixUILicenseRtf");
            xml.writeAttribute("Value", "$(var.JpLicenseRtf)");
            xml.writeEndElement(); // WixVariable
        }

        if (uiSpec.isPresent()) {
            writeNonEmptyUIElement(xml);
        } else {
            xml.writeStartElement("UI");
            xml.writeAttribute("Id", "JpUI");
            xml.writeEndElement();
        }
    }

    void writeNonEmptyUIElement(XMLStreamWriter xml) throws XMLStreamException, IOException {

        switch (getWixType()) {
            case Wix3 -> {}
            case Wix4 -> {
                // https://wixtoolset.org/docs/fourthree/faqs/#converting-custom-wixui-dialog-sets
                xml.writeProcessingInstruction("foreach WIXUIARCH in X86;X64;A64");
                writeWix4UIRef(xml, uiSpec.get().wixUI().id(), "JpUIInternal_$(WIXUIARCH)");
                xml.writeProcessingInstruction("endforeach");

                writeWix4UIRef(xml, "JpUIInternal", "JpUI");
            }
        }

        xml.writeStartElement("UI");
        switch (getWixType()) {
            case Wix3 -> {
                xml.writeAttribute("Id", "JpUI");
                xml.writeStartElement("UIRef");
                xml.writeAttribute("Id", uiSpec.get().wixUI().id());
                xml.writeEndElement(); // UIRef
            }
            case Wix4 -> {
                xml.writeAttribute("Id", "JpUIInternal");
            }
        }
        writeUIElementContents(xml);
        xml.writeEndElement(); // UI
    }

    private void writeUIElementContents(XMLStreamWriter xml) throws XMLStreamException, IOException {

        if (uiConfig.isWithInstallDirChooserDlg()) {
            xml.writeStartElement("DialogRef");
            xml.writeAttribute("Id", "InstallDirNotEmptyDlg");
            xml.writeEndElement(); // DialogRef
        }

        for (var e : uiSpec.get().customDialogSequence().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey, DialogPair.DEFAULT_COMPARATOR))
                .toList()) {
            writePublishDialogPair(getWixType(), xml, e.getValue(), e.getKey());
        }

        hideDialogs(getWixType(), xml, uiSpec.get().hideDialogs());
    }

    private static void writeWix4UIRef(XMLStreamWriter xml, String uiRef, String id) throws XMLStreamException, IOException {
        // https://wixtoolset.org/docs/fourthree/faqs/#referencing-the-standard-wixui-dialog-sets
        xml.writeStartElement("UI");
        xml.writeAttribute("Id", Objects.requireNonNull(id));
        xml.writeStartElement("ui:WixUI");
        xml.writeAttribute("Id", Objects.requireNonNull(uiRef));
        xml.writeNamespace("ui", "http://wixtoolset.org/schemas/v4/wxs/ui");
        xml.writeEndElement(); // UIRef
        xml.writeEndElement(); // UI
    }

    private static void writePublishDialogPair(
            WixToolsetType wixType,
            XMLStreamWriter xml,
            Publish publish,
            DialogPair dialogPair) throws IOException, XMLStreamException {

        xml.writeStartElement("Publish");
        xml.writeAttribute("Dialog", dialogPair.first().id());
        xml.writeAttribute("Control", publish.control().id());
        xml.writeAttribute("Event", "NewDialog");
        xml.writeAttribute("Value", dialogPair.second().id());
        if (publish.order() != 0) {
            xml.writeAttribute("Order", String.valueOf(publish.order()));
        }

        switch (wixType) {
            case Wix3 -> {
                xml.writeCharacters(publish.condition());
            }
            case Wix4 -> {
                xml.writeAttribute("Condition", publish.condition());
            }
        }

        xml.writeEndElement();
    }

    private static void hideDialogs(
            WixToolsetType wixType,
            XMLStreamWriter xml,
            Collection<? extends ShowActionSuppresser> hideDialogs) throws IOException, XMLStreamException {

        if (!hideDialogs.isEmpty()) {
            if (wixType == WixToolsetType.Wix4) {
                xml.writeStartElement("InstallUISequence");
                for (var showAction : hideDialogs.stream().sorted(ShowActionSuppresser.DEFAULT_COMPARATOR).toList()) {
                    writeWix4ShowAction(wixType, xml, showAction);
                }
                xml.writeEndElement();
            }
        }
    }

    private static void writeWix4ShowAction(
            WixToolsetType wixType,
            XMLStreamWriter xml,
            ShowActionSuppresser hideDialog) throws IOException, XMLStreamException {

        xml.writeStartElement("Show");
        xml.writeAttribute("Dialog", String.format("override %s", hideDialog.dialog().id()));
        xml.writeAttribute(switch (hideDialog.order()) {
            case AFTER -> "After";
        }, hideDialog.anchor().id());
        xml.writeAttribute("Condition", "0");
        xml.writeEndElement();
    }

    private final class CustomDialog {

        CustomDialog(Function<String, OverridableResource> createResource, String category, String wxsFileName) {
            this.wxsFileName = wxsFileName;
            this.wixVariables = new WixVariables();

            addResource(createResource.apply(wxsFileName).setCategory(category).setPublicName(wxsFileName), wxsFileName);
        }

        void addToWixPipeline(WixPipeline.Builder wixPipeline) {
            wixPipeline.addSource(getConfigRoot().toAbsolutePath().resolve(wxsFileName), wixVariables);
        }

        private final WixVariables wixVariables;
        private final String wxsFileName;
    }

    private UIConfig uiConfig;
    private Optional<UISpec> uiSpec;
    private boolean withCustomActionsDll = true;
    private List<CustomDialog> customDialogs;
}
