/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.impl.parser;

import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XmlString;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.impl.ForeignAttributesImpl;
import com.sun.xml.internal.xsom.impl.SchemaImpl;
import com.sun.xml.internal.xsom.impl.UName;
import com.sun.xml.internal.xsom.impl.Const;
import com.sun.xml.internal.xsom.impl.parser.state.NGCCRuntime;
import com.sun.xml.internal.xsom.impl.parser.state.Schema;
import com.sun.xml.internal.xsom.parser.AnnotationParser;
import com.sun.xml.internal.org.relaxng.datatype.ValidationContext;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.LocatorImpl;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * NGCCRuntime extended with various utility methods for
 * parsing XML Schema.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class NGCCRuntimeEx extends NGCCRuntime implements PatcherManager {

    /** coordinator. */
    public final ParserContext parser;

    /** The schema currently being parsed. */
    public SchemaImpl currentSchema;

    /** The @finalDefault value of the current schema. */
    public int finalDefault = 0;
    /** The @blockDefault value of the current schema. */
    public int blockDefault = 0;

    /**
     * The @elementFormDefault value of the current schema.
     * True if local elements are qualified by default.
     */
    public boolean elementFormDefault = false;

    /**
     * The @attributeFormDefault value of the current schema.
     * True if local attributes are qualified by default.
     */
    public boolean attributeFormDefault = false;

    /**
     * True if the current schema is in a chameleon mode.
     * This changes the way QNames are interpreted.
     *
     * Life is very miserable with XML Schema, as you see.
     */
    public boolean chameleonMode = false;

    /**
     * URI that identifies the schema document.
     * Maybe null if the system ID is not available.
     */
    private String documentSystemId;

    /**
     * Keep the local name of elements encountered so far.
     * This information is passed to AnnotationParser as
     * context information
     */
    private final Stack<String> elementNames = new Stack<String>();

    /**
     * Points to the schema document (the parser of it) that included/imported
     * this schema.
     */
    private final NGCCRuntimeEx referer;

    /**
     * Points to the {@link SchemaDocumentImpl} that represents the
     * schema document being parsed.
     */
    public SchemaDocumentImpl document;

    NGCCRuntimeEx( ParserContext _parser ) {
        this(_parser,false,null);
    }

    private NGCCRuntimeEx( ParserContext _parser, boolean chameleonMode, NGCCRuntimeEx referer ) {
        this.parser = _parser;
        this.chameleonMode = chameleonMode;
        this.referer = referer;

        // set up the default namespace binding
        currentContext = new Context("","",null);
        currentContext = new Context("xml","http://www.w3.org/XML/1998/namespace",currentContext);
    }

    public void checkDoubleDefError( XSDeclaration c ) throws SAXException {
        if(c==null || ignorableDuplicateComponent(c)) return;

        reportError( Messages.format(Messages.ERR_DOUBLE_DEFINITION,c.getName()) );
        reportError( Messages.format(Messages.ERR_DOUBLE_DEFINITION_ORIGINAL), c.getLocator() );
    }

    public static boolean ignorableDuplicateComponent(XSDeclaration c) {
        if(c.getTargetNamespace().equals(Const.schemaNamespace)) {
            if(c instanceof XSSimpleType)
                // hide artificial "double definitions" on simple types
                return true;
            if(c.isGlobal() && c.getName().equals("anyType"))
                return true; // ditto for anyType
        }
        return false;
    }



    /* registers a patcher that will run after all the parsing has finished. */
    @Override
    public void addPatcher( Patch patcher ) {
        parser.patcherManager.addPatcher(patcher);
    }
    @Override
    public void addErrorChecker( Patch patcher ) {
        parser.patcherManager.addErrorChecker(patcher);
    }
    @Override
    public void reportError( String msg, Locator loc ) throws SAXException {
        parser.patcherManager.reportError(msg,loc);
    }
    public void reportError( String msg ) throws SAXException {
        reportError(msg,getLocator());
    }


    /**
     * Resolves relative URI found in the document.
     *
     * @param namespaceURI
     *      passed to the entity resolver.
     * @param relativeUri
     *      value of the schemaLocation attribute. Can be null.
     *
     * @return
     *      non-null if {@link EntityResolver} returned an {@link InputSource},
     *      or if the relativeUri parameter seems to be pointing to something.
     *      Otherwise it returns null, in which case import/include should be abandoned.
     */
    private InputSource resolveRelativeURL( String namespaceURI, String relativeUri ) throws SAXException {
        try {
            String baseUri = getLocator().getSystemId();
            if(baseUri==null)
                // if the base URI is not available, the document system ID is
                // better than nothing.
                baseUri=documentSystemId;

            EntityResolver er = parser.getEntityResolver();
            String systemId = null;

            if (relativeUri!=null) {
                if (isAbsolute(relativeUri)) {
                    systemId = relativeUri;
                }
                if (baseUri == null || !isAbsolute(baseUri)) {
                    throw new IOException("Unable to resolve relative URI " + relativeUri + " because base URI is not absolute: " + baseUri);
                }
                systemId = new URL(new URL(baseUri), relativeUri).toString();
            }

            if (er!=null) {
                InputSource is = er.resolveEntity(namespaceURI,systemId);
                if (is == null) {
                    try {
                        String normalizedSystemId = URI.create(systemId).normalize().toASCIIString();
                        is = er.resolveEntity(namespaceURI,normalizedSystemId);
                    } catch (Exception e) {
                        // just ignore, this is a second try, return the fallback if this breaks
                    }
                }
                if (is != null) {
                    return is;
                }
            }

            if (systemId!=null)
                return new InputSource(systemId);
            else
                return null;
        } catch (IOException e) {
            SAXParseException se = new SAXParseException(e.getMessage(),getLocator(),e);
            parser.errorHandler.error(se);
            return null;
        }
    }

    private static final Pattern P = Pattern.compile(".*[/#?].*");

    private static boolean isAbsolute(String uri) {
        int i = uri.indexOf(':');
        if (i < 0) {
            return false;
        }
        return !P.matcher(uri.substring(0, i)).matches();
    }

    /**
     * Includes the specified schema.
     *
     * @param schemaLocation
     * @throws org.xml.sax.SAXException */
    public void includeSchema( String schemaLocation ) throws SAXException {
        NGCCRuntimeEx runtime = new NGCCRuntimeEx(parser,chameleonMode,this);
        runtime.currentSchema = this.currentSchema;
        runtime.blockDefault = this.blockDefault;
        runtime.finalDefault = this.finalDefault;

        if( schemaLocation==null ) {
            SAXParseException e = new SAXParseException(
                Messages.format( Messages.ERR_MISSING_SCHEMALOCATION ), getLocator() );
            parser.errorHandler.fatalError(e);
            throw e;
        }

        runtime.parseEntity( resolveRelativeURL(null,schemaLocation),
            true, currentSchema.getTargetNamespace(), getLocator() );
    }

    /**
     * Imports the specified schema.
     *
     * @param ns
     * @param schemaLocation
     * @throws org.xml.sax.SAXException */
    public void importSchema( String ns, String schemaLocation ) throws SAXException {
        NGCCRuntimeEx newRuntime = new NGCCRuntimeEx(parser,false,this);
        InputSource source = resolveRelativeURL(ns,schemaLocation);
        if(source!=null)
            newRuntime.parseEntity( source, false, ns, getLocator() );
        // if source == null,
        // we can't locate this document. Let's just hope that
        // we already have the schema components for this schema
        // or we will receive them in the future.
    }

    /**
     * Called when a new document is being parsed and checks
     * if the document has already been parsed before.
     *
     * <p>
     * Used to avoid recursive inclusion. Note that the same
     * document will be parsed multiple times if they are for different
     * target namespaces.
     *
     * <h2>Document Graph Model</h2>
     * <p>
     * The challenge we are facing here is that you have a graph of
     * documents that reference each other. Each document has an unique
     * URI to identify themselves, and references are done by using those.
     * The graph may contain cycles.
     *
     * <p>
     * Our goal here is to parse all the documents in the graph, without
     * parsing the same document twice. This method implements this check.
     *
     * <p>
     * One complication is the chameleon schema; a document can be parsed
     * multiple times if they are under different target namespaces.
     *
     * <p>
     * Also, note that when you resolve relative URIs in the @schemaLocation,
     * their base URI is *NOT* the URI of the document.
     *
     * @return true if the document has already been processed and thus
     *      needs to be skipped.
     */
    public boolean hasAlreadyBeenRead() {
        if( documentSystemId!=null ) {
            if( documentSystemId.startsWith("file:///") )
                // change file:///abc to file:/abc
                // JDK File.toURL method produces the latter, but according to RFC
                // I don't think that's a valid URL. Since two different ways of
                // producing URLs could produce those two different forms,
                // we need to canonicalize one to the other.
                documentSystemId = "file:/"+documentSystemId.substring(8);
        } else {
            // if the system Id is not provided, we can't test the identity,
            // so we have no choice but to read it.
            // the newly created SchemaDocumentImpl will be unique one
        }

        assert document ==null;
        document = new SchemaDocumentImpl( currentSchema, documentSystemId );

        SchemaDocumentImpl existing = parser.parsedDocuments.get(document);
        if(existing==null) {
            parser.parsedDocuments.put(document,document);
        } else {
            document = existing;
        }

        assert document !=null;

        if(referer!=null) {
            assert referer.document !=null : "referer "+referer.documentSystemId+" has docIdentity==null";
            referer.document.references.add(this.document);
            this.document.referers.add(referer.document);
        }

        return existing!=null;
    }

    /**
     * Parses the specified entity.
     *
     * @param source
     * @param importLocation
     *      The source location of the import/include statement.
     *      Used for reporting errors.
     * @param includeMode
     * @param expectedNamespace
     * @throws org.xml.sax.SAXException
     */
    public void parseEntity( InputSource source, boolean includeMode, String expectedNamespace, Locator importLocation )
            throws SAXException {

        documentSystemId = source.getSystemId();
        try {
            Schema s = new Schema(this,includeMode,expectedNamespace);
            setRootHandler(s);
            try {
                parser.parser.parse(source,this, getErrorHandler(), parser.getEntityResolver());
            } catch( IOException fnfe ) {
                SAXParseException se = new SAXParseException(fnfe.toString(), importLocation, fnfe);
                parser.errorHandler.warning(se);
            }
        } catch( SAXException e ) {
            parser.setErrorFlag();
            throw e;
        }
    }

    /**
     * Creates a new instance of annotation parser.
     *
     * @return Annotation parser
     */
    public AnnotationParser createAnnotationParser() {
        if(parser.getAnnotationParserFactory()==null)
            return DefaultAnnotationParser.theInstance;
        else
            return parser.getAnnotationParserFactory().create();
    }

    /**
     * Gets the element name that contains the annotation element.This method works correctly only when called by the annotation handler.
     *
     * @return Element name
     */
    public String getAnnotationContextElementName() {
        return elementNames.get( elementNames.size()-2 );
    }

    /**
     * Creates a copy of the current locator object.
     *
     * @return Locator copy
     */
    public Locator copyLocator() {
        return new LocatorImpl(getLocator());
    }

    public ErrorHandler getErrorHandler() {
        return parser.errorHandler;
    }

    @Override
    public void onEnterElementConsumed(String uri, String localName, String qname, Attributes atts)
        throws SAXException {
        super.onEnterElementConsumed(uri, localName, qname, atts);
        elementNames.push(localName);
    }

    @Override
    public void onLeaveElementConsumed(String uri, String localName, String qname) throws SAXException {
        super.onLeaveElementConsumed(uri, localName, qname);
        elementNames.pop();
    }



//
//
// ValidationContext implementation
//
//
    // this object lives longer than the parser itself,
    // so it's important for this object not to have any reference
    // to the parser.
    private static class Context implements ValidationContext {
        Context( String _prefix, String _uri, Context _context ) {
            this.previous = _context;
            this.prefix = _prefix;
            this.uri = _uri;
        }

        @Override
        public String resolveNamespacePrefix(String p) {
            if(p.equals(prefix))    return uri;
            if(previous==null)      return null;
            else                    return previous.resolveNamespacePrefix(p);
        }

        private final String prefix;
        private final String uri;
        private final Context previous;

        // XSDLib don't use those methods, so we cut a corner here.
        @Override
        public String getBaseUri() { return null; }
        @Override
        public boolean isNotation(String arg0) { return false; }
        @Override
        public boolean isUnparsedEntity(String arg0) { return false; }
    }

    private Context currentContext=null;

    /** Returns an immutable snapshot of the current context.
     *
     * @return Snapshot of current context
     */
    public ValidationContext createValidationContext() {
        return currentContext;
    }

    public XmlString createXmlString(String value) {
        if(value==null)     return null;
        else    return new XmlString(value,createValidationContext());
    }

    @Override
    public void startPrefixMapping( String prefix, String uri ) throws SAXException {
        super.startPrefixMapping(prefix,uri);
        currentContext = new Context(prefix,uri,currentContext);
    }
    @Override
    public void endPrefixMapping( String prefix ) throws SAXException {
        super.endPrefixMapping(prefix);
        currentContext = currentContext.previous;
    }

//
//
// Utility functions
//
//

    /**
     * Parses UName under the given context.
     * @param qname Attribute name.
     * @return New {@link UName} instance based on attribute name.
     * @throws org.xml.sax.SAXException
     */
    public UName parseUName(final String qname ) throws SAXException {
        int idx = qname.indexOf(':');
        if(idx<0) {
            String uri = resolveNamespacePrefix("");

            // chamelon behavior. ugly...
            if( uri.equals("") && chameleonMode )
                uri = currentSchema.getTargetNamespace();

            // this is guaranteed to resolve
            return new UName(uri,qname,qname);
        } else {
            String prefix = qname.substring(0,idx);
            String uri = currentContext.resolveNamespacePrefix(prefix);
            if(uri==null) {
                // prefix failed to resolve.
                reportError(Messages.format(
                    Messages.ERR_UNDEFINED_PREFIX,prefix));
                uri="undefined"; // replace with a dummy
            }
            return new UName( uri, qname.substring(idx+1), qname );
        }
    }

    /**
     * Utility function for collapsing the namespaces inside qname declarations
     * and 'name' attribute values that should contain the qname values
     *
     * @param text String where whitespaces should be collapsed
     * @return String with whitespaces collapsed
     */
    public String collapse(String text) {
        return collapse((CharSequence) text).toString();
    }

    /**
     * returns true if the specified char is a white space character.
     */
    private final boolean isWhiteSpace(char ch) {
        // most of the characters are non-control characters.
        // so check that first to quickly return false for most of the cases.
        if (ch > 0x20) {
            return false;
        }

        // other than we have to do four comparisons.
        return ch == 0x9 || ch == 0xA || ch == 0xD || ch == 0x20;
    }

    /**
     * This is usually the biggest processing bottleneck.
     *
     */
    private CharSequence collapse(CharSequence text) {
        int len = text.length();

        // most of the texts are already in the collapsed form.
        // so look for the first whitespace in the hope that we will
        // never see it.
        int s = 0;
        while (s < len) {
            if (isWhiteSpace(text.charAt(s))) {
                break;
            }
            s++;
        }
        if (s == len) // the input happens to be already collapsed.
        {
            return text;
        }

        // we now know that the input contains spaces.
        // let's sit down and do the collapsing normally.
        StringBuilder result = new StringBuilder(len /*allocate enough size to avoid re-allocation*/);

        if (s != 0) {
            for (int i = 0; i < s; i++) {
                result.append(text.charAt(i));
            }
            result.append(' ');
        }

        boolean inStripMode = true;
        for (int i = s + 1; i < len; i++) {
            char ch = text.charAt(i);
            boolean b = isWhiteSpace(ch);
            if (inStripMode && b) {
                continue; // skip this character
            }
            inStripMode = b;
            if (inStripMode) {
                result.append(' ');
            } else {
                result.append(ch);
            }
        }

        // remove trailing whitespaces
        len = result.length();
        if (len > 0 && result.charAt(len - 1) == ' ') {
            result.setLength(len - 1);
        }
        // whitespaces are already collapsed,
        // so all we have to do is to remove the last one character
        // if it's a whitespace.

        return result;
    }

    public boolean parseBoolean(String v) {
        if(v==null) return false;
        v=v.trim();
        return v.equals("true") || v.equals("1");
    }


    @Override
    protected void unexpectedX(String token) throws SAXException {
        SAXParseException e = new SAXParseException(MessageFormat.format(
            "Unexpected {0} appears at line {1} column {2}",
                token,
                getLocator().getLineNumber(),
                getLocator().getColumnNumber()),
            getLocator());

        parser.errorHandler.fatalError(e);
        throw e;    // we will abort anyway
    }

    public ForeignAttributesImpl parseForeignAttributes( ForeignAttributesImpl next ) {
        ForeignAttributesImpl impl = new ForeignAttributesImpl(createValidationContext(),copyLocator(),next);

        Attributes atts = getCurrentAttributes();
        for( int i=0; i<atts.getLength(); i++ ) {
            if(atts.getURI(i).length()>0) {
                impl.addAttribute(
                    atts.getURI(i),
                    atts.getLocalName(i),
                    atts.getQName(i),
                    atts.getType(i),
                    atts.getValue(i)
                );
            }
        }

        return impl;
    }


    public static final String XMLSchemaNSURI = "http://www.w3.org/2001/XMLSchema";
}
