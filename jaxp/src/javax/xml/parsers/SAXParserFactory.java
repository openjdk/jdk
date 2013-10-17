/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.parsers;

import javax.xml.validation.Schema;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Defines a factory API that enables applications to configure and
 * obtain a SAX based parser to parse XML documents.
 *
 * @author <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @author <a href="mailto:Neeraj.Bajaj@sun.com">Neeraj Bajaj</a>
 *
 * @version $Revision: 1.9 $, $Date: 2010/05/25 16:19:44 $
 *
 */
public abstract class SAXParserFactory {

    /**
     * <p>Should Parsers be validating?</p>
     */
    private boolean validating = false;

    /**
     * <p>Should Parsers be namespace aware?</p>
     */
    private boolean namespaceAware = false;

    /**
     * <p>Protected constructor to force use of {@link #newInstance()}.</p>
     */
    protected SAXParserFactory () {

    }

    /**
     * Obtain a new instance of a <code>SAXParserFactory</code>. This
     * static method creates a new factory instance
     * This method uses the following ordered lookup procedure to determine
     * the <code>SAXParserFactory</code> implementation class to
     * load:
     * <ul>
     * <li>
     * Use the <code>javax.xml.parsers.SAXParserFactory</code> system
     * property.
     * </li>
     * <li>
     * Use the properties file "lib/jaxp.properties" in the JRE directory.
     * This configuration file is in standard <code>java.util.Properties
     * </code> format and contains the fully qualified name of the
     * implementation class with the key being the system property defined
     * above.
     *
     * The jaxp.properties file is read only once by the JAXP implementation
     * and it's values are then cached for future use.  If the file does not exist
     * when the first attempt is made to read from it, no further attempts are
     * made to check for its existence.  It is not possible to change the value
     * of any property in jaxp.properties after it has been read for the first time.
     * </li>
     * <li>
     * Use the service-provider loading facilities, defined by the
     * {@link java.util.ServiceLoader} class, to attempt to locate and load an
     * implementation of the service using the {@linkplain
     * java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}:
     * the service-provider loading facility will use the {@linkplain
     * java.lang.Thread#getContextClassLoader() current thread's context class loader}
     * to attempt to load the service. If the context class
     * loader is null, the {@linkplain
     * ClassLoader#getSystemClassLoader() system class loader} will be used.
     * </li>
     * <li>
     * Otherwise the system-default implementation is returned.
     * </li>
     * </ul>
     *
     * Once an application has obtained a reference to a
     * <code>SAXParserFactory</code> it can use the factory to
     * configure and obtain parser instances.
     *
     *
     *
     * <h2>Tip for Trouble-shooting</h2>
     * <p>Setting the <code>jaxp.debug</code> system property will cause
     * this method to print a lot of debug messages
     * to <code>System.err</code> about what it is doing and where it is looking at.</p>
     *
     * <p> If you have problems loading {@link SAXParser}s, try:</p>
     * <pre>
     * java -Djaxp.debug=1 YourProgram ....
     * </pre>
     *
     *
     * @return A new instance of a SAXParserFactory.
     *
     * @throws FactoryConfigurationError in case of {@linkplain
     * java.util.ServiceConfigurationError service configuration error} or if
     * the implementation is not available or cannot be instantiated.
     */

