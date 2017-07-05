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
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;



import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.RetrievalMethod;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509Certificate;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolver;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * The RetrievalMethodResolver can retrieve public keys and certificates from
 * other locations. The location is specified using the ds:RetrievalMethod
 * element which points to the location. This includes the handling of raw
 * (binary) X.509 certificate which are not encapsulated in an XML structure.
 * If the retrieval process encounters an element which the
 * RetrievalMethodResolver cannot handle itself, resolving of the extracted
 * element is delegated back to the KeyResolver mechanism.
 *
 * @author $Author: raul $
 */
public class RetrievalMethodResolver extends KeyResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                        RetrievalMethodResolver.class.getName());

   /**
    * Method engineCanResolve
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public boolean engineCanResolve(Element element, String BaseURI,
                                   StorageResolver storage) {

      if
         (!XMLUtils.elementIsInSignatureSpace(element,
                 Constants._TAG_RETRIEVALMETHOD)) {
         return false;
      }

      return true;
   }

   /**
    * Method engineResolvePublicKey
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public PublicKey engineResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage)
              {

      try {
         RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
         Attr uri = rm.getURIAttr();

         // type can be null because it's optional
         String type = rm.getType();
         Transforms transforms = rm.getTransforms();
         ResourceResolver resRes = ResourceResolver.getInstance(uri, BaseURI);

         if (resRes != null) {
            XMLSignatureInput resource = resRes.resolve(uri, BaseURI);
            if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Before applying Transforms, resource has "
                      + resource.getBytes().length + "bytes");

            if (transforms != null) {
               if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "We have Transforms");

               resource = transforms.performTransforms(resource);
            }
            if (true) {
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "After applying Transforms, resource has "
                      + resource.getBytes().length + "bytes");
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Resolved to resource " + resource.getSourceURI());
            }

            byte inputBytes[] = resource.getBytes();

            if ((type != null) && type.equals(RetrievalMethod.TYPE_RAWX509)) {

               // if the resource stores a raw certificate, we have to handle it
               CertificateFactory certFact =
                  CertificateFactory
                     .getInstance(XMLX509Certificate.JCA_CERT_ID);
               X509Certificate cert =
                  (X509Certificate) certFact
                     .generateCertificate(new ByteArrayInputStream(inputBytes));

               if (cert != null) {
                  return cert.getPublicKey();
               }
            } else {

               // otherwise, we parse the resource, create an Element and delegate
                if (true)
                        if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "we have to parse " + inputBytes.length + " bytes");

               Element e = this.getDocFromBytes(inputBytes);
               if (true)
                    if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Now we have a {" + e.getNamespaceURI() + "}"
                         + e.getLocalName() + " Element");

               if (e != null) {
                  KeyResolver newKeyResolver = KeyResolver.getInstance(getFirstElementChild(e),
                                                  BaseURI, storage);

                  if (newKeyResolver != null) {
                     return newKeyResolver.resolvePublicKey(getFirstElementChild(e), BaseURI,
                                                            storage);
                  }
               }
            }
         }
      } catch (XMLSecurityException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);
      } catch (CertificateException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "CertificateException", ex);
      } catch (IOException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "IOException", ex);
      }

      return null;
   }

   /**
    * Method engineResolveX509Certificate
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public X509Certificate engineResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage)
              {

      try {
         RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
         Attr uri = rm.getURIAttr();
         Transforms transforms = rm.getTransforms();
         if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Asked to resolve URI " + uri);

         ResourceResolver resRes = ResourceResolver.getInstance(uri, BaseURI);

         if (resRes != null) {
            XMLSignatureInput resource = resRes.resolve(uri, BaseURI);
            if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Before applying Transforms, resource has "
                      + resource.getBytes().length + "bytes");

            if (transforms != null) {
               if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "We have Transforms");

               resource = transforms.performTransforms(resource);
            }

            if (true) {
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "After applying Transforms, resource has "
                      + resource.getBytes().length + "bytes");
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Resolved to resource " + resource.getSourceURI());
            }

            byte inputBytes[] = resource.getBytes();

            if ((rm.getType() != null)
                    && rm.getType().equals(RetrievalMethod.TYPE_RAWX509)) {

               // if the resource stores a raw certificate, we have to handle it
               CertificateFactory certFact =
                  CertificateFactory
                     .getInstance(XMLX509Certificate.JCA_CERT_ID);
               X509Certificate cert =
                  (X509Certificate) certFact
                     .generateCertificate(new ByteArrayInputStream(inputBytes));

               if (cert != null) {
                  return cert;
               }
            } else {

               // otherwise, we parse the resource, create an Element and delegate
                if (true)
                        if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "we have to parse " + inputBytes.length + " bytes");

               Element e = this.getDocFromBytes(inputBytes);

               if (true)
                    if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Now we have a {" + e.getNamespaceURI() + "}"
                         + e.getLocalName() + " Element");

               if (e != null) {
                  KeyResolver newKeyResolver = KeyResolver.getInstance(getFirstElementChild(e),
                                                  BaseURI, storage);

                  if (newKeyResolver != null) {
                     return newKeyResolver.resolveX509Certificate(getFirstElementChild(e), BaseURI,
                             storage);
                  }
               }
            }
         }
      } catch (XMLSecurityException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);
      } catch (CertificateException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "CertificateException", ex);
      } catch (IOException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "IOException", ex);
      }

      return null;
   }

   /**
    * Parses a byte array and returns the parsed Element.
    *
    * @param bytes
    * @return the Document Element after parsing bytes
    * @throws KeyResolverException if something goes wrong
    */
   Element getDocFromBytes(byte[] bytes) throws KeyResolverException {

      try {
         javax.xml.parsers.DocumentBuilderFactory dbf =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();

         dbf.setNamespaceAware(true);

         javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
         org.w3c.dom.Document doc =
            db.parse(new java.io.ByteArrayInputStream(bytes));

         return doc.getDocumentElement();
      } catch (org.xml.sax.SAXException ex) {
         throw new KeyResolverException("empty", ex);
      } catch (java.io.IOException ex) {
         throw new KeyResolverException("empty", ex);
      } catch (javax.xml.parsers.ParserConfigurationException ex) {
         throw new KeyResolverException("empty", ex);
      }
   }

   /**
    * Method engineResolveSecretKey
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public javax.crypto.SecretKey engineResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage)
   {
      return null;
   }
   static Element getFirstElementChild(Element e){
            Node n=e.getFirstChild();
            while (n!=null && n.getNodeType()!=Node.ELEMENT_NODE) {
                n=n.getNextSibling();
            }
                return (Element)n;
   }
}
