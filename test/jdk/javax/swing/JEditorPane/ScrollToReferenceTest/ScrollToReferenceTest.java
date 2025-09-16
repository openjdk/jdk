/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4194428
 * @summary Checks that scrolling an href to visible scrolls it to the top of the page.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollToReferenceTest
 */

import java.awt.Container;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URL;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

public class ScrollToReferenceTest {

    static final String INSTRUCTIONS = """
        Wait for the html document to finish loading, click on the anchor
        with text 'CLICK ME'. If 'should be at top of editor pane' is
        scrolled to the top of the visible region of the text, click PASS,
        otherwise click FAIL.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(ScrollToReferenceTest::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("ScrollToReferenceTest");
        JEditorPane pane = new JEditorPane();

        try {
            pane.setPage(ScrollToReferenceTest.class.getResource("test.html"));
        } catch (IOException ioe) {
            PassFailJFrame.forceFail("Couldn't find html file");
        }


        pane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED &&
                    e.getURL() != null) {
                    try {
                        pane.setPage(e.getURL());
                    } catch (IOException ioe) {
                        pane.setText("error finding url, click fail!");
                    }
                }
            }
        });
        pane.setEditable(false);
        JScrollPane sp = new JScrollPane(pane);
        sp.setPreferredSize(new Dimension(400, 400));
        frame.add(sp);
        frame.setSize(400, 400);
        return frame;
    }
}
