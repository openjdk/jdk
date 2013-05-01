/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: TreeWalker.java,v 1.2.4.1 2005/09/15 08:15:59 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.utils;

import com.sun.org.apache.xalan.internal.utils.SecuritySupport;
import java.io.File;

import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.LocatorImpl;

/**
 * This class does a pre-order walk of the DOM tree, calling a ContentHandler
 * interface as it goes.
 * @xsl.usage advanced
 */

public class TreeWalker
{

  /** Local reference to a ContentHandler          */
  private ContentHandler m_contentHandler = null;

  // ARGHH!!  JAXP Uses Xerces without setting the namespace processing to ON!
  // DOM2Helper m_dh = new DOM2Helper();

  /** DomHelper for this TreeWalker          */
  protected DOMHelper m_dh;

        /** Locator object for this TreeWalker          */
        private LocatorImpl m_locator = new LocatorImpl();

  /**
   * Get the ContentHandler used for the tree walk.
   *
   * @return the ContentHandler used for the tree walk
   */
  public ContentHandler getContentHandler()
  {
    return m_contentHandler;
  }

  /**
   * Get the ContentHandler used for the tree walk.
   *
   * @return the ContentHandler used for the tree walk
   */
  public void setContentHandler(ContentHandler ch)
  {
    m_contentHandler = ch;
  }

        /**
   * Constructor.
   * @param   contentHandler The implemention of the
   * @param   systemId System identifier for the document.
   * contentHandler operation (toXMLString, digest, ...)
   */
  public TreeWalker(ContentHandler contentHandler, DOMHelper dh, String systemId)
  {
    this.m_contentHandler = contentHandler;
    m_contentHandler.setDocumentLocator(m_locator);
    if (systemId != null)
        m_locator.setSystemId(systemId);
    else {
        try {
          // Bug see Bugzilla  26741
          m_locator.setSystemId(SecuritySupport.getSystemProperty("user.dir") + File.separator + "dummy.xsl");
         }
         catch (SecurityException se) {// user.dir not accessible from applet
         }
    }
    m_dh = dh;
  }

  /**
   * Constructor.
   * @param   contentHandler The implemention of the
   * contentHandler operation (toXMLString, digest, ...)
   */
  public TreeWalker(ContentHandler contentHandler, DOMHelper dh)
  {
    this.m_contentHandler = contentHandler;
    m_contentHandler.setDocumentLocator(m_locator);
    try {
       // Bug see Bugzilla  26741
      m_locator.setSystemId(SecuritySupport.getSystemProperty("user.dir") + File.separator + "dummy.xsl");
    }
    catch (SecurityException se){// user.dir not accessible from applet
    }
    m_dh = dh;
  }

  /**
   * Constructor.
   * @param   contentHandler The implemention of the
   * contentHandler operation (toXMLString, digest, ...)
   */
  public TreeWalker(ContentHandler contentHandler)
  {
    this.m_contentHandler = contentHandler;
                if (m_contentHandler != null)
                        m_contentHandler.setDocumentLocator(m_locator);
                try {
                   // Bug see Bugzilla  26741
                  m_locator.setSystemId(SecuritySupport.getSystemProperty("user.dir") + File.separator + "dummy.xsl");
                }
                catch (SecurityException se){// user.dir not accessible from applet

    }
    m_dh = new DOM2Helper();
  }

  /**
   * Perform a pre-order traversal non-recursive style.
   *
   * Note that TreeWalker assumes that the subtree is intended to represent
   * a complete (though not necessarily well-formed) document and, during a
   * traversal, startDocument and endDocument will always be issued to the
   * SAX listener.
   *
   * @param pos Node in the tree where to start traversal
   *
   * @throws TransformerException
   */
  public void traverse(Node pos) throws org.xml.sax.SAXException
  {
        this.m_contentHandler.startDocument();

        traverseFragment(pos);

        this.m_contentHandler.endDocument();
  }

  /**
   * Perform a pre-order traversal non-recursive style.
   *
   * In contrast to the traverse() method this method will not issue
   * startDocument() and endDocument() events to the SAX listener.
   *
   * @param pos Node in the tree where to start traversal
   *
   * @throws TransformerException
   */
  public void traverseFragment(Node pos) throws org.xml.sax.SAXException
  {
    Node top = pos;

    while (null != pos)
    {
      startNode(pos);

      Node nextNode = pos.getFirstChild();

      while (null == nextNode)
      {
        endNode(pos);

        if (top.equals(pos))
          break;

        nextNode = pos.getNextSibling();

        if (null == nextNode)
        {
          pos = pos.getParentNode();

          if ((null == pos) || (top.equals(pos)))
          {
            if (null != pos)
              endNode(pos);

            nextNode = null;

            break;
          }
        }
      }

      pos = nextNode;
    }
  }

