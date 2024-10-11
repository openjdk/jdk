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

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
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
 * @bug 4035189
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
                1. Select the Image for a Dialog and Frame using either
                   Load/Save/Just Dialog.
                2. Set the Icon Image/s to Frame and Dialog. Verify that the
                   Icon is set for the respective Frame and Dialog.
                   If selected Icon is set to Frame and Dialog press PASS
                   else FAIL.
                                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static void setImagesToFD(java.util.List<Image> listIcon) {
        FileDialogIconTest.images = listIcon;
    }

    public static void setImagesToFrame(java.util.List<Image> listIcon) {
        frame.setIconImages(listIcon);
    }

    public static void setImageToFD(Image img) {
        FileDialogIconTest.image = img;
    }

    public static void setImageToFrame(Image img) {
        frame.setIconImage(img);
    }

    public static Frame initialize() {
        frame = new Frame("FileDialogIconTest");
        Button setImageButton1 = new Button("setIconImageToFrame");
        Button setImageButton2 = new Button("setIconImageToDialog");
        Button setImageButton3 = new Button("setIconImagesToFrame");
        Button setImageButton4 = new Button("setIconImagesToDialog");
        Button setImageButton5 = new Button("setIconBufferedImagesToDialog");
        Button setImageButton6 = new Button("setIconBufferedImagesToFrame");

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
                    PassFailJFrame.log("Loaded image . setting to frame");
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        setImageButton3.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    Image image;
                    java.util.List<Image> list = new java.util.ArrayList();
                    for (int i = 1; i <= 4; i++) {
                        String fileName = fileBase + "T" + i + ".gif";
                        image = Toolkit.getDefaultToolkit().getImage(fileName);
                        PassFailJFrame.log("Loaded image " + fileName + ". setting to the list for frame");
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
                        PassFailJFrame.log("Loaded image " + fileName + ". setting to the list for dialog");
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
                List<BufferedImage> list = new ArrayList<>();
                try {
                    Robot robot = new Robot();
                    Rectangle rectangle;
                    for (int i = 1; i <= 4; i++) {
                        rectangle = new Rectangle(i * 10, i * 10, i * 10 + 40, i * 10 + 40);
                        java.awt.image.BufferedImage image = robot.createScreenCapture(rectangle);
                        robot.delay(100);
                        list.add(image);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                PassFailJFrame.log("Captured images and set to the list for dialog");
            }
        });

        setImageButton6.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                List<BufferedImage> list = new ArrayList<>();
                try {
                    Robot robot = new Robot();
                    Rectangle rectangle;
                    for (int i = 1; i <= 4; i++) {
                        rectangle = new Rectangle(i * 10, i * 10, i * 10 + 40, i * 10 + 40);
                        java.awt.image.BufferedImage image = robot.createScreenCapture(rectangle);
                        robot.delay(100);
                        list.add(image);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                PassFailJFrame.log("Captured images and set to the list for frame");
            }
        });

        Button buttonLoad = new Button("Load Dialog");
        Button buttonSave = new Button("Save Dialog");
        Button buttonSimple = new Button("Just Dialog");
        buttonLoad.addActionListener(new MyActionListener(FileDialog.LOAD, "LOAD"));
        buttonSave.addActionListener(new MyActionListener(FileDialog.SAVE, "SAVE"));
        buttonSimple.addActionListener(new MyActionListener(-1, ""));

        frame.setSize(400, 400);
        frame.setLayout(new FlowLayout());
        frame.add(buttonLoad);
        frame.add(buttonSave);
        frame.add(buttonSimple);
        frame.add(setImageButton1);
        frame.add(setImageButton2);
        frame.add(setImageButton3);
        frame.add(setImageButton4);
        frame.pack();
        return frame;
    }
}

class MyActionListener implements ActionListener {
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
            if (FileDialogIconTest.image != null) {
                filedialog.setIconImage(FileDialogIconTest.image);
            }

            if (FileDialogIconTest.images != null) {
                filedialog.setIconImages(FileDialogIconTest.images);
            }
            filedialog.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
