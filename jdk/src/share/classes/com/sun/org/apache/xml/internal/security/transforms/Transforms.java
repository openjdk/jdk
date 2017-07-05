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
package com.sun.org.apache.xml.internal.security.transforms;



import java.io.IOException;
import java.io.OutputStream;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * Holder of the {@link com.sun.org.apache.xml.internal.security.transforms.Transform} steps to be performed on the data.
 * The input to the first Transform is the result of dereferencing the <code>URI</code> attribute of the <code>Reference</code> element.
 * The output from the last Transform is the input for the <code>DigestMethod algorithm</code>
 *
 * @author Christian Geuer-Pollmann
 * @see Transform
 * @see com.sun.org.apache.xml.internal.security.signature.Reference
 */
public class Transforms extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(Transforms.class.getName());
   //J-
   /** Canonicalization - Required Canonical XML (omits comments) */
   public static final String TRANSFORM_C14N_OMIT_COMMENTS = Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS;
   /** Canonicalization - Recommended Canonical XML with Comments */
   public static final String TRANSFORM_C14N_WITH_COMMENTS = Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS;
   /** Canonicalization - Required Exclusive Canonicalization (omits comments) */
   public static final String TRANSFORM_C14N_EXCL_OMIT_COMMENTS = Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS;
   /** Canonicalization - Recommended Exclusive Canonicalization with Comments */
   public static final String TRANSFORM_C14N_EXCL_WITH_COMMENTS = Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS;
   /** Transform - Optional XSLT */
   public static final String TRANSFORM_XSLT = "http://www.w3.org/TR/1999/REC-xslt-19991116";
   /** Transform - Required base64 decoding */
   public static final String TRANSFORM_BASE64_DECODE = Constants.SignatureSpecNS + "base64";
   /** Transform - Recommended XPath */
   public static final String TRANSFORM_XPATH = "http://www.w3.org/TR/1999/REC-xpath-19991116";
   /** Transform - Required Enveloped Signature */
   public static final String TRANSFORM_ENVELOPED_SIGNATURE = Constants.SignatureSpecNS + "enveloped-signature";
   /** Transform - XPointer */
   public static final String TRANSFORM_XPOINTER = "http://www.w3.org/TR/2001/WD-xptr-20010108";
   /** Transform - XPath Filter v2.0 */
   public static final String TRANSFORM_XPATH2FILTER04 = "http://www.w3.org/2002/04/xmldsig-filter2";
   /** Transform - XPath Filter */
   public static final String TRANSFORM_XPATH2FILTER = "http://www.w3.org/2002/06/xmldsig-filter2";
   /** Transform - XPath Filter  CHGP private*/
   public static final String TRANSFORM_XPATHFILTERCHGP = "http://www.nue.et-inf.uni-siegen.de/~geuer-pollmann/#xpathFilter";
   //J+
   Element []transforms;
   /**
    * Consturcts {@link Transforms}
    *
    * @param doc the {@link Document} in which <code>XMLsignature</code> will be placed
    */
   public Transforms(Document doc) {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Consturcts {@link Transforms} from {@link Element} which is <code>Transforms</code> Element
    *
    * @param element  is <code>Transforms</code> element
    * @param BaseURI the URI where the XML instance was stored
    * @throws DOMException
    * @throws InvalidTransformException
    * @throws TransformationException
    * @throws XMLSecurityException
    * @throws XMLSignatureException
    */
   public Transforms(Element element, String BaseURI)
           throws DOMException, XMLSignatureException,
                  InvalidTransformException, TransformationException,
                  XMLSecurityException {

      super(element, BaseURI);

      int numberOfTransformElems = this.getLength();

      if (numberOfTransformElems == 0) {

         // At least ont Transform element must be present. Bad.
         Object exArgs[] = { Constants._TAG_TRANSFORM,
                             Constants._TAG_TRANSFORMS };

         throw new TransformationException("xml.WrongContent", exArgs);
      }
   }

   /**
    * Adds the <code>Transform</code> with the specified <code>Transform algorithm URI</code>
    *
    * @param transformURI the URI form of transform that indicates which transformation is applied to data
    * @throws TransformationException
    */
   public void addTransform(String transformURI)
           throws TransformationException {

      try {
         if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Transforms.addTransform(" + transformURI + ")");

         Transform transform = Transform.getInstance(this._doc, transformURI);

         this.addTransform(transform);
      } catch (InvalidTransformException ex) {
         throw new TransformationException("empty", ex);
      }
   }

   /**
    * Adds the <code>Transform</code> with the specified <code>Transform algorithm URI</code>
    *
    * @param transformURI the URI form of transform that indicates which transformation is applied to data
    * @param contextElement
    * @throws TransformationException
    * @see Transform#getInstance(Document doc, String algorithmURI, Element childElement)
    */
   public void addTransform(String transformURI, Element contextElement)
           throws TransformationException {

      try {
         if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Transforms.addTransform(" + transformURI + ")");

         Transform transform = Transform.getInstance(this._doc, transformURI,
                                                     contextElement);

         this.addTransform(transform);
      } catch (InvalidTransformException ex) {
         throw new TransformationException("empty", ex);
      }
   }

   /**
    * Adds the <code>Transform</code> with the specified <code>Transform algorithm URI</code>
    *
    * @param transformURI the URI form of transform that indicates which transformation is applied to data
    * @param contextNodes
    * @throws TransformationException
    * @see Transform#getInstance(Document doc, String algorithmURI, NodeList contextNodes)
    */
   public void addTransform(String transformURI, NodeList contextNodes)
           throws TransformationException {

      try {
         Transform transform = Transform.getInstance(this._doc, transformURI,
                                                     contextNodes);

         this.addTransform(transform);
      } catch (InvalidTransformException ex) {
         throw new TransformationException("empty", ex);
      }
   }

   /**
    * Adds a user-provided Transform step.
    *
    * @param transform {@link Transform} object
    */
   private void addTransform(Transform transform) {
      if (true)
        if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Transforms.addTransform(" + transform.getURI() + ")");

      Element transformElement = transform.getElement();

      this._constructionElement.appendChild(transformElement);
      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Applies all included <code>Transform</code>s to xmlSignatureInput and returns the result of these transformations.
    *
    * @param xmlSignatureInput the input for the <code>Transform</code>s
    * @return the result of the <code>Transforms</code>
    * @throws TransformationException
    */
   public XMLSignatureInput performTransforms(
           XMLSignatureInput xmlSignatureInput) throws TransformationException {
             return performTransforms(xmlSignatureInput,null);
   }

   /**
    * Applies all included <code>Transform</code>s to xmlSignatureInput and returns the result of these transformations.
    *
    * @param xmlSignatureInput the input for the <code>Transform</code>s
    * @param os where to output the last transformation.
    * @return the result of the <code>Transforms</code>
    * @throws TransformationException
    */
    public XMLSignatureInput performTransforms(
            XMLSignatureInput xmlSignatureInput,OutputStream os) throws TransformationException {

      try {
        int last=this.getLength()-1;
         for (int i = 0; i < last; i++) {
            Transform t = this.item(i);
            if (true) {
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Preform the (" + i + ")th " + t.getURI() + " transform");
            }
                        xmlSignatureInput = t.performTransform(xmlSignatureInput);
         }
         if (last>=0) {
                        Transform t = this.item(last);
            xmlSignatureInput = t.performTransform(xmlSignatureInput, os);
         }


         return xmlSignatureInput;
      } catch (IOException ex) {
         throw new TransformationException("empty", ex);
      // } catch (ParserConfigurationException ex) { throw new TransformationException("empty", ex);
      // } catch (SAXException ex) { throw new TransformationException("empty", ex);
      } catch (CanonicalizationException ex) {
         throw new TransformationException("empty", ex);
      } catch (InvalidCanonicalizerException ex) {
         throw new TransformationException("empty", ex);
      }
   }

   /**
    * Return the nonnegative number of transformations.
    *
    * @return the number of transformations
    */
   public int getLength()
   {
                /*Element nscontext = XMLUtils.createDSctx(this._doc, "ds",
                                                      Constants.SignatureSpecNS);
             NodeList transformElems =
                XPathAPI.selectNodeList(this._constructionElement,
                                        "./ds:Transform", nscontext);
             return transformElems.getLength();*/
       if (transforms==null) {
        transforms=XMLUtils.selectDsNodes(this._constructionElement.getFirstChild(),
           "Transform");
       }
       return transforms.length;
  }

   /**
    * Return the <it>i</it><sup>th</sup> <code>{@link Transform}</code>.
    * Valid <code>i</code> values are 0 to <code>{@link #getLength}-1</code>.
    *
    * @param i index of {@link Transform} to return
    * @return the <it>i</it><sup>th</sup> transforms
    * @throws TransformationException
    */
   public Transform item(int i) throws TransformationException {

                try {
                        if (transforms==null) {
                                transforms=XMLUtils.selectDsNodes(this._constructionElement.getFirstChild(),
                                "Transform");
                        }
                        return new Transform(transforms[i], this._baseURI);
                } catch (XMLSecurityException ex) {
                        throw new TransformationException("empty", ex);
                }
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_TRANSFORMS;
   }
}
