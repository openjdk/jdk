/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5032471
 * @key headful
 * @summary Verifies JFormattedTextField uses edit formatter on initial focus 
 * @run main JFormattedTextProblem
 */

import java.util.Date;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JFormattedTextField;
import javax.swing.text.DateFormatter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Document;
import javax.swing.SwingUtilities;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class JFormattedTextProblem {

    static JFrame frame;	
    static volatile boolean expected = false;

    public static void main(String[] args) throws Exception {
        try {	    
            Robot robot = new Robot();	    
            robot.setAutoDelay(100);
            JFormattedTextProblem test = new JFormattedTextProblem();
            SwingUtilities.invokeAndWait(() -> {	
                test.testFormatter();
            });
            robot.waitForIdle();
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            if (!expected) {
                throw new RuntimeException("Field not editable format");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {	
                if (frame != null) {
                    frame.dispose();
                }
            });
        }	    
    }

    private void testFormatter() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.add(new JLabel("Nothing: "));
        panel.add(new JTextField("Focus Starts Here."));

        DateFormat displayDate = DateFormat.getDateInstance(DateFormat.MEDIUM);
        DateFormat editDate = DateFormat.getDateInstance(DateFormat.SHORT);

        JFormattedTextField dateField = new JFormattedTextField();
        dateField.setFormatterFactory(new DefaultFormatterFactory(
                                         new DateFormatter(editDate),
                                         new DateFormatter(displayDate),
                                         new DateFormatter(editDate)));

        panel.add(new JLabel("Date: "));
        panel.add(dateField);


        DateFormat displayTime = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        SimpleDateFormat editTime = new SimpleDateFormat("HH:mm");

        dateField.setText(displayDate.format(new Date()));

        frame.getContentPane().add(panel);
    
        DocumentListener documentListener = new DocumentListener() {
            public void changedUpdate(DocumentEvent documentEvent) {
                printIt(documentEvent);
            }
            public void insertUpdate(DocumentEvent documentEvent) {
                printIt(documentEvent);
            }
            public void removeUpdate(DocumentEvent documentEvent) {
                printIt(documentEvent);
            }
            private void printIt(DocumentEvent documentEvent) {
                DocumentEvent.EventType type = documentEvent.getType();
                if (type.equals(DocumentEvent.EventType.INSERT)) {
                    expected = true;
                }
            }
        };
        dateField.getDocument().addDocumentListener(documentListener);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
