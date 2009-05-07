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

package com.sun.xml.internal.ws.encoding.fastinfoset;

import com.sun.xml.internal.fastinfoset.stax.StAXDocumentParser;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import java.io.InputStream;
import java.io.Reader;
import javax.xml.stream.XMLStreamReader;

/**
 * @author Alexey Stashok
 */
public final class FastInfosetStreamReaderFactory extends XMLStreamReaderFactory {
    private static final FastInfosetStreamReaderFactory factory = new FastInfosetStreamReaderFactory();

    private ThreadLocal<StAXDocumentParser> pool = new ThreadLocal<StAXDocumentParser>();

    public static FastInfosetStreamReaderFactory getInstance() {
        return factory;
    }

    public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
        StAXDocumentParser parser = fetch();
        if (parser == null) {
            return FastInfosetCodec.createNewStreamReaderRecyclable(in, false);
        }

        parser.setInputStream(in);
        return parser;
    }

    public XMLStreamReader doCreate(String systemId, Reader reader, boolean rejectDTDs) {
        throw new UnsupportedOperationException();
    }

    private StAXDocumentParser fetch() {
        StAXDocumentParser parser = pool.get();
        pool.set(null);
        return parser;
    }

    public void doRecycle(XMLStreamReader r) {
        if (r instanceof StAXDocumentParser) {
            pool.set((StAXDocumentParser) r);
        }
    }
}
