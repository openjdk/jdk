/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.Map;
import jtreg.SkippedException;

/*
 * @test
 * @key headful
 * @bug 4310333
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @summary  A unit test for TrayIcon RFE
 * @run main/manual TrayIconTest
 */

public class TrayIconTest {
    private static SystemTray tray;
    static Frame frame = new Frame("TrayIcon Test");
    private static final String INSTRUCTIONS = """
            The test frame contains CheckboxGroup of tray icons.
            A selected checkbox represents the TrayIcon (or null
            TrayIcon) whose functionality is currently tested.

            If you are under Linux make sure your Application Panel has
            System Tray (on Gnome it is called Notification Area).

            Perform all the cases (1-7) documented below.

            CASE 1: Testing ADD/REMOVE/PropertyChange functionality.
            --------------------------------------------------------
             1. Select null TrayIcon and pressAdd button:
                - NullPointerException should be thrown.
             2. Select some of the valid TrayIcons and press Add button:
                - The selected TrayIcon should appear in the SystemTray.
                - PropertyChangeEvent should be fired (the property is
                  an array of TrayIcons added to the system tray).
             3. Press Add button again:
                - IllegalArgumentException should be thrown.
                - No PropertyChangeEvent should be fired.
             4. Press Remove button:
                - The TrayIcon should disappear from the SystemTray.
                - PropertyChangeEvent should be fired.
             5. Press Remove button again:
                - It should have no effect.
                - No PropertyChangeEvent should be fired.
             6. Add all the valid TrayIcons (by selecting everyone and pressing Add
                button):
                - All the TrayIcons should appear in the SystemTray.
                - PropertyChangeEvent should be fired on each adding.
             7. Remove all the TrayIcons (again by selecting everyone and pressing
                Remove):
                - All the TrayIcons should disappear from the SystemTray.
                - PropertyChangeEvent should be fired on each removing.
             8. Not for Windows! Remove the system tray (Notification Area) from
                the desktop. Try to add some valid TrayIcon:
                - AWTException should be thrown.
                - No PropertyChangeEvent should be fired.
             9. Not for Windows! Add the system tray back to the desktop. Add all the
                valid TrayIcons:
                - All the TrayIcons should appear in the system tray.
                - PropertyChangeEvent should be fired on each adding.
             11. Not for Windows! Remove the system tray from the desktop:
                - All the TrayIcons should disappear.
                - PropertyChangeEvent should be fired for each TrayIcon
                  removal.
                - PropertyChangeEvent should be fired for SystemTray removal.
             12. Add the system tray and go to the next step.
                - All the TrayIcons should appear again.
                - PropertyChangeEvent should be fired for SystemTray addition.
                - PropertyChangeEvent shouldn't be fired for TrayIcon removal.

            CASE 2: Testing RESIZE functionality.
            -------------------------------------
             1. Select some of the TrayIcons and add it. Then press resize button:
                - The TrayIcon selected should be resized to fit the area it occupies.
             2. Press resize button again:
                - The TrayIcon should be resized to the original size.
             3. Repeat the 1-2 steps for other TrayIcons:
                - The TrayIcons should be resized appropriately.

            CASE 3: Testing EVENTS functionality
            ---------------------------------
             1. Select some of the TrayIcons and add it. Select MouseEvent from the
                group of checkboxes at the top-right of the test frame.
                Click on the TrayIcon in the SystemTray:
                - MOUSE_PRESSED MOUSE_RELEASED and MOUSE_CLICKED events should be
                  generated.
             2. Press mouse inside the TrayIcon dragging mouse and releasing it.
                - Make sure that MOUSE_CLICKED event is not triggered.
             3. Click on the TrayIcon with different modification keys:
                - there should be appropriate modifiers in the events.
             4. Keep clicking on the TrayIcon:
               - there should be correct absolute coordinates in the events.
             5. Only for Windows! Focus the system tray using keyboard:
               - press WIN key once to bring up the start menu then press ESC once to
                 close the menu the focus should be on the start button
               - press TAB key for several times until you focus on the system
                 tray then use ARROW keys to move to the TrayIcon
               - press ENTER or SPACE should trigger ACTION_PERFORMED message
                 make sure that mouse events are not  triggered.
             6. Select MouseMotionEvent checkbox. Move mouse over the TrayIcon:
               - MOUSE_MOVED event should be generated. It should contain
                 correct coordinates.
             7. Deselect both the checkboxes and then select AWTEventListener.
                Click on the TrayIcon and then move mouse over it:
                - Appropriate mouse events should be generated (catched by the
                  AWTEventListener).
             8. Deselect all the checkboxes and go to the following step.

            CASE 4: Testing DISPLAY MESSAGE functionality.
            ----------------------------------------------
             1. Select some of the TrayIcons and add it. Then press Display message
                button:
                - A balloon message should appear near the TrayIcon.
             2. After the message is displayed wait for some period:
                - The message window should be closed automatically.
             3. Display the message again. Close it by pressing X in its top-right
                corner:
                - The message window should be closed immediately.
             4. Display the message again. Click inside it:
                - The message should be closed an ACTION_PERFORMED event should be
                  generated with correct information and an Ok dialog should appear.
                  Close the dialog.
             5. Select a message type from the Type choice and display the message
                again:
                - It should contain an icon appropriate to the message type selected
                  or no icon if NONE is selected.
             6. Change the content of the Message and Caption text fields and
                display the message:
                - The message content should be changed in the accordance with the text
                  typed.
             7. Not for Windows! Type some too long or too short text for the Caption
                and Message:
                - The message should process the text correctly. The long text should
                  be cut.
             8. Not for Windows! Type null in the Message text field and display
                the message:
                - The message body should contain no text.
             9. Type null in the Caption text field and display the message:
                - The message caption should contain no text.
             10. Type null in the both Message and Caption fields and display
                 the message:
                - NullPointerException should be generated and no message should be
                  displayed.
             11. Try to hide the taskbar. Click Display message for several times.
                 Then restore the taskbar. Click on the TrayIcon:
                 - No message should appear.
                 Try to display the message once more:
                 - It should appear appropriately.
             12. Try to display the message for other TrayIcons:
                 - The messages should be displayed appropriately.

            CASE 5: Testing POPUP MENU functionality.
            -----------------------------------------
             1. Add some TrayIcon to the system tray. Press Set button in the
                Popup menu test area. Trigger the popup menu for the TrayIcon with
                the mouse:
                - A popup menu should appear. Make sure it behaves properly.
                - Make sure the 'duke.gif' image is animated while the popup menu is shown.
             2. Press Remove button for the popup menu and try to trigger it again:
                - No popup menu should appear.
             3. Perform 1-2 steps for other TrayIcons:
                - Make sure the popup menu behaves properly.
             4. Add more than one TrayIcons to the system tray. Press Set button in
                the PopupMenu test area for some of the TrayIcon added. Trigger
                the popup menu for this TrayIcon:
                - A popup menu should appear properly.
             5. Try to set the popup menu to the same TrayIcon again:
                - It should have no effect
             6. Try to set the popup menu for other TrayIcons you've added to the system
                tray:
                - for each one IllegalArgumentException should be thrown.

            CASE 6: Testing TOOLTIP functionality.
            --------------------------------------
             1. Type something in the Tooltip text field and press Set button.
                Then move mouse cursor over the TrayIcon and wait for a second:
                - A tooltip should appear containing the text typed.
             2. Show a tooltip again and keep your mouse over the TrayIcon for some period:
                - The tooltip should disappear automatically.
             3. Show a tooltip again and leave the TrayIcon:
                - The tooltip should disappear immediately.
             4. Type null in the Tooltip field and press set then move your
                mouse to the SystemTray:
                - The tooltip shouldn't appear.
             5. Type something too long in the Tooltip field and show the tooltip:
                - The tooltip text should be cut.

            CASE 7: Testing ACTION functionality.
            -------------------------------------
             1. Add some TrayIcon to the system tray. Double click it with the left mouse
                button:
                - An ACTION_PERFORMED event should be generated.
             2. Double click the TrayIcon with the left mouse button several times:
                - Several ACTION_PERFORMED events should be generated
                - Make sure that the time-stamp of each event ('when' field) is increased.

            If all the above cases work as expected Press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new SkippedException("Test not applicable as"
                                       + " System Tray not supported");
        }
        try {
            PassFailJFrame.builder()
                          .title("TrayIconTest Instructions")
                          .instructions(INSTRUCTIONS)
                          .columns(50)
                          .rows(40)
                          .testUI(TrayIconTest::createAndShowUI)
                          .logArea(10)
                          .build()
                          .awaitAndCheck();

        } finally {
            EventQueue.invokeAndWait(() -> {
                if (tray != null) {
                    //Remove any remaining tray icons before ending the test.
                    TrayIcon[] icons = tray.getTrayIcons();
                    for (TrayIcon icon : icons) {
                        tray.remove(icon);
                    }
                }
            });
        }
    }

    private static Frame createAndShowUI() {
        final TrayIconControl ctrl = new TrayIconControl();
        frame.setLayout(new BorderLayout());
        frame.add(ctrl.cont, BorderLayout.CENTER);
        frame.setBackground(Color.LIGHT_GRAY);

        frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    ctrl.dispose();
                }
            });

        frame.pack();
        return frame;
    }

    private static class TrayIconControl {
        final String RED_ICON = "RED ICON";
        final String BLUE_ICON = "BLUE ICON";
        final String GREEN_ICON = "GREEN ICON";

        CheckboxGroup cbg = new CheckboxGroup();
        Button addButton = new PackedButton("   Add   ");
        Button remButton = new PackedButton("Remove");
        Button resizeButton = new PackedButton("Resize");
        Button balloonButton = new PackedButton("Display message");
        Choice balloonChoice = new Choice();
        String[] balloonTypes = new String[] { "ERROR", "WARNING", "INFO", "NONE" };

        TextField balloonText = new TextField(
                                "A TrayIcon can generate various MouseEvents and"
                                + " supports adding corresponding listeners to receive"
                                + " notification of these events. TrayIcon processes"
                                + " some of the events by itself. For example,"
                                + " by default, when the right-mouse click", 70);
        TextField balloonCaption = new TextField("TrayIcon", 70);

        MessageType[] typeArr = new MessageType[] { MessageType.ERROR, MessageType.WARNING,
                MessageType.INFO, MessageType.NONE };
        Checkbox mouseListenerCbox =  new Checkbox("MouseEvent");
        Checkbox motionListenerCbox = new Checkbox("  MouseMotionEvent");
        Checkbox awtListenerCbox =    new Checkbox("  AWTEventListener");
        TextField tipText = new TextField("TrayIcon", 50);
        Button tipButton = new PackedButton("Set");
        Button setPopupButton = new PackedButton("Set");
        Button remPopupButton = new PackedButton("Remove");

        PopupMenu popupMenu = new PopupMenu();

        Map<String, TrayIcon> resToObjMap = new HashMap<>();

        Container cont = new Container();

        TrayIconControl() {
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getSource() instanceof TrayIcon && awtListenerCbox.getState()) {
                    PassFailJFrame.log(e.toString());
                }
            }, MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK |
                   ActionEvent.ACTION_EVENT_MASK);

            cont.setLayout(new GridLayout(4, 1));

            Container raw1 = new Container();
            raw1.setLayout(new GridLayout(1, 4));
            cont.add(raw1);

            InsetsPanel cbgPanel = new InsetsPanel();
            cbgPanel.setLayout(new GridLayout(4, 1));
            Checkbox nullCbox = new Checkbox("null", cbg, true);
            Checkbox redCbox = new Checkbox(RED_ICON, cbg, false);
            Checkbox blueCbox = new Checkbox(BLUE_ICON, cbg, false);
            Checkbox greenCbox = new Checkbox(GREEN_ICON, cbg, false);
            cbgPanel.add(nullCbox);
            cbgPanel.add(redCbox);
            cbgPanel.add(blueCbox);
            cbgPanel.add(greenCbox);
            cbgPanel.addTo(raw1);

            InsetsPanel addremPanel = new InsetsPanel();
            addremPanel.setLayout(new BorderLayout());
            addremPanel.add(addButton.getParent(), BorderLayout.NORTH);
            addremPanel.add(remButton.getParent(), BorderLayout.SOUTH);
            addremPanel.addTo(raw1);

            InsetsPanel resizePanel = new InsetsPanel();
            resizePanel.add(resizeButton);
            resizePanel.addTo(raw1);

            InsetsPanel lstPanel = new InsetsPanel();
            lstPanel.setLayout(new GridLayout(3, 1));
            lstPanel.add(mouseListenerCbox);
            lstPanel.add(motionListenerCbox);
            lstPanel.add(awtListenerCbox);
            lstPanel.addTo(raw1);

            Container raw2 = new Container();
            raw2.setLayout(new BorderLayout());
            cont.add(raw2);

            InsetsPanel balloonPanel = new InsetsPanel();
            balloonPanel.setLayout(new BorderLayout());
            balloonPanel.add(balloonButton.getParent(), BorderLayout.NORTH);
            Container bc = new Container();
            bc.setLayout(new FlowLayout());
            bc.add(new Label("  Type:"));
            bc.add(balloonChoice);
            balloonPanel.add(bc, BorderLayout.SOUTH);
            balloonPanel.addTo(raw2, BorderLayout.WEST);

            InsetsPanel blnTextPanel = new InsetsPanel();
            blnTextPanel.setLayout(new GridLayout(2, 2));
            Container c1 = new Panel();
            c1.setLayout(new FlowLayout());
            blnTextPanel.add(c1);
            c1.add(new Label("Message:"));
            c1.add(balloonText);

            Container c2 = new Panel();
            c2.setLayout(new FlowLayout());
            blnTextPanel.add(c2);
            c2.add(new Label("Caption:"));
            c2.add(balloonCaption);
            blnTextPanel.addTo(raw2, BorderLayout.CENTER);


            Container raw3 = new Container();
            raw3.setLayout(new BorderLayout());
            cont.add(raw3);

            InsetsPanel popupPanel = new InsetsPanel();
            popupPanel.setLayout(new FlowLayout());
            popupPanel.add(new Label("Popup menu:"));
            popupPanel.add(setPopupButton);
            popupPanel.add(remPopupButton);
            popupPanel.addTo(raw3);


            Container raw4 = new Container();
            raw4.setLayout(new BorderLayout());
            cont.add(raw4);

            InsetsPanel tipPanel = new InsetsPanel();
            tipPanel.setLayout(new FlowLayout());
            tipPanel.add(new Label("Tooltip:"));
            tipPanel.add(tipText);
            tipPanel.add(tipButton);
            tipPanel.addTo(raw4);

            addButton.addActionListener(e -> {
                try {
                    tray.add(getCurIcon());
                } catch (NullPointerException npe) {
                    if (npe.getMessage() == null) {
                        PassFailJFrame.log("Probably wrong path to the images.");
                        throw npe; // if wrong images path was set
                    }
                    PassFailJFrame.log(npe.toString());
                } catch (IllegalArgumentException iae) {
                    PassFailJFrame.log(iae.toString());
                } catch (AWTException ise) {
                    PassFailJFrame.log(ise.toString());
                }
            });
            remButton.addActionListener(e -> tray.remove(getCurIcon()));

            resizeButton.addActionListener(
                    e -> getCurIcon().setImageAutoSize(!getCurIcon().isImageAutoSize()));

            balloonButton.addActionListener(e -> {
                String text = null, caption = null;
                if (balloonText.getText().compareToIgnoreCase("null") != 0) {
                    text = balloonText.getText();
                }
                if (balloonCaption.getText().compareToIgnoreCase("null") != 0) {
                    caption = balloonCaption.getText();
                }
                try {
                    getCurIcon().displayMessage(caption, text, typeArr[balloonChoice.getSelectedIndex()]);
                } catch (NullPointerException npe) {
                    PassFailJFrame.log(npe.toString());
                }
            });

            tipButton.addActionListener(e -> {
                String tip = null;
                if (tipText.getText().compareToIgnoreCase("null") != 0) {
                    tip = tipText.getText();
                }
                getCurIcon().setToolTip(tip);
            });

            setPopupButton.addActionListener(e -> {
                try {
                    getCurIcon().setPopupMenu(popupMenu);
                } catch (IllegalArgumentException iae) {
                    PassFailJFrame.log(iae.toString());
                }
            });

            remPopupButton.addActionListener(e -> getCurIcon().setPopupMenu(null));
            for (String s: balloonTypes) {
                balloonChoice.add(s);
            }

            init();
        }

        void init() {
            tray = SystemTray.getSystemTray();
            tray.addPropertyChangeListener("trayIcons",
                                           e -> printPropertyChangeEvent(e));

            tray.addPropertyChangeListener("systemTray",
                                           e -> printPropertyChangeEvent(e));

            configureTrayIcon(RED_ICON);
            configureTrayIcon(BLUE_ICON);
            configureTrayIcon(GREEN_ICON);

            for (String s: balloonTypes) {
                popupMenu.add(new MenuItem(s));
            }
        }

        void printPropertyChangeEvent(PropertyChangeEvent e) {
            String name = e.getPropertyName();
            Object oldValue = e.getOldValue();
            Object newValue = e.getNewValue();

            PassFailJFrame.log("PropertyChangeEvent[name=" + name
                               + ",oldValue=" + oldValue + ",newValue=" + newValue + "]");
        }

        void configureTrayIcon(String icon) {
            Color color = Color.WHITE;
            switch (icon) {
                case "RED ICON" -> color = Color.RED;
                case "BLUE ICON" -> color = Color.BLUE;
                case "GREEN ICON" -> color = Color.GREEN;
            }
            Image image = createIcon(color);
            TrayIcon trayIcon = new TrayIcon(image);

            trayIcon.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (mouseListenerCbox.getState())
                        PassFailJFrame.log(e.toString());
                }
                public void mouseReleased(MouseEvent e) {
                    if (mouseListenerCbox.getState())
                        PassFailJFrame.log(e.toString());
                }
                public void mouseClicked(MouseEvent e) {
                    if (mouseListenerCbox.getState())
                        PassFailJFrame.log(e.toString());
                }
            });
            trayIcon.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    if (motionListenerCbox.getState())
                        PassFailJFrame.log(e.toString());
                }
            });
            trayIcon.addActionListener(e -> PassFailJFrame.log(e.toString()));

            resToObjMap.remove(icon);
            resToObjMap.put(icon, trayIcon);
        }

        String getCurImgName() {
            return cbg.getSelectedCheckbox().getLabel();
        }

        TrayIcon getCurIcon() {
            return resToObjMap.get(getCurImgName());
        }

        public void dispose() {
            tray.remove(getCurIcon());
        }

        private static Image createIcon(Color color) {
            BufferedImage image = new BufferedImage(16, 16,
                                                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(color);
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            return image;
        }

    }

    private static class InsetsPanel extends Panel {
        Container parent = new Container() {
            public Insets getInsets() {
                return new Insets(2, 2, 2, 2);
            }
        };

        InsetsPanel() {
            parent.setLayout(new BorderLayout());
            setBackground(new Color(240, 240, 240));
        }

        void addTo(Container c) {
            parent.add(this);
            c.add(parent);
        }

        void addTo(Container c, String pos) {
            parent.add(this);
            c.add(parent, pos);
        }
    }

    private static class PackedButton extends Button {
        Container parent = new Container();
        PackedButton(String l) {
            super(l);
            parent.setLayout(new FlowLayout());
            parent.add(this);
        }
    }
}

