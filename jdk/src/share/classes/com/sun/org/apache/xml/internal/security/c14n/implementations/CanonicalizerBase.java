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
package com.sun.org.apache.xml.internal.security.c14n.implementations;



import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizerSpi;
import com.sun.org.apache.xml.internal.security.c14n.helper.AttrCompare;
import com.sun.org.apache.xml.internal.security.signature.NodeFilter;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.UnsyncByteArrayOutputStream;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.SAXException;


/**
 * Abstract base class for canonicalization algorithms.
 *
 * @author Christian Geuer-Pollmann <geuerp@apache.org>
 * @version $Revision: 1.5 $
 */
public abstract class CanonicalizerBase extends CanonicalizerSpi {
   //Constants to be outputed, In char array form, so
   //less garbage is generate when outputed.
   private static final byte[] _END_PI = {'?','>'};
   private static final byte[] _BEGIN_PI = {'<','?'};
   private static final byte[] _END_COMM = {'-','-','>'};
   private static final byte[] _BEGIN_COMM = {'<','!','-','-'};
   private static final byte[] __XA_ = {'&','#','x','A',';'};
   private static final byte[] __X9_ = {'&','#','x','9',';'};
   private static final byte[] _QUOT_ = {'&','q','u','o','t',';'};
   private static final byte[] __XD_ = {'&','#','x','D',';'};
   private static final byte[] _GT_ = {'&','g','t',';'};
   private static final byte[] _LT_ = {'&','l','t',';'};
   private static final byte[] _END_TAG = {'<','/'};
   private static final byte[] _AMP_ = {'&','a','m','p',';'};
   final static AttrCompare COMPARE=new AttrCompare();
   final static String XML="xml";
   final static String XMLNS="xmlns";
   final static byte[] equalsStr= {'=','\"'};
   static final int NODE_BEFORE_DOCUMENT_ELEMENT = -1;
   static final int NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT = 0;
   static final int NODE_AFTER_DOCUMENT_ELEMENT = 1;
   //The null xmlns definiton.
   protected static final Attr nullNode;
   static {
    try {
        nullNode=DocumentBuilderFactory.newInstance().
            newDocumentBuilder().newDocument().createAttributeNS(Constants.NamespaceSpecNS,XMLNS);
        nullNode.setValue("");
    } catch (Exception e) {
        throw new RuntimeException("Unable to create nullNode"/*,*/+e);
    }
   }

   List nodeFilter;

   boolean _includeComments;
   Set _xpathNodeSet = null;
   /**
    * The node to be skiped/excluded from the DOM tree
    * in subtree canonicalizations.
    */
   Node _excludeNode =null;
   OutputStream _writer = new UnsyncByteArrayOutputStream();//null;

   /**
    * Constructor CanonicalizerBase
    *
    * @param includeComments
    */
   public CanonicalizerBase(boolean includeComments) {
      this._includeComments = includeComments;
   }

   /**
    * Method engineCanonicalizeSubTree
    * @inheritDoc
    * @param rootNode
    * @throws CanonicalizationException
    */
   public byte[] engineCanonicalizeSubTree(Node rootNode)
           throws CanonicalizationException {
                return engineCanonicalizeSubTree(rootNode,(Node)null);
   }
   /**
    * Method engineCanonicalizeXPathNodeSet
    * @inheritDoc
    * @param xpathNodeSet
    * @throws CanonicalizationException
    */
   public byte[] engineCanonicalizeXPathNodeSet(Set xpathNodeSet)
           throws CanonicalizationException {
           this._xpathNodeSet = xpathNodeSet;
           return engineCanonicalizeXPathNodeSetInternal(XMLUtils.getOwnerDocument(this._xpathNodeSet));
   }

