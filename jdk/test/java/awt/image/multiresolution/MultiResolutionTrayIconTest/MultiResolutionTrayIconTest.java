/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 8150176
  @ignore 8150176
  @summary Check if correct resolution variant is used for tray icon.
  @author a.stepanov
  @run applet/manual=yesno MultiResolutionTrayIconTest.html
*/


import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;


public class MultiResolutionTrayIconTest extends Applet {

    private SystemTray tray;
    private TrayIcon   icon, iconMRI;

    public void init() { this.setLayout(new BorderLayout()); }

    public void start() {

        boolean trayIsSupported = SystemTray.isSupported();
        Button b = new Button("Start");
        if (trayIsSupported) {

            prepareIcons();
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) { doTest(); }
            });
        } else {
             b.setLabel("not supported");
             b.setEnabled(false);
             System.out.println("system tray is not supported");
        }
        add(b, BorderLayout.CENTER);

        validate();
        setVisible(true);
    }

    private BufferedImage generateImage(int w, int h, Color c) {

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        int r = (Math.min(w, h) >= 8) ? 3 : 1;
        g.fillRect(r, r, w - 2 * r, h - 2 * r);
        return img;
    }

    private void prepareIcons() {

        tray = SystemTray.getSystemTray();
        Dimension d = tray.getTrayIconSize();
        int w = d.width, h = d.height;

        BufferedImage img = generateImage(w, h, Color.BLUE);
        // use wrong icon size for "nok"
        BufferedImage nok = generateImage(w / 2 + 2, h / 2 + 2, Color.RED);
        BaseMultiResolutionImage mri =
            new BaseMultiResolutionImage(new BufferedImage[] {nok, img});
        icon    = new TrayIcon(img);
        iconMRI = new TrayIcon(mri);
    }

    private void doTest() {

        if (tray.getTrayIcons().length > 0) { return; } // icons were added already
        try {
            tray.add(icon);
            tray.add(iconMRI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {

        // check for null, just in case
        if (tray != null) {
            tray.remove(icon);
            tray.remove(iconMRI);
        }
    }
}
