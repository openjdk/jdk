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
import java.util.HashMap;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.AlgorithmAlreadyRegisteredException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.HelperNodeList;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Implements the behaviour of the <code>ds:Transform</code> element.
 *
 * This <code>Transform</code>(Factory) class role as the Factory and Proxy of
 * implemanting class that have the functionality of <a
 * href=http://www.w3.org/TR/xmldsig-core/#sec-TransformAlg>a Transform
 * algorithm</a>.
 * Implements the Factory and Proxy pattern for ds:Transform algorithms.
 *
 * @author Christian Geuer-Pollmann
 * @see Transforms
 * @see TransformSpi
 *
 */
public final class Transform extends SignatureElementProxy {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(Transform.class.getName());

   /** Field _alreadyInitialized */
   static boolean _alreadyInitialized = false;

   /** All available Transform classes are registered here */
   static HashMap _transformHash = null;

   /** Field transformSpi */
   protected TransformSpi transformSpi = null;

   /**
    * Constructs {@link Transform}
    *
    * @param doc the {@link Document} in which <code>Transform</code> will be placed
    * @param algorithmURI URI representation of
    * <code>Transform algorithm</code> will be specified as parameter of
    * {@link #getInstance(Document, String)}, when generate. </br>
    * @param contextNodes the child node list of <code>Transform</code> element
    * @throws InvalidTransformException
    */
   public Transform(Document doc, String algorithmURI, NodeList contextNodes)
           throws InvalidTransformException {

      super(doc);

      try {
         this._constructionElement.setAttributeNS(null, Constants._ATT_ALGORITHM,
                                                algorithmURI);

         Class implementingClass =
            Transform.getImplementingClass(algorithmURI);

         if(implementingClass == null) {
             Object exArgs[] = { algorithmURI };

             throw new InvalidTransformException(
                "signature.Transform.UnknownTransform", exArgs);
         }
         if (true) {
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Create URI \"" + algorithmURI + "\" class \""
                   + implementingClass + "\"");
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "The NodeList is " + contextNodes);
         }

         // create the custom Transform object
         this.transformSpi =
            (TransformSpi) implementingClass.newInstance();

         this.transformSpi.setTransform(this);

