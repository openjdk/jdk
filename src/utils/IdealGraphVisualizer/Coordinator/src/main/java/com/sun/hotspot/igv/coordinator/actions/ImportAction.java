/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.sun.hotspot.igv.coordinator.actions;

import com.sun.hotspot.igv.coordinator.OutlineTopComponent;
import com.sun.hotspot.igv.settings.Settings;
import java.io.File;
import java.io.IOException;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.openide.util.*;
import org.openide.util.actions.CallableSystemAction;

/**
 *
 * @author Thomas Wuerthinger
 */

public final class ImportAction extends CallableSystemAction {

    public static FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.getName().toLowerCase().endsWith(".xml") || f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Graph files (*.xml)";
            }
        };
    }

    @Override
    public void performAction() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(ImportAction.getFileFilter());
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));
        fc.setMultiSelectionEnabled(true);

        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            for (final File file : fc.getSelectedFiles()) {
                File dir = file;
                if (!dir.isDirectory()) {
                    dir = dir.getParentFile();
                }

                Settings.get().put(Settings.DIRECTORY, dir.getAbsolutePath());
                try {
                    final OutlineTopComponent component = OutlineTopComponent.findInstance();
                    component.loadGraphDocument(file.getAbsolutePath());
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ImportAction.class, "CTL_ImportAction");
    }

    public ImportAction() {
        putValue(Action.SHORT_DESCRIPTION, "Import from XML...");
        // D is the Control key on most platforms, the Command (meta) key on Macintosh
        putValue(Action.ACCELERATOR_KEY, Utilities.stringToKey("D-O"));
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon(iconResource(), true));
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/coordinator/images/import.png";
    }
}
