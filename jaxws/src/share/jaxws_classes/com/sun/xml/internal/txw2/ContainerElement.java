/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.xml.internal.txw2;

import com.sun.xml.internal.txw2.annotation.XmlAttribute;
import com.sun.xml.internal.txw2.annotation.XmlElement;
import com.sun.xml.internal.txw2.annotation.XmlNamespace;
import com.sun.xml.internal.txw2.annotation.XmlValue;
import com.sun.xml.internal.txw2.annotation.XmlCDATA;

import javax.xml.namespace.QName;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Dynamically implements {@link TypedXmlWriter} interfaces.
 *
 * @author Kohsuke Kawaguchi
 */
final class ContainerElement implements InvocationHandler, TypedXmlWriter {

    final Document document;

    /**
     * Initially, point to the start tag token, but
     * once we know we are done with the start tag, we will reset it to null
     * so that the token sequence can be GC-ed.
     */
    StartTag startTag;
    final EndTag endTag = new EndTag();

    /**
     * Namespace URI of this element.
     */
    private final String nsUri;

    /**
     * When this element can accept more child content, this value
     * is non-null and holds the last child {@link Content}.
     *
     * If this element is committed, this parameter is null.
     */
    private Content tail;

    /**
     * Uncommitted {@link ContainerElement}s form a doubly-linked list,
     * so that the parent can close them recursively.
     */
    private ContainerElement prevOpen;
    private ContainerElement nextOpen;
    private final ContainerElement parent;
    private ContainerElement lastOpenChild;

    /**
     * Set to true if the start eleent is blocked.
     */
    private boolean blocked;

    public ContainerElement(Document document,ContainerElement parent,String nsUri, String localName) {
        this.parent = parent;
        this.document = document;
        this.nsUri = nsUri;
        this.startTag = new StartTag(this,nsUri,localName);
        tail = startTag;

        if(isRoot())
            document.setFirstContent(startTag);
    }

    private boolean isRoot() {
        return parent==null;
    }

    private boolean isCommitted() {
        return tail==null;
    }

    public Document getDocument() {
        return document;
    }

    boolean isBlocked() {
        return blocked && !isCommitted();
    }

