/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
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
 * $Id: Util.java,v 1.2.4.1 2005/09/14 09:37:34 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.trax;

import com.sun.org.apache.xalan.internal.XalanConstants;
import java.io.InputStream;
import java.io.Reader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import com.sun.org.apache.xalan.internal.utils.FactoryImpl;
import com.sun.org.apache.xalan.internal.utils.XMLSecurityManager;
import com.sun.org.apache.xalan.internal.xsltc.compiler.XSLTC;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMsg;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * @author Santiago Pericas-Geertsen
 */
public final class Util {

    public static String baseName(String name) {
        return com.sun.org.apache.xalan.internal.xsltc.compiler.util.Util.baseName(name);
    }

    public static String noExtName(String name) {
        return com.sun.org.apache.xalan.internal.xsltc.compiler.util.Util.noExtName(name);
    }

    public static String toJavaName(String name) {
        return com.sun.org.apache.xalan.internal.xsltc.compiler.util.Util.toJavaName(name);
    }




    /**
     * Creates a SAX2 InputSource object from a TrAX Source object
     */
    public static InputSource getInputSource(XSLTC xsltc, Source source)
        throws TransformerConfigurationException
    {
        InputSource input = null;

        String systemId = source.getSystemId();

        try {
            // Try to get InputSource from SAXSource input
            if (source instanceof SAXSource) {
                final SAXSource sax = (SAXSource)source;
                input = sax.getInputSource();
                // Pass the SAX parser to the compiler
                try {
                    XMLReader reader = sax.getXMLReader();

                     /*
                      * Fix for bug 24695
                      * According to JAXP 1.2 specification if a SAXSource
                      * is created using a SAX InputSource the Transformer or
                      * TransformerFactory creates a reader via the
                      * XMLReaderFactory if setXMLReader is not used
                      */

                    if (reader == null) {
                       try {
                           reader= XMLReaderFactory.createXMLReader();
                           try {
                                reader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                                            xsltc.isSecureProcessing());
                           } catch (SAXNotRecognizedException e) {
                                System.err.println("Warning:  " + reader.getClass().getName() + ": "
                                        + e.getMessage());
                           }
                       } catch (Exception e ) {
                           try {

                               //Incase there is an exception thrown
                               // resort to JAXP
                               SAXParserFactory parserFactory = FactoryImpl.getSAXFactory(xsltc.useServicesMechnism());
                               parserFactory.setNamespaceAware(true);

                               if (xsltc.isSecureProcessing()) {
                                  try {
                                      parserFactory.setFeature(
                                          XMLConstants.FEATURE_SECURE_PROCESSING, true);
                                  }
                                  catch (org.xml.sax.SAXException se) {}
                               }

                               reader = parserFactory.newSAXParser()
                                     .getXMLReader();


                           } catch (ParserConfigurationException pce ) {
                               throw new TransformerConfigurationException
                                 ("ParserConfigurationException" ,pce);
                           }
                       }
                    }
                    reader.setFeature
                        ("http://xml.org/sax/features/namespaces",true);
                    reader.setFeature
                        ("http://xml.org/sax/features/namespace-prefixes",false);

                    try {
                        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,
                                   xsltc.getProperty(XMLConstants.ACCESS_EXTERNAL_DTD));
                    } catch (SAXNotRecognizedException e) {
                        System.err.println("Warning:  " + reader.getClass().getName() + ": "
                                + e.getMessage());
                    }

                    try {
                        XMLSecurityManager securityManager =
                                (XMLSecurityManager)xsltc.getProperty(XalanConstants.SECURITY_MANAGER);
                        if (securityManager != null) {
                            for (XMLSecurityManager.Limit limit : XMLSecurityManager.Limit.values()) {
                                reader.setProperty(limit.apiProperty(),
                                        securityManager.getLimitValueAsString(limit));
                            }
                            if (securityManager.printEntityCountInfo()) {
                                reader.setProperty(XalanConstants.JDK_ENTITY_COUNT_INFO, XalanConstants.JDK_YES);
                            }
                        }
                    } catch (SAXException se) {
                        System.err.println("Warning:  " + reader.getClass().getName() + ": "
                                    + se.getMessage());
                    }
                    xsltc.setXMLReader(reader);
                }catch (SAXNotRecognizedException snre ) {
                  throw new TransformerConfigurationException
                       ("SAXNotRecognizedException ",snre);
                }catch (SAXNotSupportedException snse ) {
                  throw new TransformerConfigurationException
                       ("SAXNotSupportedException ",snse);
                }catch (SAXException se ) {
                  throw new TransformerConfigurationException
                       ("SAXException ",se);
                }

            }
            // handle  DOMSource
            else if (source instanceof DOMSource) {
                final DOMSource domsrc = (DOMSource)source;
                final Document dom = (Document)domsrc.getNode();
                final DOM2SAX dom2sax = new DOM2SAX(dom);
                xsltc.setXMLReader(dom2sax);

                // Try to get SAX InputSource from DOM Source.
                input = SAXSource.sourceToInputSource(source);
                if (input == null){
                    input = new InputSource(domsrc.getSystemId());
                }
            }

            // handle StAXSource
            else if (source instanceof StAXSource) {
                final StAXSource staxSource = (StAXSource)source;
                StAXEvent2SAX staxevent2sax = null;
                StAXStream2SAX staxStream2SAX = null;
                if (staxSource.getXMLEventReader() != null) {
                    final XMLEventReader xmlEventReader = staxSource.getXMLEventReader();
                    staxevent2sax = new StAXEvent2SAX(xmlEventReader);
                    xsltc.setXMLReader(staxevent2sax);
                } else if (staxSource.getXMLStreamReader() != null) {
                    final XMLStreamReader xmlStreamReader = staxSource.getXMLStreamReader();
                    staxStream2SAX = new StAXStream2SAX(xmlStreamReader);
                    xsltc.setXMLReader(staxStream2SAX);
                }

                // get sax InputSource from StAXSource
                input = SAXSource.sourceToInputSource(source);
                if (input == null){
                    input = new InputSource(staxSource.getSystemId());
                }
            }

            // Try to get InputStream or Reader from StreamSource
            else if (source instanceof StreamSource) {
                final StreamSource stream = (StreamSource)source;
                final InputStream istream = stream.getInputStream();
                final Reader reader = stream.getReader();
                xsltc.setXMLReader(null);     // Clear old XML reader

                // Create InputSource from Reader or InputStream in Source
                if (istream != null) {
                    input = new InputSource(istream);
                }
                else if (reader != null) {
                    input = new InputSource(reader);
                }
                else {
                    input = new InputSource(systemId);
                }
            }
            else {
                ErrorMsg err = new ErrorMsg(ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR);
                throw new TransformerConfigurationException(err.toString());
            }
            input.setSystemId(systemId);
        }
        catch (NullPointerException e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.JAXP_NO_SOURCE_ERR,
                                        "TransformerFactory.newTemplates()");
            throw new TransformerConfigurationException(err.toString());
        }
        catch (SecurityException e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.FILE_ACCESS_ERR, systemId);
            throw new TransformerConfigurationException(err.toString());
        }
        return input;
    }

}
