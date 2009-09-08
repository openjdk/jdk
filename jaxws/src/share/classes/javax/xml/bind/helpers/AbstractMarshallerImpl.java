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
package javax.xml.bind.helpers;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
// J2SE1.4 feature
// import java.nio.charset.Charset;
// import java.nio.charset.UnsupportedCharsetException;

/**
 * Partial default <tt>Marshaller</tt> implementation.
 *
 * <p>
 * This class provides a partial default implementation for the
 * {@link javax.xml.bind.Marshaller} interface.
 *
 * <p>
 * The only methods that a JAXB Provider has to implement are
 * {@link Marshaller#marshal(Object, javax.xml.transform.Result) marshal(Object, javax.xml.transform.Result)},
 * {@link Marshaller#marshal(Object, javax.xml.transform.Result) marshal(Object, javax.xml.stream.XMLStreamWriter)}, and
 * {@link Marshaller#marshal(Object, javax.xml.transform.Result) marshal(Object, javax.xml.stream.XMLEventWriter)}.
 *
 * @author <ul><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li></ul>
 * @version $Revision: 1.8 $ $Date: 2006/08/28 17:56:38 $
 * @see javax.xml.bind.Marshaller
 * @since JAXB1.0
 */
public abstract class AbstractMarshallerImpl implements Marshaller
{
    /** handler that will be used to process errors and warnings during marshal */
    private ValidationEventHandler eventHandler =
        new DefaultValidationEventHandler();

    //J2SE1.4 feature
    //private Charset encoding = null;

    /** store the value of the encoding property. */
    private String encoding = "UTF-8";

    /** store the value of the schemaLocation property. */
    private String schemaLocation = null;

    /** store the value of the noNamespaceSchemaLocation property. */
    private String noNSSchemaLocation = null;

    /** store the value of the formattedOutput property. */
    private boolean formattedOutput = false;

    /** store the value of the fragment property. */
    private boolean fragment = false;

    public final void marshal( Object obj, java.io.OutputStream os )
        throws JAXBException {

        checkNotNull( obj, "obj", os, "os" );
        marshal( obj, new StreamResult(os) );
    }

