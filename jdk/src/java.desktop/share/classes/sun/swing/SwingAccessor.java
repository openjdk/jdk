/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.swing;

import jdk.internal.misc.Unsafe;

import java.awt.*;
import javax.swing.*;

import javax.swing.text.JTextComponent;

/**
 * The SwingAccessor utility class.
 * The main purpose of this class is to enable accessing
 * private and package-private fields of classes from
 * different classes/packages. See sun.misc.SharedSecretes
 * for another example.
 */
public final class SwingAccessor {
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * We don't need any objects of this class.
     * It's rather a collection of static methods
     * and interfaces.
     */
    private SwingAccessor() {
    }

    /**
     * An accessor for the JTextComponent class.
     * Note that we intentionally introduce the JTextComponentAccessor,
     * and not the JComponentAccessor because the needed methods
     * aren't override methods.
     */
    public interface JTextComponentAccessor {

        /**
         * Calculates a custom drop location for the text component,
         * representing where a drop at the given point should insert data.
         */
        TransferHandler.DropLocation dropLocationForPoint(JTextComponent textComp, Point p);

        /**
         * Called to set or clear the drop location during a DnD operation.
         */
        Object setDropLocation(JTextComponent textComp, TransferHandler.DropLocation location,
                               Object state, boolean forDrop);
    }

    /**
     * An accessor for the JLightweightFrame class.
     */
    public interface JLightweightFrameAccessor {
        /**
         * Notifies the JLightweight frame that it needs to update a cursor
         */
        void updateCursor(JLightweightFrame frame);
    }

    /**
     * An accessor for the RepaintManager class.
     */
    public interface RepaintManagerAccessor {
        void addRepaintListener(RepaintManager rm, SwingUtilities2.RepaintListener l);
        void removeRepaintListener(RepaintManager rm, SwingUtilities2.RepaintListener l);
    }

    /**
     * An accessor for PopupFactory class.
     */
    public interface PopupFactoryAccessor {
        Popup getHeavyWeightPopup(PopupFactory factory, Component owner, Component contents,
                                  int ownerX, int ownerY);
    }

    /*
     * An accessor for the KeyStroke class
     */
    public interface KeyStrokeAccessor {

        KeyStroke create();
    }

    /**
     * The javax.swing.text.JTextComponent class accessor object.
     */
    private static JTextComponentAccessor jtextComponentAccessor;

    /**
     * Set an accessor object for the javax.swing.text.JTextComponent class.
     */
    public static void setJTextComponentAccessor(JTextComponentAccessor jtca) {
         jtextComponentAccessor = jtca;
    }

    /**
     * Retrieve the accessor object for the javax.swing.text.JTextComponent class.
     */
    public static JTextComponentAccessor getJTextComponentAccessor() {
        if (jtextComponentAccessor == null) {
            unsafe.ensureClassInitialized(JTextComponent.class);
        }

        return jtextComponentAccessor;
    }

    /**
     * The JLightweightFrame class accessor object
     */
    private static JLightweightFrameAccessor jLightweightFrameAccessor;

    /**
     * Set an accessor object for the JLightweightFrame class.
     */
    public static void setJLightweightFrameAccessor(JLightweightFrameAccessor accessor) {
        jLightweightFrameAccessor = accessor;
    }

    /**
     * Retrieve the accessor object for the JLightweightFrame class
     */
    public static JLightweightFrameAccessor getJLightweightFrameAccessor() {
        if (jLightweightFrameAccessor == null) {
            unsafe.ensureClassInitialized(JLightweightFrame.class);
        }
        return jLightweightFrameAccessor;
    }

    /**
     * The RepaintManager class accessor object.
     */
    private static RepaintManagerAccessor repaintManagerAccessor;

    /**
     * Set an accessor object for the RepaintManager class.
     */
    public static void setRepaintManagerAccessor(RepaintManagerAccessor accessor) {
        repaintManagerAccessor = accessor;
    }

    /**
     * Retrieve the accessor object for the RepaintManager class.
     */
    public static RepaintManagerAccessor getRepaintManagerAccessor() {
        if (repaintManagerAccessor == null) {
            unsafe.ensureClassInitialized(RepaintManager.class);
        }
        return repaintManagerAccessor;
    }

    /**
     * The PopupFactory class accessor object.
     */
    private static PopupFactoryAccessor popupFactoryAccessor;

    /**
     * Retrieve the accessor object for the PopupFactory class.
     */
    public static PopupFactoryAccessor getPopupFactoryAccessor() {
        if (popupFactoryAccessor == null) {
            unsafe.ensureClassInitialized(PopupFactory.class);
        }
        return popupFactoryAccessor;
    }

    /**
     * Set an Accessor object for the PopupFactory class.
     */
    public static void setPopupFactoryAccessor(PopupFactoryAccessor popupFactoryAccessor) {
        SwingAccessor.popupFactoryAccessor = popupFactoryAccessor;
    }

    /**
     * The KeyStroke class accessor object.
     */
    private static KeyStrokeAccessor keyStrokeAccessor;

    /**
     * Retrieve the accessor object for the KeyStroke class.
     */
    public static KeyStrokeAccessor getKeyStrokeAccessor() {
        if (keyStrokeAccessor == null) {
            unsafe.ensureClassInitialized(KeyStroke.class);
        }
        return keyStrokeAccessor;
    }

    /*
     * Set the accessor object for the KeyStroke class.
     */
    public static void setKeyStrokeAccessor(KeyStrokeAccessor accessor) {
        SwingAccessor.keyStrokeAccessor = accessor;
    }
}
