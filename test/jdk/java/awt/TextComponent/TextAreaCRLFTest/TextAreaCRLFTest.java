/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4701398 4652358 4659958 4697796 4666876
  @summary REGRESSION: TextArea.append does not work consistently with \r.
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;

public class TextAreaCRLFTest {
    private static final char[] DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static Dialog aDialog;
    public static TextArea area;
    public static boolean passed = true;
    public static boolean res;
    public static String atext = "";

    public static void main(String[] args) throws Exception {
        String atextCRLF = "row1\r\nrow2\r\nrow3";

        try {
            EventQueue.invokeAndWait(() -> {
                aDialog = new Dialog(new Frame());
                aDialog.setTitle("ADialog");
                aDialog.setBackground(Color.lightGray);
                aDialog.setLayout(new BorderLayout());
                Panel mainPanel = new Panel();
                mainPanel.setLayout(new BorderLayout(6, 6));
                area = new TextArea(atextCRLF, 25, 68,
                    TextArea.SCROLLBARS_VERTICAL_ONLY);
                area.setFont(new Font("Monospaced", Font.PLAIN, 11));
                mainPanel.add(area, "Center");
                aDialog.add(mainPanel, "Center");
                aDialog.pack();
                System.out.println("before: "+hexEncode(atextCRLF));
                System.out.println(" after: "+hexEncode(area.getText()));
                res = area.getText().equals(atextCRLF);
                System.out.println("01: " + res + "\n");
                passed = passed && res;
                area.setText(atextCRLF);
                System.out.println("before: "+hexEncode(atextCRLF));
                System.out.println(" after: "+hexEncode(area.getText()));
                res = area.getText().equals(atextCRLF);
                System.out.println("02: " + res + "\n");
                passed = passed && res;

                area.setText("");
                atext = "row1";
                area.append(atext+"\r");
                area.append(atext+"\r");
                System.out.println("before: "
                    +hexEncode(atext+"\r" + atext+"\r"));
                System.out.println(" after: "+hexEncode(area.getText()));
                res = area.getText().equals(atext + atext);
                System.out.println("03: " + res + "\n");
                passed = passed && res;

                area.setText("");
                String atext1 = "fine.";
                String atext2 = "messed up.";
                atext = atext1 +"\r\n"+ atext2;
                for (int i = 0; i < atext.length(); i++) {
                    area.append(atext.substring(i, i+1));
                }
                System.out.println("before: "
                    +hexEncode(atext1 +"\r\n"+ atext2));
                System.out.println(" after: "+hexEncode(area.getText()));
                String s = area.getText();
                String t = s.substring(s.length()-atext2.length());
                res = t.equals(atext2);
                System.out.println("04: " + res);
                passed = passed && res;

                area.setText("");
                atext = "\r";
                area.append(atext);
                System.out.println("before: "+hexEncode(atext));
                System.out.println(" after: "+hexEncode(area.getText()));
                res = area.getText().equals("");
                System.out.println("05: " + res + "\n");
                passed = passed && res;

                if (System.getProperty("os.name").toUpperCase().
                    startsWith("WIN")) {
                    if (!passed) {
                        throw new RuntimeException("TextAreaCRLFTest FAILED.");
                    } else {
                        System.out.println("TextAreaCRLFTest PASSED");
                    }
                } else {
                    System.out.println("This is a Windows oriented testcase.");
                }
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (aDialog != null) {
                    aDialog.dispose();
                }
            });
        }
    }

    private static String hexEncode(String str) {
        return hexEncode(str.getBytes());
    }

    private static String hexEncode(byte[] bytes) {
        StringBuffer buffer = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            buffer.append(DIGITS[(b & 0xF0) >> 4]);
            buffer.append(DIGITS[b & 0x0F]);
        }
        return buffer.toString();
    }
}