    public void marshal(Object jaxbElement, File output) throws JAXBException {
        checkNotNull(jaxbElement, "jaxbElement", output, "output" );
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(output));
            try {
                marshal( jaxbElement, new StreamResult(os) );
            } finally {
                os.close();
            }
        } catch (IOException e) {
            throw new JAXBException(e);
        }
    }

    public final void marshal( Object obj, java.io.Writer w )
        throws JAXBException {

        checkNotNull( obj, "obj", w, "writer" );
        marshal( obj, new StreamResult(w) );
    }

    public final void marshal( Object obj, org.xml.sax.ContentHandler handler )
        throws JAXBException {

        checkNotNull( obj, "obj", handler, "handler" );
        marshal( obj, new SAXResult(handler) );
    }

    public final void marshal( Object obj, org.w3c.dom.Node node )
        throws JAXBException {

        checkNotNull( obj, "obj", node, "node" );
        marshal( obj, new DOMResult(node) );
    }

    /**
     * By default, the getNode method is unsupported and throw
     * an {@link java.lang.UnsupportedOperationException}.
     *
     * Implementations that choose to support this method must
     * override this method.
     */
    public org.w3c.dom.Node getNode( Object obj ) throws JAXBException {

        checkNotNull( obj, "obj", Boolean.TRUE, "foo" );

        throw new UnsupportedOperationException();
    }

    /**
     * Convenience method for getting the current output encoding.
     *
     * @return the current encoding or "UTF-8" if it hasn't been set.
     */
    protected String getEncoding() {
        return encoding;
    }

    /**
     * Convenience method for setting the output encoding.
     *
     * @param encoding a valid encoding as specified in the Marshaller class
     * documentation
     */
    protected void setEncoding( String encoding ) {
        this.encoding = encoding;
    }

    /**
     * Convenience method for getting the current schemaLocation.
     *
     * @return the current schemaLocation or null if it hasn't been set
     */
    protected String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Convenience method for setting the schemaLocation.
     *
     * @param location the schemaLocation value
     */
    protected void setSchemaLocation( String location ) {
        schemaLocation = location;
    }

    /**
     * Convenience method for getting the current noNamespaceSchemaLocation.
     *
     * @return the current noNamespaceSchemaLocation or null if it hasn't
     * been set
     */
    protected String getNoNSSchemaLocation() {
        return noNSSchemaLocation;
    }

    /**
     * Convenience method for setting the noNamespaceSchemaLocation.
     *
     * @param location the noNamespaceSchemaLocation value
     */
    protected void setNoNSSchemaLocation( String location ) {
        noNSSchemaLocation = location;
    }

    /**
     * Convenience method for getting the formatted output flag.
     *
     * @return the current value of the formatted output flag or false if
     * it hasn't been set.
     */
    protected boolean isFormattedOutput() {
        return formattedOutput;
    }

    /**
     * Convenience method for setting the formatted output flag.
     *
     * @param v value of the formatted output flag.
     */
    protected void setFormattedOutput( boolean v ) {
        formattedOutput = v;
    }


    /**
     * Convenience method for getting the fragment flag.
     *
     * @return the current value of the fragment flag or false if
     * it hasn't been set.
     */
    protected boolean isFragment() {
        return fragment;
    }

    /**
     * Convenience method for setting the fragment flag.
     *
     * @param v value of the fragment flag.
     */
    protected void setFragment( boolean v ) {
        fragment = v;
    }


    static String[] aliases = {
        "UTF-8", "UTF8",
        "UTF-16", "Unicode",
        "UTF-16BE", "UnicodeBigUnmarked",
        "UTF-16LE", "UnicodeLittleUnmarked",
        "US-ASCII", "ASCII",
        "TIS-620", "TIS620",

        // taken from the project-X parser
        "ISO-10646-UCS-2", "Unicode",

        "EBCDIC-CP-US", "cp037",
        "EBCDIC-CP-CA", "cp037",
        "EBCDIC-CP-NL", "cp037",
        "EBCDIC-CP-WT", "cp037",

        "EBCDIC-CP-DK", "cp277",
        "EBCDIC-CP-NO", "cp277",
        "EBCDIC-CP-FI", "cp278",
        "EBCDIC-CP-SE", "cp278",

        "EBCDIC-CP-IT", "cp280",
        "EBCDIC-CP-ES", "cp284",
        "EBCDIC-CP-GB", "cp285",
        "EBCDIC-CP-FR", "cp297",

        "EBCDIC-CP-AR1", "cp420",
        "EBCDIC-CP-HE", "cp424",
        "EBCDIC-CP-BE", "cp500",
        "EBCDIC-CP-CH", "cp500",

        "EBCDIC-CP-ROECE", "cp870",
        "EBCDIC-CP-YU", "cp870",
        "EBCDIC-CP-IS", "cp871",
        "EBCDIC-CP-AR2", "cp918",

        // IANA also defines two that JDK 1.2 doesn't handle:
        //  EBCDIC-CP-GR        --> CP423
        //  EBCDIC-CP-TR        --> CP905
    };

    /**
     * Gets the corresponding Java encoding name from an IANA name.
     *
     * This method is a helper method for the derived class to convert
     * encoding names.
     *
     * @exception UnsupportedEncodingException
     *      If this implementation couldn't find the Java encoding name.
     */
    protected String getJavaEncoding( String encoding ) throws UnsupportedEncodingException {
        try {
            "1".getBytes(encoding);
            return encoding;
        } catch( UnsupportedEncodingException e ) {
            // try known alias
            for( int i=0; i<aliases.length; i+=2 ) {
                if(encoding.equals(aliases[i])) {
                    "1".getBytes(aliases[i+1]);
                    return aliases[i+1];
                }
            }

            throw new UnsupportedEncodingException(encoding);
        }
        /* J2SE1.4 feature
        try {
            this.encoding = Charset.forName( _encoding );
        } catch( UnsupportedCharsetException uce ) {
            throw new JAXBException( uce );
        }
         */
    }

    /**
     * Default implementation of the setProperty method handles
     * the four defined properties in Marshaller. If a provider
     * needs to handle additional properties, it should override
     * this method in a derived class.
     */
    public void setProperty( String name, Object value )
        throws PropertyException {

        if( name == null ) {
            throw new IllegalArgumentException(
                Messages.format( Messages.MUST_NOT_BE_NULL, "name" ) );
        }

        // recognize and handle four pre-defined properties.
        if( JAXB_ENCODING.equals(name) ) {
            checkString( name, value );
            setEncoding( (String)value );
            return;
        }
        if( JAXB_FORMATTED_OUTPUT.equals(name) ) {
            checkBoolean( name, value );
            setFormattedOutput((Boolean) value );
            return;
        }
        if( JAXB_NO_NAMESPACE_SCHEMA_LOCATION.equals(name) ) {
            checkString( name, value );
            setNoNSSchemaLocation( (String)value );
            return;
        }
        if( JAXB_SCHEMA_LOCATION.equals(name) ) {
            checkString( name, value );
            setSchemaLocation( (String)value );
            return;
        }
        if( JAXB_FRAGMENT.equals(name) )  {
            checkBoolean(name, value);
            setFragment((Boolean) value );
            return;
        }

        throw new PropertyException(name, value);
    }

    /**
     * Default implementation of the getProperty method handles
     * the four defined properties in Marshaller.  If a provider
     * needs to support additional provider specific properties,
     * it should override this method in a derived class.
     */
    public Object getProperty( String name )
        throws PropertyException {

        if( name == null ) {
            throw new IllegalArgumentException(
                Messages.format( Messages.MUST_NOT_BE_NULL, "name" ) );
        }

        // recognize and handle four pre-defined properties.
        if( JAXB_ENCODING.equals(name) )
            return getEncoding();
        if( JAXB_FORMATTED_OUTPUT.equals(name) )
            return isFormattedOutput()?Boolean.TRUE:Boolean.FALSE;
        if( JAXB_NO_NAMESPACE_SCHEMA_LOCATION.equals(name) )
            return getNoNSSchemaLocation();
        if( JAXB_SCHEMA_LOCATION.equals(name) )
            return getSchemaLocation();
        if( JAXB_FRAGMENT.equals(name) )
            return isFragment()?Boolean.TRUE:Boolean.FALSE;

        throw new PropertyException(name);
    }
    /**
     * @see javax.xml.bind.Marshaller#getEventHandler()
     */
    public ValidationEventHandler getEventHandler() throws JAXBException {
        return eventHandler;
    }

    /**
     * @see javax.xml.bind.Marshaller#setEventHandler(ValidationEventHandler)
     */
    public void setEventHandler(ValidationEventHandler handler)
        throws JAXBException {

        if( handler == null ) {
            eventHandler = new DefaultValidationEventHandler();
        } else {
            eventHandler = handler;
        }
    }




    /*
     * assert that the given object is a Boolean
     */
    private void checkBoolean( String name, Object value ) throws PropertyException {
        if(!(value instanceof Boolean))
            throw new PropertyException(
                Messages.format( Messages.MUST_BE_BOOLEAN, name ) );
    }

    /*
     * assert that the given object is a String
     */
    private void checkString( String name, Object value ) throws PropertyException {
        if(!(value instanceof String))
            throw new PropertyException(
                Messages.format( Messages.MUST_BE_STRING, name ) );
    }

    /*
     * assert that the parameters are not null
     */
    private void checkNotNull( Object o1, String o1Name,
                               Object o2, String o2Name ) {

        if( o1 == null ) {
            throw new IllegalArgumentException(
                Messages.format( Messages.MUST_NOT_BE_NULL, o1Name ) );
        }
        if( o2 == null ) {
            throw new IllegalArgumentException(
                Messages.format( Messages.MUST_NOT_BE_NULL, o2Name ) );
        }
    }

    public void marshal(Object obj, XMLEventWriter writer)
        throws JAXBException {

        throw new UnsupportedOperationException();
    }

    public void marshal(Object obj, XMLStreamWriter writer)
        throws JAXBException {

        throw new UnsupportedOperationException();
    }

    public void setSchema(Schema schema) {
        throw new UnsupportedOperationException();
    }

    public Schema getSchema() {
        throw new UnsupportedOperationException();
    }

    public void setAdapter(XmlAdapter adapter) {
        if(adapter==null)
            throw new IllegalArgumentException();
        setAdapter((Class)adapter.getClass(),adapter);
    }

    public <A extends XmlAdapter> void setAdapter(Class<A> type, A adapter) {
        throw new UnsupportedOperationException();
    }

    public <A extends XmlAdapter> A getAdapter(Class<A> type) {
        throw new UnsupportedOperationException();
    }

    public void setAttachmentMarshaller(AttachmentMarshaller am) {
        throw new UnsupportedOperationException();
    }

    public AttachmentMarshaller getAttachmentMarshaller() {
        throw new UnsupportedOperationException();
    }

    public void setListener(Listener listener) {
        throw new UnsupportedOperationException();
    }

    public Listener getListener() {
        throw new UnsupportedOperationException();
    }
}
