/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.OperatingSystem;

import java.util.ResourceBundle;
import java.io.File;
import java.text.MessageFormat;


/**
 * CLIHelp
 *
 * Generate and show the command line interface help message(s).
 */
public class CLIHelp {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.HelpResources");

    // generates --help for jpackage's CLI
    public static void showHelp(boolean noArgs) {

        if (noArgs) {
            Log.info(I18N.getString("MSG_Help_no_args"));
        } else {
            OperatingSystem platform = OperatingSystem.current();
            String types;
            String pLaunchOptions;
            String pInstallOptions;
            String pInstallDir;
            String pAppImageDescription;
            String pSignSampleUsage;
            String pAppContentNote;
            switch (platform) {
                case MACOS:
                    types = "{\"app-image\", \"dmg\", \"pkg\"}";
                    pLaunchOptions = I18N.getString("MSG_Help_mac_launcher");
                    pInstallOptions = I18N.getString("MSG_Help_mac_install");
                    pInstallDir
                            = I18N.getString("MSG_Help_mac_linux_install_dir");
                    pAppImageDescription
                            = I18N.getString("MSG_Help_mac_app_image");
                    pSignSampleUsage
                            = I18N.getString("MSG_Help_mac_sign_sample_usage");
                    pAppContentNote
                            = I18N.getString("MSG_Help_mac_app_content_note");
                    break;
                case LINUX:
                    types = "{\"app-image\", \"rpm\", \"deb\"}";
                    pLaunchOptions = "";
                    pInstallOptions = I18N.getString("MSG_Help_linux_install");
                    pInstallDir
                            = I18N.getString("MSG_Help_mac_linux_install_dir");
                    pAppImageDescription
                            = I18N.getString("MSG_Help_default_app_image");
                    pSignSampleUsage = "";
                    pAppContentNote = "";
                    break;
                case WINDOWS:
                    types = "{\"app-image\", \"exe\", \"msi\"}";
                    pLaunchOptions = I18N.getString("MSG_Help_win_launcher");
                    pInstallOptions = I18N.getString("MSG_Help_win_install");
                    pInstallDir
                            = I18N.getString("MSG_Help_win_install_dir");
                    pAppImageDescription
                            = I18N.getString("MSG_Help_default_app_image");
                    pSignSampleUsage = "";
                    pAppContentNote = "";
                    break;
                default:
                    types = "{\"app-image\", \"exe\", \"msi\", \"rpm\", \"deb\", \"pkg\", \"dmg\"}";
                    pLaunchOptions = I18N.getString("MSG_Help_win_launcher")
                            + I18N.getString("MSG_Help_mac_launcher");
                    pInstallOptions = I18N.getString("MSG_Help_win_install")
                            + I18N.getString("MSG_Help_linux_install")
                            + I18N.getString("MSG_Help_mac_install");
                    pInstallDir
                            = I18N.getString("MSG_Help_default_install_dir");
                    pAppImageDescription
                            = I18N.getString("MSG_Help_default_app_image");
                    pSignSampleUsage = "";
                    pAppContentNote = "";
                    break;
            }
            Log.info(MessageFormat.format(I18N.getString("MSG_Help"),
                    File.pathSeparator, types, pLaunchOptions,
                    pInstallOptions, pInstallDir, pAppImageDescription,
                    pSignSampleUsage, pAppContentNote));
        }
    }
}
