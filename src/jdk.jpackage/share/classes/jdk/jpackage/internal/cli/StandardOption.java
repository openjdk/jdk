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
package jdk.jpackage.internal.cli;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_EXE;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;
import static jdk.jpackage.internal.cli.PackageTypeGroup.ALL_PACKAGE_TYPES;
import static jdk.jpackage.internal.cli.PackageTypeGroup.APP_IMAGE;
import static jdk.jpackage.internal.cli.PackageTypeGroup.NATIVE_PACKAGE;

public enum StandardOption implements Option {
    TYPE(build("type").shortName("t").valueConverter(new ValueConverter<PackageType>() {
        @Override
        public PackageType convert(String value) {
            if ("app-image".equals(value)) {
                return AppImagePackageType.APP_IMAGE;
            } else {
                return fromCmdLineType(value);
            }
        }

        @Override
        public Class<? extends PackageType> valueType() {
            return PackageType.class;
        }

        private static StandardPackageType fromCmdLineType(String type) {
            Objects.requireNonNull(type);
            return Stream.of(StandardPackageType.values()).filter(pt -> {
                return pt.suffix().substring(1).equals(type);
            }).findAny().get();
        }
    })),
    INPUT(build("input").shortName("i").ofDirectory(), APP_IMAGE),
    DEST(build("dest").shortName("d").ofDirectory()),
    DESCRIPTION("description"),
    VENDOR("vendor"),
    APPCLASS("main-class"),
    NAME(build("input").shortName("i").ofString()),
    VERBOSE(build("verbose").noValue()),
    RESOURCE_DIR("resource-dir", OptionSpecBuilder::ofDirectory),
    ARGUMENTS("arguments", OptionSpecBuilder::ofStringArray),
    JLINK_OPTIONS("arguments", OptionSpecBuilder::ofStringArray),
    ICON("icon", OptionSpecBuilder::ofPath),
    COPYRIGHT("copyright"),
    LICENSE_FILE("license-file", OptionSpecBuilder::ofPath),
    VERSION("app-version"),
    ABOUT_URL("about-url", OptionSpecBuilder::ofUrl),
    JAVA_OPTIONS("java-options", OptionSpecBuilder::ofStringArray),
    APP_CONTENT("app-content", OptionSpecBuilder::ofPathArray),
    FILE_ASSOCIATIONS("file-associations", OptionSpecBuilder::ofPath),
    ADD_LAUNCHER(build("add-launcher").valueConverter(new ValueConverter<AdditionalLauncher>() {
        @Override
        public AdditionalLauncher convert(String value) {
            var components = value.split("=", 2);
            if (components.length == 1) {
                components = new String[] { null, components[0] };
            }
            return new AdditionalLauncher(components[0],
                    StandardValueConverter.PATH_CONV.convert(components[1]));
        }

        @Override
        public Class<? extends AdditionalLauncher> valueType() {
            return AdditionalLauncher.class;
        }
    })),
    TEMP_ROOT("temp", OptionSpecBuilder::ofDirectory),
    INSTALL_DIR("install-dir", OptionSpecBuilder::ofPath),
    PREDEFINED_APP_IMAGE("app-image", OptionSpecBuilder::ofDirectory, NATIVE_PACKAGE),
    PREDEFINED_RUNTIME_IMAGE("runtime-image", OptionSpecBuilder::ofDirectory),
    MAIN_JAR("main-jar", OptionSpecBuilder::ofPath),
    MODULE(build("module").shortName("m").ofString()),
    ADD_MODULES("add-modules", OptionSpecBuilder::ofStringArray),
    MODULE_PATH(build("module-path").shortName("p").ofDirectoryArray()),
    LAUNCHER_AS_SERVICE(build("launcher-as-service").noValue()),
    // Linux-specific
    LINUX_RELEASE("linux-app-release", NATIVE_PACKAGE),
    LINUX_BUNDLE_NAME("linux-package-name", NATIVE_PACKAGE),
    LINUX_DEB_MAINTAINER("linux-deb-maintainer", NATIVE_PACKAGE),
    LINUX_CATEGORY("linux-app-category", NATIVE_PACKAGE),
    LINUX_RPM_LICENSE_TYPE("linux-rpm-license-type", NATIVE_PACKAGE),
    LINUX_PACKAGE_DEPENDENCIES("linux-package-deps", NATIVE_PACKAGE),
    LINUX_SHORTCUT_HINT(build("linux-shortcut").noValue(), NATIVE_PACKAGE),
    LINUX_MENU_GROUP("linux-package-deps", NATIVE_PACKAGE),
    // MacOS-specific
    DMG_CONTENT(build("mac-dmg-content").ofPathArray()),
    MAC_SIGN(build("mac-sign").shortName("s").noValue()),
    MAC_APP_STORE(build("mac-app-store").shortName("s").noValue(), NATIVE_PACKAGE),
    MAC_CATEGORY("mac-app-category"),
    MAC_BUNDLE_NAME("mac-package-name"),
    MAC_BUNDLE_IDENTIFIER("mac-package-identifier"),
    MAC_BUNDLE_SIGNING_PREFIX("mac-package-signing-prefix"),
    MAC_SIGNING_KEY_NAME("mac-signing-key-user-name"),
    MAC_APP_IMAGE_SIGN_IDENTITY("mac-app-image-sign-identity", APP_IMAGE),
    MAC_INSTALLER_SIGN_IDENTITY("mac-installer-sign-identity", NATIVE_PACKAGE),
    MAC_SIGNING_KEYCHAIN("mac-signing-keychain", OptionSpecBuilder::ofPath),
    MAC_ENTITLEMENTS("mac-entitlements"),
    // Windows-specific
    WIN_HELP_URL("win-help-url", OptionSpecBuilder::ofUrl, NATIVE_PACKAGE),
    WIN_UPDATE_URL("win-update-url", OptionSpecBuilder::ofUrl, NATIVE_PACKAGE),
    WIN_MENU_HINT(build("win-menu").noValue(), NATIVE_PACKAGE),
    WIN_MENU_GROUP("win-menu-group", NATIVE_PACKAGE),
    WIN_SHORTCUT_HINT(build("win-shortcut").noValue(), NATIVE_PACKAGE),
    WIN_SHORTCUT_PROMPT("win-shortcut-prompt", NATIVE_PACKAGE),
    WIN_PER_USER_INSTALLATION(build("win-per-user-install").noValue(), NATIVE_PACKAGE),
    WIN_DIR_CHOOSER(build("win-dir-chooser").noValue(), NATIVE_PACKAGE),
    WIN_UPGRADE_UUID("win-upgrade-uuid", NATIVE_PACKAGE),
    WIN_CONSOLE_HINT(build("win-console").noValue()),
    ;

