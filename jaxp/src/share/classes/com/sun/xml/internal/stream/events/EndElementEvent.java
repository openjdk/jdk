/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

package com.sun.xml.internal.stream.events ;

import java.util.List;
import java.util.ArrayList;

import javax.xml.namespace.QName;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import java.io.Writer;
import java.util.Iterator;
import javax.xml.stream.events.XMLEvent;
import com.sun.xml.internal.stream.util.ReadOnlyIterator;

/** Implementation of EndElement event.
 *
 * @author Neeraj Bajaj Sun Microsystems,Inc.
 * @author K.Venugopal Sun Microsystems,Inc.
 */

public class EndElementEvent extends DummyEvent
implements EndElement {

    List fNamespaces = null;
    QName fQName ;

    public EndElementEvent() {
        init();
    }

    protected void init() {
        setEventType(XMLEvent.END_ELEMENT);
        fNamespaces = new ArrayList();
    }


    public EndElementEvent(String prefix,  String uri, String localpart) {
        this(new QName(uri,localpart,prefix));
    }

    public EndElementEvent(QName qname) {
        this.fQName = qname;
        init();
    }

    public QName getName() {
        return fQName;
    }

    public void setName(QName qname) {
        this.fQName = qname;
    }


    /** This method will write the XMLEvent as per the XML 1.0 specification as Unicode characters.
     * No indentation or whitespace should be outputted.
     *
     * Any user defined event type SHALL have this method
     * called when being written to on an output stream.
     * Built in Event types MUST implement this method,
     * but implementations MAY choose not call these methods
     * for optimizations reasons when writing out built in
     * Events to an output stream.
     * The output generated MUST be equivalent in terms of the
     * infoset expressed.
     *
     * @param writer The writer that will output the data
     * @throws XMLStreamException if there is a fatal error writing the event
     */
    public void writeAsEncodedUnicode(Writer writer) throws javax.xml.stream.XMLStreamException {
    }

    /** Returns an Iterator of namespaces that have gone out
     * of scope.  Returns an empty iterator if no namespaces have gone
     * out of scope.
     * @return an Iterator over Namespace interfaces, or an
     * empty iterator
     */
    public Iterator getNamespaces() {
        if(fNamespaces != null)
            fNamespaces.iterator();
        return new ReadOnlyIterator();
    }

    void addNamespace(Namespace attr){
        if(attr != null){
            fNamespaces.add(attr);
        }
    }

    public String toString() {
        String s = "</" + nameAsString();
        s = s + ">";
        return s;
    }

    public String nameAsString() {
        if("".equals(fQName.getNamespaceURI()))
            return fQName.getLocalPart();
        if(fQName.getPrefix() != null)
            return "['" + fQName.getNamespaceURI() + "']:" + fQName.getPrefix() + ":" + fQName.getLocalPart();
        else
            return "['" + fQName.getNamespaceURI() + "']:" + fQName.getLocalPart();
    }

}
