/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * @test
 * @key headful
 * @bug 8359266
 * @summary Tests for a race condition when setting a full-screen window
 */
public final class FullScreenWindowRace {

    public static void main(String[] args) throws InterruptedException {
        Window window = new Window(null);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                               .getDefaultScreenDevice();

        Thread thread = new Thread(() -> {
            while (gd.getFullScreenWindow() == null) {
                // Busy wait - can be optimized away without volatile
            }
        });

        thread.setDaemon(true);
        thread.start();

        // Give thread some time to start and begin the loop
        Thread.sleep(2000);

        gd.setFullScreenWindow(window);

        thread.join(15000);

        boolean alive = thread.isAlive();

        gd.setFullScreenWindow(null);
        window.dispose();
        if (alive) {
            throw new RuntimeException("Full screen window is NOT detected!");
        }
    }
}
