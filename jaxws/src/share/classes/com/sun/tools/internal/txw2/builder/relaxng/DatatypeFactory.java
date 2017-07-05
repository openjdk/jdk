/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2.builder.relaxng;

import com.sun.tools.internal.txw2.model.Data;
import com.sun.codemodel.JType;
import com.sun.codemodel.JCodeModel;

import javax.xml.namespace.QName;

/**
 * Builds {@link Data} from a XML Schema datatype.
 * @author Kohsuke Kawaguchi
 */
public class DatatypeFactory {
    private final JCodeModel codeModel;

    public DatatypeFactory(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }

    /**
     * Decides the Java datatype from XML datatype.
     *
     * @return null
     *      if none is found.
     */
    public JType getType(String datatypeLibrary, String type) {
        if(datatypeLibrary.equals("http://www.w3.org/2001/XMLSchema-datatypes")
        || datatypeLibrary.equals("http://www.w3.org/2001/XMLSchema")) {
            type = type.intern();

            if(type=="boolean")
                return codeModel.BOOLEAN;
            if(type=="int" || type=="nonNegativeInteger" || type=="positiveInteger")
                return codeModel.INT;
            if(type=="QName")
                return codeModel.ref(QName.class);
            if(type=="float")
                return codeModel.FLOAT;
            if(type=="double")
                return codeModel.DOUBLE;
            if(type=="anySimpleType" || type=="anyType")
                return codeModel.ref(String.class);
        }

        return null;
    }
}
