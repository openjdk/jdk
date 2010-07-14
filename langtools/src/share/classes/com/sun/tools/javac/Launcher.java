/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac;

import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;


/**
 * <b>Unsupported</b> entry point for starting javac from an IDE.
 *
 * <p><b>Note:</b> this class is not available in the JDK.  It is not
 * compiled by default and will not be in tools.jar.  It is designed
 * to be useful when editing the compiler sources in an IDE (as part
 * of a <em>project</em>).  Simply ensure that this class is added to
 * the project and make it the main class of the project.</p>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
class Launcher {
    public static void main(String... args) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        JFileChooser fileChooser;
        Preferences prefs = Preferences.userNodeForPackage(Launcher.class);
        if (args.length > 0)
            fileChooser = new JFileChooser(args[0]);
        else {
            String fileName = prefs.get("recent.file", null);
            fileChooser = new JFileChooser();
            if (fileName != null) {
                fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new File(fileName));
            }
        }
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            String fileName = fileChooser.getSelectedFile().getPath();
            prefs.put("recent.file", fileName);
            javac.run(System.in, null, null, "-d", "/tmp", fileName);
        }
    }
}
