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



import java.io.IOException;
import java.io.OutputStream;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.KeyInfo;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.I18n;
import com.sun.org.apache.xml.internal.security.utils.IdResolver;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.SignerOutputStream;
import com.sun.org.apache.xml.internal.security.utils.UnsyncBufferedOutputStream;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;


/**
 * Handles <code>&lt;ds:Signature&gt;</code> elements.
 * This is the main class that deals with creating and verifying signatures.
 *
 * <p>There are 2 types of constructors for this class. The ones that take a
 * document, baseURI and 1 or more Java Objects. This is mostly used for
 * signing purposes.
 * The other constructor is the one that takes a DOM Element and a BaseURI.
 * This is used mostly with for verifying, when you have a SignatureElement.
 *
 * There are a few different types of methods:
 * <ul><li>The addDocument* methods are used to add References with optional
 * transforms during signing. </li>
 * <li>addKeyInfo* methods are to add Certificates and Keys to the
 * KeyInfo tags during signing. </li>
 * <li>appendObject allows a user to add any XML Structure as an
 * ObjectContainer during signing.</li>
 * <li>sign and checkSignatureValue methods are used to sign and validate the
 * signature. </li></ul>
 *
 * @author $Author: mullan $
 */
public final class XMLSignature extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
   static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(XMLSignature.class.getName());

   //J-
   /** MAC - Required HMAC-SHA1 */
   public static final String ALGO_ID_MAC_HMAC_SHA1 = Constants.SignatureSpecNS + "hmac-sha1";

   /** Signature - Required DSAwithSHA1 (DSS) */
   public static final String ALGO_ID_SIGNATURE_DSA = Constants.SignatureSpecNS + "dsa-sha1";

   /** Signature - Recommended RSAwithSHA1 */
   public static final String ALGO_ID_SIGNATURE_RSA = Constants.SignatureSpecNS + "rsa-sha1";
   /** Signature - Recommended RSAwithSHA1 */
   public static final String ALGO_ID_SIGNATURE_RSA_SHA1 = Constants.SignatureSpecNS + "rsa-sha1";
   /** Signature - NOT Recommended RSAwithMD5 */
   public static final String ALGO_ID_SIGNATURE_NOT_RECOMMENDED_RSA_MD5 = Constants.MoreAlgorithmsSpecNS + "rsa-md5";
   /** Signature - Optional RSAwithRIPEMD160 */
   public static final String ALGO_ID_SIGNATURE_RSA_RIPEMD160 = Constants.MoreAlgorithmsSpecNS + "rsa-ripemd160";
   /** Signature - Optional RSAwithSHA256 */
   public static final String ALGO_ID_SIGNATURE_RSA_SHA256 = Constants.MoreAlgorithmsSpecNS + "rsa-sha256";
   /** Signature - Optional RSAwithSHA384 */
   public static final String ALGO_ID_SIGNATURE_RSA_SHA384 = Constants.MoreAlgorithmsSpecNS + "rsa-sha384";
   /** Signature - Optional RSAwithSHA512 */
   public static final String ALGO_ID_SIGNATURE_RSA_SHA512 = Constants.MoreAlgorithmsSpecNS + "rsa-sha512";

   /** HMAC - NOT Recommended HMAC-MD5 */
   public static final String ALGO_ID_MAC_HMAC_NOT_RECOMMENDED_MD5 = Constants.MoreAlgorithmsSpecNS + "hmac-md5";
   /** HMAC - Optional HMAC-RIPEMD160 */
   public static final String ALGO_ID_MAC_HMAC_RIPEMD160 = Constants.MoreAlgorithmsSpecNS + "hmac-ripemd160";
   /** HMAC - Optional HMAC-SHA256 */
   public static final String ALGO_ID_MAC_HMAC_SHA256 = Constants.MoreAlgorithmsSpecNS + "hmac-sha256";
   /** HMAC - Optional HMAC-SHA284 */
   public static final String ALGO_ID_MAC_HMAC_SHA384 = Constants.MoreAlgorithmsSpecNS + "hmac-sha384";
   /** HMAC - Optional HMAC-SHA512 */
   public static final String ALGO_ID_MAC_HMAC_SHA512 = Constants.MoreAlgorithmsSpecNS + "hmac-sha512";
   /**Signature - Optional ECDSAwithSHA1 */
   public static final String ALGO_ID_SIGNATURE_ECDSA_SHA1 = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";


   //J+

   /** ds:Signature.ds:SignedInfo element */
   private SignedInfo _signedInfo = null;

   /** ds:Signature.ds:KeyInfo */
   private KeyInfo _keyInfo = null;

   /**
    * Checking the digests in References in a Signature are mandatory, but for
    * References inside a Manifest it is application specific. This boolean is
    * to indicate that the References inside Manifests should be validated.
    */
   private boolean _followManifestsDuringValidation = false;

