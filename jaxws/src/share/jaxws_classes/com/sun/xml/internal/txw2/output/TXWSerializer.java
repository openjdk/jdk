/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.TXW;

/**
 * Dummpy implementation to pass through {@link TypedXmlWriter}
 * to {@link TXW}
 *
 * @author Kohsuke Kawaguchi
 */
public final class TXWSerializer implements XmlSerializer {
    public final TypedXmlWriter txw;

    public TXWSerializer(TypedXmlWriter txw) {
        this.txw = txw;
    }

    public void startDocument() {
        throw new UnsupportedOperationException();
    }

    public void endDocument() {
        throw new UnsupportedOperationException();
    }

    public void beginStartTag(String uri, String localName, String prefix) {
        throw new UnsupportedOperationException();
    }

    public void writeAttribute(String uri, String localName, String prefix, StringBuilder value) {
        throw new UnsupportedOperationException();
    }

    public void writeXmlns(String prefix, String uri) {
        throw new UnsupportedOperationException();
    }

    public void endStartTag(String uri, String localName, String prefix) {
        throw new UnsupportedOperationException();
    }

    public void endTag() {
        throw new UnsupportedOperationException();
    }

    public void text(StringBuilder text) {
        throw new UnsupportedOperationException();
    }

    public void cdata(StringBuilder text) {
        throw new UnsupportedOperationException();
    }

    public void comment(StringBuilder comment) {
        throw new UnsupportedOperationException();
    }

    public void flush() {
        throw new UnsupportedOperationException();
    }
}
