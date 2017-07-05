/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
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
package com.sun.org.apache.xml.internal.security.utils;

import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Christian Geuer-Pollmann
 *
 */
public class HelperNodeList implements NodeList {

   /** Field nodes */
   ArrayList nodes = new ArrayList(20);
   boolean _allNodesMustHaveSameParent = false;

   /**
    *
    */
   public HelperNodeList() {
      this(false);
   }


   /**
    * @param allNodesMustHaveSameParent
    */
   public HelperNodeList(boolean allNodesMustHaveSameParent) {
      this._allNodesMustHaveSameParent = allNodesMustHaveSameParent;
   }

   /**
    * Method item
    *
    * @param index
    * @return node with inde i
    */
   public Node item(int index) {

      // log.log(java.util.logging.Level.FINE, "item(" + index + ") of " + this.getLength() + " nodes");

      return (Node) nodes.get(index);
   }

   /**
    * Method getLength
    *
    *  @return length of the list
    */
   public int getLength() {
      return nodes.size();
   }

   /**
    * Method appendChild
    *
    * @param node
    * @throws IllegalArgumentException
    */
   public void appendChild(Node node) throws IllegalArgumentException {
      if (this._allNodesMustHaveSameParent && this.getLength() > 0) {
         if (this.item(0).getParentNode() != node.getParentNode()) {
            throw new IllegalArgumentException("Nodes have not the same Parent");
         }
      }
      nodes.add(node);
   }

   /**
    * @return the document that contains this nodelist
    */
   public Document getOwnerDocument() {
      if (this.getLength() == 0) {
         return null;
      }
      return XMLUtils.getOwnerDocument(this.item(0));
   }
}
