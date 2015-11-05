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

// @@3RD PARTY CODE@@

// XMLWriter.java - serialize an XML document.
// Written by David Megginson, david@megginson.com
// NO WARRANTY!  This class is in the public domain.

// Id: XMLWriter.java,v 1.5 2000/09/17 01:08:16 david Exp

package com.sun.xml.internal.bind.marshaller;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;


/**
 * Filter to write an XML document from a SAX event stream.
 *
 * <p>This class can be used by itself or as part of a SAX event
 * stream: it takes as input a series of SAX2 ContentHandler
 * events and uses the information in those events to write
 * an XML document.  Since this class is a filter, it can also
 * pass the events on down a filter chain for further processing
 * (you can use the XMLWriter to take a snapshot of the current
 * state at any point in a filter chain), and it can be
 * used directly as a ContentHandler for a SAX2 XMLReader.</p>
 *
 * <p>The client creates a document by invoking the methods for
 * standard SAX2 events, always beginning with the
 * {@link #startDocument startDocument} method and ending with
 * the {@link #endDocument endDocument} method.  There are convenience
 * methods provided so that clients to not have to create empty
 * attribute lists or provide empty strings as parameters; for
 * example, the method invocation</p>
 *
 * <pre>
 * w.startElement("foo");
 * </pre>
 *
 * <p>is equivalent to the regular SAX2 ContentHandler method</p>
 *
 * <pre>
 * w.startElement("", "foo", "", new AttributesImpl());
 * </pre>
 *
 * <p>Except that it is more efficient because it does not allocate
 * a new empty attribute list each time.  The following code will send
 * a simple XML document to standard output:</p>
 *
 * <pre>
 * XMLWriter w = new XMLWriter();
 *
 * w.startDocument();
 * w.startElement("greeting");
 * w.characters("Hello, world!");
 * w.endElement("greeting");
 * w.endDocument();
 * </pre>
 *
 * <p>The resulting document will look like this:</p>
 *
 * <pre>{@code
 * <?xml version="1.0" standalone="yes"?>
 *
 * <greeting>Hello, world!</greeting>
 * }</pre>
 *
 * <p>In fact, there is an even simpler convenience method,
 * <var>dataElement</var>, designed for writing elements that
 * contain only character data, so the code to generate the
 * document could be shortened to</p>
 *
 * <pre>
 * XMLWriter w = new XMLWriter();
 *
 * w.startDocument();
 * w.dataElement("greeting", "Hello, world!");
 * w.endDocument();
 * </pre>
 *
 * <h2>Whitespace</h2>
 *
 * <p>According to the XML Recommendation, <em>all</em> whitespace
 * in an XML document is potentially significant to an application,
 * so this class never adds newlines or indentation.  If you
 * insert three elements in a row, as in</p>
 *
 * <pre>
 * w.dataElement("item", "1");
 * w.dataElement("item", "2");
 * w.dataElement("item", "3");
 * </pre>
 *
 * <p>you will end up with</p>
 *
 * <pre>{@code
 * <item>1</item><item>3</item><item>3</item>
 * }</pre>
 *
 * <p>You need to invoke one of the <var>characters</var> methods
 * explicitly to add newlines or indentation.  Alternatively, you
 * can use {@link DataWriter}, which
 * is derived from this class -- it is optimized for writing
 * purely data-oriented (or field-oriented) XML, and does automatic
 * linebreaks and indentation (but does not support mixed content
 * properly).</p>
 *
 *
 * <h2>Namespace Support</h2>
 *
 * <p>The writer contains extensive support for XML Namespaces, so that
 * a client application does not have to keep track of prefixes and
 * supply <var>xmlns</var> attributes.  By default, the XML writer will
 * generate Namespace declarations in the form _NS1, _NS2, etc., wherever
 * they are needed, as in the following example:</p>
 *
 * <pre>
 * w.startDocument();
 * w.emptyElement("http://www.foo.com/ns/", "foo");
 * w.endDocument();
 * </pre>
 *
 * <p>The resulting document will look like this:</p>
 *
 * <pre>{@code
 * <?xml version="1.0" standalone="yes"?>
 *
 * <_NS1:foo xmlns:_NS1="http://www.foo.com/ns/"/>
 * }</pre>
 *
 * <p>In many cases, document authors will prefer to choose their
 * own prefixes rather than using the (ugly) default names.  The
 * XML writer allows two methods for selecting prefixes:</p>
 *
 * <ol>
 * <li>the qualified name</li>
 * </ol>
 *
 * <p>Whenever the XML writer finds a new Namespace URI, it checks
 * to see if a qualified (prefixed) name is also available; if so
 * it attempts to use the name's prefix (as long as the prefix is
 * not already in use for another Namespace URI).</p>
 *
 * <p>The resulting document will look like this:</p>
 *
 * <pre>{@code
 * <?xml version="1.0" standalone="yes"?>
 *
 * <foo:foo xmlns:foo="http://www.foo.com/ns/"/>
 * }</pre>
 *
 * <p>The default Namespace simply uses an empty string as the prefix:</p>
 *
 * <pre>
 * w.setPrefix("http://www.foo.com/ns/", "");
 * w.startDocument();
 * w.emptyElement("http://www.foo.com/ns/", "foo");
 * w.endDocument();
 * </pre>
 *
 * <p>The resulting document will look like this:</p>
 *
 * <pre>{@code
 * <?xml version="1.0" standalone="yes"?>
 *
 * <foo xmlns="http://www.foo.com/ns/"/>
 * }</pre>
 *
 * <p>By default, the XML writer will not declare a Namespace until
 * it is actually used.  Sometimes, this approach will create
 * a large number of Namespace declarations, as in the following
 * example:</p>
 *
 * <pre>{@code
 * <xml version="1.0" standalone="yes"?>
 *
 * <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
 *  <rdf:Description about="http://www.foo.com/ids/books/12345">
 *   <dc:title xmlns:dc="http://www.purl.org/dc/">A Dark Night</dc:title>
 *   <dc:creator xmlns:dc="http://www.purl.org/dc/">Jane Smith</dc:title>
 *   <dc:date xmlns:dc="http://www.purl.org/dc/">2000-09-09</dc:title>
 *  </rdf:Description>
 * </rdf:RDF>
 * }</pre>
 *
 * <p>The "rdf" prefix is declared only once, because the RDF Namespace
 * is used by the root element and can be inherited by all of its
 * descendants; the "dc" prefix, on the other hand, is declared three
 * times, because no higher element uses the Namespace.  To solve this
 * problem, you can instruct the XML writer to predeclare Namespaces
 * on the root element even if they are not used there:</p>
 *
 * <pre>
 * w.forceNSDecl("http://www.purl.org/dc/");
 * </pre>
 *
 * <p>Now, the "dc" prefix will be declared on the root element even
 * though it's not needed there, and can be inherited by its
 * descendants:</p>
 *
 * <pre>{@code
 * <xml version="1.0" standalone="yes"?>
 *
 * <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
 *             xmlns:dc="http://www.purl.org/dc/">
 *  <rdf:Description about="http://www.foo.com/ids/books/12345">
 *   <dc:title>A Dark Night</dc:title>
 *   <dc:creator>Jane Smith</dc:title>
 *   <dc:date>2000-09-09</dc:title>
 *  </rdf:Description>
 * </rdf:RDF>
 * }</pre>
 *
 * <p>This approach is also useful for declaring Namespace prefixes
 * that be used by qualified names appearing in attribute values or
 * character data.</p>
 *
 * @author David Megginson, david@megginson.com
 * @version 0.2
 * @since JAXB 1.0
 * @see org.xml.sax.XMLFilter
 * @see org.xml.sax.ContentHandler
 */
