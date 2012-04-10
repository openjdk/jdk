/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.sun.org.apache.xerces.internal.xinclude;


import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Stack;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.XMLErrorReporter;
import com.sun.org.apache.xerces.internal.impl.dtd.DTDGrammar;
import com.sun.org.apache.xerces.internal.util.ParserConfigurationSettings;
import com.sun.org.apache.xerces.internal.xni.Augmentations;
import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.xni.XMLAttributes;
import com.sun.org.apache.xerces.internal.xni.XMLDocumentHandler;
import com.sun.org.apache.xerces.internal.xni.XMLLocator;
import com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier;
import com.sun.org.apache.xerces.internal.xni.XMLString;
import com.sun.org.apache.xerces.internal.xni.XNIException;
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarDescription;
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarPool;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;
import com.sun.org.apache.xerces.internal.xni.parser.XMLConfigurationException;
import com.sun.org.apache.xerces.internal.xni.parser.XMLDocumentSource;
import com.sun.org.apache.xerces.internal.xni.parser.XMLEntityResolver;
import com.sun.org.apache.xerces.internal.impl.dtd.XMLDTDValidator;
/**
 * @author Arun Yadav, Sun Microsystem
 */
public class XPointerElementHandler implements XPointerSchema {


    // recognized features and properties

    /** Property identifier: error handler. */
    protected static final String ERROR_REPORTER =
    Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY;

    /** Property identifier: grammar pool . */
    protected static final String GRAMMAR_POOL =
    Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;

    /** Property identifier: grammar pool . */
    protected static final String ENTITY_RESOLVER =
    Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_RESOLVER_PROPERTY;

    protected static final String XPOINTER_SCHEMA =
    Constants.XERCES_PROPERTY_PREFIX + Constants.XPOINTER_SCHEMA_PROPERTY;

    /** Recognized features. */
    private static final String[] RECOGNIZED_FEATURES = {
    };

    /** Feature defaults. */
    private static final Boolean[] FEATURE_DEFAULTS = {
    };

    /** Recognized properties. */

    private static final String[] RECOGNIZED_PROPERTIES =
    { ERROR_REPORTER, GRAMMAR_POOL, ENTITY_RESOLVER, XPOINTER_SCHEMA };

    /** Property defaults. */
    private static final Object[] PROPERTY_DEFAULTS = { null, null, null,null };

    // Data

    protected XMLDocumentHandler fDocumentHandler;
    protected XMLDocumentSource fDocumentSource;

    protected XIncludeHandler fParentXIncludeHandler;

    protected XMLLocator fDocLocation;
    protected XIncludeNamespaceSupport fNamespaceContext;
    protected XMLErrorReporter fErrorReporter;
    protected XMLGrammarPool fGrammarPool;
    protected XMLGrammarDescription fGrammarDesc;
    protected DTDGrammar fDTDGrammar;
    protected XMLEntityResolver fEntityResolver;
    protected ParserConfigurationSettings fSettings;
    //protected String fPointerSchema;
    protected StringBuffer fPointer;
    private int elemCount = 0;


    // The current element depth.
    // This is used to access the appropriate level of the following stacks.
    private int fDepth;

    // The depth of the first element to actually be part of the result infoset.
    // This will normally be 1, but it could be larger when the top-level item
    // is an include, and processing goes to the fallback.
    private int fRootDepth;

    // this value must be at least 1
    private static final int INITIAL_SIZE = 8;


    // Used to ensure that fallbacks are always children of include elements,
    // and that include elements are never children of other include elements.
    // An index contains true if the ancestor of the current element which resides
    // at that depth was an include element.
    private boolean[] fSawInclude = new boolean[INITIAL_SIZE];


    // Ensures that only one fallback element can be at a single depth.
    // An index contains true if we have seen any fallback elements at that depth,
    // and it is only reset to false when the end tag of the parent is encountered.
    private boolean[] fSawFallback = new boolean[INITIAL_SIZE];


