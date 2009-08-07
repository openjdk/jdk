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

package com.sun.xml.internal.bind.v2.schemagen;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.LocalAttribute;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.LocalElement;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Schema;
import com.sun.xml.internal.txw2.TypedXmlWriter;

/**
 * Represents the form default value.
 *
 * @author Kohsuke Kawaguchi
 */
enum Form {
    QUALIFIED(XmlNsForm.QUALIFIED,true) {
        void declare(String attName,Schema schema) {
            schema._attribute(attName,"qualified");
        }
    },
    UNQUALIFIED(XmlNsForm.UNQUALIFIED,false) {
        void declare(String attName,Schema schema) {
            // pointless, but required by the spec.
            // people need to understand that @attributeFormDefault is a syntax sugar
            schema._attribute(attName,"unqualified");
        }
    },
    UNSET(XmlNsForm.UNSET,false) {
        void declare(String attName,Schema schema) {
        }
    };

    /**
     * The same constant defined in the spec.
     */
    private final XmlNsForm xnf;

    /**
     * What's the effective value? UNSET means unqualified per XSD spec.)
     */
    public final boolean isEffectivelyQualified;

    Form(XmlNsForm xnf, boolean effectivelyQualified) {
        this.xnf = xnf;
        this.isEffectivelyQualified = effectivelyQualified;
    }

    /**
     * Writes the attribute on the generated &lt;schema> element.
     */
    abstract void declare(String attName, Schema schema);

    /**
     * Given the effective 'form' value, write (or suppress) the @form attribute
     * on the generated XML.
     */
    public void writeForm(LocalElement e, QName tagName) {
        _writeForm(e,tagName);
    }

    public void writeForm(LocalAttribute a, QName tagName) {
        _writeForm(a,tagName);
    }

    private void _writeForm(TypedXmlWriter e, QName tagName) {
        boolean qualified = tagName.getNamespaceURI().length()>0;

        if(qualified && this!=QUALIFIED)
            e._attribute("form","qualified");
        else
        if(!qualified && this==QUALIFIED)
            e._attribute("form","unqualified");
    }

    /**
     * Gets the constant the corresponds to the given {@link XmlNsForm}.
     */
    public static Form get(XmlNsForm xnf) {
        for (Form v : values()) {
            if(v.xnf==xnf)
                return v;
        }
        throw new IllegalArgumentException();
    }

}