public class XMLWriter extends XMLFilterImpl
{

    ////////////////////////////////////////////////////////////////////
    // Constructors.
    ////////////////////////////////////////////////////////////////////




    /**
     * Create a new XML writer.
     *
     * <p>Write to the writer provided.</p>
     *
     * @param writer
     *      The output destination, or null to use standard output.
     * @param encoding
     *      If non-null string is specified, it is written as a part
     *      of the XML declaration.
     */
    public XMLWriter (Writer writer, String encoding, CharacterEscapeHandler _escapeHandler )
    {
        init(writer,encoding);
        this.escapeHandler = _escapeHandler;
    }

    public XMLWriter (Writer writer, String encoding ) {
        this( writer, encoding, DumbEscapeHandler.theInstance );
    }



    /**
     * Internal initialization method.
     *
     * <p>All of the public constructors invoke this method.
     *
     * @param writer The output destination, or null to use
     *        standard output.
     */
    private void init (Writer writer,String encoding)
    {
        setOutput(writer,encoding);
    }



    ////////////////////////////////////////////////////////////////////
    // Public methods.
    ////////////////////////////////////////////////////////////////////


    /**
     * Reset the writer.
     *
     * <p>This method is especially useful if the writer throws an
     * exception before it is finished, and you want to reuse the
     * writer for a new document.  It is usually a good idea to
     * invoke {@link #flush flush} before resetting the writer,
     * to make sure that no output is lost.</p>
     *
     * <p>This method is invoked automatically by the
     * {@link #startDocument startDocument} method before writing
     * a new document.</p>
     *
     * <p><strong>Note:</strong> this method will <em>not</em>
     * clear the prefix or URI information in the writer or
     * the selected output writer.</p>
     *
     * @see #flush()
     */
    public void reset ()
    {
        elementLevel = 0;
        startTagIsClosed = true;
    }


