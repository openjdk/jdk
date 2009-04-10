/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.beans;

/**
 * An "IndexedPropertyChange" event gets delivered whenever a component that
 * conforms to the JavaBeans<TM> specification (a "bean") changes a bound
 * indexed property. This class is an extension of <code>PropertyChangeEvent</code>
 * but contains the index of the property that has changed.
 * <P>
 * Null values may be provided for the old and the new values if their
 * true values are not known.
 * <P>
 * An event source may send a null object as the name to indicate that an
 * arbitrary set of if its properties have changed.  In this case the
 * old and new values should also be null.
 *
 * @since 1.5
 * @author Mark Davidson
 */
public class IndexedPropertyChangeEvent extends PropertyChangeEvent {
    private static final long serialVersionUID = -320227448495806870L;

    private int index;

    /**
     * Constructs a new <code>IndexedPropertyChangeEvent</code> object.
     *
     * @param source  The bean that fired the event.
     * @param propertyName  The programmatic name of the property that
     *             was changed.
     * @param oldValue      The old value of the property.
     * @param newValue      The new value of the property.
     * @param index index of the property element that was changed.
     */
    public IndexedPropertyChangeEvent(Object source, String propertyName,
                                      Object oldValue, Object newValue,
                                      int index) {
        super (source, propertyName, oldValue, newValue);
        this.index = index;
    }


    /**
     * Gets the index of the property that was changed.
     *
     * @return The index specifying the property element that was
     *         changed.
     */
    public int getIndex() {
        return index;
    }
}
