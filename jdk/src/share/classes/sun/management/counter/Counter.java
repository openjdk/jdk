/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.management.counter;

/**
 * The base class for a performance counter.
 *
 * @author   Brian Doherty
 */
public interface Counter extends java.io.Serializable {

    /**
     * Returns the name of this performance counter
     */
    public String getName();

    /**
     * Returns the Units for this performance counter
     */
    public Units getUnits();

    /**
     * Returns the Variability for this performance counter
     */
    public Variability getVariability();

    /**
     * Returns true if this performance counter is a vector
     */
    public boolean isVector();

    /**
     * Returns the length of the vector
     */
    public int getVectorLength();

    /**
     * Returns an Object that encapsulates the data value of this counter
     */
    public Object getValue();

    /**
     * Returns <tt>true</tt> if this counter is an internal counter.
     */
    public boolean isInternal();

    /**
     * Return the flags associated with the counter.
     */
    public int getFlags();
}