    public void block() {
        blocked = true;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if(method.getDeclaringClass()==TypedXmlWriter.class || method.getDeclaringClass()==Object.class) {
            // forward to myself
            try {
                return method.invoke(this,args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        XmlAttribute xa = method.getAnnotation(XmlAttribute.class);
        XmlValue xv = method.getAnnotation(XmlValue.class);
        XmlElement xe = method.getAnnotation(XmlElement.class);


        if(xa!=null) {
            if(xv!=null || xe!=null)
                throw new IllegalAnnotationException(method.toString());

            addAttribute(xa,method,args);
            return proxy; // allow method chaining
        }
        if(xv!=null) {
            if(xe!=null)
                throw new IllegalAnnotationException(method.toString());

            _pcdata(args);
            return proxy; // allow method chaining
        }

        return addElement(xe,method,args);
    }

    /**
     * Writes an attribute.
     */
    private void addAttribute(XmlAttribute xa, Method method, Object[] args) {
        assert xa!=null;

        checkStartTag();

        String localName = xa.value();
        if(xa.value().length()==0)
            localName = method.getName();

        _attribute(xa.ns(),localName,args);
    }

    private void checkStartTag() {
        if(startTag==null)
            throw new IllegalStateException("start tag has already been written");
    }

    /**
     * Writes a new element.
     */
    private Object addElement(XmlElement e, Method method, Object[] args) {
        Class<?> rt = method.getReturnType();

        // the last precedence: default name
        String nsUri = "##default";
        String localName = method.getName();

        if(e!=null) {
            // then the annotation on this method
            if(e.value().length()!=0)
                localName = e.value();
            nsUri = e.ns();
        }

        if(nsUri.equals("##default")) {
            // look for the annotation on the declaring class
            Class<?> c = method.getDeclaringClass();
            XmlElement ce = c.getAnnotation(XmlElement.class);
            if(ce!=null) {
                nsUri = ce.ns();
            }

            if(nsUri.equals("##default"))
                // then default to the XmlNamespace
                nsUri = getNamespace(c.getPackage());
        }



        if(rt==Void.TYPE) {
            // leaf element with just a value

            boolean isCDATA = method.getAnnotation(XmlCDATA.class)!=null;

            StartTag st = new StartTag(document,nsUri,localName);
            addChild(st);
            for( Object arg : args ) {
                Text text;
                if(isCDATA)     text = new Cdata(document,st,arg);
                else            text = new Pcdata(document,st,arg);
                addChild(text);
            }
            addChild(new EndTag());
            return null;
        }
        if(TypedXmlWriter.class.isAssignableFrom(rt)) {
            // sub writer
            return _element(nsUri,localName,(Class)rt);
        }

        throw new IllegalSignatureException("Illegal return type: "+rt);
    }

    /**
     * Decides the namespace URI of the given package.
     */
    private String getNamespace(Package pkg) {
        if(pkg==null)       return "";

        String nsUri;
        XmlNamespace ns = pkg.getAnnotation(XmlNamespace.class);
        if(ns!=null)
            nsUri = ns.value();
        else
            nsUri = "";
        return nsUri;
    }

    /**
     * Appends this child object to the tail.
     */
    private void addChild(Content child) {
        tail.setNext(document,child);
        tail = child;
    }

    public void commit() {
        commit(true);
    }

    public void commit(boolean includingAllPredecessors) {
        _commit(includingAllPredecessors);
        document.flush();
    }

    private void _commit(boolean includingAllPredecessors) {
        if(isCommitted())  return;

        addChild(endTag);
        if(isRoot())
            addChild(new EndDocument());
        tail = null;

        // _commit predecessors if so told
        if(includingAllPredecessors) {
            for( ContainerElement e=this; e!=null; e=e.parent ) {
                while(e.prevOpen!=null) {
                    e.prevOpen._commit(false);
                    // e.prevOpen should change as a result of committing it.
                }
            }
        }

        // _commit all children recursively
        while(lastOpenChild!=null)
            lastOpenChild._commit(false);

        // remove this node from the link
        if(parent!=null) {
            if(parent.lastOpenChild==this) {
                assert nextOpen==null : "this must be the last one";
                parent.lastOpenChild = prevOpen;
            } else {
                assert nextOpen.prevOpen==this;
                nextOpen.prevOpen = this.prevOpen;
            }
            if(prevOpen!=null) {
                assert prevOpen.nextOpen==this;
                prevOpen.nextOpen = this.nextOpen;
            }
        }

        this.nextOpen = null;
        this.prevOpen = null;
    }

    public void _attribute(String localName, Object value) {
        _attribute("",localName,value);
    }

    public void _attribute(String nsUri, String localName, Object value) {
        checkStartTag();
        startTag.addAttribute(nsUri,localName,value);
    }

    public void _attribute(QName attributeName, Object value) {
        _attribute(attributeName.getNamespaceURI(),attributeName.getLocalPart(),value);
    }

    public void _namespace(String uri) {
        _namespace(uri,false);
    }

    public void _namespace(String uri, String prefix) {
        if(prefix==null)
            throw new IllegalArgumentException();
        checkStartTag();
        startTag.addNamespaceDecl(uri,prefix,false);
    }

    public void _namespace(String uri, boolean requirePrefix) {
        checkStartTag();
        startTag.addNamespaceDecl(uri,null,requirePrefix);
    }

    public void _pcdata(Object value) {
        // we need to allow this method even when startTag has already been completed.
        // checkStartTag();
        addChild(new Pcdata(document,startTag,value));
    }

    public void _cdata(Object value) {
        addChild(new Cdata(document,startTag,value));
    }

    public void _comment(Object value) throws UnsupportedOperationException {
        addChild(new Comment(document,startTag,value));
    }

    public <T extends TypedXmlWriter> T _element(String localName, Class<T> contentModel) {
        return _element(nsUri,localName,contentModel);
    }

    public <T extends TypedXmlWriter> T _element(QName tagName, Class<T> contentModel) {
        return _element(tagName.getNamespaceURI(),tagName.getLocalPart(),contentModel);
    }

    public <T extends TypedXmlWriter> T _element(Class<T> contentModel) {
        return _element(TXW.getTagName(contentModel),contentModel);
    }

    public <T extends TypedXmlWriter> T _cast(Class<T> facadeType) {
        return facadeType.cast(Proxy.newProxyInstance(facadeType.getClassLoader(),new Class[]{facadeType},this));
    }

    public <T extends TypedXmlWriter> T _element(String nsUri, String localName, Class<T> contentModel) {
        ContainerElement child = new ContainerElement(document,this,nsUri,localName);
        addChild(child.startTag);
        tail = child.endTag;

        // update uncommitted link list
        if(lastOpenChild!=null) {
            assert lastOpenChild.parent==this;

            assert child.prevOpen==null;
            assert child.nextOpen==null;
            child.prevOpen = lastOpenChild;
            assert lastOpenChild.nextOpen==null;
            lastOpenChild.nextOpen = child;
        }

        this.lastOpenChild = child;

        return child._cast(contentModel);
    }
}
