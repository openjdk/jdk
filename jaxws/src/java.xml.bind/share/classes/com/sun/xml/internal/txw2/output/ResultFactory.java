/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.txw2.output;

import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;

/**
 * Factory for producing XmlSerializers for various Result types.
 *
 * @author Ryan.Shoemaker@Sun.COM
 */
public abstract class ResultFactory {

    /**
     * Do not instanciate.
     */
    private ResultFactory() {}

    /**
     * Factory method for producing {@link XmlSerializer} from {@link javax.xml.transform.Result}.
     *
     * This method supports {@link javax.xml.transform.sax.SAXResult},
     * {@link javax.xml.transform.stream.StreamResult}, and {@link javax.xml.transform.dom.DOMResult}.
     *
     * @param result the Result that will receive output from the XmlSerializer
     * @return an implementation of XmlSerializer that will produce output on the supplied Result
     */
    public static XmlSerializer createSerializer(Result result) {
        if (result instanceof SAXResult)
            return new SaxSerializer((SAXResult) result);
        if (result instanceof DOMResult)
            return new DomSerializer((DOMResult) result);
        if (result instanceof StreamResult)
            return new StreamSerializer((StreamResult) result);
        if (result instanceof TXWResult)
            return new TXWSerializer(((TXWResult)result).getWriter());

        throw new UnsupportedOperationException("Unsupported Result type: " + result.getClass().getName());
    }

}
