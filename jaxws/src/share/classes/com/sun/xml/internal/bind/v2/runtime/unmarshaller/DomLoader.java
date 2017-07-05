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
 */

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.bind.annotation.DomHandler;
import javax.xml.transform.Result;
import javax.xml.transform.sax.TransformerHandler;

import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;

import org.xml.sax.SAXException;

/**
 * Loads a DOM.
 *
 * @author Kohsuke Kawaguchi
 */
public class DomLoader<ResultT extends Result> extends Loader {

    private final DomHandler<?,ResultT> dom;

    /**
     * Used to capture the state.
     *
     * This instance is created for each unmarshalling episode.
     */
    private final class State {
        /** This handler will receive SAX events. */
        private final TransformerHandler handler = JAXBContextImpl.createTransformerHandler();

        /** {@link #handler} will produce this result. */
        private final ResultT result;

        // nest level of elements.
        int depth = 1;

        public State( UnmarshallingContext context ) throws SAXException {
            result = dom.createUnmarshaller(context);

            handler.setResult(result);

            // emulate the start of documents
            try {
                handler.setDocumentLocator(context.getLocator());
                handler.startDocument();
                declarePrefixes( context, context.getAllDeclaredPrefixes() );
            } catch( SAXException e ) {
                context.handleError(e);
                throw e;
            }
        }

        public Object getElement() {
            return dom.getElement(result);
        }

        private void declarePrefixes( UnmarshallingContext context, String[] prefixes ) throws SAXException {
            for( int i=prefixes.length-1; i>=0; i-- ) {
                String nsUri = context.getNamespaceURI(prefixes[i]);
                if(nsUri==null)     throw new IllegalStateException("prefix \'"+prefixes[i]+"\' isn't bound");
                handler.startPrefixMapping(prefixes[i],nsUri );
            }
        }

        private void undeclarePrefixes( String[] prefixes ) throws SAXException {
            for( int i=prefixes.length-1; i>=0; i-- )
                handler.endPrefixMapping( prefixes[i] );
        }
    }

    public DomLoader(DomHandler<?, ResultT> dom) {
        super(true);
        this.dom = dom;
    }

    public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        UnmarshallingContext context = state.getContext();
        if (state.target == null)
            state.target = new State(context);

        State s = (State) state.target;
        try {
            s.declarePrefixes(context, context.getNewlyDeclaredPrefixes());
            s.handler.startElement(ea.uri, ea.local, ea.getQname(), ea.atts);
        } catch (SAXException e) {
            context.handleError(e);
            throw e;
        }
    }


    public void childElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        state.loader = this;
        State s = (State) state.prev.target;
        s.depth++;
        state.target = s;
    }

    public void text(UnmarshallingContext.State state, CharSequence text) throws SAXException {
        if(text.length()==0)
            return;     // there's no point in creating an empty Text node in DOM.
        try {
            State s = (State) state.target;
            s.handler.characters(text.toString().toCharArray(),0,text.length());
        } catch( SAXException e ) {
            state.getContext().handleError(e);
            throw e;
        }
    }

    public void leaveElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        State s = (State) state.target;
        UnmarshallingContext context = state.getContext();

        try {
            s.handler.endElement(ea.uri, ea.local, ea.getQname());
            s.undeclarePrefixes(context.getNewlyDeclaredPrefixes());
        } catch( SAXException e ) {
            context.handleError(e);
            throw e;
        }

        if((--s.depth)==0) {
            // emulate the end of the document
            try {
                s.undeclarePrefixes(context.getAllDeclaredPrefixes());
                s.handler.endDocument();
            } catch( SAXException e ) {
                context.handleError(e);
                throw e;
            }

            // we are done
            state.target = s.getElement();
        }
    }

}
