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
package com.sun.org.apache.xml.internal.security.c14n.helper;



import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Attr;


/**
 * Compares two attributes based on the C14n specification.
 *
 * <UL>
 * <LI>Namespace nodes have a lesser document order position than attribute nodes.
 * <LI> An element's namespace nodes are sorted lexicographically by
 *   local name (the default namespace node, if one exists, has no
 *   local name and is therefore lexicographically least).
 * <LI> An element's attribute nodes are sorted lexicographically with
 *   namespace URI as the primary key and local name as the secondary
 *   key (an empty namespace URI is lexicographically least).
 * </UL>
 *
 * $todo$ Should we implement java.util.Comparator and import java.util.Arrays to use Arrays.sort(intarray);
 * @author Christian Geuer-Pollmann
 */
public class AttrCompare implements java.util.Comparator {

   private final int ATTR0_BEFORE_ATTR1 = -1;
   private final int ATTR1_BEFORE_ATTR0 = 1;

   private final static String XMLNS=Constants.NamespaceSpecNS;
   /**
    * Compares two attributes based on the C14n specification.
    *
    * <UL>
    * <LI>Namespace nodes have a lesser document order position than attribute nodes.
    * <LI> An element's namespace nodes are sorted lexicographically by
    *   local name (the default namespace node, if one exists, has no
    *   local name and is therefore lexicographically least).
    * <LI> An element's attribute nodes are sorted lexicographically with
    *   namespace URI as the primary key and local name as the secondary
    *   key (an empty namespace URI is lexicographically least).
    * </UL>
    *
    * @param obj0 casted Attr
    * @param obj1 casted Attr
    * @return returns a negative integer, zero, or a positive integer as obj0 is less than, equal to, or greater than obj1
    *
    */
   public int compare(Object obj0, Object obj1) {

      Attr attr0 = (Attr) obj0;
      Attr attr1 = (Attr) obj1;
      String namespaceURI0 = attr0.getNamespaceURI();
      String namespaceURI1 = attr1.getNamespaceURI();

      boolean isNamespaceAttr0 =
        XMLNS.equals(namespaceURI0);
      boolean isNamespaceAttr1 =
        XMLNS.equals(namespaceURI1);

      if (isNamespaceAttr0) {
         if (isNamespaceAttr1) {

            // both are namespaces
            String localname0 = attr0.getLocalName();
            String localname1 = attr1.getLocalName();

            if (localname0.equals("xmlns")) {
               localname0 = "";
            }

            if (localname1.equals("xmlns")) {
               localname1 = "";
            }

            return localname0.compareTo(localname1);
         }
         // attr0 is a namespace, attr1 is not
         return ATTR0_BEFORE_ATTR1;

      }
      if (isNamespaceAttr1) {

            // attr1 is a namespace, attr0 is not
            return ATTR1_BEFORE_ATTR0;
      }

      // none is a namespae

      if (namespaceURI0 == null) {
        if (namespaceURI1 == null) {
                /*
                 String localName0 = attr0.getLocalName();
                 String localName1 = attr1.getLocalName();
                 return localName0.compareTo(localName1);
                 */

                String name0 = attr0.getName();
                String name1 = attr1.getName();
                return name0.compareTo(name1);
        }
        return ATTR0_BEFORE_ATTR1;

      }
      if (namespaceURI1 == null) {
                return ATTR1_BEFORE_ATTR0;
      }
      int a = namespaceURI0.compareTo(namespaceURI1);

      if (a != 0) {
        return a;
      }
      /*
      String localName0 = ;
      String localName1 =;*/

      return (attr0.getLocalName())
                                .compareTo( attr1.getLocalName());

   }

}
