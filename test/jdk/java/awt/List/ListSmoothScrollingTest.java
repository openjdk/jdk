/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 *
 */

/*
 * @test
 * @bug 4665745
 * @summary JCK1.4/13a, interactive: api/java_awt/interactive/ComponentTests.html#ComponentT
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "windows")
 * @run main/manual/othervm -Dsun.java2d.uiScale=1.0 ListSmoothScrollingTest
 */

import java.awt.Button;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.List;
import java.awt.Panel;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Properties;

public class ListSmoothScrollingTest {

    private static final String INSTRUCTIONS = """
          This test is for Windows platform only.

          Scroll the list box. The scrolling should be smooth.
          If some artifacts appear the test failed else see the next step.

          Press the "Print to Screen..." button the frame with an exact
          copy of the list box should appear.
          If there are some differences between the original list box and
          its copy the test failed else see the next step

          Press the "Print to Printer..." button and examine the printed list box.

          If there are no differences between the original list box and
          its printed copy the test passed else failed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ListSmoothScrollingTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(Test::new)
                .build()
                .awaitAndCheck();
    }
}

class Test extends Frame implements ActionListener {

    Panel tp;
    List li;
    Button buttonScreen;
    Button buttonPrinter;

    public Test() {
        super("ListSmoothScrollingTest Frame");
        setLayout(new FlowLayout());

        buttonScreen = new Button("Print to Screen...");
        buttonScreen.setActionCommand("screen");
        buttonScreen.addActionListener(this);
        add(buttonScreen);

        buttonPrinter = new Button("Print to Printer...");
        buttonPrinter.setActionCommand("printer");
        buttonPrinter.addActionListener(this);
        add(buttonPrinter);

        li = new List(7, false);
        add(li);
        for (int i = 1; i <= 17; i++)
            li.add("Item " + i);
        li.select(11);

        pack();
    }

    public void actionPerformed(ActionEvent ev) {
        String cmd = ev.getActionCommand();
        if (cmd.equals("screen")) {
            PrintFrame printFrame = new PrintFrame(Test.this);
            printFrame.setLocation(500, 300);
            printFrame.setVisible(true);
        }
        if (cmd.equals("printer")) {
            Properties props = new Properties();
            PrintJob pj = Toolkit.getDefaultToolkit().getPrintJob(Test.this, "Print test!", props);
            if( pj != null ) {
                Graphics g = pj.getGraphics();
                printComponents(g);
                pj.end();
            }
        }
    }

    class PrintFrame extends Frame {
        private Container printContainer;

        public PrintFrame(Container c) {
            super("Print to Screen");
            printContainer = c ;
            addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent ev) {
                        setVisible(false);
                        dispose();
                    }
                }
            );
            setSize(printContainer.getSize());
            setResizable(false);
        }

        public void paint(Graphics g) {
            printContainer.printComponents(g);
        }
    }

}