    /**
     * Flush the output.
     *
     * <p>This method flushes the output stream.  It is especially useful
     * when you need to make certain that the entire document has
     * been written to output but do not want to close the output
     * stream.</p>
     *
     * <p>This method is invoked automatically by the
     * {@link #endDocument endDocument} method after writing a
     * document.</p>
     *
     * @see #reset()
     */
    public void flush ()
        throws IOException
    {
        output.flush();
    }


    /**
     * Set a new output destination for the document.
     *
     * @param writer The output destination, or null to use
     *        standard output.
     * @see #flush()
     */
    public void setOutput (Writer writer,String _encoding)
    {
        if (writer == null) {
            output = new OutputStreamWriter(System.out);
        } else {
            output = writer;
        }
        encoding = _encoding;
    }

    /**
     * Set whether the writer should print out the XML declaration
     * ({@code <?xml version='1.0' ... ?>}).
     * <p>
     * This option is set to true by default.
     */
    public void setXmlDecl( boolean _writeXmlDecl ) {
        this.writeXmlDecl = _writeXmlDecl;
    }

    /**
     * Sets the header string.
     *
     * This string will be written right after the xml declaration
     * without any escaping. Useful for generating a boiler-plate
     * DOCTYPE decl, PIs, and comments.
     *
     * @param _header
     *      passing null will work as if the empty string is passed.
     */
    public void setHeader( String _header ) {
        this.header = _header;
    }


    private final HashMap<String,String> locallyDeclaredPrefix = new HashMap<String,String>();
    public void startPrefixMapping( String prefix, String uri ) throws SAXException {
        locallyDeclaredPrefix.put(prefix,uri);
    }


    ////////////////////////////////////////////////////////////////////
    // Methods from org.xml.sax.ContentHandler.
    ////////////////////////////////////////////////////////////////////

