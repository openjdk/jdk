/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * A <code>CompoundControl</code>, such as a graphic equalizer, provides control
 * over two or more related properties, each of which is itself represented as
 * a <code>Control</code>.
 *
 * @author Kara Kytle
 * @since 1.3
 */
public abstract class CompoundControl extends Control {


    // TYPE DEFINES


    // INSTANCE VARIABLES


    /**
     * The set of member controls.
     */
    private Control[] controls;



    // CONSTRUCTORS


    /**
     * Constructs a new compound control object with the given parameters.
     *
     * @param type the type of control represented this compound control object
     * @param memberControls the set of member controls
     */
    protected CompoundControl(Type type, Control[] memberControls) {

        super(type);
        this.controls = memberControls;
    }



    // METHODS


    /**
     * Returns the set of member controls that comprise the compound control.
     * @return the set of member controls.
     */
    public Control[] getMemberControls() {

        Control[] localArray = new Control[controls.length];

        for (int i = 0; i < controls.length; i++) {
            localArray[i] = controls[i];
        }

        return localArray;
    }


    // ABSTRACT METHOD IMPLEMENTATIONS: CONTROL


    /**
     * Provides a string representation of the control
     * @return a string description
     */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < controls.length; i++) {
            if (i != 0) {
                sb.append(", ");
                if ((i + 1) == controls.length) {
                    sb.append("and ");
                }
            }
            sb.append(controls[i].getType());
        }

        return new String(getType() + " Control containing " + sb + " Controls.");
    }


    // INNER CLASSES


    /**
     * An instance of the <code>CompoundControl.Type</code> inner class identifies one kind of
     * compound control.  Static instances are provided for the
     * common types.
     *
     * @author Kara Kytle
     * @since 1.3
     */
    public static class Type extends Control.Type {


        // TYPE DEFINES

        // CONSTRUCTOR


        /**
         * Constructs a new compound control type.
         * @param name  the name of the new compound control type
         */
        protected Type(String name) {
            super(name);
        }
    } // class Type

} // class CompoundControl