         // give it to the current document
         if (contextNodes != null) {
            /*
            while (contextNodes.getLength() > 0) {
               this._constructionElement.appendChild(contextNodes.item(0));
            }
            */

            for (int i = 0; i < contextNodes.getLength(); i++) {
               this._constructionElement.appendChild(contextNodes.item(i).cloneNode(true));
            }

         }
      } catch (IllegalAccessException ex) {
         Object exArgs[] = { algorithmURI };

         throw new InvalidTransformException(
            "signature.Transform.UnknownTransform", exArgs, ex);
      } catch (InstantiationException ex) {
         Object exArgs[] = { algorithmURI };

         throw new InvalidTransformException(
            "signature.Transform.UnknownTransform", exArgs, ex);
      }
   }

   /**
    * This constructor can only be called from the {@link Transforms} object, so
    * it's protected.
    *
    * @param element <code>ds:Transform</code> element
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @throws InvalidTransformException
    * @throws TransformationException
    * @throws XMLSecurityException
    */
   public Transform(Element element, String BaseURI)
           throws InvalidTransformException, TransformationException,
                  XMLSecurityException {

      super(element, BaseURI);

      // retrieve Algorithm Attribute from ds:Transform
      String AlgorithmURI = element.getAttributeNS(null, Constants._ATT_ALGORITHM);

      if ((AlgorithmURI == null) || (AlgorithmURI.length() == 0)) {
         Object exArgs[] = { Constants._ATT_ALGORITHM,
                             Constants._TAG_TRANSFORM };

         throw new TransformationException("xml.WrongContent", exArgs);
      }

      try {
         Class implementingClass = (Class) _transformHash.get(AlgorithmURI);
         this.transformSpi =
            (TransformSpi) implementingClass.newInstance();

         this.transformSpi.setTransform(this);
      } catch (IllegalAccessException e) {
         Object exArgs[] = { AlgorithmURI };

         throw new InvalidTransformException(
            "signature.Transform.UnknownTransform", exArgs);
      } catch (InstantiationException e) {
         Object exArgs[] = { AlgorithmURI };

         throw new InvalidTransformException(
            "signature.Transform.UnknownTransform", exArgs);
      } catch (NullPointerException e) {
                  Object exArgs[] = { AlgorithmURI };

                 throw new InvalidTransformException(
                    "signature.Transform.UnknownTransform", exArgs);
        }
   }

   /**
    * Generates a Transform object that implements the specified <code>Transform algorithm</code> URI.
    *
    * @param algorithmURI <code>Transform algorithm</code> URI representation, such as specified in <a href=http://www.w3.org/TR/xmldsig-core/#sec-TransformAlg>Transform algorithm </a>
    * @param doc the proxy {@link Document}
    * @return <code>{@link Transform}</code> object
    * @throws InvalidTransformException
    */
   public static final Transform getInstance(
           Document doc, String algorithmURI) throws InvalidTransformException {
      return Transform.getInstance(doc, algorithmURI, (NodeList) null);
   }

   /**
    * Generates a Transform object that implements the specified <code>Transform algorithm</code> URI.
    *
    * @param algorithmURI <code>Transform algorithm</code> URI representation, such as specified in <a href=http://www.w3.org/TR/xmldsig-core/#sec-TransformAlg>Transform algorithm </a>
    * @param contextChild the child element of <code>Transform</code> element
    * @param doc the proxy {@link Document}
    * @return <code>{@link Transform}</code> object
    * @throws InvalidTransformException
    */
   public static final Transform getInstance(
           Document doc, String algorithmURI, Element contextChild)
              throws InvalidTransformException {

      HelperNodeList contextNodes = new HelperNodeList();

      contextNodes.appendChild(doc.createTextNode("\n"));
      contextNodes.appendChild(contextChild);
      contextNodes.appendChild(doc.createTextNode("\n"));

      return Transform.getInstance(doc, algorithmURI, contextNodes);
   }

   /**
    * Generates a Transform object that implements the specified <code>Transform algorithm</code> URI.
    *
    * @param algorithmURI <code>Transform algorithm</code> URI form, such as specified in <a href=http://www.w3.org/TR/xmldsig-core/#sec-TransformAlg>Transform algorithm </a>
    * @param contextNodes the child node list of <code>Transform</code> element
    * @param doc the proxy {@link Document}
    * @return <code>{@link Transform}</code> object
    * @throws InvalidTransformException
    */
   public static final Transform getInstance(
           Document doc, String algorithmURI, NodeList contextNodes)
              throws InvalidTransformException {
      return new Transform(doc, algorithmURI, contextNodes);
   }

   /**
    * Initalizes for this {@link Transform}
    *
    */
   public static void init() {

      if (!_alreadyInitialized) {
         _transformHash = new HashMap(10);
         _alreadyInitialized = true;
      }
   }

   /**
    * Registers implementing class of the Transform algorithm with algorithmURI
    *
    * @param algorithmURI algorithmURI URI representation of <code>Transform algorithm</code>
    *  will be specified as parameter of {@link #getInstance(Document, String)}, when generate. </br>
    * @param implementingClass <code>implementingClass</code> the implementing class of {@link TransformSpi}
    * @throws AlgorithmAlreadyRegisteredException if specified algorithmURI is already registered
    */
   public static void register(String algorithmURI, String implementingClass)
           throws AlgorithmAlreadyRegisteredException {

      {

         // are we already registered?
         Class registeredClass = Transform.getImplementingClass(algorithmURI);

         if ((registeredClass != null) ) {
            Object exArgs[] = { algorithmURI, registeredClass };

            throw new AlgorithmAlreadyRegisteredException(
               "algorithm.alreadyRegistered", exArgs);
         }

         ClassLoader cl = (ClassLoader) AccessController.doPrivileged(
             new PrivilegedAction() {
                 public Object run() {
                     return Thread.currentThread().getContextClassLoader();
                 }
             });

         try {
             Transform._transformHash.put
                 (algorithmURI, Class.forName(implementingClass, true, cl));
         } catch (ClassNotFoundException e) {
             // TODO Auto-generated catch block
             e.printStackTrace();
         }
      }
   }

   /**
    * Returns the URI representation of Transformation algorithm
    *
    * @return the URI representation of Transformation algorithm
    */
   public final String getURI() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_ALGORITHM);
   }

   /**
    * Transforms the input, and generats {@link XMLSignatureInput} as output.
    * @param input input {@link XMLSignatureInput} which can supplied Octect Stream and NodeSet as Input of Transformation
    *
    * @return the {@link XMLSignatureInput} class as the result of transformation
    * @throws CanonicalizationException
    * @throws IOException
    * @throws InvalidCanonicalizerException
    * @throws TransformationException
    */
   public XMLSignatureInput performTransform(XMLSignatureInput input)
           throws IOException, CanonicalizationException,
                  InvalidCanonicalizerException, TransformationException {

      XMLSignatureInput result = null;

      try {
         result = transformSpi.enginePerformTransform(input);
      } catch (ParserConfigurationException ex) {
         Object exArgs[] = { this.getURI(), "ParserConfigurationException" };

         throw new CanonicalizationException(
            "signature.Transform.ErrorDuringTransform", exArgs, ex);
      } catch (SAXException ex) {
         Object exArgs[] = { this.getURI(), "SAXException" };

         throw new CanonicalizationException(
            "signature.Transform.ErrorDuringTransform", exArgs, ex);
      }

      return result;
   }

   /**
    * Transforms the input, and generats {@link XMLSignatureInput} as output.
    * @param input input {@link XMLSignatureInput} which can supplied Octect Stream and NodeSet as Input of Transformation
    * @param os where to output the result of the last transformation
    *
    * @return the {@link XMLSignatureInput} class as the result of transformation
    * @throws CanonicalizationException
    * @throws IOException
    * @throws InvalidCanonicalizerException
    * @throws TransformationException
    */
   public XMLSignatureInput performTransform(XMLSignatureInput input, OutputStream os)
   throws IOException, CanonicalizationException,
          InvalidCanonicalizerException, TransformationException {

            XMLSignatureInput result = null;

            try {
                result = transformSpi.enginePerformTransform(input,os);
            } catch (ParserConfigurationException ex) {
                Object exArgs[] = { this.getURI(), "ParserConfigurationException" };

                throw new CanonicalizationException(
                                "signature.Transform.ErrorDuringTransform", exArgs, ex);
            } catch (SAXException ex) {
                Object exArgs[] = { this.getURI(), "SAXException" };

                throw new CanonicalizationException(
                                "signature.Transform.ErrorDuringTransform", exArgs, ex);
            }

            return result;
   }

   /**
    * Method getImplementingClass
    *
    * @param URI
    * @return The name of the class implementing the URI.
    */
   private static Class getImplementingClass(String URI) {
       return (Class)Transform._transformHash.get(URI);
   }


   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_TRANSFORM;
   }
}
