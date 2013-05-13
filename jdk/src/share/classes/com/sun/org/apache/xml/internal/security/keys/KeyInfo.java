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
package com.sun.org.apache.xml.internal.security.keys;



import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.encryption.EncryptedKey;
import com.sun.org.apache.xml.internal.security.encryption.XMLCipher;
import com.sun.org.apache.xml.internal.security.encryption.XMLEncryptionException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.KeyName;
import com.sun.org.apache.xml.internal.security.keys.content.KeyValue;
import com.sun.org.apache.xml.internal.security.keys.content.MgmtData;
import com.sun.org.apache.xml.internal.security.keys.content.PGPData;
import com.sun.org.apache.xml.internal.security.keys.content.RetrievalMethod;
import com.sun.org.apache.xml.internal.security.keys.content.SPKIData;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.DSAKeyValue;
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.RSAKeyValue;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolver;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.utils.EncryptionConstants;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.IdResolver;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * This class stand for KeyInfo Element that may contain keys, names,
 * certificates and other public key management information,
 * such as in-band key distribution or key agreement data.
 * <BR />
 * KeyInfo Element has two basic functions:
 * One is KeyResolve for getting the public key in signature validation processing.
 * the other one is toElement for getting the element in signature generation processing.
 * <BR />
 * The <CODE>lengthXXX()</CODE> methods provide access to the internal Key
 * objects:
 * <UL>
 * <LI>If the <CODE>KeyInfo</CODE> was constructed from an Element
 * (Signature verification), the <CODE>lengthXXX()</CODE> methods searches
 * for child elements of <CODE>ds:KeyInfo</CODE> for known types. </LI>
 * <LI>If the <CODE>KeyInfo</CODE> was constructed from scratch (during
 * Signature generation), the <CODE>lengthXXX()</CODE> methods return the number
 * of <CODE>XXXs</CODE> objects already passed to the KeyInfo</LI>
 * </UL>
 * <BR />
 * The <CODE>addXXX()</CODE> methods are used for adding Objects of the
 * appropriate type to the <CODE>KeyInfo</CODE>. This is used during signature
 * generation.
 * <BR />
 * The <CODE>itemXXX(int i)</CODE> methods return the i'th object of the
 * corresponding type.
 * <BR />
 * The <CODE>containsXXX()</CODE> methods return <I>whether</I> the KeyInfo
 * contains the corresponding type.
 *
 * @author $Author: mullan $
 */
