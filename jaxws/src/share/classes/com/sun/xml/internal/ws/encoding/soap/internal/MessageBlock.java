/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.encoding.soap.internal;

import javax.xml.namespace.QName;

/**
 * @author WS Development Team
 */
public class MessageBlock {
    protected QName _name;
    protected Object _value;

    public MessageBlock() {
    }

    public MessageBlock(QName name, Object value) {
        _name = name;
        _value = value;
    }

    public MessageBlock(QName name) {
        _name = name;
    }

    /**
     * @return the value of this block
     */
    public Object getValue() {
        return _value;
    }

    /**
     * @param element
     */
    public void setValue(Object element) {
        _value = element;
    }

    /**
     * @return the <code>QName</code> of this block
     */
    public QName getName() {
        return _name;
    }

    /**
     * @param name
     */
    public void setName(QName name) {
        _name = name;
    }
}
