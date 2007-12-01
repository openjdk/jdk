/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2000-2002 The Apache Software Foundation.  All rights
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
package com.sun.org.apache.xerces.internal.util;

import java.util.Hashtable;

import com.sun.org.apache.xerces.internal.xs.XSTypeDefinition;
import org.w3c.dom.TypeInfo;

/**
 * Straight-forward implementation of {@link TypeInfo}.
 *
 * <p>
 * This class is immutable.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class TypeInfoImpl implements TypeInfo {

    private final String typeNamespace;
    private final String typeName;
        private final static String dtdNamespaceURI = "http://www.w3.org/TR/REC-xml";
        public TypeInfoImpl(){
                typeNamespace = null;
                typeName = null;
        }
    public TypeInfoImpl(String typeNamespace, String typeName) {
        this.typeNamespace = typeNamespace;
        this.typeName = typeName;
    }

    public TypeInfoImpl(XSTypeDefinition t) {
        this( t.getNamespace(), t.getName() );
    }

    public String getTypeName() {
        return typeName;
    }

    public String getTypeNamespace() {
        return typeNamespace;
    }

    /**
     * Always returns false.
     */
    public boolean isDerivedFrom(String typeNamespaceArg,  String typeNameArg, int derivationMethod) {
        return false;
    }

    /**
     * Map from DTD type name ({@link String}) to {@link TypeInfo}.
     */
    private static final Hashtable dtdCache = new Hashtable();

    /**
     * Obtains a {@link TypeInfo} object from the DTD type name.
     * <p>
     * Since DTD has a very limited type names, we can actually
     * cache the {@link TypeInfo} objects.
     */
    public static TypeInfo getDTDTypeInfo( String name ) {
        TypeInfo t = (TypeInfo)dtdCache.get(name);
        if(t==null) throw new IllegalArgumentException("Unknown DTD datatype "+name);
        return t;
    }

    static {
        String[] typeNames = new String[]{
            "CDATA", "ID", "IDREF", "IDREFS", "NMTOKEN", "NMTOKENS",
            "ENTITY", "ENTITIES", "NOTATION"};
        for( int i=0; i<typeNames.length; i++ )
            dtdCache.put(typeNames[i],new TypeInfoImpl(dtdNamespaceURI,typeNames[i]));
    }
}
