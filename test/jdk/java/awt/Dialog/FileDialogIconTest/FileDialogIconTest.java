/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * @test
 * @bug 6425126
 * @summary Test to verify that PIT File Dialog icon not matching with
 *          the new java icon (frame Icon) - PIT build
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogIconTest
 */

public class FileDialogIconTest {
    public static Frame frame;
    public static Image image;
    public static List<Image> images;
    static String fileBase;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                The 1st row of buttons in testUI are to open Load,
                Save and Simple Dialog respectively.

                The rest of the buttons are to set icons to
                the Frame and Dialog.

                1. Set an icon for the Frame and Dialog by clicking
                   on one of the set icon buttons.
                2. Verify that the icon is set for the Frame (testUI)
                   and for the Dialog (by clicking on Load, Save or
                   Simple Dialog button).

                If selected icon is set to Frame and Dialog press PASS
                else FAIL.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions Frame")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static void setImagesToFD(List<Image> listIcon) {
        images = listIcon;
    }

    public static void setImagesToFrame(List<Image> listIcon) {
        frame.setIconImages(listIcon);
    }

    public static void setImageToFD(Image img) {
        image = img;
    }

    public static void setImageToFrame(Image img) {
        frame.setIconImage(img);
    }

    public static Frame initialize() {
        frame = new Frame("FileDialogIconTest TestUI");
        Button setImageButton1 = new Button("setIconImageToFrame");
        Button setImageButton2 = new Button("setIconImageToDialog");
        Button setImageButton3 = new Button("setIconImagesToFrame");
        Button setImageButton4 = new Button("setIconImagesToDialog");
        Button setImageButton5 = new Button("setIconBufferedImagesToFrame");
        Button setImageButton6 = new Button("setIconBufferedImagesToDialog");

        if (System.getProperty("test.src") == null) {
            fileBase = "";
        } else {
            fileBase = System.getProperty("test.src") + System.getProperty("file.separator");
        }

        final String fileName = fileBase + "loading-msg.gif";

        setImageButton1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    Image image = Toolkit.getDefaultToolkit().getImage(fileName);
                    setImageToFrame(image);
                    PassFailJFrame.log("Loaded image. Setting to frame");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        setImageButton2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    Image image = Toolkit.getDefaultToolkit().getImage(fileName);
                    setImageToFD(image);
                    PassFailJFrame.log("Loaded image. Setting to dialog");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        setImageButton3.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    Image image;
                    List<Image> list = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        String fileName = fileBase + "T" + i + ".gif";
                        image = Toolkit.getDefaultToolkit().getImage(fileName);
                        PassFailJFrame.log("Loaded image " + "T" + i + ".gif."
                                           + "Setting to the list for frame");
                        list.add(image);
                    }
                    setImagesToFrame(list);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        setImageButton4.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    Image image;
                    List<Image> list = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        String fileName = fileBase + "T" + i + ".gif";
                        image = Toolkit.getDefaultToolkit().getImage(fileName);
                        PassFailJFrame.log("Loaded image " + "T" + i + ".gif."
                                           + "Setting to the list for dialog");
                        list.add(image);
                    }
                    setImagesToFD(list);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        setImageButton5.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                List<Image> list = new ArrayList<>();
                try {
                    Robot robot = new Robot();
                    Rectangle rectangle;
                    for (int i = 1; i <= 4; i++) {
                        rectangle = new Rectangle(i * 10, i * 10,
                                            i * 10 + 40, i * 10 + 40);
                        BufferedImage image = robot.createScreenCapture(rectangle);
                        robot.delay(100);
                        list.add(image);
                    }
                    setImagesToFrame(list);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                PassFailJFrame.log("Captured images and set to the list for frame");
            }
        });

        setImageButton6.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                List<Image> list = new ArrayList<>();
                try {
                    Robot robot = new Robot();
                    Rectangle rectangle;
                    for (int i = 1; i <= 4; i++) {
                        rectangle = new Rectangle(i * 10, i * 10,
                                            i * 10 + 40, i * 10 + 40);
                        BufferedImage image = robot.createScreenCapture(rectangle);
                        robot.delay(100);
                        list.add(image);
                    }
                    setImagesToFD(list);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                PassFailJFrame.log("Captured images and set to the list for dialog");
            }
        });

        Button buttonLoad = new Button("Load Dialog");
        Button buttonSave = new Button("Save Dialog");
        Button buttonSimple = new Button("Simple Dialog");
        buttonLoad.addActionListener(new MyActionListener(FileDialog.LOAD, "LOAD"));
        buttonSave.addActionListener(new MyActionListener(FileDialog.SAVE, "SAVE"));
        buttonSimple.addActionListener(new MyActionListener(-1, ""));

        frame.setLayout(new GridLayout(2, 1));

        Panel panel1 = new Panel(new GridLayout(1, 3));
        panel1.add(buttonLoad);
        panel1.add(buttonSave);
        panel1.add(buttonSimple);

        Panel panel2 = new Panel(new GridLayout(3, 2));
        panel2.add(setImageButton1);
        panel2.add(setImageButton2);
        panel2.add(setImageButton3);
        panel2.add(setImageButton4);
        panel2.add(setImageButton5);
        panel2.add(setImageButton6);

        frame.add(panel1);
        frame.add(panel2);
        frame.pack();

        return frame;
    }

    static class MyActionListener implements ActionListener {
        int id;
        String name;

        public MyActionListener(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public void actionPerformed(ActionEvent ae) {
            try {
                FileDialog filedialog;
                if (id == -1 && Objects.equals(name, "")) {
                    filedialog = new FileDialog(FileDialogIconTest.frame);
                } else {
                    filedialog = new FileDialog(FileDialogIconTest.frame, name, id);
                }
                if (image != null) {
                    filedialog.setIconImage(image);
                }

                if (FileDialogIconTest.images != null) {
                    filedialog.setIconImages(images);
                }
                filedialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
