/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2012
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.parse.xml;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.DataPatternBuilder;
import com.sun.xml.internal.rngom.ast.builder.Div;
import com.sun.xml.internal.rngom.ast.builder.ElementAnnotationBuilder;
import com.sun.xml.internal.rngom.ast.builder.Grammar;
import com.sun.xml.internal.rngom.ast.builder.GrammarSection;
import com.sun.xml.internal.rngom.ast.builder.Include;
import com.sun.xml.internal.rngom.ast.builder.IncludedGrammar;
import com.sun.xml.internal.rngom.ast.builder.NameClassBuilder;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedNameClass;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.Context;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.util.Localizer;
import com.sun.xml.internal.rngom.util.Uri;
import com.sun.xml.internal.rngom.xml.sax.AbstractLexicalHandler;
import com.sun.xml.internal.rngom.xml.sax.XmlBaseHandler;
import com.sun.xml.internal.rngom.xml.util.Naming;
import com.sun.xml.internal.rngom.xml.util.WellKnownNamespaces;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

class SchemaParser {

    private static final String relaxngURIPrefix =
            WellKnownNamespaces.RELAX_NG.substring(0, WellKnownNamespaces.RELAX_NG.lastIndexOf('/') + 1);
    static final String relaxng10URI = WellKnownNamespaces.RELAX_NG;
    private static final Localizer localizer = new Localizer(new Localizer(Parseable.class), SchemaParser.class);
    private String relaxngURI;
    private final XMLReader xr;
    private final ErrorHandler eh;
    private final SchemaBuilder schemaBuilder;
    /**
     * The value of the {@link SchemaBuilder#getNameClassBuilder()} for the
     * {@link #schemaBuilder} object.
     */
    private final NameClassBuilder nameClassBuilder;
    private ParsedPattern startPattern;
    private Locator locator;
    private final XmlBaseHandler xmlBaseHandler = new XmlBaseHandler();
    private final ContextImpl context = new ContextImpl();
    private boolean hadError = false;
    private Hashtable patternTable;
    private Hashtable nameClassTable;

    static class PrefixMapping {

        final String prefix;
        final String uri;
        final PrefixMapping next;

        PrefixMapping(String prefix, String uri, PrefixMapping next) {
            this.prefix = prefix;
            this.uri = uri;
            this.next = next;
        }
    }

    static abstract class AbstractContext extends DtdContext implements Context {

        PrefixMapping prefixMapping;

        AbstractContext() {
            prefixMapping = new PrefixMapping("xml", WellKnownNamespaces.XML, null);
        }

        AbstractContext(AbstractContext context) {
            super(context);
            prefixMapping = context.prefixMapping;
        }

        public String resolveNamespacePrefix(String prefix) {
            for (PrefixMapping p = prefixMapping; p != null; p = p.next) {
                if (p.prefix.equals(prefix)) {
                    return p.uri;
                }
            }
            return null;
        }

        public Enumeration prefixes() {
            Vector v = new Vector();
            for (PrefixMapping p = prefixMapping; p != null; p = p.next) {
                if (!v.contains(p.prefix)) {
                    v.addElement(p.prefix);
                }
            }
            return v.elements();
        }

        public Context copy() {
            return new SavedContext(this);
        }
    }

    static class SavedContext extends AbstractContext {

        private final String baseUri;

        SavedContext(AbstractContext context) {
            super(context);
            this.baseUri = context.getBaseUri();
        }

        public String getBaseUri() {
            return baseUri;
        }
    }

    class ContextImpl extends AbstractContext {

        public String getBaseUri() {
            return xmlBaseHandler.getBaseUri();
        }
    }

    static interface CommentHandler {

        void comment(String value);
    }

    abstract class Handler implements ContentHandler, CommentHandler {

        CommentList comments;

        CommentList getComments() {
            CommentList tem = comments;
            comments = null;
            return tem;
        }

        public void comment(String value) {
            if (comments == null) {
                comments = schemaBuilder.makeCommentList();
            }
            comments.addComment(value, makeLocation());
        }

        public void processingInstruction(String target, String date) {
        }

        public void skippedEntity(String name) {
        }

        public void ignorableWhitespace(char[] ch, int start, int len) {
        }

        public void startDocument() {
        }

        public void endDocument() {
        }

        public void startPrefixMapping(String prefix, String uri) {
            context.prefixMapping = new PrefixMapping(prefix, uri, context.prefixMapping);
        }

        public void endPrefixMapping(String prefix) {
            context.prefixMapping = context.prefixMapping.next;
        }

        public void setDocumentLocator(Locator loc) {
            locator = loc;
            xmlBaseHandler.setLocator(loc);
        }
    }

    abstract class State extends Handler {

        State parent;
        String nsInherit;
        String ns;
        String datatypeLibrary;
        /**
         * The current scope, or null if there's none.
         */
        Scope scope;
        Location startLocation;
        Annotations annotations;

        void set() {
            xr.setContentHandler(this);
        }

        abstract State create();

        abstract State createChildState(String localName) throws SAXException;

        void setParent(State parent) {
            this.parent = parent;
            this.nsInherit = parent.getNs();
            this.datatypeLibrary = parent.datatypeLibrary;
            this.scope = parent.scope;
            this.startLocation = makeLocation();
            if (parent.comments != null) {
                annotations = schemaBuilder.makeAnnotations(parent.comments, getContext());
                parent.comments = null;
            } else if (parent instanceof RootState) {
                annotations = schemaBuilder.makeAnnotations(null, getContext());
            }
        }

