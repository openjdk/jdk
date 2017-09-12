/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.vocab;


public abstract class Vocabulary {
    public static final int RESTRICTED_ALPHABET = 0;
    public static final int ENCODING_ALGORITHM = 1;
    public static final int PREFIX = 2;
    public static final int NAMESPACE_NAME = 3;
    public static final int LOCAL_NAME = 4;
    public static final int OTHER_NCNAME = 5;
    public static final int OTHER_URI = 6;
    public static final int ATTRIBUTE_VALUE = 7;
    public static final int OTHER_STRING = 8;
    public static final int CHARACTER_CONTENT_CHUNK = 9;
    public static final int ELEMENT_NAME = 10;
    public static final int ATTRIBUTE_NAME = 11;

    protected boolean _hasInitialReadOnlyVocabulary;

    protected String _referencedVocabularyURI;

    public boolean hasInitialVocabulary() {
        return _hasInitialReadOnlyVocabulary;
    }

    protected void setInitialReadOnlyVocabulary(boolean hasInitialReadOnlyVocabulary) {
        _hasInitialReadOnlyVocabulary = hasInitialReadOnlyVocabulary;
    }

    public boolean hasExternalVocabulary() {
        return _referencedVocabularyURI != null;
    }

    public String getExternalVocabularyURI() {
        return _referencedVocabularyURI;
    }

    protected void setExternalVocabularyURI(String referencedVocabularyURI) {
        _referencedVocabularyURI = referencedVocabularyURI;
    }

}
