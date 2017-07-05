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
 * Handles <code>&lt;ds:SignatureProperty&gt;</code> elements
 * Addittional information item concerning the generation of the signature(s) can
 * be placed in this Element
 *
 * @author Christian Geuer-Pollmann
 */
public class SignatureProperty extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                            SignatureProperty.class.getName());

   /**
    * Constructs{@link SignatureProperty} using specified <code>Target</code> attribute
    *
    * @param doc the {@link Document} in which <code>XMLsignature</code> is placed
    * @param Target the <code>Target</code> attribute references the <code>Signature</code> element to which the property applies SignatureProperty
    */
   public SignatureProperty(Document doc, String Target) {
      this(doc, Target, null);
   }

   /**
    * Constructs {@link SignatureProperty} using sepcified <code>Target</code> attribute and <code>Id</code> attribute
    *
    * @param doc the {@link Document} in which <code>XMLsignature</code> is placed
    * @param Target the <code>Target</code> attribute references the <code>Signature</code> element to which the property applies
    * @param Id the <code>Id</code> will be specified by {@link Reference#getURI} in validation
    */
   public SignatureProperty(Document doc, String Target, String Id) {

      super(doc);

      this.setTarget(Target);
      this.setId(Id);
   }

   /**
    * Constructs a {@link SignatureProperty} from an {@link Element}
    * @param element <code>SignatureProperty</code> element
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @throws XMLSecurityException
    */
   public SignatureProperty(Element element, String BaseURI)
           throws XMLSecurityException {
      super(element, BaseURI);
   }

   /**
    *   Sets the <code>Id</code> attribute
    *
    *   @param Id the <code>Id</code> attribute
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
    * Sets the <code>Target</code> attribute
    *
    * @param Target the <code>Target</code> attribute
    */
   public void setTarget(String Target) {

      if ((this._state == MODE_SIGN) && (Target != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_TARGET, Target);
      }
   }

   /**
    * Returns the <code>Target</code> attribute
    *
    * @return the <code>Target</code> attribute
    */
   public String getTarget() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_TARGET);
   }

   /**
    * Method appendChild
    *
    * @param node
    * @return the node in this element.
    */
   public Node appendChild(Node node) {
      return this._constructionElement.appendChild(node);
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_SIGNATUREPROPERTY;
   }
}
