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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.org.jvnet.fastinfoset;

import java.io.OutputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;
import javax.xml.transform.sax.SAXResult;
import com.sun.xml.internal.fastinfoset.sax.SAXDocumentSerializer;

/**
 *  A JAXP Result implementation that supports the serialization to fast
 *  infoset documents for use by applications that expect a Result.
 *
 *  <P>The derivation of FIResult from SAXResult is an implementation
 *  detail.<P>
 *
 *  <P>This implementation is designed for interoperation with JAXP and is not
 *  not designed with performance in mind. It is recommended that for performant
 *  interoperation alternative serializer specific solutions be used.<P>
 *
 *  <P>General applications shall not call the following methods:
 *   <UL>
 *     <LI>setHandler</LI>
 *     <LI>setLexicalHandler</LI>
 *     <LI>setSystemId</LI>
 *   </UL>
 *  </P>
 */
public class FastInfosetResult extends SAXResult {

    OutputStream _outputStream;

    public FastInfosetResult(OutputStream outputStream) {
        _outputStream = outputStream;
    }

    public ContentHandler getHandler() {
        ContentHandler handler = super.getHandler();
        if (handler == null) {
            handler = new SAXDocumentSerializer();
            setHandler(handler);
        }
        ((SAXDocumentSerializer) handler).setOutputStream(_outputStream);
        return handler;
    }

    public LexicalHandler getLexicalHandler() {
        return (LexicalHandler) getHandler();
    }

    public OutputStream getOutputStream() {
        return _outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        _outputStream = outputStream;
    }
}