   /**
    * Canonicalizes a Subtree node.
    * @param input the root of the subtree to canicalize
    * @return The canonicalize stream.
    * @throws CanonicalizationException
    */
    public byte[] engineCanonicalize(XMLSignatureInput input)
    throws CanonicalizationException {
        try {
                if (input.isExcludeComments())
                        _includeComments = false;
                        byte[] bytes;
                        if (input.isOctetStream()) {
                                return engineCanonicalize(input.getBytes());
                        }
                        if (input.isElement()) {
                                bytes = engineCanonicalizeSubTree(input.getSubNode(), input
                                                .getExcludeNode());
                                return bytes;
                        } else if (input.isNodeSet()) {
                                nodeFilter=input.getNodeFilters();

                circumventBugIfNeeded(input);

                                if (input.getSubNode() != null) {
                                    bytes = engineCanonicalizeXPathNodeSetInternal(input.getSubNode());
                                } else {
                                    bytes = engineCanonicalizeXPathNodeSet(input.getNodeSet());
                                }
                                return bytes;

                        }
                        return null;
                } catch (CanonicalizationException ex) {
                        throw new CanonicalizationException("empty", ex);
                } catch (ParserConfigurationException ex) {
                        throw new CanonicalizationException("empty", ex);
                } catch (IOException ex) {
                        throw new CanonicalizationException("empty", ex);
                } catch (SAXException ex) {
                        throw new CanonicalizationException("empty", ex);
                }
   }
   /**
    * @param _writer The _writer to set.
    */
    public void setWriter(OutputStream _writer) {
        this._writer = _writer;
    }

    /**
         * Canonicalizes a Subtree node.
         *
         * @param rootNode
         *            the root of the subtree to canicalize
         * @param excludeNode
         *            a node to be excluded from the canicalize operation
         * @return The canonicalize stream.
         * @throws CanonicalizationException
         */
    byte[] engineCanonicalizeSubTree(Node rootNode,Node excludeNode)
    throws CanonicalizationException {
        this._excludeNode = excludeNode;
        try {
         NameSpaceSymbTable ns=new NameSpaceSymbTable();
         int nodeLevel=NODE_BEFORE_DOCUMENT_ELEMENT;
         if (rootNode instanceof Element) {
                //Fills the nssymbtable with the definitions of the parent of the root subnode
                getParentNameSpaces((Element)rootNode,ns);
                nodeLevel=NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
         }
         this.canonicalizeSubTree(rootNode,ns,rootNode,nodeLevel);
         this._writer.close();
         if (this._writer instanceof ByteArrayOutputStream) {
            byte []result=((ByteArrayOutputStream)this._writer).toByteArray();
            if (reset) {
                ((ByteArrayOutputStream)this._writer).reset();
            }
                return result;
         }  else if (this._writer instanceof UnsyncByteArrayOutputStream) {
                 byte []result=((UnsyncByteArrayOutputStream)this._writer).toByteArray();
             if (reset) {
                 ((UnsyncByteArrayOutputStream)this._writer).reset();
             }
                 return result;
         }
         return null;

      } catch (UnsupportedEncodingException ex) {
         throw new CanonicalizationException("empty", ex);
      } catch (IOException ex) {
         throw new CanonicalizationException("empty", ex);
      }
   }


