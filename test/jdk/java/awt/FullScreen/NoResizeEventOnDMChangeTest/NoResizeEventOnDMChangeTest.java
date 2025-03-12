/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6646411
 * @summary Tests that full screen window and its children receive resize
            event when display mode changes
 * @library /test/lib
 * @build   jdk.test.lib.Platform jtreg.SkippedException
 * @run main/othervm NoResizeEventOnDMChangeTest
 * @run main/othervm -Dsun.java2d.d3d=false NoResizeEventOnDMChangeTest
 */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import jdk.test.lib.Platform;
import jtreg.SkippedException;

public class NoResizeEventOnDMChangeTest {

    public static void main(String[] args) {
        if (Platform.isOnWayland() && !isFixDelivered()) {
            throw new SkippedException("Test skipped because fix was not" +
                    "delivered in current GnomeShell version");
        }

        final GraphicsDevice gd = GraphicsEnvironment.
            getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!gd.isFullScreenSupported()) {
            System.out.println("Full screen not supported, test passed");
            return;
        }

        DisplayMode dm = gd.getDisplayMode();
        final DisplayMode dms[] = new DisplayMode[2];
        for (DisplayMode dm1 : gd.getDisplayModes()) {
            if (dm1.getWidth()  != dm.getWidth() ||
                dm1.getHeight() != dm.getHeight())
            {
                dms[0] = dm1;
                break;
            }
        }
        if (dms[0] == null) {
            System.out.println("Test Passed: all DMs have same dimensions");
            return;
        }
        dms[1] = dm;

        Frame f = new Frame() {
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.red);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.green);
                g.drawRect(0, 0, getWidth()-1, getHeight()-1);
            }
        };
        f.setUndecorated(true);
        testFSWindow(gd, dms, f);

        Window w = new Window(f) {
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.magenta);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.cyan);
                g.drawRect(0, 0, getWidth()-1, getHeight()-1);
            }
        };
        testFSWindow(gd, dms, w);
        System.out.println("Test Passed.");
    }

    private static void testFSWindow(final GraphicsDevice gd,
                                     final DisplayMode dms[],
                                     final Window fsWin)
    {
        System.out.println("Testing FS window: "+fsWin);
        Component c = new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(Color.blue);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.magenta);
                g.drawRect(0, 0, getWidth()-1, getHeight()-1);
                g.setColor(Color.red);
                g.drawString("FS Window   : " + fsWin, 50, 50);
                DisplayMode dm =
                    getGraphicsConfiguration().getDevice().getDisplayMode();
                g.drawString("Display Mode: " +
                             dm.getWidth() + "x" + dm.getHeight(), 50, 75);
            }
        };
        fsWin.add("Center", c);
        fsWin.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fsWin.dispose();
                if (fsWin.getOwner() != null) {
                    fsWin.getOwner().dispose();
                }
            }
        });

        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    gd.setFullScreenWindow(fsWin);
                }
            });
        } catch (Exception ex) {}

        sleep(1000);

        final ResizeEventChecker r1 = new ResizeEventChecker();
        final ResizeEventChecker r2 = new ResizeEventChecker();

        if (gd.isDisplayChangeSupported()) {
            fsWin.addComponentListener(r1);
            c.addComponentListener(r2);
            for (final DisplayMode dm1 : dms) {
                try {
                    EventQueue.invokeAndWait(new Runnable() {
                        public void run() {
                            System.err.printf("----------- Setting DM %dx%d:\n",
                                              dm1.getWidth(), dm1.getHeight());
                            try {
                                Frame f = fsWin instanceof Frame ? (Frame) fsWin : (Frame) fsWin.getOwner();
                                DisplayMode oldMode = f.getGraphicsConfiguration().getDevice().getDisplayMode();
                                gd.setDisplayMode(dm1);
                                sleep(2000);
                                // Check if setting new display mode actually results in frame being
                                // placed onto display with different resolution.
                                DisplayMode newMode = f.getGraphicsConfiguration().getDevice().getDisplayMode();
                                if (oldMode.getWidth() != newMode.getWidth()
                                        || oldMode.getHeight() != newMode.getHeight()) {
                                    r1.incDmChanges();
                                    r2.incDmChanges();
                                } else {
                                    System.out.println("Skipping this iteration. Details:");
                                    System.out.println("Requested device = " + gd);
                                    System.out.println("Actual device = " + f.getGraphicsConfiguration().getDevice());
                                }
                            } catch (IllegalArgumentException iae) {}
                        }
                    });
                } catch (Exception ex) {}
                for (int i = 0; i < 3; i++) {
                    fsWin.repaint();
                    sleep(1000);
                }
            }
            fsWin.removeComponentListener(r1);
            c.removeComponentListener(r2);
        }

        try {
           EventQueue.invokeAndWait(new Runnable() {
               public void run() {
                   gd.setFullScreenWindow(null);
                    fsWin.dispose();
                    if (fsWin.getOwner() != null) {
                        fsWin.getOwner().dispose();
                    }
                }
            });
        } catch (Exception ex) {}

        System.out.printf("FS Window: resizes=%d, dm changes=%d\n",
                           r1.getResizes(), r1.getDmChanges());
        System.out.printf("Component: resizes=%d, dm changes=%d\n",
                          r2.getResizes(), r2.getDmChanges());
        if (r1.getResizes() < r1.getDmChanges()) {
            throw new RuntimeException("FS Window didn't receive all resizes!");
        }
        if (r2.getResizes() < r2.getDmChanges()) {
            throw new RuntimeException("Component didn't receive all resizes!");
        }
    }

    static void sleep(long ms) {
        long targetTime = System.currentTimeMillis() + ms;
        do {
            try {
                Thread.sleep(targetTime - System.currentTimeMillis());
            } catch (InterruptedException ex) {}
        } while (System.currentTimeMillis() < targetTime);
    }

    static class ResizeEventChecker extends ComponentAdapter {
        int dmChanges;
        int resizes;

        @Override
        public synchronized void componentResized(ComponentEvent e) {
            System.out.println("Received resize event for "+e.getSource());
            resizes++;
        }
        public synchronized int getResizes() {
            return resizes;
        }
        public synchronized void incDmChanges() {
            dmChanges++;
        }
        public synchronized int getDmChanges() {
            return dmChanges;
        }
    }

    private static boolean isFixDelivered() {
        try {
            Process process =
                    new ProcessBuilder("/usr/bin/gnome-shell", "--version")
                            .start();

            try (BufferedReader reader = process.inputReader()) {
                if (process.waitFor(2, SECONDS) &&  process.exitValue() == 0) {
                    String line = reader.readLine();
                    if (line != null) {
                        System.out.println("Gnome shell version: " + line);
                        String[] versionComponents = line
                                .replaceAll("[^\\d.]", "")
                                .split("\\.");

                        if (versionComponents.length >= 1) {
                            return Integer.parseInt(versionComponents[0]) > 42;
                        }
                    }
                }
            }
        } catch (IOException
                 | InterruptedException
                 | IllegalThreadStateException
                 | NumberFormatException ignored) {
        }

        return false;
    }
}
