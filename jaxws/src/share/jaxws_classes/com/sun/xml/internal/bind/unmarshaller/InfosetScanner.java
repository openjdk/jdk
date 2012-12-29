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

package com.sun.xml.internal.bind.unmarshaller;

import javax.xml.bind.Binder;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.LocatorEx;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Visits a DOM-ish API and generates SAX events.
 *
 * <p>
 * This interface is not tied to any particular DOM API.
 * Used by the {@link Binder}.
 *
 * <p>
 * Since we are parsing a DOM-ish tree, I don't think this
 * scanner itself will ever find an error, so this class
 * doesn't have its own error reporting scheme.
 *
 * <p>
 * This interface <b>MAY NOT</b> be implemented by the generated
 * runtime nor the generated code. We may add new methods on
 * this interface later. This is to be implemented by the static runtime
 * only.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @since 2.0
 */
public interface InfosetScanner<XmlNode> {
    /**
     * Parses the given DOM-ish element/document and generates
     * SAX events.
     *
     * @throws ClassCastException
     *      If the type of the node is not known to this implementation.
     *
     * @throws SAXException
     *      If the {@link ContentHandler} throws a {@link SAXException}.
     *      Do not throw an exception just because the scanner failed
     *      (if that can happen we need to change the API.)
     */
    void scan( XmlNode node ) throws SAXException;

    /**
     * Sets the {@link ContentHandler}.
     *
     * This handler receives the SAX events.
     */
    void setContentHandler( ContentHandler handler );
    ContentHandler getContentHandler();

    /**
     * Gets the current element we are parsing.
     *
     * <p>
     * This method could
     * be called from the {@link ContentHandler#startElement(String, String, String, Attributes)}
     * or {@link ContentHandler#endElement(String, String, String)}.
     *
     * <p>
     * Otherwise the behavior of this method is undefined.
     *
     * @return
     *      never return null.
     */
    XmlNode getCurrentElement();

    LocatorEx getLocator();
}
