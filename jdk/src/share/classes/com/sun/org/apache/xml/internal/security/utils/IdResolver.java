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




import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;


/**
 * Purpose of this class is to enable the XML Parser to keep track of ID
 * attributes. This is done by 'registering' attributes of type ID at the
 * IdResolver. This is necessary if we create a document from scratch and we
 * sign some resources with a URI using a fragent identifier...
 * <BR />
 * The problem is that if you do not validate a document, you cannot use the
 * <CODE>getElementByID</CODE> functionality. So this modules uses some implicit
 * knowledge on selected Schemas and DTDs to pick the right Element for a given
 * ID: We know that all <CODE>@Id</CODE> attributes in an Element from the XML
 * Signature namespace are of type <CODE>ID</CODE>.
 *
 * @author $Author: raul $
 * @see <A HREF="http://www.xml.com/lpt/a/2001/11/07/id.html">"Identity Crisis" on xml.com</A>
 */
public class IdResolver {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(IdResolver.class.getName());

   static WeakHashMap docMap = new WeakHashMap();

   /**
    * Constructor IdResolver
    *
    */
   private IdResolver() {

      // we don't allow instantiation
   }

   /**
    * Method registerElementById
    *
    * @param element
    * @param idValue
    */
   public static void registerElementById(Element element, String idValue) {
      Document doc = element.getOwnerDocument();
      WeakHashMap elementMap = (WeakHashMap) docMap.get(doc);
      if(elementMap == null) {
          elementMap = new WeakHashMap();
          docMap.put(doc, elementMap);
      }
      elementMap.put(idValue, new WeakReference(element));
   }

   /**
    * Method registerElementById
    *
    * @param element
    * @param id
    */
   public static void registerElementById(Element element, Attr id) {
      IdResolver.registerElementById(element, id.getNodeValue());
   }

   /**
    * Method getElementById
    *
    * @param doc
    * @param id
    * @return the element obtained by the Id, or null if it is not found.
    */
   public static Element getElementById(Document doc, String id) {

      Element result = null;

      result = IdResolver.getElementByIdType(doc, id);

      if (result != null) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE,
            "I could find an Element using the simple getElementByIdType method: "
            + result.getTagName());

         return result;
      }

       result = IdResolver.getElementByIdUsingDOM(doc, id);

       if (result != null) {
          if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE,
             "I could find an Element using the simple getElementByIdUsingDOM method: "
            + result.getTagName());

         return result;
      }
       // this must be done so that Xalan can catch ALL namespaces
       //XMLUtils.circumventBug2650(doc);
      result = IdResolver.getElementBySearching(doc, id);

      if (result != null) {
                  IdResolver.registerElementById(result, id);

         return result;
      }

      return null;
   }


    /**
     * Method getElementByIdUsingDOM
     *
     * @param doc
     * @param id
     * @return the element obtained by the Id, or null if it is not found.
     */
    private static Element getElementByIdUsingDOM(Document doc, String id) {
        if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "getElementByIdUsingDOM() Search for ID " + id);
        return doc.getElementById(id);
    }

   /**
    * Method getElementByIdType
    *
    * @param doc
    * @param id
    * @return the element obtained by the Id, or null if it is not found.
    */
   private static Element getElementByIdType(Document doc, String id) {
          if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "getElementByIdType() Search for ID " + id);
       WeakHashMap elementMap = (WeakHashMap) docMap.get(doc);
       if (elementMap != null) {
           WeakReference weakReference = (WeakReference) elementMap.get(id);
           if (weakReference != null)
           {
                return (Element) weakReference.get();
           }
       }
       return null;
   }


   static java.util.List names;
   static {
           String namespaces[]={ Constants.SignatureSpecNS,
                           EncryptionConstants.EncryptionSpecNS,
                           "http://schemas.xmlsoap.org/soap/security/2000-12",
                           "http://www.w3.org/2002/03/xkms#"
                   };
           names=Arrays.asList(namespaces);
   }


   private static Element getElementBySearching(Node root,String id) {
           Element []els=new Element[5];
           getElementBySearching(root,id,els);
           for (int i=0;i<els.length;i++) {
                   if (els[i]!=null) {
                           return els[i];
                   }
           }
           return null;

   }
   private static int getElementBySearching(Node root,String id,Element []els) {
           switch (root.getNodeType()) {
           case Node.ELEMENT_NODE:
                   Element el=(Element)root;
                   if (el.hasAttributes()) {
                           int index=names.indexOf(el.getNamespaceURI());
                           if (index<0) {
                                   index=4;
                           }
                           if (el.getAttribute("Id").equals(id)) {
                                   els[index]=el;
                                   if (index==0) {
                                           return 1;
                                   }
                           } else if ( el.getAttribute("id").equals(id) ) {
                                   if (index!=2) {
                                           index=4;
                                   }
                                   els[index]=el;
                           } else if ( el.getAttribute("ID").equals(id) ) {
                                   if (index!=3) {
                                           index=4;
                                   }
                                   els[index]=el;
                           } else if ((index==3)&&(
                                   el.getAttribute("OriginalRequestID").equals(id) ||
                                   el.getAttribute("RequestID").equals(id) ||
                                   el.getAttribute("ResponseID" ).equals(id))) {
                                   els[3]=el;
                           }
                   }
                case Node.DOCUMENT_NODE:
                        Node sibling=root.getFirstChild();
                        while (sibling!=null) {
                                if (getElementBySearching(sibling,id,els)==1)
                                        return 1;
                                sibling=sibling.getNextSibling();
                        }
           }
           return 0;
   }

}
