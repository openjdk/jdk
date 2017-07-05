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

package javax.xml.transform;

/**
 * <p>A TransformerFactory instance can be used to create
 * {@link javax.xml.transform.Transformer} and
 * {@link javax.xml.transform.Templates} objects.</p>
 *
 * <p>The system property that determines which Factory implementation
 * to create is named <code>"javax.xml.transform.TransformerFactory"</code>.
 * This property names a concrete subclass of the
 * <code>TransformerFactory</code> abstract class. If the property is not
 * defined, a platform default is be used.</p>
 *
 * @author <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @author <a href="mailto:Neeraj.Bajaj@sun.com">Neeraj Bajaj</a>
 *
 * @since 1.5
 */
public abstract class TransformerFactory {

    /**
     * Default constructor is protected on purpose.
     */
    protected TransformerFactory() { }



    /**
     * <p>Obtain a new instance of a <code>TransformerFactory</code>.
     * This static method creates a new factory instance.</p>
     * <p>This method uses the following ordered lookup procedure to determine
     * the <code>TransformerFactory</code> implementation class to
     * load:</p>
     * <ul>
     * <li>
     * Use the <code>javax.xml.transform.TransformerFactory</code> system
     * property.
     * </li>
     * <li>
     * Use the properties file "lib/jaxp.properties" in the JRE directory.
     * This configuration file is in standard <code>java.util.Properties
     * </code> format and contains the fully qualified name of the
     * implementation class with the key being the system property defined
     * above.
     * <br>
     * The jaxp.properties file is read only once by the JAXP implementation
     * and it's values are then cached for future use.  If the file does not exist
     * when the first attempt is made to read from it, no further attempts are
     * made to check for its existence.  It is not possible to change the value
     * of any property in jaxp.properties after it has been read for the first time.
     * </li>
     * <li>
     *   Use the service-provider loading facilities, defined by the
     *   {@link java.util.ServiceLoader} class, to attempt to locate and load an
     *   implementation of the service using the {@linkplain
     *   java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}:
     *   the service-provider loading facility will use the {@linkplain
     *   java.lang.Thread#getContextClassLoader() current thread's context class loader}
     *   to attempt to load the service. If the context class
     *   loader is null, the {@linkplain
     *   ClassLoader#getSystemClassLoader() system class loader} will be used.
     * </li>
     * <li>
     *   Otherwise, the system-default implementation is returned.
     * </li>
     * </ul>
     *
     * <p>Once an application has obtained a reference to a <code>
     * TransformerFactory</code> it can use the factory to configure
     * and obtain transformer instances.</p>
     *
     * @return new TransformerFactory instance, never null.
     *
     * @throws TransformerFactoryConfigurationError Thrown in case of {@linkplain
     * java.util.ServiceConfigurationError service configuration error} or if
     * the implementation is not available or cannot be instantiated.
     */
    public static TransformerFactory newInstance()
        throws TransformerFactoryConfigurationError {

        return FactoryFinder.find(
            /* The default property name according to the JAXP spec */
            TransformerFactory.class,
            /* The fallback implementation class name, XSLTC */
            "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    }

    /**
     * <p>Obtain a new instance of a <code>TransformerFactory</code> from factory class name.
     * This function is useful when there are multiple providers in the classpath.
     * It gives more control to the application as it can specify which provider
     * should be loaded.</p>
     *
     * <p>Once an application has obtained a reference to a <code>
     * TransformerFactory</code> it can use the factory to configure
     * and obtain transformer instances.</p>
     *
     * <h2>Tip for Trouble-shooting</h2>
     * <p>Setting the <code>jaxp.debug</code> system property will cause
     * this method to print a lot of debug messages
     * to <code>System.err</code> about what it is doing and where it is looking at.</p>
     *
     * <p> If you have problems try:</p>
     * <pre>
     * java -Djaxp.debug=1 YourProgram ....
     * </pre>
     *
     * @param factoryClassName fully qualified factory class name that provides implementation of <code>javax.xml.transform.TransformerFactory</code>.
     *
     * @param classLoader <code>ClassLoader</code> used to load the factory class. If <code>null</code>
     *                     current <code>Thread</code>'s context classLoader is used to load the factory class.
     *
     * @return new TransformerFactory instance, never null.
     *
     * @throws TransformerFactoryConfigurationError
     *                    if <code>factoryClassName</code> is <code>null</code>, or
     *                   the factory class cannot be loaded, instantiated.
     *
     * @see #newInstance()
     *
     * @since 1.6
     */
    public static TransformerFactory newInstance(String factoryClassName, ClassLoader classLoader)
        throws TransformerFactoryConfigurationError{

        //do not fallback if given classloader can't find the class, throw exception
        return  FactoryFinder.newInstance(TransformerFactory.class,
                    factoryClassName, classLoader, false, false);
    }
    /**
     * <p>Process the <code>Source</code> into a <code>Transformer</code>
     * <code>Object</code>.  The <code>Source</code> is an XSLT document that
     * conforms to <a href="http://www.w3.org/TR/xslt">
     * XSL Transformations (XSLT) Version 1.0</a>.  Care must
     * be taken not to use this <code>Transformer</code> in multiple
     * <code>Thread</code>s running concurrently.
     * Different <code>TransformerFactories</code> can be used concurrently by
     * different <code>Thread</code>s.</p>
     *
     * @param source <code>Source </code> of XSLT document used to create
     *   <code>Transformer</code>.
     *   Examples of XML <code>Source</code>s include
     *   {@link javax.xml.transform.dom.DOMSource DOMSource},
     *   {@link javax.xml.transform.sax.SAXSource SAXSource}, and
     *   {@link javax.xml.transform.stream.StreamSource StreamSource}.
     *
     * @return A <code>Transformer</code> object that may be used to perform
     *   a transformation in a single <code>Thread</code>, never
     *   <code>null</code>.
     *
     * @throws TransformerConfigurationException Thrown if there are errors when
     *    parsing the <code>Source</code> or it is not possible to create a
     *   <code>Transformer</code> instance.
     *
     * @see <a href="http://www.w3.org/TR/xslt">
     *   XSL Transformations (XSLT) Version 1.0</a>
     */
    public abstract Transformer newTransformer(Source source)
        throws TransformerConfigurationException;

    /**
     * <p>Create a new <code>Transformer</code> that performs a copy
     * of the <code>Source</code> to the <code>Result</code>.
     * i.e. the "<em>identity transform</em>".</p>
     *
     * @return A Transformer object that may be used to perform a transformation
     * in a single thread, never null.
     *
     * @throws TransformerConfigurationException When it is not
     *   possible to create a <code>Transformer</code> instance.
     */
    public abstract Transformer newTransformer()
        throws TransformerConfigurationException;

    /**
     * Process the Source into a Templates object, which is a
     * a compiled representation of the source. This Templates object
     * may then be used concurrently across multiple threads.  Creating
     * a Templates object allows the TransformerFactory to do detailed
     * performance optimization of transformation instructions, without
     * penalizing runtime transformation.
     *
     * @param source An object that holds a URL, input stream, etc.
     *
     * @return A Templates object capable of being used for transformation
     *   purposes, never <code>null</code>.
     *
     * @throws TransformerConfigurationException When parsing to
     *   construct the Templates object fails.
     */
    public abstract Templates newTemplates(Source source)
        throws TransformerConfigurationException;

    /**
     * <p>Get the stylesheet specification(s) associated with the
     * XML <code>Source</code> document via the
     * <a href="http://www.w3.org/TR/xml-stylesheet/">
     * xml-stylesheet processing instruction</a> that match the given criteria.
     * Note that it is possible to return several stylesheets, in which case
     * they are applied as if they were a list of imports or cascades in a
     * single stylesheet.</p>
     *
     * @param source The XML source document.
     * @param media The media attribute to be matched.  May be null, in which
     *      case the prefered templates will be used (i.e. alternate = no).
     * @param title The value of the title attribute to match.  May be null.
     * @param charset The value of the charset attribute to match.  May be null.
     *
     * @return A <code>Source</code> <code>Object</code> suitable for passing
     *   to the <code>TransformerFactory</code>.
     *
     * @throws TransformerConfigurationException An <code>Exception</code>
     *   is thrown if an error occurings during parsing of the
     *   <code>source</code>.
     *
     * @see <a href="http://www.w3.org/TR/xml-stylesheet/">
     *   Associating Style Sheets with XML documents Version 1.0</a>
     */
    public abstract Source getAssociatedStylesheet(
        Source source,
        String media,
        String title,
        String charset)
        throws TransformerConfigurationException;

    /**
     * Set an object that is used by default during the transformation
     * to resolve URIs used in document(), xsl:import, or xsl:include.
     *
     * @param resolver An object that implements the URIResolver interface,
     * or null.
     */
    public abstract void setURIResolver(URIResolver resolver);

    /**
     * Get the object that is used by default during the transformation
     * to resolve URIs used in document(), xsl:import, or xsl:include.
     *
     * @return The URIResolver that was set with setURIResolver.
     */
    public abstract URIResolver getURIResolver();

    //======= CONFIGURATION METHODS =======

        /**
         * <p>Set a feature for this <code>TransformerFactory</code> and <code>Transformer</code>s
         * or <code>Template</code>s created by this factory.</p>
         *
         * <p>
         * Feature names are fully qualified {@link java.net.URI}s.
         * Implementations may define their own features.
         * An {@link TransformerConfigurationException} is thrown if this <code>TransformerFactory</code> or the
         * <code>Transformer</code>s or <code>Template</code>s it creates cannot support the feature.
         * It is possible for an <code>TransformerFactory</code> to expose a feature value but be unable to change its state.
         * </p>
         *
         * <p>All implementations are required to support the {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} feature.
         * When the feature is:</p>
         * <ul>
         *   <li>
         *     <code>true</code>: the implementation will limit XML processing to conform to implementation limits
         *     and behave in a secure fashion as defined by the implementation.
         *     Examples include resolving user defined style sheets and functions.
         *     If XML processing is limited for security reasons, it will be reported via a call to the registered
         *     {@link ErrorListener#fatalError(TransformerException exception)}.
         *     See {@link  #setErrorListener(ErrorListener listener)}.
         *   </li>
         *   <li>
         *     <code>false</code>: the implementation will processing XML according to the XML specifications without
         *     regard to possible implementation limits.
         *   </li>
         * </ul>
         *
         * @param name Feature name.
         * @param value Is feature state <code>true</code> or <code>false</code>.
         *
         * @throws TransformerConfigurationException if this <code>TransformerFactory</code>
         *   or the <code>Transformer</code>s or <code>Template</code>s it creates cannot support this feature.
     * @throws NullPointerException If the <code>name</code> parameter is null.
         */
        public abstract void setFeature(String name, boolean value)
                throws TransformerConfigurationException;

    /**
     * Look up the value of a feature.
     *
         * <p>
         * Feature names are fully qualified {@link java.net.URI}s.
         * Implementations may define their own features.
         * <code>false</code> is returned if this <code>TransformerFactory</code> or the
         * <code>Transformer</code>s or <code>Template</code>s it creates cannot support the feature.
         * It is possible for an <code>TransformerFactory</code> to expose a feature value but be unable to change its state.
         * </p>
         *
         * @param name Feature name.
         *
     * @return The current state of the feature, <code>true</code> or <code>false</code>.
     *
     * @throws NullPointerException If the <code>name</code> parameter is null.
     */
    public abstract boolean getFeature(String name);

    /**
     * Allows the user to set specific attributes on the underlying
     * implementation.  An attribute in this context is defined to
     * be an option that the implementation provides.
     * An <code>IllegalArgumentException</code> is thrown if the underlying
     * implementation doesn't recognize the attribute.
     * <p>
     * All implementations that implement JAXP 1.5 or newer are required to
     * support the {@link javax.xml.XMLConstants#ACCESS_EXTERNAL_DTD}  and
     * {@link javax.xml.XMLConstants#ACCESS_EXTERNAL_STYLESHEET} properties.
     * </p>
     * <ul>
     *   <li>
     *      <p>
     *      Access to external DTDs in the source file is restricted to the protocols
     *      specified by the {@link javax.xml.XMLConstants#ACCESS_EXTERNAL_DTD} property.
     *      If access is denied during transformation due to the restriction of this property,
     *      {@link javax.xml.transform.TransformerException} will be thrown by
     *      {@link javax.xml.transform.Transformer#transform(Source, Result)}.
     *      </p>
     *      <p>
     *      Access to external DTDs in the stylesheet is restricted to the protocols
     *      specified by the {@link javax.xml.XMLConstants#ACCESS_EXTERNAL_DTD} property.
     *      If access is denied during the creation of a new transformer due to the
     *      restriction of this property,
     *      {@link javax.xml.transform.TransformerConfigurationException} will be thrown
     *      by the {@link #newTransformer(Source)} method.
     *      </p>
     *      <p>
     *      Access to external reference set by the stylesheet processing instruction,
     *      Import and Include element is restricted to the protocols specified by the
     *      {@link javax.xml.XMLConstants#ACCESS_EXTERNAL_STYLESHEET} property.
     *      If access is denied during the creation of a new transformer due to the
     *      restriction of this property,
     *      {@link javax.xml.transform.TransformerConfigurationException} will be thrown
     *      by the {@link #newTransformer(Source)} method.
     *      </p>
     *      <p>
     *      Access to external document through XSLT document function is restricted
     *      to the protocols specified by the property. If access is denied during
     *      the transformation due to the restriction of this property,
     *      {@link javax.xml.transform.TransformerException} will be thrown by the
     *      {@link javax.xml.transform.Transformer#transform(Source, Result)} method.
     *      </p>
     *   </li>
     * </ul>
     *
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     *
     * @throws IllegalArgumentException When implementation does not
     *   recognize the attribute.
     */
    public abstract void setAttribute(String name, Object value);

    /**
     * Allows the user to retrieve specific attributes on the underlying
     * implementation.
     * An <code>IllegalArgumentException</code> is thrown if the underlying
     * implementation doesn't recognize the attribute.
     *
     * @param name The name of the attribute.
     *
     * @return value The value of the attribute.
     *
     * @throws IllegalArgumentException When implementation does not
     *   recognize the attribute.
     */
    public abstract Object getAttribute(String name);

    /**
     * Set the error event listener for the TransformerFactory, which
     * is used for the processing of transformation instructions,
     * and not for the transformation itself.
     * An <code>IllegalArgumentException</code> is thrown if the
     * <code>ErrorListener</code> listener is <code>null</code>.
     *
     * @param listener The new error listener.
     *
     * @throws IllegalArgumentException When <code>listener</code> is
     *   <code>null</code>
     */
    public abstract void setErrorListener(ErrorListener listener);

    /**
     * Get the error event handler for the TransformerFactory.
     *
     * @return The current error handler, which should never be null.
     */
    public abstract ErrorListener getErrorListener();

}
