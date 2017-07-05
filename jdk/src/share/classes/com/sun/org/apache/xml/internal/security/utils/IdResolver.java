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
package com.sun.org.apache.xml.internal.security.utils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.WeakHashMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


/**
 * Purpose of this class is to enable the XML Parser to keep track of ID
 * attributes. This is done by 'registering' attributes of type ID at the
 * IdResolver. This is necessary if we create a document from scratch and we
 * sign some resources with a URI using a fragent identifier...
 * <BR />
 * The problem is that if you do not validate a document, you cannot use the
 * <CODE>getElementByID</CODE> functionality. So this modules uses some implicit
 * knowledge on selected Schemas and DTDs to pick the right Element for a given
 * ID: We know that all <CODE>@Id</CODE> attributes in an Element from the XML
 * Signature namespace are of type <CODE>ID</CODE>.
 *
 * @author $Author: mullan $
 * @see <A HREF="http://www.xml.com/lpt/a/2001/11/07/id.html">"Identity Crisis" on xml.com</A>
 */
public class IdResolver {

    /** {@link java.util.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(IdResolver.class.getName());

    private static WeakHashMap docMap = new WeakHashMap();

    /**
     * Constructor IdResolver
     *
     */
    private IdResolver() {
       // we don't allow instantiation
    }

    /**
     * Method registerElementById
     *
     * @param element the element to register
     * @param idValue the value of the ID attribute
     */
    public static void registerElementById(Element element, String idValue) {
        Document doc = element.getOwnerDocument();
        WeakHashMap elementMap;
        synchronized (docMap) {
            elementMap = (WeakHashMap) docMap.get(doc);
            if (elementMap == null) {
                elementMap = new WeakHashMap();
                docMap.put(doc, elementMap);
            }
        }
        elementMap.put(idValue, new WeakReference(element));
    }

    /**
     * Method registerElementById
     *
     * @param element the element to register
     * @param id the ID attribute
     */
    public static void registerElementById(Element element, Attr id) {
        IdResolver.registerElementById(element, id.getNodeValue());
    }

    /**
     * Method getElementById
     *
     * @param doc the document
     * @param id the value of the ID
     * @return the element obtained by the id, or null if it is not found.
     */
    public static Element getElementById(Document doc, String id) {

        Element result = IdResolver.getElementByIdType(doc, id);

        if (result != null) {
            log.log(java.util.logging.Level.FINE,
            "I could find an Element using the simple getElementByIdType method: "
            + result.getTagName());

            return result;
        }

        result = IdResolver.getElementByIdUsingDOM(doc, id);

        if (result != null) {
            log.log(java.util.logging.Level.FINE,
             "I could find an Element using the simple getElementByIdUsingDOM method: "
            + result.getTagName());

            return result;
        }
        // this must be done so that Xalan can catch ALL namespaces
        //XMLUtils.circumventBug2650(doc);
        result = IdResolver.getElementBySearching(doc, id);

        if (result != null) {
            IdResolver.registerElementById(result, id);

            return result;
        }

        return null;
    }


    /**
     * Method getElementByIdUsingDOM
     *
     * @param doc the document
     * @param id the value of the ID
     * @return the element obtained by the id, or null if it is not found.
     */
    private static Element getElementByIdUsingDOM(Document doc, String id) {
        if (log.isLoggable(java.util.logging.Level.FINE))
            log.log(java.util.logging.Level.FINE, "getElementByIdUsingDOM() Search for ID " + id);
        return doc.getElementById(id);
    }

    /**
     * Method getElementByIdType
     *
     * @param doc the document
     * @param id the value of the ID
     * @return the element obtained by the id, or null if it is not found.
     */
    private static Element getElementByIdType(Document doc, String id) {
        if (log.isLoggable(java.util.logging.Level.FINE))
            log.log(java.util.logging.Level.FINE, "getElementByIdType() Search for ID " + id);
        WeakHashMap elementMap;
        synchronized (docMap) {
            elementMap = (WeakHashMap) docMap.get(doc);
        }
        if (elementMap != null) {
            WeakReference weakReference = (WeakReference) elementMap.get(id);
            if (weakReference != null) {
                return (Element) weakReference.get();
            }
        }
        return null;
    }

