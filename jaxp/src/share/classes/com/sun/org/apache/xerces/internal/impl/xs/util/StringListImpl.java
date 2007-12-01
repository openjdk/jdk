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

import com.sun.org.apache.xerces.internal.xs.StringList;
import java.util.Vector;
/**
 * Containts a list of Object's.
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 *
 */
public class StringListImpl implements StringList {

    /**
     * An immutable empty list.
     */
    public static final StringList EMPTY_LIST = new StringList () {
        public int getLength() {
            return 0;
        }
        public boolean contains(String item) {
            return false;
        }
        public String item(int index) {
            return null;
        }
    };

    // The array to hold all data
    private String[] fArray = null;
    // Number of elements in this list
    private int fLength = 0;

    // REVISIT: this is temp solution. In general we need to use this class
    //          instead of the Vector.
    private Vector fVector;

    public StringListImpl(Vector v) {
        fVector = v;
        fLength = (v == null) ? 0 : v.size();
    }

    /**
     * Construct an XSObjectList implementation
     *
     * @param array     the data array
     * @param length    the number of elements
     */
    public StringListImpl(String[] array, int length) {
        fArray = array;
        fLength = length;
    }

    /**
     * The number of <code>Objects</code> in the list. The range of valid
     * child node indices is 0 to <code>length-1</code> inclusive.
     */
    public int getLength() {
        return fLength;
    }

    /**
     *  Checks if the <code>GenericString</code> <code>item</code> is a member
     * of this list.
     * @param item  <code>GenericString</code> whose presence in this list is
     *   to be tested.
     * @return  True if this list contains the <code>GenericString</code>
     *   <code>item</code>.
     */
    public boolean contains(String item) {
        if (fVector != null)
            return fVector.contains(item);

        if (item == null) {
            for (int i = 0; i < fLength; i++) {
                if (fArray[i] == null)
                    return true;
            }
        }
        else {
            for (int i = 0; i < fLength; i++) {
                if (item.equals(fArray[i]))
                    return true;
            }
        }
        return false;
    }

    public String item(int index) {
        if (index < 0 || index >= fLength)
            return null;
        if (fVector != null) {
            return (String)fVector.elementAt(index);
        }
        return fArray[index];
    }

} // class XSParticle