   /**
    * Method canonicalizeSubTree, this function is a recursive one.
    *
    * @param currentNode
    * @param ns
    * @param endnode
    * @throws CanonicalizationException
    * @throws IOException
    */
    final void canonicalizeSubTree(Node currentNode, NameSpaceSymbTable ns,Node endnode,
                int documentLevel)
    throws CanonicalizationException, IOException {
        if (isVisibleInt(currentNode)==-1)
                return;
        Node sibling=null;
        Node parentNode=null;
        final OutputStream writer=this._writer;
        final Node excludeNode=this._excludeNode;
        final boolean includeComments=this._includeComments;
        Map cache=new HashMap();
        do {
                switch (currentNode.getNodeType()) {

                case Node.DOCUMENT_TYPE_NODE :
                default :
                        break;

                case Node.ENTITY_NODE :
                case Node.NOTATION_NODE :
                case Node.ATTRIBUTE_NODE :
                        // illegal node type during traversal
                        throw new CanonicalizationException("empty");

            case Node.DOCUMENT_FRAGMENT_NODE :
                case Node.DOCUMENT_NODE :
                        ns.outputNodePush();
                        sibling= currentNode.getFirstChild();
                        break;

                case Node.COMMENT_NODE :
                        if (includeComments) {
                                outputCommentToWriter((Comment) currentNode, writer, documentLevel);
                        }
                        break;

                case Node.PROCESSING_INSTRUCTION_NODE :
                        outputPItoWriter((ProcessingInstruction) currentNode, writer, documentLevel);
                        break;

                case Node.TEXT_NODE :
                case Node.CDATA_SECTION_NODE :
                        outputTextToWriter(currentNode.getNodeValue(), writer);
                        break;

                case Node.ELEMENT_NODE :
                        documentLevel=NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
                        if (currentNode==excludeNode) {
                                break;
                        }
                        Element currentElement = (Element) currentNode;
                        //Add a level to the nssymbtable. So latter can be pop-back.
                        ns.outputNodePush();
                        writer.write('<');
                        String name=currentElement.getTagName();
                        UtfHelpper.writeByte(name,writer,cache);

                        Iterator attrs = this.handleAttributesSubtree(currentElement,ns);
                        if (attrs!=null) {
                                //we output all Attrs which are available
                                while (attrs.hasNext()) {
                                        Attr attr = (Attr) attrs.next();
                                        outputAttrToWriter(attr.getNodeName(),attr.getNodeValue(), writer,cache);
                                }
                        }
                        writer.write('>');
                        sibling= currentNode.getFirstChild();
                        if (sibling==null) {
                                writer.write(_END_TAG);
                                UtfHelpper.writeStringToUtf8(name,writer);
                                writer.write('>');
                                //We fineshed with this level, pop to the previous definitions.
                                ns.outputNodePop();
                                    if (parentNode != null) {
                                        sibling= currentNode.getNextSibling();
                                    }
                        } else {
                                parentNode=currentElement;
                        }
                        break;
                }
                while (sibling==null  && parentNode!=null) {
                        writer.write(_END_TAG);
                        UtfHelpper.writeByte(((Element)parentNode).getTagName(),writer,cache);
                        writer.write('>');
                        //We fineshed with this level, pop to the previous definitions.
                        ns.outputNodePop();
                        if (parentNode==endnode)
                                return;
                        sibling=parentNode.getNextSibling();
                        parentNode=parentNode.getParentNode();
                        if (!(parentNode instanceof Element)) {
                                documentLevel=NODE_AFTER_DOCUMENT_ELEMENT;
                                parentNode=null;
                        }
                }
                if (sibling==null)
                        return;
                currentNode=sibling;
                sibling=currentNode.getNextSibling();
        } while(true);
    }



   private  byte[] engineCanonicalizeXPathNodeSetInternal(Node doc)
           throws CanonicalizationException {

      try {
         this.canonicalizeXPathNodeSet(doc,doc);
         this._writer.close();
         if (this._writer instanceof ByteArrayOutputStream) {
            byte [] sol=((ByteArrayOutputStream)this._writer).toByteArray();
            if (reset) {
                ((ByteArrayOutputStream)this._writer).reset();
            }
                return sol;
         }  else if (this._writer instanceof UnsyncByteArrayOutputStream) {
                 byte []result=((UnsyncByteArrayOutputStream)this._writer).toByteArray();
             if (reset) {
                 ((UnsyncByteArrayOutputStream)this._writer).reset();
             }
                 return result;
         }
         return null;
      } catch (UnsupportedEncodingException ex) {
         throw new CanonicalizationException("empty", ex);
      } catch (IOException ex) {
         throw new CanonicalizationException("empty", ex);
      }
   }

