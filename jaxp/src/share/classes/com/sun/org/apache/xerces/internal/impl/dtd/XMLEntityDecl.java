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

/**
 */
public class XMLEntityDecl {

    //
    // Data
    //

    /** name */
    public String name;

    /** publicId */
    public String publicId;

    /** systemId */
    public String systemId;

    /** baseSystemId */
    public String baseSystemId;

    /** notation */
    public String notation;

    /** isPE */
    public boolean isPE;

    /** inExternal */
    /** <strong>Note:</strong> flag of where the entity is defined, not whether it is a external entity */
    public boolean inExternal;

    /** Value. */
    public String value;

    //
    // Methods
    //

    /**
     * setValues
     *
     * @param name
     * @param publicId
     * @param systemId
     * @param baseSystemId
     * @param notation
     * @param isPE
     * @param inExternal
     */
    public void setValues(String name, String publicId, String systemId,
                          String baseSystemId, String notation,
                          boolean isPE, boolean inExternal) {
        setValues(name, publicId, systemId, baseSystemId, notation, null, isPE, inExternal);
    }

    /**
     * setValues
     *
     * @param name
     * @param publicId
     * @param systemId
     * @param baseSystemId
     * @param value
     * @param notation
     * @param isPE
     * @param inExternal
     */
    public void setValues(String name, String publicId, String systemId,
                          String baseSystemId, String notation,
                          String value, boolean isPE, boolean inExternal) {
        this.name         = name;
        this.publicId     = publicId;
        this.systemId     = systemId;
        this.baseSystemId = baseSystemId;
        this.notation     = notation;
        this.value        = value;
        this.isPE         = isPE;
        this.inExternal   = inExternal;
    } // setValues(String,String,String,String,String,boolean,boolean)

    /**
     * clear
     */
    public void clear() {
       this.name         = null;
       this.publicId     = null;
       this.systemId     = null;
       this.baseSystemId = null;
       this.notation     = null;
       this.value        = null;
       this.isPE         = false;
       this.inExternal   = false;

    } // clear

} // class XMLEntityDecl
