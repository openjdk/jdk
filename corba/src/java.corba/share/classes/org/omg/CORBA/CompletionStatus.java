/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * An object that indicates whether a method had completed running
 * when a <code>SystemException</code> was thrown.
 * <P>
 * The class <code>CompletionStatus</code>
 * contains three <code>CompletionStatus</code> instances, which are constants
 * representing each
 * possible completion status: <code>COMPLETED_MAYBE</code>,
 * <code>COMPLETED_NO</code>, and <code>COMPLETED_YES</code>.
 * It also contains
 * three <code>int</code> members, each a constant corresponding to one of
 * the <code>CompletionStatus</code> instances.  These <code>int</code>
 * members make it possible to use a <code>switch</code> statement.
 * <P>
 * The class also contains two methods:
 * <UL>
 * <LI><code>public int <b>value</b>()</code> -- which accesses the
 * <code>value</code> field of a <code>CompletionStatus</code> object
 * <LI><code>public static CompletionStatus
 * <b>from_int</b>(int i)</code> --
 * for creating an instance from one of the <code>int</code> members
 * </UL>
 * @see     org.omg.CORBA.SystemException
 * @since   JDK1.2
 */

public final class CompletionStatus implements org.omg.CORBA.portable.IDLEntity
{
/**
 * The constant indicating that a method completed running
 * before a <code>SystemException</code> was thrown.
 */
    public static final int _COMPLETED_YES = 0,

/**
 * The constant indicating that a method had not completed running
 * when a <code>SystemException</code> was thrown.
 */
        _COMPLETED_NO = 1,

/**
 * The constant indicating that it is unknown whether a method had
 * completed running when a <code>SystemException</code> was thrown.
 */
        _COMPLETED_MAYBE = 2;


/**
 * An instance of <code>CompletionStatus</code> initialized with
 * the constant <code>_COMPLETED_YES</code>.
 */
    public static final CompletionStatus COMPLETED_YES   = new CompletionStatus(_COMPLETED_YES);

/**
 * An instance of <code>CompletionStatus</code> initialized with
 * the constant <code>_COMPLETED_NO</code>.
 */
    public static final CompletionStatus COMPLETED_NO    = new CompletionStatus(_COMPLETED_NO);

    /**
 * An instance of <code>CompletionStatus</code> initialized with
 * the constant <code>_COMPLETED_MAYBE</code>.
 */
    public static final CompletionStatus COMPLETED_MAYBE = new CompletionStatus(_COMPLETED_MAYBE);

    /**
 * Retrieves the value of this <code>CompletionStatus</code> object.
 *
 * @return  one of the possible <code>CompletionStatus</code> values:
 *          <code>_COMPLETED_YES</code>, <code>_COMPLETED_NO</code>, or
 *          <code>_COMPLETED_MAYBE</code>
 *
 */
    public int value() { return _value; }

/**
 * Creates a <code>CompletionStatus</code> object from the given <code>int</code>.
 *
 * @param i  one of <code>_COMPLETED_YES</code>, <code>_COMPLETED_NO</code>, or
 *          <code>_COMPLETED_MAYBE</code>
 *
 * @return  one of the possible <code>CompletionStatus</code> objects
 *          with values:
 *          <code>_COMPLETED_YES</code>, <code>_COMPLETED_NO</code>, or
 *          <code>_COMPLETED_MAYBE</code>
 *
 * @exception org.omg.CORBA.BAD_PARAM  if the argument given is not one of the
 *            <code>int</code> constants defined in <code>CompletionStatus</code>
 */
    public static CompletionStatus from_int(int i)  {
        switch (i) {
        case _COMPLETED_YES:
            return COMPLETED_YES;
        case _COMPLETED_NO:
            return COMPLETED_NO;
        case _COMPLETED_MAYBE:
            return COMPLETED_MAYBE;
        default:
            throw new org.omg.CORBA.BAD_PARAM();
        }
    }


/**
 * Creates a <code>CompletionStatus</code> object from the given <code>int</code>.
 *
 * @param _value  one of <code>_COMPLETED_YES</code>, <code>_COMPLETED_NO</code>, or
 *          <code>_COMPLETED_MAYBE</code>
 *
 */
    private CompletionStatus(int _value) {
        this._value = _value;
    }

    private int _value;
}
