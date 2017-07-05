/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.txw2.output.XmlSerializer;

import java.util.Map;
import java.util.HashMap;

/**
 * Coordinates the entire writing process.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class Document {

    private final XmlSerializer out;

    /**
     * Set to true once we invoke {@link XmlSerializer#startDocument()}.
     *
     * <p>
     * This is so that we can defer the writing as much as possible.
     */
    private boolean started=false;

    /**
     * Currently active writer.
     *
     * <p>
     * This points to the last written token.
     */
    private Content current = null;

    private final Map<Class,DatatypeWriter> datatypeWriters = new HashMap<Class,DatatypeWriter>();

    /**
     * Used to generate unique namespace prefix.
     */
    private int iota = 1;

    /**
     * Used to keep track of in-scope namespace bindings declared in ancestors.
     */
    private final NamespaceSupport inscopeNamespace = new NamespaceSupport();

    /**
     * Remembers the namespace declarations of the last unclosed start tag,
     * so that we can fix up dummy prefixes in {@link Pcdata}.
     */
    private NamespaceDecl activeNamespaces;


    Document(XmlSerializer out) {
        this.out = out;
        for( DatatypeWriter dw : DatatypeWriter.BUILTIN )
            datatypeWriters.put(dw.getType(),dw);
    }

    void flush() {
        out.flush();
    }

    void setFirstContent(Content c) {
        assert current==null;
        current = new StartDocument();
        current.setNext(this,c);
    }

    /**
     * Defines additional user object -> string conversion logic.
     *
     * <p>
     * Applications can add their own {@link DatatypeWriter} so that
     * application-specific objects can be turned into {@link String}
     * for output.
     *
     * @param dw
     *      The {@link DatatypeWriter} to be added. Must not be null.
     */
    public void addDatatypeWriter( DatatypeWriter<?> dw ) {
        datatypeWriters.put(dw.getType(),dw);
    }

    /**
     * Performs the output as much as possible
     */
    void run() {
        while(true) {
            Content next = current.getNext();
            if(next==null || !next.isReadyToCommit())
                return;
            next.accept(visitor);
            next.written();
            current = next;
        }
    }

    /**
     * Appends the given object to the end of the given buffer.
     *
     * @param nsResolver
     *      use
     */
    void writeValue( Object obj, NamespaceResolver nsResolver, StringBuilder buf ) {
        if(obj==null)
            throw new IllegalArgumentException("argument contains null");

        if(obj instanceof Object[]) {
            for( Object o : (Object[])obj )
                writeValue(o,nsResolver,buf);
            return;
        }
        if(obj instanceof Iterable) {
            for( Object o : (Iterable<?>)obj )
                writeValue(o,nsResolver,buf);
            return;
        }

        if(buf.length()>0)
            buf.append(' ');

        Class c = obj.getClass();
        while(c!=null) {
            DatatypeWriter dw = datatypeWriters.get(c);
            if(dw!=null) {
                dw.print(obj,nsResolver,buf);
                return;
            }
            c = c.getSuperclass();
        }

        // if nothing applies, just use toString
        buf.append(obj);
    }

    // I wanted to hide those write method from users
    private final ContentVisitor visitor = new ContentVisitor() {
        public void onStartDocument() {
            // the startDocument token is used as the sentry, so this method shall never
            // be called.
            // out.startDocument() is invoked when we write the start tag of the root element.
            throw new IllegalStateException();
        }

        public void onEndDocument() {
            out.endDocument();
        }

        public void onEndTag() {
            out.endTag();
            inscopeNamespace.popContext();
            activeNamespaces = null;
        }

        public void onPcdata(StringBuilder buffer) {
            if(activeNamespaces!=null)
                buffer = fixPrefix(buffer);
            out.text(buffer);
        }

        public void onCdata(StringBuilder buffer) {
            if(activeNamespaces!=null)
                buffer = fixPrefix(buffer);
            out.cdata(buffer);
        }

        public void onComment(StringBuilder buffer) {
            if(activeNamespaces!=null)
                buffer = fixPrefix(buffer);
            out.comment(buffer);
        }

        public void onStartTag(String nsUri, String localName, Attribute attributes, NamespaceDecl namespaces) {
            assert nsUri!=null;
            assert localName!=null;

            activeNamespaces = namespaces;

            if(!started) {
                started = true;
                out.startDocument();
            }

            inscopeNamespace.pushContext();

            // declare the explicitly bound namespaces
            for( NamespaceDecl ns=namespaces; ns!=null; ns=ns.next ) {
                ns.declared = false;    // reset this flag

                if(ns.prefix!=null) {
                    String uri = inscopeNamespace.getURI(ns.prefix);
                    if(uri!=null && uri.equals(ns.uri))
                        ; // already declared
                    else {
                        // declare this new binding
                        inscopeNamespace.declarePrefix(ns.prefix,ns.uri);
                        ns.declared = true;
                    }
                }
            }

            // then use in-scope namespace to assign prefixes to others
            for( NamespaceDecl ns=namespaces; ns!=null; ns=ns.next ) {
                if(ns.prefix==null) {
                    if(inscopeNamespace.getURI("").equals(ns.uri))
                        ns.prefix="";
                    else {
                        String p = inscopeNamespace.getPrefix(ns.uri);
                        if(p==null) {
                            // assign a new one
                            while(inscopeNamespace.getURI(p=newPrefix())!=null)
                                ;
                            ns.declared = true;
                            inscopeNamespace.declarePrefix(p,ns.uri);
                        }
                        ns.prefix = p;
                    }
                }
            }

            // the first namespace decl must be the one for the element
            assert namespaces.uri.equals(nsUri);
            assert namespaces.prefix!=null : "a prefix must have been all allocated";
            out.beginStartTag(nsUri,localName,namespaces.prefix);

            // declare namespaces
            for( NamespaceDecl ns=namespaces; ns!=null; ns=ns.next ) {
                if(ns.declared)
                    out.writeXmlns( ns.prefix, ns.uri );
            }

            // writeBody attributes
            for( Attribute a=attributes; a!=null; a=a.next) {
                String prefix;
                if(a.nsUri.length()==0) prefix="";
                else                    prefix=inscopeNamespace.getPrefix(a.nsUri);
                out.writeAttribute( a.nsUri, a.localName, prefix, fixPrefix(a.value) );
            }

            out.endStartTag(nsUri,localName,namespaces.prefix);
        }
    };

    /**
     * Used by {@link #newPrefix()}.
     */
    private final StringBuilder prefixSeed = new StringBuilder("ns");

    private int prefixIota = 0;

    /**
     * Allocates a new unique prefix.
     */
    private String newPrefix() {
        prefixSeed.setLength(2);
        prefixSeed.append(++prefixIota);
        return prefixSeed.toString();
    }

    /**
     * Replaces dummy prefixes in the value to the real ones
     * by using {@link #activeNamespaces}.
     *
     * @return
     *      the buffer passed as the <tt>buf</tt> parameter.
     */
    private StringBuilder fixPrefix(StringBuilder buf) {
        assert activeNamespaces!=null;

        int i;
        int len=buf.length();
        for(i=0;i<len;i++)
            if( buf.charAt(i)==MAGIC )
                break;
        // typically it doens't contain any prefix.
        // just return the original buffer in that case
        if(i==len)
            return buf;

        while(i<len) {
            char uriIdx = buf.charAt(i+1);
            NamespaceDecl ns = activeNamespaces;
            while(ns!=null && ns.uniqueId!=uriIdx)
                ns=ns.next;
            if(ns==null)
                throw new IllegalStateException("Unexpected use of prefixes "+buf);

            int length = 2;
            String prefix = ns.prefix;
            if(prefix.length()==0) {
                if(buf.length()<=i+2 || buf.charAt(i+2)!=':')
                    throw new IllegalStateException("Unexpected use of prefixes "+buf);
                length=3;
            }

            buf.replace(i,i+length,prefix);
            len += prefix.length()-length;

            while(i<len && buf.charAt(i)!=MAGIC)
                i++;
        }

        return buf;
    }

    /**
     * The first char of the dummy prefix.
     */
    static final char MAGIC = '\u0000';

    char assignNewId() {
        return (char)iota++;
    }
}
