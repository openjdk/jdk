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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.I18n;
import com.sun.org.apache.xml.internal.security.utils.IdResolver;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;



/**
 * Handles <code>&lt;ds:Manifest&gt;</code> elements.
 * <p> This element holds the <code>Reference</code> elements</p>
 * @author $author: $
 */
public class Manifest extends SignatureElementProxy {

  /** {@link java.util.logging} logging facility */
  static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(Manifest.class.getName());

   /** Field _references */
   List<Reference> _references;
   Element[] _referencesEl;

   /** Field verificationResults[] */
   private boolean verificationResults[] = null;

   /** Field _resolverProperties */
   Map<String,String> _resolverProperties = null;

   /** Field _perManifestResolvers */
   List<ResourceResolver> _perManifestResolvers = null;

   /**
    * Consturts {@link Manifest}
    *
    * @param doc the {@link Document} in which <code>XMLsignature</code> is placed
    */
   public Manifest(Document doc) {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);

      this._references = new ArrayList<Reference>();
   }

   /**
    * Constructor Manifest
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public Manifest(Element element, String BaseURI)
           throws XMLSecurityException {

      super(element, BaseURI);

      Attr attr = element.getAttributeNodeNS(null, "Id");
      if (attr != null) {
          element.setIdAttributeNode(attr, true);
      }

      // check out Reference children
      this._referencesEl = XMLUtils.selectDsNodes(this._constructionElement.getFirstChild(),
         Constants._TAG_REFERENCE);
      int le = this._referencesEl.length;
      {
         if (le == 0) {

            // At least one Reference must be present. Bad.
            Object exArgs[] = { Constants._TAG_REFERENCE,
                                Constants._TAG_MANIFEST };

            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR,
                                   I18n.translate("xml.WrongContent", exArgs));
         }
      }

      // create Vector
      this._references = new ArrayList<Reference>(le);

      for (int i = 0; i < le; i++) {
         Element refElem = this._referencesEl[i];
         Attr refAttr = refElem.getAttributeNodeNS(null, "Id");
         if (refAttr != null) {
             refElem.setIdAttributeNode(refAttr, true);
         }
         this._references.add(null);
      }
   }

   /**
    * This <code>addDocument</code> method is used to add a new resource to the
    * signed info. A {@link com.sun.org.apache.xml.internal.security.signature.Reference} is built
    * from the supplied values.
    *
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @param referenceURI <code>URI</code> attribute in <code>Reference</code> for specifing where data is
    * @param transforms com.sun.org.apache.xml.internal.security.signature.Transforms object with an ordered list of transformations to be performed.
    * @param digestURI The digest algorthim URI to be used.
    * @param ReferenceId
    * @param ReferenceType
    * @throws XMLSignatureException
    */
   public void addDocument(
           String BaseURI, String referenceURI, Transforms transforms, String digestURI, String ReferenceId, String ReferenceType)
              throws XMLSignatureException {

         // the this._doc is handed implicitly by the this.getOwnerDocument()
         Reference ref = new Reference(this._doc, BaseURI, referenceURI, this,
                                       transforms, digestURI);

         if (ReferenceId != null) {
            ref.setId(ReferenceId);
         }

         if (ReferenceType != null) {
            ref.setType(ReferenceType);
         }

         // add Reference object to our cache vector
         this._references.add(ref);

         // add the Element of the Reference object to the Manifest/SignedInfo
         this._constructionElement.appendChild(ref.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * The calculation of the DigestValues in the References must be after the
    * References are already added to the document and during the signing
    * process. This ensures that all neccesary data is in place.
    *
    * @throws ReferenceNotInitializedException
    * @throws XMLSignatureException
    */
   public void generateDigestValues()
           throws XMLSignatureException, ReferenceNotInitializedException {

         for (int i = 0; i < this.getLength(); i++) {

            // update the cached Reference object, the Element content is automatically updated
            Reference currentRef = this._references.get(i);

            currentRef.generateDigestValue();
         }
   }

   /**
    * Return the nonnegative number of added references.
    *
    * @return the number of references
    */
   public int getLength() {
      return this._references.size();
   }

   /**
    * Return the <it>i</it><sup>th</sup> reference.  Valid <code>i</code>
    * values are 0 to <code>{link@ getSize}-1</code>.
    *
    * @param i Index of the requested {@link Reference}
    * @return the <it>i</it><sup>th</sup> reference
    * @throws XMLSecurityException
    */
   public Reference item(int i) throws XMLSecurityException {

         if (this._references.get(i) == null) {

            // not yet constructed, so _we_ have to
            Reference ref = new Reference(_referencesEl[i], this._baseURI, this);

            this._references.set(i, ref);
         }

         return this._references.get(i);

   }

   /**
    * Sets the <code>Id</code> attribute
    *
    * @param Id the <code>Id</code> attribute in <code>ds:Manifest</code>
    */
   public void setId(String Id) {

      if (Id != null) {
          setLocalIdAttribute(Constants._ATT_ID, Id);
      }
   }

   /**
    * Returns the <code>Id</code> attribute
    *
    * @return the <code>Id</code> attribute in <code>ds:Manifest</code>
    */
   public String getId() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_ID);
   }

   /**
    * Used to do a <A HREF="http://www.w3.org/TR/xmldsig-core/#def-ValidationReference">reference
    * validation</A> of all enclosed references using the {@link Reference#verify} method.
    *
    * <p>This step loops through all {@link Reference}s and does verify the hash
    * values. If one or more verifications fail, the method returns
    * <code>false</code>. If <i>all</i> verifications are successful,
    * it returns <code>true</code>. The results of the individual reference
    * validations are available by using the {@link #getVerificationResult(int)} method
    *
    * @return true if all References verify, false if one or more do not verify.
    * @throws MissingResourceFailureException if a {@link Reference} does not verify (throws a {@link com.sun.org.apache.xml.internal.security.signature.ReferenceNotInitializedException} because of an uninitialized {@link XMLSignatureInput}
    * @see com.sun.org.apache.xml.internal.security.signature.Reference#verify
    * @see com.sun.org.apache.xml.internal.security.signature.SignedInfo#verify()
    * @see com.sun.org.apache.xml.internal.security.signature.MissingResourceFailureException
    * @throws XMLSecurityException
    */
   public boolean verifyReferences()
           throws MissingResourceFailureException, XMLSecurityException {
      return this.verifyReferences(false);
   }

   /**
    * Used to do a <A HREF="http://www.w3.org/TR/xmldsig-core/#def-ValidationReference">reference
    * validation</A> of all enclosed references using the {@link Reference#verify} method.
    *
    * <p>This step loops through all {@link Reference}s and does verify the hash
    * values. If one or more verifications fail, the method returns
    * <code>false</code>. If <i>all</i> verifications are successful,
    * it returns <code>true</code>. The results of the individual reference
    * validations are available by using the {@link #getVerificationResult(int)} method
    *
    * @param followManifests
    * @return true if all References verify, false if one or more do not verify.
    * @throws MissingResourceFailureException if a {@link Reference} does not verify (throws a {@link com.sun.org.apache.xml.internal.security.signature.ReferenceNotInitializedException} because of an uninitialized {@link XMLSignatureInput}
    * @see com.sun.org.apache.xml.internal.security.signature.Reference#verify
    * @see com.sun.org.apache.xml.internal.security.signature.SignedInfo#verify(boolean)
    * @see com.sun.org.apache.xml.internal.security.signature.MissingResourceFailureException
    * @throws XMLSecurityException
    */
   public boolean verifyReferences(boolean followManifests)
           throws MissingResourceFailureException, XMLSecurityException {
      if (_referencesEl==null) {
        this._referencesEl =
            XMLUtils.selectDsNodes(this._constructionElement.getFirstChild(),
                         Constants._TAG_REFERENCE);
      }
          if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "verify " +_referencesEl.length + " References");
        log.log(java.util.logging.Level.FINE, "I am " + (followManifests
                           ? ""
                           : "not") + " requested to follow nested Manifests");
      }
      boolean verify = true;

      if (_referencesEl.length==0) {
         throw new XMLSecurityException("empty");
      }

      this.verificationResults =
         new boolean[_referencesEl.length];

      for (int i =
              0; i < this._referencesEl.length; i++) {
         Reference currentRef =
            new Reference(_referencesEl[i], this._baseURI, this);

         this._references.set(i, currentRef);

         /* if only one item does not verify, the whole verification fails */
         try {
            boolean currentRefVerified = currentRef.verify();

            this.setVerificationResult(i, currentRefVerified);

            if (!currentRefVerified) {
               verify = false;
            }
            if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "The Reference has Type " + currentRef.getType());

            // was verification successful till now and do we want to verify the Manifest?
            if (verify && followManifests
                    && currentRef.typeIsReferenceToManifest()) {
               log.log(java.util.logging.Level.FINE, "We have to follow a nested Manifest");

                try {
                  XMLSignatureInput signedManifestNodes =
                    currentRef.dereferenceURIandPerformTransforms(null);
                  Set<Node> nl = signedManifestNodes.getNodeSet();
                  Manifest referencedManifest = null;
                  Iterator<Node> nlIterator = nl.iterator();

                  findManifest: while (nlIterator.hasNext()) {
                     Node n =  nlIterator.next();

                     if ((n.getNodeType() == Node.ELEMENT_NODE) && ((Element) n)
                             .getNamespaceURI()
                             .equals(Constants.SignatureSpecNS) && ((Element) n)
                             .getLocalName().equals(Constants._TAG_MANIFEST)) {
                        try {
                           referencedManifest =
                              new Manifest((Element) n,
                                           signedManifestNodes.getSourceURI());

                           break findManifest;
                        } catch (XMLSecurityException ex) {

                           // Hm, seems not to be a ds:Manifest
                        }
                     }
                  }

                  if (referencedManifest == null) {

                     // The Reference stated that it points to a ds:Manifest
                     // but we did not find a ds:Manifest in the signed area
                     throw new MissingResourceFailureException("empty",
                                                               currentRef);
                  }

                  referencedManifest._perManifestResolvers =
                     this._perManifestResolvers;
                  referencedManifest._resolverProperties =
                     this._resolverProperties;

                  boolean referencedManifestValid =
                     referencedManifest.verifyReferences(followManifests);

                  if (!referencedManifestValid) {
                     verify = false;

                     log.log(java.util.logging.Level.WARNING, "The nested Manifest was invalid (bad)");
                  } else {
                     log.log(java.util.logging.Level.FINE, "The nested Manifest was valid (good)");
                  }
               } catch (IOException ex) {
                  throw new ReferenceNotInitializedException("empty", ex);
               } catch (ParserConfigurationException ex) {
                  throw new ReferenceNotInitializedException("empty", ex);
               } catch (SAXException ex) {
                  throw new ReferenceNotInitializedException("empty", ex);
               }
            }
         } catch (ReferenceNotInitializedException ex) {
            Object exArgs[] = { currentRef.getURI() };

            throw new MissingResourceFailureException(
               "signature.Verification.Reference.NoInput", exArgs, ex,
               currentRef);
         }
      }

      return verify;
   }

   /**
    * Method setVerificationResult
    *
    * @param index
    * @param verify
    */
   private void setVerificationResult(int index, boolean verify)
   {

      if (this.verificationResults == null) {
         this.verificationResults = new boolean[this.getLength()];
      }

      this.verificationResults[index] = verify;
   }

   /**
    * After verifying a {@link Manifest} or a {@link SignedInfo} using the
    * {@link Manifest#verifyReferences()} or {@link SignedInfo#verify()} methods,
    * the individual results can be retrieved with this method.
    *
    * @param index an index of into a {@link Manifest} or a {@link SignedInfo}
    * @return the results of reference validation at the specified index
    * @throws XMLSecurityException
    */
   public boolean getVerificationResult(int index) throws XMLSecurityException {

      if ((index < 0) || (index > this.getLength() - 1)) {
         Object exArgs[] = { Integer.toString(index),
                             Integer.toString(this.getLength()) };
         Exception e =
            new IndexOutOfBoundsException(I18n
               .translate("signature.Verification.IndexOutOfBounds", exArgs));

         throw new XMLSecurityException("generic.EmptyMessage", e);
      }

      if (this.verificationResults == null) {
         try {
            this.verifyReferences();
         } catch (Exception ex) {
            throw new XMLSecurityException("generic.EmptyMessage", ex);
         }
      }

      return this.verificationResults[index];
   }

   /**
    * Adds Resource Resolver for retrieving resources at specified <code>URI</code> attribute in <code>reference</code> element
    *
    * @param resolver {@link ResourceResolver} can provide the implemenatin subclass of {@link ResourceResolverSpi} for retrieving resource.
    */
   public void addResourceResolver(ResourceResolver resolver) {

      if (resolver == null) {
          return;
      }
      if (_perManifestResolvers==null)
          _perManifestResolvers = new ArrayList<ResourceResolver>();
      this._perManifestResolvers.add(resolver);

   }

   /**
    * Adds Resource Resolver for retrieving resources at specified <code>URI</code> attribute in <code>reference</code> element
    *
    * @param resolverSpi the implemenatin subclass of {@link ResourceResolverSpi} for retrieving resource.
    */
   public void addResourceResolver(ResourceResolverSpi resolverSpi) {

      if (resolverSpi == null) {
          return;
      }
      if (_perManifestResolvers==null)
                  _perManifestResolvers = new ArrayList<ResourceResolver>();
      this._perManifestResolvers.add(new ResourceResolver(resolverSpi));

   }

   /**
    * Used to pass parameters like proxy servers etc to the ResourceResolver
    * implementation.
    *
    * @param key the key
    * @param value the value
    */
   public void setResolverProperty(String key, String value) {
           if (_resolverProperties==null) {
                   _resolverProperties=new HashMap<String, String>(10);
           }
      this._resolverProperties.put(key, value);
   }

   /**
    * Returns the value at specified key
    *
    * @param key the key
    * @return the value
    */
   public String getResolverProperty(String key) {
      return this._resolverProperties.get(key);
   }

   /**
    * Method getSignedContentItem
    *
    * @param i
    * @return The signed content of the i reference.
    *
    * @throws XMLSignatureException
    */
   public byte[] getSignedContentItem(int i) throws XMLSignatureException {

      try {
         return this.getReferencedContentAfterTransformsItem(i).getBytes();
      } catch (IOException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (CanonicalizationException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (InvalidCanonicalizerException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (XMLSecurityException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /**
    * Method getReferencedContentPriorTransformsItem
    *
    * @param i
    * @return The contents before transformation of the reference i.
    * @throws XMLSecurityException
    */
   public XMLSignatureInput getReferencedContentBeforeTransformsItem(int i)
           throws XMLSecurityException {
      return this.item(i).getContentsBeforeTransformation();
   }

   /**
    * Method getReferencedContentAfterTransformsItem
    *
    * @param i
    * @return The contents after transformation of the reference i.
    * @throws XMLSecurityException
    */
   public XMLSignatureInput getReferencedContentAfterTransformsItem(int i)
           throws XMLSecurityException {
      return this.item(i).getContentsAfterTransformation();
   }

   /**
    * Method getSignedContentLength
    *
    * @return The nu,ber of references contained in this reference.
    */
   public int getSignedContentLength() {
      return this.getLength();
   }

   /**
    * Method getBaseLocalName
    *
    * @inheritDoc
    */
   public String getBaseLocalName() {
      return Constants._TAG_MANIFEST;
   }
}
