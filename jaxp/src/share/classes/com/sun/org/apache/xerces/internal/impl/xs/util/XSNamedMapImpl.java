/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002,2004 The Apache Software Foundation.
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

import com.sun.org.apache.xerces.internal.util.SymbolHash;
import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.xs.*;

/**
 * Containts the map between qnames and XSObject's.
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 *
 */
public class XSNamedMapImpl implements XSNamedMap {

    /**
     * An immutable empty map.
     */
    public static final XSNamedMap EMPTY_MAP = new XSNamedMap () {
        public int getLength() {
            return 0;
        }
        public XSObject itemByName(String namespace, String localName) {
            return null;
        }
        public XSObject item(int index) {
            return null;
        }
    };

    // components of these namespaces are stored in this map
    String[]     fNamespaces;
    // number of namespaces
    int          fNSNum;
    // each entry contains components in one namespace
    SymbolHash[] fMaps;
    // store all components from all namespace.
    // used when this map is accessed as a list.
    XSObject[]   fArray = null;
    // store the number of componetns.
    // used when this map is accessed as a list.
    int          fLength = -1;
    // temprory QName object
    QName        fName = new QName();

    /**
     * Construct an XSNamedMap implmentation for one namespace
     *
     * @param namespace the namespace to which the components belong
     * @param map       the map from local names to components
     */
    public XSNamedMapImpl(String namespace, SymbolHash map) {
        fNamespaces = new String[] {namespace};
        fMaps = new SymbolHash[] {map};
        fNSNum = 1;
    }

    /**
     * Construct an XSNamedMap implmentation for a list of namespaces
     *
     * @param namespaces the namespaces to which the components belong
     * @param maps       the maps from local names to components
     * @param num        the number of namespaces
     */
    public XSNamedMapImpl(String[] namespaces, SymbolHash[] maps, int num) {
        fNamespaces = namespaces;
        fMaps = maps;
        fNSNum = num;
    }

    /**
     * Construct an XSNamedMap implmentation one namespace from an array
     *
     * @param array     containing all components
     * @param length    number of components
     */
    public XSNamedMapImpl(XSObject[] array, int length) {
        if (length == 0) {
            fNSNum = 0;
            fLength = 0;
            return;
        }
        // because all components are from the same target namesapce,
        // get the namespace from the first one.
        fNamespaces = new String[]{array[0].getNamespace()};
        fMaps = null;
        fNSNum = 1;
        // copy elements to the Vector
        fArray = array;
        fLength = length;
    }

    /**
     * The number of <code>XSObjects</code> in the <code>XSObjectList</code>. The
     * range of valid child node indices is 0 to <code>length-1</code>
     * inclusive.
     */
    public synchronized int getLength() {
        if (fLength == -1) {
            fLength = 0;
            for (int i = 0; i < fNSNum; i++)
                fLength += fMaps[i].getLength();
        }
        return fLength;
    }

    /**
     * Retrieves an <code>XSObject</code> specified by local name and namespace
     * URI.
     * @param namespace The namespace URI of the <code>XSObject</code> to
     *   retrieve.
     * @param localName The local name of the <code>XSObject</code> to retrieve.
     * @return A <code>XSObject</code> (of any type) with the specified local
     *   name and namespace URI, or <code>null</code> if they do not
     *   identify any <code>XSObject</code> in this map.
     */
    public XSObject itemByName(String namespace, String localName) {
        if (namespace != null)
            namespace = namespace.intern();
        for (int i = 0; i < fNSNum; i++) {
            if (namespace == fNamespaces[i]) {
                // when this map is created from SymbolHash's
                // get the component from SymbolHash
                if (fMaps != null)
                    return (XSObject)fMaps[i].get(localName);
                // Otherwise (it's created from an array)
                // go through the array to find a matcing name
                XSObject ret;
                for (int j = 0; j < fLength; j++) {
                    ret = fArray[j];
                    if (ret.getName().equals(localName))
                        return ret;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the <code>index</code>th item in the map. The index starts at
     * 0. If <code>index</code> is greater than or equal to the number of
     * nodes in the list, this returns <code>null</code>.
     * @param index The position in the map from which the item is to be
     *   retrieved.
     * @return The <code>XSObject</code> at the <code>index</code>th position
     *   in the <code>XSNamedMap</code>, or <code>null</code> if that is
     *   not a valid index.
     */
    public synchronized XSObject item(int index) {
        if (fArray == null) {
            // calculate the total number of elements
            getLength();
            fArray = new XSObject[fLength];
            int pos = 0;
            // get components from all SymbolHash's
            for (int i = 0; i < fNSNum; i++) {
                pos += fMaps[i].getValues(fArray, pos);
            }
        }
        if (index < 0 || index >= fLength)
            return null;
        return fArray[index];
    }

} // class XSNamedMapImpl
