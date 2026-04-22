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
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.JobAttributes;
import java.awt.Label;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.PageAttributes;
import java.awt.Panel;
import java.awt.PrintJob;
import java.awt.Scrollbar;
import java.awt.ScrollPane;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.JobAttributes.DialogType;
import java.awt.PageAttributes.OriginType;

import java.util.Enumeration;
import java.util.Properties;

/*
 * @test
 * @bug 4247583
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests that the old Properties API still works
 * @run main/manual PrintCompatibilityTest
 */

public class PrintCompatibilityTest {

    public static void main(String[] args) throws Exception {

        String INSTRUCTIONS = """
                A frame window will appear.
                Choose 'Print to Printer...' from the 'Print' menu. Make sure that you print
                to a printer, not a file. Examine the output and verify that the frame and all
                the components in it get printed properly.

                Known problems:
                    * The text in the second row of the menubar is not indented correctly.

                You can also use the 'Print to Screen...' command for a quick manual check that
                printing works, but this is only for debugging purposes.""";

        PassFailJFrame.builder()
                .title("PrintComponentTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(60)
                .testTimeOut(10)
                .testUI(new MainFrame())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }
}

class MainFrame extends Frame {
    private LWContainer lwc;

    public MainFrame() {
        super("PrintCompatibilityTest");

        setSize(800, 400);
        setLayout(new FlowLayout());

        // peered components
        Button button = new Button("Button");
        button.setFont(new Font("Dialog", Font.PLAIN, 12));
        add(button);
        add(new TestCanvas());
        Checkbox cbox = new Checkbox("Checkbox", true);
        cbox.setFont(new Font("DialogInput", Font.PLAIN, 12));
        add(cbox);
        Choice choice = new Choice();
        choice.add("Choice 1");
        choice.add("Choice Two");
        choice.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(choice);
        Label label = new Label("Label");
        label.setFont(new Font("Serif", Font.PLAIN, 12));
        add(label);
        List list = new List();
        list.add("List 1");
        list.add("List Two");
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(list);
        add(new Scrollbar(Scrollbar.VERTICAL) );
        add(new Scrollbar(Scrollbar.HORIZONTAL) );
        ScrollPane scrollpane = new ScrollPane();
        Button spButton = new Button("Button in a scrollpane");
        spButton.setFont(new Font("Monospaced", Font.PLAIN, 12));
        scrollpane.add(spButton);
        add(scrollpane);
        TextArea textarea = new TextArea("TextArea", 3, 30);
        textarea.setFont(new Font("Dialog", Font.ITALIC, 10));
        add(textarea);
        TextField textfield = new TextField("TextField");
        textfield.setFont(new Font("DialogInput", Font.ITALIC, 10));
        add(textfield);

        // nested components
        Panel panel1 = new Panel();
        panel1.setLayout(new FlowLayout());
        panel1.setBackground(Color.red);
        this.add(panel1);

        Button p1Button = new Button("level 2");
        p1Button.setFont(new Font("Monospaced", Font.ITALIC, 10));
        panel1.add(p1Button);

        Panel panel2 = new Panel();
        panel2.setLayout(new FlowLayout());
        panel2.setBackground(Color.green);
        panel1.add(panel2);

        Button p2Button = new Button("level 3");
        p2Button.setFont(new Font("Serif", Font.ITALIC, 10));
        panel2.add(p2Button);


        // lightweight components
        LWButton lwbutton = new LWButton("LWbutton");
        lwbutton.setFont(new Font("SansSerif", Font.ITALIC, 10));
        add(lwbutton);

        lwc = new LWContainer("LWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainerLWContainer");
        lwc.setFont(new Font("Monospaced", Font.ITALIC, 10));
        add(lwc);
        Button lwcButton1 = new Button("HW Button 1");
        Button lwcButton2 = new Button("HW Button 2");
        LWButton lwcButton3 = new LWButton("LW Button");
        lwcButton1.setFont(new Font("Dialog", Font.BOLD, 14));
        lwcButton2.setFont(new Font("DialogInput", Font.BOLD, 14));
        lwcButton3.setFont(new Font("Monospaced", Font.BOLD, 14));
        lwc.add(lwcButton1);
        lwc.add(lwcButton2);
        lwc.add(lwcButton3);

        // overlapping components
        add(new ZOrderPanel());

        ///////////////////////

        Menu menu = new Menu("Print");
        Menu menu2 = new Menu("File");
        Menu menu3 = new Menu("Edit");
        Menu menu4 = new Menu("ReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyReallyLong");
        menu2.setFont(new Font("SansSerif", Font.BOLD, 20));
        menu2.setEnabled(false);
        menu3.setFont(new Font("Monospaced", Font.ITALIC, 18));
        menu3.setEnabled(false);
        menu4.setEnabled(false);
        MenuItem itemPrinter = new MenuItem("Print to Printer...");
        MenuItem itemScreen = new MenuItem("Print to Screen...");
        menu.add(itemPrinter);
        menu.add(itemScreen);
        MenuBar menuBar = new MenuBar();
        menuBar.add( menu );
        menuBar.add( menu2 );
        menuBar.add( menu3 );
        menuBar.add( menu4 );
        setMenuBar(menuBar);

        itemPrinter.addActionListener( new ActionPrint() );
        itemScreen.addActionListener( new ActionPrintToScreen() );
        setVisible(true);
    }