private Element signatureValueElement;

  /**
    * This creates a new <CODE>ds:Signature</CODE> Element and adds an empty
    * <CODE>ds:SignedInfo</CODE>.
    * The <code>ds:SignedInfo</code> is initialized with the specified Signature
    * algorithm and Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS which is REQUIRED
    * by the spec. This method's main use is for creating a new signature.
    *
    * @param doc Document in which the signature will be appended after creation.
    * @param BaseURI URI to be used as context for all relative URIs.
    * @param SignatureMethodURI signature algorithm to use.
    * @throws XMLSecurityException
    */
   public XMLSignature(Document doc, String BaseURI, String SignatureMethodURI)
           throws XMLSecurityException {
      this(doc, BaseURI, SignatureMethodURI, 0,
           Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
   }

   /**
    * Constructor XMLSignature
    *
    * @param doc
    * @param BaseURI
    * @param SignatureMethodURI the Signature method to be used.
    * @param HMACOutputLength
    * @throws XMLSecurityException
    */
   public XMLSignature(
           Document doc, String BaseURI, String SignatureMethodURI, int HMACOutputLength)
              throws XMLSecurityException {
      this(doc, BaseURI, SignatureMethodURI, HMACOutputLength,
           Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
   }

   /**
    * Constructor XMLSignature
    *
    * @param doc
    * @param BaseURI
    * @param SignatureMethodURI the Signature method to be used.
    * @param CanonicalizationMethodURI the canonicalization algorithm to be used to c14nize the SignedInfo element.
    * @throws XMLSecurityException
    */
   public XMLSignature(
           Document doc, String BaseURI, String SignatureMethodURI, String CanonicalizationMethodURI)
              throws XMLSecurityException {
      this(doc, BaseURI, SignatureMethodURI, 0, CanonicalizationMethodURI);
   }

   /**
    * Constructor XMLSignature
    *
    * @param doc
    * @param BaseURI
    * @param SignatureMethodURI
    * @param HMACOutputLength
    * @param CanonicalizationMethodURI
    * @throws XMLSecurityException
    */
   public XMLSignature(
           Document doc, String BaseURI, String SignatureMethodURI, int HMACOutputLength, String CanonicalizationMethodURI)
              throws XMLSecurityException {

      super(doc);

      String xmlnsDsPrefix = getDefaultPrefix(Constants.SignatureSpecNS);
      if (xmlnsDsPrefix == null) {
         this._constructionElement.setAttributeNS
            (Constants.NamespaceSpecNS, "xmlns", Constants.SignatureSpecNS);
      } else {
         this._constructionElement.setAttributeNS
            (Constants.NamespaceSpecNS, "xmlns:" + xmlnsDsPrefix, Constants.SignatureSpecNS);
      }
      XMLUtils.addReturnToElement(this._constructionElement);

      this._baseURI = BaseURI;
      this._signedInfo = new SignedInfo(this._doc, SignatureMethodURI,
                                        HMACOutputLength,
                                        CanonicalizationMethodURI);

      this._constructionElement.appendChild(this._signedInfo.getElement());
      XMLUtils.addReturnToElement(this._constructionElement);

      // create an empty SignatureValue; this is filled by setSignatureValueElement
      signatureValueElement =
         XMLUtils.createElementInSignatureSpace(this._doc,
                                                Constants._TAG_SIGNATUREVALUE);

      this._constructionElement.appendChild(signatureValueElement);
      XMLUtils.addReturnToElement(this._constructionElement);
   }
   /**
    *  Creates a XMLSignature in a Document
    * @param doc
    * @param BaseURI
    * @param SignatureMethodElem
    * @param CanonicalizationMethodElem
    * @throws XMLSecurityException
    */
   public XMLSignature(
           Document doc, String BaseURI, Element SignatureMethodElem, Element CanonicalizationMethodElem)
              throws XMLSecurityException {

      super(doc);

      String xmlnsDsPrefix = getDefaultPrefix(Constants.SignatureSpecNS);
      if (xmlnsDsPrefix == null) {
         this._constructionElement.setAttributeNS
            (Constants.NamespaceSpecNS, "xmlns", Constants.SignatureSpecNS);
      } else {
         this._constructionElement.setAttributeNS
            (Constants.NamespaceSpecNS, "xmlns:" + xmlnsDsPrefix, Constants.SignatureSpecNS);
      }
      XMLUtils.addReturnToElement(this._constructionElement);

      this._baseURI = BaseURI;
      this._signedInfo = new SignedInfo(this._doc, SignatureMethodElem, CanonicalizationMethodElem);

      this._constructionElement.appendChild(this._signedInfo.getElement());
      XMLUtils.addReturnToElement(this._constructionElement);

      // create an empty SignatureValue; this is filled by setSignatureValueElement
      signatureValueElement =
         XMLUtils.createElementInSignatureSpace(this._doc,
                                                Constants._TAG_SIGNATUREVALUE);

      this._constructionElement.appendChild(signatureValueElement);
      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * This will parse the element and construct the Java Objects.
    * That will allow a user to validate the signature.
    *
    * @param element ds:Signature element that contains the whole signature
    * @param BaseURI URI to be prepended to all relative URIs
    * @throws XMLSecurityException
    * @throws XMLSignatureException if the signature is badly formatted
    */
   public XMLSignature(Element element, String BaseURI)
           throws XMLSignatureException, XMLSecurityException {

      super(element, BaseURI);

      // check out SignedInfo child
      Element signedInfoElem = XMLUtils.getNextElement(element.getFirstChild());// XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                  //Constants._TAG_SIGNEDINFO,0);

      // check to see if it is there
      if (signedInfoElem == null) {
         Object exArgs[] = { Constants._TAG_SIGNEDINFO,
                             Constants._TAG_SIGNATURE };

         throw new XMLSignatureException("xml.WrongContent", exArgs);
      }

      // create a SignedInfo object from that element
      this._signedInfo = new SignedInfo(signedInfoElem, BaseURI);

      // check out SignatureValue child
      this.signatureValueElement =XMLUtils.getNextElement(signedInfoElem.getNextSibling()); //XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                       //  Constants._TAG_SIGNATUREVALUE,0);

      // check to see if it exists
      if (signatureValueElement == null) {
         Object exArgs[] = { Constants._TAG_SIGNATUREVALUE,
                             Constants._TAG_SIGNATURE };

         throw new XMLSignatureException("xml.WrongContent", exArgs);
      }
      Attr signatureValueAttr = signatureValueElement.getAttributeNodeNS(null, "Id");
      if (signatureValueAttr != null) {
          signatureValueElement.setIdAttributeNode(signatureValueAttr, true);
      }

      // <element ref="ds:KeyInfo" minOccurs="0"/>
      Element keyInfoElem = XMLUtils.getNextElement(signatureValueElement.getNextSibling());//XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                              // Constants._TAG_KEYINFO,0);

      // If it exists use it, but it's not mandatory
      if ((keyInfoElem != null) && (keyInfoElem.getNamespaceURI().equals(Constants.SignatureSpecNS) &&
                  keyInfoElem.getLocalName().equals(Constants._TAG_KEYINFO)) ) {
         this._keyInfo = new KeyInfo(keyInfoElem, BaseURI);
      }

      // <element ref="ds:Object" minOccurs="0" maxOccurs="unbounded"/>
      Element objectElem =
          XMLUtils.getNextElement(signatureValueElement.getNextSibling());
      while (objectElem != null) {
          Attr objectAttr = objectElem.getAttributeNodeNS(null, "Id");
          if (objectAttr != null) {
              objectElem.setIdAttributeNode(objectAttr, true);
          }

          NodeList nodes = objectElem.getChildNodes();
          int length = nodes.getLength();
          // Register Ids of the Object child elements
          for (int i = 0; i < length; i++) {
              Node child = nodes.item(i);
              if (child.getNodeType() == Node.ELEMENT_NODE) {
                  Element childElem = (Element)child;
                  String tag = childElem.getLocalName();
                  if (tag.equals("Manifest")) {
                      new Manifest(childElem, BaseURI);
                  } else if (tag.equals("SignatureProperties")) {
                      new SignatureProperties(childElem, BaseURI);
                  }
              }
          }

          objectElem = XMLUtils.getNextElement(objectElem.getNextSibling());
      }
   }

   /**
    * Sets the <code>Id</code> attribute
    *
    * @param Id Id value to be used by the id attribute on the Signature Element
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
    * Returns the completely parsed <code>SignedInfo</code> object.
    *
    * @return the completely parsed <code>SignedInfo</code> object.
    */
   public SignedInfo getSignedInfo() {
      return this._signedInfo;
   }

   /**
    * Returns the octet value of the SignatureValue element.
    * Throws an XMLSignatureException if it has no or wrong content.
    *
    * @return the value of the SignatureValue element.
    * @throws XMLSignatureException If there is no content
    */
   public byte[] getSignatureValue() throws XMLSignatureException {

      try {
          byte[] signatureValue = Base64.decode(signatureValueElement);

         return signatureValue;
      } catch (Base64DecodingException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

    /**
     * Base64 encodes and sets the bytes as the content of the SignatureValue
     * Node.
     *
     * @param bytes bytes to be used by SignatureValue before Base64 encoding
     */
    private void setSignatureValueElement(byte[] bytes) {

        while (signatureValueElement.hasChildNodes()) {
            signatureValueElement.removeChild
                (signatureValueElement.getFirstChild());
        }

        String base64codedValue = Base64.encode(bytes);

        if (base64codedValue.length() > 76 && !XMLUtils.ignoreLineBreaks()) {
            base64codedValue = "\n" + base64codedValue + "\n";
        }

        Text t = this._doc.createTextNode(base64codedValue);
        signatureValueElement.appendChild(t);
    }

   /**
    * Returns the KeyInfo child. If we are in signing mode and the KeyInfo
    * does not exist yet, it is created on demand and added to the Signature.
    * <br>
    * This allows to add arbitrary content to the KeyInfo during signing.
    *
    * @return the KeyInfo object
    */
   public KeyInfo getKeyInfo() {

      // check to see if we are signing and if we have to create a keyinfo
      if ( (this._keyInfo == null)) {

         // create the KeyInfo
         this._keyInfo = new KeyInfo(this._doc);

         // get the Element from KeyInfo
         Element keyInfoElement = this._keyInfo.getElement();
         Element firstObject=null;
         Node sibling= this._constructionElement.getFirstChild();
         firstObject = XMLUtils.selectDsNode(sibling,Constants._TAG_OBJECT,0);

            if (firstObject != null) {

               // add it before the object
               this._constructionElement.insertBefore(keyInfoElement,
                                                      firstObject);
               XMLUtils.addReturnBeforeChild(this._constructionElement, firstObject);
            } else {

               // add it as the last element to the signature
               this._constructionElement.appendChild(keyInfoElement);
               XMLUtils.addReturnToElement(this._constructionElement);
            }
      }

      return this._keyInfo;
   }

   /**
    * Appends an Object (not a <code>java.lang.Object</code> but an Object
    * element) to the Signature. Please note that this is only possible
    * when signing.
    *
    * @param object ds:Object to be appended.
    * @throws XMLSignatureException When this object is used to verify.
    */
   public void appendObject(ObjectContainer object)
           throws XMLSignatureException {

      //try {
         //if (this._state != MODE_SIGN) {
           // throw new XMLSignatureException(
             //  "signature.operationOnlyBeforeSign");
         //}

         this._constructionElement.appendChild(object.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
      //} catch (XMLSecurityException ex) {
        // throw new XMLSignatureException("empty", ex);
      //}
   }

   /**
    * Returns the <code>i<code>th <code>ds:Object</code> child of the signature
    * or null if no such <code>ds:Object</code> element exists.
    *
    * @param i
    * @return the <code>i<code>th <code>ds:Object</code> child of the signature or null if no such <code>ds:Object</code> element exists.
    */
   public ObjectContainer getObjectItem(int i) {

      Element objElem = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
            Constants._TAG_OBJECT,i);

      try {
         return new ObjectContainer(objElem, this._baseURI);
      } catch (XMLSecurityException ex) {
         return null;
      }
   }

   /**
    * Returns the number of all <code>ds:Object</code> elements.
    *
    * @return the number of all <code>ds:Object</code> elements.
    */
   public int getObjectLength() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_OBJECT);
   }

   /**
    * Digests all References in the SignedInfo, calculates the signature value and
    * sets it in the SignatureValue Element.
    *
    * @param signingKey the {@link java.security.PrivateKey} or {@link javax.crypto.SecretKey} that is used to sign.
    * @throws XMLSignatureException
    */
   public void sign(Key signingKey) throws XMLSignatureException {

      if (signingKey instanceof PublicKey) {
         throw new IllegalArgumentException(I18n
            .translate("algorithms.operationOnlyVerification"));
      }

      try {
        // if (this._state == MODE_SIGN) {
            //Create a SignatureAlgorithm object
                SignedInfo si = this.getSignedInfo();
            SignatureAlgorithm sa = si.getSignatureAlgorithm();
            // initialize SignatureAlgorithm for signing
            sa.initSign(signingKey);

            // generate digest values for all References in this SignedInfo
            si.generateDigestValues();
            OutputStream so=new UnsyncBufferedOutputStream(new SignerOutputStream(sa));
            try {
                so.close();
            } catch (IOException e) {
                //Imposible
            }
            // get the canonicalized bytes from SignedInfo
            si.signInOctectStream(so);

            byte jcebytes[] = sa.sign();

            // set them on the SignateValue element
            this.setSignatureValueElement(jcebytes);
         //}
      } catch (CanonicalizationException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (InvalidCanonicalizerException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (XMLSecurityException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /**
    * Adds a {@link ResourceResolver} to enable the retrieval of resources.
    *
    * @param resolver
    */
   public void addResourceResolver(ResourceResolver resolver) {
      this.getSignedInfo().addResourceResolver(resolver);
   }

   /**
    * Adds a {@link ResourceResolverSpi} to enable the retrieval of resources.
    *
    * @param resolver
    */
   public void addResourceResolver(ResourceResolverSpi resolver) {
      this.getSignedInfo().addResourceResolver(resolver);
   }

   /**
    * Extracts the public key from the certificate and verifies if the signature
    * is valid by re-digesting all References, comparing those against the
    * stored DigestValues and then checking to see if the Signatures match on
    * the SignedInfo.
    *
    * @param cert Certificate that contains the public key part of the keypair that was used to sign.
    * @return true if the signature is valid, false otherwise
    * @throws XMLSignatureException
    */
   public boolean checkSignatureValue(X509Certificate cert)
           throws XMLSignatureException {

      // see if cert is null
      if (cert != null) {

         //check the values with the public key from the cert
         return this.checkSignatureValue(cert.getPublicKey());
      }

      Object exArgs[] = { "Didn't get a certificate" };
      throw new XMLSignatureException("empty", exArgs);

   }

   /**
    * Verifies if the signature is valid by redigesting all References,
    * comparing those against the stored DigestValues and then checking to see
    * if the Signatures match on the SignedInfo.
    *
    * @param pk {@link java.security.PublicKey} part of the keypair or {@link javax.crypto.SecretKey} that was used to sign
    * @return true if the signature is valid, false otherwise
    * @throws XMLSignatureException
    */
   public boolean checkSignatureValue(Key pk) throws XMLSignatureException {

      //COMMENT: pk suggests it can only be a public key?
      //check to see if the key is not null
      if (pk == null) {
         Object exArgs[] = { "Didn't get a key" };

         throw new XMLSignatureException("empty", exArgs);
      }
      // all references inside the signedinfo need to be dereferenced and
      // digested again to see if the outcome matches the stored value in the
      // SignedInfo.
      // If _followManifestsDuringValidation is true it will do the same for
      // References inside a Manifest.
      try {
         SignedInfo si=this.getSignedInfo();
         //create a SignatureAlgorithms from the SignatureMethod inside
         //SignedInfo. This is used to validate the signature.
         SignatureAlgorithm sa =si.getSignatureAlgorithm();
         if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "SignatureMethodURI = " + sa.getAlgorithmURI());
                log.log(java.util.logging.Level.FINE, "jceSigAlgorithm    = " + sa.getJCEAlgorithmString());
                log.log(java.util.logging.Level.FINE, "jceSigProvider     = " + sa.getJCEProviderName());
                log.log(java.util.logging.Level.FINE, "PublicKey = " + pk);
         }
         sa.initVerify(pk);

         // Get the canonicalized (normalized) SignedInfo
         SignerOutputStream so=new SignerOutputStream(sa);
         OutputStream bos=new UnsyncBufferedOutputStream(so);
         si.signInOctectStream(bos);
         try {
                bos.close();
         } catch (IOException e) {
                //Imposible
         }

         //retrieve the byte[] from the stored signature
         byte sigBytes[] = this.getSignatureValue();

         //Have SignatureAlgorithm sign the input bytes and compare them to the
         //bytes that were stored in the signature.
         if (!sa.verify(sigBytes)) {
            log.log(java.util.logging.Level.WARNING, "Signature verification failed.");
            return false;
         }

         return si.verify(this._followManifestsDuringValidation);
      } catch (XMLSecurityException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /**
    * Add a Reference with full parameters to this Signature
    *
    * @param referenceURI URI of the resource to be signed. Can be null in which
    * case the dereferencing is application specific. Can be "" in which it's
    * the parent node (or parent document?). There can only be one "" in each
    * signature.
    * @param trans Optional list of transformations to be done before digesting
    * @param digestURI Mandatory URI of the digesting algorithm to use.
    * @param ReferenceId Optional id attribute for this Reference
    * @param ReferenceType Optional mimetype for the URI
    * @throws XMLSignatureException
    */
   public void addDocument(
           String referenceURI, Transforms trans, String digestURI, String ReferenceId, String ReferenceType)
              throws XMLSignatureException {
      this._signedInfo.addDocument(this._baseURI, referenceURI, trans,
                                   digestURI, ReferenceId, ReferenceType);
   }

   /**
    * This method is a proxy method for the {@link Manifest#addDocument} method.
    *
    * @param referenceURI URI according to the XML Signature specification.
    * @param trans List of transformations to be applied.
    * @param digestURI URI of the digest algorithm to be used.
    * @see Manifest#addDocument
    * @throws XMLSignatureException
    */
   public void addDocument(
           String referenceURI, Transforms trans, String digestURI)
              throws XMLSignatureException {
      this._signedInfo.addDocument(this._baseURI, referenceURI, trans,
                                   digestURI, null, null);
   }

   /**
    * Adds a Reference with just the URI and the transforms. This used the
    * SHA1 algorithm as a default digest algorithm.
    *
    * @param referenceURI URI according to the XML Signature specification.
    * @param trans List of transformations to be applied.
    * @throws XMLSignatureException
    */
   public void addDocument(String referenceURI, Transforms trans)
           throws XMLSignatureException {
      this._signedInfo.addDocument(this._baseURI, referenceURI, trans,
                                   Constants.ALGO_ID_DIGEST_SHA1, null, null);
   }

   /**
    * Add a Reference with just this URI. It uses SHA1 by default as the digest
    * algorithm
    *
    * @param referenceURI URI according to the XML Signature specification.
    * @throws XMLSignatureException
    */
   public void addDocument(String referenceURI) throws XMLSignatureException {
      this._signedInfo.addDocument(this._baseURI, referenceURI, null,
                                   Constants.ALGO_ID_DIGEST_SHA1, null, null);
   }

   /**
    * Add an X509 Certificate to the KeyInfo. This will include the whole cert
    * inside X509Data/X509Certificate tags.
    *
    * @param cert Certificate to be included. This should be the certificate of the key that was used to sign.
    * @throws XMLSecurityException
    */
   public void addKeyInfo(X509Certificate cert) throws XMLSecurityException {

      X509Data x509data = new X509Data(this._doc);

      x509data.addCertificate(cert);
      this.getKeyInfo().add(x509data);
   }

   /**
    * Add this public key to the KeyInfo. This will include the complete key in
    * the KeyInfo structure.
    *
    * @param pk
    */
   public void addKeyInfo(PublicKey pk) {
      this.getKeyInfo().add(pk);
   }

   /**
    * Proxy method for {@link SignedInfo#createSecretKey(byte[])}. If you want to
    * create a MAC, this method helps you to obtain the {@link javax.crypto.SecretKey}
    * from octets.
    *
    * @param secretKeyBytes
    * @return the secret key created.
    * @see SignedInfo#createSecretKey(byte[])
    */
   public SecretKey createSecretKey(byte[] secretKeyBytes)
   {
      return this.getSignedInfo().createSecretKey(secretKeyBytes);
   }

   /**
    * Signal wether Manifest should be automatically validated.
    * Checking the digests in References in a Signature are mandatory, but for
    * References inside a Manifest it is application specific. This boolean is
    * to indicate that the References inside Manifests should be validated.
    *
    * @param followManifests
    * @see <a href="http://www.w3.org/TR/xmldsig-core/#sec-CoreValidation">Core validation section in the XML Signature Rec.</a>
    */
   public void setFollowNestedManifests(boolean followManifests) {
      this._followManifestsDuringValidation = followManifests;
   }

   /**
    * Get the local name of this element
    *
    * @return Constant._TAG_SIGNATURE
    */
   public String getBaseLocalName() {
      return Constants._TAG_SIGNATURE;
   }
}