        String getNs() {
            return ns == null ? nsInherit : ns;
        }

        boolean isRelaxNGElement(String uri) throws SAXException {
            return uri.equals(relaxngURI);
        }

        public void startElement(String namespaceURI,
                String localName,
                String qName,
                Attributes atts) throws SAXException {
            xmlBaseHandler.startElement();
            if (isRelaxNGElement(namespaceURI)) {
                State state = createChildState(localName);
                if (state == null) {
                    xr.setContentHandler(new Skipper(this));
                    return;
                }
                state.setParent(this);
                state.set();
                state.attributes(atts);
            } else {
                checkForeignElement();
                ForeignElementHandler feh = new ForeignElementHandler(this, getComments());
                feh.startElement(namespaceURI, localName, qName, atts);
                xr.setContentHandler(feh);
            }
        }

        public void endElement(String namespaceURI,
                String localName,
                String qName) throws SAXException {
            xmlBaseHandler.endElement();
            parent.set();
            end();
        }

        void setName(String name) throws SAXException {
            error("illegal_name_attribute");
        }

        void setOtherAttribute(String name, String value) throws SAXException {
            error("illegal_attribute_ignored", name);
        }

        void endAttributes() throws SAXException {
        }

        void checkForeignElement() throws SAXException {
        }

        void attributes(Attributes atts) throws SAXException {
            int len = atts.getLength();
            for (int i = 0; i < len; i++) {
                String uri = atts.getURI(i);
                if (uri.length() == 0) {
                    String name = atts.getLocalName(i);
                    if (name.equals("name")) {
                        setName(atts.getValue(i).trim());
                    } else if (name.equals("ns")) {
                        ns = atts.getValue(i);
                    } else if (name.equals("datatypeLibrary")) {
                        datatypeLibrary = atts.getValue(i);
                        checkUri(datatypeLibrary);
                        if (!datatypeLibrary.equals("")
                                && !Uri.isAbsolute(datatypeLibrary)) {
                            error("relative_datatype_library");
                        }
                        if (Uri.hasFragmentId(datatypeLibrary)) {
                            error("fragment_identifier_datatype_library");
                        }
                        datatypeLibrary = Uri.escapeDisallowedChars(datatypeLibrary);
                    } else {
                        setOtherAttribute(name, atts.getValue(i));
                    }
                } else if (uri.equals(relaxngURI)) {
                    error("qualified_attribute", atts.getLocalName(i));
                } else if (uri.equals(WellKnownNamespaces.XML)
                        && atts.getLocalName(i).equals("base")) {
                    xmlBaseHandler.xmlBaseAttribute(atts.getValue(i));
                } else {
                    if (annotations == null) {
                        annotations = schemaBuilder.makeAnnotations(null, getContext());
                    }
                    annotations.addAttribute(uri, atts.getLocalName(i), findPrefix(atts.getQName(i), uri),
                            atts.getValue(i), startLocation);
                }
            }
            endAttributes();
        }

        abstract void end() throws SAXException;

        void endChild(ParsedPattern pattern) {
            // XXX cannot happen; throw exception
        }

        void endChild(ParsedNameClass nc) {
            // XXX cannot happen; throw exception
        }

        @Override
        public void startDocument() {
        }

        @Override
        public void endDocument() {
            if (comments != null && startPattern != null) {
                startPattern = schemaBuilder.commentAfter(startPattern, comments);
                comments = null;
            }
        }

        public void characters(char[] ch, int start, int len) throws SAXException {
            for (int i = 0; i < len; i++) {
                switch (ch[start + i]) {
                    case ' ':
                    case '\r':
                    case '\n':
                    case '\t':
                        break;
                    default:
                        error("illegal_characters_ignored");
                        break;
                }
            }
        }

        boolean isPatternNamespaceURI(String s) {
            return s.equals(relaxngURI);
        }

        void endForeignChild(ParsedElementAnnotation ea) {
            if (annotations == null) {
                annotations = schemaBuilder.makeAnnotations(null, getContext());
            }
            annotations.addElement(ea);
        }

        void mergeLeadingComments() {
            if (comments != null) {
                if (annotations == null) {
                    annotations = schemaBuilder.makeAnnotations(comments, getContext());
                } else {
                    annotations.addLeadingComment(comments);
                }
                comments = null;
            }
        }
    }

    class ForeignElementHandler extends Handler {

        final State nextState;
        ElementAnnotationBuilder builder;
        final Stack builderStack = new Stack();
        StringBuffer textBuf;
        Location textLoc;

        ForeignElementHandler(State nextState, CommentList comments) {
            this.nextState = nextState;
            this.comments = comments;
        }

        public void startElement(String namespaceURI, String localName,
                String qName, Attributes atts) {
            flushText();
            if (builder != null) {
                builderStack.push(builder);
            }
            Location loc = makeLocation();
            builder = schemaBuilder.makeElementAnnotationBuilder(namespaceURI,
                    localName,
                    findPrefix(qName, namespaceURI),
                    loc,
                    getComments(),
                    getContext());
            int len = atts.getLength();
            for (int i = 0; i < len; i++) {
                String uri = atts.getURI(i);
                builder.addAttribute(uri, atts.getLocalName(i), findPrefix(atts.getQName(i), uri),
                        atts.getValue(i), loc);
            }
        }

