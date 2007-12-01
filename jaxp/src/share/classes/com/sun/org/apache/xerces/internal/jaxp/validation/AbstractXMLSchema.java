/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.jaxp.validation;

import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;

/**
 * <p>Abstract implementation of Schema for W3C XML Schemas.</p>
 *
 * @author Michael Glavassevich, IBM
 */
abstract class AbstractXMLSchema extends Schema implements
        XSGrammarPoolContainer {

    /*
     * Schema methods
     */

    /*
     * @see javax.xml.validation.Schema#newValidator()
     */
    public final Validator newValidator() {
        return new ValidatorImpl(this);
    }

    /*
     * @see javax.xml.validation.Schema#newValidatorHandler()
     */
    public final ValidatorHandler newValidatorHandler() {
        return new ValidatorHandlerImpl(this);
    }

} // AbstractXMLSchema
