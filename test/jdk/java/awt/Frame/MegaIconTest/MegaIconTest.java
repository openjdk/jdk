/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.MediaTracker;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ImageProducer;
import java.net.URL;

/*
 * @test
 * @bug 4175560
 * @summary Test use of user-defined icons
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MegaIconTest
 */

public class MegaIconTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Each of the buttons in the main window represents a test
                of certain icon functionality -  background transparency/opacity
                of the icon, scaling etc.
                Clicking on each button brings up a window displaying the graphic
                that should appear in the corresponding icon.
                Click on each button, minimize the resulting window, and check that
                the icon is displayed as the test name indicates.
                On Win32, icons should also be displayed correctly in the title bar.
                If all the test pass, then this test passes, else fail.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(35)
                .testUI(MegaIconTest::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        //Create the iconTestFrames and add to IconTestButtons
        IconTestButtons itb = new IconTestButtons(new IconTestFrame[]{
                new IconTestFrame("Opaque, Scaled Icon Test",
                        "duke_404.gif"),

                new IconTestFrame("Transparent Icon",
                        "dukeWave.gif"),

                new IconTestFrameBG("Transparent, Scaled Icon with bg",
                        "fight.gif", Color.red),

                new IconTestFrameDlg("Transparent icon w/ Dialog",
                        "dukeWave.gif")
        });
        itb.pack();
        return itb;
    }
}

class IconTestButtons extends Frame {
    public IconTestButtons(IconTestFrame[] iconTests) {
        IconTestFrame tempTest;
        Button newBtn;
        Panel newPnl;
        DoneLabel newLbl;

        setTitle("MegaIconTest");

        setLayout(new GridLayout(iconTests.length, 1));

        //For each icon test frame
        //Get name, add button with name and action to
        //display the window, and add label "done" after

        for (int i = 0; i < iconTests.length; i++) {
            tempTest = iconTests[i];
            newBtn = new Button(tempTest.getTestName());
            newLbl = new DoneLabel();
            newBtn.addActionListener(new IconTestActionListener(tempTest,
                    newLbl));
            newPnl = new Panel();
            newPnl.add(newBtn);
            newPnl.add(newLbl);
            add(newPnl);
        }
    }

    protected class DoneLabel extends Label {
        public DoneLabel() {
            super("Done");
            setVisible(false);
        }
    }

    protected class IconTestActionListener implements ActionListener {
        IconTestFrame f;
        DoneLabel l;

        public IconTestActionListener(IconTestFrame frame, DoneLabel label) {
            this.f = frame;
            this.l = label;
        }

        public void actionPerformed(ActionEvent e) {
            f.pack();
            f.setVisible(true);
            l.setVisible(true);
            IconTestButtons.this.pack();
        }
    }
}

class IconTestFrame extends Frame {
    private String testName;
    int width, height;
    Image iconImage;
    MediaTracker tracker;

    public IconTestFrame(String testName, String iconFileName) {
        super(testName);
        this.testName = testName;
        tracker = new MediaTracker(this);

        //Set icon image
        URL url = MegaIconTest.class.getResource(iconFileName);
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (tk == null) {
            System.out.println("Toolkit is null!");
        }
        if (url == null) {
            System.out.println("Can't load icon is null!");
            return;
        }
        try {
            iconImage = tk.createImage((ImageProducer) url.getContent());
        } catch (java.io.IOException e) {
            System.out.println("Unable to load icon image from url: " + url);
        }
        tracker.addImage(iconImage, 0);
        try {
            tracker.waitForAll();
        } catch (java.lang.InterruptedException e) {
            System.err.println(e);
        }
        width = iconImage.getWidth(this);
        height = iconImage.getHeight(this);
        setIconImage(iconImage);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        setLayout(new BorderLayout());
        setBackground(Color.YELLOW);

        //Add the icon graphic and instructions to the Frame
        add(new IconCanvas(), "Center");
        pack();
    }

    class IconCanvas extends Canvas {
        public void paint(Graphics g) {
            if (IconTestFrame.this.iconImage == null) {
                throw new NullPointerException();
            }
            g.drawImage(IconTestFrame.this.iconImage, 0, 0, this);
        }

        public Dimension getPreferredSize() {
            return new Dimension(IconTestFrame.this.width,
                    IconTestFrame.this.height);
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    public String getTestName() {
        return testName;
    }
}

class IconTestFrameBG extends IconTestFrame {
    public IconTestFrameBG(String testName, String iconFileName, Color bg) {
        super(testName, iconFileName);
        setBackground(bg);
        Panel p = new Panel();
        p.setLayout(new GridLayout(3, 1));
        p.add(new Label("The background of this window has been set."));
        p.add(new Label("Unless the default icon background is the same color,"));
        p.add(new Label("the icon background should NOT be this color."));
        add(p, "North");
        pack();
    }
}

class IconTestFrameDlg extends IconTestFrame implements ActionListener {
    Dialog dlg;
    Button dlgBtn;

    public IconTestFrameDlg(String testName, String iconFilename) {
        super(testName, iconFilename);
        Panel p = new Panel();
        p.setLayout(new GridLayout(4, 1));
        p.add(new Label("Click on the button below to display a child dialog."));
        p.add(new Label("On Win32, the Dialog's titlebar icon should match"));
        p.add(new Label("the titlebar icon of this window."));
        p.add(new Label("Minimizing this Frame should yield only one icon."));
        add(p, "North");

        dlg = new Dialog(this);
        dlg.setSize(200, 200);
        dlg.add(new Label("Dialog stuff."));
        dlg.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        dlgBtn = new Button("Display Dialog");
        dlgBtn.addActionListener(this);
        add(dlgBtn, "South");
    }

    public void actionPerformed(ActionEvent e) {
        dlg.setVisible(true);
    }
}
