/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.util.List;

import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.awt.Toolkit.getDefaultToolkit;

/**
 * A utility class which provides standard window layouts for multi-window
 * manual tests using the {@link PassFailJFrame} framework.
 * The layout methods {@code right-} and {@code bottom-} implement the
 * {@link PassFailJFrame.PositionWindows PositionWindows} interface and
 * can be used directly or via builder methods.
 * <p>
 * There are several helper methods, such as
 * {@link #getScreenCenter() getScreenCenter}, which could help you
 * implement customized windows layouts.
 */
public final class WindowLayouts {

    /** Private constructor to prevent instantiating the utility class. */
    private WindowLayouts() {
    }

    /** A gap between windows. (Local copy makes expressions shorter.) */
    private static final int WINDOW_GAP = PassFailJFrame.WINDOW_GAP;

    /**
     * Lays out the window list in one row to the right of
     * the instruction frame. The top of the windows is aligned to
     * that of the instruction frame.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void rightOneRow(final List<Window> windows,
                                   final PassFailJFrame.InstructionUI instructionUI) {
        layoutRow(instructionUI.getLocation().x
                  + instructionUI.getSize().width
                  + WINDOW_GAP,
                  instructionUI.getLocation().y,
                  windows);
    }

    /**
     * Lays out the window list in one column to the right of
     * the instruction frame. The top of the first window is aligned to
     * that of the instruction frame.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void rightOneColumn(final List<Window> windows,
                                      final PassFailJFrame.InstructionUI instructionUI) {
        layoutColumn(instructionUI.getLocation().x
                     + instructionUI.getSize().width
                     + WINDOW_GAP,
                     instructionUI.getLocation().y,
                     windows);
    }

    /**
     * Lays out the window list in one column to the right of
     * the instruction frame centering the stack of the windows.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void rightOneColumnCentered(final List<Window> windows,
                                              final PassFailJFrame.InstructionUI instructionUI) {
        layoutColumn(instructionUI.getLocation().x
                     + instructionUI.getSize().width
                     + WINDOW_GAP,
                     getScreenCenter().y
                     - getWindowListHeight(windows) / 2,
                     windows);
    }


    /**
     * Lays out the window list in one row to the bottom of
     * the instruction frame. The left of the first window is aligned to
     * that of the instruction frame.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void bottomOneRow(final List<Window> windows,
                                    final PassFailJFrame.InstructionUI instructionUI) {
        layoutRow(instructionUI.getLocation().x,
                  instructionUI.getLocation().y
                  + instructionUI.getSize().height
                  + WINDOW_GAP,
                  windows);
    }

    /**
     * Lays out the window list in one row to the bottom of
     * the instruction frame centering the row of the windows.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void bottomOneRowCentered(final List<Window> windows,
                                            final PassFailJFrame.InstructionUI instructionUI) {
        layoutRow(getScreenCenter().x
                  - getWindowListWidth(windows) / 2,
                  instructionUI.getLocation().y
                  + instructionUI.getSize().height
                  + WINDOW_GAP,
                  windows);
    }

    /**
     * Lays out the window list in one column to the bottom of
     * the instruction frame. The left of the first window is aligned to
     * that of the instruction frame.
     *
     * @param windows the list of windows to lay out
     * @param instructionUI information about the instruction frame
     */
    public static void bottomOneColumn(final List<Window> windows,
                                       final PassFailJFrame.InstructionUI instructionUI) {
        layoutColumn(instructionUI.getLocation().x,
                     instructionUI.getLocation().y
                     + instructionUI.getSize().height
                     + WINDOW_GAP,
                     windows);
    }


    /**
     * Lays out the window list in one row starting at
     * ({@code x0}, {@code y}).
     *
     * @param x0 the starting <var>x</var> coordinate of the windows
     * @param y the <var>y</var> coordinate of the windows
     * @param windows the list of windows to lay out
     */
    public static void layoutRow(final int x0,
                                 final int y,
                                 final List<Window> windows) {
        int x = x0;
        for (Window w : windows) {
            w.setLocation(x, y);
            x += w.getWidth() + WINDOW_GAP;
        }
    }

    /**
     * Lays out the window list in one column starting at
     * ({@code x}, {@code y0}).
     *
     * @param x the <var>x</var> coordinate of the windows
     * @param y0 the starting <var>y</var> coordinate of the windows
     * @param windows the list of windows to lay out
     */
    public static void layoutColumn(final int x,
                                    final int y0,
                                    final List<Window> windows) {
        int y = y0;
        for (Window w : windows) {
            w.setLocation(x, y);
            y += w.getHeight() + WINDOW_GAP;
        }
    }


    /**
     * {@return the center point of the main screen}
     */
    public static Point getScreenCenter() {
        GraphicsConfiguration gc = getLocalGraphicsEnvironment()
                                   .getDefaultScreenDevice()
                                   .getDefaultConfiguration();
        Dimension size = gc.getBounds()
                           .getSize();
        Insets insets = getDefaultToolkit()
                        .getScreenInsets(gc);

        return new Point((size.width - insets.left - insets.right) / 2,
                         (size.height - insets.top - insets.bottom) / 2);
    }

    /**
     * {@return width of the windows in the list, taking into account
     * the gap between windows}
     *
     * @param windows the list of windows to get the width of
     */
    public static int getWindowListWidth(final List<Window> windows) {
        return windows.stream()
                      .mapToInt(Component::getWidth)
                      .sum()
               + WINDOW_GAP * (windows.size() - 1);
    }

    /**
     * {@return height of the windows in the list, taking into account
     * the gap between windows}
     *
     * @param windows the list of windows to get the height of
     */
    public static int getWindowListHeight(final List<Window> windows) {
        return windows.stream()
                      .mapToInt(Component::getHeight)
                      .sum()
               + WINDOW_GAP * (windows.size() - 1);
    }
}