public class KeyInfo extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(KeyInfo.class.getName());
    List<X509Data> x509Datas=null;
    List<EncryptedKey> encryptedKeys=null;

    static final List<StorageResolver> nullList;
    static {
        List<StorageResolver> list = new ArrayList<StorageResolver>(1);
        list.add(null);
        nullList = Collections.unmodifiableList(list);
    }

   /**
    * Constructor KeyInfo
    * @param doc
    */
   public KeyInfo(Document doc) {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);

   }

   /**
    * Constructor KeyInfo
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public KeyInfo(Element element, String BaseURI) throws XMLSecurityException {
      super(element, BaseURI);

      Attr attr = element.getAttributeNodeNS(null, "Id");
      if (attr != null) {
          element.setIdAttributeNode(attr, true);
      }
   }

   /**
    * Sets the <code>Id</code> attribute
    *
    * @param Id ID
    */
   public void setId(String Id) {

      if (Id != null) {
          setLocalIdAttribute(Constants._ATT_ID, Id);
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
    * Method addKeyName
    *
    * @param keynameString
    */
   public void addKeyName(String keynameString) {
      this.add(new KeyName(this._doc, keynameString));
   }

   /**
    * Method add
    *
    * @param keyname
    */
   public void add(KeyName keyname) {

         this._constructionElement.appendChild(keyname.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addKeyValue
    *
    * @param pk
    */
   public void addKeyValue(PublicKey pk) {
      this.add(new KeyValue(this._doc, pk));
   }

   /**
    * Method addKeyValue
    *
    * @param unknownKeyValueElement
    */
   public void addKeyValue(Element unknownKeyValueElement) {
      this.add(new KeyValue(this._doc, unknownKeyValueElement));
   }

   /**
    * Method add
    *
    * @param dsakeyvalue
    */
   public void add(DSAKeyValue dsakeyvalue) {
      this.add(new KeyValue(this._doc, dsakeyvalue));
   }

   /**
    * Method add
    *
    * @param rsakeyvalue
    */
   public void add(RSAKeyValue rsakeyvalue) {
      this.add(new KeyValue(this._doc, rsakeyvalue));
   }

   /**
    * Method add
    *
    * @param pk
    */
   public void add(PublicKey pk) {
      this.add(new KeyValue(this._doc, pk));
   }

   /**
    * Method add
    *
    * @param keyvalue
    */
   public void add(KeyValue keyvalue) {
         this._constructionElement.appendChild(keyvalue.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addMgmtData
    *
    * @param mgmtdata
    */
   public void addMgmtData(String mgmtdata) {
      this.add(new MgmtData(this._doc, mgmtdata));
   }

   /**
    * Method add
    *
    * @param mgmtdata
    */
   public void add(MgmtData mgmtdata) {
         this._constructionElement.appendChild(mgmtdata.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addPGPData
    *
    * @param pgpdata
    */
   public void add(PGPData pgpdata) {
         this._constructionElement.appendChild(pgpdata.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addRetrievalMethod
    *
    * @param URI
    * @param transforms
    * @param Type
    */
   public void addRetrievalMethod(String URI, Transforms transforms,
                                  String Type) {
      this.add(new RetrievalMethod(this._doc, URI, transforms, Type));
   }

   /**
    * Method add
    *
    * @param retrievalmethod
    */
   public void add(RetrievalMethod retrievalmethod) {
         this._constructionElement.appendChild(retrievalmethod.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method add
    *
    * @param spkidata
    */
   public void add(SPKIData spkidata) {
         this._constructionElement.appendChild(spkidata.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addX509Data
    *
    * @param x509data
    */
   public void add(X509Data x509data) {
          if (x509Datas==null)
                  x509Datas=new ArrayList<X509Data>();
          x509Datas.add(x509data);
         this._constructionElement.appendChild(x509data.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

        /**
         * Method addEncryptedKey
         *
         * @param encryptedKey
         * @throws XMLEncryptionException
         */

        public void add(EncryptedKey encryptedKey)
                throws XMLEncryptionException {
                        if (encryptedKeys==null)
                                encryptedKeys=new ArrayList<EncryptedKey>();
                        encryptedKeys.add(encryptedKey);
                        XMLCipher cipher = XMLCipher.getInstance();
                        this._constructionElement.appendChild(cipher.martial(encryptedKey));
        }

   /**
    * Method addUnknownElement
    *
    * @param element
    */
   public void addUnknownElement(Element element) {
         this._constructionElement.appendChild(element);
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method lengthKeyName
    *
    * @return the number of the KeyName tags
    */
   public int lengthKeyName() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_KEYNAME);
   }

   /**
    * Method lengthKeyValue
    *
    *@return the number of the KeyValue tags
    */
   public int lengthKeyValue() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_KEYVALUE);
   }

   /**
    * Method lengthMgmtData
    *
    *@return the number of the MgmtData tags
    */
   public int lengthMgmtData() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_MGMTDATA);
   }

   /**
    * Method lengthPGPData
    *
    *@return the number of the PGPDat. tags
    */
   public int lengthPGPData() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_PGPDATA);
   }

   /**
    * Method lengthRetrievalMethod
    *
    *@return the number of the RetrievalMethod tags
    */
   public int lengthRetrievalMethod() {
      return this.length(Constants.SignatureSpecNS,
                         Constants._TAG_RETRIEVALMETHOD);
   }

   /**
    * Method lengthSPKIData
    *
    *@return the number of the SPKIData tags
    */
   public int lengthSPKIData() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_SPKIDATA);
   }

   /**
    * Method lengthX509Data
    *
    *@return the number of the X509Data tags
    */
   public int lengthX509Data() {
           if (x509Datas!=null) {
                   return x509Datas.size();
           }
      return this.length(Constants.SignatureSpecNS, Constants._TAG_X509DATA);
   }

   /**
    * Method lengthUnknownElement
    * NOTE posibly buggy.
    *@return the number of the UnknownElement tags
    */
   public int lengthUnknownElement() {

      int res = 0;
      NodeList nl = this._constructionElement.getChildNodes();

      for (int i = 0; i < nl.getLength(); i++) {
         Node current = nl.item(i);

         /**
          * $todo$ using this method, we don't see unknown Elements
          *  from Signature NS; revisit
          */
         if ((current.getNodeType() == Node.ELEMENT_NODE)
                 && current.getNamespaceURI()
                    .equals(Constants.SignatureSpecNS)) {
            res++;
         }
      }

      return res;
   }

   /**
    * Method itemKeyName
    *
    * @param i
    * @return the asked KeyName element, null if the index is too big
    * @throws XMLSecurityException
    */
   public KeyName itemKeyName(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_KEYNAME,i);

      if (e != null) {
         return new KeyName(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemKeyValue
    *
    * @param i
    * @return the asked KeyValue element, null if the index is too big
    * @throws XMLSecurityException
    */
   public KeyValue itemKeyValue(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_KEYVALUE,i);

      if (e != null) {
         return new KeyValue(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemMgmtData
    *
    * @param i
    *@return the asked MgmtData element, null if the index is too big
    * @throws XMLSecurityException
    */
   public MgmtData itemMgmtData(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_MGMTDATA,i);

      if (e != null) {
         return new MgmtData(e, this._baseURI);
      }
       return null;
   }

   /**
    * Method itemPGPData
    *
    * @param i
    *@return the asked PGPData element, null if the index is too big
    * @throws XMLSecurityException
    */
   public PGPData itemPGPData(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_PGPDATA,i);

      if (e != null) {
         return new PGPData(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemRetrievalMethod
    *
    * @param i
    *@return the asked RetrievalMethod element, null if the index is too big
    * @throws XMLSecurityException
    */
   public RetrievalMethod itemRetrievalMethod(int i)
           throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_RETRIEVALMETHOD,i);

      if (e != null) {
         return new RetrievalMethod(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemSPKIData
    *
    * @param i
    *@return the asked SPKIData element, null if the index is too big
    * @throws XMLSecurityException
    */
   public SPKIData itemSPKIData(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_SPKIDATA,i);

      if (e != null) {
         return new SPKIData(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemX509Data
    *@return the asked X509Data element, null if the index is too big
    * @param i
    *
    * @throws XMLSecurityException
    */
   public X509Data itemX509Data(int i) throws XMLSecurityException {
           if (x509Datas!=null) {
                   return x509Datas.get(i);
           }
      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_X509DATA,i);

      if (e != null) {
         return new X509Data(e, this._baseURI);
      }
      return null;
   }

   /**
        * Method itemEncryptedKey
        *
        * @param i
        * @return the asked EncryptedKey element, null if the index is too big
        * @throws XMLSecurityException
        */

        public EncryptedKey itemEncryptedKey(int i) throws XMLSecurityException {
                if (encryptedKeys!=null) {
                        return encryptedKeys.get(i);
                }
                Element e =
                        XMLUtils.selectXencNode(this._constructionElement.getFirstChild(),
                                                                                  EncryptionConstants._TAG_ENCRYPTEDKEY,i);

                if (e != null) {
                        XMLCipher cipher = XMLCipher.getInstance();
                        cipher.init(XMLCipher.UNWRAP_MODE, null);
                        return cipher.loadEncryptedKey(e);
                }
                return null;
   }

   /**
    * Method itemUnknownElement
    *
    * @param i index
    * @return the element number of the unknown elemens
    */
   public Element itemUnknownElement(int i) {

      NodeList nl = this._constructionElement.getChildNodes();
      int res = 0;

      for (int j = 0; j < nl.getLength(); j++) {
         Node current = nl.item(j);

         /**
          * $todo$ using this method, we don't see unknown Elements
          *  from Signature NS; revisit
          */
         if ((current.getNodeType() == Node.ELEMENT_NODE)
                 && current.getNamespaceURI()
                    .equals(Constants.SignatureSpecNS)) {
            res++;

            if (res == i) {
               return (Element) current;
            }
         }
      }

      return null;
   }

   /**
    * Method isEmpty
    *
    * @return true if the element has no descedants.
    */
   public boolean isEmpty() {
      return this._constructionElement.getFirstChild()==null;
   }

   /**
    * Method containsKeyName
    *
    * @return If the KeyInfo contains a KeyName node
    */
   public boolean containsKeyName() {
      return this.lengthKeyName() > 0;
   }

   /**
    * Method containsKeyValue
    *
    * @return If the KeyInfo contains a KeyValue node
    */
   public boolean containsKeyValue() {
      return this.lengthKeyValue() > 0;
   }

   /**
    * Method containsMgmtData
    *
    * @return If the KeyInfo contains a MgmtData node
    */
   public boolean containsMgmtData() {
      return this.lengthMgmtData() > 0;
   }

   /**
    * Method containsPGPData
    *
    * @return If the KeyInfo contains a PGPData node
    */
   public boolean containsPGPData() {
      return this.lengthPGPData() > 0;
   }

   /**
    * Method containsRetrievalMethod
    *
    * @return If the KeyInfo contains a RetrievalMethod node
    */
   public boolean containsRetrievalMethod() {
      return this.lengthRetrievalMethod() > 0;
   }

   /**
    * Method containsSPKIData
    *
    * @return If the KeyInfo contains a SPKIData node
    */
   public boolean containsSPKIData() {
      return this.lengthSPKIData() > 0;
   }

   /**
    * Method containsUnknownElement
    *
    * @return If the KeyInfo contains a UnknownElement node
    */
   public boolean containsUnknownElement() {
      return this.lengthUnknownElement() > 0;
   }

   /**
    * Method containsX509Data
    *
    * @return If the KeyInfo contains a X509Data node
    */
   public boolean containsX509Data() {
      return this.lengthX509Data() > 0;
   }

   /**
    * This method returns the public key.
    *
    * @return If the KeyInfo contains a PublicKey node
    * @throws KeyResolverException
    */

   public PublicKey getPublicKey() throws KeyResolverException {

      PublicKey pk = this.getPublicKeyFromInternalResolvers();

      if (pk != null) {
         log.log(java.util.logging.Level.FINE, "I could find a key using the per-KeyInfo key resolvers");

         return pk;
      }
      log.log(java.util.logging.Level.FINE, "I couldn't find a key using the per-KeyInfo key resolvers");

      pk = this.getPublicKeyFromStaticResolvers();

      if (pk != null) {
         log.log(java.util.logging.Level.FINE, "I could find a key using the system-wide key resolvers");

         return pk;
      }
      log.log(java.util.logging.Level.FINE, "I couldn't find a key using the system-wide key resolvers");

      return null;
   }

    /**
     * Searches the library wide keyresolvers for public keys
     *
     * @return The public key contained in this Node.
     * @throws KeyResolverException
     */
    PublicKey getPublicKeyFromStaticResolvers() throws KeyResolverException {
        Iterator<KeyResolverSpi> it = KeyResolver.iterator();
        while (it.hasNext()) {
            KeyResolverSpi keyResolver = it.next();
            Node currentChild = this._constructionElement.getFirstChild();
            String uri = this.getBaseURI();
            while (currentChild != null) {
                if (currentChild.getNodeType() == Node.ELEMENT_NODE) {
                    for (StorageResolver storage : _storageResolvers) {
                        PublicKey pk =
                            keyResolver.engineLookupAndResolvePublicKey(
                                (Element) currentChild, uri, storage
                            );

                        if (pk != null) {
                            return pk;
                        }
                    }
                }
                currentChild = currentChild.getNextSibling();
            }
        }
        return null;
    }

   /**
    * Searches the per-KeyInfo keyresolvers for public keys
    *
    * @return The publick contained in this Node.
    * @throws KeyResolverException
    */
   PublicKey getPublicKeyFromInternalResolvers() throws KeyResolverException {
          int length=lengthInternalKeyResolver();
          int storageLength=this._storageResolvers.size();
      for (int i = 0; i < length; i++) {
         KeyResolverSpi keyResolver = this.itemInternalKeyResolver(i);
         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Try " + keyResolver.getClass().getName());

         Node currentChild=this._constructionElement.getFirstChild();
         String uri=this.getBaseURI();
         while (currentChild!=null)      {
            if (currentChild.getNodeType() == Node.ELEMENT_NODE) {
               for (int k = 0; k < storageLength; k++) {
                   StorageResolver storage =
                       this._storageResolvers.get(k);
                   PublicKey pk = keyResolver
                           .engineLookupAndResolvePublicKey((Element) currentChild, uri, storage);

                     if (pk != null) {
                         return pk;
                     }
                  }
            }
            currentChild=currentChild.getNextSibling();
         }
      }

      return null;
   }

   /**
    * Method getX509Certificate
    *
    * @return The certificate contined in this KeyInfo
    * @throws KeyResolverException
    */
   public X509Certificate getX509Certificate() throws KeyResolverException {

      // First search using the individual resolvers from the user
      X509Certificate cert = this.getX509CertificateFromInternalResolvers();

      if (cert != null) {
         log.log(java.util.logging.Level.FINE,
            "I could find a X509Certificate using the per-KeyInfo key resolvers");

         return cert;
      }
      log.log(java.util.logging.Level.FINE,
            "I couldn't find a X509Certificate using the per-KeyInfo key resolvers");


      // Then use the system-wide Resolvers
      cert = this.getX509CertificateFromStaticResolvers();

      if (cert != null) {
         log.log(java.util.logging.Level.FINE,
            "I could find a X509Certificate using the system-wide key resolvers");

         return cert;
      }
      log.log(java.util.logging.Level.FINE,
            "I couldn't find a X509Certificate using the system-wide key resolvers");


      return null;
   }

    /**
     * This method uses each System-wide {@link KeyResolver} to search the
     * child elements. Each combination of {@link KeyResolver} and child element
     * is checked against all {@link StorageResolver}s.
     *
     * @return The certificate contained in this KeyInfo
     * @throws KeyResolverException
     */
    X509Certificate getX509CertificateFromStaticResolvers()
        throws KeyResolverException {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE,
                "Start getX509CertificateFromStaticResolvers() with " + KeyResolver.length()
                + " resolvers"
            );
        }
        String uri = this.getBaseURI();
        Iterator<KeyResolverSpi> it = KeyResolver.iterator();
        while (it.hasNext()) {
            KeyResolverSpi keyResolver = it.next();
            X509Certificate cert = applyCurrentResolver(uri, keyResolver);
            if (cert != null) {
                return cert;
            }
        }
        return null;
    }

    private X509Certificate applyCurrentResolver(
        String uri, KeyResolverSpi keyResolver
    ) throws KeyResolverException {
        Node currentChild = this._constructionElement.getFirstChild();
        while (currentChild != null)      {
            if (currentChild.getNodeType() == Node.ELEMENT_NODE) {
                for (StorageResolver storage : _storageResolvers) {
                    X509Certificate cert =
                        keyResolver.engineLookupResolveX509Certificate(
                            (Element) currentChild, uri, storage
                        );

                    if (cert != null) {
                        return cert;
                    }
                }
            }
            currentChild = currentChild.getNextSibling();
        }
        return null;
    }

    /**
     * Method getX509CertificateFromInternalResolvers
     *
     * @return The certificate contined in this KeyInfo
     * @throws KeyResolverException
     */
    X509Certificate getX509CertificateFromInternalResolvers()
        throws KeyResolverException {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE,
                "Start getX509CertificateFromInternalResolvers() with "
                + this.lengthInternalKeyResolver() + " resolvers"
            );
        }
        String uri = this.getBaseURI();
        for (KeyResolverSpi keyResolver : _internalKeyResolvers) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "Try " + keyResolver.getClass().getName());
            }
            X509Certificate cert = applyCurrentResolver(uri, keyResolver);
            if (cert != null) {
                return cert;
            }
        }

        return null;
    }

   /**
    * This method returns a secret (symmetric) key. This is for XML Encryption.
    * @return the secret key contained in this KeyInfo
    * @throws KeyResolverException
    */
   public SecretKey getSecretKey() throws KeyResolverException {
      SecretKey sk = this.getSecretKeyFromInternalResolvers();

      if (sk != null) {
         log.log(java.util.logging.Level.FINE, "I could find a secret key using the per-KeyInfo key resolvers");

         return sk;
      }
      log.log(java.util.logging.Level.FINE, "I couldn't find a secret key using the per-KeyInfo key resolvers");


      sk = this.getSecretKeyFromStaticResolvers();

      if (sk != null) {
         log.log(java.util.logging.Level.FINE, "I could find a secret key using the system-wide key resolvers");

         return sk;
      }
      log.log(java.util.logging.Level.FINE, "I couldn't find a secret key using the system-wide key resolvers");


      return null;
   }

   /**
    * Searches the library wide keyresolvers for Secret keys
    *
    * @return the secret key contained in this KeyInfo
    * @throws KeyResolverException
    */

   SecretKey getSecretKeyFromStaticResolvers() throws KeyResolverException {
          final int length=KeyResolver.length();
          int storageLength=this._storageResolvers.size();
          Iterator<KeyResolverSpi> it = KeyResolver.iterator();
      for (int i = 0; i < length; i++) {
         KeyResolverSpi keyResolver = it.next();

         Node currentChild=this._constructionElement.getFirstChild();
         String uri=this.getBaseURI();
         while (currentChild!=null)      {
            if (currentChild.getNodeType() == Node.ELEMENT_NODE) {
                  for (int k = 0; k < storageLength; k++) {
                     StorageResolver storage =
                        this._storageResolvers.get(k);

                     SecretKey sk =
                           keyResolver.engineLookupAndResolveSecretKey((Element) currentChild,
                                                        uri,
                                                        storage);

                     if (sk != null) {
                        return sk;
                     }
                  }
            }
            currentChild=currentChild.getNextSibling();
         }
      }
      return null;
   }

   /**
    * Searches the per-KeyInfo keyresolvers for secret keys
    *
    * @return the secret key contained in this KeyInfo
    * @throws KeyResolverException
    */

   SecretKey getSecretKeyFromInternalResolvers() throws KeyResolverException {
           int storageLength=this._storageResolvers.size();
      for (int i = 0; i < this.lengthInternalKeyResolver(); i++) {
         KeyResolverSpi keyResolver = this.itemInternalKeyResolver(i);
         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Try " + keyResolver.getClass().getName());

         Node currentChild=this._constructionElement.getFirstChild();
         String uri=this.getBaseURI();
         while (currentChild!=null)      {
            if (currentChild.getNodeType() == Node.ELEMENT_NODE) {
               for (int k = 0; k < storageLength; k++) {
                     StorageResolver storage =
                        this._storageResolvers.get(k);

                     SecretKey sk = keyResolver
                           .engineLookupAndResolveSecretKey((Element) currentChild, uri, storage);

                     if (sk != null) {
                        return sk;
                     }
                }
             }
            currentChild=currentChild.getNextSibling();
         }
      }

      return null;
   }

   /**
    * Stores the individual (per-KeyInfo) {@link KeyResolver}s
    */
   List<KeyResolverSpi> _internalKeyResolvers = new ArrayList<KeyResolverSpi>();

   /**
    * This method is used to add a custom {@link KeyResolverSpi} to a KeyInfo
    * object.
    *
    * @param realKeyResolver
    */
   public void registerInternalKeyResolver(KeyResolverSpi realKeyResolver) {
           if (_internalKeyResolvers==null) {
                   _internalKeyResolvers=new ArrayList<KeyResolverSpi>();
           }
      this._internalKeyResolvers.add(realKeyResolver);
   }

   /**
    * Method lengthInternalKeyResolver
    * @return the length of the key
    */
   int lengthInternalKeyResolver() {
           if (_internalKeyResolvers==null)
                   return 0;
      return this._internalKeyResolvers.size();
   }

   /**
    * Method itemInternalKeyResolver
    *
    * @param i the index
    * @return the KeyResolverSpi for the index.
    */
   KeyResolverSpi itemInternalKeyResolver(int i) {
      return this._internalKeyResolvers.get(i);
   }

   /** Field _storageResolvers */
   private List<StorageResolver> _storageResolvers = nullList;

   /**
    * Method addStorageResolver
    *
    * @param storageResolver
    */
   public void addStorageResolver(StorageResolver storageResolver) {
           if  (_storageResolvers == nullList  ){
                   _storageResolvers=new ArrayList<StorageResolver>();
           }
         this._storageResolvers.add(storageResolver);

   }

   //J-
   static boolean _alreadyInitialized = false;
   /** init the keyinfo (Still needed?)*/
   public static void init() {

      if (!KeyInfo._alreadyInitialized) {
         if (KeyInfo.log == null) {

            /**
             * $todo$ why the hell does the static initialization from the
             *  start not work ?
             */
            KeyInfo.log =
                    java.util.logging.Logger.getLogger(KeyInfo.class.getName());

            log.log(java.util.logging.Level.SEVERE, "Had to assign log in the init() function");
         }

         // KeyInfo._contentHandlerHash = new HashMap(10);
         KeyInfo._alreadyInitialized = true;
      }
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_KEYINFO;
   }
}