    // The state of the processor at each given depth.
    private int[] fState = new int[INITIAL_SIZE];

    QName foundElement = null;
    boolean skip = false;
    // Constructors

    public XPointerElementHandler() {


        fDepth = 0;
        fRootDepth = 0;
        fSawFallback[fDepth] = false;
        fSawInclude[fDepth] = false;
        fSchemaName="element";


    }

    // START OF IMPLEMENTATION OF XMLComponent methods //////

    public void reset(){
        elemCount =0;
        fPointerToken = null;
        fCurrentTokenint=0;
        fCurrentTokenString=null;
        fCurrentTokenType=0 ;
        fElementCount =0;
        fCurrentToken =0;
        includeElement = false;
        foundElement = null;
        skip = false;
        fSubResourceIdentified=false;
    }

    public void reset(XMLComponentManager componentManager)
    throws XNIException {
        fNamespaceContext = null;
        elemCount =0;
        fDepth = 0;
        fRootDepth = 0;
        fPointerToken = null;
        fCurrentTokenint=0;
        fCurrentTokenString=null;
        fCurrentTokenType=0 ;
        foundElement = null;
        includeElement = false;
        skip = false;
        fSubResourceIdentified=false;




        try {
            setErrorReporter(
            (XMLErrorReporter)componentManager.getProperty(ERROR_REPORTER));
        }
        catch (XMLConfigurationException e) {
            fErrorReporter = null;
        }
        try {
            fGrammarPool =
            (XMLGrammarPool)componentManager.getProperty(GRAMMAR_POOL);
        }
        catch (XMLConfigurationException e) {
            fGrammarPool = null;
        }
        try {
            fEntityResolver =
            (XMLEntityResolver)componentManager.getProperty(
            ENTITY_RESOLVER);
        }
        catch (XMLConfigurationException e) {
            fEntityResolver = null;
        }

        fSettings = new ParserConfigurationSettings();

        Enumeration xercesFeatures = Constants.getXercesFeatures();
        while (xercesFeatures.hasMoreElements()) {
            String featureId = (String)xercesFeatures.nextElement();
            fSettings.addRecognizedFeatures(new String[] { featureId });
            try {
                fSettings.setFeature(
                featureId,
                componentManager.getFeature(featureId));
            }
            catch (XMLConfigurationException e) {
                // componentManager doesn't support this feature,
                // so we won't worry about it
            }
        }
/*              try{
          dtdValidator =   (XMLDTDValidator)componentManager.getProperty( Constants.XERCES_PROPERTY_PREFIX + Constants.DTD_VALIDATOR_PROPERTY);
                }Catch(Exception ex){
                        ex.printStackTrace();
                }*/

    } // reset(XMLComponentManager)

    /**
     * Returns a list of feature identifiers that are recognized by
     * this component. This method may return null if no features
     * are recognized by this component.
     */
    public String[] getRecognizedFeatures() {
        return RECOGNIZED_FEATURES;
    } // getRecognizedFeatures():String[]

    /**
     * Sets the state of a feature. This method is called by the component
     * manager any time after reset when a feature changes state.
     * <p>
     * <strong>Note:</strong> Components should silently ignore features
     * that do not affect the operation of the component.
     *
     * @param featureId The feature identifier.
     * @param state     The state of the feature.
     *
     * @throws SAXNotRecognizedException The component should not throw
     *                                   this exception.
     * @throws SAXNotSupportedException The component should not throw
     *                                  this exception.
     */
    public void setFeature(String featureId, boolean state)
    throws XMLConfigurationException {
        if (fSettings != null) {
            fSettings.setFeature(featureId, state);
        }

    } // setFeature(String,boolean)

    /**
     * Returns a list of property identifiers that are recognized by
     * this component. This method may return null if no properties
     * are recognized by this component.
     */
    public String[] getRecognizedProperties() {
        return RECOGNIZED_PROPERTIES;
    } // getRecognizedProperties():String[]

