/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.bind.JAXBElement;

import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;
import com.sun.xml.internal.bind.api.AccessorException;

import org.xml.sax.SAXException;

/**
 * Reads a text value and set to the current target.
 *
 * @see LeafPropertyLoader
 * @author Kohsuke Kawaguchi
 */
public class ValuePropertyLoader extends Loader {

    private final TransducedAccessor xacc;

    public ValuePropertyLoader(TransducedAccessor xacc) {
        super(true);
        this.xacc = xacc;
    }

    public void text(UnmarshallingContext.State state, CharSequence text) throws SAXException {
        try {
            xacc.parse(state.getTarget(),text);
        } catch (AccessorException e) {
            handleGenericException(e,true);
        } catch (RuntimeException e) {
            if(state.getPrev() != null) {
                if (!(state.getPrev().getTarget() instanceof JAXBElement))
                    handleParseConversionException(state,e);
                // else
                // do nothing - issue 601 - don't report exceptions like
                // NumberFormatException when unmarshalling "nillable" element
                // (I suppose JAXBElement indicates this
            } else {
                handleParseConversionException(state,e);
            }
        }
    }
}
