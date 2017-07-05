/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.stax.events ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;

import com.sun.xml.internal.fastinfoset.stax.events.EmptyIterator;
import com.sun.xml.internal.fastinfoset.stax.events.ReadIterator;

public class StartElementEvent extends EventBase implements StartElement {

    private Map _attributes;
    private List _namespaces;
    private NamespaceContext _context = null;
    private QName _qname;

    public void reset() {
        if (_attributes != null) _attributes.clear();
        if (_namespaces != null) _namespaces.clear();
        if (_context != null) _context = null;
    }

    public StartElementEvent() {
        init();
    }

    public StartElementEvent(String prefix, String uri, String localpart) {
        init();
        if (uri == null) uri = "";
        if (prefix == null) prefix ="";
        _qname = new QName(uri, localpart, prefix);
        setEventType(START_ELEMENT);
    }

    public StartElementEvent(QName qname) {
        init();
        _qname = qname;
    }

    public StartElementEvent(StartElement startelement) {
        this(startelement.getName());
        addAttributes(startelement.getAttributes());
        addNamespaces(startelement.getNamespaces());
    }

    protected void init() {
        setEventType(XMLStreamConstants.START_ELEMENT);
        _attributes = new HashMap();
        _namespaces = new ArrayList();
    }

    // ---------------------methods defined by StartElement-----------------//
    /**
    * Get the name of this event
    * @return the qualified name of this event
    */
    public QName getName() {
        return _qname;
    }
    /**
    * Returns an Iterator of non-namespace declared attributes
    * returns an empty iterator if there are no attributes.  The
    * iterator must contain only implementations of the javax.xml.stream.Attribute
    * interface.   Attributes are fundamentally unordered and may not be reported
    * in any order.
    *
    * @return a readonly Iterator over Attribute interfaces, or an
    * empty iterator
    */
    public Iterator getAttributes() {
        if(_attributes != null){
            Collection coll = _attributes.values();
            return new ReadIterator(coll.iterator());
        }
        return EmptyIterator.getInstance();
    }

  /**
   * Returns an Iterator of namespaces declared on this element.
   * This Iterator does not contain previously declared namespaces
   * unless they appear on the current START_ELEMENT.
   * Therefore this list may contain redeclared namespaces and duplicate namespace
   * declarations. Use the getNamespaceContext() method to get the
   * current context of namespace declarations.
   *
   * <p>The iterator must contain only implementations of the
   * javax.xml.stream.Namespace interface.
   *
   * <p>A Namespace is an Attribute.  One
   * can iterate over a list of namespaces as a list of attributes.
   * However this method returns only the list of namespaces
   * declared on this START_ELEMENT and does not
   * include the attributes declared on this START_ELEMENT.
   *
   * @return a readonly Iterator over Namespace interfaces, or an
   * empty iterator if there are no namespaces.
   *
   */
    public Iterator getNamespaces() {
        if(_namespaces != null){
            return new ReadIterator(_namespaces.iterator());
        }
        return EmptyIterator.getInstance();
    }

  /**
   * Returns the attribute referred to by this name
   * @param qname the qname of the desired name
   * @return the attribute corresponding to the name value or null
   */
    public Attribute getAttributeByName(QName qname) {
        if(qname == null)
            return null;
        return (Attribute)_attributes.get(qname);
    }

    /** Gets a read-only namespace context. If no context is
     * available this method will return an empty namespace context.
     * The NamespaceContext contains information about all namespaces
     * in scope for this StartElement.
     *
     * @return the current namespace context
     */
    public NamespaceContext getNamespaceContext() {
        return _context;
    }
// ---------------------end of methods defined by StartElement-----------------//

    public void setName(QName qname) {
        this._qname = qname;
    }


    public String getNamespace(){
        return _qname.getNamespaceURI();
    }

    /**
    * Gets the value that the prefix is bound to in the
    * context of this element.  Returns null if
    * the prefix is not bound in this context
    * @param prefix the prefix to lookup
    * @return the uri bound to the prefix or null
    */
    public String getNamespaceURI(String prefix) {
        //first check if the URI was supplied when creating this startElement event
        if( getNamespace() != null ) return getNamespace();
        //else check the namespace context
        if(_context != null)
            return _context.getNamespaceURI(prefix);
        return null;
    }

    public String toString() {
        String s = "<" + nameAsString();

        if(_attributes != null){
            Iterator it = this.getAttributes();
            Attribute attr = null;
            while(it.hasNext()){
                attr = (Attribute)it.next();
                s = s + " " + attr.toString();
            }
        }

        if(_namespaces != null){
            Iterator it = _namespaces.iterator();
            Namespace attr = null;
            while(it.hasNext()){
                attr = (Namespace)it.next();
                s = s + " " + attr.toString();
            }
        }
        s = s + ">";
        return s;
    }

    /** Return this event as String
     * @return String Event returned as string.
     */
    public String nameAsString() {
        if("".equals(_qname.getNamespaceURI()))
            return _qname.getLocalPart();
        if(_qname.getPrefix() != null)
            return "['" + _qname.getNamespaceURI() + "']:" + _qname.getPrefix() + ":" + _qname.getLocalPart();
        else
            return "['" + _qname.getNamespaceURI() + "']:" + _qname.getLocalPart();
    }


    public void setNamespaceContext(NamespaceContext context) {
        _context = context;
    }

    public void addAttribute(Attribute attr){
        _attributes.put(attr.getName(),attr);
    }

    public void addAttributes(Iterator attrs){
        if(attrs != null) {
            while(attrs.hasNext()){
                Attribute attr = (Attribute)attrs.next();
                _attributes.put(attr.getName(),attr);
            }
        }
    }

    public void addNamespace(Namespace namespace){
        if(namespace != null) {
            _namespaces.add(namespace);
        }
    }

    public void addNamespaces(Iterator namespaces){
        if(namespaces != null) {
            while(namespaces.hasNext()){
                Namespace namespace = (Namespace)namespaces.next();
                _namespaces.add(namespace);
            }
        }
    }

}
