/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * EfficientStreamingTransformer.java
 *
 * Created on July 29, 2002, 3:49 PM
 */

package com.sun.xml.internal.messaging.saaj.util.transform;

import java.io.*;

import java.net.URISyntaxException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import com.sun.xml.internal.messaging.saaj.util.XMLDeclarationParser;
import com.sun.xml.internal.messaging.saaj.util.FastInfosetReflection;
import java.net.URI;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

/**
 * This class is a proxy for a Transformer object with optimizations
 * for certain cases. If source and result are of type stream, then
 * bytes are simply copied whenever possible (note that this assumes
 * that the input is well formed). In addition, it provides support for
 * FI using native DOM parsers and serializers.
 *
 * @author Panos Kougiouris panos@acm.org
 * @author Santiago.PericasGeertsen@sun.com
 *
 */
public class EfficientStreamingTransformer
    extends javax.xml.transform.Transformer {

  //static final String version;
  //static final String vendor;
  // removing static : security issue : CR 6813167Z
  private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

  /**
  removing support for Java 1.4 and 1.3 : CR6658158
  static {
        version = System.getProperty("java.vm.version");
        vendor = System.getProperty("java.vm.vendor");
        if (vendor.startsWith("Sun") &&
            (version.startsWith("1.4") || version.startsWith("1.3"))) {
            transformerFactory =
                new com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl();
        }
  }*/

    /**
     * TransformerFactory instance.
     */

    /**
     * Underlying XSLT transformer.
     */
    private Transformer m_realTransformer = null;

    /**
     * Undelying FI DOM parser.
     */
    private Object m_fiDOMDocumentParser = null;

    /**
     * Underlying FI DOM serializer.
     */
    private Object m_fiDOMDocumentSerializer = null;

    private EfficientStreamingTransformer() {
    }

    private void materialize() throws TransformerException {
        if (m_realTransformer == null) {
            m_realTransformer = transformerFactory.newTransformer();
        }
    }

    @Override
    public void clearParameters() {
        if (m_realTransformer != null)
            m_realTransformer.clearParameters();
    }

    @Override
    public javax.xml.transform.ErrorListener getErrorListener() {
        try {
            materialize();
            return m_realTransformer.getErrorListener();
        } catch (TransformerException e) {
            // will be caught later
        }
        return null;
    }

    @Override
    public java.util.Properties getOutputProperties() {
        try {
            materialize();
            return m_realTransformer.getOutputProperties();
        } catch (TransformerException e) {
            // will be caught later
        }
        return null;
    }

    @Override
    public String getOutputProperty(String str)
        throws java.lang.IllegalArgumentException {
        try {
            materialize();
            return m_realTransformer.getOutputProperty(str);
        } catch (TransformerException e) {
            // will be caught later
        }
        return null;
    }

    @Override
    public Object getParameter(String str) {
        try {
            materialize();
            return m_realTransformer.getParameter(str);
        } catch (TransformerException e) {
            // will be caught later
        }
        return null;
    }

    @Override
    public javax.xml.transform.URIResolver getURIResolver() {
        try {
            materialize();
            return m_realTransformer.getURIResolver();
        } catch (TransformerException e) {
            // will be caught later
        }
        return null;
    }

    @Override
    public void setErrorListener(
        javax.xml.transform.ErrorListener errorListener)
        throws java.lang.IllegalArgumentException {
        try {
            materialize();
            m_realTransformer.setErrorListener(errorListener);
        } catch (TransformerException e) {
            // will be caught later
        }
    }

    @Override
    public void setOutputProperties(java.util.Properties properties)
        throws java.lang.IllegalArgumentException {
        try {
            materialize();
            m_realTransformer.setOutputProperties(properties);
        } catch (TransformerException e) {
            // will be caught later
        }
    }

    @Override
    public void setOutputProperty(String str, String str1)
        throws java.lang.IllegalArgumentException {
        try {
            materialize();
            m_realTransformer.setOutputProperty(str, str1);
        } catch (TransformerException e) {
            // will be caught later
        }
    }

    @Override
    public void setParameter(String str, Object obj) {
        try {
            materialize();
            m_realTransformer.setParameter(str, obj);
        } catch (TransformerException e) {
            // will be caught later
        }
    }

    @Override
    public void setURIResolver(javax.xml.transform.URIResolver uRIResolver) {
        try {
            materialize();
            m_realTransformer.setURIResolver(uRIResolver);
        } catch (TransformerException e) {
            // will be caught later
        }
    }

    private InputStream getInputStreamFromSource(StreamSource s)
        throws TransformerException {

        InputStream stream = s.getInputStream();
        if (stream != null)
            return stream;

        if (s.getReader() != null)
            return null;

        String systemId = s.getSystemId();
        if (systemId != null) {
            try {
                String fileURL = systemId;

                if (systemId.startsWith("file:///"))
                {
                    /*
                     systemId is:
                     file:///<drive>:/some/path/file.xml
                     or
                     file:///some/path/file.xml
                    */

                    String absolutePath = systemId.substring(7);
                    /*
                     /<drive>:/some/path/file.xml
                     or
                     /some/path/file.xml
                    */

                    boolean hasDriveDesignator = absolutePath.indexOf(":") > 0;
                    if (hasDriveDesignator) {
                      String driveDesignatedPath = absolutePath.substring(1);
                      /*
                      <drive>:/some/path/file.xml */
                      fileURL = driveDesignatedPath;
                    }
                    else {
                      /*
                      /some/path/file.xml */
                      fileURL = absolutePath;
                    }
                }
                //return new FileInputStream(fileURL);
                try {
                    return new FileInputStream(new File(new URI(fileURL)));
                } catch (URISyntaxException ex) {
                    throw new TransformerException(ex);
                }
            } catch (IOException e) {
                throw new TransformerException(e.toString());
            }
        }

        throw new TransformerException("Unexpected StreamSource object");
    }

    //------------------------------------------------------------------------

    @Override
    public void transform(
        javax.xml.transform.Source source,
        javax.xml.transform.Result result)
        throws javax.xml.transform.TransformerException
    {
        // StreamSource -> StreamResult
        if ((source instanceof StreamSource)
            && (result instanceof StreamResult)) {
            try {
                StreamSource streamSource = (StreamSource) source;
                InputStream is = getInputStreamFromSource(streamSource);

                OutputStream os = ((StreamResult) result).getOutputStream();
                if (os == null)
                    // TODO: We might want to fix this if it were to be used beyond
                    // XmlDataContentHandler that we know uses only OutputStream
                    throw new TransformerException("Unexpected StreamResult object contains null OutputStream");

                if (is != null) {
                    if (is.markSupported())
                        is.mark(Integer.MAX_VALUE);
                    int num;
                    byte[] b = new byte[8192];
                    while ((num = is.read(b)) != -1) {
                        os.write(b, 0, num);
                    }
                    if (is.markSupported())
                        is.reset();
                    return;
                }

                Reader reader = streamSource.getReader();
                if (reader != null) {

                    if (reader.markSupported())
                        reader.mark(Integer.MAX_VALUE);

                    PushbackReader pushbackReader = new PushbackReader(reader, 4096);
                    //some size to unread <?xml ....?>
                    XMLDeclarationParser ev =
                        new XMLDeclarationParser(pushbackReader);
                    try {
                        ev.parse();
                    } catch (Exception ex) {
                        throw new TransformerException(
                            "Unable to run the JAXP transformer on a stream "
                                + ex.getMessage());
                    }
                    Writer writer =
                        new OutputStreamWriter(os /*, ev.getEncoding()*/);
                    ev.writeTo(writer);         // doesn't write any, if no header

                    int num;
                    char[] ac = new char[8192];
                    while ((num = pushbackReader.read(ac)) != -1) {
                        writer.write(ac, 0, num);
                    }
                    writer.flush();

                    if (reader.markSupported())
                        reader.reset();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new TransformerException(e.toString());
            }

            throw new TransformerException("Unexpected StreamSource object");
        }
        // FastInfosetSource -> DOMResult
        else if (FastInfosetReflection.isFastInfosetSource(source)
                && (result instanceof DOMResult))
        {
            try {
                // Use reflection to avoid a static dep with FI
                if (m_fiDOMDocumentParser == null) {
                    m_fiDOMDocumentParser = FastInfosetReflection.DOMDocumentParser_new();
                }

                // m_fiDOMDocumentParser.parse(document, source.getInputStream())
                FastInfosetReflection.DOMDocumentParser_parse(
                    m_fiDOMDocumentParser,
                    (Document) ((DOMResult) result).getNode(),
                    FastInfosetReflection.FastInfosetSource_getInputStream(source));

                // We're done!
                return;
            }
            catch (Exception e) {
                throw new TransformerException(e);
            }
        }
        // DOMSource -> FastInfosetResult
        else if ((source instanceof DOMSource)
                && FastInfosetReflection.isFastInfosetResult(result))
        {
            try {
                // Use reflection to avoid a static dep with FI
                if (m_fiDOMDocumentSerializer == null) {
                    m_fiDOMDocumentSerializer = FastInfosetReflection.DOMDocumentSerializer_new();
                }

                // m_fiDOMDocumentSerializer.setOutputStream(result.getOutputStream())
                FastInfosetReflection.DOMDocumentSerializer_setOutputStream(
                    m_fiDOMDocumentSerializer,
                    FastInfosetReflection.FastInfosetResult_getOutputStream(result));

                // m_fiDOMDocumentSerializer.serialize(node)
                FastInfosetReflection.DOMDocumentSerializer_serialize(
                    m_fiDOMDocumentSerializer,
                    ((DOMSource) source).getNode());

                // We're done!
                return;
            }
            catch (Exception e) {
                throw new TransformerException(e);
            }
        }

        // All other cases -- use transformer object

        materialize();
        m_realTransformer.transform(source, result);
    }

    /**
     * Threadlocal to hold a Transformer instance for this thread.
     * CR : 6813167
     */
    //private static ThreadLocal effTransformer = new ThreadLocal();

    /**
     * Return Transformer instance for this thread, allocating a new one if
     * necessary. Note that this method does not clear global parameters,
     * properties or any other data set on a previously used transformer.
     *
     * @return Transformer instance
     */
    public static Transformer newTransformer() {
        //CR : 6813167
        /*Transformer tt = (Transformer) effTransformer.get();
        if (tt == null) {
            effTransformer.set(tt = new EfficientStreamingTransformer());
        }
        return tt;*/
        return new EfficientStreamingTransformer();
    }

}