    static void printProps(Properties props)
    {
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String propName = (String)propNames.nextElement();
            PassFailJFrame.log( propName + " = " + props.getProperty(propName));
        }
    }

    class ActionPrint implements ActionListener {
        private final int ITERATIONS = 1;
        private Properties props = new Properties();

        public void actionPerformed(ActionEvent ev) {
            PassFailJFrame.log("About to show print dialog...");
            printProps(props);
            PrintJob pj = getToolkit().getPrintJob(
                MainFrame.this, "Print test!", props);
            if (pj == null) {
                return;
            }
            Dimension d = pj.getPageDimension();
            PassFailJFrame.log("About to print...");
            PassFailJFrame.log("Dimensions: " + d);
            printProps(props);

            // For xor mode set, there is a printing issue with number of copies to be print.
            // So, ITERATIONS are changed to 1 from 3.
            // So, for now the XOR related code is commented out.

            //boolean xor = false;

            for (int i = 0; i < ITERATIONS; i++) {
                Graphics g = pj.getGraphics();
                g.setColor(Color.red);
                //if (xor) {
                //    g.setXORMode(Color.blue);
                //}
                g.translate(13, 13);
                printAll(g);
                g.dispose();
                //xor = (xor) ? false : true;
            }

            // For xor mode set, LWC components don't get printed.
            // So, for now the code is commented out and separate bug
            // (JDK-8340495) is filed to handle it.

            // one more page so that we can test printing a lightweight
            // at the top of the hierarchy (BugId 4212564)
            //Graphics g = pj.getGraphics();
            //g.setColor(Color.red);
            //g.translate(13, 13);
            //lwc.printAll(g);
            //g.dispose();
            // end 4212564

            pj.end();
        }
    }

    class ActionPrintToScreen implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            PrintFrame printFrame = new PrintFrame(MainFrame.this);
            printFrame.show();
            Graphics g = printFrame.getGraphics();
            g.setColor(Color.red);
            printAll(g);
            g.dispose();
        }
    }

    // Frame window that displays results of printing
    // main window to a screen Graphics-- useful for
    // quick testing of printing
    class PrintFrame extends Frame
    {
        private Component printComponent;
        public PrintFrame( Component c )
        {
            super("Print to Screen");
            printComponent = c ;
            addWindowListener( new WindowAdapter() {
                                        public void windowClosing(WindowEvent ev) {
                                            setVisible(false);
                                            dispose();
                                        }
                                    }
                                );
            setSize(printComponent.getSize());
            setResizable(false);
        }

        public void paint( Graphics g ) {
            printComponent.printAll(g);
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
            g.setFont(getFont());
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(Color.black);
            int x = 5;
            int y = (d.height - 5);
            g.drawString(label, x, y);
        }

        public Dimension getPreferredSize()
        {
            return new Dimension(width, height);
        }
    }

    class LWContainer extends Container {
        String label;
        int width = 300;
        int height = 100;

        public LWContainer(String label) {
            super();
            this.label = label;
            setLayout(new FlowLayout());
        }

        public void paint(Graphics g) {
            super.paint(g);
            Dimension d = getSize();
            g.setColor(Color.green);
            g.setFont(getFont());
            g.drawLine(0, 0, d.width - 1, 0);
            g.drawLine(d.width - 1, 0, d.width - 1, d.height - 1);
            g.drawLine(d.width - 1, d.height - 1, 0, d.height - 1);
            g.drawLine(0, d.height - 1, 0, 0);
            g.setColor(Color.black);
            int x = 5;
            int y = (d.height - 5);
            g.drawString(label, x, y);
        }

        public Dimension getPreferredSize()
        {
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

    class ZOrderPanel extends Panel
    {
        ZOrderPanel()
        {
            setLayout(null);

            Component first, second, third, fourth;

            setVisible(true);
            // add first component
            first = makeBox("Second", Color.blue,
                            new Font("Serif", Font.BOLD, 14),
                            -1);
            // insert on top
            second = makeBox("First", Color.yellow,
                             new Font("SansSerif", Font.BOLD, 14),
                             0);
            // put at the back
            fourth = makeBox("Fourth", Color.red,
                             new Font("Monospaced", Font.BOLD, 14),
                             2);
            // insert in last position
            third = makeBox("Third", Color.green,
                            new Font("Dialog", Font.PLAIN, 12),
                            3);
            // swap third and fourth to correct positions
            remove(third);
            add(third, 2);
            // re-validate so third and fourth peers change position
            validate();
            // now make things really interesting with a lightweight
            // component at the top of the z-order, that should print
            // _below_ the native guys to match the screen...
            add(new LWButton("LWButton"), 0);
        }

        public Dimension preferredSize()
        {
            return new Dimension(260, 80);
        }

        public void layout()
        {
            int i, n;
            Insets ins = getInsets();
            n = getComponentCount();
            for (i = n-1; i >= 0; i--) {
                Component p = getComponent(i);
                p.setBounds(ins.left + 40 * i, ins.top + 5 * i, 60, 60);
            }
        }

        public Component makeBox(String s, Color c, Font f, int index)
        {
            Label l = new Label(s);
            l.setBackground(c);
            l.setAlignment(Label.RIGHT);
            l.setFont(f);
            add(l, index);
            validate();
            return l;
        }
    }
}
