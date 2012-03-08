/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999-2003 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.dtd;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;

/**
 * This allows the validator to correctlyhandle XML 1.1
 * documents.
 *
 * @xerces.internal
 *
 * @author Neil Graham
 */
public class XML11DTDValidator extends XMLDTDValidator {

    //
    // Constants
    //

    protected final static String DTD_VALIDATOR_PROPERTY =
        Constants.XERCES_PROPERTY_PREFIX+Constants.DTD_VALIDATOR_PROPERTY;

    //
    // Constructors
    //

    /** Default constructor. */
    public XML11DTDValidator() {

        super();
    } // <init>()

    // overridden so that this class has access to the same
    // grammarBucket as the corresponding DTDProcessor
    // will try and use...
    public void reset(XMLComponentManager manager) {
        XMLDTDValidator curr = null;
        if((curr = (XMLDTDValidator)manager.getProperty(DTD_VALIDATOR_PROPERTY)) != null &&
                curr != this) {
            fGrammarBucket = curr.getGrammarBucket();
        }
        super.reset(manager);
    } //reset(XMLComponentManager)

    protected void init() {
        if(fValidation || fDynamicValidation) {
            super.init();
            // now overwrite some entries in parent:

            try {
                fValID       = fDatatypeValidatorFactory.getBuiltInDV("XML11ID");
                fValIDRef    = fDatatypeValidatorFactory.getBuiltInDV("XML11IDREF");
                fValIDRefs   = fDatatypeValidatorFactory.getBuiltInDV("XML11IDREFS");
                fValNMTOKEN  = fDatatypeValidatorFactory.getBuiltInDV("XML11NMTOKEN");
                fValNMTOKENS = fDatatypeValidatorFactory.getBuiltInDV("XML11NMTOKENS");

            }
            catch (Exception e) {
                // should never happen
                e.printStackTrace(System.err);
            }
        }
    } // init()

} // class XML11DTDValidator
