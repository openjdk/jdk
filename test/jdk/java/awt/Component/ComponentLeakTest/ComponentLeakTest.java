/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @requires (os.family != "linux")
 * @bug 4119609 4149812 4136116 4171960 4170095 4294016 4343272
 * @summary  This test verifies that java.awt objects are being garbage
 * collected correctly. That is, it ensures that unneeded
 * references (such as JNI global refs or refs in static arrays)
 * do not remain after the object is disposed.
 * @run main/othervm ComponentLeakTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.CardLayout;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.HashMap;

public class ComponentLeakTest {

    public static void main(String[] args) {
        final int iter = 5;

        for(int count = 0; count < iter; count++) {
            MainFrame f = new MainFrame();
            MainWindow w = new MainWindow(f);
            MainDialog d = new MainDialog(f);
            TestFileDialog fd = new TestFileDialog(f, "TestFileDialog");
            fd.addNotify(); // fd.show() hangs

            fd.dispose();
            d.dispose();
            w.dispose();
            f.dispose();
        }

        // Test layout managers
        Frame border = new Frame();
        border.setLayout(new BorderLayout());
        Frame card = new Frame();
        card.setLayout(new CardLayout());
        Frame flow = new Frame();
        flow.setLayout(new FlowLayout());
        Frame gridBag = new Frame();
        gridBag.setLayout(new GridBagLayout());
        Frame grid = new Frame();
        grid.setLayout(new GridLayout(1, 2));

        for (int count = 0; count < iter; count++) {
            border.add(new BorderTestButton("BorderTest"),
                    BorderLayout.WEST);
            border.add(new BorderTestButton("BorderTest"),
                    BorderLayout.EAST);
            card.add(new CardTestButton("CardTest"), "card0");
            card.add(new CardTestButton("CardTest"), "card1");
            flow.add(new FlowTestButton());
            flow.add(new FlowTestButton());
            gridBag.add(new GridBagTestButton(), new GridBagConstraints());
            gridBag.add(new GridBagTestButton(), new GridBagConstraints());
            grid.add(new GridTestButton());
            grid.add(new GridTestButton());

            border.removeAll();
            card.removeAll();
            flow.removeAll();
            gridBag.removeAll();
            grid.removeAll();
        }

        gc(5);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }

        freeReferences();
        reportLeaks();
        System.err.println("Test passed.");
    }

    public static void initWindow(Window w) {
        w.setSize(600, 400);
        w.setLayout(new FlowLayout());

        // peered components
        w.add(new TestButton("Button"));
        w.add(new TestCanvas());
        w.add(new TestCheckbox("Checkbox", true));
        TestChoice choice = new TestChoice();
        choice.add("Choice 1");
        choice.add("Choice Two");
        w.add(choice);
        w.add(new TestLabel("Label"));
        TestList list = new TestList();
        list.add("List 1");
        list.add("List Two");
        w.add(list);
        w.add(new TestScrollbar(Scrollbar.VERTICAL));
        w.add(new TestScrollbar(Scrollbar.HORIZONTAL));
        TestScrollPane scrollpane = new TestScrollPane();
        scrollpane.add(new TestButton("Button in a scrollpane"));
        w.add(scrollpane);
        w.add(new TestTextArea("TextArea", 3, 30));
        w.add(new TestTextField("TextField"));

        // nested components
        TestPanel panel1 = new TestPanel();
        panel1.setLayout(new FlowLayout());
        panel1.setBackground(Color.red);
        w.add(panel1);

        panel1.add(new TestButton("level 2"));

        Panel panel2 = new Panel();
        panel2.setLayout(new FlowLayout());
        panel2.setBackground(Color.green);
        panel1.add(panel2);

        panel2.add(new TestButton("level 3"));

        w.add(new TestLightweight("Lightweight"));
    }

    private static ReferenceQueue queue = new ReferenceQueue();
    private static Map<Reference, String> refs = new HashMap<Reference, String>();

    public static void register(Object obj) {
        PhantomReference ref = new PhantomReference(obj, queue);
        refs.put(ref, obj.getClass().getName());
    }

    private static void gc() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Test was interrupted");
        }
    }

    private static void gc(int num) {
        for (; num > 0; num--) {
            gc();
        }
    }

    public static void freeReferences() {
        System.err.println("Total references: " + refs.size());
        boolean wasFreed = false;
        do {
            Object[] arr = new Object[2000];
            gc(5);
            Reference ref = null;
            wasFreed = false;
            while ((ref = queue.poll()) != null) {
                refs.remove(ref);
                wasFreed = true;
                gc();
            }
        } while (wasFreed);
    }

    public static void reportLeaks() {
        for (Reference ref : refs.keySet()) {
            System.err.println("Leaked " + refs.get(ref));
        }

        if (refs.size() > 0) {
            throw new RuntimeException("Some references remained: " + refs.size());
        }
    }
}

