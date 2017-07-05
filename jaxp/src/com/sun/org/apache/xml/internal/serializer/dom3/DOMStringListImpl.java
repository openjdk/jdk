/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id:  $
 */
package com.sun.org.apache.xml.internal.serializer.dom3;

import java.util.Vector;

//import org.apache.xerces.dom3.DOMStringList;
import org.w3c.dom.DOMStringList;

/**
 * This class implemets the DOM Level 3 Core interface DOMStringList.
 *
 * @xsl.usage internal
 */
final class DOMStringListImpl implements DOMStringList {

    //A collection of DOMString values
    private Vector fStrings;

    /**
     * Construct an empty list of DOMStringListImpl
     */
    DOMStringListImpl() {
        fStrings = new Vector();
    }

    /**
     * Construct an empty list of DOMStringListImpl
     */
    DOMStringListImpl(Vector params) {
        fStrings = params;
    }

    /**
     * Construct an empty list of DOMStringListImpl
     */
    DOMStringListImpl(String[] params ) {
        fStrings = new Vector();
        if (params != null) {
            for (int i=0; i < params.length; i++) {
                fStrings.add(params[i]);
            }
        }
    }

    /**
     * @see org.apache.xerces.dom3.DOMStringList#item(int)
     */
    public String item(int index) {
        try {
            return (String) fStrings.elementAt(index);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * @see org.apache.xerces.dom3.DOMStringList#getLength()
     */
    public int getLength() {
        return fStrings.size();
    }

    /**
     * @see org.apache.xerces.dom3.DOMStringList#contains(String)
     */
    public boolean contains(String param) {
        return fStrings.contains(param) ;
    }

    /**
     * DOM Internal:
     * Add a <code>DOMString</code> to the list.
     *
     * @param domString A string to add to the list
     */
    public void add(String param) {
        fStrings.add(param);
    }

}