  /**
   * Perform a pre-order traversal non-recursive style.

   * Note that TreeWalker assumes that the subtree is intended to represent
   * a complete (though not necessarily well-formed) document and, during a
   * traversal, startDocument and endDocument will always be issued to the
   * SAX listener.
   *
   * @param pos Node in the tree where to start traversal
   * @param top Node in the tree where to end traversal
   *
   * @throws TransformerException
   */
  public void traverse(Node pos, Node top) throws org.xml.sax.SAXException
  {

        this.m_contentHandler.startDocument();

    while (null != pos)
    {
      startNode(pos);

      Node nextNode = pos.getFirstChild();

      while (null == nextNode)
      {
        endNode(pos);

        if ((null != top) && top.equals(pos))
          break;

        nextNode = pos.getNextSibling();

        if (null == nextNode)
        {
          pos = pos.getParentNode();

          if ((null == pos) || ((null != top) && top.equals(pos)))
          {
            nextNode = null;

            break;
          }
        }
      }

      pos = nextNode;
    }
    this.m_contentHandler.endDocument();
  }

  /** Flag indicating whether following text to be processed is raw text          */
  boolean nextIsRaw = false;

  /**
   * Optimized dispatch of characters.
   */
  private final void dispatachChars(Node node)
     throws org.xml.sax.SAXException
  {
    if(m_contentHandler instanceof com.sun.org.apache.xml.internal.dtm.ref.dom2dtm.DOM2DTM.CharacterNodeHandler)
    {
      ((com.sun.org.apache.xml.internal.dtm.ref.dom2dtm.DOM2DTM.CharacterNodeHandler)m_contentHandler).characters(node);
    }
    else
    {
      String data = ((Text) node).getData();
      this.m_contentHandler.characters(data.toCharArray(), 0, data.length());
    }
  }

