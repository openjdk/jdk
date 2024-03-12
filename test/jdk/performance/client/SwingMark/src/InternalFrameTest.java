/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * A SwingMark to test two different aspects of InternalFrames.
 *   -repaint Brings each internal frame to the front of the
 *            other internal frames and message each widget to repaint.
 *   -move    Moves each of the internal frames around the desktop.
 *
 * If you you do not specify one the default is to do both.
 * The following options are also available:
 *   -numLevels          Number of levels to create, default is 1.
 *   -numInternalFrames  Number of internal frames to create per level,
                         default is 10.
 *   -numButtons         Number of widgets to create, default is 10.
 *   -laf                Look and feel to use, default is metal.
 *
 */

public class InternalFrameTest extends AbstractSwingTest {

    JDesktopPane        desktop;
    JFrame              frame;
    int                 numInternalFrames;
    int                 numButtons;
    int                 numLevels;
    JInternalFrame[][]  frames;
    int                 whatTest;

    JComponent          widget;

    public InternalFrameTest(int numLevels, int numInternalFrames,
                             int numButtons, int whatTest) {
        this.numLevels = numLevels;
        this.numInternalFrames = numInternalFrames;
        this.numButtons = numButtons;
        this.whatTest = whatTest;
        frames = new JInternalFrame[numLevels][];
        createDesktopPane();
        createInternalFrames();
    }

    public InternalFrameTest() {
        this( 1, 10, 10, 3);
    }

    public String getTestName() {
        return "InternalFrame";
    }

    public JComponent getTestComponent() {
        return desktop;
    }

    public void runTest() {
        if ((whatTest & 1) != 0) {
            testMove();
        }
        if ((whatTest & 2) != 0) {
            testRepaint();
        }
    }

    protected void createDesktopPane() {
        desktop = new JDesktopPane();
        desktop.setPreferredSize(new Dimension(425, 425));
        desktop.setMinimumSize(new Dimension(425, 425));
    }

    protected void createInternalFrames() {
        Container       parent = desktop;

        for (int counter = 0; counter < numLevels; counter++) {
            Integer      level = Integer.valueOf(JLayeredPane.DEFAULT_LAYER.
                                             intValue() + counter);

            frames[counter] = new JInternalFrame[numInternalFrames];
            for (int iCounter = 0; iCounter < numInternalFrames; iCounter++) {
                JInternalFrame       internalFrame;

                internalFrame = createInternalFrame(level, iCounter);
                parent.add(internalFrame);
                frames[counter][iCounter] = internalFrame;
            }
        }
    }

    protected JInternalFrame createInternalFrame(Integer id, int number) {
        JInternalFrame       jif;

        jif = new JInternalFrame("Internal Frame " + id + " " + number,
                                 true, true, true, true);
        createWidgets(jif);

        jif.setBounds((number * 50) % 220, (number * 50) % 220,
                     100 + (number * 50) % 125,
                     100 + (number * 25) % 125);

        return jif;
    }

    protected void createWidgets(JInternalFrame jif) {
        Container         parent = jif.getContentPane();
        JComponent        child = null;

        parent.setLayout(new FlowLayout());
        for (int counter = 0; counter < numButtons; counter++) {
            switch (counter % 4) {
            case 0:
                child = new JButton("Button " + counter);
                break;
            case 1:
                child = new JCheckBox("CheckBox " + counter);
                break;
            case 2:
                child = new JTextField("TF " + counter);
                break;
            case 3:
                child = new JLabel("Label " + counter);
                break;
            }
            parent.add(child);
        }
        if (widget == null) {
            widget = child;
        }
    }

    protected void slide(MoveRunnable mv, JInternalFrame frame,
                         int x, int y, int newX, int newY) {
        int        xInc = (newX - x) / 10;
        int        yInc = (newY - y) / 10;

        mv.jif = frame;
        mv.moveToFront = true;
        if (xInc != 0 || yInc != 0) {
            mv.jif = frame;
            for (int counter = 0; counter < 10; counter++) {
                x += xInc;
                y += yInc;
                mv.newX = x;
                mv.newY = y;
                try {
                    SwingUtilities.invokeLater(mv);
                    rest();
                }
                catch (Exception ex) {
                    System.out.println("--> " + ex);
                }
            }
        }
    }