class TestFrame extends Frame {
    public TestFrame() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestFrame(String title) {
        super(title);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestWindow extends Window {
    public TestWindow(Frame owner) {
        super(owner);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestWindow(Window owner) {
        super(owner);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestDialogL extends Dialog {
    public TestDialogL(Frame owner) {
        super(owner);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Frame owner, boolean modal) {
        super(owner, modal);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Frame owner, String title) {
        super(owner, title);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Frame owner, String title, boolean modal) {
        super(owner, title, modal);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Dialog owner) {
        super(owner);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Dialog owner, String title) {
        super(owner, title);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestDialogL(Dialog owner, String title, boolean modal) {
        super(owner, title, modal);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestFileDialog extends FileDialog {
    public TestFileDialog(Frame parent) {
        super(parent);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestFileDialog(Frame parent, String title) {
        super(parent, title);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestFileDialog(Frame parent, String title, int mode) {
        super(parent, title, mode);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestButton extends Button {
    public TestButton() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestCanvas extends Canvas {
    int width = 100;
    int height = 100;

    public TestCanvas() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestCanvas(GraphicsConfiguration config) {
        super(config);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

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

class TestCheckbox extends Checkbox {
    public TestCheckbox() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestCheckbox(String label) {
        super(label);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestCheckbox(String label, boolean state) {
        super(label, state);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestCheckbox(String label, boolean state, CheckboxGroup group) {
        super(label, state, group);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestCheckbox(String label, CheckboxGroup group, boolean state) {
        super(label, group, state);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestChoice extends Choice {
    public TestChoice() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestLabel extends Label {
    public TestLabel() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestLabel(String text) {
        super(text);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestLabel(String text, int align) {
        super(text, align);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestList extends List {
    public TestList() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestList(int rows) {
        super(rows);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestList(int rows, boolean multipleMode) {
        super(rows, multipleMode);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestScrollbar extends Scrollbar {
    public TestScrollbar() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestScrollbar(int orientation) {
        super(orientation);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestScrollbar(int orient, int val, int visible, int min, int max) {
        super(orient, val, visible, min, max);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestScrollPane extends ScrollPane {
    public TestScrollPane() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestScrollPane(int policy) {
        super(policy);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}

class TestTextField extends TextField {
    public TestTextField() {
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextField(String text) {
        super(text);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextField(int columns) {
        super(columns);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextField(String text, int columns) {
        super(text, columns);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }
}

class TestTextArea extends TextArea {
    public TestTextArea() {
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextArea(String text) {
        super(text);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextArea(int rows, int columns) {
        super(rows, columns);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextArea(String text, int rows, int columns) {
        super(text, rows, columns);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }

    public TestTextArea(String text, int rows, int columns, int bars) {
        super(text, rows, columns, bars);
        ComponentLeakTest.register(this);
        requestFocus();
        setDropTarget(new TestDropTarget(this));
    }
}

class TestPanel extends Panel {
    public TestPanel() {
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }

    public TestPanel(LayoutManager layout) {
        super(layout);
        ComponentLeakTest.register(this);
        setDropTarget(new TestDropTarget(this));
    }
}
class TestMenu extends Menu {
    public TestMenu() {
        ComponentLeakTest.register(this);
    }

    public TestMenu(String label) {
        super(label);
        ComponentLeakTest.register(this);
    }

    public TestMenu(String label, boolean tearOff) {
        super(label, tearOff);
        ComponentLeakTest.register(this);
    }
}

class TestMenuItem extends MenuItem {
    public TestMenuItem() {
        ComponentLeakTest.register(this);
    }
    public TestMenuItem(String label) {
        super(label);
        ComponentLeakTest.register(this);
    }

    public TestMenuItem(String label, MenuShortcut s) {
        super(label, s);
        ComponentLeakTest.register(this);
    }
}

class TestMenuBar extends MenuBar {
    public TestMenuBar() {
        ComponentLeakTest.register(this);
    }
}

class TestPopupMenu extends PopupMenu {
    public TestPopupMenu() {
        ComponentLeakTest.register(this);
    }

    public TestPopupMenu(String label) {
        super(label);
        ComponentLeakTest.register(this);
    }
}

class TestCheckboxMenuItem extends CheckboxMenuItem {
    public TestCheckboxMenuItem() {
        ComponentLeakTest.register(this);
    }

    public TestCheckboxMenuItem(String label) {
        super(label);
        ComponentLeakTest.register(this);
    }

    public TestCheckboxMenuItem(String label, boolean state) {
        super(label, state);
        ComponentLeakTest.register(this);
    }
}

class BorderTestButton extends Button {
    public BorderTestButton() {
        ComponentLeakTest.register(this);
    }

    public BorderTestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
    }
}

class CardTestButton extends Button {
    public CardTestButton() {
        ComponentLeakTest.register(this);
    }

    public CardTestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
    }
}

class FlowTestButton extends Button {
    public FlowTestButton() {
        ComponentLeakTest.register(this);
    }

    public FlowTestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
    }
}

class GridBagTestButton extends Button {
    public GridBagTestButton() {
        ComponentLeakTest.register(this);
    }

    public GridBagTestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
    }
}

class GridTestButton extends Button {
    public GridTestButton() {
        ComponentLeakTest.register(this);
    }

    public GridTestButton(String title) {
        super(title);
        ComponentLeakTest.register(this);
    }
}

class TestLightweight extends Component {
    String label;
    int width = 100;
    int height = 30;

    public TestLightweight(String label) {
        this.label = label;
        ComponentLeakTest.register(this);
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

    public Dimension getPreferredSize() {
        return new Dimension(width,height);
    }
}

class TestDropTarget extends DropTarget {
    public TestDropTarget(Component comp) {
        super(comp, new DropTargetListener() {
            public void dragEnter(DropTargetDragEvent dtde) {}
            public void dragOver(DropTargetDragEvent dtde) {}
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            public void dragExit(DropTargetEvent dte) {}
            public void drop(DropTargetDropEvent dtde) {}
        });
        ComponentLeakTest.register(this);
    }
}

class MainWindow extends TestWindow {
    public MainWindow(Frame f) {
        super(f);
        ComponentLeakTest.initWindow(this);
        setVisible(true);

        TestPopupMenu popup = new TestPopupMenu("hi");
        add(popup);
        popup.show(this, 5, 5);
    }
}

class MainDialog extends TestDialogL {
    public MainDialog(Frame f) {
        super(f, "MainDialog", false);
        ComponentLeakTest.initWindow(this);
        setVisible(true);

        TestPopupMenu popup = new TestPopupMenu("hi");
        add(popup);
        popup.show(this, 5, 5);
    }
}

class MainFrame extends TestFrame {
    public MainFrame(){
        super("Component Leak Test MainFrame");

        ComponentLeakTest.initWindow(this);

        TestMenu menu = new TestMenu("Print");
        TestMenu menu2 = new TestMenu("File");
        TestMenu menu3 = new TestMenu("Edit");
        TestMenu menu4 = new TestMenu("ReallyReallyReallyReallyReallyReallyReallyReally" +
                "ReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyLong");
        menu2.setFont(new Font("SansSerif", Font.BOLD, 20));
        menu2.setEnabled(false);
        menu3.setFont(new Font("Monospaced", Font.ITALIC, 18));
        menu3.setEnabled(false);
        menu4.setEnabled(false);
        TestMenuItem itemPrinter  = new TestMenuItem("foobar");
        TestMenuItem itemScreen  = new TestMenuItem("baz");
        TestCheckboxMenuItem itemCheck = new TestCheckboxMenuItem("yep");
        menu.add(itemPrinter);
        menu.add(itemScreen);
        menu.add(itemCheck);
        TestMenuBar menuBar = new TestMenuBar();
        menuBar.add( menu );
        menuBar.add( menu2 );
        menuBar.add( menu3 );
        menuBar.add( menu4 );
        setMenuBar(menuBar);

        setVisible(true);

        TestPopupMenu popup = new TestPopupMenu("hi");
        add(popup);
        popup.show(this, 5, 5);
    }
}
