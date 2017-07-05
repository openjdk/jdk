/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2008 The Apache Software Foundation.
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
package com.sun.org.apache.xml.internal.security.utils;



import java.math.BigInteger;
import java.util.HashMap;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;


/**
 * This is the base class to all Objects which have a direct 1:1 mapping to an
 * Element in a particular namespace.
 *
 * @author $Author: mullan $
 */
public abstract class ElementProxy {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(ElementProxy.class.getName());

   /**
    * Returns the namespace of the Elements of the sub-class.
    *
    * @return the namespace of the Elements of the sub-class.
    */
   public abstract String getBaseNamespace();

   /**
    * Returns the localname of the Elements of the sub-class.
    *
    * @return the localname of the Elements of the sub-class.
    */
   public abstract String getBaseLocalName();

   /** Field _constructionElement */
   protected Element _constructionElement = null;

   /** Field _baseURI */
   protected String _baseURI = null;

   /** Field _doc */
   protected Document _doc = null;

   /**
    * Constructor ElementProxy
    *
    */
   public ElementProxy() {
   }

   /**
    * Constructor ElementProxy
    *
    * @param doc
    */
   public ElementProxy(Document doc) {
      if (doc == null) {
         throw new RuntimeException("Document is null");
      }

      this._doc = doc;
      this._constructionElement = createElementForFamilyLocal(this._doc,
                  this.getBaseNamespace(), this.getBaseLocalName());
   }
   protected Element createElementForFamilyLocal(Document doc, String namespace,
           String localName) {
                  Element result = null;
              if (namespace == null) {
                 result = doc.createElementNS(null, localName);
              } else {
                  String baseName=this.getBaseNamespace();
                  String prefix=ElementProxy.getDefaultPrefix(baseName);
                 if ((prefix == null) || (prefix.length() == 0)) {
                    result = doc.createElementNS(namespace, localName);

                    result.setAttributeNS(Constants.NamespaceSpecNS, "xmlns",
                                          namespace);
                 } else {
                         String tagName=null;
                         String defaultPrefixNaming=ElementProxy.getDefaultPrefixBindings(baseName);
                         StringBuffer sb=new StringBuffer(prefix);
                         sb.append(':');
                         sb.append(localName);
                         tagName=sb.toString();
                    result = doc.createElementNS(namespace, tagName );

                    result.setAttributeNS(Constants.NamespaceSpecNS,  defaultPrefixNaming,
                                          namespace);
                 }
              }
              return result;
}


   /**
    * This method creates an Element in a given namespace with a given localname.
    * It uses the {@link ElementProxy#getDefaultPrefix} method to decide whether
    * a particular prefix is bound to that namespace.
    * <BR />
    * This method was refactored out of the constructor.
    *
    * @param doc
    * @param namespace
    * @param localName
    * @return The element created.
    */
   public static Element createElementForFamily(Document doc, String namespace,
           String localName) {
       //Element nscontext = XMLUtils.createDSctx(doc, "x", namespace);
      Element result = null;
      String prefix = ElementProxy.getDefaultPrefix(namespace);

      if (namespace == null) {
         result = doc.createElementNS(null, localName);
      } else {
         if ((prefix == null) || (prefix.length() == 0)) {
            result = doc.createElementNS(namespace, localName);

            result.setAttributeNS(Constants.NamespaceSpecNS, "xmlns",
                                  namespace);
         } else {
            result = doc.createElementNS(namespace, prefix + ":" + localName);

            result.setAttributeNS(Constants.NamespaceSpecNS,  ElementProxy.getDefaultPrefixBindings(namespace),
                                  namespace);
         }
      }

      return result;
   }

   /**
    * Method setElement
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public void setElement(Element element, String BaseURI)
           throws XMLSecurityException {

      if (element == null) {
         throw new XMLSecurityException("ElementProxy.nullElement");
      }

      if (log.isLoggable(java.util.logging.Level.FINE)) {
        log.log(java.util.logging.Level.FINE, "setElement(" + element.getTagName() + ", \"" + BaseURI + "\"");
      }

      this._doc = element.getOwnerDocument();
      this._constructionElement = element;
      this._baseURI = BaseURI;
   }

   /**
    * Constructor ElementProxy
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public ElementProxy(Element element, String BaseURI)
           throws XMLSecurityException {
      if (element == null) {
         throw new XMLSecurityException("ElementProxy.nullElement");
      }

      if (log.isLoggable(java.util.logging.Level.FINE)) {
        log.log(java.util.logging.Level.FINE, "setElement(\"" + element.getTagName() + "\", \"" + BaseURI
                + "\")");
      }

      this._doc = element.getOwnerDocument();
      this._constructionElement = element;
      this._baseURI = BaseURI;

      this.guaranteeThatElementInCorrectSpace();
   }

   /**
    * Returns the Element which was constructed by the Object.
    *
    * @return the Element which was constructed by the Object.
    */
   public final Element getElement() {
      return this._constructionElement;
   }

