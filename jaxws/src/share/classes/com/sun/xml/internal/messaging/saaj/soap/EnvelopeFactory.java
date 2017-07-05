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
/*
 * $Id: EnvelopeFactory.java,v 1.24 2006/01/27 12:49:26 vj135062 Exp $
 * $Revision: 1.24 $
 * $Date: 2006/01/27 12:49:26 $
 */


package com.sun.xml.internal.messaging.saaj.soap;

import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;
import javax.xml.soap.SOAPException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.util.*;

import com.sun.xml.internal.messaging.saaj.util.transform.EfficientStreamingTransformer;

/**
 * EnvelopeFactory creates SOAP Envelope objects using different
 * underlying implementations.
 */
public class EnvelopeFactory {

    protected static Logger
        log = Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
        "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    private static ParserPool parserPool = new ParserPool(5);

    public static Envelope createEnvelope(Source src, SOAPPartImpl soapPart)
        throws SOAPException
    {
        // Insert SAX filter to disallow Document Type Declarations since
        // they are not legal in SOAP
        SAXParser saxParser = null;
        if (src instanceof StreamSource) {
            if (src instanceof JAXMStreamSource) {
                try {
                    ((JAXMStreamSource) src).reset();
                } catch (java.io.IOException ioe) {
                    log.severe("SAAJ0515.source.reset.exception");
                    throw new SOAPExceptionImpl(ioe);
                }
            }
            try {
                saxParser = parserPool.get();
            } catch (Exception e) {
                log.severe("SAAJ0601.util.newSAXParser.exception");
                throw new SOAPExceptionImpl(
                    "Couldn't get a SAX parser while constructing a envelope",
                    e);
            }
            InputSource is = SAXSource.sourceToInputSource(src);
            XMLReader rejectFilter;
            try {
                rejectFilter = new RejectDoctypeSaxFilter(saxParser);
            } catch (Exception ex) {
                log.severe("SAAJ0510.soap.cannot.create.envelope");
                throw new SOAPExceptionImpl(
                    "Unable to create envelope from given source: ",
                    ex);
            }
            src = new SAXSource(rejectFilter, is);
        }

        try {
            Transformer transformer =
                EfficientStreamingTransformer.newTransformer();
            DOMResult result = new DOMResult(soapPart);
            transformer.transform(src, result);

            Envelope env = (Envelope) soapPart.getEnvelope();
            if (saxParser != null) {
                parserPool.put(saxParser);
            }
            return env;
        } catch (Exception ex) {
            if (ex instanceof SOAPVersionMismatchException) {
                throw (SOAPVersionMismatchException) ex;
            }
            log.severe("SAAJ0511.soap.cannot.create.envelope");
            throw new SOAPExceptionImpl(
                "Unable to create envelope from given source: ",
                ex);
        }
    }
}