    private static java.util.List names;
    private static int namesLength;
    static {
        String namespaces[]={
            Constants.SignatureSpecNS,
            EncryptionConstants.EncryptionSpecNS,
            "http://schemas.xmlsoap.org/soap/security/2000-12",
            "http://www.w3.org/2002/03/xkms#",
            "urn:oasis:names:tc:SAML:1.0:assertion",
            "urn:oasis:names:tc:SAML:1.0:protocol"
        };
        names = Arrays.asList(namespaces);
        namesLength = names.size();
    }


    private static Element getElementBySearching(Node root,String id) {
        Element []els=new Element[namesLength + 1];
        getEl(root,id,els);
        for (int i=0;i<els.length;i++) {
            if (els[i]!=null) {
                return els[i];
            }
        }
        return null;
    }

    private static int getEl(Node currentNode,String id,Element []els) {
        Node sibling=null;
        Node parentNode=null;
        do {
                switch (currentNode.getNodeType()) {
                case Node.DOCUMENT_FRAGMENT_NODE :
                case Node.DOCUMENT_NODE :
                        sibling= currentNode.getFirstChild();
                        break;


                case Node.ELEMENT_NODE :
                        Element currentElement = (Element) currentNode;
                        if (isElement(currentElement, id, els)==1)
                                return 1;
                        sibling= currentNode.getFirstChild();
                        if (sibling==null) {
                            if (parentNode != null) {
                                        sibling= currentNode.getNextSibling();
                                    }
                        } else {
                                parentNode=currentElement;
                        }
                        break;
        } while (sibling==null  && parentNode!=null) {
                        sibling=parentNode.getNextSibling();
                        parentNode=parentNode.getParentNode();
                        if (!(parentNode instanceof Element)) {
                                parentNode=null;
                        }
                }
                if (sibling==null)
                        return 1;
                currentNode=sibling;
                sibling=currentNode.getNextSibling();
        } while(true);

    }
    public static int isElement(Element el, String id,Element[] els) {
        if (!el.hasAttributes()) {
                return 0;
        }
        NamedNodeMap ns=el.getAttributes();
        int elementIndex=names.indexOf(el.getNamespaceURI());
            elementIndex=(elementIndex<0) ? namesLength : elementIndex;
        for (int length=ns.getLength(), i=0; i<length; i++) {
                Attr n=(Attr)ns.item(i);
                String s=n.getNamespaceURI();

                    int index=s==null ? elementIndex : names.indexOf(n.getNamespaceURI());
                    index=(index<0) ? namesLength : index;
                    String name=n.getLocalName();
                    if (name.length()>2)
                        continue;
                    String value=n.getNodeValue();
                    if (name.charAt(0)=='I') {
                        char ch=name.charAt(1);
                        if (ch=='d' && value.equals(id)) {
                                els[index]=el;
                                if (index==0) {
                                        return 1;
                                }
                        } else if (ch=='D' &&value.endsWith(id)) {
                                if (index!=3) {
                                    index=namesLength;
                                }
                                els[index]=el;
                        }
                    } else if ( "id".equals(name) && value.equals(id) ) {
                        if (index!=2) {
                                index=namesLength;
                        }
                        els[index]=el;
                    }
        }
        //For an element namespace search for importants
        if ((elementIndex==3)&&(
                    el.getAttribute("OriginalRequestID").equals(id) ||
                    el.getAttribute("RequestID").equals(id) ||
                    el.getAttribute("ResponseID").equals(id))) {
                    els[3]=el;
        } else if ((elementIndex==4)&&(
                    el.getAttribute("AssertionID").equals(id))) {
                    els[4]=el;
        } else if ((elementIndex==5)&&(
                    el.getAttribute("RequestID").equals(id) ||
                    el.getAttribute("ResponseID").equals(id))) {
                    els[5]=el;
                 }
        return 0;
    }
}
