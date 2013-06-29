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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
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
import org.xml.sax.SAXException;


/**
 * The RetrievalMethodResolver can retrieve public keys and certificates from
 * other locations. The location is specified using the ds:RetrievalMethod
 * element which points to the location. This includes the handling of raw
 * (binary) X.509 certificate which are not encapsulated in an XML structure.
 * If the retrieval process encounters an element which the
 * RetrievalMethodResolver cannot handle itself, resolving of the extracted
 * element is delegated back to the KeyResolver mechanism.
 *
 * @author $Author: mullan $ modified by Dave Garcia
 */
public class RetrievalMethodResolver extends KeyResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                        RetrievalMethodResolver.class.getName());

   /**
    * Method engineResolvePublicKey
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public PublicKey engineLookupAndResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage)
              {
           if  (!XMLUtils.elementIsInSignatureSpace(element,
               Constants._TAG_RETRIEVALMETHOD)) {
                   return null;
           }

      try {
                   //Create a retrieval method over the given element
                   RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
               String type = rm.getType();
                   XMLSignatureInput resource=resolveInput(rm,BaseURI);
           if (RetrievalMethod.TYPE_RAWX509.equals(type)) {
                //a raw certificate, direct parsing is done!
                X509Certificate cert=getRawCertificate(resource);
                                if (cert != null) {
                                 return cert.getPublicKey();
                            }
                                return null;
            };
                        Element e = obtainRefrenceElement(resource);
                        return resolveKey(e,BaseURI,storage);
          } catch (XMLSecurityException ex) {
         log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);
      } catch (CertificateException ex) {
         log.log(java.util.logging.Level.FINE, "CertificateException", ex);
      } catch (IOException ex) {
         log.log(java.util.logging.Level.FINE, "IOException", ex);
      } catch (ParserConfigurationException e) {
                  log.log(java.util.logging.Level.FINE, "ParserConfigurationException", e);
          } catch (SAXException e) {
                 log.log(java.util.logging.Level.FINE, "SAXException", e);
          }
      return null;
   }

   static private Element obtainRefrenceElement(XMLSignatureInput resource) throws CanonicalizationException, ParserConfigurationException, IOException, SAXException, KeyResolverException {
           Element e;
           if (resource.isElement()){
                   e=(Element) resource.getSubNode();
           } else if (resource.isNodeSet()) {
                   //Retrieved resource is a nodeSet
                   e=getDocumentElement(resource.getNodeSet());
           } else {
                   //Retrieved resource is an inputStream
                   byte inputBytes[] = resource.getBytes();
                   e = getDocFromBytes(inputBytes);
                   //otherwise, we parse the resource, create an Element and delegate
                   if (log.isLoggable(java.util.logging.Level.FINE))
                           log.log(java.util.logging.Level.FINE, "we have to parse " + inputBytes.length + " bytes");
           }
           return e;
   }

   /**
    * Method engineResolveX509Certificate
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public X509Certificate engineLookupResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage)
              {
           if  (!XMLUtils.elementIsInSignatureSpace(element,
               Constants._TAG_RETRIEVALMETHOD)) {
                   return null;
           }

           try {
         RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
                 String type = rm.getType();
                 XMLSignatureInput resource=resolveInput(rm,BaseURI);
                 if (RetrievalMethod.TYPE_RAWX509.equals(type)) {
                X509Certificate cert=getRawCertificate(resource);
            return cert;
                 }
                 Element e = obtainRefrenceElement(resource);
                 return resolveCertificate(e,BaseURI,storage);
      } catch (XMLSecurityException ex) {
         log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);
      } catch (CertificateException ex) {
         log.log(java.util.logging.Level.FINE, "CertificateException", ex);
      } catch (IOException ex) {
         log.log(java.util.logging.Level.FINE, "IOException", ex);
          } catch (ParserConfigurationException e) {
                  log.log(java.util.logging.Level.FINE, "ParserConfigurationException", e);
          } catch (SAXException e) {
                 log.log(java.util.logging.Level.FINE, "SAXException", e);
          }
      return null;
   }

   /**
    * Retrieves a x509Certificate from the given information
    * @param e
    * @param BaseURI
    * @param storage
    * @return
    * @throws KeyResolverException
    */
   static private X509Certificate resolveCertificate(Element e,String BaseURI,StorageResolver storage) throws KeyResolverException{
                  if (log.isLoggable(java.util.logging.Level.FINE))
                          log.log(java.util.logging.Level.FINE, "Now we have a {" + e.getNamespaceURI() + "}"+ e.getLocalName() + " Element");
                  //An element has been provided
          if (e != null) {
                          return KeyResolver.getX509Certificate(e,BaseURI, storage);
                  }
                  return null;
   }

   /**
    * Retrieves a x509Certificate from the given information
    * @param e
    * @param BaseURI
    * @param storage
    * @return
    * @throws KeyResolverException
    */
   static private PublicKey resolveKey(Element e,String BaseURI,StorageResolver storage) throws KeyResolverException{
                  if (log.isLoggable(java.util.logging.Level.FINE))
                          log.log(java.util.logging.Level.FINE, "Now we have a {" + e.getNamespaceURI() + "}"+ e.getLocalName() + " Element");
                  //An element has been provided
          if (e != null) {
                          return KeyResolver.getPublicKey(e,BaseURI, storage);
                  }
                  return null;
   }

   static private X509Certificate getRawCertificate(XMLSignatureInput resource) throws CanonicalizationException, IOException, CertificateException{
           byte inputBytes[] = resource.getBytes();
       // if the resource stores a raw certificate, we have to handle it
       CertificateFactory certFact =CertificateFactory.getInstance(XMLX509Certificate.JCA_CERT_ID);
       X509Certificate cert =(X509Certificate) certFact.generateCertificate(new ByteArrayInputStream(inputBytes));
       return cert;
   }
   /**
    * Resolves the input from the given retrieval method
    * @return
    * @throws XMLSecurityException
    */
   static private XMLSignatureInput resolveInput(RetrievalMethod rm,String BaseURI) throws XMLSecurityException{
       Attr uri = rm.getURIAttr();
           //Apply the trnasforms
       Transforms transforms = rm.getTransforms();
       ResourceResolver resRes = ResourceResolver.getInstance(uri, BaseURI);
       if (resRes != null) {
          XMLSignatureInput resource = resRes.resolve(uri, BaseURI);
          if (transforms != null) {
                  log.log(java.util.logging.Level.FINE, "We have Transforms");
                          resource = transforms.performTransforms(resource);
          }
                  return resource;
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
   static Element getDocFromBytes(byte[] bytes) throws KeyResolverException {
      try {
         javax.xml.parsers.DocumentBuilderFactory dbf =javax.xml.parsers.DocumentBuilderFactory.newInstance();
         dbf.setNamespaceAware(true);
         dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
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
   public javax.crypto.SecretKey engineLookupAndResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage)
   {
      return null;
   }

   static Element getDocumentElement(Set<Node> set) {
           Iterator<Node> it=set.iterator();
           Element e=null;
           while (it.hasNext()) {
                   Node currentNode=it.next();
                   if (currentNode != null && currentNode.getNodeType() == Node.ELEMENT_NODE) {
                           e=(Element)currentNode;
                           break;
                   }

           }
           List<Element> parents=new ArrayList<Element>(10);

                //Obtain all the parents of the elemnt
                while (e != null) {
                        parents.add(e);
                        Node n=e.getParentNode();
                        if (n == null || n.getNodeType() != Node.ELEMENT_NODE) {
                                break;
                        }
                        e=(Element)n;
                }
                //Visit them in reverse order.
                ListIterator<Element> it2=parents.listIterator(parents.size()-1);
                Element ele=null;
                while (it2.hasPrevious()) {
                        ele=it2.previous();
                        if (set.contains(ele)) {
                                return ele;
                        }
        }
                return null;
   }
}