   /**
    * Returns the Element plus a leading and a trailing CarriageReturn Text node.
    *
    * @return the Element which was constructed by the Object.
    */
   public final NodeList getElementPlusReturns() {

      HelperNodeList nl = new HelperNodeList();

      nl.appendChild(this._doc.createTextNode("\n"));
      nl.appendChild(this.getElement());
      nl.appendChild(this._doc.createTextNode("\n"));

      return nl;
   }

   /**
    * Method getDocument
    *
    * @return the Document where this element is contained.
    */
   public Document getDocument() {
      return this._doc;
   }

   /**
    * Method getBaseURI
    *
    * @return the base uri of the namespace of this element
    */
   public String getBaseURI() {
      return this._baseURI;
   }

   static ElementChecker checker = new ElementCheckerImpl.InternedNsChecker();

   /**
    * Method guaranteeThatElementInCorrectSpace
    *
    * @throws XMLSecurityException
    */
   void guaranteeThatElementInCorrectSpace()
           throws XMLSecurityException {

          checker.guaranteeThatElementInCorrectSpace(this,this._constructionElement);

   }

   /**
    * Method setVal
    *
    * @param bi
    * @param localname
    */
   public void addBigIntegerElement(BigInteger bi, String localname) {

      if (bi != null) {
         Element e = XMLUtils.createElementInSignatureSpace(this._doc,
                        localname);

         Base64.fillElementWithBigInteger(e, bi);
         this._constructionElement.appendChild(e);
         XMLUtils.addReturnToElement(this._constructionElement);
      }
   }

   /**
    * Method addBase64Element
    *
    * @param bytes
    * @param localname
    */
   public void addBase64Element(byte[] bytes, String localname) {

      if (bytes != null) {

         Element e = Base64.encodeToElement(this._doc, localname, bytes);

         this._constructionElement.appendChild(e);
         if (!XMLUtils.ignoreLineBreaks()) {
            this._constructionElement.appendChild(this._doc.createTextNode("\n"));
         }
      }
   }

