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
// $Id: XPathFactoryImpl.java,v 1.2 2005/08/16 22:41:13 jeffsuttor Exp $

package com.sun.org.apache.xpath.internal.jaxp;

import com.sun.org.apache.xpath.internal.res.XPATHErrorResources;
import com.sun.org.apache.xalan.internal.res.XSLMessages;

import javax.xml.XMLConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/**
 * The XPathFactory builds XPaths.
 *
 * @author  Ramesh Mandava
 */
public  class XPathFactoryImpl extends XPathFactory {

        /**
         * <p>Name of class as a constant to use for debugging.</p>
         */
        private static final String CLASS_NAME = "XPathFactoryImpl";

        /**
         *<p>XPathFunctionResolver for this XPathFactory and created XPaths.</p>
         */
        private XPathFunctionResolver xPathFunctionResolver = null;

        /**
         * <p>XPathVariableResolver for this XPathFactory and created XPaths</p>
         */
        private XPathVariableResolver xPathVariableResolver = null;

        /**
         * <p>State of secure processing feature.</p>
         */
        private boolean featureSecureProcessing = false;

        /**
         * <p>Is specified object model supported by this
         * <code>XPathFactory</code>?</p>
         *
         * @param objectModel Specifies the object model which the returned
         * <code>XPathFactory</code> will understand.
         *
         * @return <code>true</code> if <code>XPathFactory</code> supports
         * <code>objectModel</code>, else <code>false</code>.
         *
         * @throws NullPointerException If <code>objectModel</code> is <code>null</code>.
         * @throws IllegalArgumentException If <code>objectModel.length() == 0</code>.
         */
        public boolean isObjectModelSupported(String objectModel) {

            if (objectModel == null) {
                String fmsg = XSLMessages.createXPATHMessage(
                        XPATHErrorResources.ER_OBJECT_MODEL_NULL,
                        new Object[] { this.getClass().getName() } );

                throw new NullPointerException( fmsg );
            }

            if (objectModel.length() == 0) {
                String fmsg = XSLMessages.createXPATHMessage(
                        XPATHErrorResources.ER_OBJECT_MODEL_EMPTY,
                        new Object[] { this.getClass().getName() } );
                throw new IllegalArgumentException( fmsg );
            }

            // know how to support default object model, W3C DOM
            if (objectModel.equals(XPathFactory.DEFAULT_OBJECT_MODEL_URI)) {
                return true;
            }

            // don't know how to support anything else
            return false;
        }

        /**
         * <p>Returns a new <code>XPath</code> object using the underlying
         * object model determined when the factory was instantiated.</p>
         *
         * @return New <code>XPath</code>
         */
        public javax.xml.xpath.XPath newXPath() {
            return new com.sun.org.apache.xpath.internal.jaxp.XPathImpl(
                    xPathVariableResolver, xPathFunctionResolver,
                    featureSecureProcessing );
        }

