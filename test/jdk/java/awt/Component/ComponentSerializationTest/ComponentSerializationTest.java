/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4146452
 * @summary Tests serialization of peered and lightweight Components.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ComponentSerializationTest
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Label;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import javax.swing.JPanel;

public class ComponentSerializationTest extends JPanel {
    private MainFrame mf;
    private MainWindow mw;
    private MainDialog md;
    private MainFileDialog mfd;
    private static final String INSTRUCTIONS = """
        A Frame, a Window, and a Dialog should appear. From the Frame's
        "Serialize" menu, select "Serialize!". Another Frame, Window, and
        Dialog should appear exactly on top of the existing ones. The state
        and functionality of the two sets of Windows should be identical. If
        any errors or exceptions appear in the log area, or if the second set of
        Windows is different from the first, the test fails. Otherwise, the
        test passes.
    """;

    private static final ArrayList<Window> toDispose = new ArrayList<>();

    public ComponentSerializationTest() {
        mf = new MainFrame();
        toDispose.add(mf);
        mw = new MainWindow(mf);
        toDispose.add(mw);
        md = new MainDialog(mf);
        toDispose.add(md);
        mfd = new MainFileDialog(mf);
        toDispose.add(mfd);
    }

    public static void main(String[] argc) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Component Serialization Test")
                .splitUI(ComponentSerializationTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
        for (Window w : toDispose) {
            if (w != null) {
                EventQueue.invokeAndWait(w::dispose);
            }
        }
    }

    private void initWindow(Window w) {
        w.setSize(600, 400);
        w.setLayout(new FlowLayout());

        // peered components
        w.add(new Button("Button"));
        w.add(new TestCanvas());
        w.add(new Checkbox("Checkbox", true));
        Choice choice = new Choice();
        choice.add("Choice 1");
        choice.add("Choice Two");
        w.add(choice);
        w.add(new Label("Label"));
        List list = new List();
        list.add("List 1");
        list.add("List Two");
        w.add(list);
        w.add(new Scrollbar(Scrollbar.VERTICAL));
        w.add(new Scrollbar(Scrollbar.HORIZONTAL));
        ScrollPane scrollpane = new ScrollPane();
        scrollpane.add(new Button("Button in a scrollpane"));
        w.add(scrollpane);
        w.add(new TextArea("TextArea", 3, 30));
        w.add(new TextField("TextField"));

        // nested components
        Panel panel1 = new Panel();
        panel1.setLayout(new FlowLayout());
        panel1.setBackground(Color.red);
        w.add(panel1);

        panel1.add(new Button("level 2"));

        Panel panel2 = new Panel();
        panel2.setLayout(new FlowLayout());
        panel2.setBackground(Color.green);
        panel1.add(panel2);

        panel2.add(new Button("level 3"));

        // lightweight components
        w.add(new LWButton("LWbutton") );

        // overlapping components
        w.add(new ZOrderPanel());
    }

    class MainWindow extends Window {
        public MainWindow(Frame f) {
            super(f);
            initWindow(this);
            setLocation(650, 0);
            setVisible(true);
        }
    }

    class MainDialog extends Dialog {
        public MainDialog(Frame f) {
            super(f, "MainDialog", false);
            initWindow(this);
            setLocation(0, 450);
            setVisible(true);
        }
    }

    class MainFileDialog extends FileDialog {
        public MainFileDialog(Frame f) {
            super(f, "MainFileDialog", FileDialog.SAVE);
            setLocation(650, 450);
            addNotify();
        }
    }