    StandardOption(OptionSpecBuilder builder) {
        this(builder, ALL_PACKAGE_TYPES);
    }

    StandardOption(OptionSpecBuilder builder, PackageTypeGroup packageTypeGroup) {
        this.spec = createOptionSpec(builder, packageTypeGroup);
    }

    StandardOption(String name) {
        this(name, ALL_PACKAGE_TYPES);
    }

    StandardOption(String name, PackageTypeGroup packageTypeGroup) {
        this(name, OptionSpecBuilder::ofString, packageTypeGroup);
    }

    StandardOption(String name, UnaryOperator<OptionSpecBuilder> conv) {
        this(name, conv, ALL_PACKAGE_TYPES);
    }

    StandardOption(String name, UnaryOperator<OptionSpecBuilder> conv,
            PackageTypeGroup packageTypeGroup) {
        this(conv.apply(build(name)), packageTypeGroup);
    }

    @Override
    public OptionSpec getSpec() {
        return spec;
    }

    public static List<StandardOption> filterOptions(PackageType... packageTypes) {
        return Stream.of(values()).filter(option -> {
            return Stream.of(packageTypes).anyMatch(
                    option.spec.supportedPackageTypes()::contains);
        }).toList();
    }

    private static OptionSpecBuilder build(String name) {
        return new OptionSpecBuilder().name(name);
    }

    private static OptionSpec createOptionSpec(OptionSpecBuilder builder,
            PackageTypeGroup packageTypeGroup) {
        Objects.requireNonNull(packageTypeGroup);
        if (Optional.ofNullable(builder.getSupportedPackageTypes()).map(
                Collection::isEmpty).orElse(false)) {
            var name = builder.getName();
            if (name.startsWith("win-exe")) {
                builder.supportedPackageTypes(WIN_EXE);
            } else if (name.startsWith("win-msi")) {
                builder.supportedPackageTypes(WIN_MSI);
            } else if (name.startsWith("linux-deb")) {
                builder.supportedPackageTypes(LINUX_DEB);
            } else if (name.startsWith("linux-rpm")) {
                builder.supportedPackageTypes(LINUX_RPM);
            } else if (name.startsWith("mac-dmg")) {
                builder.supportedPackageTypes(MAC_DMG);
            } else if (name.startsWith("mac-pkg")) {
                builder.supportedPackageTypes(MAC_PKG);
            } else {
                builder.supportedPackageTypes(packageTypeGroup.forOptionName(name));
            }
        }
        return builder.create();
    }

    private final OptionSpec spec;
}
