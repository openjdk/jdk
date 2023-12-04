/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.Arguments.CLIOptions;
import static jdk.jpackage.internal.LinuxAppImageBuilder.DEFAULT_ICON;
import static jdk.jpackage.internal.LinuxAppImageBuilder.ICON_PNG;
import static jdk.jpackage.internal.OverridableResource.createResource;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;

/**
 * Helper to create files for desktop integration.
 */
final class DesktopIntegration extends ShellCustomAction {

    private static final String COMMANDS_INSTALL = "DESKTOP_COMMANDS_INSTALL";
    private static final String COMMANDS_UNINSTALL = "DESKTOP_COMMANDS_UNINSTALL";
    private static final String SCRIPTS = "DESKTOP_SCRIPTS";
    private static final String COMMON_SCRIPTS = "COMMON_SCRIPTS";

    private static final List<String> REPLACEMENT_STRING_IDS = List.of(
            COMMANDS_INSTALL, COMMANDS_UNINSTALL, SCRIPTS, COMMON_SCRIPTS);

    private DesktopIntegration(PlatformPackage thePackage,
            Map<String, ? super Object> params,
            Map<String, ? super Object> mainParams) throws IOException {

        associations = FileAssociation.fetchFrom(params).stream()
                .filter(fa -> !fa.mimeTypes.isEmpty())
                .map(LinuxFileAssociation::new)
                .collect(Collectors.toUnmodifiableList());

        launchers = ADD_LAUNCHERS.fetchFrom(params);

        this.thePackage = thePackage;

        // Need desktop and icon files if one of conditions is met:
        //  - there are file associations configured
        //  - user explicitly requested to create a shortcut
        boolean withDesktopFile = !associations.isEmpty() || LINUX_SHORTCUT_HINT.fetchFrom(params);

        var curIconResource = LinuxAppImageBuilder.createIconResource(DEFAULT_ICON,
                ICON_PNG, params, mainParams);
        if (curIconResource == null) {
            // This is additional launcher with explicit `no icon` configuration.
            withDesktopFile = false;
        } else {
            final Path nullPath = null;
            if (curIconResource.saveToFile(nullPath)
                    != OverridableResource.Source.DefaultResource) {
                // This launcher has custom icon configured.
                withDesktopFile = true;
            }
        }

        desktopFileResource = createResource("template.desktop", params)
                .setCategory(I18N.getString("resource.menu-shortcut-descriptor"))
                .setPublicName(APP_NAME.fetchFrom(params) + ".desktop");

        final String escapedAppFileName = APP_NAME.fetchFrom(params).replaceAll("\\s+", "_");

        // XDG recommends to use vendor prefix in desktop file names as xdg
        // commands copy files to system directories.
        // Package name should be a good prefix.
        final String desktopFileName = String.format("%s-%s.desktop",
                    thePackage.name(), escapedAppFileName);
        final String mimeInfoFileName = String.format("%s-%s-MimeInfo.xml",
                    thePackage.name(), escapedAppFileName);

        mimeInfoFile = new DesktopFile(mimeInfoFileName);

        if (withDesktopFile) {
            desktopFile = new DesktopFile(desktopFileName);
            iconFile = new DesktopFile(escapedAppFileName
                    + IOUtils.getSuffix(Path.of(DEFAULT_ICON)));

            if (curIconResource == null) {
                // Create default icon.
                curIconResource = LinuxAppImageBuilder.createIconResource(
                        DEFAULT_ICON, ICON_PNG, mainParams, null);
            }
        } else {
            desktopFile = null;
            iconFile = null;
        }

        iconResource = curIconResource;

        desktopFileData = Collections.unmodifiableMap(
                createDataForDesktopFile(params));

        nestedIntegrations = new ArrayList<>();
        // Read launchers information from predefine app image
        if (launchers.isEmpty() &&
                PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            List<AppImageFile.LauncherInfo> launcherInfos =
                    AppImageFile.getLaunchers(
                    PREDEFINED_APP_IMAGE.fetchFrom(params), params);
            if (!launcherInfos.isEmpty()) {
                launcherInfos.remove(0); // Remove main launcher
            }
            for (var launcherInfo : launcherInfos) {
                Map<String, ? super Object> launcherParams = new HashMap<>();
                Arguments.putUnlessNull(launcherParams, CLIOptions.NAME.getId(),
                        launcherInfo.getName());
                launcherParams = AddLauncherArguments.merge(params,
                        launcherParams, ICON.getID(), ICON_PNG.getID(),
                        ADD_LAUNCHERS.getID(), FILE_ASSOCIATIONS.getID(),
                        PREDEFINED_APP_IMAGE.getID());
                if (launcherInfo.isShortcut()) {
                    nestedIntegrations.add(new DesktopIntegration(thePackage,
                            launcherParams, params));
                }
            }
        } else {
            for (var launcherParams : launchers) {
                launcherParams = AddLauncherArguments.merge(params,
                        launcherParams, ICON.getID(), ICON_PNG.getID(),
                        ADD_LAUNCHERS.getID(), FILE_ASSOCIATIONS.getID());
                if (SHORTCUT_HINT.fetchFrom(launcherParams)) {
                    nestedIntegrations.add(new DesktopIntegration(thePackage,
                            launcherParams, params));
                }
            }
        }
    }

