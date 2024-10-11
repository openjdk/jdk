/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 6255196
 * @key headful
 * @summary Verifies the supported actions on different platforms.
 * @library /test/lib
 * @run main/othervm ActionSupportTest
 */

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import javax.swing.JMenuBar;
import jtreg.SkippedException;

import static java.awt.desktop.QuitStrategy.NORMAL_EXIT;

public class ActionSupportTest {

    public static void main(String[] args) {
        final File file = new File("nonExistentFile");
        final URI uri = URI.create("nonExistentSchema:anything");
        final StringBuilder error = new StringBuilder();

        if (!Desktop.isDesktopSupported()) {
            throw new SkippedException("Class java.awt.Desktop is not supported on " +
                    "current platform. Farther testing will not be performed");
        }

        Desktop desktop = Desktop.getDesktop();
        for (Desktop.Action action : Desktop.Action.values()) {
            boolean supported = desktop.isSupported(action);

            try {
                switch (action) {
                    case OPEN:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.open(file);
                        break;
                    case EDIT:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.edit(file);
                        break;
                    case PRINT:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.print(file);
                        break;
                    case MAIL:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.mail(uri);
                        break;
                    case BROWSE:
                        if (supported) {
                            continue; // prevent native message about strange schema
                        }
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.browse(uri);
                        break;
                    case APP_EVENT_FOREGROUND:
                    case APP_EVENT_HIDDEN:
                    case APP_EVENT_REOPENED:
                    case APP_EVENT_SCREEN_SLEEP:
                    case APP_EVENT_SYSTEM_SLEEP:
                    case APP_EVENT_USER_SESSION:
                        continue; // Has no effect if SystemEventListener's sub-type
                        // is unsupported on the current platform.
                    case APP_ABOUT:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setAboutHandler(e -> {
                        });
                        break;
                    case APP_PREFERENCES:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setPreferencesHandler(e -> {
                        });
                        break;
                    case APP_OPEN_FILE:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setOpenFileHandler(e -> {
                        });
                        break;
                    case APP_PRINT_FILE:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setPrintFileHandler(e -> {
                        });
                        break;
                    case APP_OPEN_URI:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setOpenURIHandler(e -> {
                        });
                        break;
                    case APP_QUIT_HANDLER:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setQuitHandler((e, response) -> {
                        });
                        break;
                    case APP_QUIT_STRATEGY:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setQuitStrategy(NORMAL_EXIT);
                        break;
                    case APP_SUDDEN_TERMINATION:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.enableSuddenTermination();
                        break;
                    case APP_REQUEST_FOREGROUND:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.requestForeground(true);
                        break;
                    case APP_HELP_VIEWER:
                        if (supported) {
                            continue; // prevent open separate window
                        }
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.openHelpViewer();
                        break;
                    case APP_MENU_BAR:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.setDefaultMenuBar(new JMenuBar());
                        break;
                    case BROWSE_FILE_DIR:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.browseFileDirectory(file);
                        break;
                    case MOVE_TO_TRASH:
                        // if not supported, an UnsupportedOperationException will be thrown.
                        // if supported, other exception might be thrown.
                        desktop.moveToTrash(file);
                        break;
                }
                // no exception has been thrown.
                if (!supported) {
                    error.append("Action " + action.name() + " is an " +
                            "unsupported operation, but no exception has been thrown\n");
                }
            } catch (UnsupportedOperationException uoe) {
                if (!supported) {
                    System.out.println("Action " + action.name() + "is not supported.");
                } else {
                    error.append("Action " + action.name() + " is a " +
                            "supported operation, " +
                            "but UnsupportedOperationException has been thrown\n");
                }
            } catch (Exception e) {
                if (supported) {
                    System.out.println("Action " + action.name() + "supported.");
                } else {
                    error.append("Action " + action.name() + " is an " +
                            "unsupported operation, but " +
                            "UnsupportedOperationException has not been thrown\n");
                }
            }
        }

        if (!error.isEmpty()) {
            System.err.println(error);
            throw new RuntimeException("One or more tests failed. " +
                    "Look at the error output for details");
        }
        System.out.println("Test completed");
    }
}
