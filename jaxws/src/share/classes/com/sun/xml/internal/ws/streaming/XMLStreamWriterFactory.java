/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.xml.internal.ws.util.FastInfosetReflection;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;

/**
 * <p>A factory to create XML and FI serializers.</p>
 *
 * @author Santiago.PericasGeertsen@sun.com
 */
public class XMLStreamWriterFactory {

    /**
     * StAX input factory shared by all threads.
     */
    static XMLOutputFactory xmlOutputFactory;

    /**
     * FI stream writer for each thread.
     */
    static ThreadLocal fiStreamWriter = new ThreadLocal();

    static {
        // Use StAX pluggability layer to get factory instance
        xmlOutputFactory = XMLOutputFactory.newInstance();
    }

    // -- XML ------------------------------------------------------------

    public static XMLStreamWriter createXMLStreamWriter(OutputStream out) {
        return createXMLStreamWriter(out, "UTF-8");
    }

    public static XMLStreamWriter createXMLStreamWriter(OutputStream out, String encoding) {
        return createXMLStreamWriter(out, encoding, true);
    }

    /**
     * TODO: declare?
     * TODO: Use thread locals and reset() when using Zephyr
     */
    public static XMLStreamWriter createXMLStreamWriter(OutputStream out,
        String encoding, boolean declare)
    {
        try {
            // Assume StAX factory implementation is not thread-safe
            synchronized (xmlOutputFactory) {
                return xmlOutputFactory.createXMLStreamWriter(out, encoding);
            }
        }
        catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    // -- Fast Infoset ---------------------------------------------------

    public static XMLStreamWriter createFIStreamWriter(OutputStream out) {
        return createFIStreamWriter(out, "UTF-8");
    }

    public static XMLStreamWriter createFIStreamWriter(OutputStream out, String encoding) {
        return createFIStreamWriter(out, encoding, true);
    }

    public static XMLStreamWriter createFIStreamWriter(OutputStream out,
        String encoding, boolean declare)
    {
        // Check if compatible implementation of FI was found
        if (FastInfosetReflection.fiStAXDocumentSerializer_new == null) {
            throw new XMLReaderException("fastinfoset.noImplementation");
        }

        try {
            Object sds = fiStreamWriter.get();
            if (sds == null) {
                // Do not use StAX pluggable layer for FI
                fiStreamWriter.set(sds = FastInfosetReflection.fiStAXDocumentSerializer_new.newInstance());
            }
            FastInfosetReflection.fiStAXDocumentSerializer_setOutputStream.invoke(sds, out);
            FastInfosetReflection.fiStAXDocumentSerializer_setEncoding.invoke(sds, encoding);
            return (XMLStreamWriter) sds;
        }  catch (Exception e) {
            throw new XMLStreamWriterException(e);
        }
    }

}
