/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: XMLReaderManager.java,v 1.2.4.1 2005/09/15 08:16:02 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.utils;

import com.sun.org.apache.xalan.internal.XalanConstants;
import com.sun.org.apache.xalan.internal.utils.FactoryImpl;
import com.sun.org.apache.xalan.internal.utils.SecuritySupport;
import java.util.HashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Creates XMLReader objects and caches them for re-use.
 * This class follows the singleton pattern.
 */
public class XMLReaderManager {

    private static final String NAMESPACES_FEATURE =
                             "http://xml.org/sax/features/namespaces";
    private static final String NAMESPACE_PREFIXES_FEATURE =
                             "http://xml.org/sax/features/namespace-prefixes";
    private static final XMLReaderManager m_singletonManager =
                                                     new XMLReaderManager();
    private static final String property = "org.xml.sax.driver";
    /**
     * Parser factory to be used to construct XMLReader objects
     */
    private static SAXParserFactory m_parserFactory;

    /**
     * Cache of XMLReader objects
     */
    private ThreadLocal m_readers;

    /**
     * Keeps track of whether an XMLReader object is in use.
     */
    private HashMap m_inUse;

    private boolean m_useServicesMechanism = true;
     /**
     * protocols allowed for external DTD references in source file and/or stylesheet.
     */
    private String _accessExternalDTD = XalanConstants.EXTERNAL_ACCESS_DEFAULT;

    /**
     * Hidden constructor
     */
    private XMLReaderManager() {
    }

    /**
     * Retrieves the singleton reader manager
     */
    public static XMLReaderManager getInstance(boolean useServicesMechanism) {
        m_singletonManager.setServicesMechnism(useServicesMechanism);
        return m_singletonManager;
    }

    /**
     * Retrieves a cached XMLReader for this thread, or creates a new
     * XMLReader, if the existing reader is in use.  When the caller no
     * longer needs the reader, it must release it with a call to
     * {@link #releaseXMLReader}.
     */
    public synchronized XMLReader getXMLReader() throws SAXException {
        XMLReader reader;

        if (m_readers == null) {
            // When the m_readers.get() method is called for the first time
            // on a thread, a new XMLReader will automatically be created.
            m_readers = new ThreadLocal();
        }

        if (m_inUse == null) {
            m_inUse = new HashMap();
        }

        // If the cached reader for this thread is in use, construct a new
        // one; otherwise, return the cached reader unless it isn't an
        // instance of the class set in the 'org.xml.sax.driver' property
        reader = (XMLReader) m_readers.get();
        boolean threadHasReader = (reader != null);
        String factory = SecuritySupport.getSystemProperty(property);
        if (threadHasReader && m_inUse.get(reader) != Boolean.TRUE &&
                ( factory == null || reader.getClass().getName().equals(factory))) {
            m_inUse.put(reader, Boolean.TRUE);
        } else {
            try {
                try {
                    // According to JAXP 1.2 specification, if a SAXSource
                    // is created using a SAX InputSource the Transformer or
                    // TransformerFactory creates a reader via the
                    // XMLReaderFactory if setXMLReader is not used
                    reader = XMLReaderFactory.createXMLReader();

                } catch (Exception e) {
                   try {
                        // If unable to create an instance, let's try to use
                        // the XMLReader from JAXP
                        if (m_parserFactory == null) {
                            m_parserFactory = FactoryImpl.getSAXFactory(m_useServicesMechanism);
                            m_parserFactory.setNamespaceAware(true);
                        }

                        reader = m_parserFactory.newSAXParser().getXMLReader();
                   } catch (ParserConfigurationException pce) {
                       throw pce;   // pass along pce
                   }
                }
                try {
                    reader.setFeature(NAMESPACES_FEATURE, true);
                    reader.setFeature(NAMESPACE_PREFIXES_FEATURE, false);
                } catch (SAXException se) {
                    // Try to carry on if we've got a parser that
                    // doesn't know about namespace prefixes.
                }
                try {
                    reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, _accessExternalDTD);
                } catch (SAXException se) {
                    System.err.println("Warning:  " + reader.getClass().getName() + ": "
                                + se.getMessage());
                }
            } catch (ParserConfigurationException ex) {
                throw new SAXException(ex);
            } catch (FactoryConfigurationError ex1) {
                throw new SAXException(ex1.toString());
            } catch (NoSuchMethodError ex2) {
            } catch (AbstractMethodError ame) {
            }

            // Cache the XMLReader if this is the first time we've created
            // a reader for this thread.
            if (!threadHasReader) {
                m_readers.set(reader);
                m_inUse.put(reader, Boolean.TRUE);
            }
        }

        return reader;
    }

    /**
     * Mark the cached XMLReader as available.  If the reader was not
     * actually in the cache, do nothing.
     *
     * @param reader The XMLReader that's being released.
     */
    public synchronized void releaseXMLReader(XMLReader reader) {
        // If the reader that's being released is the cached reader
        // for this thread, remove it from the m_isUse list.
        if (m_readers.get() == reader && reader != null) {
            m_inUse.remove(reader);
        }
    }
    /**
     * Return the state of the services mechanism feature.
     */
    public boolean useServicesMechnism() {
        return m_useServicesMechanism;
    }

    /**
     * Set the state of the services mechanism feature.
     */
    public void setServicesMechnism(boolean flag) {
        m_useServicesMechanism = flag;
    }

    /**
     * Get property value
     */
    public String getProperty(String name) {
        if (name.equals(XMLConstants.ACCESS_EXTERNAL_DTD)) {
            return _accessExternalDTD;
        }
        return null;
    }

    /**
     * Set property.
     */
    public void setProperty(String name, String value) {
        if (name.equals(XMLConstants.ACCESS_EXTERNAL_DTD)) {
            _accessExternalDTD = (String)value;
        }
    }
}
