/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.LinuxLauncher;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.Launcher;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import static jdk.jpackage.internal.ApplicationImageUtils.createLauncherIconResource;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.util.CompositeProxy;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.XmlUtils;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

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

    private DesktopIntegration(BuildEnv env, LinuxPackage pkg, LinuxLauncher launcher) throws IOException {

        associations = launcher.fileAssociations().stream().map(
                LinuxFileAssociation::create).toList();

        this.env = env;
        this.pkg = pkg;
        this.launcher = launcher;

        // Need desktop and icon files if one of conditions is met:
        //  - there are file associations configured
        //  - user explicitly requested to create a shortcut
        boolean withDesktopFile = !associations.isEmpty() || launcher.shortcut().orElse(false);

        var curIconResource = createLauncherIconResource(pkg.app(), launcher,
                env::createResource);

        if (curIconResource.isEmpty()) {
            // This is additional launcher with explicit `no icon` configuration.
            withDesktopFile = false;
        } else {
            final Path nullPath = null;
            if (curIconResource.get().saveToFile(nullPath) != OverridableResource.Source.DefaultResource) {
                // This launcher has custom icon configured.
                withDesktopFile = true;
            }
        }

        desktopFileResource = env.createResource("template.desktop")
                .setCategory(I18N.getString("resource.menu-shortcut-descriptor"))
                .setPublicName(launcher.name() + ".desktop");

        final String escapedAppFileName = launcher.name().replaceAll("\\s+", "_");

        // XDG recommends to use vendor prefix in desktop file names as xdg
        // commands copy files to system directories.
        // Package name should be a good prefix.
        final String desktopFileName = String.format("%s-%s.desktop",
                    pkg.packageName(), escapedAppFileName);
        final String mimeInfoFileName = String.format("%s-%s-MimeInfo.xml",
                    pkg.packageName(), escapedAppFileName);

        mimeInfoFile = createDesktopFile(mimeInfoFileName);

        if (withDesktopFile) {
            desktopFile = Optional.of(createDesktopFile(desktopFileName));
            iconFile = Optional.of(createDesktopFile(escapedAppFileName + ".png"));

            if (curIconResource.isEmpty()) {
                // Create default icon.
                curIconResource = createLauncherIconResource(pkg.app(), pkg.app().mainLauncher().orElseThrow(), env::createResource);
            }
        } else {
            desktopFile = Optional.empty();
            iconFile = Optional.empty();
        }

        iconResource = curIconResource;

        desktopFileData = createDataForDesktopFile();

        if (launcher != pkg.app().mainLauncher().orElseThrow()) {
            nestedIntegrations = List.of();
        } else {
            nestedIntegrations = pkg.app().additionalLaunchers().stream().map(v -> {
                return (LinuxLauncher)v;
            }).filter(l -> {
                return l.shortcut().orElse(true);
            }).map(toFunction(l -> {
                return new DesktopIntegration(env, pkg, l);
            })).toList();
        }
    }

    static ShellCustomAction create(BuildEnv env, Package pkg) throws IOException {
        if (pkg.isRuntimeInstaller()) {
            return ShellCustomAction.nop(REPLACEMENT_STRING_IDS);
        }
        return new DesktopIntegration(env, (LinuxPackage) pkg,
                (LinuxLauncher) pkg.app().mainLauncher().orElseThrow());
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
        if (iconFile.isPresent()) {
            // Create application icon file.
            iconResource.orElseThrow().saveToFile(iconFile.orElseThrow().srcPath());
        }

        Map<String, String> data = new HashMap<>(desktopFileData);

        final ShellCommands shellCommands;
        if (desktopFile.isPresent()) {
            // Create application desktop description file.
            saveDesktopFile(data);

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
        return desktopFile.map(file -> List.of("xdg-utils")).orElseGet(Collections::emptyList);
    }

    private Map<String, String> createDataForDesktopFile() {
        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_NAME", launcher.name());
        data.put("APPLICATION_DESCRIPTION", launcher.description());
        data.put("APPLICATION_ICON", iconFile.map(
                f -> f.installPath().toString()).orElse(null));
        data.put("DEPLOY_BUNDLE_CATEGORY", pkg.menuGroupName());
        data.put("APPLICATION_LAUNCHER", Enquoter.forPropertyValues().applyTo(
                pkg.asInstalledPackageApplicationLayout().orElseThrow().launchersDirectory().resolve(
                        launcher.executableNameWithSuffix()).toString()));

        return data;
    }

    /**
     * Shell commands to integrate something with desktop.
     */
    private class ShellCommands {

        ShellCommands() {
            registerIconCmds = new ArrayList<>();
            unregisterIconCmds = new ArrayList<>();

            final var desktopFileInstallPath = desktopFile.orElseThrow().installPath().toString();

            registerDesktopFileCmd = String.join(" ",
                    "xdg-desktop-menu", "install", desktopFileInstallPath);
            unregisterDesktopFileCmd = String.join(" ",
                    "do_if_file_belongs_to_single_package", desktopFileInstallPath,
                    "xdg-desktop-menu", "uninstall", desktopFileInstallPath);
        }

        void setFileAssociations() {
            registerFileAssociationsCmd = String.join(" ", "xdg-mime",
                    "install",
                    mimeInfoFile.installPath().toString());
            unregisterFileAssociationsCmd = String.join(" ",
                    "do_if_file_belongs_to_single_package", mimeInfoFile.
                            installPath().toString(), "xdg-mime", "uninstall",
                    mimeInfoFile.installPath().toString());

            final var desktopFileInstallPath = desktopFile.orElseThrow().installPath();

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
                    "do_if_file_belongs_to_single_package", desktopFileInstallPath.toString(),
                    "desktop_uninstall_default_mime_handler", desktopFileInstallPath.getFileName().toString(),
                    String.join(" ", getMimeTypeNamesFromFileAssociations()));

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
     * Creates desktop integration file. xml, icon, etc.
     *
     * Returned instance:
     *  - srcPath(): path where it should be placed at package build time;
     *  - installPath(): path where it should be installed by package manager;
     */
    private InstallableFile createDesktopFile(String fileName) {
        var srcPath = pkg.asPackageApplicationLayout().orElseThrow().resolveAt(env.appImageDir()).desktopIntegrationDirectory().resolve(fileName);
        var installPath = pkg.asInstalledPackageApplicationLayout().orElseThrow().desktopIntegrationDirectory().resolve(fileName);
        return new InstallableFile(srcPath, installPath);
    }

    private void appendFileAssociation(XMLStreamWriter xml,
            LinuxFileAssociation fa) throws XMLStreamException {

            xml.writeStartElement("mime-type");
            xml.writeAttribute("type", fa.mimeType());

            final var description = fa.description().orElse(null);
            if (description != null) {
                xml.writeStartElement("comment");
                xml.writeCharacters(description);
                xml.writeEndElement();
            }

            xml.writeStartElement("glob");
            xml.writeAttribute("pattern", "*." + fa.extension());
            xml.writeEndElement();

            xml.writeEndElement();
    }

    private void createFileAssociationsMimeInfoFile() throws IOException {
        XmlUtils.createXml(mimeInfoFile.srcPath(), xml -> {
            xml.writeStartElement("mime-info");
            xml.writeDefaultNamespace(
                    "http://www.freedesktop.org/standards/shared-mime-info");

            for (var fa : associations) {
                appendFileAssociation(xml, fa);
            }

            xml.writeEndElement();
        });
    }

    private void addFileAssociationIconFiles(ShellCommands shellCommands)
            throws IOException {
        Set<String> processedMimeTypes = new HashSet<>();
        for (var fa : associations) {
            if (!fa.hasIcon()) {
                // No icon.
                continue;
            }

            var mimeType = fa.mimeType();
            if (processedMimeTypes.contains(mimeType)) {
                continue;
            }

            processedMimeTypes.add(mimeType);

            final var faIcon = fa.icon().orElseThrow();

            // Create icon name for mime type from mime type.
            var faIconFile = createDesktopFile(mimeType.replace(File.separatorChar,
                    '-') + PathUtils.getSuffix(faIcon));

            IOUtils.copyFile(faIcon, faIconFile.srcPath());

            shellCommands.addIcon(mimeType, faIconFile.installPath(), fa.iconSize());
        }
    }

    private void saveDesktopFile(Map<String, String> data) throws IOException {
        List<String> mimeTypes = getMimeTypeNamesFromFileAssociations();
        data.put("DESKTOP_MIMES", "MimeType=" + String.join(";", mimeTypes));

        // prepare desktop shortcut
        desktopFileResource
                .setSubstitutionData(data)
                .saveToFile(desktopFile.orElseThrow().srcPath());
    }

    private List<String> getMimeTypeNamesFromFileAssociations() {
        return associations.stream().map(FileAssociation::mimeType).toList();
    }

    private static int getSquareSizeOfImage(Path path) {
        try {
            BufferedImage bi = ImageIO.read(path.toFile());
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

    private interface LinuxFileAssociationMixin {
        int iconSize();

        record Stub(int iconSize) implements LinuxFileAssociationMixin {}
    }

    private static interface LinuxFileAssociation extends FileAssociation, LinuxFileAssociationMixin {
        static LinuxFileAssociation create(FileAssociation fa) {
            var iconSize = getIconSize(fa);
            if (iconSize <= 0) {
                // nullify the icon
                fa = new FileAssociation.Stub(fa.description(), Optional.empty(),
                        fa.mimeType(), fa.extension());
            }
            return CompositeProxy.build()
                    .invokeTunnel(CompositeProxyTunnel.INSTANCE)
                    .create(LinuxFileAssociation.class, fa,
                            new LinuxFileAssociationMixin.Stub(iconSize));
        }

        private static int getIconSize(FileAssociation fa) {
            return Optional.of(fa)
                    .flatMap(FileAssociation::icon)
                    .map(DesktopIntegration::getSquareSizeOfImage)
                    .orElse(-1);
        }
    }

    private final BuildEnv env;
    private final LinuxPackage pkg;
    private final Launcher launcher;

    private final List<LinuxFileAssociation> associations;

    private final Optional<OverridableResource> iconResource;
    private final OverridableResource desktopFileResource;

    private final InstallableFile mimeInfoFile;
    private final Optional<InstallableFile> desktopFile;
    private final Optional<InstallableFile> iconFile;

    private final List<DesktopIntegration> nestedIntegrations;

    private final Map<String, String> desktopFileData;
}
