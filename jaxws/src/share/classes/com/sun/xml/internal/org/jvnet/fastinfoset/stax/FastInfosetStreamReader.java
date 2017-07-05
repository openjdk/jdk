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

package com.sun.xml.internal.org.jvnet.fastinfoset.stax;

import javax.xml.stream.XMLStreamException;

/**
 * Fast Infoset Stream Reader.
 * <p>
 * This interface provides additional optimized methods to that of
 * {@link javax.xml.stream.XMLStreamReader}.
 */
public interface FastInfosetStreamReader {
    /**
     * Peek at the next event.
     *
     * @return the event, which will be the same as that returned from
     *         {@link #next}.
     */
    public int peekNext() throws XMLStreamException;

    // Faster access methods without checks

    public int accessNamespaceCount();

    public String accessLocalName();

    public String accessNamespaceURI();

    public String accessPrefix();

    public char[] accessTextCharacters();

    public int accessTextStart();

    public int accessTextLength();
}
