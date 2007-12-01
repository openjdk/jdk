/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.utils;


import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * This {@link org.xml.sax.ErrorHandler} does absulutely nothing but logging
 * the events.
 *
 * @author Christian Geuer-Pollmann
 */
public class IgnoreAllErrorHandler implements ErrorHandler {

        /** {@link java.util.logging} logging facility */
        static java.util.logging.Logger log =
                java.util.logging.Logger.getLogger(
                        IgnoreAllErrorHandler.class.getName());

        /** Field throwExceptions */
        static final boolean warnOnExceptions = System.getProperty(
                "com.sun.org.apache.xml.internal.security.test.warn.on.exceptions", "false").equals("true");

        /** Field throwExceptions           */
        static final boolean throwExceptions = System.getProperty(
                "com.sun.org.apache.xml.internal.security.test.throw.exceptions", "false").equals("true");


        /** @inheritDoc */
        public void warning(SAXParseException ex) throws SAXException {
                if (IgnoreAllErrorHandler.warnOnExceptions) {
                        log.log(java.util.logging.Level.WARNING, "", ex);
                }
                if (IgnoreAllErrorHandler.throwExceptions) {
                        throw ex;
                }
        }


        /** @inheritDoc */
        public void error(SAXParseException ex) throws SAXException {
                if (IgnoreAllErrorHandler.warnOnExceptions) {
                        log.log(java.util.logging.Level.SEVERE, "", ex);
                }
                if (IgnoreAllErrorHandler.throwExceptions) {
                        throw ex;
                }
        }



        /** @inheritDoc */
        public void fatalError(SAXParseException ex) throws SAXException {
                if (IgnoreAllErrorHandler.warnOnExceptions) {
                        log.log(java.util.logging.Level.WARNING, "", ex);
                }
                if (IgnoreAllErrorHandler.throwExceptions) {
                        throw ex;
                }
        }
}