    protected void testMove() {
        Rectangle              tempRect = new Rectangle();
        int                    width = desktop.getWidth();
        int                    height = desktop.getHeight();
        int                    tempWidth;
        int                    tempHeight;
        MoveRunnable           mv = new MoveRunnable();


        for (int counter = frames.length - 1; counter >= 0; counter--) {
            for (int iCounter = frames[counter].length - 1; iCounter >= 0;
                iCounter--) {
                JInternalFrame       iFrame = frames[counter][iCounter];

                iFrame.getBounds(tempRect);
                tempWidth = width - tempRect.width;
                tempHeight = height - tempRect.height;
                // Slide to origin
                slide(mv, iFrame, tempRect.x, tempRect.y, 0, 0);
                // Slide to the right
                slide(mv, iFrame, 0, 0, tempWidth, 0);
                // Slide down
                slide(mv, iFrame, tempWidth, 0, tempWidth, tempHeight);
                // Slide to the left
                slide(mv, iFrame, tempWidth, tempHeight, 0, tempHeight);
                // Slide to original spot.
                slide(mv, iFrame, 0, tempHeight, tempRect.x, tempRect.y);
            }
        }
    }

    public void testRepaint() {
        MoveRunnable      mr = new MoveRunnable();
        Rectangle         tempRect = new Rectangle();

        for (int counter = frames.length - 1; counter >= 0; counter--) {
            for (int iCounter = frames[counter].length - 1; iCounter >= 0;
                iCounter--) {
                JInternalFrame       iFrame = frames[counter][iCounter];
                Container            c = iFrame.getContentPane();

                iFrame.getBounds(tempRect);

                mr.moveToFront = true;
                mr.newX = tempRect.x;
                mr.newY = tempRect.y;
                mr.jif = iFrame;
                try {
                    SwingUtilities.invokeLater(mr);
                    rest();
                }
                catch (Exception ex) {
                    System.out.println("--> " + ex);
                }

                iFrame.repaint();
                rest();
                for (int cCounter = c.getComponentCount() - 1;
                    cCounter >= 0; cCounter--) {
                    JComponent     comp = (JComponent)c.getComponent(cCounter);

                    comp.getBounds(tempRect);
                    comp.repaint();
                    rest();
                }
            }
        }
    }

    public static void main(String args[]) {
        int                 whatTest = 0;
        int                 numInternalFrames = 10;
        int                 numButtons = 10;
        int                 numLevels = 1;

        for (int counter = args.length - 1; counter >= 0; counter--) {
            if (args[counter].equals("-repaint")) {
                whatTest |= 2;
            }
            else if (args[counter].equals("-move")) {
                whatTest |= 1;
            }
            else if (args[counter].equals("-numButtons")) {
                try {
                    numButtons = Integer.parseInt(args[counter + 1]);
                }
                catch (NumberFormatException nfe) {}
            }
            else if (args[counter].equals("-numInternalFrames")) {
                try {
                    numInternalFrames =Integer.parseInt(args[counter + 1]);
                }
                catch (NumberFormatException nfe) {}
            }
            else if (args[counter].equals("-numLevels")) {
                try {
                    numLevels = Integer.parseInt(args[counter + 1]);
                }
                catch (NumberFormatException nfe) {}
            }
            else if (args[counter].equals("-laf")) {
                try {
                    UIManager.setLookAndFeel(args[counter + 1]);
                }
                catch (Exception lafEX) {
                    System.out.println("Couldn't laf: " + lafEX);
                }
            }
        }
        if (whatTest == 0) {
            whatTest = 3;
        }
        final InternalFrameTest test =
            new InternalFrameTest(numLevels, numInternalFrames, numButtons, whatTest);

        runStandAloneTest(test);
        System.exit(1);
    }

    static class MoveRunnable implements Runnable {
        int             newX;
        int             newY;
        JInternalFrame  jif;
        boolean         moveToFront;

        public void run() {
            if (moveToFront) {
                moveToFront = false;
                jif.moveToFront();
            }
            jif.setLocation(newX, newY);
        }
    }
}
