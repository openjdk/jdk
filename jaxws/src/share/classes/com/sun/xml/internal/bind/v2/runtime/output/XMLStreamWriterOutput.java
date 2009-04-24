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
import java.lang.reflect.Constructor;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.MarshallerImpl;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;

import org.xml.sax.SAXException;

/**
 * {@link XmlOutput} that writes to StAX {@link XMLStreamWriter}.
 * <p>
 * TODO:
 * Finding the optimized FI implementations is a bit hacky and not very
 * extensible. Can we use the service provider mechnism in general for
 * concrete implementations of XmlOutputAbstractImpl.
 *
 * @author Kohsuke Kawaguchi
 */
public class XMLStreamWriterOutput extends XmlOutputAbstractImpl {

    /**
     * Creates a new {@link XmlOutput} from a {@link XMLStreamWriter}.
     * This method recognizes an FI StAX writer.
     */
    public static XmlOutput create(XMLStreamWriter out, JAXBContextImpl context) {
        // try optimized path
        final Class writerClass = out.getClass();
        if (writerClass==FI_STAX_WRITER_CLASS) {
            try {
                return FI_OUTPUT_CTOR.newInstance(out, context);
            } catch (Exception e) {
            }
        }
        if (STAXEX_WRITER_CLASS!=null && STAXEX_WRITER_CLASS.isAssignableFrom(writerClass)) {
            try {
                return STAXEX_OUTPUT_CTOR.newInstance(out);
            } catch (Exception e) {
            }
        }

        // otherwise the normal writer.
        return new XMLStreamWriterOutput(out);
    }


    private final XMLStreamWriter out;

    protected final char[] buf = new char[256];

    protected XMLStreamWriterOutput(XMLStreamWriter out) {
        this.out = out;
    }

    // not called if we are generating fragments
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        super.startDocument(serializer, fragment,nsUriIndex2prefixIndex,nsContext);
        if(!fragment)
            out.writeStartDocument();
    }

    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        if(!fragment) {
            out.writeEndDocument();
            out.flush();
        }
        super.endDocument(fragment);
    }

    public void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException {
        out.writeStartElement(
            nsContext.getPrefix(prefix),
            localName,
            nsContext.getNamespaceURI(prefix));

        NamespaceContextImpl.Element nse = nsContext.getCurrent();
        if(nse.count()>0) {
            for( int i=nse.count()-1; i>=0; i-- ) {
                String uri = nse.getNsUri(i);
                if(uri.length()==0 && nse.getBase()==1)
                    continue;   // no point in definint xmlns='' on the root
                out.writeNamespace(nse.getPrefix(i),uri);
            }
        }
    }

    public void attribute(int prefix, String localName, String value) throws IOException, XMLStreamException {
        if(prefix==-1)
            out.writeAttribute(localName,value);
        else
            out.writeAttribute(
                    nsContext.getPrefix(prefix),
                    nsContext.getNamespaceURI(prefix),
                    localName, value);
    }

    public void endStartTag() throws IOException, SAXException {
        // noop
    }

    public void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException {
        out.writeEndElement();
    }

    public void text(String value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        if(needsSeparatingWhitespace)
            out.writeCharacters(" ");
        out.writeCharacters(value);
    }

    public void text(Pcdata value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        if(needsSeparatingWhitespace)
            out.writeCharacters(" ");

        int len = value.length();
        if(len <buf.length) {
            value.writeTo(buf,0);
            out.writeCharacters(buf,0,len);
        } else {
            out.writeCharacters(value.toString());
        }
    }


    /**
     * Reference to FI's XMLStreamWriter class, if FI can be loaded.
     */
    private static final Class FI_STAX_WRITER_CLASS = initFIStAXWriterClass();
    private static final Constructor<? extends XmlOutput> FI_OUTPUT_CTOR = initFastInfosetOutputClass();

    private static Class initFIStAXWriterClass() {
        try {
            Class llfisw = MarshallerImpl.class.getClassLoader().
                    loadClass("com.sun.xml.internal.org.jvnet.fastinfoset.stax.LowLevelFastInfosetStreamWriter");
            Class sds = MarshallerImpl.class.getClassLoader().
                    loadClass("com.sun.xml.internal.fastinfoset.stax.StAXDocumentSerializer");
            // Check if StAXDocumentSerializer implements LowLevelFastInfosetStreamWriter
            if (llfisw.isAssignableFrom(sds))
                return sds;
            else
                return null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Constructor<? extends XmlOutput> initFastInfosetOutputClass() {
        try {
            if (FI_STAX_WRITER_CLASS == null)
                return null;

            Class c = UnmarshallerImpl.class.getClassLoader().loadClass("com.sun.xml.internal.bind.v2.runtime.output.FastInfosetStreamWriterOutput");
            return c.getConstructor(FI_STAX_WRITER_CLASS, JAXBContextImpl.class);
        } catch (Throwable e) {
            return null;
        }
    }

    //
    // StAX-ex
    //
    private static final Class STAXEX_WRITER_CLASS = initStAXExWriterClass();
    private static final Constructor<? extends XmlOutput> STAXEX_OUTPUT_CTOR = initStAXExOutputClass();

    private static Class initStAXExWriterClass() {
        try {
            return MarshallerImpl.class.getClassLoader().loadClass("com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx");
        } catch (Throwable e) {
            return null;
        }
    }

    private static Constructor<? extends XmlOutput> initStAXExOutputClass() {
        try {
            Class c = UnmarshallerImpl.class.getClassLoader().loadClass("com.sun.xml.internal.bind.v2.runtime.output.StAXExStreamWriterOutput");
            return c.getConstructor(STAXEX_WRITER_CLASS);
        } catch (Throwable e) {
            return null;
        }
    }
}
