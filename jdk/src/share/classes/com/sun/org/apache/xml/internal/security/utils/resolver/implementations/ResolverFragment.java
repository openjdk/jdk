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
package com.sun.org.apache.xml.internal.security.utils.resolver.implementations;



import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverException;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * This resolver is used for resolving same-document URIs like URI="" of URI="#id".
 *
 * @author $Author: mullan $
 * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#sec-ReferenceProcessingModel">The Reference processing model in the XML Signature spec</A>
 * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#sec-Same-Document">Same-Document URI-References in the XML Signature spec</A>
 * @see <A HREF="http://www.ietf.org/rfc/rfc2396.txt">Section 4.2 of RFC 2396</A>
 */
public class ResolverFragment extends ResourceResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                            ResolverFragment.class.getName());
   public boolean engineIsThreadSafe() {
           return true;
   }
   /**
    * Method engineResolve
    *
    * @inheritDoc
    * @param uri
    * @param baseURI
    */
   public XMLSignatureInput engineResolve(Attr uri, String baseURI)
       throws ResourceResolverException
   {
        String uriNodeValue = uri.getNodeValue();
        Document doc = uri.getOwnerElement().getOwnerDocument();

        Node selectedElem = null;
        if (uriNodeValue.equals("")) {

           /*
            * Identifies the node-set (minus any comment nodes) of the XML
            * resource containing the signature
            */

            log.log(java.util.logging.Level.FINE, "ResolverFragment with empty URI (means complete document)");
            selectedElem = doc;
        } else {

            /*
             * URI="#chapter1"
             * Identifies a node-set containing the element with ID attribute
             * value 'chapter1' of the XML resource containing the signature.
             * XML Signature (and its applications) modify this node-set to
             * include the element plus all descendents including namespaces and
             * attributes -- but not comments.
             */
            String id = uriNodeValue.substring(1);

            selectedElem = doc.getElementById(id);
            if (selectedElem == null) {
                Object exArgs[] = { id };
                throw new ResourceResolverException(
                    "signature.Verification.MissingID", exArgs, uri, baseURI);
            }
            if (secureValidation) {
                Element start = uri.getOwnerDocument().getDocumentElement();
                if (!XMLUtils.protectAgainstWrappingAttack(start, id)) {
                    Object exArgs[] = { id };
                    throw new ResourceResolverException(
                        "signature.Verification.MultipleIDs", exArgs,
                        uri, baseURI);
                }
            }
            if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Try to catch an Element with ID " + id + " and Element was " + selectedElem);
        }

        XMLSignatureInput result = new XMLSignatureInput(selectedElem);
        result.setExcludeComments(true);

        result.setMIMEType("text/xml");
        if (baseURI != null && baseURI.length() > 0) {
            result.setSourceURI(baseURI.concat(uri.getNodeValue()));
        } else {
            result.setSourceURI(uri.getNodeValue());
        }
        return result;
    }

   /**
    * Method engineCanResolve
    * @inheritDoc
    * @param uri
    * @param BaseURI
    *
    */
   public boolean engineCanResolve(Attr uri, String BaseURI) {

      if (uri == null) {
         log.log(java.util.logging.Level.FINE, "Quick fail for null uri");
         return false;
      }

      String uriNodeValue = uri.getNodeValue();

      if  (uriNodeValue.equals("") ||
             (
            (uriNodeValue.charAt(0)=='#')
              && !((uriNodeValue.charAt(1)=='x') && uriNodeValue.startsWith("#xpointer("))
              )
           ){
         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "State I can resolve reference: \"" + uriNodeValue + "\"");
         return true;
      }
      if (log.isLoggable(java.util.logging.Level.FINE))
        log.log(java.util.logging.Level.FINE, "Do not seem to be able to resolve reference: \"" + uriNodeValue + "\"");
      return false;
   }

}
