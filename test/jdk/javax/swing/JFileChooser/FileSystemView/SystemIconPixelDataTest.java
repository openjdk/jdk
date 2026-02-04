/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;

/**
 * @test
 * @bug 8376253
 * @summary FileSystemView may not report system icons when -Xcheck:jni enabled
 * @run main SystemIconPixelDataTest
 * @run main/othervm -Xcheck:jni SystemIconPixelDataTest
 */
public final class SystemIconPixelDataTest {

    public static void main(String[] args) throws Exception {
        for (LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            AtomicBoolean ok = new AtomicBoolean();
            EventQueue.invokeAndWait(() -> ok.set(setLookAndFeel(laf)));
            if (ok.get()) {
                EventQueue.invokeAndWait(SystemIconPixelDataTest::test);
            }
        }
    }

    private static boolean setLookAndFeel(LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println("LookAndFeel: " + laf.getClassName());
            return true;
        } catch (UnsupportedLookAndFeelException ignored) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void test() {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File home = fsv.getHomeDirectory();
        Icon icon = fsv.getSystemIcon(home);
        if (icon == null) {
            return;
        }
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) {
            throw new RuntimeException("Invalid icon size: " + w + "x" + h);
        }
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (img.getRGB(x, y) != 0) {
                    return;
                }
            }
        }
        throw new RuntimeException("All pixels are zero");
    }
}
