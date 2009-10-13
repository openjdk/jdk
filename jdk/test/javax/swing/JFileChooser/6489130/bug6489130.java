/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 6489130
 * @summary FileChooserDemo hung by keeping pressing Enter key
 * @author Pavel Porvatov
   @run main bug6489130
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class bug6489130 {
    private final JFileChooser chooser = new JFileChooser();

    private static final CountDownLatch MUX = new CountDownLatch(1);

    private final Timer timer = new Timer(1000, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            switch (state) {
                case 0:
                case 1: {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            chooser.showOpenDialog(null);
                        }
                    });

                    break;
                }

                case 2:
                case 3: {
                    Window[] windows = Frame.getWindows();

                    if (windows.length > 0) {
                        windows[0].dispose();
                    }

                    break;
                }

                case 4: {
                    MUX.countDown();

                    break;
                }
            }

            state++;
        }
    });

    private int state = 0;

    public static void main(String[] args) throws InterruptedException {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new bug6489130().run();
            }
        });

        if (!MUX.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout");
        }
    }

    private void run() {
        timer.start();
    }
}
