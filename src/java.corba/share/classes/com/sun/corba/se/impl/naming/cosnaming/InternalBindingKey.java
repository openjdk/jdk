/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

import org.omg.CosNaming.NameComponent;

/**
 * Class InternalBindingKey implements the necessary wrapper code
 * around the org.omg.CosNaming::NameComponent class to implement the proper
 * equals() method and the hashCode() method for use in a hash table.
 * It computes the hashCode once and stores it, and also precomputes
 * the lengths of the id and kind strings for faster comparison.
 */
public class InternalBindingKey
{
    // A key contains a name
    public NameComponent name;
    private int idLen;
    private int kindLen;
    private int hashVal;

    // Default Constructor
    public InternalBindingKey() {}

    // Normal constructor
    public InternalBindingKey(NameComponent n)
    {
        idLen = 0;
        kindLen = 0;
        setup(n);
    }

    // Setup the object
    protected void setup(NameComponent n) {
        this.name = n;
        // Precompute lengths and values since they will not change
        if( this.name.id != null ) {
            idLen = this.name.id.length();
        }
        if( this.name.kind != null ) {
            kindLen = this.name.kind.length();
        }
        hashVal = 0;
        if (idLen > 0)
            hashVal += this.name.id.hashCode();
        if (kindLen > 0)
            hashVal += this.name.kind.hashCode();
    }

    // Compare the keys by comparing name's id and kind
    public boolean equals(java.lang.Object o) {
        if (o == null)
            return false;
        if (o instanceof InternalBindingKey) {
            InternalBindingKey that = (InternalBindingKey)o;
            // Both lengths must match
            if (this.idLen != that.idLen || this.kindLen != that.kindLen) {
                return false;
            }
            // If id is set is must be equal
            if (this.idLen > 0 && this.name.id.equals(that.name.id) == false) {
                return false;
            }
            // If kind is set it must be equal
            if (this.kindLen > 0 && this.name.kind.equals(that.name.kind) == false) {
                return false;
            }
            // Must be the same
            return true;
        } else {
            return false;
        }
    }
    // Return precomputed value
    public int hashCode() {
        return this.hashVal;
    }
}