   /**
    * Canoicalizes all the nodes included in the currentNode and contained in the
        * _xpathNodeSet field.
    *
    * @param currentNode
        * @param endnode
    * @throws CanonicalizationException
    * @throws IOException
    */
   final void canonicalizeXPathNodeSet(Node currentNode,Node endnode )
           throws CanonicalizationException, IOException {
        if (isVisibleInt(currentNode)==-1)
                return;
        boolean currentNodeIsVisible = false;
        NameSpaceSymbTable ns=new  NameSpaceSymbTable();
        if (currentNode instanceof Element)
                getParentNameSpaces((Element)currentNode,ns);
        Node sibling=null;
        Node parentNode=null;
        OutputStream writer=this._writer;
        int documentLevel=NODE_BEFORE_DOCUMENT_ELEMENT;
        Map cache=new HashMap();
        do {
                switch (currentNode.getNodeType()) {

                case Node.DOCUMENT_TYPE_NODE :
                default :
                        break;

                case Node.ENTITY_NODE :
                case Node.NOTATION_NODE :
                case Node.ATTRIBUTE_NODE :
                        // illegal node type during traversal
                        throw new CanonicalizationException("empty");

        case Node.DOCUMENT_FRAGMENT_NODE :
                case Node.DOCUMENT_NODE :
                        ns.outputNodePush();
                        //currentNode = currentNode.getFirstChild();
                        sibling= currentNode.getFirstChild();
                        break;

                case Node.COMMENT_NODE :
                        if (this._includeComments && (isVisibleDO(currentNode,ns.getLevel())==1)) {
                                outputCommentToWriter((Comment) currentNode, writer, documentLevel);
                        }
                        break;

                case Node.PROCESSING_INSTRUCTION_NODE :
                        if (isVisible(currentNode))
                                outputPItoWriter((ProcessingInstruction) currentNode, writer, documentLevel);
                        break;

                case Node.TEXT_NODE :
                case Node.CDATA_SECTION_NODE :
                        if (isVisible(currentNode)) {
                        outputTextToWriter(currentNode.getNodeValue(), writer);
                        for (Node nextSibling = currentNode.getNextSibling();
                    (nextSibling != null)
                    && ((nextSibling.getNodeType() == Node.TEXT_NODE)
                        || (nextSibling.getNodeType()
                            == Node.CDATA_SECTION_NODE));
                    nextSibling = nextSibling.getNextSibling()) {
               outputTextToWriter(nextSibling.getNodeValue(), writer);
               currentNode=nextSibling;
               sibling=currentNode.getNextSibling();
            }

                        }
                        break;

                case Node.ELEMENT_NODE :
                        documentLevel=NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
                        Element currentElement = (Element) currentNode;
                        //Add a level to the nssymbtable. So latter can be pop-back.
                        String name=null;
                        int i=isVisibleDO(currentNode,ns.getLevel());
                        if (i==-1) {
                                sibling= currentNode.getNextSibling();
                                break;
                        }
                        currentNodeIsVisible=(i==1);
                        if (currentNodeIsVisible) {
                                ns.outputNodePush();
                                writer.write('<');
                                name=currentElement.getTagName();
                                UtfHelpper.writeByte(name,writer,cache);
                        } else {
                                ns.push();
                        }

                        Iterator attrs = handleAttributes(currentElement,ns);
                        if (attrs!=null) {
                                //we output all Attrs which are available
                                while (attrs.hasNext()) {
                                        Attr attr = (Attr) attrs.next();
                                        outputAttrToWriter(attr.getNodeName(),attr.getNodeValue(), writer,cache);
                                }
                        }
                        if (currentNodeIsVisible) {
                                writer.write('>');
                        }
                        sibling= currentNode.getFirstChild();

                        if (sibling==null) {
                                if (currentNodeIsVisible) {
                                        writer.write(_END_TAG);
                                        UtfHelpper.writeByte(name,writer,cache);
                                        writer.write('>');
                                        //We fineshed with this level, pop to the previous definitions.
                                        ns.outputNodePop();
                                } else {
                                        ns.pop();
                                }
                                if (parentNode != null) {
                                        sibling= currentNode.getNextSibling();
                                }
                        } else {
                                parentNode=currentElement;
                        }
                        break;
                }
                while (sibling==null  && parentNode!=null) {
                        if (isVisible(parentNode)) {
                                writer.write(_END_TAG);
                                UtfHelpper.writeByte(((Element)parentNode).getTagName(),writer,cache);
                                writer.write('>');
                                //We fineshed with this level, pop to the previous definitions.
                                ns.outputNodePop();
                        } else {
                                ns.pop();
                        }
                        if (parentNode==endnode)
                                return;
                        sibling=parentNode.getNextSibling();
                        parentNode=parentNode.getParentNode();
                        if (!(parentNode instanceof Element)) {
                                parentNode=null;
                                documentLevel=NODE_AFTER_DOCUMENT_ELEMENT;
                        }
                }
                if (sibling==null)
                        return;
                currentNode=sibling;
                sibling=currentNode.getNextSibling();
        } while(true);
   }
   int isVisibleDO(Node currentNode,int level) {
           if (nodeFilter!=null) {
                        Iterator it=nodeFilter.iterator();
                        while (it.hasNext()) {
                                int i=((NodeFilter)it.next()).isNodeIncludeDO(currentNode,level);
                                if (i!=1)
                                        return i;
                        }
                   }
           if ((this._xpathNodeSet!=null) && !this._xpathNodeSet.contains(currentNode))
                        return 0;
           return 1;
   }
   int isVisibleInt(Node currentNode) {
           if (nodeFilter!=null) {
                Iterator it=nodeFilter.iterator();
                while (it.hasNext()) {
                        int i=((NodeFilter)it.next()).isNodeInclude(currentNode);
                        if (i!=1)
                                return i;
                }
           }
                if ((this._xpathNodeSet!=null) && !this._xpathNodeSet.contains(currentNode))
                        return 0;
                return 1;
        }

