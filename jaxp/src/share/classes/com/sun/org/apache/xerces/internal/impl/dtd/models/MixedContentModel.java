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

package com.sun.org.apache.xerces.internal.impl.dtd.models;

import com.sun.org.apache.xerces.internal.xni.QName;

import com.sun.org.apache.xerces.internal.impl.dtd.XMLContentSpec;

/**
 * MixedContentModel is a derivative of the abstract content model base
 * class that handles the special case of mixed model elements. If an element
 * is mixed model, it has PCDATA as its first possible content, followed
 * by an alternation of the possible children. The children cannot have any
 * numeration or order, so it must look like this:
 * <pre>
 *   &lt;!ELEMENT Foo ((#PCDATA|a|b|c|)*)&gt;
 * </pre>
 * So, all we have to do is to keep an array of the possible children and
 * validate by just looking up each child being validated by looking it up
 * in the list.
 *
 * @xerces.internal
 *
 */
public class MixedContentModel
    implements ContentModelValidator {

    //
    // Data
    //

    /** The count of possible children that we have to deal with. */
    private int fCount;

    /** The list of possible children that we have to accept. */
    private QName fChildren[];

    /** The type of the children to support ANY. */
    private int fChildrenType[];

    /* this is the EquivClassComparator object */
    //private EquivClassComparator comparator = null;

    /**
     * True if mixed content model is ordered. DTD mixed content models
     * are <em>always</em> unordered.
     */
    private boolean fOrdered;

    //
    // Constructors
    //

    /**
     * Constructs a mixed content model.
     *
     * @param children The list of allowed children.
     * @param type The list of the types of the children.
     * @param offset The start offset position in the children.
     * @param length The child count.
     * @param ordered True if content must be ordered.
     */
    public MixedContentModel(QName[] children, int[] type, int offset, int length , boolean ordered) {
        // Make our own copy now, which is exactly the right size
        fCount = length;
        fChildren = new QName[fCount];
        fChildrenType = new int[fCount];
        for (int i = 0; i < fCount; i++) {
            fChildren[i] = new QName(children[offset + i]);
            fChildrenType[i] = type[offset + i];
        }
        fOrdered = ordered;

    }

    //
    // ContentModelValidator methods
    //


    /**
     * Check that the specified content is valid according to this
     * content model. This method can also be called to do 'what if'
     * testing of content models just to see if they would be valid.
     * <p>
     * A value of -1 in the children array indicates a PCDATA node. All other
     * indexes will be positive and represent child elements. The count can be
     * zero, since some elements have the EMPTY content model and that must be
     * confirmed.
     *
     * @param children The children of this element.  Each integer is an index within
     *                 the <code>StringPool</code> of the child element name.  An index
     *                 of -1 is used to indicate an occurrence of non-whitespace character
     *                 data.
     * @param offset Offset into the array where the children starts.
     * @param length The number of entries in the <code>children</code> array.
     *
     * @return The value -1 if fully valid, else the 0 based index of the child
     *         that first failed. If the value returned is equal to the number
     *         of children, then the specified children are valid but additional
     *         content is required to reach a valid ending state.
     *
     */
    public int validate(QName[] children, int offset, int length) {

        // must match order
        if (fOrdered) {
            int inIndex = 0;
            for (int outIndex = 0; outIndex < length; outIndex++) {

                // ignore mixed text
                final QName curChild = children[offset + outIndex];
                if (curChild.localpart == null) {
                    continue;
                }

                // element must match
                int type = fChildrenType[inIndex];
                if (type == XMLContentSpec.CONTENTSPECNODE_LEAF) {
                    if (fChildren[inIndex].rawname != children[offset + outIndex].rawname) {
                        return outIndex;
                    }
                }
                else if (type == XMLContentSpec.CONTENTSPECNODE_ANY) {
                    String uri = fChildren[inIndex].uri;
                    if (uri != null && uri != children[outIndex].uri) {
                        return outIndex;
                    }
                }
                else if (type == XMLContentSpec.CONTENTSPECNODE_ANY_LOCAL) {
                    if (children[outIndex].uri != null) {
                        return outIndex;
                    }
                }
                else if (type == XMLContentSpec.CONTENTSPECNODE_ANY_OTHER) {
                    if (fChildren[inIndex].uri == children[outIndex].uri) {
                        return outIndex;
                    }
                }

                // advance index
                inIndex++;
            }
        }

        // can appear in any order
        else {
            for (int outIndex = 0; outIndex < length; outIndex++)
            {
                // Get the current child out of the source index
                final QName curChild = children[offset + outIndex];

                // If its PCDATA, then we just accept that
                if (curChild.localpart == null)
                    continue;

                // And try to find it in our list
                int inIndex = 0;
                for (; inIndex < fCount; inIndex++)
                {
                    int type = fChildrenType[inIndex];
                    if (type == XMLContentSpec.CONTENTSPECNODE_LEAF) {
                        if (curChild.rawname == fChildren[inIndex].rawname) {
                            break;
                        }
                    }
                    else if (type == XMLContentSpec.CONTENTSPECNODE_ANY) {
                        String uri = fChildren[inIndex].uri;
                        if (uri == null || uri == children[outIndex].uri) {
                            break;
                        }
                    }
                    else if (type == XMLContentSpec.CONTENTSPECNODE_ANY_LOCAL) {
                        if (children[outIndex].uri == null) {
                            break;
                        }
                    }
                    else if (type == XMLContentSpec.CONTENTSPECNODE_ANY_OTHER) {
                        if (fChildren[inIndex].uri != children[outIndex].uri) {
                            break;
                        }
                    }
                    // REVISIT: What about checking for multiple ANY matches?
                    //          The content model ambiguity *could* be checked
                    //          by the caller before constructing the mixed
                    //          content model.
                }

                // We did not find this one, so the validation failed
                if (inIndex == fCount)
                    return outIndex;
            }
        }

        // Everything seems to be in order, so return success
        return -1;
    } // validate

} // class MixedContentModel
