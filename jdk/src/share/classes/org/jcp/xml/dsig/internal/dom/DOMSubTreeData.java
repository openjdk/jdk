/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2006 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMSubTreeData.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.NodeSetData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This is a subtype of NodeSetData that represents a dereferenced
 * same-document URI as the root of a subdocument. The main reason is
 * for efficiency and performance, as some transforms can operate
 * directly on the subdocument and there is no need to convert it
 * first to an XPath node-set.
 */
public class DOMSubTreeData implements NodeSetData {

    private boolean excludeComments;
    private Iterator ni;
    private Node root;

    public DOMSubTreeData(Node root, boolean excludeComments) {
        this.root = root;
        this.ni = new DelayedNodeIterator(root, excludeComments);
        this.excludeComments = excludeComments;
    }

    public Iterator iterator() {
        return ni;
    }

    public Node getRoot() {
        return root;
    }

    public boolean excludeComments() {
        return excludeComments;
    }

    /**
     * This is an Iterator that contains a backing node-set that is
     * not populated until the caller first attempts to advance the iterator.
     */
    static class DelayedNodeIterator implements Iterator {
        private Node root;
        private List nodeSet;
        private ListIterator li;
        private boolean withComments;

        DelayedNodeIterator(Node root, boolean excludeComments) {
            this.root = root;
            this.withComments = !excludeComments;
        }

        public boolean hasNext() {
            if (nodeSet == null) {
                nodeSet = dereferenceSameDocumentURI(root);
                li = nodeSet.listIterator();
            }
            return li.hasNext();
        }

        public Object next() {
            if (nodeSet == null) {
                nodeSet = dereferenceSameDocumentURI(root);
                li = nodeSet.listIterator();
            }
            if (li.hasNext()) {
                return (Node) li.next();
            } else {
                throw new NoSuchElementException();
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Dereferences a same-document URI fragment.
         *
         * @param node the node (document or element) referenced by the
         *       URI fragment. If null, returns an empty set.
         * @return a set of nodes (minus any comment nodes)
         */
        private List dereferenceSameDocumentURI(Node node) {
            List nodeSet = new ArrayList();
            if (node != null) {
                nodeSetMinusCommentNodes(node, nodeSet, null);
            }
            return nodeSet;
        }

        /**
         * Recursively traverses the subtree, and returns an XPath-equivalent
         * node-set of all nodes traversed, excluding any comment nodes,
         * if specified.
         *
         * @param node the node to traverse
         * @param nodeSet the set of nodes traversed so far
         * @param the previous sibling node
         */
        private void nodeSetMinusCommentNodes(Node node, List nodeSet,
            Node prevSibling) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE :
                    NamedNodeMap attrs = node.getAttributes();
                    if (attrs != null) {
                        for (int i = 0, len = attrs.getLength(); i < len; i++) {
                            nodeSet.add(attrs.item(i));
                        }
                    }
                    nodeSet.add(node);
                case Node.DOCUMENT_NODE :
                    Node pSibling = null;
                    for (Node child = node.getFirstChild(); child != null;
                        child = child.getNextSibling()) {
                        nodeSetMinusCommentNodes(child, nodeSet, pSibling);
                        pSibling = child;
                    }
                    break;
                case Node.TEXT_NODE :
                case Node.CDATA_SECTION_NODE:
                    // emulate XPath which only returns the first node in
                    // contiguous text/cdata nodes
                    if (prevSibling != null &&
                        (prevSibling.getNodeType() == Node.TEXT_NODE ||
                         prevSibling.getNodeType() == Node.CDATA_SECTION_NODE)){                        return;
                    }
                case Node.PROCESSING_INSTRUCTION_NODE :
                    nodeSet.add(node);
                    break;
                case Node.COMMENT_NODE:
                    if (withComments) {
                        nodeSet.add(node);
                    }
            }
        }
    }
}
