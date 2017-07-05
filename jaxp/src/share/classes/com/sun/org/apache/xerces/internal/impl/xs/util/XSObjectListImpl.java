/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002,2003-2004 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.xs.util;

import com.sun.org.apache.xerces.internal.xs.XSObject;
import com.sun.org.apache.xerces.internal.xs.XSObjectList;

/**
 * Containts a list of XSObject's.
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 *
 */
public class XSObjectListImpl implements XSObjectList {

    /**
     * An immutable empty list.
     */
    public static final XSObjectList EMPTY_LIST = new XSObjectList () {
        public int getLength() {
            return 0;
        }
        public XSObject item(int index) {
            return null;
        }
    };

    private static final int DEFAULT_SIZE = 4;

    // The array to hold all data
    private XSObject[] fArray = null;
    // Number of elements in this list
    private int fLength = 0;



    public XSObjectListImpl() {
        fArray = new XSObject[DEFAULT_SIZE];
        fLength = 0;
    }

    /**
     * Construct an XSObjectList implementation
     *
     * @param array     the data array
     * @param length    the number of elements
     */
    public XSObjectListImpl(XSObject[] array, int length) {
        fArray = array;
        fLength = length;
    }

    /**
     * The number of <code>XSObjects</code> in the list. The range of valid
     * child node indices is 0 to <code>length-1</code> inclusive.
     */
    public int getLength() {
        return fLength;
    }

    /**
     * Returns the <code>index</code>th item in the collection. The index
     * starts at 0. If <code>index</code> is greater than or equal to the
     * number of nodes in the list, this returns <code>null</code>.
     * @param index index into the collection.
     * @return The XSObject at the <code>index</code>th position in the
     *   <code>XSObjectList</code>, or <code>null</code> if that is not a
     *   valid index.
     */
    public XSObject item(int index) {
        if (index < 0 || index >= fLength)
            return null;
        return fArray[index];
    }

    // clear this object
    public void clear() {
        for (int i=0; i<fLength; i++) {
            fArray[i] = null;
        }
        fArray = null;
        fLength = 0;
    }

    public void add (XSObject object){
       if (fLength == fArray.length){
           XSObject[] temp = new XSObject[fLength + 4];
           System.arraycopy(fArray, 0, temp, 0, fLength);
           fArray = temp;
       }
       fArray[fLength++]=object;
    }
    public void add (int index, XSObject object){
        fArray [index] = object;
    }

} // class XSObjectList
