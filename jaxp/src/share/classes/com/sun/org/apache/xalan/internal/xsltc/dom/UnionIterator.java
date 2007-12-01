/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * $Id: UnionIterator.java,v 1.5 2005/09/28 13:48:38 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.dom;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.runtime.BasisLibrary;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.dtm.ref.DTMAxisIteratorBase;

/**
 * UnionIterator takes a set of NodeIterators and produces
 * a merged NodeSet in document order with duplicates removed
 * The individual iterators are supposed to generate nodes
 * in document order
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 */
public final class UnionIterator extends MultiValuedNodeHeapIterator {
    /** wrapper for NodeIterators to support iterator
        comparison on the value of their next() method
    */
    final private DOM _dom;

    private final class LookAheadIterator
            extends MultiValuedNodeHeapIterator.HeapNode
    {
        public DTMAxisIterator iterator;

        public LookAheadIterator(DTMAxisIterator iterator) {
            super();
            this.iterator = iterator;
        }

        public int step() {
            _node = iterator.next();
            return _node;
        }

        public HeapNode cloneHeapNode() {
            LookAheadIterator clone = (LookAheadIterator) super.cloneHeapNode();
            clone.iterator = iterator.cloneIterator();
            return clone;
        }

        public void setMark() {
            super.setMark();
            iterator.setMark();
        }

        public void gotoMark() {
            super.gotoMark();
            iterator.gotoMark();
        }

        public boolean isLessThan(HeapNode heapNode) {
            LookAheadIterator comparand = (LookAheadIterator) heapNode;
            return _dom.lessThan(_node, heapNode._node);
        }

        public HeapNode setStartNode(int node) {
            iterator.setStartNode(node);
            return this;
        }

        public HeapNode reset() {
            iterator.reset();
            return this;
        }
    } // end of LookAheadIterator

    public UnionIterator(DOM dom) {
        _dom = dom;
    }

    public UnionIterator addIterator(DTMAxisIterator iterator) {
        addHeapNode(new LookAheadIterator(iterator));
        return this;
    }
}
