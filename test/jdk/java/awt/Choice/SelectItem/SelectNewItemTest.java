/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @bug 8215921
 * @summary Test that selecting a different item does send an ItemEvent
 * @key headful
 * @run main SelectNewItemTest
*/
public final class SelectNewItemTest
        extends SelectCurrentItemTest {

    private SelectNewItemTest() throws AWTException {
        super();
    }

    @Override
    protected void checkItemStateChanged() throws InterruptedException {
        if (!itemStateChanged.await(500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("ItemEvent is not received");
        }
    }

    @Override
    protected void checkSelectedIndex(final int initialIndex,
                                      final int currentIndex) {
        if (initialIndex == currentIndex) {
            throw new RuntimeException("Selected index in Choice should've changed");
        }
    }

    @Override
    protected Point getClickLocation(final Rectangle choiceRect) {
        // Click a different item the popup, not the first one
        return new Point(choiceRect.x + choiceRect.width / 2,
                         choiceRect.y + choiceRect.height * 3);
    }

    public static void main(String... args) throws Exception {
        new SelectNewItemTest().runTest();
    }

}