    /**
     * Sets the value of a property. This method is called by the component
     * manager any time after reset when a property changes value.
     * <p>
     * <strong>Note:</strong> Components should silently ignore properties
     * that do not affect the operation of the component.
     *
     * @param propertyId The property identifier.
     * @param value      The value of the property.
     *
     * @throws SAXNotRecognizedException The component should not throw
     *                                   this exception.
     * @throws SAXNotSupportedException The component should not throw
     *                                  this exception.
     */
    public void setProperty(String propertyId, Object value)
    throws XMLConfigurationException {
        if (propertyId.equals(ERROR_REPORTER)) {
            setErrorReporter((XMLErrorReporter)value);
        }
        if (propertyId.equals(GRAMMAR_POOL)) {
            fGrammarPool = (XMLGrammarPool)value;
        }
        if (propertyId.equals(ENTITY_RESOLVER)) {
            fEntityResolver = (XMLEntityResolver)value;
        }

    } // setProperty(String,Object)

    /**
     * Returns the default state for a feature, or null if this
     * component does not want to report a default value for this
     * feature.
     *
     * @param featureId The feature identifier.
     *
     * @since Xerces 2.2.0
     */
    public Boolean getFeatureDefault(String featureId) {
        for (int i = 0; i < RECOGNIZED_FEATURES.length; i++) {
            if (RECOGNIZED_FEATURES[i].equals(featureId)) {
                return FEATURE_DEFAULTS[i];
            }
        }
        return null;
    } // getFeatureDefault(String):Boolean

    /**
     * Returns the default state for a property, or null if this
     * component does not want to report a default value for this
     * property.
     *
     * @param propertyId The property identifier.
     *
     * @since Xerces 2.2.0
     */
    public Object getPropertyDefault(String propertyId) {
        for (int i = 0; i < RECOGNIZED_PROPERTIES.length; i++) {
            if (RECOGNIZED_PROPERTIES[i].equals(propertyId)) {
                return PROPERTY_DEFAULTS[i];
            }
        }
        return null;
    } // getPropertyDefault(String):Object

    private void setErrorReporter(XMLErrorReporter reporter) {
        fErrorReporter = reporter;
        if (fErrorReporter != null) {
            fErrorReporter.putMessageFormatter(
            XIncludeMessageFormatter.XINCLUDE_DOMAIN,
            new XIncludeMessageFormatter());
        }
    }
    ///////// END OF IMPLEMENTATION  OF XMLComponents methods. //////////



    //////// START OF  IMPLEMENTATION OF XMLDOCUMENTSOURCE INTERFACE /////////

    public void setDocumentHandler(XMLDocumentHandler handler) {
        fDocumentHandler = handler;
    }

    public XMLDocumentHandler getDocumentHandler() {
        return fDocumentHandler;
    }

    ///////   END OF IMPLENTATION OF XMLDOCUMENTSOURCE INTERFACE ///////////




    /////////////// Implementation of XPointerSchema Methods //////////////////////

    String fSchemaName;
    String fSchemaPointer;
    boolean fSubResourceIdentified;
    /**
     * set the Schema Name  eg element , xpointer
     */
    public void setXPointerSchemaName(String schemaName){
        fSchemaName = schemaName;
    }

    /**
     * Return  Schema Name  eg element , xpointer
     *
     */
    public String getXpointerSchemaName(){
        return fSchemaName;
    }

    /**
     * Parent Contenhandler for the this contenthandler.
     * // not sure about the parameter type. It can be Contenthandler instead of Object type.
     */
    public void setParent(Object parent){
        fParentXIncludeHandler = (XIncludeHandler)parent;
    }


    /**
     * return the Parent Contenthandler
     */
    public Object getParent(){
        return fParentXIncludeHandler;
    }

    /**
     * Content of the XPointer Schema. Xpath to be resolved.
     */
    public void setXPointerSchemaPointer(String content){
        fSchemaPointer = content;
    }