        public void endElement(String namespaceURI, String localName,
                String qName) {
            flushText();
            if (comments != null) {
                builder.addComment(getComments());
            }
            ParsedElementAnnotation ea = builder.makeElementAnnotation();
            if (builderStack.empty()) {
                nextState.endForeignChild(ea);
                nextState.set();
            } else {
                builder = (ElementAnnotationBuilder) builderStack.pop();
                builder.addElement(ea);
            }
        }

        public void characters(char ch[], int start, int length) {
            if (textBuf == null) {
                textBuf = new StringBuffer();
            }
            textBuf.append(ch, start, length);
            if (textLoc == null) {
                textLoc = makeLocation();
            }
        }

        @Override
        public void comment(String value) {
            flushText();
            super.comment(value);
        }

        void flushText() {
            if (textBuf != null && textBuf.length() != 0) {
                builder.addText(textBuf.toString(), textLoc, getComments());
                textBuf.setLength(0);
            }
            textLoc = null;
        }
    }

    static class Skipper extends DefaultHandler implements CommentHandler {

        int level = 1;
        final State nextState;

        Skipper(State nextState) {
            this.nextState = nextState;
        }

        @Override
        public void startElement(String namespaceURI,
                String localName,
                String qName,
                Attributes atts) throws SAXException {
            ++level;
        }

        @Override
        public void endElement(String namespaceURI,
                String localName,
                String qName) throws SAXException {
            if (--level == 0) {
                nextState.set();
            }
        }

        public void comment(String value) {
        }
    }

    abstract class EmptyContentState extends State {

        State createChildState(String localName) throws SAXException {
            error("expected_empty", localName);
            return null;
        }

        abstract ParsedPattern makePattern() throws SAXException;

        void end() throws SAXException {
            if (comments != null) {
                if (annotations == null) {
                    annotations = schemaBuilder.makeAnnotations(null, getContext());
                }
                annotations.addComment(comments);
                comments = null;
            }
            parent.endChild(makePattern());
        }
    }
    static private final int INIT_CHILD_ALLOC = 5;

    abstract class PatternContainerState extends State {

        List<ParsedPattern> childPatterns;

        State createChildState(String localName) throws SAXException {
            State state = (State) patternTable.get(localName);
            if (state == null) {
                error("expected_pattern", localName);
                return null;
            }
            return state.create();
        }

        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            if (patterns.size() == 1 && anno == null) {
                return patterns.get(0);
            }
            return schemaBuilder.makeGroup(patterns, loc, anno);
        }

        @Override
        void endChild(ParsedPattern pattern) {
            if (childPatterns == null) {
                childPatterns = new ArrayList<ParsedPattern>(INIT_CHILD_ALLOC);
            }
            childPatterns.add(pattern);
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            // Harshit : Annotation handling should always be taken care of, irrespective of childPatterns being null or not.
            super.endForeignChild(ea);
            if (childPatterns != null) {
                int idx = childPatterns.size() - 1;
                childPatterns.set(idx, schemaBuilder.annotateAfter(childPatterns.get(idx), ea));
            }
        }

        void end() throws SAXException {
            if (childPatterns == null) {
                error("missing_children");
                endChild(schemaBuilder.makeErrorPattern());
            }
            if (comments != null) {
                int idx = childPatterns.size() - 1;
                childPatterns.set(idx, schemaBuilder.commentAfter(childPatterns.get(idx), comments));
                comments = null;
            }
            sendPatternToParent(buildPattern(childPatterns, startLocation, annotations));
        }

