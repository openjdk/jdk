/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
package org.jdesktop.swingx.designer;


/**
 * BlendingMode - Enum of composite blending modes, setup to match photoshop as closely as possible
 *
 * @author Created by Jasper Potts (May 31, 2007)
 */
public enum BlendingMode {
    NORMAL,
    // DISSOLVE, missing
    // -----------------------------
    DARKEN,
    MULTIPLY,
    COLOR_BURN,
    LINEAR_BURN, // (SUBTRACT)
    // -----------------------------
    LIGHTEN,
    SCREEN,
    COLOR_DODGE,
    LINEAR_DODGE, // (ADD)
    // -----------------------------
    OVERLAY,
    SOFT_LIGHT,
    HARD_LIGHT,
    VIVID_LIGHT, // (HEAT) is close
    LINEAR_LIGHT, // (GLOW) is close
    //PIN_LIGHT, missing
    //HARD_MIX, missing
    // -----------------------------
    DIFFERENCE,
    EXCLUSION,
    // -----------------------------
    HUE, // nowhere close
    SATURATION,
    COLOR,
    LUMINOSITY, // close but not exact
    //LIGHTER_COLOR, missing
    //DARKER_COLOR, missing
    ;


    // =================================================================================================================
    // Helper methods for creating Blending Mode Combo Box

    public static final Object[] BLENDING_MODES = new Object[]{
            BlendingMode.NORMAL,
            // DISSOLVE, missing
            "-",
            BlendingMode.DARKEN,
            BlendingMode.MULTIPLY,
            BlendingMode.COLOR_BURN,
            BlendingMode.LINEAR_BURN, // (SUBTRACT)
            "-",
            BlendingMode.LIGHTEN,
            BlendingMode.SCREEN,
            BlendingMode.COLOR_DODGE,
            BlendingMode.LINEAR_DODGE, // (ADD)
            "-",
            BlendingMode.OVERLAY,
            BlendingMode.SOFT_LIGHT,
            BlendingMode.HARD_LIGHT,
            BlendingMode.VIVID_LIGHT, // (HEAT) is close
            BlendingMode.LINEAR_LIGHT, // (GLOW) is close
            //PIN_LIGHT, missing
            //HARD_MIX, missing
            "-",
            BlendingMode.DIFFERENCE,
            BlendingMode.EXCLUSION,
            "-",
            BlendingMode.HUE, // nowhere close
            BlendingMode.SATURATION,
            BlendingMode.COLOR,
            BlendingMode.LUMINOSITY, // close but not exact
    };
}
