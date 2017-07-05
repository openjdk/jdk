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
package com.sun.org.apache.xml.internal.security.transforms.params;



import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.transforms.TransformParam;
import com.sun.org.apache.xml.internal.security.utils.ElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * This Object serves as Content for the ds:Transforms for exclusive
 * Canonicalization.
 * <BR />
 * It implements the {@link Element} interface
 * and can be used directly in a DOM tree.
 *
 * @author Christian Geuer-Pollmann
 */
public class InclusiveNamespaces extends ElementProxy
        implements TransformParam {

   /** Field _TAG_EC_INCLUSIVENAMESPACES */
   public static final String _TAG_EC_INCLUSIVENAMESPACES =
      "InclusiveNamespaces";

   /** Field _ATT_EC_PREFIXLIST */
   public static final String _ATT_EC_PREFIXLIST = "PrefixList";

   /** Field ExclusiveCanonicalizationNamespace */
   public static final String ExclusiveCanonicalizationNamespace =
      "http://www.w3.org/2001/10/xml-exc-c14n#";

   /**
    * Constructor XPathContainer
    *
    * @param doc
    * @param prefixList
    */
   public InclusiveNamespaces(Document doc, String prefixList) {
      this(doc, InclusiveNamespaces.prefixStr2Set(prefixList));
   }

   /**
    * Constructor InclusiveNamespaces
    *
    * @param doc
    * @param prefixes
    */
   public InclusiveNamespaces(Document doc, Set prefixes) {

      super(doc);

      StringBuffer sb = new StringBuffer();
      SortedSet prefixList = new TreeSet(prefixes);


      Iterator it = prefixList.iterator();

      while (it.hasNext()) {
         String prefix = (String) it.next();

         if (prefix.equals("xmlns")) {
            sb.append("#default ");
         } else {
            sb.append(prefix + " ");
         }
      }

      this._constructionElement
         .setAttributeNS(null, InclusiveNamespaces._ATT_EC_PREFIXLIST,
                       sb.toString().trim());
   }

   /**
    * Method getInclusiveNamespaces
    *
    * @return The Inclusive Namespace string
    */
   public String getInclusiveNamespaces() {
      return this._constructionElement
         .getAttributeNS(null, InclusiveNamespaces._ATT_EC_PREFIXLIST);
   }

   /**
    * Constructor InclusiveNamespaces
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public InclusiveNamespaces(Element element, String BaseURI)
           throws XMLSecurityException {
      super(element, BaseURI);
   }

   /**
    * Decodes the <code>inclusiveNamespaces</code> String and returns all
    * selected namespace prefixes as a Set. The <code>#default</code>
    * namespace token is represented as an empty namespace prefix
    * (<code>"xmlns"</code>).
    * <BR/>
    * The String <code>inclusiveNamespaces=" xenc    ds #default"</code>
    * is returned as a Set containing the following Strings:
    * <UL>
    * <LI><code>xmlns</code></LI>
    * <LI><code>xenc</code></LI>
    * <LI><code>ds</code></LI>
    * </UL>
    *
    * @param inclusiveNamespaces
    * @return A set to string
    */
   public static SortedSet prefixStr2Set(String inclusiveNamespaces) {

      SortedSet prefixes = new TreeSet();

      if ((inclusiveNamespaces == null)
              || (inclusiveNamespaces.length() == 0)) {
         return prefixes;
      }

      StringTokenizer st = new StringTokenizer(inclusiveNamespaces, " \t\r\n");

      while (st.hasMoreTokens()) {
         String prefix = st.nextToken();

         if (prefix.equals("#default")) {
            prefixes.add("xmlns" );
         } else {
            prefixes.add( prefix);
         }
      }

      return prefixes;
   }

   /**
    * Method getBaseNamespace
    *
    * @inheritDoc
    */
   public String getBaseNamespace() {
      return InclusiveNamespaces.ExclusiveCanonicalizationNamespace;
   }

   /**
    * Method getBaseLocalName
    *
    * @inheritDoc
    */
   public String getBaseLocalName() {
      return InclusiveNamespaces._TAG_EC_INCLUSIVENAMESPACES;
   }
}