        void sendPatternToParent(ParsedPattern p) {
            parent.endChild(p);
        }
    }

    class GroupState extends PatternContainerState {

        State create() {
            return new GroupState();
        }
    }

    class ZeroOrMoreState extends PatternContainerState {

        State create() {
            return new ZeroOrMoreState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeZeroOrMore(super.buildPattern(patterns, loc, null), loc, anno);
        }
    }

    class OneOrMoreState extends PatternContainerState {

        State create() {
            return new OneOrMoreState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeOneOrMore(super.buildPattern(patterns, loc, null), loc, anno);
        }
    }

    class OptionalState extends PatternContainerState {

        State create() {
            return new OptionalState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeOptional(super.buildPattern(patterns, loc, null), loc, anno);
        }
    }

    class ListState extends PatternContainerState {

        State create() {
            return new ListState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeList(super.buildPattern(patterns, loc, null), loc, anno);
        }
    }

    class ChoiceState extends PatternContainerState {

        State create() {
            return new ChoiceState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeChoice(patterns, loc, anno);
        }
    }

    class InterleaveState extends PatternContainerState {

        State create() {
            return new InterleaveState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) {
            return schemaBuilder.makeInterleave(patterns, loc, anno);
        }
    }

    class MixedState extends PatternContainerState {

        State create() {
            return new MixedState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeMixed(super.buildPattern(patterns, loc, null), loc, anno);
        }
    }

    static interface NameClassRef {

        void setNameClass(ParsedNameClass nc);
    }

    class ElementState extends PatternContainerState implements NameClassRef {

        ParsedNameClass nameClass;
        boolean nameClassWasAttribute;
        String name;

        @Override
        void setName(String name) {
            this.name = name;
        }

        public void setNameClass(ParsedNameClass nc) {
            nameClass = nc;
        }

        @Override
        void endAttributes() throws SAXException {
            if (name != null) {
                nameClass = expandName(name, getNs(), null);
                nameClassWasAttribute = true;
            } else {
                new NameClassChildState(this, this).set();
            }
        }

        State create() {
            return new ElementState();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeElement(nameClass, super.buildPattern(patterns, loc, null), loc, anno);
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            if (nameClassWasAttribute || childPatterns != null || nameClass == null) {
                super.endForeignChild(ea);
            } else {
                nameClass = nameClassBuilder.annotateAfter(nameClass, ea);
            }
        }
    }

    class RootState extends PatternContainerState {

        IncludedGrammar grammar;

        RootState() {
        }

        RootState(IncludedGrammar grammar, Scope scope, String ns) {
            this.grammar = grammar;
            this.scope = scope;
            this.nsInherit = ns;
            this.datatypeLibrary = "";
        }

        State create() {
            return new RootState();
        }

        @Override
        State createChildState(String localName) throws SAXException {
            if (grammar == null) {
                return super.createChildState(localName);
            }
            if (localName.equals("grammar")) {
                return new MergeGrammarState(grammar);
            }
            error("expected_grammar", localName);
            return null;
        }

        @Override
        void checkForeignElement() throws SAXException {
            error("root_bad_namespace_uri", WellKnownNamespaces.RELAX_NG);
        }

        @Override
        void endChild(ParsedPattern pattern) {
            startPattern = pattern;
        }

        @Override
        boolean isRelaxNGElement(String uri) throws SAXException {
            if (!uri.startsWith(relaxngURIPrefix)) {
                return false;
            }
            if (!uri.equals(WellKnownNamespaces.RELAX_NG)) {
                warning("wrong_uri_version",
                        WellKnownNamespaces.RELAX_NG.substring(relaxngURIPrefix.length()),
                        uri.substring(relaxngURIPrefix.length()));
            }
            relaxngURI = uri;
            return true;
        }
    }

    class NotAllowedState extends EmptyContentState {

        State create() {
            return new NotAllowedState();
        }

        ParsedPattern makePattern() {
            return schemaBuilder.makeNotAllowed(startLocation, annotations);
        }
    }

    class EmptyState extends EmptyContentState {

        State create() {
            return new EmptyState();
        }

        ParsedPattern makePattern() {
            return schemaBuilder.makeEmpty(startLocation, annotations);
        }
    }

    class TextState extends EmptyContentState {

        State create() {
            return new TextState();
        }

        ParsedPattern makePattern() {
            return schemaBuilder.makeText(startLocation, annotations);
        }
    }

    class ValueState extends EmptyContentState {

        final StringBuffer buf = new StringBuffer();
        String type;

        State create() {
            return new ValueState();
        }

        @Override
        void setOtherAttribute(String name, String value) throws SAXException {
            if (name.equals("type")) {
                type = checkNCName(value.trim());
            } else {
                super.setOtherAttribute(name, value);
            }
        }

        @Override
        public void characters(char[] ch, int start, int len) {
            buf.append(ch, start, len);
        }

        @Override
        void checkForeignElement() throws SAXException {
            error("value_contains_foreign_element");
        }

        ParsedPattern makePattern() throws SAXException {
            if (type == null) {
                return makePattern("", "token");
            } else {
                return makePattern(datatypeLibrary, type);
            }
        }

        @Override
        void end() throws SAXException {
            mergeLeadingComments();
            super.end();
        }

        ParsedPattern makePattern(String datatypeLibrary, String type) {
            return schemaBuilder.makeValue(datatypeLibrary,
                    type,
                    buf.toString(),
                    getContext(),
                    getNs(),
                    startLocation,
                    annotations);
        }
    }

    class DataState extends State {

        String type;
        ParsedPattern except = null;
        DataPatternBuilder dpb = null;

        State create() {
            return new DataState();
        }

        State createChildState(String localName) throws SAXException {
            if (localName.equals("param")) {
                if (except != null) {
                    error("param_after_except");
                }
                return new ParamState(dpb);
            }
            if (localName.equals("except")) {
                if (except != null) {
                    error("multiple_except");
                }
                return new ChoiceState();
            }
            error("expected_param_except", localName);
            return null;
        }

        @Override
        void setOtherAttribute(String name, String value) throws SAXException {
            if (name.equals("type")) {
                type = checkNCName(value.trim());
            } else {
                super.setOtherAttribute(name, value);
            }
        }

        @Override
        void endAttributes() throws SAXException {
            if (type == null) {
                error("missing_type_attribute");
            } else {
                dpb = schemaBuilder.makeDataPatternBuilder(datatypeLibrary, type, startLocation);
            }
        }

        void end() throws SAXException {
            ParsedPattern p;
            if (dpb != null) {
                if (except != null) {
                    p = dpb.makePattern(except, startLocation, annotations);
                } else {
                    p = dpb.makePattern(startLocation, annotations);
                }
            } else {
                p = schemaBuilder.makeErrorPattern();
            }
            // XXX need to capture comments
            parent.endChild(p);
        }

        @Override
        void endChild(ParsedPattern pattern) {
            except = pattern;
        }
    }

    class ParamState extends State {

        private final StringBuffer buf = new StringBuffer();
        private final DataPatternBuilder dpb;
        private String name;

        ParamState(DataPatternBuilder dpb) {
            this.dpb = dpb;
        }

        State create() {
            return new ParamState(null);
        }

        @Override
        void setName(String name) throws SAXException {
            this.name = checkNCName(name);
        }

        @Override
        void endAttributes() throws SAXException {
            if (name == null) {
                error("missing_name_attribute");
            }
        }

        State createChildState(String localName) throws SAXException {
            error("expected_empty", localName);
            return null;
        }

        @Override
        public void characters(char[] ch, int start, int len) {
            buf.append(ch, start, len);
        }

        @Override
        void checkForeignElement() throws SAXException {
            error("param_contains_foreign_element");
        }

        void end() throws SAXException {
            if (name == null) {
                return;
            }
            if (dpb == null) {
                return;
            }
            mergeLeadingComments();
            dpb.addParam(name, buf.toString(), getContext(), getNs(), startLocation, annotations);
        }
    }

    class AttributeState extends PatternContainerState implements NameClassRef {

        ParsedNameClass nameClass;
        boolean nameClassWasAttribute;
        String name;

        State create() {
            return new AttributeState();
        }

        @Override
        void setName(String name) {
            this.name = name;
        }

        public void setNameClass(ParsedNameClass nc) {
            nameClass = nc;
        }

        @Override
        void endAttributes() throws SAXException {
            if (name != null) {
                String nsUse;
                if (ns != null) {
                    nsUse = ns;
                } else {
                    nsUse = "";
                }
                nameClass = expandName(name, nsUse, null);
                nameClassWasAttribute = true;
            } else {
                new NameClassChildState(this, this).set();
            }
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            if (nameClassWasAttribute || childPatterns != null || nameClass == null) {
                super.endForeignChild(ea);
            } else {
                nameClass = nameClassBuilder.annotateAfter(nameClass, ea);
            }
        }

        @Override
        void end() throws SAXException {
            if (childPatterns == null) {
                endChild(schemaBuilder.makeText(startLocation, null));
            }
            super.end();
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return schemaBuilder.makeAttribute(nameClass, super.buildPattern(patterns, loc, null), loc, anno);
        }

        @Override
        State createChildState(String localName) throws SAXException {
            State tem = super.createChildState(localName);
            if (tem != null && childPatterns != null) {
                error("attribute_multi_pattern");
            }
            return tem;
        }
    }

    abstract class SinglePatternContainerState extends PatternContainerState {

        @Override
        State createChildState(String localName) throws SAXException {
            if (childPatterns == null) {
                return super.createChildState(localName);
            }
            error("too_many_children");
            return null;
        }
    }

    class GrammarSectionState extends State {

        GrammarSection section;

        GrammarSectionState() {
        }

        GrammarSectionState(GrammarSection section) {
            this.section = section;
        }

        State create() {
            return new GrammarSectionState(null);
        }

        State createChildState(String localName) throws SAXException {
            if (localName.equals("define")) {
                return new DefineState(section);
            }
            if (localName.equals("start")) {
                return new StartState(section);
            }
            if (localName.equals("include")) {
                Include include = section.makeInclude();
                if (include != null) {
                    return new IncludeState(include);
                }
            }
            if (localName.equals("div")) {
                return new DivState(section.makeDiv());
            }
            error("expected_define", localName);
            // XXX better errors
            return null;
        }

        void end() throws SAXException {
            if (comments != null) {
                section.topLevelComment(comments);
                comments = null;
            }
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            section.topLevelAnnotation(ea);
        }
    }

    class DivState extends GrammarSectionState {

        final Div div;

        DivState(Div div) {
            super(div);
            this.div = div;
        }

        @Override
        void end() throws SAXException {
            super.end();
            div.endDiv(startLocation, annotations);
        }
    }

    class IncludeState extends GrammarSectionState {

        String href;
        final Include include;

        IncludeState(Include include) {
            super(include);
            this.include = include;
        }

        @Override
        void setOtherAttribute(String name, String value) throws SAXException {
            if (name.equals("href")) {
                href = value;
                checkUri(href);
            } else {
                super.setOtherAttribute(name, value);
            }
        }

        @Override
        void endAttributes() throws SAXException {
            if (href == null) {
                error("missing_href_attribute");
            } else {
                href = resolve(href);
            }
        }

        @Override
        void end() throws SAXException {
            super.end();
            if (href != null) {
                try {
                    include.endInclude(parseable, href, getNs(), startLocation, annotations);
                } catch (IllegalSchemaException e) {
                }
            }
        }
    }

    class MergeGrammarState extends GrammarSectionState {

        final IncludedGrammar grammar;

        MergeGrammarState(IncludedGrammar grammar) {
            super(grammar);
            this.grammar = grammar;
        }

        @Override
        void end() throws SAXException {
            super.end();
            parent.endChild(grammar.endIncludedGrammar(startLocation, annotations));
        }
    }

    class GrammarState extends GrammarSectionState {

        Grammar grammar;

        @Override
        void setParent(State parent) {
            super.setParent(parent);
            grammar = schemaBuilder.makeGrammar(scope);
            section = grammar;
            scope = grammar;
        }

        @Override
        State create() {
            return new GrammarState();
        }

        @Override
        void end() throws SAXException {
            super.end();
            parent.endChild(grammar.endGrammar(startLocation, annotations));
        }
    }

    class RefState extends EmptyContentState {

        String name;

        State create() {
            return new RefState();
        }

        @Override
        void endAttributes() throws SAXException {
            if (name == null) {
                error("missing_name_attribute");
            }
        }

        @Override
        void setName(String name) throws SAXException {
            this.name = checkNCName(name);
        }

        ParsedPattern makePattern() throws SAXException {
            if (name == null) {
                return schemaBuilder.makeErrorPattern();
            }
            if (scope == null) {
                error("ref_outside_grammar", name);
                return schemaBuilder.makeErrorPattern();
            } else {
                return scope.makeRef(name, startLocation, annotations);
            }
        }
    }

    class ParentRefState extends RefState {

        @Override
        State create() {
            return new ParentRefState();
        }

        @Override
        ParsedPattern makePattern() throws SAXException {
            if (name == null) {
                return schemaBuilder.makeErrorPattern();
            }
            if (scope == null) {
                error("parent_ref_outside_grammar", name);
                return schemaBuilder.makeErrorPattern();
            } else {
                return scope.makeParentRef(name, startLocation, annotations);
            }
        }
    }

    class ExternalRefState extends EmptyContentState {

        String href;

        State create() {
            return new ExternalRefState();
        }

        @Override
        void setOtherAttribute(String name, String value) throws SAXException {
            if (name.equals("href")) {
                href = value;
                checkUri(href);
            } else {
                super.setOtherAttribute(name, value);
            }
        }

        @Override
        void endAttributes() throws SAXException {
            if (href == null) {
                error("missing_href_attribute");
            } else {
                href = resolve(href);
            }
        }

        ParsedPattern makePattern() {
            if (href != null) {
                try {
                    return schemaBuilder.makeExternalRef(parseable,
                            href,
                            getNs(),
                            scope,
                            startLocation,
                            annotations);
                } catch (IllegalSchemaException e) {
                }
            }
            return schemaBuilder.makeErrorPattern();
        }
    }

    abstract class DefinitionState extends PatternContainerState {

        GrammarSection.Combine combine = null;
        final GrammarSection section;

        DefinitionState(GrammarSection section) {
            this.section = section;
        }

        @Override
        void setOtherAttribute(String name, String value) throws SAXException {
            if (name.equals("combine")) {
                value = value.trim();
                if (value.equals("choice")) {
                    combine = GrammarSection.COMBINE_CHOICE;
                } else if (value.equals("interleave")) {
                    combine = GrammarSection.COMBINE_INTERLEAVE;
                } else {
                    error("combine_attribute_bad_value", value);
                }
            } else {
                super.setOtherAttribute(name, value);
            }
        }

        @Override
        ParsedPattern buildPattern(List<ParsedPattern> patterns, Location loc, Annotations anno) throws SAXException {
            return super.buildPattern(patterns, loc, null);
        }
    }

    class DefineState extends DefinitionState {

        String name;

        DefineState(GrammarSection section) {
            super(section);
        }

        State create() {
            return new DefineState(null);
        }

        @Override
        void setName(String name) throws SAXException {
            this.name = checkNCName(name);
        }

        @Override
        void endAttributes() throws SAXException {
            if (name == null) {
                error("missing_name_attribute");
            }
        }

        @Override
        void sendPatternToParent(ParsedPattern p) {
            if (name != null) {
                section.define(name, combine, p, startLocation, annotations);
            }
        }
    }

    class StartState extends DefinitionState {

        StartState(GrammarSection section) {
            super(section);
        }

        State create() {
            return new StartState(null);
        }

        @Override
        void sendPatternToParent(ParsedPattern p) {
            section.define(GrammarSection.START, combine, p, startLocation, annotations);
        }

        @Override
        State createChildState(String localName) throws SAXException {
            State tem = super.createChildState(localName);
            if (tem != null && childPatterns != null) {
                error("start_multi_pattern");
            }
            return tem;
        }
    }

    abstract class NameClassContainerState extends State {

        State createChildState(String localName) throws SAXException {
            State state = (State) nameClassTable.get(localName);
            if (state == null) {
                error("expected_name_class", localName);
                return null;
            }
            return state.create();
        }
    }

    class NameClassChildState extends NameClassContainerState {

        final State prevState;
        final NameClassRef nameClassRef;

        State create() {
            return null;
        }

        NameClassChildState(State prevState, NameClassRef nameClassRef) {
            this.prevState = prevState;
            this.nameClassRef = nameClassRef;
            setParent(prevState.parent);
            this.ns = prevState.ns;
        }

        @Override
        void endChild(ParsedNameClass nameClass) {
            nameClassRef.setNameClass(nameClass);
            prevState.set();
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            prevState.endForeignChild(ea);
        }

        void end() throws SAXException {
            nameClassRef.setNameClass(nameClassBuilder.makeErrorNameClass());
            error("missing_name_class");
            prevState.set();
            prevState.end();
        }
    }

    abstract class NameClassBaseState extends State {

        abstract ParsedNameClass makeNameClass() throws SAXException;

        void end() throws SAXException {
            parent.endChild(makeNameClass());
        }
    }

    class NameState extends NameClassBaseState {

        final StringBuffer buf = new StringBuffer();

        State createChildState(String localName) throws SAXException {
            error("expected_name", localName);
            return null;
        }

        State create() {
            return new NameState();
        }

        @Override
        public void characters(char[] ch, int start, int len) {
            buf.append(ch, start, len);
        }

        @Override
        void checkForeignElement() throws SAXException {
            error("name_contains_foreign_element");
        }

        ParsedNameClass makeNameClass() throws SAXException {
            mergeLeadingComments();
            return expandName(buf.toString().trim(), getNs(), annotations);
        }
    }
    private static final int PATTERN_CONTEXT = 0;
    private static final int ANY_NAME_CONTEXT = 1;
    private static final int NS_NAME_CONTEXT = 2;
    private SAXParseable parseable;

    class AnyNameState extends NameClassBaseState {

        ParsedNameClass except = null;

        State create() {
            return new AnyNameState();
        }

        State createChildState(String localName) throws SAXException {
            if (localName.equals("except")) {
                if (except != null) {
                    error("multiple_except");
                }
                return new NameClassChoiceState(getContext());
            }
            error("expected_except", localName);
            return null;
        }

        int getContext() {
            return ANY_NAME_CONTEXT;
        }

        ParsedNameClass makeNameClass() {
            if (except == null) {
                return makeNameClassNoExcept();
            } else {
                return makeNameClassExcept(except);
            }
        }

        ParsedNameClass makeNameClassNoExcept() {
            return nameClassBuilder.makeAnyName(startLocation, annotations);
        }

        ParsedNameClass makeNameClassExcept(ParsedNameClass except) {
            return nameClassBuilder.makeAnyName(except, startLocation, annotations);
        }

        @Override
        void endChild(ParsedNameClass nameClass) {
            except = nameClass;
        }
    }

    class NsNameState extends AnyNameState {

        @Override
        State create() {
            return new NsNameState();
        }

        @Override
        ParsedNameClass makeNameClassNoExcept() {
            return nameClassBuilder.makeNsName(getNs(), null, null);
        }

        @Override
        ParsedNameClass makeNameClassExcept(ParsedNameClass except) {
            return nameClassBuilder.makeNsName(getNs(), except, null, null);
        }

        @Override
        int getContext() {
            return NS_NAME_CONTEXT;
        }
    }

    class NameClassChoiceState extends NameClassContainerState {

        private ParsedNameClass[] nameClasses;
        private int nNameClasses;
        private int context;

        NameClassChoiceState() {
            this.context = PATTERN_CONTEXT;
        }

        NameClassChoiceState(int context) {
            this.context = context;
        }

        @Override
        void setParent(State parent) {
            super.setParent(parent);
            if (parent instanceof NameClassChoiceState) {
                this.context = ((NameClassChoiceState) parent).context;
            }
        }

        State create() {
            return new NameClassChoiceState();
        }

        @Override
        State createChildState(String localName) throws SAXException {
            if (localName.equals("anyName")) {
                if (context >= ANY_NAME_CONTEXT) {
                    error(context == ANY_NAME_CONTEXT
                            ? "any_name_except_contains_any_name"
                            : "ns_name_except_contains_any_name");
                    return null;
                }
            } else if (localName.equals("nsName")) {
                if (context == NS_NAME_CONTEXT) {
                    error("ns_name_except_contains_ns_name");
                    return null;
                }
            }
            return super.createChildState(localName);
        }

        @Override
        void endChild(ParsedNameClass nc) {
            if (nameClasses == null) {
                nameClasses = new ParsedNameClass[INIT_CHILD_ALLOC];
            } else if (nNameClasses >= nameClasses.length) {
                ParsedNameClass[] newNameClasses = new ParsedNameClass[nameClasses.length * 2];
                System.arraycopy(nameClasses, 0, newNameClasses, 0, nameClasses.length);
                nameClasses = newNameClasses;
            }
            nameClasses[nNameClasses++] = nc;
        }

        @Override
        void endForeignChild(ParsedElementAnnotation ea) {
            if (nNameClasses == 0) {
                super.endForeignChild(ea);
            } else {
                nameClasses[nNameClasses - 1] = nameClassBuilder.annotateAfter(nameClasses[nNameClasses - 1], ea);
            }
        }

        void end() throws SAXException {
            if (nNameClasses == 0) {
                error("missing_name_class");
                parent.endChild(nameClassBuilder.makeErrorNameClass());
                return;
            }
            if (comments != null) {
                nameClasses[nNameClasses - 1] = nameClassBuilder.commentAfter(nameClasses[nNameClasses - 1], comments);
                comments = null;
            }
            parent.endChild(nameClassBuilder.makeChoice(Arrays.asList(nameClasses).subList(0, nNameClasses), startLocation, annotations));
        }
    }

    private void initPatternTable() {
        patternTable = new Hashtable();
        patternTable.put("zeroOrMore", new ZeroOrMoreState());
        patternTable.put("oneOrMore", new OneOrMoreState());
        patternTable.put("optional", new OptionalState());
        patternTable.put("list", new ListState());
        patternTable.put("choice", new ChoiceState());
        patternTable.put("interleave", new InterleaveState());
        patternTable.put("group", new GroupState());
        patternTable.put("mixed", new MixedState());
        patternTable.put("element", new ElementState());
        patternTable.put("attribute", new AttributeState());
        patternTable.put("empty", new EmptyState());
        patternTable.put("text", new TextState());
        patternTable.put("value", new ValueState());
        patternTable.put("data", new DataState());
        patternTable.put("notAllowed", new NotAllowedState());
        patternTable.put("grammar", new GrammarState());
        patternTable.put("ref", new RefState());
        patternTable.put("parentRef", new ParentRefState());
        patternTable.put("externalRef", new ExternalRefState());
    }

    private void initNameClassTable() {
        nameClassTable = new Hashtable();
        nameClassTable.put("name", new NameState());
        nameClassTable.put("anyName", new AnyNameState());
        nameClassTable.put("nsName", new NsNameState());
        nameClassTable.put("choice", new NameClassChoiceState());
    }

    public ParsedPattern getParsedPattern() throws IllegalSchemaException {
        if (hadError) {
            throw new IllegalSchemaException();
        }
        return startPattern;
    }

    private void error(String key) throws SAXException {
        error(key, locator);
    }

    private void error(String key, String arg) throws SAXException {
        error(key, arg, locator);
    }

    void error(String key, String arg1, String arg2) throws SAXException {
        error(key, arg1, arg2, locator);
    }

    private void error(String key, Locator loc) throws SAXException {
        error(new SAXParseException(localizer.message(key), loc));
    }

    private void error(String key, String arg, Locator loc) throws SAXException {
        error(new SAXParseException(localizer.message(key, arg), loc));
    }

    private void error(String key, String arg1, String arg2, Locator loc)
            throws SAXException {
        error(new SAXParseException(localizer.message(key, arg1, arg2), loc));
    }

    private void error(SAXParseException e) throws SAXException {
        hadError = true;
        if (eh != null) {
            eh.error(e);
        }
    }

    void warning(String key) throws SAXException {
        warning(key, locator);
    }

    private void warning(String key, String arg) throws SAXException {
        warning(key, arg, locator);
    }

    private void warning(String key, String arg1, String arg2) throws SAXException {
        warning(key, arg1, arg2, locator);
    }

    private void warning(String key, Locator loc) throws SAXException {
        warning(new SAXParseException(localizer.message(key), loc));
    }

    private void warning(String key, String arg, Locator loc) throws SAXException {
        warning(new SAXParseException(localizer.message(key, arg), loc));
    }

    private void warning(String key, String arg1, String arg2, Locator loc)
            throws SAXException {
        warning(new SAXParseException(localizer.message(key, arg1, arg2), loc));
    }

    private void warning(SAXParseException e) throws SAXException {
        if (eh != null) {
            eh.warning(e);
        }
    }

    SchemaParser(SAXParseable parseable,
            XMLReader xr,
            ErrorHandler eh,
            SchemaBuilder schemaBuilder,
            IncludedGrammar grammar,
            Scope scope,
            String inheritedNs) throws SAXException {
        this.parseable = parseable;
        this.xr = xr;
        this.eh = eh;
        this.schemaBuilder = schemaBuilder;
        this.nameClassBuilder = schemaBuilder.getNameClassBuilder();
        if (eh != null) {
            xr.setErrorHandler(eh);
        }
        xr.setDTDHandler(context);
        if (schemaBuilder.usesComments()) {
            try {
                xr.setProperty("http://xml.org/sax/properties/lexical-handler", new LexicalHandlerImpl());
            } catch (SAXNotRecognizedException e) {
                warning("no_comment_support", xr.getClass().getName());
            } catch (SAXNotSupportedException e) {
                warning("no_comment_support", xr.getClass().getName());
            }
        }
        initPatternTable();
        initNameClassTable();
        new RootState(grammar, scope, inheritedNs).set();
    }

    private Context getContext() {
        return context;
    }

    class LexicalHandlerImpl extends AbstractLexicalHandler {

        private boolean inDtd = false;

        @Override
        public void startDTD(String s, String s1, String s2) throws SAXException {
            inDtd = true;
        }

        @Override
        public void endDTD() throws SAXException {
            inDtd = false;
        }

        @Override
        public void comment(char[] chars, int start, int length) throws SAXException {
            if (!inDtd) {
                ((CommentHandler) xr.getContentHandler()).comment(new String(chars, start, length));
            }
        }
    }

    private ParsedNameClass expandName(String name, String ns, Annotations anno) throws SAXException {
        int ic = name.indexOf(':');
        if (ic == -1) {
            return nameClassBuilder.makeName(ns, checkNCName(name), null, null, anno);
        }
        String prefix = checkNCName(name.substring(0, ic));
        String localName = checkNCName(name.substring(ic + 1));
        for (PrefixMapping tem = context.prefixMapping; tem != null; tem = tem.next) {
            if (tem.prefix.equals(prefix)) {
                return nameClassBuilder.makeName(tem.uri, localName, prefix, null, anno);
            }
        }
        error("undefined_prefix", prefix);
        return nameClassBuilder.makeName("", localName, null, null, anno);
    }

    private String findPrefix(String qName, String uri) {
        String prefix = null;
        if (qName == null || qName.equals("")) {
            for (PrefixMapping p = context.prefixMapping; p != null; p = p.next) {
                if (p.uri.equals(uri)) {
                    prefix = p.prefix;
                    break;
                }
            }
        } else {
            int off = qName.indexOf(':');
            if (off > 0) {
                prefix = qName.substring(0, off);
            }
        }
        return prefix;
    }

    private String checkNCName(String str) throws SAXException {
        if (!Naming.isNcname(str)) {
            error("invalid_ncname", str);
        }
        return str;
    }

    private String resolve(String systemId) throws SAXException {
        if (Uri.hasFragmentId(systemId)) {
            error("href_fragment_id");
        }
        systemId = Uri.escapeDisallowedChars(systemId);
        return Uri.resolve(xmlBaseHandler.getBaseUri(), systemId);
    }

    private Location makeLocation() {
        if (locator == null) {
            return null;
        }
        return schemaBuilder.makeLocation(locator.getSystemId(),
                locator.getLineNumber(),
                locator.getColumnNumber());
    }

    private void checkUri(String s) throws SAXException {
        if (!Uri.isValid(s)) {
            error("invalid_uri", s);
        }
    }
}
