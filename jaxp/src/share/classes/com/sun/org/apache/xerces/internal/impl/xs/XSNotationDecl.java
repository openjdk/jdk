/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.xs;

import com.sun.org.apache.xerces.internal.xs.XSAnnotation;
import com.sun.org.apache.xerces.internal.xs.XSConstants;
import com.sun.org.apache.xerces.internal.xs.XSNamespaceItem;
import com.sun.org.apache.xerces.internal.xs.XSNotationDeclaration;

/**
 * The XML representation for a NOTATION declaration
 * schema component is a global <notation> element information item
 *
 * @xerces.internal
 *
 * @author Rahul Srivastava, Sun Microsystems Inc.
 */
public class XSNotationDecl implements XSNotationDeclaration {

    // name of the group
    public String fName = null;
    // target namespace of the group
    public String fTargetNamespace = null;
    // public id of the notation
    public String fPublicId = null;
    // system id of the notation
    public String fSystemId = null;

    // optional annotation
    public XSAnnotationImpl fAnnotation = null;

    /**
     * Get the type of the object, i.e ELEMENT_DECLARATION.
     */
    public short getType() {
        return XSConstants.NOTATION_DECLARATION;
    }

    /**
     * The <code>name</code> of this <code>XSObject</code> depending on the
     * <code>XSObject</code> type.
     */
    public String getName() {
        return fName;
    }

    /**
     * The namespace URI of this node, or <code>null</code> if it is
     * unspecified.  defines how a namespace URI is attached to schema
     * components.
     */
    public String getNamespace() {
        return fTargetNamespace;
    }

    /**
     * Optional if {public identifier} is present. A URI reference.
     */
    public String getSystemId() {
        return fSystemId;
    }

    /**
     * Optional if {system identifier} is present. A public identifier,
     * as defined in [XML 1.0 (Second Edition)].
     */
    public String getPublicId() {
        return fPublicId;
    }

    /**
     * Optional. Annotation.
     */
    public XSAnnotation getAnnotation() {
        return fAnnotation;
    }

        /**
         * @see com.sun.org.apache.xerces.internal.xs.XSObject#getNamespaceItem()
         */
        public XSNamespaceItem getNamespaceItem() {
                return null;
        }

} // class XSNotationDecl
