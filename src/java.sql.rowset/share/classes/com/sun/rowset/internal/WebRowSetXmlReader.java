/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.rowset.internal;

import java.net.URL;
import java.sql.*;
import javax.sql.*;
import java.io.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.*;

import com.sun.rowset.*;
import java.text.MessageFormat;
import javax.sql.rowset.*;
import javax.sql.rowset.spi.*;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * An implementation of the <code>XmlReader</code> interface, which
 * reads and parses an XML formatted <code>WebRowSet</code> object.
 * This implementation uses an <code>org.xml.sax.Parser</code> object
 * as its parser.
 */
public class WebRowSetXmlReader implements XmlReader, Serializable {
    // standard schema
    static final String WEBROWSET_XSD = "webrowset.xsd";
    private static final String VALIDATION_PROPERTY = "jdk.sql.rowset.webrowsetValidation";

    private JdbcRowSetResourceBundle resBundle;

    public WebRowSetXmlReader(){
        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Parses the given <code>WebRowSet</code> object, getting its input from
     * the given <code>java.io.Reader</code> object.  The parser will send
     * notifications of parse events to the rowset's
     * <code>XmlReaderDocHandler</code>, which will build the rowset as
     * an XML document.
     * <P>
     * This method is called internally by the method
     * <code>WebRowSet.readXml</code>.
     * <P>
     * If a parsing error occurs, the exception thrown will include
     * information for locating the error in the original XML document.
     *
     * @param caller the <code>WebRowSet</code> object to be parsed, whose
     *        <code>xmlReader</code> field must contain a reference to
     *        this <code>XmlReader</code> object
     * @param reader the <code>java.io.Reader</code> object from which
     *        the parser will get its input
     * @exception SQLException if a database access error occurs or
     *            this <code>WebRowSetXmlReader</code> object is not the
     *            reader for the given rowset
     * @see XmlReaderContentHandler
     */
    public void readXML(WebRowSet caller, java.io.Reader reader) throws SQLException {
        readXml(caller, new InputSource(reader));
    }


    /**
     * Parses the given <code>WebRowSet</code> object, getting its input from
     * the given <code>java.io.InputStream</code> object.  The parser will send
     * notifications of parse events to the rowset's
     * <code>XmlReaderDocHandler</code>, which will build the rowset as
     * an XML document.
     * <P>
     * Using streams is a much faster way than using <code>java.io.Reader</code>
     * <P>
     * This method is called internally by the method
     * <code>WebRowSet.readXml</code>.
     * <P>
     * If a parsing error occurs, the exception thrown will include
     * information for locating the error in the original XML document.
     *
     * @param caller the <code>WebRowSet</code> object to be parsed, whose
     *        <code>xmlReader</code> field must contain a reference to
     *        this <code>XmlReader</code> object
     * @param iStream the <code>java.io.InputStream</code> object from which
     *        the parser will get its input
     * @throws SQLException if a database access error occurs or
     *            this <code>WebRowSetXmlReader</code> object is not the
     *            reader for the given rowset
     * @see XmlReaderContentHandler
     */
    public void readXML(WebRowSet caller, java.io.InputStream iStream) throws SQLException {
        readXml(caller, new InputSource(iStream));
    }

    /**
     * Parses the supplied XML input into the given {@code WebRowSet}.
     *
     * The parser validates the XML against the standard WebRowSet schema by default.
     * Validation can be disabled by setting {@systemProperty jdk.sql.rowset.webrowsetValidation}
     * to {@code false} so that the parser can read legacy WebRowSet XML.
     *
     * @param caller the rowset to populate
     * @param is the SAX input source to parse
     * @throws SQLException if parsing or rowset population fails
     */
    private void readXml(WebRowSet caller, InputSource is) throws SQLException {
        try {
            boolean validating = validationEnabled();

            SAXParserFactory spf = SAXParserFactory.newDefaultNSInstance();
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            if (validating) {
                spf.setSchema(getSchema());
            }

            XMLReader reader = spf.newSAXParser().getXMLReader();
            reader.setEntityResolver(new XmlResolver());
            reader.setContentHandler(new XmlReaderContentHandler(caller, validating));
            reader.setErrorHandler(new XmlErrorHandler());
            reader.parse(is);
        } catch (SAXException | ParserConfigurationException | IOException err) {
            throw new SQLException(MessageFormat.format(resBundle.handleGetObject("wrsxmlreader.readxml").toString(),
                err.getMessage()), err);
        }
    }

    /**
     * Returns the standard WebRowSet schema bundled in the
     * {@code java.sql.rowset} module.
     *
     * A new schema is created for each call so parser instances do not share
     * validator implementation state. This can be revisited once the bundled
     * schema implementation is safe to share.
     *
     * @return the WebRowSet schema
     * @throws SAXException if the schema cannot be located or parsed
     */
    private Schema getSchema() throws SAXException {
        URL stdSchema = WebRowSet.class.getResource(WEBROWSET_XSD);
        if (stdSchema == null) {
            throw new SAXException(MessageFormat.format(resBundle.handleGetObject(
                    "wrsxmlreader.stdschema").toString(), WEBROWSET_XSD));
        }

        SchemaFactory sf = SchemaFactory.newDefaultInstance();
        sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return sf.newSchema(stdSchema);
    }

    /**
     * Returns whether WebRowSet XML validation is enabled.
     *
     * Validation is enabled by default. It is disabled only when the
     * {@systemProperty jdk.sql.rowset.webrowsetValidation} is set to
     * {@code false}, case-insensitive.
     *
     * @return {@code true} if XML should be schema-validated
     */
    private static boolean validationEnabled() {
        return !"false".equalsIgnoreCase(
                System.getProperty(VALIDATION_PROPERTY, "true").trim());
    }

    /**
     * For code coverage purposes only right now
     *
     */

    public void readData(RowSetInternal caller) {
    }

    /**
     * This method re populates the resBundle
     * during the deserialization process
     *
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Default state initialization happens here
        ois.defaultReadObject();
        // Initialization of transient Res Bundle happens here .
        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }

    }

    static final long serialVersionUID = -9127058392819008014L;
}
