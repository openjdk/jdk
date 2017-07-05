/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * The mapping of a CORBA <code>enum</code> tagging
 * <code>SET_OVERRIDE</code> and <code>ADD_OVERRIDE</code>, which
 * indicate whether policies should replace the
 * existing policies of an <code>Object</code> or be added to them.
 * <P>
 * The method {@link org.omg.CORBA.Object#_set_policy_override} takes
 * either <code>SetOverrideType.SET_OVERRIDE</code> or
 * <code>SetOverrideType.ADD_OVERRIDE</code> as its second argument.
 * The method <code>_set_policy_override</code>
 * creates a new <code>Object</code> initialized with the
 * <code>Policy</code> objects supplied as the first argument.  If the
 * second argument is <code>ADD_OVERRIDE</code>, the new policies
 * are added to those of the <code>Object</code> instance that is
 * calling the <code>_set_policy_override</code> method.  If
 * <code>SET_OVERRIDE</code> is given instead, the existing policies
 * are replaced with the given ones.
 *
 * @author OMG
 * @since   JDK1.2
 */

public class SetOverrideType implements org.omg.CORBA.portable.IDLEntity {

    /**
     * The <code>int</code> constant for the enum value SET_OVERRIDE.
     */
    public static final int _SET_OVERRIDE = 0;

    /**
     * The <code>int</code> constant for the enum value ADD_OVERRIDE.
     */
    public static final int _ADD_OVERRIDE = 1;

    /**
     * The <code>SetOverrideType</code> constant for the enum value SET_OVERRIDE.
     */
    public static final SetOverrideType SET_OVERRIDE = new SetOverrideType(_SET_OVERRIDE);

    /**
     * The <code>SetOverrideType</code> constant for the enum value ADD_OVERRIDE.
     */
    public static final SetOverrideType ADD_OVERRIDE = new SetOverrideType(_ADD_OVERRIDE);

    /**
     * Retrieves the value of this <code>SetOverrideType</code> instance.
     *
     * @return  the <code>int</code> for this <code>SetOverrideType</code> instance.
     */
    public int value() {
        return _value;
    }

    /**
     * Converts the given <code>int</code> to the corresponding
     * <code>SetOverrideType</code> instance.
     *
     * @param  i the <code>int</code> to convert; must be either
     *         <code>SetOverrideType._SET_OVERRIDE</code> or
     *         <code>SetOverrideType._ADD_OVERRIDE</code>
     * @return  the <code>SetOverrideType</code> instance whose value
     *       matches the given <code>int</code>
     * @exception  BAD_PARAM  if the given <code>int</code> does not
     *       match the value of
     *       any <code>SetOverrideType</code> instance
     */
    public static SetOverrideType from_int(int i)
    {
        switch (i) {
        case _SET_OVERRIDE:
            return SET_OVERRIDE;
        case _ADD_OVERRIDE:
            return ADD_OVERRIDE;
        default:
            throw new org.omg.CORBA.BAD_PARAM();
        }
    }

    /**
     * Constructs a <code>SetOverrideType</code> instance from an
     * <code>int</code>.
     * @param _value must be either <code>SET_OVERRIDE</code> or
     *        <code>ADD_OVERRIDE</code>
     */
    protected SetOverrideType(int _value){
        this._value = _value;
    }

    /**
     * The field containing the value for this <code>SetOverrideType</code>
     * object.
     *
     */
    private int _value;
}
