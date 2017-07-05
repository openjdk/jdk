/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999-2002 The Apache Software Foundation.  All rights
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

package com.sun.org.apache.xerces.internal.impl.dtd;

import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.impl.dtd.models.ContentModelValidator;

/**
 */
public class XMLElementDecl {

    //
    // Constants
    //

    /** TYPE_ANY */
    public static final short TYPE_ANY = 0;

    /** TYPE_EMPTY */
    public static final short TYPE_EMPTY = 1;

    /** TYPE_MIXED */
    public static final short TYPE_MIXED = 2;

    /** TYPE_CHILDREN */
    public static final short TYPE_CHILDREN = 3;

    /** TYPE_SIMPLE */
    public static final short TYPE_SIMPLE = 4;

    //
    // Data
    //

    /** name */
    public final QName name = new QName();

    /** scope */
    public int scope = -1;

    /** type */
    public short type = -1;

    /** contentModelValidator */
    public ContentModelValidator contentModelValidator;

    /** simpleType */
    public final XMLSimpleType simpleType = new XMLSimpleType();

    //
    // Methods
    //

    /**
     * setValues
     *
     * @param name
     * @param scope
     * @param type
     * @param contentModelValidator
     * @param simpleType
     */
    public void setValues(QName name, int scope, short type, ContentModelValidator contentModelValidator, XMLSimpleType simpleType) {
        this.name.setValues(name);
        this.scope                 = scope;
        this.type                  = type;
        this.contentModelValidator = contentModelValidator;
        this.simpleType.setValues(simpleType);
    } // setValues

    /**
     * clear
     */
    public void clear() {
        this.name.clear();
        this.type          = -1;
        this.scope         = -1;
        this.contentModelValidator = null;
        this.simpleType.clear();
    } // clear

} // class XMLElementDecl
