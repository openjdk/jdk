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
package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;

/**
 * Text data in XML.
 *
 * <p>
 * This class is used inside the marshaller/unmarshaller to
 * send/receive text data.
 *
 * <p>
 * On top of {@link CharSequence}, this class has an
 * ability to write itself to the {@link XmlOutput}. This allows
 * the implementation to choose the most efficient way possible
 * when writing to XML (for example, it can skip the escaping
 * of buffer copying.)
 *
 * TODO: visitor pattern support?
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Pcdata implements CharSequence {

    /**
     * Writes itself to {@link UTF8XmlOutput}.
     *
     * <p>
     * This is the most performance critical path for the marshaller,
     * so it warrants its own method.
     */
    public abstract void writeTo(UTF8XmlOutput output) throws IOException;

    /**
     * Writes itself to the character array.
     *
     * <p>
     * This method is used by most other {@link XmlOutput}.
     * The default implementation involves in one extra char[] copying.
     *
     * <p>
     * The caller must provide a big enough buffer that can hold
     * enough characters returned by the {@link #length()} method.
     */
    public void writeTo(char[] buf, int start) {
        toString().getChars(0,length(),buf,start);
    }

    public abstract String toString();
}
