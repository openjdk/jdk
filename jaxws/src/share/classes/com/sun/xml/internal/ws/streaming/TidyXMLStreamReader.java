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

package com.sun.xml.internal.ws.streaming;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.util.xml.XMLStreamReaderFilter;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import java.io.Closeable;
import java.io.IOException;

/**
 * Wrapper over XMLStreamReader. It will be used primarily to
 * clean up the resources such as closure on InputStream/Reader.
 *
 * @author Vivek Pandey
 */
public class TidyXMLStreamReader extends XMLStreamReaderFilter {
    private final Closeable closeableSource;

    public TidyXMLStreamReader(@NotNull XMLStreamReader reader, @Nullable Closeable closeableSource) {
        super(reader);
        this.closeableSource = closeableSource;
    }

    public void close() throws XMLStreamException {
        super.close();
        try {
            if(closeableSource != null)
                closeableSource.close();
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
    }
}
