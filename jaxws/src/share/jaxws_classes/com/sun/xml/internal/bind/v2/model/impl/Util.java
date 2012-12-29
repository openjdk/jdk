/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSchemaTypes;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationSource;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * Common code between {@link PropertyInfoImpl} and {@link ElementInfoImpl}.
 *
 * @author Kohsuke Kawaguchi
 */
final class Util {
    static <T,C,F,M> QName calcSchemaType(
            AnnotationReader<T,C,F,M> reader,
            AnnotationSource primarySource, C enclosingClass, T individualType, Locatable src ) {

        XmlSchemaType xst = primarySource.readAnnotation(XmlSchemaType.class);
        if(xst!=null) {
            return new QName(xst.namespace(),xst.name());
        }

        // check the defaulted annotation
        XmlSchemaTypes xsts = reader.getPackageAnnotation(XmlSchemaTypes.class,enclosingClass,src);
        XmlSchemaType[] values = null;
        if(xsts!=null)
            values = xsts.value();
        else {
            xst = reader.getPackageAnnotation(XmlSchemaType.class,enclosingClass,src);
            if(xst!=null) {
                values = new XmlSchemaType[1];
                values[0] = xst;
            }
        }
        if(values!=null) {
            for( XmlSchemaType item : values ) {
                if(reader.getClassValue(item,"type").equals(individualType)) {
                    return new QName(item.namespace(),item.name());
                }
            }
        }

        return null;
    }

    static MimeType calcExpectedMediaType(AnnotationSource primarySource,
                        ModelBuilder builder ) {
        XmlMimeType xmt = primarySource.readAnnotation(XmlMimeType.class);
        if(xmt==null)
            return null;

        try {
            return new MimeType(xmt.value());
        } catch (MimeTypeParseException e) {
            builder.reportError(new IllegalAnnotationException(
                Messages.ILLEGAL_MIME_TYPE.format(xmt.value(),e.getMessage()),
                xmt
            ));
            return null;
        }
    }
}
