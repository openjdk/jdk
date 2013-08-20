/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2003 The Apache Software Foundation.
 * All rights reserved.
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

package com.sun.org.apache.xerces.internal.util;
import com.sun.org.apache.xerces.internal.impl.Constants;
/**
 * This class is a container for parser settings that relate to
 * security, or more specifically, it is intended to be used to prevent denial-of-service
 * attacks from being launched against a system running Xerces.
 * Any component that is aware of a denial-of-service attack that can arise
 * from its processing of a certain kind of document may query its Component Manager
 * for the property (http://apache.org/xml/properties/security-manager)
 * whose value will be an instance of this class.
 * If no value has been set for the property, the component should proceed in the "usual" (spec-compliant)
 * manner.  If a value has been set, then it must be the case that the component in
 * question needs to know what method of this class to query.  This class
 * will provide defaults for all known security issues, but will also provide
 * setters so that those values can be tailored by applications that care.
 *
 * @author  Neil Graham, IBM
 *
 */
public final class SecurityManager {

    //
    // Constants
    //

    // default value for entity expansion limit
    private final static int DEFAULT_ENTITY_EXPANSION_LIMIT = 64000;

    /** Default value of number of nodes created. **/
    private final static int DEFAULT_MAX_OCCUR_NODE_LIMIT = 5000;

    //
    // Data
    //

        private final static int DEFAULT_ELEMENT_ATTRIBUTE_LIMIT = 10000;

    /** Entity expansion limit. **/
    private int entityExpansionLimit;

    /** W3C XML Schema maxOccurs limit. **/
    private int maxOccurLimit;

        private int fElementAttributeLimit;
    // default constructor.  Establishes default values for
    // all known security holes.
    /**
     * Default constructor.  Establishes default values
     * for known security vulnerabilities.
     */
    public SecurityManager() {
        entityExpansionLimit = DEFAULT_ENTITY_EXPANSION_LIMIT;
        maxOccurLimit = DEFAULT_MAX_OCCUR_NODE_LIMIT ;
                fElementAttributeLimit = DEFAULT_ELEMENT_ATTRIBUTE_LIMIT;
                //We are reading system properties only once ,
                //at the time of creation of this object ,
                readSystemProperties();
    }

    /**
     * <p>Sets the number of entity expansions that the
     * parser should permit in a document.</p>
     *
     * @param limit the number of entity expansions
     * permitted in a document
     */
    public void setEntityExpansionLimit(int limit) {
        entityExpansionLimit = limit;
    }

    /**
     * <p>Returns the number of entity expansions
     * that the parser permits in a document.</p>
     *
     * @return the number of entity expansions
     * permitted in a document
     */
    public int getEntityExpansionLimit() {
        return entityExpansionLimit;
    }

    /**
     * <p>Sets the limit of the number of content model nodes
     * that may be created when building a grammar for a W3C
     * XML Schema that contains maxOccurs attributes with values
     * other than "unbounded".</p>
     *
     * @param limit the maximum value for maxOccurs other
     * than "unbounded"
     */
    public void setMaxOccurNodeLimit(int limit){
        maxOccurLimit = limit;
    }

    /**
     * <p>Returns the limit of the number of content model nodes
     * that may be created when building a grammar for a W3C
     * XML Schema that contains maxOccurs attributes with values
     * other than "unbounded".</p>
     *
     * @return the maximum value for maxOccurs other
     * than "unbounded"
     */
    public int getMaxOccurNodeLimit(){
        return maxOccurLimit;
    }

    public int getElementAttrLimit(){
                return fElementAttributeLimit;
        }

        public void setElementAttrLimit(int limit){
                fElementAttributeLimit = limit;
        }

        private void readSystemProperties(){

                try {
                        String value = System.getProperty(Constants.ENTITY_EXPANSION_LIMIT);
                        if(value != null && !value.equals("")){
                                entityExpansionLimit = Integer.parseInt(value);
                                if (entityExpansionLimit < 0)
                                        entityExpansionLimit = DEFAULT_ENTITY_EXPANSION_LIMIT;
                        }
                        else
                                entityExpansionLimit = DEFAULT_ENTITY_EXPANSION_LIMIT;
                }catch(Exception ex){}

                try {
                        String value = System.getProperty(Constants.MAX_OCCUR_LIMIT);
                        if(value != null && !value.equals("")){
                                maxOccurLimit = Integer.parseInt(value);
                                if (maxOccurLimit < 0)
                                        maxOccurLimit = DEFAULT_MAX_OCCUR_NODE_LIMIT;
                        }
                        else
                                maxOccurLimit = DEFAULT_MAX_OCCUR_NODE_LIMIT;
                }catch(Exception ex){}

                try {
                        String value = System.getProperty(Constants.ELEMENT_ATTRIBUTE_LIMIT);
                        if(value != null && !value.equals("")){
                                fElementAttributeLimit = Integer.parseInt(value);
                                if ( fElementAttributeLimit < 0)
                                        fElementAttributeLimit = DEFAULT_ELEMENT_ATTRIBUTE_LIMIT;
                        }
                        else
                                fElementAttributeLimit = DEFAULT_ELEMENT_ATTRIBUTE_LIMIT;

                }catch(Exception ex){}

        }

} // class SecurityManager