    public static SAXParserFactory newInstance() {
        return FactoryFinder.find(
                /* The default property name according to the JAXP spec */
                SAXParserFactory.class,
                /* The fallback implementation class name */
                "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
    }

    /**
     * <p>Obtain a new instance of a <code>SAXParserFactory</code> from class name.
     * This function is useful when there are multiple providers in the classpath.
     * It gives more control to the application as it can specify which provider
     * should be loaded.</p>
     *
     * <p>Once an application has obtained a reference to a <code>SAXParserFactory</code>
     * it can use the factory to configure and obtain parser instances.</p>
     *
     *
     * <h2>Tip for Trouble-shooting</h2>
     * <p>Setting the <code>jaxp.debug</code> system property will cause
     * this method to print a lot of debug messages
     * to <code>System.err</code> about what it is doing and where it is looking at.</p>
     *
     * <p> If you have problems, try:</p>
     * <pre>
     * java -Djaxp.debug=1 YourProgram ....
     * </pre>
     *
     * @param factoryClassName fully qualified factory class name that provides implementation of <code>javax.xml.parsers.SAXParserFactory</code>.
     *
     * @param classLoader <code>ClassLoader</code> used to load the factory class. If <code>null</code>
     *                     current <code>Thread</code>'s context classLoader is used to load the factory class.
     *
     * @return New instance of a <code>SAXParserFactory</code>
     *
     * @throws FactoryConfigurationError if <code>factoryClassName</code> is <code>null</code>, or
     *                                   the factory class cannot be loaded, instantiated.
     *
     * @see #newInstance()
     *
     * @since 1.6
     */
    public static SAXParserFactory newInstance(String factoryClassName, ClassLoader classLoader){
            //do not fallback if given classloader can't find the class, throw exception
            return FactoryFinder.newInstance(SAXParserFactory.class,
                    factoryClassName, classLoader, false);
    }

    /**
     * <p>Creates a new instance of a SAXParser using the currently
     * configured factory parameters.</p>
     *
     * @return A new instance of a SAXParser.
     *
     * @throws ParserConfigurationException if a parser cannot
     *   be created which satisfies the requested configuration.
     * @throws SAXException for SAX errors.
     */

    public abstract SAXParser newSAXParser()
        throws ParserConfigurationException, SAXException;


    /**
     * Specifies that the parser produced by this code will
     * provide support for XML namespaces. By default the value of this is set
     * to <code>false</code>.
     *
     * @param awareness true if the parser produced by this code will
     *                  provide support for XML namespaces; false otherwise.
     */

    public void setNamespaceAware(boolean awareness) {
        this.namespaceAware = awareness;
    }

    /**
     * Specifies that the parser produced by this code will
     * validate documents as they are parsed. By default the value of this is
     * set to <code>false</code>.
     *
     * <p>
     * Note that "the validation" here means
     * <a href="http://www.w3.org/TR/REC-xml#proc-types">a validating
     * parser</a> as defined in the XML recommendation.
     * In other words, it essentially just controls the DTD validation.
     * (except the legacy two properties defined in JAXP 1.2.)
     * </p>
     *
     * <p>
     * To use modern schema languages such as W3C XML Schema or
     * RELAX NG instead of DTD, you can configure your parser to be
     * a non-validating parser by leaving the {@link #setValidating(boolean)}
     * method <code>false</code>, then use the {@link #setSchema(Schema)}
     * method to associate a schema to a parser.
     * </p>
     *
     * @param validating true if the parser produced by this code will
     *                   validate documents as they are parsed; false otherwise.
     */

    public void setValidating(boolean validating) {
        this.validating = validating;
    }

    /**
     * Indicates whether or not the factory is configured to produce
     * parsers which are namespace aware.
     *
     * @return true if the factory is configured to produce
     *         parsers which are namespace aware; false otherwise.
     */

    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    /**
     * Indicates whether or not the factory is configured to produce
     * parsers which validate the XML content during parse.
     *
     * @return true if the factory is configured to produce parsers which validate
     *         the XML content during parse; false otherwise.
     */

    public boolean isValidating() {
        return validating;
    }

    /**
     *
     * <p>Sets the particular feature in the underlying implementation of
     * org.xml.sax.XMLReader.
     * A list of the core features and properties can be found at
     * <a href="http://www.saxproject.org/">http://www.saxproject.org/</a></p>
     *
     * <p>All implementations are required to support the {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} feature.
     * When the feature is</p>
     * <ul>
     *   <li>
     *     <code>true</code>: the implementation will limit XML processing to conform to implementation limits.
     *     Examples include entity expansion limits and XML Schema constructs that would consume large amounts of resources.
     *     If XML processing is limited for security reasons, it will be reported via a call to the registered
     *     {@link org.xml.sax.ErrorHandler#fatalError(SAXParseException exception)}.
     *     See {@link SAXParser} <code>parse</code> methods for handler specification.
     *   </li>
     *   <li>
     *     When the feature is <code>false</code>, the implementation will processing XML according to the XML specifications without
     *     regard to possible implementation limits.
     *   </li>
     * </ul>
     *
     * @param name The name of the feature to be set.
     * @param value The value of the feature to be set.
     *
     * @throws ParserConfigurationException if a parser cannot
     *     be created which satisfies the requested configuration.
     * @throws SAXNotRecognizedException When the underlying XMLReader does
     *            not recognize the property name.
     * @throws SAXNotSupportedException When the underlying XMLReader
     *            recognizes the property name but doesn't support the
     *            property.
     * @throws NullPointerException If the <code>name</code> parameter is null.
     *
     * @see org.xml.sax.XMLReader#setFeature
     */
    public abstract void setFeature(String name, boolean value)
        throws ParserConfigurationException, SAXNotRecognizedException,
                SAXNotSupportedException;

    /**
     *
     * <p>Returns the particular property requested for in the underlying
     * implementation of org.xml.sax.XMLReader.</p>
     *
     * @param name The name of the property to be retrieved.
     *
     * @return Value of the requested property.
     *
     * @throws ParserConfigurationException if a parser cannot be created which satisfies the requested configuration.
     * @throws SAXNotRecognizedException When the underlying XMLReader does not recognize the property name.
     * @throws SAXNotSupportedException When the underlying XMLReader recognizes the property name but doesn't support the property.
     *
     * @see org.xml.sax.XMLReader#getProperty
     */
    public abstract boolean getFeature(String name)
        throws ParserConfigurationException, SAXNotRecognizedException,
                SAXNotSupportedException;


    /**
     * Gets the {@link Schema} object specified through
     * the {@link #setSchema(Schema schema)} method.
     *
     *
     * @throws UnsupportedOperationException When implementation does not
     *   override this method
     *
     * @return
     *      the {@link Schema} object that was last set through
     *      the {@link #setSchema(Schema)} method, or null
     *      if the method was not invoked since a {@link SAXParserFactory}
     *      is created.
     *
     * @since 1.5
     */
    public Schema getSchema() {
        throw new UnsupportedOperationException(
            "This parser does not support specification \""
            + this.getClass().getPackage().getSpecificationTitle()
            + "\" version \""
            + this.getClass().getPackage().getSpecificationVersion()
            + "\""
            );
    }

    /**
     * <p>Set the {@link Schema} to be used by parsers created
     * from this factory.</p>
     *
     * <p>When a {@link Schema} is non-null, a parser will use a validator
     * created from it to validate documents before it passes information
     * down to the application.</p>
     *
     * <p>When warnings/errors/fatal errors are found by the validator, the parser must
     * handle them as if those errors were found by the parser itself.
     * In other words, if the user-specified {@link org.xml.sax.ErrorHandler}
     * is set, it must receive those errors, and if not, they must be
     * treated according to the implementation specific
     * default error handling rules.
     *
     * <p>A validator may modify the SAX event stream (for example by
     * adding default values that were missing in documents), and a parser
     * is responsible to make sure that the application will receive
     * those modified event stream.</p>
     *
     * <p>Initialy, <code>null</code> is set as the {@link Schema}.</p>
     *
     * <p>This processing will take effect even if
     * the {@link #isValidating()} method returns <code>false</code>.
     *
     * <p>It is an error to use
     * the <code>http://java.sun.com/xml/jaxp/properties/schemaSource</code>
     * property and/or the <code>http://java.sun.com/xml/jaxp/properties/schemaLanguage</code>
     * property in conjunction with a non-null {@link Schema} object.
     * Such configuration will cause a {@link SAXException}
     * exception when those properties are set on a {@link SAXParser}.</p>
     *
     * <h4>Note for implementors</h4>
     * <p>
     * A parser must be able to work with any {@link Schema}
     * implementation. However, parsers and schemas are allowed
     * to use implementation-specific custom mechanisms
     * as long as they yield the result described in the specification.
     * </p>
     *
     * @param schema <code>Schema</code> to use, <code>null</code> to remove a schema.
     *
     * @throws UnsupportedOperationException When implementation does not
     *   override this method
     *
     * @since 1.5
     */
    public void setSchema(Schema schema) {
        throw new UnsupportedOperationException(
            "This parser does not support specification \""
            + this.getClass().getPackage().getSpecificationTitle()
            + "\" version \""
            + this.getClass().getPackage().getSpecificationVersion()
            + "\""
            );
    }

    /**
     * <p>Set state of XInclude processing.</p>
     *
     * <p>If XInclude markup is found in the document instance, should it be
     * processed as specified in <a href="http://www.w3.org/TR/xinclude/">
     * XML Inclusions (XInclude) Version 1.0</a>.</p>
     *
     * <p>XInclude processing defaults to <code>false</code>.</p>
     *
     * @param state Set XInclude processing to <code>true</code> or
     *   <code>false</code>
     *
     * @throws UnsupportedOperationException When implementation does not
     *   override this method
     *
     * @since 1.5
     */
    public void setXIncludeAware(final boolean state) {
        if (state) {
            throw new UnsupportedOperationException(" setXIncludeAware " +
                "is not supported on this JAXP"  +
                " implementation or earlier: " + this.getClass());
        }
    }

    /**
     * <p>Get state of XInclude processing.</p>
     *
     * @return current state of XInclude processing
     *
     * @throws UnsupportedOperationException When implementation does not
     *   override this method
     *
     * @since 1.5
     */
    public boolean isXIncludeAware() {
        throw new UnsupportedOperationException(
            "This parser does not support specification \""
            + this.getClass().getPackage().getSpecificationTitle()
            + "\" version \""
            + this.getClass().getPackage().getSpecificationVersion()
            + "\""
            );
    }
}
