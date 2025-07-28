/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4075484
 * @summary Tests that events of different types are generated for the
 *          corresponding scroll actions.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPaneEventType
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.ScrollPane;
import java.awt.event.AdjustmentListener;

public class ScrollPaneEventType {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. This test verifies that when user performs some scrolling operation on
                   ScrollPane the correct AdjustmentEvent is being generated.
                2. To test this, press on:
                   - scrollbar's arrows and verify that UNIT event is generated,
                   - scrollbar's grey area(non-thumb) and verify that BLOCK event is
                    generated,
                   - drag scrollbar's thumb and verify that TRACK event is generated
                   If you see correct events for both scroll bars then test is PASSED.
                   Otherwise it is FAILED.
                   """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollPaneEventType::initialize)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame frame = new Frame("ScrollPane event type test");
        frame.setLayout(new BorderLayout());
        ScrollPane pane = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        pane.add(new Button("press") {
            public Dimension getPreferredSize() {
                return new Dimension(1000, 1000);
            }
        });

        AdjustmentListener listener = e -> PassFailJFrame.log(e.toString());
        pane.getHAdjustable().addAdjustmentListener(listener);
        pane.getVAdjustable().addAdjustmentListener(listener);
        frame.add(pane);
        frame.setSize(200, 200);
        return frame;
    }
}
