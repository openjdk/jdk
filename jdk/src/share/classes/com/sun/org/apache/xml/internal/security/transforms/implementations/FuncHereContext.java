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
package com.sun.org.apache.xml.internal.security.transforms.implementations;



import com.sun.org.apache.xml.internal.dtm.DTMManager;
import com.sun.org.apache.xml.internal.security.utils.I18n;
import com.sun.org.apache.xpath.internal.CachedXPathAPI;
import com.sun.org.apache.xpath.internal.XPathContext;
import org.w3c.dom.Node;


/**
 * {@link FuncHereContext} extends {@link XPathContext} for supplying context
 * for the <CODE>here()</CODE> function. The here() function needs to know
 * <I>where</I> in an XML instance the XPath text string appeared. This can be
 * in {@link org.w3c.dom.Text}, {@link org.w3c.dom.Attr}ibutes and {@ProcessingInstrinction} nodes. The
 * correct node must be supplied to the constructor of {@link FuncHereContext}.
 * The supplied Node MUST contain the XPath which is to be executed.
 *
 * <PRE>
 * From: Scott_Boag\@lotus.com
 * To: Christian Geuer-Pollmann <maillist\@nue.et-inf.uni-siegen.de>
 * CC: xalan-dev@xml.apache.org
 * Subject: Re: Cleanup of XPathContext & definition of XSLTContext
 * Date: Tue, 21 Aug 2001 18:36:24 -0400
 *
 * > My point is to say to get this baby to run, the XPath must have a
 * > possibility to retrieve the information where itself occured in a
 * > document.
 *
 * It sounds to me like you have to derive an XMLSigContext from the
 * XPathContext?
 *
 * > and supplied the Node which contains the xpath string as "owner". Question:
 * > Is this the correct use of the owner object? It works, but I don't know
 * > whether this is correct from the xalan-philosophy...
 *
 * Philosophically it's fine.  The owner is the TransformerImpl if XPath is
 * running under XSLT.  If it is not running under XSLT, it can be whatever
 * you want.
 *
 * -scott
 * </PRE>
 *
 * @author $Author: mullan $
 * @see com.sun.org.apache.xml.internal.security.transforms.implementations.FuncHere
 * @see com.sun.org.apache.xml.internal.security.utils.XPathFuncHereAPI
 * @see <A HREF="http://www.w3.org/Signature/Drafts/xmldsig-core/Overview.html#function-here">XML Signature - The here() function</A>
 */
public class FuncHereContext extends XPathContext {

   /**
    * This constuctor is disabled because if we use the here() function we
    * <I>always</I> need to know in which node the XPath occured.
    */
   private FuncHereContext() {}

   /**
    * Constructor FuncHereContext
    *
    * @param owner
    */
   public FuncHereContext(Node owner) {
      super(owner);
   }

   /**
    * Constructor FuncHereContext
    *
    * @param owner
    * @param xpathContext
    */
   public FuncHereContext(Node owner, XPathContext xpathContext) {

      super(owner);

      try {
         super.m_dtmManager = xpathContext.getDTMManager();
      } catch (IllegalAccessError iae) {
         throw new IllegalAccessError(I18n.translate("endorsed.jdk1.4.0")
                                      + " Original message was \""
                                      + iae.getMessage() + "\"");
      }
   }

   /**
    * Constructor FuncHereContext
    *
    * @param owner
    * @param previouslyUsed
    */
   public FuncHereContext(Node owner, CachedXPathAPI previouslyUsed) {

      super(owner);

      try {
         super.m_dtmManager = previouslyUsed.getXPathContext().getDTMManager();
      } catch (IllegalAccessError iae) {
         throw new IllegalAccessError(I18n.translate("endorsed.jdk1.4.0")
                                      + " Original message was \""
                                      + iae.getMessage() + "\"");
      }
   }

   /**
    * Constructor FuncHereContext
    *
    * @param owner
    * @param dtmManager
    */
   public FuncHereContext(Node owner, DTMManager dtmManager) {

      super(owner);

      try {
         super.m_dtmManager = dtmManager;
      } catch (IllegalAccessError iae) {
         throw new IllegalAccessError(I18n.translate("endorsed.jdk1.4.0")
                                      + " Original message was \""
                                      + iae.getMessage() + "\"");
      }
   }
}
