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
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;

import com.sun.xml.internal.fastinfoset.stax.events.EmptyIterator;


public class EndElementEvent extends EventBase implements EndElement {

    List _namespaces = null;
    QName _qname ;

    public void reset() {
        if (_namespaces != null) _namespaces.clear();
    }

    public EndElementEvent() {
        setEventType(END_ELEMENT);
    }

    public EndElementEvent(String namespaceURI, String localpart, String prefix) {
        _qname = getQName(namespaceURI,localpart,prefix);
        setEventType(END_ELEMENT);
    }

    public EndElementEvent(QName qname) {
        _qname = qname;
        setEventType(END_ELEMENT);
    }

  /**
   * Get the name of this event
   * @return the qualified name of this event
   */
    public QName getName() {
        return _qname;
    }

    public void setName(QName qname) {
        _qname = qname;
    }


    /** Returns an Iterator of namespaces that have gone out
     * of scope.  Returns an empty iterator if no namespaces have gone
     * out of scope.
     * @return an Iterator over Namespace interfaces, or an
     * empty iterator
     */
    public Iterator getNamespaces() {
        if(_namespaces != null)
            return _namespaces.iterator();
        return EmptyIterator.getInstance();
    }

    public void addNamespace(Namespace namespace){
        if (_namespaces == null) {
            _namespaces = new ArrayList();
        }
        _namespaces.add(namespace);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("</").append(nameAsString());
        Iterator namespaces = getNamespaces();
        while(namespaces.hasNext()) {
            sb.append(" ").append(namespaces.next().toString());
        }
        sb.append(">");
        return sb.toString();
    }


    private String nameAsString() {
        if("".equals(_qname.getNamespaceURI()))
            return _qname.getLocalPart();
        if(_qname.getPrefix() != null)
            return "['" + _qname.getNamespaceURI() + "']:" + _qname.getPrefix() + ":" + _qname.getLocalPart();
        else
            return "['" + _qname.getNamespaceURI() + "']:" + _qname.getLocalPart();
    }
    private QName getQName(String uri, String localPart, String prefix){
        QName qn = null;
        if(prefix != null && uri != null)
            qn = new QName(uri, localPart, prefix);
        else if(prefix == null && uri != null)
            qn = new QName(uri, localPart);
        else if(prefix == null && uri == null)
            qn = new QName(localPart);
        return qn;
    }
}
