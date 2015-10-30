/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.internalizer;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.XMLStreamReaderToContentHandler;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.xmlschema.parser.SchemaConstraintChecker;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;
import com.sun.xml.internal.bind.marshaller.DataWriter;
import com.sun.xml.internal.bind.v2.util.XmlFactory;
import com.sun.xml.internal.xsom.parser.JAXPParser;
import com.sun.xml.internal.xsom.parser.XMLParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

import static com.sun.xml.internal.bind.v2.util.XmlFactory.allowExternalAccess;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;


/**
 * Builds a DOM forest and maintains association from
 * system IDs to DOM trees.
 *
 * <p>
 * A forest is a transitive reflexive closure of referenced documents.
 * IOW, if a document is in a forest, all the documents referenced from
 * it is in a forest, too. To support this semantics, {@link DOMForest}
 * uses {@link InternalizationLogic} to find referenced documents.
 *
 * <p>
 * Some documents are marked as "root"s, meaning those documents were
 * put into a forest explicitly, not because it is referenced from another
 * document. (However, a root document can be referenced from other
 * documents, too.)
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class DOMForest {
    /** actual data storage {@code map<SystemId,Document>}. */
    private final Map<String,Document> core = new HashMap<String,Document>();

    /**
     * To correctly feed documents to a schema parser, we need to remember
     * which documents (of the forest) were given as the root
     * documents, and which of them are read as included/imported
     * documents.
     *
     * <p>
     * Set of system ids as strings.
     */
    private final Set<String> rootDocuments = new LinkedHashSet<String>();

    /** Stores location information for all the trees in this forest. */
    public final LocatorTable locatorTable = new LocatorTable();

    /** Stores all the outer-most {@code <jaxb:bindings>} customizations. */
    public final Set<Element> outerMostBindings = new HashSet<Element>();

    /** Used to resolve references to other schema documents. */
    private EntityResolver entityResolver = null;

    /** Errors encountered during the parsing will be sent to this object. */
    private ErrorReceiver errorReceiver = null;

    /** Schema language dependent part of the processing. */
    protected final InternalizationLogic logic;

    private final SAXParserFactory parserFactory;
    private final DocumentBuilder documentBuilder;

    private final Options options;

    public DOMForest(
        SAXParserFactory parserFactory, DocumentBuilder documentBuilder,
        InternalizationLogic logic ) {

        this.parserFactory = parserFactory;
        this.documentBuilder = documentBuilder;
        this.logic = logic;
        this.options = null;
    }

    public DOMForest( InternalizationLogic logic, Options opt ) {

        if (opt == null) throw new AssertionError("Options object null");
        this.options = opt;

        try {
            DocumentBuilderFactory dbf = XmlFactory.createDocumentBuilderFactory(opt.disableXmlSecurity);
            this.documentBuilder = dbf.newDocumentBuilder();
            this.parserFactory = XmlFactory.createParserFactory(opt.disableXmlSecurity);
        } catch( ParserConfigurationException e ) {
            throw new AssertionError(e);
        }

        this.logic = logic;
    }

    /**
     * Gets the DOM tree associated with the specified system ID,
     * or null if none is found.
     */
    public Document get( String systemId ) {
        Document doc = core.get(systemId);

        if( doc==null && systemId.startsWith("file:/") && !systemId.startsWith("file://") ) {
            // As of JDK1.4, java.net.URL.toExternal method returns URLs like
            // "file:/abc/def/ghi" which is an incorrect file protocol URL according to RFC1738.
            // Some other correctly functioning parts return the correct URLs ("file:///abc/def/ghi"),
            // and this descripancy breaks DOM look up by system ID.

            // this extra check solves this problem.
            doc = core.get( "file://"+systemId.substring(5) );
        }

        if( doc==null && systemId.startsWith("file:") ) {
            // on Windows, filenames are case insensitive.
            // perform case-insensitive search for improved user experience
            String systemPath = getPath(systemId);
            for (String key : core.keySet()) {
                if(key.startsWith("file:") && getPath(key).equalsIgnoreCase(systemPath)) {
                    doc = core.get(key);
                    break;
                }
            }
        }

        return doc;
    }

    /**
     * Strips off the leading 'file:///' portion from an URL.
     */
    private String getPath(String key) {
        key = key.substring(5); // skip 'file:'
        while(key.length()>0 && key.charAt(0)=='/') {
            key = key.substring(1);
        }
        return key;
    }

    /**
     * Returns a read-only set of root document system IDs.
     */
    public Set<String> getRootDocuments() {
        return Collections.unmodifiableSet(rootDocuments);
    }

    /**
     * Picks one document at random and returns it.
     */
    public Document getOneDocument() {
        for (Document dom : core.values()) {
            if (!dom.getDocumentElement().getNamespaceURI().equals(Const.JAXB_NSURI))
                return dom;
        }
        // we should have caught this error very early on
        throw new AssertionError();
    }

    /**
     * Checks the correctness of the XML Schema documents and return true
     * if it's OK.
     *
     * <p>
     * This method performs a weaker version of the tests where error messages
     * are provided without line number information. So whenever possible
     * use {@link SchemaConstraintChecker}.
     *
     * @see SchemaConstraintChecker
     */
    public boolean checkSchemaCorrectness(ErrorReceiver errorHandler) {
        try {
            boolean disableXmlSecurity = false;
            if (options != null) {
                disableXmlSecurity = options.disableXmlSecurity;
            }
            SchemaFactory sf = XmlFactory.createSchemaFactory(W3C_XML_SCHEMA_NS_URI, disableXmlSecurity);
            ErrorReceiverFilter filter = new ErrorReceiverFilter(errorHandler);
            sf.setErrorHandler(filter);
            Set<String> roots = getRootDocuments();
            Source[] sources = new Source[roots.size()];
            int i=0;
            for (String root : roots) {
                sources[i++] = new DOMSource(get(root),root);
            }
            sf.newSchema(sources);
            return !filter.hadError();
        } catch (SAXException e) {
            // the errors should have been reported
            return false;
        }
    }

    /**
     * Gets the system ID from which the given DOM is parsed.
     * <p>
     * Poor-man's base URI.
     */
    public String getSystemId( Document dom ) {
        for (Map.Entry<String,Document> e : core.entrySet()) {
            if (e.getValue() == dom)
                return e.getKey();
        }
        return null;
    }

    public Document parse( InputSource source, boolean root ) throws SAXException {
        if( source.getSystemId()==null )
            throw new IllegalArgumentException();

        return parse( source.getSystemId(), source, root );
    }

    /**
     * Parses an XML at the given location (
     * and XMLs referenced by it) into DOM trees
     * and stores them to this forest.
     *
     * @return the parsed DOM document object.
     */
    public Document parse( String systemId, boolean root ) throws SAXException, IOException {

        systemId = Options.normalizeSystemId(systemId);

        if( core.containsKey(systemId) )
            // this document has already been parsed. Just ignore.
            return core.get(systemId);

        InputSource is=null;

        // allow entity resolver to find the actual byte stream.
        if( entityResolver!=null )
            is = entityResolver.resolveEntity(null,systemId);
        if( is==null )
            is = new InputSource(systemId);

        // but we still use the original system Id as the key.
        return parse( systemId, is, root );
    }

    /**
     * Returns a {@link ContentHandler} to feed SAX events into.
     *
     * <p>
     * The client of this class can feed SAX events into the handler
     * to parse a document into this DOM forest.
     *
     * This version requires that the DOM object to be created and registered
     * to the map beforehand.
     */
    private ContentHandler getParserHandler( Document dom ) {
        ContentHandler handler = new DOMBuilder(dom,locatorTable,outerMostBindings);
        handler = new WhitespaceStripper(handler,errorReceiver,entityResolver);
        handler = new VersionChecker(handler,errorReceiver,entityResolver);

        // insert the reference finder so that
        // included/imported schemas will be also parsed
        XMLFilterImpl f = logic.createExternalReferenceFinder(this);
        f.setContentHandler(handler);

        if(errorReceiver!=null)
            f.setErrorHandler(errorReceiver);
        if(entityResolver!=null)
            f.setEntityResolver(entityResolver);

        return f;
    }

    public interface Handler extends ContentHandler {
        /**
         * Gets the DOM that was built.
         */
        public Document getDocument();
    }

    private static abstract class HandlerImpl extends XMLFilterImpl implements Handler {
    }

    /**
     * Returns a {@link ContentHandler} to feed SAX events into.
     *
     * <p>
     * The client of this class can feed SAX events into the handler
     * to parse a document into this DOM forest.
     */
    public Handler getParserHandler( String systemId, boolean root ) {
        final Document dom = documentBuilder.newDocument();
        core.put( systemId, dom );
        if(root)
            rootDocuments.add(systemId);

        ContentHandler handler = getParserHandler(dom);

        // we will register the DOM to the map once the system ID becomes available.
        // but the SAX allows the event source to not to provide that information,
        // so be prepared for such case.
        HandlerImpl x = new HandlerImpl() {
            public Document getDocument() {
                return dom;
            }
        };
        x.setContentHandler(handler);

        return x;
   }

    /**
     * Parses the given document and add it to the DOM forest.
     *
     * @return
     *      null if there was a parse error. otherwise non-null.
     */
    public Document parse( String systemId, InputSource inputSource, boolean root ) throws SAXException {
        Document dom = documentBuilder.newDocument();

        systemId = Options.normalizeSystemId(systemId);

        // put into the map before growing a tree, to
        // prevent recursive reference from causing infinite loop.
        core.put( systemId, dom );
        if(root)
            rootDocuments.add(systemId);

        try {
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            reader.setContentHandler(getParserHandler(dom));
            if(errorReceiver!=null)
                reader.setErrorHandler(errorReceiver);
            if(entityResolver!=null)
                reader.setEntityResolver(entityResolver);
            reader.parse(inputSource);
        } catch( ParserConfigurationException e ) {
            // in practice, this exception won't happen.
            errorReceiver.error(e.getMessage(),e);
            core.remove(systemId);
            rootDocuments.remove(systemId);
            return null;
        } catch( IOException e ) {
            errorReceiver.error(Messages.format(Messages.DOMFOREST_INPUTSOURCE_IOEXCEPTION, systemId, e.toString()),e);
            core.remove(systemId);
            rootDocuments.remove(systemId);
            return null;
        }

        return dom;
    }

    public Document parse( String systemId, XMLStreamReader parser, boolean root ) throws XMLStreamException {
        Document dom = documentBuilder.newDocument();

        systemId = Options.normalizeSystemId(systemId);

        if(root)
            rootDocuments.add(systemId);

        if(systemId==null)
            throw new IllegalArgumentException("system id cannot be null");
        core.put( systemId, dom );

        new XMLStreamReaderToContentHandler(parser,getParserHandler(dom),false,false).bridge();

        return dom;
    }

    /**
     * Performs internalization.
     *
     * This method should be called only once, only after all the
     * schemas are parsed.
     *
     * @return
     *      the returned bindings need to be applied after schema
     *      components are built.
     */
    public SCDBasedBindingSet transform(boolean enableSCD) {
        return Internalizer.transform(this, enableSCD, options.disableXmlSecurity);
    }

    /**
     * Performs the schema correctness check by using JAXP 1.3.
     *
     * <p>
     * This is "weak", because {@link SchemaFactory#newSchema(Source[])}
     * doesn't handle inclusions very correctly (it ends up parsing it
     * from its original source, not in this tree), and because
     * it doesn't handle two documents for the same namespace very
     * well.
     *
     * <p>
     * We should eventually fix JAXP (and Xerces), but meanwhile
     * this weaker and potentially wrong correctness check is still
     * better than nothing when used inside JAX-WS (JAXB CLI and Ant
     * does a better job of checking this.)
     *
     * <p>
     * To receive errors, use {@link SchemaFactory#setErrorHandler(ErrorHandler)}.
     */
    public void weakSchemaCorrectnessCheck(SchemaFactory sf) {
        List<SAXSource> sources = new ArrayList<SAXSource>();
        for( String systemId : getRootDocuments() ) {
            Document dom = get(systemId);
            if (dom.getDocumentElement().getNamespaceURI().equals(Const.JAXB_NSURI))
                continue;   // this isn't a schema. we have to do a negative check because if we see completely unrelated ns, we want to report that as an error

            SAXSource ss = createSAXSource(systemId);
            try {
                ss.getXMLReader().setFeature("http://xml.org/sax/features/namespace-prefixes",true);
            } catch (SAXException e) {
                throw new AssertionError(e);    // Xerces wants this. See 6395322.
            }
            sources.add(ss);
        }

        try {
            allowExternalAccess(sf, "file,http", options.disableXmlSecurity).newSchema(sources.toArray(new SAXSource[0]));
        } catch (SAXException e) {
            // error should have been reported.
        } catch (RuntimeException re) {
            // JAXP RI isn't very trustworthy when it comes to schema error check,
            // and we know some cases where it just dies with NPE. So handle it gracefully.
            // this masks a bug in the JAXP RI, but we need a release that we have to make.
            try {
                sf.getErrorHandler().warning(
                    new SAXParseException(Messages.format(
                        Messages.ERR_GENERAL_SCHEMA_CORRECTNESS_ERROR,re.getMessage()),
                        null,null,-1,-1,re));
            } catch (SAXException e) {
                // ignore
            }
        }
    }

    /**
     * Creates a {@link SAXSource} that, when parsed, reads from this {@link DOMForest}
     * (instead of parsing the original source identified by the system ID.)
     */
    public @NotNull SAXSource createSAXSource(String systemId) {
        ContentHandlerNamespacePrefixAdapter reader = new ContentHandlerNamespacePrefixAdapter(new XMLFilterImpl() {
            // XMLReader that uses XMLParser to parse. We need to use XMLFilter to indrect
            // handlers, since SAX allows handlers to be changed while parsing.
            @Override
            public void parse(InputSource input) throws SAXException, IOException {
                createParser().parse(input, this, this, this);
            }

            @Override
            public void parse(String systemId) throws SAXException, IOException {
                parse(new InputSource(systemId));
            }
        });

        return new SAXSource(reader,new InputSource(systemId));
    }

    /**
     * Creates {@link XMLParser} for XSOM which reads documents from
     * this DOMForest rather than doing a fresh parse.
     *
     * The net effect is that XSOM will read transformed XML Schemas
     * instead of the original documents.
     */
    public XMLParser createParser() {
        return new DOMForestParser(this, new JAXPParser(XmlFactory.createParserFactory(options.disableXmlSecurity)));
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    public ErrorReceiver getErrorHandler() {
        return errorReceiver;
    }

    public void setErrorHandler(ErrorReceiver errorHandler) {
        this.errorReceiver = errorHandler;
    }

    /**
     * Gets all the parsed documents.
     */
    public Document[] listDocuments() {
        return core.values().toArray(new Document[core.size()]);
    }

    /**
     * Gets all the system IDs of the documents.
     */
    public String[] listSystemIDs() {
        return core.keySet().toArray(new String[core.keySet().size()]);
    }

    /**
     * Dumps the contents of the forest to the specified stream.
     *
     * This is a debug method. As such, error handling is sloppy.
     */
    @SuppressWarnings("CallToThreadDumpStack")
    public void dump( OutputStream out ) throws IOException {
        try {
            // create identity transformer
            boolean disableXmlSecurity = false;
            if (options != null) {
                disableXmlSecurity = options.disableXmlSecurity;
            }
            TransformerFactory tf = XmlFactory.createTransformerFactory(disableXmlSecurity);
            Transformer it = tf.newTransformer();

            for (Map.Entry<String, Document> e : core.entrySet()) {
                out.write( ("---<< "+e.getKey()+'\n').getBytes() );

                DataWriter dw = new DataWriter(new OutputStreamWriter(out),null);
                dw.setIndentStep("  ");
                it.transform( new DOMSource(e.getValue()),
                    new SAXResult(dw));

                out.write( "\n\n\n".getBytes() );
            }
        } catch( TransformerException e ) {
            e.printStackTrace();
        }
    }
}
