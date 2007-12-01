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
package com.sun.org.apache.xml.internal.security.signature;



import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.IdResolver;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * Handles <code>&lt;ds:Object&gt;</code> elements
 * <code>Object<code> {@link Element} supply facility which can contain any kind data
 *
 *
 * @author Christian Geuer-Pollmann
 * $todo$ if we remove childen, the boolean values are not updated
 */
public class ObjectContainer extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
   static java.util.logging.Logger log =
       java.util.logging.Logger.getLogger(ObjectContainer.class.getName());

   /**
    * Constructs {@link ObjectContainer}
    *
    * @param doc the {@link Document} in which <code>Object</code> element is placed
    */
   public ObjectContainer(Document doc) {

      super(doc);
   }

   /**
    * Constructs {@link ObjectContainer} from {@link Element}
    *
    * @param element is <code>Object</code> element
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @throws XMLSecurityException
    */
   public ObjectContainer(Element element, String BaseURI)
           throws XMLSecurityException {

      super(element, BaseURI);
   }

   /**
    * Sets the <code>Id</code> attribute
    *
    * @param Id <code>Id</code> attribute
    */
   public void setId(String Id) {

      if ((this._state == MODE_SIGN) && (Id != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_ID, Id);
         IdResolver.registerElementById(this._constructionElement, Id);
      }
   }

   /**
    * Returns the <code>Id</code> attribute
    *
    * @return the <code>Id</code> attribute
    */
   public String getId() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_ID);
   }

   /**
    * Sets the <code>MimeType</code> attribute
    *
    * @param MimeType the <code>MimeType</code> attribute
    */
   public void setMimeType(String MimeType) {

      if ((this._state == MODE_SIGN) && (MimeType != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_MIMETYPE,
                                                MimeType);
      }
   }

   /**
    * Returns the <code>MimeType</code> attribute
    *
    * @return the <code>MimeType</code> attribute
    */
   public String getMimeType() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_MIMETYPE);
   }

   /**
    * Sets the <code>Encoding</code> attribute
    *
    * @param Encoding the <code>Encoding</code> attribute
    */
   public void setEncoding(String Encoding) {

      if ((this._state == MODE_SIGN) && (Encoding != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_ENCODING,
                                                Encoding);
      }
   }

   /**
    * Returns the <code>Encoding</code> attribute
    *
    * @return the <code>Encoding</code> attribute
    */
   public String getEncoding() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_ENCODING);
   }

   /**
    * Adds childe Node
    *
    * @param node childe Node
    * @return the new node in the tree.
    */
   public Node appendChild(Node node) {

      Node result = null;

      if (this._state == MODE_SIGN) {
         result = this._constructionElement.appendChild(node);
      }

      return result;
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_OBJECT;
   }
}