  /**
   * Start processing given node
   *
   *
   * @param node Node to process
   *
   * @throws org.xml.sax.SAXException
   */
  protected void startNode(Node node) throws org.xml.sax.SAXException
  {

    if (m_contentHandler instanceof NodeConsumer)
    {
      ((NodeConsumer) m_contentHandler).setOriginatingNode(node);
    }

                if (node instanceof Locator)
                {
                        Locator loc = (Locator)node;
                        m_locator.setColumnNumber(loc.getColumnNumber());
                        m_locator.setLineNumber(loc.getLineNumber());
                        m_locator.setPublicId(loc.getPublicId());
                        m_locator.setSystemId(loc.getSystemId());
                }
                else
                {
                        m_locator.setColumnNumber(0);
      m_locator.setLineNumber(0);
                }

    switch (node.getNodeType())
    {
    case Node.COMMENT_NODE :
    {
      String data = ((Comment) node).getData();

      if (m_contentHandler instanceof LexicalHandler)
      {
        LexicalHandler lh = ((LexicalHandler) this.m_contentHandler);

        lh.comment(data.toCharArray(), 0, data.length());
      }
    }
    break;
    case Node.DOCUMENT_FRAGMENT_NODE :

      // ??;
      break;
    case Node.DOCUMENT_NODE :

      break;
    case Node.ELEMENT_NODE :
      NamedNodeMap atts = ((Element) node).getAttributes();
      int nAttrs = atts.getLength();
      // System.out.println("TreeWalker#startNode: "+node.getNodeName());

      for (int i = 0; i < nAttrs; i++)
      {
        Node attr = atts.item(i);
        String attrName = attr.getNodeName();

        // System.out.println("TreeWalker#startNode: attr["+i+"] = "+attrName+", "+attr.getNodeValue());
        if (attrName.equals("xmlns") || attrName.startsWith("xmlns:"))
        {
          // System.out.println("TreeWalker#startNode: attr["+i+"] = "+attrName+", "+attr.getNodeValue());
          int index;
          // Use "" instead of null, as Xerces likes "" for the
          // name of the default namespace.  Fix attributed
          // to "Steven Murray" <smurray@ebt.com>.
          String prefix = (index = attrName.indexOf(":")) < 0
                          ? "" : attrName.substring(index + 1);

          this.m_contentHandler.startPrefixMapping(prefix,
                                                   attr.getNodeValue());
        }

      }

      // System.out.println("m_dh.getNamespaceOfNode(node): "+m_dh.getNamespaceOfNode(node));
      // System.out.println("m_dh.getLocalNameOfNode(node): "+m_dh.getLocalNameOfNode(node));
      String ns = m_dh.getNamespaceOfNode(node);
      if(null == ns)
        ns = "";
      this.m_contentHandler.startElement(ns,
                                         m_dh.getLocalNameOfNode(node),
                                         node.getNodeName(),
                                         new AttList(atts, m_dh));
      break;
    case Node.PROCESSING_INSTRUCTION_NODE :
    {
      ProcessingInstruction pi = (ProcessingInstruction) node;
      String name = pi.getNodeName();

      // String data = pi.getData();
      if (name.equals("xslt-next-is-raw"))
      {
        nextIsRaw = true;
      }
      else
      {
        this.m_contentHandler.processingInstruction(pi.getNodeName(),
                                                    pi.getData());
      }
    }
    break;
    case Node.CDATA_SECTION_NODE :
    {
      boolean isLexH = (m_contentHandler instanceof LexicalHandler);
      LexicalHandler lh = isLexH
                          ? ((LexicalHandler) this.m_contentHandler) : null;

      if (isLexH)
      {
        lh.startCDATA();
      }

      dispatachChars(node);

      {
        if (isLexH)
        {
          lh.endCDATA();
        }
      }
    }
    break;
    case Node.TEXT_NODE :
    {
      //String data = ((Text) node).getData();

      if (nextIsRaw)
      {
        nextIsRaw = false;

        m_contentHandler.processingInstruction(javax.xml.transform.Result.PI_DISABLE_OUTPUT_ESCAPING, "");
        dispatachChars(node);
        m_contentHandler.processingInstruction(javax.xml.transform.Result.PI_ENABLE_OUTPUT_ESCAPING, "");
      }
      else
      {
        dispatachChars(node);
      }
    }
    break;
    case Node.ENTITY_REFERENCE_NODE :
    {
      EntityReference eref = (EntityReference) node;

      if (m_contentHandler instanceof LexicalHandler)
      {
        ((LexicalHandler) this.m_contentHandler).startEntity(
          eref.getNodeName());
      }
      else
      {

        // warning("Can not output entity to a pure SAX ContentHandler");
      }
    }
    break;
    default :
    }
  }

  /**
   * End processing of given node
   *
   *
   * @param node Node we just finished processing
   *
   * @throws org.xml.sax.SAXException
   */
  protected void endNode(Node node) throws org.xml.sax.SAXException
  {

    switch (node.getNodeType())
    {
    case Node.DOCUMENT_NODE :
      break;

    case Node.ELEMENT_NODE :
      String ns = m_dh.getNamespaceOfNode(node);
      if(null == ns)
        ns = "";
      this.m_contentHandler.endElement(ns,
                                         m_dh.getLocalNameOfNode(node),
                                         node.getNodeName());

      NamedNodeMap atts = ((Element) node).getAttributes();
      int nAttrs = atts.getLength();

      for (int i = 0; i < nAttrs; i++)
      {
        Node attr = atts.item(i);
        String attrName = attr.getNodeName();

        if (attrName.equals("xmlns") || attrName.startsWith("xmlns:"))
        {
          int index;
          // Use "" instead of null, as Xerces likes "" for the
          // name of the default namespace.  Fix attributed
          // to "Steven Murray" <smurray@ebt.com>.
          String prefix = (index = attrName.indexOf(":")) < 0
                          ? "" : attrName.substring(index + 1);

          this.m_contentHandler.endPrefixMapping(prefix);
        }
      }
      break;
    case Node.CDATA_SECTION_NODE :
      break;
    case Node.ENTITY_REFERENCE_NODE :
    {
      EntityReference eref = (EntityReference) node;

      if (m_contentHandler instanceof LexicalHandler)
      {
        LexicalHandler lh = ((LexicalHandler) this.m_contentHandler);

        lh.endEntity(eref.getNodeName());
      }
    }
    break;
    default :
    }
  }
}  //TreeWalker
