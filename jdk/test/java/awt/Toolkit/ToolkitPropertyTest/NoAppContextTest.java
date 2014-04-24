/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8032960
 * @summary checks that desktop properties work if Toolkit thread has no AppContext
 * @author Petr Pchelko
 */

import sun.awt.OSInfo;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class NoAppContextTest {

    private static final ThreadGroup stubGroup = new ThreadGroup("stub");
    private static final ThreadGroup awtGroup = new ThreadGroup("AWT");
    private static final AtomicBoolean propertyChangeFired = new AtomicBoolean(false);
    private static Frame frame;

    private static final Object LOCK = new Object();

    public static void main(String[] args) throws Exception {

        if (OSInfo.getOSType() != OSInfo.OSType.WINDOWS) {
            // The test is for Windows platform only
            return;
        }

        createStubContext();

        Thread awtThread = new Thread(awtGroup, () -> {
            SunToolkit.createNewAppContext();
            SwingUtilities.invokeLater(() -> {
                synchronized (LOCK) {
                    frame = new Frame();
                    frame.setBounds(100, 100, 100, 100);
                    frame.setVisible(true);
                    Toolkit.getDefaultToolkit().addPropertyChangeListener("win.propNames", ev -> {
                        propertyChangeFired.set(true);
                    });
                }
            });
        });
        awtThread.start();
        awtThread.join();
        sync();

        final GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();

        AtomicReference<DisplayMode> originalRef = new AtomicReference<>();
        try {
            AtomicBoolean isSupported = new AtomicBoolean(true);
            invokeInAWT(() -> {
                if (device.isFullScreenSupported()) {
                    device.setFullScreenWindow(frame);
                } else {
                    isSupported.set(false);
                }
            });
            if (!isSupported.get()) {
                return;
            }
            invokeInAWT(() -> {
                if (device.isDisplayChangeSupported()) {
                    DisplayMode original = device.getDisplayMode();
                    originalRef.set(original);
                    try {
                        DisplayMode[] modes = device.getDisplayModes();
                        for (DisplayMode mode : modes) {
                            if (!mode.equals(original)) {
                                device.setDisplayMode(mode);
                                break;
                            }
                        }
                    } finally {
                        device.setDisplayMode(original);
                    }
                } else {
                    isSupported.set(false);
                }
            });
            if (!isSupported.get()) {
                return;
            }
        } finally {
            invokeInAWT(() -> {
                device.setDisplayMode(originalRef.get());
                frame.dispose();
            });
        }

        if (!propertyChangeFired.get()) {
            throw new RuntimeException("Failed: PropertyChange did not fire");
        }
    }

    private static void invokeInAWT(Runnable r) throws InterruptedException {
        Thread awtThread = new Thread(awtGroup, () -> {
            SwingUtilities.invokeLater(() -> {
                synchronized (LOCK) {
                    r.run();
                }
            });
        });
        awtThread.start();
        awtThread.join();
        sync();
    }

    private static void createStubContext() throws InterruptedException {
        Thread stub = new Thread(stubGroup, SunToolkit::createNewAppContext);
        stub.start();
        stub.join();
    }

    /**
     * Runs realSync on a thread with an AppContext and waits for it to finish
     */
    private static void sync() throws InterruptedException {
        final AtomicReference<InterruptedException> exc = new AtomicReference<>(null);

        Thread syncThread = new Thread(awtGroup, () -> {
            try {
                ((SunToolkit) Toolkit.getDefaultToolkit()).realSync();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                exc.set(e);
            }
        });
        syncThread.start();
        syncThread.join();
        if (exc.get() != null) {
            throw exc.get();
        }
    }
}

