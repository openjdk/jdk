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
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * Handles <code>&lt;ds:SignatureProperties&gt;</code> elements
 * This Element holds {@link SignatureProperty} that contian additional information items
 * concerning the generation of the signature.
 * for example, data-time stamp, serial number of cryptographic hardware.
 *
 * @author Christian Geuer-Pollmann
 *
 */
public class SignatureProperties extends SignatureElementProxy {

   /**
    * Constructor SignatureProperties
    *
    * @param doc
    */
   public SignatureProperties(Document doc) {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Constructs {@link SignatureProperties} from {@link Element}
    * @param element <code>SignatureProperties</code> elementt
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @throws XMLSecurityException
    */
   public SignatureProperties(Element element, String BaseURI)
           throws XMLSecurityException {
      super(element, BaseURI);
   }

   /**
    * Return the nonnegative number of added SignatureProperty elements.
    *
    * @return the number of SignatureProperty elements
    */
   public int getLength() {

         Element[] propertyElems =
            XMLUtils.selectDsNodes(this._constructionElement,
                                     Constants._TAG_SIGNATUREPROPERTY
                                    );

         return propertyElems.length;
   }

   /**
    * Return the <it>i</it><sup>th</sup> SignatureProperty.  Valid <code>i</code>
    * values are 0 to <code>{link@ getSize}-1</code>.
    *
    * @param i Index of the requested {@link SignatureProperty}
    * @return the <it>i</it><sup>th</sup> SignatureProperty
    * @throws XMLSignatureException
    */
   public SignatureProperty item(int i) throws XMLSignatureException {
          try {
         Element propertyElem =
            XMLUtils.selectDsNode(this._constructionElement,
                                 Constants._TAG_SIGNATUREPROPERTY,
                                 i );

         if (propertyElem == null) {
            return null;
         }
         return new SignatureProperty(propertyElem, this._baseURI);
      } catch (XMLSecurityException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /**
    * Sets the <code>Id</code> attribute
    *
    * @param Id the <code>Id</code> attribute
    */
   public void setId(String Id) {

      if ((Id != null)) {
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
    * Method addSignatureProperty
    *
    * @param sp
    */
   public void addSignatureProperty(SignatureProperty sp) {
      this._constructionElement.appendChild(sp.getElement());
      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_SIGNATUREPROPERTIES;
   }
}