   boolean isVisible(Node currentNode) {
           if (nodeFilter!=null) {
                Iterator it=nodeFilter.iterator();
                while (it.hasNext()) {
                        if (((NodeFilter)it.next()).isNodeInclude(currentNode)!=1)
                                return false;
                }
           }
                if ((this._xpathNodeSet!=null) && !this._xpathNodeSet.contains(currentNode))
                        return false;
                return true;
        }

        void handleParent(Element e,NameSpaceSymbTable ns) {
           if (!e.hasAttributes()) {
                                return;
                }
                NamedNodeMap attrs = e.getAttributes();
                int attrsLength = attrs.getLength();
                for (int i = 0; i < attrsLength; i++) {
                        Attr N = (Attr) attrs.item(i);
                        if (Constants.NamespaceSpecNS!=N.getNamespaceURI()) {
                                //Not a namespace definition, ignore.
                                continue;
                        }

                        String NName=N.getLocalName();
                        String NValue=N.getNodeValue();
                        if (XML.equals(NName)
               && Constants.XML_LANG_SPACE_SpecNS.equals(NValue)) {
                                        continue;
                        }
                        ns.addMapping(NName,NValue,N);
                }
   }

        /**
         * Adds to ns the definitons from the parent elements of el
         * @param el
         * @param ns
         */
        final void getParentNameSpaces(Element el,NameSpaceSymbTable ns)  {
                List parents=new ArrayList(10);
                Node n1=el.getParentNode();
                if (!(n1 instanceof Element)) {
                        return;
                }
                //Obtain all the parents of the elemnt
                Element parent=(Element) n1;
                while (parent!=null) {
                        parents.add(parent);
                        Node n=parent.getParentNode();
                        if (!(n instanceof Element )) {
                                break;
                        }
                        parent=(Element)n;
                }
                //Visit them in reverse order.
                ListIterator it=parents.listIterator(parents.size());
                while (it.hasPrevious()) {
                        Element ele=(Element)it.previous();
                        handleParent(ele, ns);
        }
        Attr nsprefix;
        if (((nsprefix=ns.getMappingWithoutRendered("xmlns"))!=null)
                && "".equals(nsprefix.getValue())) {
             ns.addMappingAndRender("xmlns","",nullNode);
        }
        }
   /**
    * Obtain the attributes to output for this node in XPathNodeSet c14n.
    *
    * @param E
        * @param ns
        * @return the attributes nodes to output.
    * @throws CanonicalizationException
    */
   abstract Iterator handleAttributes(Element E, NameSpaceSymbTable ns )
   throws CanonicalizationException;

   /**
    * Obtain the attributes to output for this node in a Subtree c14n.
    *
    * @param E
        * @param ns
        * @return the attributes nodes to output.
    * @throws CanonicalizationException
    */
   abstract Iterator handleAttributesSubtree(Element E, NameSpaceSymbTable ns)
   throws CanonicalizationException;

   abstract void circumventBugIfNeeded(XMLSignatureInput input) throws CanonicalizationException, ParserConfigurationException, IOException, SAXException;

