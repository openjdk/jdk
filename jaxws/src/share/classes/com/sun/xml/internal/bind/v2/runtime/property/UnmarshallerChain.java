/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.bind.v2.runtime.property;

import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Scope;


/**
 * Pass around a 'ticket dispenser' when creating new
 * unmarshallers. This controls the index of the slot
 * allocated to the chain of handlers.

 *
 * <p>
 * A ticket dispenser also maintains the offset for handlers
 * to access state slots. A handler records this value when it's created.
 *
 *
 */
public final class UnmarshallerChain {
    /**
     * This offset allows child unmarshallers to have its own {@link Scope} without colliding with siblings.
     */
    private int offset = 0;

    public final JAXBContextImpl context;

    public UnmarshallerChain(JAXBContextImpl context) {
        this.context = context;
    }

    /**
     * Allocates a new {@link Scope} offset.
     */
    public int allocateOffset() {
        return offset++;
    }

    /**
     * Gets the number of total scope offset allocated.
     */
    public int getScopeSize() {
        return offset;
    }
}
