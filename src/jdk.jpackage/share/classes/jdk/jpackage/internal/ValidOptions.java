/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.HashMap;

import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Arguments.CLIOptions;

/**
 * ValidOptions
 *
 * Two basic methods for validating command line options.
 *
 * initArgs()
 *      Computes the Map of valid options for each mode on this Platform.
 *
 * checkIfSupported(CLIOptions arg)
 *      Determine if the given arg is valid on this platform.
 *
 * checkIfImageSupported(CLIOptions arg)
 *      Determine if the given arg is valid for creating app image.
 *
 * checkIfInstallerSupported(CLIOptions arg)
 *      Determine if the given arg is valid for creating installer.
 *
 * checkIfSigningSupported(CLIOptions arg)
 *      Determine if the given arg is valid for signing app image.
 *
 */
class ValidOptions {

    enum USE {
        ALL,        // valid in all cases
        LAUNCHER,   // valid when creating a launcher
        INSTALL,    // valid when creating an installer
        SIGN,       // valid when signing is requested
    }

    private static final HashMap<String, EnumSet<USE>> options = new HashMap<>();

    // initializing list of mandatory arguments
    static {
        put(CLIOptions.NAME.getId(), USE.ALL);
        put(CLIOptions.VERSION.getId(), USE.ALL);
        put(CLIOptions.OUTPUT.getId(), USE.ALL);
        put(CLIOptions.TEMP_ROOT.getId(), USE.ALL);
        put(CLIOptions.VERBOSE.getId(),
                EnumSet.of(USE.ALL, USE.SIGN));
        put(CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId(), USE.ALL);
        put(CLIOptions.RESOURCE_DIR.getId(), USE.ALL);
        put(CLIOptions.DESCRIPTION.getId(), USE.ALL);
        put(CLIOptions.VENDOR.getId(), USE.ALL);
        put(CLIOptions.COPYRIGHT.getId(), USE.ALL);
        put(CLIOptions.PACKAGE_TYPE.getId(),
                EnumSet.of(USE.ALL, USE.SIGN));
        put(CLIOptions.ICON.getId(), USE.ALL);

        put(CLIOptions.INPUT.getId(), USE.LAUNCHER);
        put(CLIOptions.MODULE.getId(), USE.LAUNCHER);
        put(CLIOptions.MODULE_PATH.getId(), USE.LAUNCHER);
        put(CLIOptions.ADD_MODULES.getId(), USE.LAUNCHER);
        put(CLIOptions.MAIN_JAR.getId(), USE.LAUNCHER);
        put(CLIOptions.APPCLASS.getId(), USE.LAUNCHER);
        put(CLIOptions.ARGUMENTS.getId(), USE.LAUNCHER);
        put(CLIOptions.JAVA_OPTIONS.getId(), USE.LAUNCHER);
        put(CLIOptions.ADD_LAUNCHER.getId(), USE.LAUNCHER);
        put(CLIOptions.JLINK_OPTIONS.getId(), USE.LAUNCHER);
        put(CLIOptions.APP_CONTENT.getId(), USE.LAUNCHER);

        put(CLIOptions.LICENSE_FILE.getId(), USE.INSTALL);
        put(CLIOptions.INSTALL_DIR.getId(), USE.INSTALL);
        put(CLIOptions.PREDEFINED_APP_IMAGE.getId(),
                (OperatingSystem.isMacOS()) ?
                        EnumSet.of(USE.INSTALL, USE.SIGN) :
                        EnumSet.of(USE.INSTALL));
        put(CLIOptions.LAUNCHER_AS_SERVICE.getId(), USE.INSTALL);

        put(CLIOptions.ABOUT_URL.getId(), USE.INSTALL);

        put(CLIOptions.FILE_ASSOCIATIONS.getId(),
            (OperatingSystem.isMacOS()) ? USE.ALL : USE.INSTALL);

        if (OperatingSystem.isWindows()) {
            put(CLIOptions.WIN_CONSOLE_HINT.getId(), USE.LAUNCHER);

            put(CLIOptions.WIN_HELP_URL.getId(), USE.INSTALL);
            put(CLIOptions.WIN_UPDATE_URL.getId(), USE.INSTALL);

            put(CLIOptions.WIN_MENU_HINT.getId(), USE.INSTALL);
            put(CLIOptions.WIN_MENU_GROUP.getId(), USE.INSTALL);
            put(CLIOptions.WIN_SHORTCUT_HINT.getId(), USE.INSTALL);
            put(CLIOptions.WIN_SHORTCUT_PROMPT.getId(), USE.INSTALL);
            put(CLIOptions.WIN_DIR_CHOOSER.getId(), USE.INSTALL);
            put(CLIOptions.WIN_UPGRADE_UUID.getId(), USE.INSTALL);
            put(CLIOptions.WIN_PER_USER_INSTALLATION.getId(),
                    USE.INSTALL);
        }

        if (OperatingSystem.isMacOS()) {
            put(CLIOptions.MAC_SIGN.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.MAC_BUNDLE_NAME.getId(), USE.ALL);
            put(CLIOptions.MAC_BUNDLE_IDENTIFIER.getId(), USE.ALL);
            put(CLIOptions.MAC_BUNDLE_SIGNING_PREFIX.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.MAC_SIGNING_KEY_NAME.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.MAC_APP_IMAGE_SIGN_IDENTITY.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.MAC_INSTALLER_SIGN_IDENTITY.getId(),
                    EnumSet.of(USE.INSTALL, USE.SIGN));
            put(CLIOptions.MAC_SIGNING_KEYCHAIN.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.MAC_APP_STORE.getId(), USE.ALL);
            put(CLIOptions.MAC_CATEGORY.getId(), USE.ALL);
            put(CLIOptions.MAC_ENTITLEMENTS.getId(),
                    EnumSet.of(USE.ALL, USE.SIGN));
            put(CLIOptions.DMG_CONTENT.getId(), USE.INSTALL);
        }

        if (OperatingSystem.isLinux()) {
            put(CLIOptions.LINUX_BUNDLE_NAME.getId(), USE.INSTALL);
            put(CLIOptions.LINUX_DEB_MAINTAINER.getId(), USE.INSTALL);
            put(CLIOptions.LINUX_CATEGORY.getId(), USE.INSTALL);
            put(CLIOptions.LINUX_RPM_LICENSE_TYPE.getId(), USE.INSTALL);
            put(CLIOptions.LINUX_PACKAGE_DEPENDENCIES.getId(),
                    USE.INSTALL);
            put(CLIOptions.LINUX_MENU_GROUP.getId(), USE.INSTALL);
            put(CLIOptions.RELEASE.getId(), USE.INSTALL);
            put(CLIOptions.LINUX_SHORTCUT_HINT.getId(), USE.INSTALL);
        }
    }

    static boolean checkIfSupported(CLIOptions arg) {
        return options.containsKey(arg.getId());
    }

    static boolean checkIfImageSupported(CLIOptions arg) {
        EnumSet<USE> value = options.get(arg.getId());
        return value.contains(USE.ALL) ||
                value.contains(USE.LAUNCHER) ||
                value.contains(USE.SIGN);
    }

    static boolean checkIfInstallerSupported(CLIOptions arg) {
        EnumSet<USE> value = options.get(arg.getId());
        return value.contains(USE.ALL) || value.contains(USE.INSTALL);
    }

    static boolean checkIfSigningSupported(CLIOptions arg) {
        EnumSet<USE> value = options.get(arg.getId());
        return value.contains(USE.SIGN);
    }

    private static EnumSet<USE> put(String key, USE value) {
        return options.put(key, EnumSet.of(value));
    }

    private static EnumSet<USE> put(String key, EnumSet<USE> value) {
        return options.put(key, value);
    }
}