    /**
     * Write the XML declaration at the beginning of the document.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the XML declaration, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#startDocument()
     */
    public void startDocument ()
        throws SAXException
    {
        try {
            reset();

            if(writeXmlDecl) {
                String e="";
                if(encoding!=null)
                    e = " encoding=\""+encoding+'\"';

                writeXmlDecl("<?xml version=\"1.0\""+e +" standalone=\"yes\"?>");
            }

            if(header!=null)
                write(header);

            super.startDocument();
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }

    protected void writeXmlDecl(String decl) throws IOException {
        write(decl);
    }


    /**
     * Write a newline at the end of the document.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the newline, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#endDocument()
     */
    public void endDocument ()
        throws SAXException
    {
        try {
            super.endDocument();
            flush();
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }


    /**
     * Write a start tag.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @param uri The Namespace URI, or the empty string if none
     *        is available.
     * @param localName The element's local (unprefixed) name (required).
     * @param qName The element's qualified (prefixed) name, or the
     *        empty string is none is available.  This method will
     *        use the qName as a template for generating a prefix
     *        if necessary, but it is not guaranteed to use the
     *        same qName.
     * @param atts The element's attribute list (must not be null).
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the start tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    public void startElement (String uri, String localName,
                              String qName, Attributes atts)
        throws SAXException
    {
        try {
            if (!startTagIsClosed) {
                write(">");
            }
            elementLevel++;
//            nsSupport.pushContext();

            write('<');
            write(qName);
            writeAttributes(atts);

            // declare namespaces specified by the startPrefixMapping methods
            if(!locallyDeclaredPrefix.isEmpty()) {
                for (Map.Entry<String,String> e : locallyDeclaredPrefix.entrySet()) {
                    String p = e.getKey();
                    String u = e.getValue();
                    if (u == null) {
                        u = "";
                    }
                    write(' ');
                    if ("".equals(p)) {
                        write("xmlns=\"");
                    } else {
                        write("xmlns:");
                        write(p);
                        write("=\"");
                    }
                    char ch[] = u.toCharArray();
                    writeEsc(ch, 0, ch.length, true);
                    write('\"');
                }
                locallyDeclaredPrefix.clear();  // clear the contents
            }

//            if (elementLevel == 1) {
//                forceNSDecls();
//            }
//            writeNSDecls();
            super.startElement(uri, localName, qName, atts);
            startTagIsClosed = false;
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }


    /**
     * Write an end tag.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @param uri The Namespace URI, or the empty string if none
     *        is available.
     * @param localName The element's local (unprefixed) name (required).
     * @param qName The element's qualified (prefixed) name, or the
     *        empty string is none is available.  This method will
     *        use the qName as a template for generating a prefix
     *        if necessary, but it is not guaranteed to use the
     *        same qName.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the end tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    public void endElement (String uri, String localName, String qName)
        throws SAXException
    {
        try {
            if (startTagIsClosed) {
                write("</");
                write(qName);
                write('>');
            } else {
                write("/>");
                startTagIsClosed = true;
            }
            super.endElement(uri, localName, qName);
//            nsSupport.popContext();
            elementLevel--;
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }


    /**
     * Write character data.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @param ch The array of characters to write.
     * @param start The starting position in the array.
     * @param len The number of characters to write.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the characters, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    public void characters (char ch[], int start, int len)
        throws SAXException
    {
        try {
            if (!startTagIsClosed) {
                write('>');
                startTagIsClosed = true;
            }
            writeEsc(ch, start, len, false);
            super.characters(ch, start, len);
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }


    /**
     * Write ignorable whitespace.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @param ch The array of characters to write.
     * @param start The starting position in the array.
     * @param length The number of characters to write.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the whitespace, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
     */
    public void ignorableWhitespace (char ch[], int start, int length)
        throws SAXException
    {
        try {
            writeEsc(ch, start, length, false);
            super.ignorableWhitespace(ch, start, length);
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }



    /**
     * Write a processing instruction.
     *
     * Pass the event on down the filter chain for further processing.
     *
     * @param target The PI target.
     * @param data The PI data.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the PI, or if a handler further down
     *            the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
     */
    public void processingInstruction (String target, String data)
        throws SAXException
    {
        try {
            if (!startTagIsClosed) {
                write('>');
                startTagIsClosed = true;
            }
            write("<?");
            write(target);
            write(' ');
            write(data);
            write("?>");
            if (elementLevel < 1) {
                write('\n');
            }
            super.processingInstruction(target, data);
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }



    ////////////////////////////////////////////////////////////////////
    // Convenience methods.
    ////////////////////////////////////////////////////////////////////



    /**
     * Start a new element without a qname or attributes.
     *
     * <p>This method will provide a default empty attribute
     * list and an empty string for the qualified name.
     * It invokes {@link
     * #startElement(String, String, String, Attributes)}
     * directly.</p>
     *
     * @param uri The element's Namespace URI.
     * @param localName The element's local name.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the start tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public void startElement (String uri, String localName)
        throws SAXException
    {
        startElement(uri, localName, "", EMPTY_ATTS);
    }


    /**
     * Start a new element without a qname, attributes or a Namespace URI.
     *
     * <p>This method will provide an empty string for the
     * Namespace URI, and empty string for the qualified name,
     * and a default empty attribute list. It invokes
     * #startElement(String, String, String, Attributes)}
     * directly.</p>
     *
     * @param localName The element's local name.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the start tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public void startElement (String localName)
        throws SAXException
    {
        startElement("", localName, "", EMPTY_ATTS);
    }


    /**
     * End an element without a qname.
     *
     * <p>This method will supply an empty string for the qName.
     * It invokes {@link #endElement(String, String, String)}
     * directly.</p>
     *
     * @param uri The element's Namespace URI.
     * @param localName The element's local name.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the end tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #endElement(String, String, String)
     */
    public void endElement (String uri, String localName)
        throws SAXException
    {
        endElement(uri, localName, "");
    }


    /**
     * End an element without a Namespace URI or qname.
     *
     * <p>This method will supply an empty string for the qName
     * and an empty string for the Namespace URI.
     * It invokes {@link #endElement(String, String, String)}
     * directly.</p>
     *
     * @param localName The element's local name.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the end tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #endElement(String, String, String)
     */
    public void endElement (String localName)
        throws SAXException
    {
        endElement("", localName, "");
    }


    /**
     * Write an element with character data content.
     *
     * <p>This is a convenience method to write a complete element
     * with character data content, including the start tag
     * and end tag.</p>
     *
     * <p>This method invokes
     * {@link #startElement(String, String, String, Attributes)},
     * followed by
     * {@link #characters(String)}, followed by
     * {@link #endElement(String, String, String)}.</p>
     *
     * @param uri The element's Namespace URI.
     * @param localName The element's local name.
     * @param qName The element's default qualified name.
     * @param atts The element's attributes.
     * @param content The character data content.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the empty tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     * @see #characters(String)
     * @see #endElement(String, String, String)
     */
    public void dataElement (String uri, String localName,
                             String qName, Attributes atts,
                             String content)
        throws SAXException
    {
        startElement(uri, localName, qName, atts);
        characters(content);
        endElement(uri, localName, qName);
    }


    /**
     * Write an element with character data content but no attributes.
     *
     * <p>This is a convenience method to write a complete element
     * with character data content, including the start tag
     * and end tag.  This method provides an empty string
     * for the qname and an empty attribute list.</p>
     *
     * <p>This method invokes
     * {@link #startElement(String, String, String, Attributes)},
     * followed by
     * {@link #characters(String)}, followed by
     * {@link #endElement(String, String, String)}.</p>
     *
     * @param uri The element's Namespace URI.
     * @param localName The element's local name.
     * @param content The character data content.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the empty tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     * @see #characters(String)
     * @see #endElement(String, String, String)
     */
    public void dataElement (String uri, String localName, String content)
        throws SAXException
    {
        dataElement(uri, localName, "", EMPTY_ATTS, content);
    }


    /**
     * Write an element with character data content but no attributes or Namespace URI.
     *
     * <p>This is a convenience method to write a complete element
     * with character data content, including the start tag
     * and end tag.  The method provides an empty string for the
     * Namespace URI, and empty string for the qualified name,
     * and an empty attribute list.</p>
     *
     * <p>This method invokes
     * {@link #startElement(String, String, String, Attributes)},
     * followed by
     * {@link #characters(String)}, followed by
     * {@link #endElement(String, String, String)}.</p>
     *
     * @param localName The element's local name.
     * @param content The character data content.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the empty tag, or if a handler further down
     *            the filter chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     * @see #characters(String)
     * @see #endElement(String, String, String)
     */
    public void dataElement (String localName, String content)
        throws SAXException
    {
        dataElement("", localName, "", EMPTY_ATTS, content);
    }


    /**
     * Write a string of character data, with XML escaping.
     *
     * <p>This is a convenience method that takes an XML
     * String, converts it to a character array, then invokes
     * {@link #characters(char[], int, int)}.</p>
     *
     * @param data The character data.
     * @exception org.xml.sax.SAXException If there is an error
     *            writing the string, or if a handler further down
     *            the filter chain raises an exception.
     * @see #characters(char[], int, int)
     */
    public void characters (String data) throws SAXException {
        try {
            if (!startTagIsClosed) {
                write('>');
                startTagIsClosed = true;
            }
            char ch[] = data.toCharArray();
            characters(ch, 0, ch.length);
        } catch( IOException e ) {
            throw new SAXException(e);
        }
    }



    ////////////////////////////////////////////////////////////////////
    // Internal methods.
    ////////////////////////////////////////////////////////////////////




    /**
     * Write a raw character.
     *
     * @param c The character to write.
     */
    protected final void write (char c) throws IOException {
        output.write(c);
    }


    /**
     * Write a raw string.
     */
    protected final void write(String s) throws IOException {
        output.write(s);
    }


    /**
     * Write out an attribute list, escaping values.
     *
     * The names will have prefixes added to them.
     *
     * @param atts The attribute list to write.
     */
    private void writeAttributes (Attributes atts) throws IOException {
        int len = atts.getLength();
        for (int i = 0; i < len; i++) {
            char ch[] = atts.getValue(i).toCharArray();
            write(' ');
            write(atts.getQName(i));
            write("=\"");
            writeEsc(ch, 0, ch.length, true);
            write('"');
        }
    }


    /**
     * Write an array of data characters with escaping.
     *
     * @param ch The array of characters.
     * @param start The starting position.
     * @param length The number of characters to use.
     * @param isAttVal true if this is an attribute value literal.
     */
    private void writeEsc (char ch[], int start,
                             int length, boolean isAttVal)
        throws IOException
    {
        escapeHandler.escape(ch, start, length, isAttVal, output);
    }



    ////////////////////////////////////////////////////////////////////
    // Constants.
    ////////////////////////////////////////////////////////////////////

    private final Attributes EMPTY_ATTS = new AttributesImpl();



    ////////////////////////////////////////////////////////////////////
    // Internal state.
    ////////////////////////////////////////////////////////////////////

    private int elementLevel = 0;
    private Writer output;
    private String encoding;
    private boolean writeXmlDecl = true;
    /**
     * This string will be written right after the xml declaration
     * without any escaping. Useful for generating a boiler-plate DOCTYPE decl
     * , PIs, and comments.
     */
    private String header=null;

    private final CharacterEscapeHandler escapeHandler;

    private boolean startTagIsClosed = true;
}

// end of XMLWriter.java
