/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255439
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary To test tray icon scaling with on-the-fly DPI/Scale changes on Windows
 * @run main/manual TrayIconScalingTest
 * @requires (os.family == "windows")
 */

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.font.TextLayout;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class TrayIconScalingTest {

    private static SystemTray tray;
    private static TrayIcon icon;

    private static final String INSTRUCTIONS =
            "This test checks if the tray icon gets updated when DPI / Scale" +
            " is changed on the fly.\n\n" +
            "STEPS: \n\n" +
            "1. Check the system tray / notification area on Windows" +
            " taskbar, you should see a white icon which displays a" +
            " number.\n\n" +
            "2. Navigate to Settings > System > Display and change the" +
            " display scale by selecting any value from" +
            " Scale & Layout dropdown.\n\n"+
            "3. When the scale changes, check the white tray icon," +
            " there should be no distortion, it should be displayed sharp,\n" +
            " and the displayed number should correspond to the current"+
            " scale:\n" +
            " 100% - 16, 125% - 20, 150% - 24, 175% - 28, 200% - 32.\n\n"+
            " If the icon is displayed sharp and without any distortion," +
            " press PASS, otherwise press FAIL.\n";

    private static final Font font = new Font("Dialog", Font.BOLD, 12);

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        // check if SystemTray supported on the machine
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        PassFailJFrame passFailJFrame = new PassFailJFrame("TrayIcon " +
                "Test Instructions", INSTRUCTIONS, 8, 18, 85);
        createAndShowGUI();
        // does not have a test window,
        // hence only the instruction frame is positioned
        PassFailJFrame.positionTestWindow(null,
                PassFailJFrame.Position.HORIZONTAL);
        try {
            passFailJFrame.awaitAndCheck();
        } finally {
            tray.remove(icon);
        }
    }

    private static void createAndShowGUI() {
        ArrayList<Image> imageList = new ArrayList<>();
        for (int size = 16; size <= 48; size += 4) {
            imageList.add(createIcon(size));
        }
        Image mRImage =
                new BaseMultiResolutionImage(imageList.toArray(new Image[0]));

        tray = SystemTray.getSystemTray();
        icon = new TrayIcon(mRImage);

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException("Error while adding icon to system tray");
        }
    }

    private static Image createIcon(int size) {
        BufferedImage image = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setFont(font);
        g.setColor(Color.BLACK);

        TextLayout layout = new TextLayout(String.valueOf(size),
                g.getFont(), g.getFontRenderContext());
        int height = (int) layout.getBounds().getHeight();
        int width = (int) layout.getBounds().getWidth();
        layout.draw(g, (size - width) / 2f - 1, (size + height) / 2f);
        g.dispose();
        return image;
    }
}
