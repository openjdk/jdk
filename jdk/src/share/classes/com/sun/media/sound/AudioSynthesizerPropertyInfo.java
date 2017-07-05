/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.media.sound;

/**
 * Information about property used in  opening <code>AudioSynthesizer</code>.
 *
 * @author Karl Helgason
 */
public class AudioSynthesizerPropertyInfo {

    /**
     * Constructs a <code>AudioSynthesizerPropertyInfo</code> object with a given
     * name and value. The <code>description</code> and <code>choices</code>
     * are intialized by <code>null</code> values.
     *
     * @param name the name of the property
     * @param value the current value or class used for values.
     *
     */
    public AudioSynthesizerPropertyInfo(String name, Object value) {
        this.name = name;
        this.value = value;
        if (value instanceof Class)
            valueClass = (Class)value;
        else if (value != null)
            valueClass = value.getClass();
    }
    /**
     * The name of the property.
     */
    public String name;
    /**
     * A brief description of the property, which may be null.
     */
    public String description = null;
    /**
     * The <code>value</code> field specifies the current value of
     * the property.
     */
    public Object value = null;
    /**
     * The <code>valueClass</code> field specifies class
     * used in <code>value</code> field.
     */
    public Class valueClass = null;
    /**
     * An array of possible values if the value for the field
     * <code>AudioSynthesizerPropertyInfo.value</code> may be selected
     * from a particular set of values; otherwise null.
     */
    public Object[] choices = null;

}
