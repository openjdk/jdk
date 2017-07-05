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
// $Id: XPathExpressionImpl.java,v 1.3 2005/09/27 09:40:43 sunithareddy Exp $

package com.sun.org.apache.xpath.internal.jaxp;

import com.sun.org.apache.xpath.internal.*;
import javax.xml.transform.TransformerException;

import com.sun.org.apache.xpath.internal.objects.XObject;
import com.sun.org.apache.xml.internal.dtm.DTM;
import com.sun.org.apache.xml.internal.utils.PrefixResolver;
import com.sun.org.apache.xpath.internal.res.XPATHErrorResources;
import com.sun.org.apache.xalan.internal.res.XSLMessages;
import com.sun.org.apache.xalan.internal.utils.FactoryImpl;
import com.sun.org.apache.xalan.internal.utils.FeatureManager;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.traversal.NodeIterator;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.xml.sax.InputSource;

/**
 * The XPathExpression interface encapsulates a (compiled) XPath expression.
 *
 * @version $Revision: 1.10 $
 * @author  Ramesh Mandava
 */
public class XPathExpressionImpl  implements javax.xml.xpath.XPathExpression{

    private XPathFunctionResolver functionResolver;
    private XPathVariableResolver variableResolver;
    private JAXPPrefixResolver prefixResolver;
    private com.sun.org.apache.xpath.internal.XPath xpath;

    // By default Extension Functions are allowed in XPath Expressions. If
    // Secure Processing Feature is set on XPathFactory then the invocation of
    // extensions function need to throw XPathFunctionException
    private boolean featureSecureProcessing = false;

    private boolean useServicesMechanism = true;

    private final FeatureManager featureManager;

    /** Protected constructor to prevent direct instantiation; use compile()
     * from the context.
     */
    protected XPathExpressionImpl() {
        this(null, null, null, null,
             false, true, new FeatureManager());
    };

    protected XPathExpressionImpl(com.sun.org.apache.xpath.internal.XPath xpath,
            JAXPPrefixResolver prefixResolver,
            XPathFunctionResolver functionResolver,
            XPathVariableResolver variableResolver ) {
        this(xpath, prefixResolver, functionResolver, variableResolver,
             false, true, new FeatureManager());
    };

    protected XPathExpressionImpl(com.sun.org.apache.xpath.internal.XPath xpath,
            JAXPPrefixResolver prefixResolver,XPathFunctionResolver functionResolver,
            XPathVariableResolver variableResolver, boolean featureSecureProcessing,
            boolean useServicesMechanism, FeatureManager featureManager ) {
        this.xpath = xpath;
        this.prefixResolver = prefixResolver;
        this.functionResolver = functionResolver;
        this.variableResolver = variableResolver;
        this.featureSecureProcessing = featureSecureProcessing;
        this.useServicesMechanism = useServicesMechanism;
        this.featureManager = featureManager;
    };

    public void setXPath (com.sun.org.apache.xpath.internal.XPath xpath ) {
        this.xpath = xpath;
    }

    public Object eval(Object item, QName returnType)
            throws javax.xml.transform.TransformerException {
        XObject resultObject = eval ( item );
        return getResultAsType( resultObject, returnType );
    }

    private XObject eval ( Object contextItem )
            throws javax.xml.transform.TransformerException {
        com.sun.org.apache.xpath.internal.XPathContext xpathSupport = null;
        if ( functionResolver != null ) {
            JAXPExtensionsProvider jep = new JAXPExtensionsProvider(
                    functionResolver, featureSecureProcessing, featureManager );
            xpathSupport = new com.sun.org.apache.xpath.internal.XPathContext( jep );
        } else {
            xpathSupport = new com.sun.org.apache.xpath.internal.XPathContext();
        }

        xpathSupport.setVarStack(new JAXPVariableStack(variableResolver));
        XObject xobj = null;

        Node contextNode = (Node)contextItem;
        // We always need to have a ContextNode with Xalan XPath implementation
        // To allow simple expression evaluation like 1+1 we are setting
        // dummy Document as Context Node

        if ( contextNode == null )
            xobj = xpath.execute(xpathSupport, DTM.NULL, prefixResolver);
        else
            xobj = xpath.execute(xpathSupport, contextNode, prefixResolver);

        return xobj;
    }