   /**
            * Outputs an Attribute to the internal Writer.
            *
            * The string value of the node is modified by replacing
            * <UL>
            * <LI>all ampersands (&) with <CODE>&amp;amp;</CODE></LI>
            * <LI>all open angle brackets (<) with <CODE>&amp;lt;</CODE></LI>
            * <LI>all quotation mark characters with <CODE>&amp;quot;</CODE></LI>
            * <LI>and the whitespace characters <CODE>#x9</CODE>, #xA, and #xD, with character
            * references. The character references are written in uppercase
            * hexadecimal with no leading zeroes (for example, <CODE>#xD</CODE> is represented
            * by the character reference <CODE>&amp;#xD;</CODE>)</LI>
            * </UL>
            *
            * @param name
            * @param value
            * @param writer
            * @throws IOException
            */
           static final void outputAttrToWriter(final String name, final String value, final OutputStream writer,
                                final Map cache) throws IOException {
              writer.write(' ');
              UtfHelpper.writeByte(name,writer,cache);
              writer.write(equalsStr);
              byte  []toWrite;
              final int length = value.length();
              int i=0;
              while (i < length) {
                 char c = value.charAt(i++);

                 switch (c) {

                 case '&' :
                        toWrite=_AMP_;
                    break;

                 case '<' :
                        toWrite=_LT_;
                    break;

                 case '"' :
                        toWrite=_QUOT_;
                    break;

                 case 0x09 :    // '\t'
                        toWrite=__X9_;
                    break;

                 case 0x0A :    // '\n'
                        toWrite=__XA_;
                    break;

                 case 0x0D :    // '\r'
                        toWrite=__XD_;
                    break;

                 default :
                        if (c < 0x80 ) {
                                writer.write(c);
                        } else {
                                UtfHelpper.writeCharToUtf8(c,writer);
                        };
                    continue;
                 }
                 writer.write(toWrite);
              }

              writer.write('\"');
           }

        /**
            * Outputs a PI to the internal Writer.
            *
            * @param currentPI
            * @param writer where to write the things
            * @throws IOException
            */
           static final void outputPItoWriter(ProcessingInstruction currentPI, OutputStream writer,int position) throws IOException {

              if (position == NODE_AFTER_DOCUMENT_ELEMENT) {
                writer.write('\n');
              }
              writer.write(_BEGIN_PI);

              final String target = currentPI.getTarget();
              int length = target.length();

              for (int i = 0; i < length; i++) {
                 char c=target.charAt(i);
                 if (c==0x0D) {
                    writer.write(__XD_);
                 } else {
                         if (c < 0x80)  {
                                writer.write(c);
                        } else {
                                UtfHelpper.writeCharToUtf8(c,writer);
                        };
                 }
              }

              final String data = currentPI.getData();

              length = data.length();

              if (length > 0) {
                 writer.write(' ');

                 for (int i = 0; i < length; i++) {
                        char c=data.charAt(i);
                    if (c==0x0D) {
                       writer.write(__XD_);
                    } else {
                        UtfHelpper.writeCharToUtf8(c,writer);
                    }
                 }
              }

              writer.write(_END_PI);
              if (position == NODE_BEFORE_DOCUMENT_ELEMENT) {
                writer.write('\n');
             }
           }

           /**
            * Method outputCommentToWriter
            *
            * @param currentComment
            * @param writer writer where to write the things
            * @throws IOException
            */
           static final void outputCommentToWriter(Comment currentComment, OutputStream writer,int position) throws IOException {
                  if (position == NODE_AFTER_DOCUMENT_ELEMENT) {
                        writer.write('\n');
                  }
              writer.write(_BEGIN_COMM);

              final String data = currentComment.getData();
              final int length = data.length();

              for (int i = 0; i < length; i++) {
                 char c=data.charAt(i);
                 if (c==0x0D) {
                    writer.write(__XD_);
                 } else {
                         if (c < 0x80)  {
                                writer.write(c);
                        } else {
                                UtfHelpper.writeCharToUtf8(c,writer);
                        };
                 }
              }

              writer.write(_END_COMM);
              if (position == NODE_BEFORE_DOCUMENT_ELEMENT) {
                        writer.write('\n');
                 }
           }

           /**
            * Outputs a Text of CDATA section to the internal Writer.
            *
            * @param text
            * @param writer writer where to write the things
            * @throws IOException
            */
           static final void outputTextToWriter(final String text, final OutputStream writer) throws IOException {
              final int length = text.length();
              byte []toWrite;
              for (int i = 0; i < length; i++) {
                 char c = text.charAt(i);

                 switch (c) {

                 case '&' :
                        toWrite=_AMP_;
                    break;

                 case '<' :
                        toWrite=_LT_;
                    break;

                 case '>' :
                        toWrite=_GT_;
                    break;

                 case 0xD :
                        toWrite=__XD_;
                    break;

                 default :
                         if (c < 0x80) {
                                 writer.write(c);
                         } else {
                                 UtfHelpper.writeCharToUtf8(c,writer);
                         };
                    continue;
                 }
                 writer.write(toWrite);
              }
           }

}