   /**
    * Method addTextElement
    *
    * @param text
    * @param localname
    */
   public void addTextElement(String text, String localname) {

      Element e = XMLUtils.createElementInSignatureSpace(this._doc, localname);
      Text t = this._doc.createTextNode(text);

      e.appendChild(t);
      this._constructionElement.appendChild(e);
      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addBase64Text
    *
    * @param bytes
    */
   public void addBase64Text(byte[] bytes) {

      if (bytes != null) {
         Text t = XMLUtils.ignoreLineBreaks()
             ? this._doc.createTextNode(Base64.encode(bytes))
             : this._doc.createTextNode("\n" + Base64.encode(bytes) + "\n");
         this._constructionElement.appendChild(t);
      }
   }

   /**
    * Method addText
    *
    * @param text
    */
   public void addText(String text) {

      if (text != null) {
         Text t = this._doc.createTextNode(text);

         this._constructionElement.appendChild(t);
      }
   }

   /**
    * Method getVal
    *
    * @param localname
    * @param namespace
    * @return The biginter contained in the given element
 * @throws Base64DecodingException
    */
   public BigInteger getBigIntegerFromChildElement(
           String localname, String namespace) throws Base64DecodingException {

                return Base64.decodeBigIntegerFromText(
                                XMLUtils.selectNodeText(this._constructionElement.getFirstChild(),
                                                namespace,localname,0));

   }

   /**
    * Method getBytesFromChildElement
    * @deprecated
    * @param localname
    * @param namespace
    * @return the bytes
    * @throws XMLSecurityException
    */
   public byte[] getBytesFromChildElement(String localname, String namespace)
           throws XMLSecurityException {

         Element e =
             XMLUtils.selectNode(
                 this._constructionElement.getFirstChild(),
                 namespace,
                 localname,
                 0);

         return Base64.decode(e);
   }

   /**
    * Method getTextFromChildElement
    *
    * @param localname
    * @param namespace
    * @return the Text of the textNode
    */
   public String getTextFromChildElement(String localname, String namespace) {

         Text t =
             (Text) XMLUtils.selectNode(
                        this._constructionElement.getFirstChild(),
                        namespace,
                        localname,
                        0).getFirstChild();

         return t.getData();
   }

   /**
    * Method getBytesFromTextChild
    *
    * @return The base64 bytes from the text children of this element
    * @throws XMLSecurityException
    */
   public byte[] getBytesFromTextChild() throws XMLSecurityException {
      return Base64.decode
         (XMLUtils.getFullTextChildrenFromElement(this._constructionElement));
   }

   /**
    * Method getTextFromTextChild
    *
    * @return the Text obtained by concatenating all the text nodes of this
    *    element
    */
   public String getTextFromTextChild() {
      return XMLUtils.getFullTextChildrenFromElement(this._constructionElement);
   }

   /**
    * Method length
    *
    * @param namespace
    * @param localname
    * @return the number of elements {namespace}:localname under this element
    */
   public int length(String namespace, String localname) {
            int number=0;
            Node sibling=this._constructionElement.getFirstChild();
            while (sibling!=null) {
                if (localname.equals(sibling.getLocalName())
                                &&
                                        namespace==sibling.getNamespaceURI() ) {
                        number++;
                }
                sibling=sibling.getNextSibling();
            }
            return number;
     }

   /**
    * Adds an xmlns: definition to the Element. This can be called as follows:
    *
    * <PRE>
    * // set namespace with ds prefix
    * xpathContainer.setXPathNamespaceContext("ds", "http://www.w3.org/2000/09/xmldsig#");
    * xpathContainer.setXPathNamespaceContext("xmlns:ds", "http://www.w3.org/2000/09/xmldsig#");
    * </PRE>
    *
    * @param prefix
    * @param uri
    * @throws XMLSecurityException
    */
   public void setXPathNamespaceContext(String prefix, String uri)
           throws XMLSecurityException {

      String ns;

      if ((prefix == null) || (prefix.length() == 0)) {
       throw new XMLSecurityException("defaultNamespaceCannotBeSetHere");
      } else if (prefix.equals("xmlns")) {
        throw new XMLSecurityException("defaultNamespaceCannotBeSetHere");
      } else if (prefix.startsWith("xmlns:")) {
         ns = prefix;//"xmlns:" + prefix.substring("xmlns:".length());
      } else {
         ns = "xmlns:" + prefix;
      }



      Attr a = this._constructionElement.getAttributeNodeNS(Constants.NamespaceSpecNS, ns);

      if (a != null) {
       if (!a.getNodeValue().equals(uri)) {
         Object exArgs[] = { ns,
                             this._constructionElement.getAttributeNS(null,
                                                                      ns) };

         throw new XMLSecurityException("namespacePrefixAlreadyUsedByOtherURI",
                                        exArgs);
       }
       return;
      }

      this._constructionElement.setAttributeNS(Constants.NamespaceSpecNS, ns,
                                               uri);
   }

   /** Field _prefixMappings */
   static HashMap _prefixMappings = new HashMap();
   static HashMap _prefixMappingsBindings = new HashMap();

    /**
     * Method setDefaultPrefix
     *
     * @param namespace
     * @param prefix
     * @throws XMLSecurityException
     */
    public static void setDefaultPrefix(String namespace, String prefix)
        throws XMLSecurityException {

        if (ElementProxy._prefixMappings.containsValue(prefix)) {

            Object storedNamespace=ElementProxy._prefixMappings.get(namespace);
            if (!storedNamespace.equals(prefix)) {
                Object exArgs[] = { prefix, namespace, storedNamespace };

                throw new XMLSecurityException("prefix.AlreadyAssigned", exArgs);
            }
        }
        if (Constants.SignatureSpecNS.equals(namespace)) {
            XMLUtils.dsPrefix=prefix;
        }
        ElementProxy._prefixMappings.put(namespace, prefix.intern());
        if (prefix.length() == 0) {
            ElementProxy._prefixMappingsBindings.put(namespace, "xmlns");
        } else {
            ElementProxy._prefixMappingsBindings.put(namespace, ("xmlns:"+prefix).intern());
        }
   }

    /**
     * Method getDefaultPrefix
     *
     * @param namespace
     * @return the default prefix bind to this element.
     */
    public static String getDefaultPrefix(String namespace) {
        return (String) ElementProxy._prefixMappings.get(namespace);
    }

    public static String getDefaultPrefixBindings(String namespace) {
        return (String) ElementProxy._prefixMappingsBindings.get(namespace);
    }
}
