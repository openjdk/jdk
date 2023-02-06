/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6753661
 * @summary  Verifies if JFileChooser font reset after Look & Feel change
 * @run main JFileChooserFontReset
 */
import java.awt.Font;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;

public class JFileChooserFontReset {
    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String args[]) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                 UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            JFileChooser fc = new JFileChooser();
            Font origFont = fc.getFont();
            System.out.println(" orig font " + origFont);
            for (UIManager.LookAndFeelInfo newLaF :
                UIManager.getInstalledLookAndFeels()) {
                if (laf.equals(newLaF)) {
                    // Skip same laf
                    continue;
                }
                System.out.println("Transition to L&F: " + newLaF);
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(newLaF));
                SwingUtilities.updateComponentTreeUI(fc);
                SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
                System.out.println("Back to L&F: " + laf);
                SwingUtilities.updateComponentTreeUI(fc);
                Font curFont = fc.getFont();
                System.out.println("current font " + curFont);
                if (curFont != null && !curFont.equals(origFont)) {
                    throw new RuntimeException(
                         "JFileChooser font did not reset after Look & Feel change");
                }
	    }
	    System.out.println("");
	    System.out.println("");
        }
    }
}