    static ShellCustomAction create(PlatformPackage thePackage,
            Map<String, ? super Object> params) throws IOException {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return ShellCustomAction.nop(REPLACEMENT_STRING_IDS);
        }
        return new DesktopIntegration(thePackage, params, null);
    }

    @Override
    List<String> requiredPackages() {
        return Stream.of(List.of(this), nestedIntegrations).flatMap(
                List::stream).map(DesktopIntegration::requiredPackagesSelf).flatMap(
                List::stream).distinct().toList();
    }

    @Override
    protected List<String> replacementStringIds() {
        return REPLACEMENT_STRING_IDS;
    }

    @Override
    protected Map<String, String> createImpl() throws IOException {
        associations.forEach(assoc -> assoc.data.verify());

        if (iconFile != null) {
            // Create application icon file.
            iconResource.saveToFile(iconFile.srcPath());
        }

        Map<String, String> data = new HashMap<>(desktopFileData);

        final ShellCommands shellCommands;
        if (desktopFile != null) {
            // Create application desktop description file.
            createDesktopFile(data);

            // Shell commands will be created only if desktop file
            // should be installed.
            shellCommands = new ShellCommands();
        } else {
            shellCommands = null;
        }

        if (!associations.isEmpty()) {
            // Create XML file with mime types corresponding to file associations.
            createFileAssociationsMimeInfoFile();

            shellCommands.setFileAssociations();

            // Create icon files corresponding to file associations
            addFileAssociationIconFiles(shellCommands);
        }

        // Create shell commands to install/uninstall integration with desktop of the app.
        if (shellCommands != null) {
            shellCommands.applyTo(data);
        }

        // Take care of additional launchers if there are any.
        // Process every additional launcher as the main application launcher.
        // Collect shell commands to install/uninstall integration with desktop
        // of the additional launchers and append them to the corresponding
        // commands of the main launcher.
        List<String> installShellCmds = new ArrayList<>(Arrays.asList(
                data.get(COMMANDS_INSTALL)));
        List<String> uninstallShellCmds = new ArrayList<>(Arrays.asList(
                data.get(COMMANDS_UNINSTALL)));
        for (var integration: nestedIntegrations) {
            Map<String, String> launcherData = integration.create();

            installShellCmds.add(launcherData.get(COMMANDS_INSTALL));
            uninstallShellCmds.add(launcherData.get(COMMANDS_UNINSTALL));
        }

        data.put(COMMANDS_INSTALL, stringifyShellCommands(installShellCmds));
        data.put(COMMANDS_UNINSTALL, stringifyShellCommands(uninstallShellCmds));

        data.put(COMMON_SCRIPTS, stringifyTextFile("common_utils.sh"));
        data.put(SCRIPTS, stringifyTextFile("desktop_utils.sh"));

        return data;
    }

    private List<String> requiredPackagesSelf() {
        if (desktopFile != null) {
            return List.of("xdg-utils");
        }
        return Collections.emptyList();
    }

    private Map<String, String> createDataForDesktopFile(
            Map<String, ? super Object> params) {
        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_ICON", Optional.ofNullable(iconFile).map(
                f -> f.installPath().toString()).orElse(null));
        data.put("DEPLOY_BUNDLE_CATEGORY", MENU_GROUP.fetchFrom(params));
        data.put("APPLICATION_LAUNCHER", Enquoter.forPropertyValues().applyTo(
                thePackage.installedApplicationLayout().launchersDirectory().resolve(
                        LinuxAppImageBuilder.getLauncherName(params)).toString()));

        return data;
    }

    /**
     * Shell commands to integrate something with desktop.
     */
    private class ShellCommands {

        ShellCommands() {
            registerIconCmds = new ArrayList<>();
            unregisterIconCmds = new ArrayList<>();

            registerDesktopFileCmd = String.join(" ", "xdg-desktop-menu",
                    "install", desktopFile.installPath().toString());
            unregisterDesktopFileCmd = String.join(" ",
                    "do_if_file_belongs_to_single_package", desktopFile.
                            installPath().toString(), "xdg-desktop-menu",
                    "uninstall", desktopFile.installPath().toString());
        }

        void setFileAssociations() {
            registerFileAssociationsCmd = String.join(" ", "xdg-mime",
                    "install",
                    mimeInfoFile.installPath().toString());
            unregisterFileAssociationsCmd = String.join(" ",
                    "do_if_file_belongs_to_single_package", mimeInfoFile.
                            installPath().toString(), "xdg-mime", "uninstall",
                    mimeInfoFile.installPath().toString());

            //
            // Add manual cleanup of system files to get rid of
            // the default mime type handlers.
            //
            // Even after mime type is unregistered with `xdg-mime uninstall`
            // command and desktop file deleted with `xdg-desktop-menu uninstall`
            // command, records in
            // `/usr/share/applications/defaults.list` (Ubuntu 16) or
            // `/usr/local/share/applications/defaults.list` (OracleLinux 7)
            // files remain referencing deleted mime time and deleted
            // desktop file which makes `xdg-mime query default` output name
            // of non-existing desktop file.
            //
            String cleanUpCommand = String.join(" ",
                    "do_if_file_belongs_to_single_package", desktopFile.
                            installPath().toString(),
                    "desktop_uninstall_default_mime_handler", desktopFile.
                            installPath().getFileName().toString(), String.join(
                            " ", getMimeTypeNamesFromFileAssociations()));

            unregisterFileAssociationsCmd = stringifyShellCommands(
                    unregisterFileAssociationsCmd, cleanUpCommand);
        }

        void addIcon(String mimeType, Path iconFile, int imgSize) {
            imgSize = normalizeIconSize(imgSize);
            final String dashMime = mimeType.replace('/', '-');
            registerIconCmds.add(String.join(" ", "xdg-icon-resource",
                    "install", "--context", "mimetypes", "--size",
                    Integer.toString(imgSize), iconFile.toString(), dashMime));
            unregisterIconCmds.add(String.join(" ",
                    "do_if_file_belongs_to_single_package", iconFile.toString(),
                    "xdg-icon-resource", "uninstall", dashMime, "--size",
                    Integer.toString(imgSize)));
        }

        void applyTo(Map<String, String> data) {
            List<String> cmds = new ArrayList<>();

            cmds.add(registerDesktopFileCmd);
            cmds.add(registerFileAssociationsCmd);
            cmds.addAll(registerIconCmds);
            data.put(COMMANDS_INSTALL, stringifyShellCommands(cmds));

            cmds.clear();
            cmds.add(unregisterDesktopFileCmd);
            cmds.add(unregisterFileAssociationsCmd);
            cmds.addAll(unregisterIconCmds);
            data.put(COMMANDS_UNINSTALL, stringifyShellCommands(cmds));
        }

        private String registerDesktopFileCmd;
        private String unregisterDesktopFileCmd;

        private String registerFileAssociationsCmd;
        private String unregisterFileAssociationsCmd;

        private List<String> registerIconCmds;
        private List<String> unregisterIconCmds;
    }

    /**
     * Desktop integration file. xml, icon, etc.
     * Resides somewhere in application installation tree.
     * Has two paths:
     *  - path where it should be placed at package build time;
     *  - path where it should be installed by package manager;
     */
    private class DesktopFile {

        DesktopFile(String fileName) {
            var installPath = thePackage
                    .installedApplicationLayout()
                    .destktopIntegrationDirectory().resolve(fileName);
            var srcPath = thePackage
                    .sourceApplicationLayout()
                    .destktopIntegrationDirectory().resolve(fileName);

            impl = new InstallableFile(srcPath, installPath);
        }

        Path installPath() {
            return impl.installPath();
        }

        Path srcPath() {
            return impl.srcPath();
        }

        private final InstallableFile impl;
    }

    private void appendFileAssociation(XMLStreamWriter xml,
            FileAssociation assoc) throws XMLStreamException {

        for (var mimeType : assoc.mimeTypes) {
            xml.writeStartElement("mime-type");
            xml.writeAttribute("type", mimeType);

            final String description = assoc.description;
            if (description != null && !description.isEmpty()) {
                xml.writeStartElement("comment");
                xml.writeCharacters(description);
                xml.writeEndElement();
            }

            for (String ext : assoc.extensions) {
                xml.writeStartElement("glob");
                xml.writeAttribute("pattern", "*." + ext);
                xml.writeEndElement();
            }

            xml.writeEndElement();
        }
    }

    private void createFileAssociationsMimeInfoFile() throws IOException {
        IOUtils.createXml(mimeInfoFile.srcPath(), xml -> {
            xml.writeStartElement("mime-info");
            xml.writeDefaultNamespace(
                    "http://www.freedesktop.org/standards/shared-mime-info");

            for (var assoc : associations) {
                appendFileAssociation(xml, assoc.data);
            }

            xml.writeEndElement();
        });
    }

    private void addFileAssociationIconFiles(ShellCommands shellCommands)
            throws IOException {
        Set<String> processedMimeTypes = new HashSet<>();
        for (var assoc : associations) {
            if (assoc.iconSize <= 0) {
                // No icon.
                continue;
            }

            for (var mimeType : assoc.data.mimeTypes) {
                if (processedMimeTypes.contains(mimeType)) {
                    continue;
                }

                processedMimeTypes.add(mimeType);

                // Create icon name for mime type from mime type.
                DesktopFile faIconFile = new DesktopFile(mimeType.replace(
                        File.separatorChar, '-') + IOUtils.getSuffix(
                                assoc.data.iconPath));

                IOUtils.copyFile(assoc.data.iconPath,
                        faIconFile.srcPath());

                shellCommands.addIcon(mimeType, faIconFile.installPath(),
                        assoc.iconSize);
            }
        }
    }

    private void createDesktopFile(Map<String, String> data) throws IOException {
        List<String> mimeTypes = getMimeTypeNamesFromFileAssociations();
        data.put("DESKTOP_MIMES", "MimeType=" + String.join(";", mimeTypes));

        // prepare desktop shortcut
        desktopFileResource
                .setSubstitutionData(data)
                .saveToFile(desktopFile.srcPath());
    }

    private List<String> getMimeTypeNamesFromFileAssociations() {
        return associations.stream()
                .map(fa -> fa.data.mimeTypes)
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableList());
    }

    private static int getSquareSizeOfImage(File f) {
        try {
            BufferedImage bi = ImageIO.read(f);
            return Math.max(bi.getWidth(), bi.getHeight());
        } catch (IOException e) {
            Log.verbose(e);
        }
        return 0;
    }

    private static int normalizeIconSize(int iconSize) {
        // If register icon with "uncommon" size, it will be ignored.
        // So find the best matching "common" size.
        List<Integer> commonIconSizes = List.of(16, 22, 32, 48, 64, 128);

        int idx = Collections.binarySearch(commonIconSizes, iconSize);
        if (idx < 0) {
            // Given icon size is greater than the largest common icon size.
            return commonIconSizes.get(commonIconSizes.size() - 1);
        }

        if (idx == 0) {
            // Given icon size is less or equal than the smallest common icon size.
            return commonIconSizes.get(idx);
        }

        int commonIconSize = commonIconSizes.get(idx);
        if (iconSize < commonIconSize) {
            // It is better to scale down original icon than to scale it up for
            // better visual quality.
            commonIconSize = commonIconSizes.get(idx - 1);
        }

        return commonIconSize;
    }

    private static class LinuxFileAssociation {
        LinuxFileAssociation(FileAssociation fa) {
            this.data = fa;
            if (fa.iconPath != null && Files.isReadable(fa.iconPath)) {
                iconSize = getSquareSizeOfImage(fa.iconPath.toFile());
            } else {
                iconSize = -1;
            }
        }

        final FileAssociation data;
        final int iconSize;
    }

    private final PlatformPackage thePackage;

    private final List<LinuxFileAssociation> associations;

    private final List<Map<String, ? super Object>> launchers;

    private final OverridableResource iconResource;
    private final OverridableResource desktopFileResource;

    private final DesktopFile mimeInfoFile;
    private final DesktopFile desktopFile;
    private final DesktopFile iconFile;

    private final List<DesktopIntegration> nestedIntegrations;

    private final Map<String, String> desktopFileData;

    private static final BundlerParamInfo<String> MENU_GROUP =
        new StandardBundlerParam<>(
                Arguments.CLIOptions.LINUX_MENU_GROUP.getId(),
                String.class,
                params -> I18N.getString("param.menu-group.default"),
                (s, p) -> s
        );

    private static final StandardBundlerParam<Boolean> LINUX_SHORTCUT_HINT =
        new StandardBundlerParam<>(
                Arguments.CLIOptions.LINUX_SHORTCUT_HINT.getId(),
                Boolean.class,
                params -> false,
                (s, p) -> (s == null || "null".equalsIgnoreCase(s))
                        ? false : Boolean.valueOf(s)
        );
}