    /**
     * <p>Evaluate the compiled XPath expression in the specified context and
     *  return the result as the specified type.</p>
     *
     * <p>See "Evaluation of XPath Expressions" section of JAXP 1.3 spec
     * for context item evaluation,
     * variable, function and QName resolution and return type conversion.</p>
     *
     * <p>If <code>returnType</code> is not one of the types defined
     * in {@link XPathConstants},
     * then an <code>IllegalArgumentException</code> is thrown.</p>
     *
     * <p>If a <code>null</code> value is provided for
     * <code>item</code>, an empty document will be used for the
     * context.
     * If <code>returnType</code> is <code>null</code>, then a
     * <code>NullPointerException</code> is thrown.</p>
     *
     * @param item The starting context (node or node list, for example).
     * @param returnType The desired return type.
     *
     * @return The <code>Object</code> that is the result of evaluating the
     * expression and converting the result to
     *   <code>returnType</code>.
     *
     * @throws XPathExpressionException If the expression cannot be evaluated.
     * @throws IllegalArgumentException If <code>returnType</code> is not one
     * of the types defined in {@link XPathConstants}.
     * @throws NullPointerException If  <code>returnType</code> is
     * <code>null</code>.
     */
    public Object evaluate(Object item, QName returnType)
        throws XPathExpressionException {
        //Validating parameters to enforce constraints defined by JAXP spec
        if ( returnType == null ) {
           //Throwing NullPointerException as defined in spec
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_ARG_CANNOT_BE_NULL,
                    new Object[] {"returnType"} );
            throw new NullPointerException( fmsg );
        }
        // Checking if requested returnType is supported. returnType need to be
        // defined in XPathConstants
        if ( !isSupported ( returnType ) ) {
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_UNSUPPORTED_RETURN_TYPE,
                    new Object[] { returnType.toString() } );
            throw new IllegalArgumentException ( fmsg );
        }
        try {
            return eval( item, returnType);
        } catch ( java.lang.NullPointerException npe ) {
            // If VariableResolver returns null Or if we get
            // NullPointerException at this stage for some other reason
            // then we have to reurn XPathException
            throw new XPathExpressionException ( npe );
        } catch ( javax.xml.transform.TransformerException te ) {
            Throwable nestedException = te.getException();
            if ( nestedException instanceof javax.xml.xpath.XPathFunctionException ) {
                throw (javax.xml.xpath.XPathFunctionException)nestedException;
            } else {
                // For any other exceptions we need to throw
                // XPathExpressionException ( as per spec )
                throw new XPathExpressionException( te);
            }
        }

    }

    /**
     * <p>Evaluate the compiled XPath expression in the specified context and
     * return the result as a <code>String</code>.</p>
     *
     * <p>This method calls {@link #evaluate(Object item, QName returnType)}
     * with a <code>returnType</code> of
     * {@link XPathConstants#STRING}.</p>
     *
     * <p>See "Evaluation of XPath Expressions" section of JAXP 1.3 spec
     *  for context item evaluation,
     * variable, function and QName resolution and return type conversion.</p>
     *
     * <p>If a <code>null</code> value is provided for
     * <code>item</code>, an empty document will be used for the
     * context.
     *
     * @param item The starting context (node or node list, for example).
     *
     * @return The <code>String</code> that is the result of evaluating the
     * expression and converting the result to a
     *   <code>String</code>.
     *
     * @throws XPathExpressionException If the expression cannot be evaluated.
     */
    public String evaluate(Object item)
        throws XPathExpressionException {
        return (String)this.evaluate( item, XPathConstants.STRING );
    }



    static DocumentBuilderFactory dbf = null;
    static DocumentBuilder db = null;
    static Document d = null;

    /**
     * <p>Evaluate the compiled XPath expression in the context of the
     * specified <code>InputSource</code> and return the result as the
     *  specified type.</p>
     *
     * <p>This method builds a data model for the {@link InputSource} and calls
     * {@link #evaluate(Object item, QName returnType)} on the resulting
     * document object.</p>
     *
     * <p>See "Evaluation of XPath Expressions" section of JAXP 1.3 spec
     *  for context item evaluation,
     * variable, function and QName resolution and return type conversion.</p>
     *
     * <p>If <code>returnType</code> is not one of the types defined in
     * {@link XPathConstants},
     * then an <code>IllegalArgumentException</code> is thrown.</p>
     *
     *<p>If <code>source</code> or <code>returnType</code> is <code>null</code>,
     * then a <code>NullPointerException</code> is thrown.</p>
     *
     * @param source The <code>InputSource</code> of the document to evaluate
     * over.
     * @param returnType The desired return type.
     *
     * @return The <code>Object</code> that is the result of evaluating the
     * expression and converting the result to
     *   <code>returnType</code>.
     *
     * @throws XPathExpressionException If the expression cannot be evaluated.
     * @throws IllegalArgumentException If <code>returnType</code> is not one
     * of the types defined in {@link XPathConstants}.
     * @throws NullPointerException If  <code>source</code> or
     * <code>returnType</code> is <code>null</code>.
     */
    public Object evaluate(InputSource source, QName returnType)
        throws XPathExpressionException {
        if ( ( source == null ) || ( returnType == null ) ) {
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
                    null );
            throw new NullPointerException ( fmsg );
        }
        // Checking if requested returnType is supported. returnType need to be
        // defined in XPathConstants
        if ( !isSupported ( returnType ) ) {
            String fmsg = XSLMessages.createXPATHMessage(
                    XPATHErrorResources.ER_UNSUPPORTED_RETURN_TYPE,
                    new Object[] { returnType.toString() } );
            throw new IllegalArgumentException ( fmsg );
        }
        try {
            if ( dbf == null ) {
                dbf = FactoryImpl.getDOMFactory(useServicesMechanism);
                dbf.setNamespaceAware( true );
                dbf.setValidating( false );
            }
            db = dbf.newDocumentBuilder();
            Document document = db.parse( source );
            return eval(  document, returnType );
        } catch ( Exception e ) {
            throw new XPathExpressionException ( e );
        }
    }

    /**
     * <p>Evaluate the compiled XPath expression in the context of the specified <code>InputSource</code> and return the result as a
     * <code>String</code>.</p>
     *
     * <p>This method calls {@link #evaluate(InputSource source, QName returnType)} with a <code>returnType</code> of
     * {@link XPathConstants#STRING}.</p>
     *
     * <p>See "Evaluation of XPath Expressions" section of JAXP 1.3 spec
     * for context item evaluation,
     * variable, function and QName resolution and return type conversion.</p>
     *
     * <p>If <code>source</code> is <code>null</code>, then a <code>NullPointerException</code> is thrown.</p>
     *
     * @param source The <code>InputSource</code> of the document to evaluate over.
     *
     * @return The <code>String</code> that is the result of evaluating the expression and converting the result to a
     *   <code>String</code>.
     *
     * @throws XPathExpressionException If the expression cannot be evaluated.
     * @throws NullPointerException If  <code>source</code> is <code>null</code>.
     */
    public String evaluate(InputSource source)
        throws XPathExpressionException {
        return (String)this.evaluate( source, XPathConstants.STRING );
    }

    private boolean isSupported( QName returnType ) {
        // XPathConstants.STRING
        if ( ( returnType.equals( XPathConstants.STRING ) ) ||
             ( returnType.equals( XPathConstants.NUMBER ) ) ||
             ( returnType.equals( XPathConstants.BOOLEAN ) ) ||
             ( returnType.equals( XPathConstants.NODE ) ) ||
             ( returnType.equals( XPathConstants.NODESET ) )  ) {

            return true;
        }
        return false;
     }

     private Object getResultAsType( XObject resultObject, QName returnType )
        throws javax.xml.transform.TransformerException {
        // XPathConstants.STRING
        if ( returnType.equals( XPathConstants.STRING ) ) {
            return resultObject.str();
        }
        // XPathConstants.NUMBER
        if ( returnType.equals( XPathConstants.NUMBER ) ) {
            return new Double ( resultObject.num());
        }
        // XPathConstants.BOOLEAN
        if ( returnType.equals( XPathConstants.BOOLEAN ) ) {
            return new Boolean( resultObject.bool());
        }
        // XPathConstants.NODESET ---ORdered, UNOrdered???
        if ( returnType.equals( XPathConstants.NODESET ) ) {
            return resultObject.nodelist();
        }
        // XPathConstants.NODE
        if ( returnType.equals( XPathConstants.NODE ) ) {
            NodeIterator ni = resultObject.nodeset();
            //Return the first node, or null
            return ni.nextNode();
        }
        // If isSupported check is already done then the execution path
        // shouldn't come here. Being defensive
        String fmsg = XSLMessages.createXPATHMessage(
                XPATHErrorResources.ER_UNSUPPORTED_RETURN_TYPE,
                new Object[] { returnType.toString()});
        throw new IllegalArgumentException ( fmsg );
    }

 }
