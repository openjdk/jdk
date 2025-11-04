/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xpath.internal.jaxp;

import com.sun.org.apache.xml.internal.utils.WrappedRuntimeException;
import com.sun.org.apache.xpath.internal.objects.XObject;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

import jdk.xml.internal.JdkXmlConfig;
import jdk.xml.internal.JdkXmlFeatures;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * The XPathExpression interface encapsulates a (compiled) XPath expression.
 *
 * @author  Ramesh Mandava
 * @LastModified: May 2025
 */
public class XPathExpressionImpl extends XPathImplUtil implements XPathExpression {

    private com.sun.org.apache.xpath.internal.XPath xpath;

    /** Protected constructor to prevent direct instantiation; use compile()
     * from the context.
     */
    protected XPathExpressionImpl() {
        this(null, null, null, null, false, JdkXmlConfig.getInstance(false).getXMLFeatures(true));
    };

    protected XPathExpressionImpl(com.sun.org.apache.xpath.internal.XPath xpath,
            JAXPPrefixResolver prefixResolver,
            XPathFunctionResolver functionResolver,
            XPathVariableResolver variableResolver) {
        this(xpath, prefixResolver, functionResolver, variableResolver,
             false, JdkXmlConfig.getInstance(false).getXMLFeatures(true));
    };

    protected XPathExpressionImpl(com.sun.org.apache.xpath.internal.XPath xpath,
            JAXPPrefixResolver prefixResolver,XPathFunctionResolver functionResolver,
            XPathVariableResolver variableResolver, boolean featureSecureProcessing,
            JdkXmlFeatures featureManager) {
        this.xpath = xpath;
        this.prefixResolver = prefixResolver;
        this.functionResolver = functionResolver;
        this.variableResolver = variableResolver;
        this.featureSecureProcessing = featureSecureProcessing;
        this.overrideDefaultParser = featureManager.getFeature(
                JdkXmlFeatures.XmlFeature.JDK_OVERRIDE_PARSER);
        this.featureManager = featureManager;
    };

    public void setXPath (com.sun.org.apache.xpath.internal.XPath xpath) {
        this.xpath = xpath;
    }

    public Object eval(Object item, QName returnType)
            throws TransformerException {
        XObject resultObject = eval(item, xpath);
        return getResultAsType(resultObject, returnType);
    }

    @Override
    public Object evaluate(Object item, QName returnType)
        throws XPathExpressionException {
        isSupported(returnType);
        try {
            return eval(item, returnType);
        } catch (TransformerException te) {
            Throwable nestedException = te.getException();
            if (nestedException instanceof XPathFunctionException) {
                throw (XPathFunctionException)nestedException;
            } else {
                // For any other exceptions we need to throw
                // XPathExpressionException (as per spec)
                throw new XPathExpressionException(te);
            }
        } catch (RuntimeException re) {
            if (re instanceof WrappedRuntimeException) {
                throw new XPathExpressionException(((WrappedRuntimeException)re).getException());
            }
            throw new XPathExpressionException(re);
        }
    }


    @Override
    public String evaluate(Object item)
        throws XPathExpressionException {
        return (String)this.evaluate(item, XPathConstants.STRING);
    }

    @Override
    public Object evaluate(InputSource source, QName returnType)
        throws XPathExpressionException {
        requireNonNull(source, "Source");
        isSupported (returnType);
        try {
            Document document = getDocument(source);
            return eval(document, returnType);
        } catch (TransformerException e) {
            throw new XPathExpressionException(e);
        } catch (RuntimeException re) {
            if (re instanceof WrappedRuntimeException) {
                throw new XPathExpressionException(((WrappedRuntimeException)re).getException());
            }
            throw new XPathExpressionException(re);
        }
    }

    @Override
    public String evaluate(InputSource source)
        throws XPathExpressionException {
        return (String)this.evaluate(source, XPathConstants.STRING);
    }

    @Override
    public <T>T evaluateExpression(Object item, Class<T> type)
        throws XPathExpressionException {
        isSupportedClassType(type);

        try {
            XObject resultObject = eval(item, xpath);
            if (type == XPathEvaluationResult.class) {
                return getXPathResult(resultObject, type);
            } else {
                return XPathResultImpl.getValue(resultObject, type);
            }

        } catch (TransformerException te) {
            throw new XPathExpressionException(te);
        } catch (RuntimeException re) {
            if (re instanceof WrappedRuntimeException) {
                throw new XPathExpressionException(((WrappedRuntimeException)re).getException());
            }
            throw new XPathExpressionException(re);
        }
    }

    @Override
    public XPathEvaluationResult<?> evaluateExpression(Object item)
        throws XPathExpressionException {
        return evaluateExpression(item, XPathEvaluationResult.class);
    }

    @Override
    public <T>T  evaluateExpression(InputSource source, Class<T> type)
            throws XPathExpressionException {
        Document document = getDocument(source);
        return evaluateExpression(document, type);
    }

    @Override
    public XPathEvaluationResult<?> evaluateExpression(InputSource source)
        throws XPathExpressionException {
        return evaluateExpression(source, XPathEvaluationResult.class);
    }
 }
