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
package com.sun.org.apache.xml.internal.security.keys.content;



import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 *
 * @author $Author: raul $
 */
public class KeyName extends SignatureElementProxy implements KeyInfoContent {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(KeyName.class.getName());

   /**
    * Constructor KeyName
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public KeyName(Element element, String BaseURI) throws XMLSecurityException {
      super(element, BaseURI);
   }

   /**
    * Constructor KeyName
    *
    * @param doc
    * @param keyName
    */
   public KeyName(Document doc, String keyName) {

      super(doc);

      this.addText(keyName);
   }

   /**
    * Method getKeyName
    *
    * @return key name
    */
   public String getKeyName() {
      return this.getTextFromTextChild();
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_KEYNAME;
   }
}
