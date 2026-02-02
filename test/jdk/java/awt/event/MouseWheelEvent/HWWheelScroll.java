/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4425654
 * @summary Test wheel scrolling of heavyweight components
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HWWheelScroll
 */

import java.awt.Choice;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.List;
import java.awt.TextArea;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class HWWheelScroll {
    public static final int TEXT_TALL = 0;
    public static final int TEXT_WIDE = 1;
    public static final int TEXT_SMALL = 2;
    public static final int TEXT_BIG = 3;
    static String INSTRUCTIONS = """
            Test for mouse wheel scrolling of heavyweight components with built-in
            scrollbars or similar functionality that is controlled by guestures
            such as Apple Magic Mouse or trackpad scrolling guesture.
            Several windows containing either a TextArea, List, Choice, or a
            FileDialog will appear. For each window, use the mouse wheel to
            scroll its content, and then minimize it or move away
            and continue with the next window.
            Do not close any of the opened windows except the FileDialog.
            For the FileDialog, first change to a directory with enough items that a
            scrollbar appears.
            Some of the other windows don't have enough text to warrant scrollbars,
            but should be tested anyway to make sure no crash or hang occurs.
            If all scrollbars scroll correctly, press "Pass", otherwise press "Fail".
            """;

    public static ArrayList<Window> initUI() {
        ArrayList<Window> retValue = new ArrayList<>();
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_BOTH, TEXT_BIG));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_BOTH, TEXT_TALL));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_BOTH, TEXT_SMALL));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_BOTH, TEXT_WIDE));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_VERTICAL_ONLY, TEXT_TALL));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_VERTICAL_ONLY, TEXT_SMALL));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_HORIZONTAL_ONLY, TEXT_SMALL));
        retValue.add(makeTextFrame(TextArea.SCROLLBARS_HORIZONTAL_ONLY, TEXT_WIDE));
        retValue.add(makeListFrame(TEXT_TALL));
        retValue.add(makeListFrame(TEXT_WIDE));
        retValue.add(makeListFrame(TEXT_SMALL));
        Frame f = new Frame("File Dialog Owner");
        f.setSize(150, 150);
        f.setLocationRelativeTo(null);
        FileDialog fd = new FileDialog(f, "FileDialog");
        fd.setDirectory(".");
        retValue.add(fd);
        retValue.add(f);
        Frame choiceFrame = new Frame("Choice");
        Choice c = new Choice();
        for (int i = 0; i < 50; i++) {
            c.add(i + " choice item");
        }
        choiceFrame.add(c);
        choiceFrame.setSize(150, 150);
        choiceFrame.setLocationRelativeTo(null);
        retValue.add(choiceFrame);
        return retValue;
    }

    public static Frame makeTextFrame(int policy, int textShape) {
        Frame f = new Frame("TextArea");
        f.add(makeTextArea(policy, textShape));
        f.setSize(150, 150);
        f.setLocationRelativeTo(null);
        return f;
    }

    public static Frame makeListFrame(int textShape) {
        Frame f = new Frame("List");
        f.add(makeList(textShape));
        f.setSize(150, 150);
        f.setLocationRelativeTo(null);
        return f;
    }

    public static TextArea makeTextArea(int policy, int textShape) {
        TextArea ta = new TextArea("", 0, 0, policy);
        if (textShape == TEXT_TALL) {
            for (int i = 0; i < 50 ; i++) {
                ta.append(i + "\n");
            }
        } else if (textShape == TEXT_WIDE) {
            for (int i = 0; i < 2; i++) {
                ta.append(i + "very, very, very, very, very, very, very, long line of text number\n");
            }
        } else if (textShape == TEXT_SMALL) {
            ta.append("text");
        } else if (textShape == TEXT_BIG) {
            for (int i = 0; i < 50 ; i++) {
                ta.append(i + "very, very, very, very, very, very, very, long line of text number\n");
            }
        }
        return ta;
    }

    public static List makeList(int textShape) {
        java.awt.List l = new java.awt.List();
        if (textShape == TEXT_TALL) {
            for (int i = 0; i < 50 ; i++) {
                l.add(" " + i + " ");
            }
        }  else if (textShape == TEXT_WIDE) {
            for (int i = 0; i < 2 ; i++) {
                l.add(i + "very, very, very, very, very, very, very, long line of text number");
            }
        }  else if (textShape == TEXT_SMALL) {
            l.add("text");
        } else if (textShape == TEXT_BIG) {
            for (int i = 0; i < 50 ; i++) {
                l.add(i + "very, very, very, very, very, very, very, long line of text number");
            }
        }
        return l;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(HWWheelScroll::initUI)
                .build()
                .awaitAndCheck();
    }
}