        /**
         * <p>Set a feature for this <code>XPathFactory</code> and
         * <code>XPath</code>s created by this factory.</p>
         *
         * <p>
         * Feature names are fully qualified {@link java.net.URI}s.
         * Implementations may define their own features.
         * An {@link XPathFactoryConfigurationException} is thrown if this
         * <code>XPathFactory</code> or the <code>XPath</code>s
         *  it creates cannot support the feature.
         * It is possible for an <code>XPathFactory</code> to expose a feature
         * value but be unable to change its state.
         * </p>
         *
         * <p>See {@link javax.xml.xpath.XPathFactory} for full documentation
         * of specific features.</p>
         *
         * @param name Feature name.
         * @param value Is feature state <code>true</code> or <code>false</code>.
         *
         * @throws XPathFactoryConfigurationException if this
         * <code>XPathFactory</code> or the <code>XPath</code>s
         *   it creates cannot support this feature.
         * @throws NullPointerException if <code>name</code> is
         * <code>null</code>.
         */
        public void setFeature(String name, boolean value)
                throws XPathFactoryConfigurationException {

            // feature name cannot be null
            if (name == null) {
                String fmsg = XSLMessages.createXPATHMessage(
                        XPATHErrorResources.ER_FEATURE_NAME_NULL,
                        new Object[] { CLASS_NAME, new Boolean( value) } );
                throw new NullPointerException( fmsg );
             }

            // secure processing?
            if (name.equals(XMLConstants.FEATURE_SECURE_PROCESSING)) {

                featureSecureProcessing = value;

                // all done processing feature
                return;
            }

            // unknown feature
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_FEATURE_UNKNOWN,
                    new Object[] { name, CLASS_NAME, new Boolean(value) } );
            throw new XPathFactoryConfigurationException( fmsg );
        }

        /**
         * <p>Get the state of the named feature.</p>
         *
         * <p>
         * Feature names are fully qualified {@link java.net.URI}s.
         * Implementations may define their own features.
         * An {@link XPathFactoryConfigurationException} is thrown if this
         * <code>XPathFactory</code> or the <code>XPath</code>s
         * it creates cannot support the feature.
         * It is possible for an <code>XPathFactory</code> to expose a feature
         * value but be unable to change its state.
         * </p>
         *
         * @param name Feature name.
         *
         * @return State of the named feature.
         *
         * @throws XPathFactoryConfigurationException if this
         * <code>XPathFactory</code> or the <code>XPath</code>s
         *   it creates cannot support this feature.
         * @throws NullPointerException if <code>name</code> is
         * <code>null</code>.
         */
        public boolean getFeature(String name)
                throws XPathFactoryConfigurationException {

            // feature name cannot be null
            if (name == null) {
                String fmsg = XSLMessages.createXPATHMessage(
                        XPATHErrorResources.ER_GETTING_NULL_FEATURE,
                        new Object[] { CLASS_NAME } );
                throw new NullPointerException( fmsg );
            }

            // secure processing?
            if (name.equals(XMLConstants.FEATURE_SECURE_PROCESSING)) {
                return featureSecureProcessing;
            }

            // unknown feature
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_GETTING_UNKNOWN_FEATURE,
                    new Object[] { name, CLASS_NAME } );

            throw new XPathFactoryConfigurationException( fmsg );
        }

        /**
         * <p>Establish a default function resolver.</p>
         *
         * <p>Any <code>XPath</code> objects constructed from this factory will use
         * the specified resolver by default.</p>
         *
         * <p>A <code>NullPointerException</code> is thrown if
         * <code>resolver</code> is <code>null</code>.</p>
         *
         * @param resolver XPath function resolver.
         *
         * @throws NullPointerException If <code>resolver</code> is
         * <code>null</code>.
         */
        public void setXPathFunctionResolver(XPathFunctionResolver resolver) {

            // resolver cannot be null
            if (resolver == null) {
                String fmsg = XSLMessages.createXPATHMessage(
                        XPATHErrorResources.ER_NULL_XPATH_FUNCTION_RESOLVER,
                        new Object[] {  CLASS_NAME } );
                throw new NullPointerException( fmsg );
            }

            xPathFunctionResolver = resolver;
        }

        /**
         * <p>Establish a default variable resolver.</p>
         *
         * <p>Any <code>XPath</code> objects constructed from this factory will use
         * the specified resolver by default.</p>
         *
         * <p>A <code>NullPointerException</code> is thrown if <code>resolver</code> is <code>null</code>.</p>
         *
         * @param resolver Variable resolver.
         *
         *  @throws NullPointerException If <code>resolver</code> is
         * <code>null</code>.
         */
        public void setXPathVariableResolver(XPathVariableResolver resolver) {

                // resolver cannot be null
                if (resolver == null) {
                    String fmsg = XSLMessages.createXPATHMessage(
                            XPATHErrorResources.ER_NULL_XPATH_VARIABLE_RESOLVER,
                            new Object[] {  CLASS_NAME } );
                    throw new NullPointerException( fmsg );
                }

                xPathVariableResolver = resolver;
        }
}
