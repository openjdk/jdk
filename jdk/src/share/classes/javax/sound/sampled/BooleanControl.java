/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled;

/**
 * A <code>BooleanControl</code> provides the ability to switch between
 * two possible settings that affect a line's audio.  The settings are boolean
 * values (<code>true</code> and <code>false</code>).  A graphical user interface
 * might represent the control by a two-state button, an on/off switch, two
 * mutually exclusive buttons, or a checkbox (among other possibilities).
 * For example, depressing a button might activate a
 * <code>{@link BooleanControl.Type#MUTE MUTE}</code> control to silence
 * the line's audio.
 * <p>
 * As with other <code>{@link Control}</code> subclasses, a method is
 * provided that returns string labels for the values, suitable for
 * display in the user interface.
 *
 * @author Kara Kytle
 * @since 1.3
 */
public abstract class BooleanControl extends Control {


    // INSTANCE VARIABLES

    /**
     * The <code>true</code> state label, such as "true" or "on."
     */
    private final String trueStateLabel;

    /**
     * The <code>false</code> state label, such as "false" or "off."
     */
    private final String falseStateLabel;

    /**
     * The current value.
     */
    private boolean value;


    // CONSTRUCTORS


    /**
     * Constructs a new boolean control object with the given parameters.
     *
     * @param type the type of control represented this float control object
     * @param initialValue the initial control value
     * @param trueStateLabel the label for the state represented by <code>true</code>,
     * such as "true" or "on."
     * @param falseStateLabel the label for the state represented by <code>false</code>,
     * such as "false" or "off."
     */
    protected BooleanControl(Type type, boolean initialValue, String trueStateLabel, String falseStateLabel) {

        super(type);
        this.value = initialValue;
        this.trueStateLabel = trueStateLabel;
        this.falseStateLabel = falseStateLabel;
    }


    /**
     * Constructs a new boolean control object with the given parameters.
     * The labels for the <code>true</code> and <code>false</code> states
     * default to "true" and "false."
     *
     * @param type the type of control represented by this float control object
     * @param initialValue the initial control value
     */
    protected BooleanControl(Type type, boolean initialValue) {
        this(type, initialValue, "true", "false");
    }


    // METHODS


    /**
     * Sets the current value for the control.  The default
     * implementation simply sets the value as indicated.
     * Some controls require that their line be open before they can be affected
     * by setting a value.
     * @param value desired new value.
     */
    public void setValue(boolean value) {
        this.value = value;
    }



    /**
     * Obtains this control's current value.
     * @return current value.
     */
    public boolean getValue() {
        return value;
    }


    /**
     * Obtains the label for the specified state.
     * @param state the state whose label will be returned
     * @return the label for the specified state, such as "true" or "on"
     * for <code>true</code>, or "false" or "off" for <code>false</code>.
     */
    public String getStateLabel(boolean state) {
        return ((state == true) ? trueStateLabel : falseStateLabel);
    }



    // ABSTRACT METHOD IMPLEMENTATIONS: CONTROL


    /**
     * Provides a string representation of the control
     * @return a string description
     */
    public String toString() {
        return new String(super.toString() + " with current value: " + getStateLabel(getValue()));
    }


    // INNER CLASSES


    /**
     * An instance of the <code>BooleanControl.Type</code> class identifies one kind of
     * boolean control.  Static instances are provided for the
     * common types.
     *
     * @author Kara Kytle
     * @since 1.3
     */
    public static class Type extends Control.Type {


        // TYPE DEFINES


        /**
         * Represents a control for the mute status of a line.
         * Note that mute status does not affect gain.
         */
        public static final Type MUTE                           = new Type("Mute");

        /**
         * Represents a control for whether reverberation is applied
         * to a line.  Note that the status of this control not affect
         * the reverberation settings for a line, but does affect whether
         * these settings are used.
         */
        public static final Type APPLY_REVERB           = new Type("Apply Reverb");


        // CONSTRUCTOR


        /**
         * Constructs a new boolean control type.
         * @param name  the name of the new boolean control type
         */
        protected Type(String name) {
            super(name);
        }
    } // class Type
}