    class MainFrame extends Frame {
        public MainFrame() {
            super("ComponentSerializationTest");
            initWindow(this);

            Menu menu = new Menu("Serialize");
            Menu menu2 = new Menu("File");
            Menu menu3 = new Menu("Edit");
            Menu menu4 = new Menu("ReallyReallyReallyReallyReallyReallyReallyReally" +
                    "ReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyLong");
            menu2.setFont(new Font("SansSerif", Font.BOLD, 20));
            menu2.setEnabled(false);
            menu3.setFont(new Font("Monospaced", Font.ITALIC, 18));
            menu3.setEnabled(false);
            menu4.setEnabled(false);
            MenuItem itemSerialize  = new MenuItem("Serialize!");
            CheckboxMenuItem itemCheck  = new CheckboxMenuItem("Check me");
            menu.add(itemSerialize);
            menu.add(itemCheck);
            MenuBar menuBar = new MenuBar();
            menuBar.add(menu);
            menuBar.add(menu2);
            menuBar.add(menu3);
            menuBar.add(menu4);
            setMenuBar(menuBar);

            itemSerialize.addActionListener(new ActionSerialize());

            setLocation(0, 0);
            setVisible(true);
        }
    }

    class ActionSerialize implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            Frame f2 = null;
            Window w2 = null;
            Dialog d2 = null;
            FileDialog fd2 = null;

            try {
                FileOutputStream fos = new FileOutputStream("tmp");
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(mf);
                oos.writeObject(mw);
                oos.writeObject(md);
                oos.writeObject(mfd);
                oos.flush();

                FileInputStream fis = new FileInputStream("tmp");
                ObjectInputStream ois = new ObjectInputStream(fis);
                f2 = (Frame)ois.readObject();
                w2 = (Window)ois.readObject();
                d2 = (Dialog)ois.readObject();
                fd2= (FileDialog)ois.readObject();
            } catch (Exception e) {
                PassFailJFrame.log(e.getMessage());
            }

            if (f2 == null || w2 == null || d2 == null || fd2 == null) {
                PassFailJFrame.log("ERROR: one of the components was not deserialized.");
                PassFailJFrame.log("frame = " + f2);
                PassFailJFrame.log("window = " + w2);
                PassFailJFrame.log("dialog = " + d2);
                PassFailJFrame.log("file dalog = " + fd2);
            }

            if (f2 != null) {
                toDispose.add(f2);
                f2.setVisible(true);
            }
            if (w2 != null) {
                toDispose.add(w2);
                w2.setVisible(true);
            }
            if (d2 != null) {
                toDispose.add(d2);
                d2.setVisible(true);
            }
            if (fd2 != null) {
                toDispose.add(fd2);
                fd2.addNotify();
            }
        }
    }

    class LWButton extends Component {
        String label;
        int width = 100;
        int height = 30;

        public LWButton(String label) {
            super();
            this.label = label;
        }

        public void paint(Graphics g) {
            Dimension d = getSize();
            g.setColor(Color.orange);
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(Color.black);
            int x = 5;
            int y = (d.height - 5);
            g.drawString(label, x, y);
        }

        public Dimension getPreferredSize()     {
            return new Dimension(width, height);
        }
    }

    class TestCanvas extends Canvas {
        int width = 100;
        int height = 100;

        public void paint(Graphics g) {
            g.setColor(Color.blue);
            g.fillRoundRect(10, 10, 50, 50, 15, 30);
            g.setColor(Color.red);
            g.fillOval(70, 70, 25, 25);
        }
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }
    }

    class ZOrderPanel extends Panel {
        public ZOrderPanel() {
            setLayout(null);

            Component first, second, third, fourth;

            show();
            first = makeBox("Second", Color.blue, -1);
            second = makeBox("First", Color.yellow, 0);
            fourth = makeBox("Fourth", Color.red, 2);
            third = makeBox("Third", Color.green, 3);
            remove(third);
            add(third, 2);
            validate();
            add(new LWButton("LWButton"), 0);
        }

        public Dimension preferredSize() {
            return new Dimension(260, 80);
        }

        public void layout() {
            int i, n;
            Insets ins = insets();
            n = countComponents();
            for (i = n - 1; i >= 0; i--) {
                Component p = getComponent(i);
                p.reshape(ins.left + 40 * i, ins.top + 5 * i, 60, 60);
            }
        }

        public Component makeBox(String s, Color c, int index) {
            Label l = new Label(s);
            l.setBackground(c);
            l.setAlignment(Label.RIGHT);
            add(l, index);
            validate();
            return l;
        }
    }
}
