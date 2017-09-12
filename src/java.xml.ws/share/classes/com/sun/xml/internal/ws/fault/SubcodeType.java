/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.fault;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

/**
 * <pre>
 *      &lt;env:Subcode>
 *          &lt;env:Value>m:MessageTimeout1&lt;/env:Value>
 *          &lt;env:Subcode>
 *              &lt;env:Value>m:MessageTimeout2&lt;/env:Value>
 *          &lt;/env:Subcode>
 *      &lt;/env:Subcode>
 *  </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SubcodeType", namespace = "http://www.w3.org/2003/05/soap-envelope", propOrder = {
    "Value",
    "Subcode"
        })
class SubcodeType {
    @XmlTransient
    private static final String ns="http://www.w3.org/2003/05/soap-envelope";
    /**
     * mandatory, minOccurs=1
     */
    @XmlElement(namespace = ns)
    private QName Value;

    /**
     * optional, minOcccurs=0
     */
    @XmlElements(@XmlElement(namespace = ns))
    private SubcodeType Subcode;

    public SubcodeType(QName value) {
        Value = value;
    }

    public SubcodeType() {
    }

    QName getValue() {
        return Value;
    }

    SubcodeType getSubcode() {
        return Subcode;
    }

    void setSubcode(SubcodeType subcode) {
        Subcode = subcode;
    }
}
