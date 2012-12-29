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

package com.sun.tools.internal.xjc.reader.xmlschema.parser;

import java.io.InputStream;
import java.io.Reader;

import org.w3c.dom.ls.LSInput;
import org.xml.sax.InputSource;

/**
 * LSInput implementation that wraps a SAX InputSource
 *
 * @author Ryan.Shoemaker@Sun.COM
 */
public class LSInputSAXWrapper implements LSInput {
    private InputSource core;

    public LSInputSAXWrapper(InputSource inputSource) {
        assert inputSource!=null;
        core = inputSource;
    }

    public Reader getCharacterStream() {
        return core.getCharacterStream();
    }

    public void setCharacterStream(Reader characterStream) {
        core.setCharacterStream(characterStream);
    }

    public InputStream getByteStream() {
        return core.getByteStream();
    }

    public void setByteStream(InputStream byteStream) {
        core.setByteStream(byteStream);
    }

    public String getStringData() {
        return null;
    }

    public void setStringData(String stringData) {
        // no-op
    }

    public String getSystemId() {
        return core.getSystemId();
    }

    public void setSystemId(String systemId) {
        core.setSystemId(systemId);
    }

    public String getPublicId() {
        return core.getPublicId();
    }

    public void setPublicId(String publicId) {
        core.setPublicId(publicId);
    }

    public String getBaseURI() {
        return null;
    }

    public void setBaseURI(String baseURI) {
        // no-op
    }

    public String getEncoding() {
        return core.getEncoding();
    }

    public void setEncoding(String encoding) {
        core.setEncoding(encoding);
    }

    public boolean getCertifiedText() {
        return true;
    }

    public void setCertifiedText(boolean certifiedText) {
        // no-op
    }
}
