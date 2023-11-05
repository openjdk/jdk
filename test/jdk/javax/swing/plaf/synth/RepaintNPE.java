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
 * @bug 6529151
 * @key headful
 * @summary  Verifies no NPE is thrown in SynthLookAndFeel$Handler
 * @run main RepaintNPE
 */
import java.awt.Container;
import java.awt.Component;
import java.awt.Robot;
import java.awt.Point;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.plaf.ComponentUI;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class RepaintNPE
{
    private static JFrame frame;
    private static JButton showDialogButton = null;

    private static class DumbDialog extends JDialog {

        public DumbDialog ( JFrame parent )
        {
            super ( parent, "DumbDialog", true );

            setDefaultCloseOperation ( DISPOSE_ON_CLOSE );

            JEditorPane editorPane = new JEditorPane();
            editorPane.setContentType ( "text/html" );
            editorPane.setEditable ( false );

            JScrollPane scrollPane = new JScrollPane ( editorPane );
            add( scrollPane );
            setSize( 400, 400 );
            setVisible( true );
        }

        public void dispose() {
            super.dispose();
            myDispose( this );
        }

        private void myDispose( Container container ) {
            Component[] components = container.getComponents ();

            for ( Component comp : components ) {
                // Special dispose for JComponent's remove UI
                if (comp instanceof JComponent) {
                    JComponent jComp = (JComponent)comp;

                    try
                    {
                        ComponentUI compUI = jComp.getUI();
                        compUI.uninstallUI ( jComp );
                    } catch ( Throwable t ) {}

                    // Recurse children
                    myDispose( jComp );
                }
            }
        }

    }

    public static void main ( String args [] ) throws Exception
    {
        Robot robot = new Robot();
        UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        SwingUtilities.invokeAndWait ( () -> {
            frame = new JFrame();

            showDialogButton = new JButton( "Show Dialog" );
            showDialogButton.addActionListener((e) -> {
                if ( e.getSource() == showDialogButton ) {
                    new DumbDialog ( frame );
                }
            });
            frame.add ( showDialogButton );
            frame.setSize( 100, 100 );
            frame.setLocationRelativeTo(null);
            frame.setUndecorated(true);
            frame.setVisible( true );
        });
        robot.waitForIdle();
        robot.delay(1000);
        Point loc = frame.getLocationOnScreen();
        robot.mouseMove(loc.x+10, loc.y+10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> frame.dispose());
    }
}
