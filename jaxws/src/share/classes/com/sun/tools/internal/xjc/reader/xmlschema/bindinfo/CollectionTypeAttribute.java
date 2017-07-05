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
package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

import com.sun.tools.internal.xjc.generator.bean.field.FieldRenderer;
import com.sun.tools.internal.xjc.generator.bean.field.UntypedListFieldRenderer;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRendererFactory;
import com.sun.tools.internal.xjc.model.Model;

/**
 * Bean used by JAXB to bind a collection type attribute to our {@link FieldRenderer}.
 * @author Kohsuke Kawaguchi
 */
final class CollectionTypeAttribute {
    @XmlValue
    String collectionType = null;

    /**
     * Computed from {@link #collectionType} on demand.
     */
    @XmlTransient
    private FieldRenderer fr;

    FieldRenderer get(Model m) {
        if(fr==null)
            fr = calcFr(m);
        return fr;
    }

    private FieldRenderer calcFr(Model m) {
        FieldRendererFactory frf = m.options.getFieldRendererFactory();
        if (collectionType==null)
            return frf.getDefault();

        if (collectionType.equals("indexed"))
            return frf.getArray();

        return frf.getList(m.codeModel.ref(collectionType));
    }
}
