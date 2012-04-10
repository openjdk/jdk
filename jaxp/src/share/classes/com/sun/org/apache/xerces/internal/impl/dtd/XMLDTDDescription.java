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

package com.sun.org.apache.xerces.internal.impl.dtd;

import java.util.ArrayList;
import java.util.Vector;

import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarDescription;
import com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier;
import com.sun.org.apache.xerces.internal.xni.parser.XMLInputSource;
import com.sun.org.apache.xerces.internal.util.XMLResourceIdentifierImpl;

/**
 * All information specific to DTD grammars.
 *
 * @xerces.internal
 *
 * @author Neil Graham, IBM
 * @version $Id: XMLDTDDescription.java,v 1.4 2010/08/11 07:18:38 joehw Exp $
 */
public class XMLDTDDescription extends XMLResourceIdentifierImpl
        implements com.sun.org.apache.xerces.internal.xni.grammars.XMLDTDDescription {

    // Data

    // pieces of information needed to make this usable as a Grammar key
    // if we know the root of this grammar, here's its name:
    protected String fRootName = null;

    // if we don't know the root name, this stores all elements that
    // could serve; fPossibleRoots and fRootName cannot both be non-null
    protected ArrayList fPossibleRoots = null;

    // Constructors:
    public XMLDTDDescription(XMLResourceIdentifier id, String rootName) {
        this.setValues(id.getPublicId(), id.getLiteralSystemId(),
                id.getBaseSystemId(), id.getExpandedSystemId());
        this.fRootName = rootName;
        this.fPossibleRoots = null;
    } // init(XMLResourceIdentifier, String)

    public XMLDTDDescription(String publicId, String literalId,
                String baseId, String expandedId, String rootName) {
        this.setValues(publicId, literalId, baseId, expandedId);
        this.fRootName = rootName;
        this.fPossibleRoots = null;
    } // init(String, String, String, String, String)

    public XMLDTDDescription(XMLInputSource source) {
        this.setValues(source.getPublicId(), null,
                source.getBaseSystemId(), source.getSystemId());
        this.fRootName = null;
        this.fPossibleRoots = null;
    } // init(XMLInputSource)

    // XMLGrammarDescription methods

    public String getGrammarType () {
        return XMLGrammarDescription.XML_DTD;
    } // getGrammarType():  String

    /**
     * @return the root name of this DTD or null if root name is unknown
     */
    public String getRootName() {
        return fRootName;
    } // getRootName():  String

    /** Set the root name **/
    public void setRootName(String rootName) {
        fRootName = rootName;
        fPossibleRoots = null;
    }

    /** Set possible roots **/
    public void setPossibleRoots(ArrayList possibleRoots) {
        fPossibleRoots = possibleRoots;
    }

    /** Set possible roots **/
    public void setPossibleRoots(Vector possibleRoots) {
        fPossibleRoots = (possibleRoots != null) ? new ArrayList(possibleRoots) : null;
    }

    /**
     * Compares this grammar with the given grammar. Currently, we compare
     * as follows:
     * - if grammar type not equal return false immediately
     * - try and find a common root name:
     *    - if both have roots, use them
     *    - else if one has a root, examine other's possible root's for a match;
     *    - else try all combinations
     *  - test fExpandedSystemId and fPublicId as above
     *
     * @param desc The description of the grammar to be compared with
     * @return     True if they are equal, else false
     */
    public boolean equals(Object desc) {
        if (!(desc instanceof XMLGrammarDescription)) return false;
        if (!getGrammarType().equals(((XMLGrammarDescription)desc).getGrammarType())) {
            return false;
        }
        // assume it's a DTDDescription
        XMLDTDDescription dtdDesc = (XMLDTDDescription)desc;
        if (fRootName != null) {
            if ((dtdDesc.fRootName) != null && !dtdDesc.fRootName.equals(fRootName)) {
                return false;
            }
            else if (dtdDesc.fPossibleRoots != null && !dtdDesc.fPossibleRoots.contains(fRootName)) {
                return false;
            }
        }
        else if (fPossibleRoots != null) {
            if (dtdDesc.fRootName != null) {
                if (!fPossibleRoots.contains(dtdDesc.fRootName)) {
                    return false;
                }
            }
            else if (dtdDesc.fPossibleRoots == null) {
                return false;
            }
            else {
                boolean found = false;
                final int size = fPossibleRoots.size();
                for (int i = 0; i < size; ++i) {
                    String root = (String) fPossibleRoots.get(i);
                    found = dtdDesc.fPossibleRoots.contains(root);
                    if (found) break;
                }
                if (!found) return false;
            }
        }
        // if we got this far we've got a root match... try other two fields,
        // since so many different DTD's have roots in common:
        if (fExpandedSystemId != null) {
            if (!fExpandedSystemId.equals(dtdDesc.fExpandedSystemId)) {
                return false;
            }
        }
        else if (dtdDesc.fExpandedSystemId != null) {
            return false;
        }
        if (fPublicId != null) {
            if (!fPublicId.equals(dtdDesc.fPublicId)) {
                return false;
            }
        }
        else if (dtdDesc.fPublicId != null) {
            return false;
        }
        return true;
    }

    /**
     * Returns the hash code of this grammar
     * Because our .equals method is so complex, we just return a very
     * simple hash that might avoid calls to the equals method a bit...
     * @return The hash code
     */
    public int hashCode() {
        if (fExpandedSystemId != null) {
            return fExpandedSystemId.hashCode();
        }
        if (fPublicId != null) {
            return fPublicId.hashCode();
        }
        // give up; hope .equals can handle it:
        return 0;
    }
} // class XMLDTDDescription
