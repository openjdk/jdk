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

import javax.xml.namespace.QName;


/**
 * Start tag.
 *
 * <p>
 * This object implements {@link NamespaceResolver} for attribute values.
 *
 * @author Kohsuke Kawaguchi
 */
class StartTag extends Content implements NamespaceResolver {
    /**
     * Tag name of the element.
     *
     * <p>
     * This field is also used as a flag to indicate
     * whether the start tag has been written.
     * This field is initially set to non-null, and
     * then reset to null when it's written.
     */
    private String uri;
    // but we keep the local name non-null so that
    // we can diagnose an error
    private final String localName;

    private Attribute firstAtt;
    private Attribute lastAtt;

    /**
     * If this {@link StartTag} has the parent {@link ContainerElement},
     * that value. Otherwise null.
     */
    private ContainerElement owner;

    /**
     * Explicitly given namespace declarations on this element.
     *
     * <p>
     * Additional namespace declarations might be necessary to
     * generate child {@link QName}s and attributes.
     */
    private NamespaceDecl firstNs;
    private NamespaceDecl lastNs;

    final Document document;

    public StartTag(ContainerElement owner, String uri, String localName) {
        this(owner.document,uri,localName);
        this.owner = owner;
    }

    public StartTag(Document document, String uri, String localName) {
        assert uri!=null;
        assert localName!=null;

        this.uri = uri;
        this.localName = localName;
        this.document = document;

        // TODO: think about a better way to maintain namespace decls.
        // this requires at least one NamespaceDecl per start tag,
        // which is rather expensive.
        addNamespaceDecl(uri,null,false);
    }

    public void addAttribute(String nsUri, String localName, Object arg) {
        checkWritable();

        // look for the existing ones
        Attribute a;
        for(a=firstAtt; a!=null; a=a.next) {
            if(a.hasName(nsUri,localName)) {
                break;
            }
        }

        // if not found, declare a new one
        if(a==null) {
            a = new Attribute(nsUri,localName);
            if(lastAtt==null) {
                assert firstAtt==null;
                firstAtt = lastAtt = a;
            } else {
                assert firstAtt!=null;
                lastAtt.next = a;
                lastAtt = a;
            }
            if(nsUri.length()>0)
                addNamespaceDecl(nsUri,null,true);
        }

        document.writeValue(arg,this,a.value);
    }

    /**
     * Declares a new namespace URI on this tag.
     *
     * @param uri
     *      namespace URI to be bound. Can be empty, but must not be null.
     * @param prefix
     *      If non-null and non-empty, this prefix is bound to the URI
     *      on this element. If empty, then the runtime will still try to
     *      use the URI as the default namespace, but it may fail to do so
     *      because of the constraints in the XML.
     *      <p>
     *      If this parameter is null, the runtime will allocate an unique prefix.
     * @param requirePrefix
     *      Used only when the prefix parameter is null. If true, this indicates
     *      that the non-empty prefix must be assigned to this URI. If false,
     *      then this URI might be used as the default namespace.
     *      <p>
     *      Normally you just need to set it to false.
     */
    public NamespaceDecl addNamespaceDecl(String uri, String prefix,boolean requirePrefix) {
        checkWritable();

        if(uri==null)
            throw new IllegalArgumentException();
        if(uri.length()==0) {
            if(requirePrefix)
                throw new IllegalArgumentException("The empty namespace cannot have a non-empty prefix");
            if(prefix!=null && prefix.length()>0)
                throw new IllegalArgumentException("The empty namespace can be only bound to the empty prefix");
            prefix = "";
        }

        // check for the duplicate
        for(NamespaceDecl n=firstNs; n!=null; n=n.next) {
            if(uri.equals(n.uri)) {
                if(prefix==null) {
                    // reuse this binding
                    n.requirePrefix |= requirePrefix;
                    return n;
                }
                if(n.prefix==null) {
                    // reuse this binding
                    n.prefix = prefix;
                    n.requirePrefix |= requirePrefix;
                    return n;
                }
                if(prefix.equals(n.prefix)) {
                    // reuse this binding
                    n.requirePrefix |= requirePrefix;
                    return n;
                }
            }
            if(prefix!=null && n.prefix!=null && n.prefix.equals(prefix))
                throw new IllegalArgumentException(
                    "Prefix '"+prefix+"' is already bound to '"+n.uri+'\'');
        }

        NamespaceDecl ns = new NamespaceDecl(document.assignNewId(),uri,prefix,requirePrefix);
        if(lastNs==null) {
            assert firstNs==null;
            firstNs = lastNs = ns;
        } else {
            assert firstNs!=null;
            lastNs.next = ns;
            lastNs = ns;
        }
        return ns;
    }

    /**
     * Throws an error if the start tag has already been committed.
     */
    private void checkWritable() {
        if(isWritten())
            throw new IllegalStateException(
                "The start tag of "+this.localName+" has already been written. " +
                "If you need out of order writing, see the TypedXmlWriter.block method");
    }

    /**
     * Returns true if this start tag has already been written.
     */
    boolean isWritten() {
        return uri==null;
    }

    /**
     * A {@link StartTag} can be only written after
     * we are sure that all the necessary namespace declarations are given.
     */
    boolean isReadyToCommit() {
        if(owner!=null && owner.isBlocked())
            return false;

        for( Content c=getNext(); c!=null; c=c.getNext() )
            if(c.concludesPendingStartTag())
                return true;

        return false;
    }

    public void written() {
        firstAtt = lastAtt = null;
        uri = null;
        if(owner!=null) {
            assert owner.startTag==this;
            owner.startTag = null;
        }
    }

    boolean concludesPendingStartTag() {
        return true;
    }

    void accept(ContentVisitor visitor) {
        visitor.onStartTag(uri,localName,firstAtt,firstNs);
    }

    public String getPrefix(String nsUri) {
        NamespaceDecl ns = addNamespaceDecl(nsUri,null,false);
        if(ns.prefix!=null)
            // if the prefix has already been declared, use it.
            return ns.prefix;
        return ns.dummyPrefix;
    }
}
