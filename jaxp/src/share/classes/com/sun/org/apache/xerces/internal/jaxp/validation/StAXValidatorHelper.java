/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.org.apache.xerces.internal.jaxp.validation;

import java.io.IOException;
import java.util.Locale;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;

import org.xml.sax.SAXException;

/**
 * <p>A validator helper for <code>StAXSource</code>s.</p>
 *
 * @author <a href="mailto:Sunitha.Reddy@Sun.com">Sunitha Reddy</a>
 */
public final class StAXValidatorHelper implements ValidatorHelper {

    /** Component manager. **/
    private XMLSchemaValidatorComponentManager fComponentManager;

    private Transformer identityTransformer1 = null;
    private TransformerHandler identityTransformer2 = null;
    private ValidatorHandlerImpl handler = null;

    /** Creates a new instance of StaxValidatorHelper */
    public StAXValidatorHelper(XMLSchemaValidatorComponentManager componentManager) {
        fComponentManager = componentManager;
    }

    public void validate(Source source, Result result)
        throws SAXException, IOException {

        if (result == null || result instanceof StAXResult) {

            if( identityTransformer1==null ) {
                try {
                    SAXTransformerFactory tf = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
                    identityTransformer1 = tf.newTransformer();
                    identityTransformer2 = tf.newTransformerHandler();
                } catch (TransformerConfigurationException e) {
                    // this is impossible, but again better safe than sorry
                    throw new TransformerFactoryConfigurationError(e);
                }
            }

            if( result!=null ) {
                handler = new ValidatorHandlerImpl(fComponentManager);
                handler.setContentHandler(identityTransformer2);
                identityTransformer2.setResult(result);
            }

            try {
                identityTransformer1.transform( source, new SAXResult(handler) );
            } catch (TransformerException e) {
                if( e.getException() instanceof SAXException )
                    throw (SAXException)e.getException();
                throw new SAXException(e);
            } finally {
                handler.setContentHandler(null);
            }
            return;
        }
        throw new IllegalArgumentException(JAXPValidationMessageFormatter.formatMessage(Locale.getDefault(),
                "SourceResultMismatch",
                new Object [] {source.getClass().getName(), result.getClass().getName()}));
    }
}
