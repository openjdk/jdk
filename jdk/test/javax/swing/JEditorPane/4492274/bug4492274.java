/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4492274
 * @summary  Tests if JEditorPane.getPage() correctly returns anchor reference.
 * @author Denis Sharypov
 */

import sun.awt.SunToolkit;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.net.URL;

public class bug4492274 {

    private static URL page;

    private static JEditorPane jep;

    public static void main(String args[]) throws Exception {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI();
            }
        });

        toolkit.realSync();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    page = new URL(page, "#linkname");
                    jep.setPage(page);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        toolkit.realSync();

        if (getPageAnchor() == null) {
            throw new RuntimeException("JEditorPane.getPage() returns null anchor reference");
        }

    }

    private static String getPageAnchor() throws Exception {
        final String[] result = new String[1];

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                result[0] = jep.getPage().getRef();
            }
        });

        return result[0];
    }

    private static void createAndShowGUI() {
        try {
            File file = new File(System.getProperty("test.src", "."), "test.html");
            page = file.toURI().toURL();

            JFrame f = new JFrame();

            jep = new JEditorPane();
            jep.setEditorKit(new HTMLEditorKit());
            jep.setEditable(false);
            jep.setPage(page);

            JScrollPane sp = new JScrollPane(jep);

            f.getContentPane().add(sp);
            f.setSize(500, 500);
            f.setVisible(true);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