    /**
     * Return the XPointer Schema.
     */
    public String getXPointerSchemaPointer(){
        return fSchemaPointer;
    }

    public boolean isSubResourceIndentified(){
        return fSubResourceIdentified;
    }

    ///////////End Implementation of XPointerSchema Methods //////////////////////



    //////////// Tokens Playground ///////////////////

    Stack fPointerToken = new Stack();
    int  fCurrentTokenint=0;
    String fCurrentTokenString=null;
    int fCurrentTokenType=0 ;// 0 Notype; 1 for integer; 2 for string.

    public void getTokens(){
        fSchemaPointer = fSchemaPointer.substring(fSchemaPointer.indexOf("(")+1, fSchemaPointer.length());
        StringTokenizer st = new StringTokenizer(fSchemaPointer, "/");
        String tempToken;
        Integer integerToken =null;
        Stack tempPointerToken = new Stack();
        if(fPointerToken == null){
            fPointerToken = new Stack();
        }
        while(st.hasMoreTokens()){
            tempToken=st.nextToken();
            try {
                integerToken = Integer.valueOf(tempToken);
                tempPointerToken.push(integerToken);
            }catch(NumberFormatException e){
                tempPointerToken.push(tempToken);
            }
        }
        while(!tempPointerToken.empty()){
            fPointerToken.push(tempPointerToken.pop());
        }
    }//getTokens


    public boolean hasMoreToken(){
        if(fPointerToken.isEmpty())
            return false;
        else
            return true;
    }

    public boolean getNextToken(){
        Object currentToken;
        if (!fPointerToken.isEmpty()){
            currentToken = fPointerToken.pop();
            if(currentToken instanceof Integer){
                fCurrentTokenint = ((Integer)currentToken).intValue();
                fCurrentTokenType = 1;
            }
            else{
                fCurrentTokenString = ((String)currentToken).toString();
                fCurrentTokenType = 2;
            }
            return true;
        }
        else {
            return false;
        }
    }

    private boolean isIdAttribute(XMLAttributes attributes,Augmentations augs, int index) {
        Object o = augs.getItem(Constants.ID_ATTRIBUTE);
        if( o instanceof Boolean )
            return ((Boolean)o).booleanValue();
        return "ID".equals(attributes.getType(index));
    }

