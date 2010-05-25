/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * {@link Line Lines} often have a set of controls, such as gain and pan, that affect
 * the audio signal passing through the line.  Java Sound's <code>Line</code> objects
 * let you obtain a particular control object by passing its class as the
 * argument to a {@link Line#getControl(Control.Type) getControl} method.
 * <p>
 * Because the various types of controls have different purposes and features,
 * all of their functionality is accessed from the subclasses that define
 * each kind of control.
 *
 * @author Kara Kytle
 *
 * @see Line#getControls
 * @see Line#isControlSupported
 * @since 1.3
 */
public abstract class Control {


    // INSTANCE VARIABLES

    /**
     * The control type.
     */
    private final Type type;



    // CONSTRUCTORS

    /**
     * Constructs a Control with the specified type.
     * @param type the kind of control desired
     */
    protected Control(Type type) {
        this.type = type;
    }


    // METHODS

    /**
     * Obtains the control's type.
     * @return the control's type.
     */
    public Type getType() {
        return type;
    }


    // ABSTRACT METHODS

    /**
     * Obtains a String describing the control type and its current state.
     * @return a String representation of the Control.
     */
    public String toString() {
        return new String(getType() + " Control");
    }


    /**
     * An instance of the <code>Type</code> class represents the type of
     * the control.  Static instances are provided for the
     * common types.
     */
    public static class Type {

        // CONTROL TYPE DEFINES

        // INSTANCE VARIABLES

        /**
         * Type name.
         */
        private String name;


        // CONSTRUCTOR

        /**
         * Constructs a new control type with the name specified.
         * The name should be a descriptive string appropriate for
         * labelling the control in an application, such as "Gain" or "Balance."
         * @param name  the name of the new control type.
         */
        protected Type(String name) {
            this.name = name;
        }


        // METHODS

        /**
         * Finalizes the equals method
         */
        public final boolean equals(Object obj) {
            return super.equals(obj);
        }

        /**
         * Finalizes the hashCode method
         */
        public final int hashCode() {
            return super.hashCode();
        }

        /**
         * Provides the <code>String</code> representation of the control type.  This <code>String</code> is
         * the same name that was passed to the constructor.
         *
         * @return the control type name
         */
        public final String toString() {
            return name;
        }
    } // class Type

} // class Control