    public boolean checkStringToken(QName element, XMLAttributes attributes){
        QName cacheQName = null;
        String id =null;
        String rawname =null;
        QName attrName = new QName();
        String attrType = null;
        String attrValue = null;
        int attrCount = attributes.getLength();
        for (int i = 0; i < attrCount; i++) {
            Augmentations aaugs = attributes.getAugmentations(i);
            attributes.getName(i,attrName);
            attrType = attributes.getType(i);
            attrValue = attributes.getValue(i);
            if(attrType != null && attrValue!= null && isIdAttribute(attributes,aaugs,i) && attrValue.equals(fCurrentTokenString)){
                if(hasMoreToken()){
                    fCurrentTokenType = 0;
                    fCurrentTokenString = null;
                    return true;
                }
                else{
                    foundElement = element;
                    includeElement = true;
                    fCurrentTokenType = 0;
                    fCurrentTokenString = null;
                    fSubResourceIdentified = true;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkIntegerToken(QName element){
        if(!skip){
            fElementCount++;
            if(fCurrentTokenint == fElementCount){
                if(hasMoreToken()){
                    fElementCount=0;
                    fCurrentTokenType = 0;
                    return true;
                }
                else{
                    foundElement = element;
                    includeElement = true;
                    fCurrentTokenType = 0;
                    fElementCount=0;
                    fSubResourceIdentified =true;
                    return true;
                }
            }else{
                addQName(element);
                skip = true;
                return false;
            }
        }
        return false;
    }

    public void addQName(QName element){
        QName cacheQName = new QName(element);
        ftempCurrentElement.push(cacheQName);
    }

    ///////////  END TOKEN PLAYGROUND ///////////////


    /////   START OF IMPLEMTATION OF XMLDocumentHandler methods //////////


    public void startDocument(XMLLocator locator, String encoding,
    NamespaceContext namespaceContext, Augmentations augs)
    throws XNIException {

        getTokens();
    }

    public void doctypeDecl(String rootElement, String publicId, String systemId,
    Augmentations augs)throws XNIException {
    }

    public void xmlDecl(String version, String encoding, String standalone,
    Augmentations augs) throws XNIException {
    }


    public void comment(XMLString text, Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.comment(text, augs);
        }
    }

    public void processingInstruction(String target, XMLString data,
    Augmentations augs) throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.processingInstruction(target, data, augs);

        }
    }

    Stack  ftempCurrentElement = new Stack();
    int fElementCount =0;
    int fCurrentToken ;
    boolean includeElement;


    public void startElement(QName element, XMLAttributes attributes,
    Augmentations augs)throws XNIException {

        boolean requiredToken=false;
        if(fCurrentTokenType == 0)
            getNextToken();
        if(fCurrentTokenType ==1)
            requiredToken = checkIntegerToken(element);
        else if (fCurrentTokenType ==2)
            requiredToken = checkStringToken(element, attributes);
        if(requiredToken && hasMoreToken())
            getNextToken();
        if(fDocumentHandler != null && includeElement){
            elemCount++;
            fDocumentHandler.startElement(element, attributes, augs);
        }

    }


    public void endElement(QName element, Augmentations augs)
    throws XNIException {
        if(includeElement && foundElement != null ){
            if(elemCount >0 )elemCount --;
            fDocumentHandler.endElement(element, augs);
            if(elemCount == 0)includeElement = false;

        }else if(!ftempCurrentElement.empty()){
            QName name = (QName)ftempCurrentElement.peek();
            if(name.equals(element)){
                ftempCurrentElement.pop();
                skip = false;
            }
        }
    }

    public void emptyElement(QName element, XMLAttributes attributes,
    Augmentations augs)throws XNIException {
        if(fDocumentHandler != null && includeElement){
            fDocumentHandler.emptyElement(element, attributes, augs);
        }
    }

    public void startGeneralEntity(String name, XMLResourceIdentifier resId,
    String encoding,
    Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.startGeneralEntity(name, resId, encoding, augs);
        }
    }

    public void textDecl(String version, String encoding, Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.textDecl(version, encoding, augs);
        }
    }

    public void endGeneralEntity(String name, Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null) {
            fDocumentHandler.endGeneralEntity(name, augs);
        }
    }

    public void characters(XMLString text, Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null  && includeElement) {
            fDocumentHandler.characters(text, augs);
        }
    }

    public void ignorableWhitespace(XMLString text, Augmentations augs)
    throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.ignorableWhitespace(text, augs);
        }
    }

    public void startCDATA(Augmentations augs) throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.startCDATA(augs);
        }
    }

    public void endCDATA(Augmentations augs) throws XNIException {
        if (fDocumentHandler != null && includeElement) {
            fDocumentHandler.endCDATA(augs);
        }
    }

    public void endDocument(Augmentations augs) throws XNIException {
    }

    public void setDocumentSource(XMLDocumentSource source) {
        fDocumentSource = source;
    }

    public XMLDocumentSource getDocumentSource() {
        return fDocumentSource;
    }


    protected void reportFatalError(String key) {
        this.reportFatalError(key, null);
    }

    protected void reportFatalError(String key, Object[] args) {
        if (fErrorReporter != null) {
            fErrorReporter.reportError(
            fDocLocation,
            XIncludeMessageFormatter.XINCLUDE_DOMAIN,
            key,
            args,
            XMLErrorReporter.SEVERITY_FATAL_ERROR);
        }
        // we won't worry about when error reporter is null, since there should always be
        // at least the default error reporter
    }



    // used to know whether to pass declarations to the document handler
    protected boolean isRootDocument() {
        return this.fParentXIncludeHandler == null;
    }


} // class XPointerElementhandler
