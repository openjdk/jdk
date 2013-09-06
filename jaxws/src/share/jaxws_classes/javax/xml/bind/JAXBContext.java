/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import org.w3c.dom.Node;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * The <tt>JAXBContext</tt> class provides the client's entry point to the
 * JAXB API. It provides an abstraction for managing the XML/Java binding
 * information necessary to implement the JAXB binding framework operations:
 * unmarshal, marshal and validate.
 *
 * <p>A client application normally obtains new instances of this class using
 * one of these two styles for newInstance methods, although there are other
 * specialized forms of the method available:
 *
 * <ul>
 *   <li>{@link #newInstance(String,ClassLoader) JAXBContext.newInstance( "com.acme.foo:com.acme.bar" )} <br/>
 *   The JAXBContext instance is initialized from a list of colon
 *   separated Java package names. Each java package contains
 *   JAXB mapped classes, schema-derived classes and/or user annotated
 *   classes. Additionally, the java package may contain JAXB package annotations
 *   that must be processed. (see JLS, Section 7.4.1 "Named Packages").
 *   </li>
 *   <li>{@link #newInstance(Class...) JAXBContext.newInstance( com.acme.foo.Foo.class )} <br/>
 *    The JAXBContext instance is initialized with class(es)
 *    passed as parameter(s) and classes that are statically reachable from
 *    these class(es). See {@link #newInstance(Class...)} for details.
 *   </li>
 * </ul>
 *
 * <p>
 * <i><B>SPEC REQUIREMENT:</B> the provider must supply an implementation
 * class containing the following method signatures:</i>
 *
 * <pre>
 * public static JAXBContext createContext( String contextPath, ClassLoader classLoader, Map&lt;String,Object> properties ) throws JAXBException
 * public static JAXBContext createContext( Class[] classes, Map&lt;String,Object> properties ) throws JAXBException
 * </pre>
 *
 * <p><i>
 * The following JAXB 1.0 requirement is only required for schema to
 * java interface/implementation binding. It does not apply to JAXB annotated
 * classes. JAXB Providers must generate a <tt>jaxb.properties</tt> file in
 * each package containing schema derived classes.  The property file must
 * contain a property named <tt>javax.xml.bind.context.factory</tt> whose
 * value is the name of the class that implements the <tt>createContext</tt>
 * APIs.</i>
 *
 * <p><i>
 * The class supplied by the provider does not have to be assignable to
 * <tt>javax.xml.bind.JAXBContext</tt>, it simply has to provide a class that
 * implements the <tt>createContext</tt> APIs.</i>
 *
 * <p><i>
 * In addition, the provider must call the
 * {@link DatatypeConverter#setDatatypeConverter(DatatypeConverterInterface)
 * DatatypeConverter.setDatatypeConverter} api prior to any client
 * invocations of the marshal and unmarshal methods.  This is necessary to
 * configure the datatype converter that will be used during these operations.</i>
 *
 * <a name="Unmarshalling"></a>
 * <h3>Unmarshalling</h3>
 * <p>
 * The {@link Unmarshaller} class provides the client application the ability
 * to convert XML data into a tree of Java content objects.
 * The unmarshal method allows for
 * any global XML element declared in the schema to be unmarshalled as
 * the root of an instance document.
 * Additionally, the unmarshal method allows for an unrecognized root element that
 * has  an xsi:type attribute's value that references a type definition declared in
 * the schema  to be unmarshalled as the root of an instance document.
 * The <tt>JAXBContext</tt> object
 * allows the merging of global elements and type definitions across a set of schemas (listed
 * in the <tt>contextPath</tt>). Since each schema in the schema set can belong
 * to distinct namespaces, the unification of schemas to an unmarshalling
 * context should be namespace independent.  This means that a client
 * application is able to unmarshal XML documents that are instances of
 * any of the schemas listed in the <tt>contextPath</tt>.  For example:
 *
 * <pre>
 *        JAXBContext jc = JAXBContext.newInstance( "com.acme.foo:com.acme.bar" );
 *        Unmarshaller u = jc.createUnmarshaller();
 *        FooObject fooObj = (FooObject)u.unmarshal( new File( "foo.xml" ) ); // ok
 *        BarObject barObj = (BarObject)u.unmarshal( new File( "bar.xml" ) ); // ok
 *        BazObject bazObj = (BazObject)u.unmarshal( new File( "baz.xml" ) ); // error, "com.acme.baz" not in contextPath
 * </pre>
 *
 * <p>
 * The client application may also generate Java content trees explicitly rather
 * than unmarshalling existing XML data.  For all JAXB-annotated value classes,
 * an application can create content using constructors.
 * For schema-derived interface/implementation classes and for the
 * creation of elements that are not bound to a JAXB-annotated
 * class, an application needs to have access and knowledge about each of
 * the schema derived <tt> ObjectFactory</tt> classes that exist in each of
 * java packages contained in the <tt>contextPath</tt>.  For each schema
 * derived java class, there is a static factory method that produces objects
 * of that type.  For example,
 * assume that after compiling a schema, you have a package <tt>com.acme.foo</tt>
 * that contains a schema derived interface named <tt>PurchaseOrder</tt>.  In
 * order to create objects of that type, the client application would use the
 * factory method like this:
 *
 * <pre>
 *       com.acme.foo.PurchaseOrder po =
 *           com.acme.foo.ObjectFactory.createPurchaseOrder();
 * </pre>
 *
 * <p>
 * Once the client application has an instance of the the schema derived object,
 * it can use the mutator methods to set content on it.
 *
 * <p>
 * For more information on the generated <tt>ObjectFactory</tt> classes, see
 * Section 4.2 <i>Java Package</i> of the specification.
 *
 * <p>
 * <i><B>SPEC REQUIREMENT:</B> the provider must generate a class in each
 * package that contains all of the necessary object factory methods for that
 * package named ObjectFactory as well as the static
 * <tt>newInstance( javaContentInterface )</tt> method</i>
 *
 * <h3>Marshalling</h3>
 * <p>
 * The {@link Marshaller} class provides the client application the ability
 * to convert a Java content tree back into XML data.  There is no difference
 * between marshalling a content tree that is created manually using the factory
 * methods and marshalling a content tree that is the result an <tt>unmarshal
 * </tt> operation.  Clients can marshal a java content tree back to XML data
 * to a <tt>java.io.OutputStream</tt> or a <tt>java.io.Writer</tt>.  The
 * marshalling process can alternatively produce SAX2 event streams to a
 * registered <tt>ContentHandler</tt> or produce a DOM Node object.
 * Client applications have control over the output encoding as well as
 * whether or not to marshal the XML data as a complete document or
 * as a fragment.
 *
 * <p>
 * Here is a simple example that unmarshals an XML document and then marshals
 * it back out:
 *
 * <pre>
 *        JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *
 *        // unmarshal from foo.xml
 *        Unmarshaller u = jc.createUnmarshaller();
 *        FooObject fooObj = (FooObject)u.unmarshal( new File( "foo.xml" ) );
 *
 *        // marshal to System.out
 *        Marshaller m = jc.createMarshaller();
 *        m.marshal( fooObj, System.out );
 * </pre>
 *
 *
 * <h3>Validation</h3>
 * <p>
 * Validation has been changed significantly since JAXB 1.0.  The {@link Validator}
 * class has been deprecated and made optional.  This means that you are advised
 * not to use this class and, in fact, it may not even be available depending on
 * your JAXB provider.  JAXB 1.0 client applications that rely on <tt>Validator</tt>
 * will still work properly when deployed with the JAXB 1.0 runtime system.
 *
 * In JAXB 2.0, the {@link Unmarshaller} has included convenince methods that expose
 * the JAXP 1.3 {@link javax.xml.validation} framework.  Please refer to the
 * {@link Unmarshaller#setSchema(javax.xml.validation.Schema)} API for more
 * information.
 *
 *
 * <h3>JAXB Runtime Binding Framework Compatibility</h3>
 * <p>
 * The following JAXB 1.0 restriction only applies to binding schema to
 * interfaces/implementation classes.
 * Since this binding does not require a common runtime system, a JAXB
 * client application must not attempt to mix runtime objects (<tt>JAXBContext,
 * Marshaller</tt>, etc. ) from different providers.  This does not
 * mean that the client application isn't portable, it simply means that a
 * client has to use a runtime system provided by the same provider that was
 * used to compile the schema.
 *
 *
 * <h3>Discovery of JAXB implementation</h3>
 * <p>
 * When one of the <tt>newInstance</tt> methods is called, a JAXB implementation is discovered
 * by the following steps.
 *
 * <ol>
 * <li>
 * For each package/class explicitly passed in to the {@link #newInstance} method, in the order they are specified,
 * <tt>jaxb.properties</tt> file is looked up in its package, by using the associated classloader &mdash;
 * this is {@link Class#getClassLoader() the owner class loader} for a {@link Class} argument, and for a package
 * the specified {@link ClassLoader}.
 *
 * <p>
 * If such a file is discovered, it is {@link Properties#load(InputStream) loaded} as a property file, and
 * the value of the {@link #JAXB_CONTEXT_FACTORY} key will be assumed to be the provider factory class.
 * This class is then loaded by the associated classloader discussed above.
 *
 * <p>
 * This phase of the look up allows some packages to force the use of a certain JAXB implementation.
 * (For example, perhaps the schema compiler has generated some vendor extension in the code.)
 *
 * <li>
 * If the system property {@link #JAXB_CONTEXT_FACTORY} exists, then its value is assumed to be the provider
 * factory class. This phase of the look up enables per-JVM override of the JAXB implementation.
 *
 * <li>
 * Look for <tt>/META-INF/services/javax.xml.bind.JAXBContext</tt> file in the associated classloader.
 * This file follows the standard service descriptor convention, and if such a file exists, its content
 * is assumed to be the provider factory class. This phase of the look up is for automatic discovery.
 * It allows users to just put a JAXB implementation in a classpath and use it without any furhter configuration.
 *
 * <li>
 * Finally, if all the steps above fail, then the rest of the look up is unspecified. That said,
 * the recommended behavior is to simply look for some hard-coded platform default JAXB implementation.
 * This phase of the look up is so that JavaSE can have its own JAXB implementation as the last resort.
 * </ol>
 *
 * <p>
 * Once the provider factory class is discovered, its
 * <tt>public static JAXBContext createContext(String,ClassLoader,Map)</tt> method
 * (see {@link #newInstance(String, ClassLoader, Map)} for the parameter semantics.)
 * or <tt>public static JAXBContext createContet(Class[],Map)</tt> method
 * (see {@link #newInstance(Class[], Map)} for the parameter semantics) are invoked
 * to create a {@link JAXBContext}.
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @see Marshaller
 * @see Unmarshaller
 * @see S 7.4.1 "Named Packages" in Java Language Specification</a>
 * @since JAXB1.0
 */
public abstract class JAXBContext {

    /**
     * The name of the property that contains the name of the class capable
     * of creating new <tt>JAXBContext</tt> objects.
     */
    public static final String JAXB_CONTEXT_FACTORY =
        "javax.xml.bind.context.factory";


    protected JAXBContext() {
    }


    /**
     * <p>
     * Obtain a new instance of a <tt>JAXBContext</tt> class.
     *
     * <p>
     * This is a convenience method to invoke the
     * {@link #newInstance(String,ClassLoader)} method with
     * the context class loader of the current thread.
     *
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>JAXBContext</tt> such as
     * <ol>
     *   <li>failure to locate either ObjectFactory.class or jaxb.index in the packages</li>
     *   <li>an ambiguity among global elements contained in the contextPath</li>
     *   <li>failure to locate a value for the context factory provider property</li>
     *   <li>mixing schema derived packages from different providers on the same contextPath</li>
     * </ol>
     */
    public static JAXBContext newInstance( String contextPath )
        throws JAXBException {

        //return newInstance( contextPath, JAXBContext.class.getClassLoader() );
        return newInstance( contextPath, getContextClassLoader());
    }

    /**
     * <p>
     * Obtain a new instance of a <tt>JAXBContext</tt> class.
     *
     * <p>
     * The client application must supply a context path which is a list of
     * colon (':', \u005Cu003A) separated java package names that contain
     * schema-derived classes and/or fully qualified JAXB-annotated classes.
     * Schema-derived
     * code is registered with the JAXBContext by the
     * ObjectFactory.class generated per package.
     * Alternatively than being listed in the context path, programmer
     * annotated JAXB mapped classes can be listed in a
     * <tt>jaxb.index</tt> resource file, format described below.
     * Note that a java package can contain both schema-derived classes and
     * user annotated JAXB classes. Additionally, the java package may
     * contain JAXB package annotations  that must be processed. (see JLS,
     * Section 7.4.1 "Named Packages").
     * </p>
     *
     * <p>
     * Every package listed on the contextPath must meet <b>one or both</b> of the
     * following conditions otherwise a <tt>JAXBException</tt> will be thrown:
     * </p>
     * <ol>
     *   <li>it must contain ObjectFactory.class</li>
     *   <li>it must contain jaxb.index</li>
     * </ol>
     *
     * <p>
     * <b>Format for jaxb.index</b>
     * <p>
     * The file contains a newline-separated list of class names.
     * Space and tab characters, as well as blank
     * lines, are ignored. The comment character
     * is '#' (0x23); on each line all characters following the first comment
     * character are ignored. The file must be encoded in UTF-8. Classes that
     * are reachable, as defined in {@link #newInstance(Class...)}, from the
     * listed classes are also registered with JAXBContext.
     * <p>
     * Constraints on class name occuring in a <tt>jaxb.index</tt> file are:
     * <ul>
     *   <li>Must not end with ".class".</li>
     *   <li>Class names are resolved relative to package containing
     *       <tt>jaxb.index</tt> file. Only classes occuring directly in package
     *       containing <tt>jaxb.index</tt> file are allowed.</li>
     *   <li>Fully qualified class names are not allowed.
     *       A qualified class name,relative to current package,
     *       is only allowed to specify a nested or inner class.</li>
     * </ul>
     *
     * <p>
     * To maintain compatibility with JAXB 1.0 schema to java
     * interface/implementation binding, enabled by schema customization
     * <tt>&lt;jaxb:globalBindings valueClass="false"></tt>,
     * the JAXB provider will ensure that each package on the context path
     * has a <tt>jaxb.properties</tt> file which contains a value for the
     * <tt>javax.xml.bind.context.factory</tt> property and that all values
     * resolve to the same provider.  This requirement does not apply to
     * JAXB annotated classes.
     *
     * <p>
     * If there are any global XML element name collisions across the various
     * packages listed on the <tt>contextPath</tt>, a <tt>JAXBException</tt>
     * will be thrown.
     *
     * <p>
     * Mixing generated interface/impl bindings from multiple JAXB Providers
     * in the same context path may result in a <tt>JAXBException</tt>
     * being thrown.
     *
     * <p>
     * The steps involved in discovering the JAXB implementation is discussed in the class javadoc.
     *
     * @param contextPath list of java package names that contain schema
     *                    derived class and/or java to schema (JAXB-annotated)
     *                    mapped classes
     * @param classLoader
     *      This class loader will be used to locate the implementation
     *      classes.
     *
     * @return a new instance of a <tt>JAXBContext</tt>
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>JAXBContext</tt> such as
     * <ol>
     *   <li>failure to locate either ObjectFactory.class or jaxb.index in the packages</li>
     *   <li>an ambiguity among global elements contained in the contextPath</li>
     *   <li>failure to locate a value for the context factory provider property</li>
     *   <li>mixing schema derived packages from different providers on the same contextPath</li>
     * </ol>
     */
    public static JAXBContext newInstance( String contextPath, ClassLoader classLoader ) throws JAXBException {

        return newInstance(contextPath,classLoader,Collections.<String,Object>emptyMap());
    }

    /**
     * <p>
     * Obtain a new instance of a <tt>JAXBContext</tt> class.
     *
     * <p>
     * This is mostly the same as {@link JAXBContext#newInstance(String, ClassLoader)},
     * but this version allows you to pass in provider-specific properties to configure
     * the instantiation of {@link JAXBContext}.
     *
     * <p>
     * The interpretation of properties is up to implementations. Implementations should
     * throw <tt>JAXBException</tt> if it finds properties that it doesn't understand.
     *
     * @param contextPath list of java package names that contain schema derived classes
     * @param classLoader
     *      This class loader will be used to locate the implementation classes.
     * @param properties
     *      provider-specific properties. Can be null, which means the same thing as passing
     *      in an empty map.
     *
     * @return a new instance of a <tt>JAXBContext</tt>
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>JAXBContext</tt> such as
     * <ol>
     *   <li>failure to locate either ObjectFactory.class or jaxb.index in the packages</li>
     *   <li>an ambiguity among global elements contained in the contextPath</li>
     *   <li>failure to locate a value for the context factory provider property</li>
     *   <li>mixing schema derived packages from different providers on the same contextPath</li>
     * </ol>
     * @since JAXB2.0
     */
    public static JAXBContext newInstance( String contextPath, ClassLoader classLoader, Map<String,?>  properties  )
        throws JAXBException {

        return ContextFinder.find(
                        /* The default property name according to the JAXB spec */
                        JAXB_CONTEXT_FACTORY,

                        /* the context path supplied by the client app */
                        contextPath,

                        /* class loader to be used */
                        classLoader,
                        properties );
    }

// TODO: resurrect this once we introduce external annotations
//    /**
//     * <p>
//     * Obtain a new instance of a <tt>JAXBContext</tt> class.
//     *
//     * <p>
//     * The client application must supply a list of classes that the new
//     * context object needs to recognize.
//     *
//     * Not only the new context will recognize all the classes specified,
//     * but it will also recognize any classes that are directly/indirectly
//     * referenced statically from the specified classes.
//     *
//     * For example, in the following Java code, if you do
//     * <tt>newInstance(Foo.class)</tt>, the newly created {@link JAXBContext}
//     * will recognize both <tt>Foo</tt> and <tt>Bar</tt>, but not <tt>Zot</tt>:
//     * <pre>
//     * class Foo {
//     *      Bar b;
//     * }
//     * class Bar { int x; }
//     * class Zot extends Bar { int y; }
//     * </pre>
//     *
//     * Therefore, a typical client application only needs to specify the
//     * top-level classes, but it needs to be careful.
//     *
//     * TODO: if we are to define other mechanisms, refer to them.
//     *
//     * @param externalBindings
//     *      list of external binding files. Can be null or empty if none is used.
//     *      when specified, those files determine how the classes are bound.
//     *
//     * @param classesToBeBound
//     *      list of java classes to be recognized by the new {@link JAXBContext}.
//     *      Can be empty, in which case a {@link JAXBContext} that only knows about
//     *      spec-defined classes will be returned.
//     *
//     * @return
//     *      A new instance of a <tt>JAXBContext</tt>. Always non-null valid object.
//     *
//     * @throws JAXBException
//     *      if an error was encountered while creating the
//     *      <tt>JAXBContext</tt>, such as (but not limited to):
//     * <ol>
//     *  <li>No JAXB implementation was discovered
//     *  <li>Classes use JAXB annotations incorrectly
//     *  <li>Classes have colliding annotations (i.e., two classes with the same type name)
//     *  <li>Specified external bindings are incorrect
//     *  <li>The JAXB implementation was unable to locate
//     *      provider-specific out-of-band information (such as additional
//     *      files generated at the development time.)
//     * </ol>
//     *
//     * @throws IllegalArgumentException
//     *      if the parameter contains {@code null} (i.e., {@code newInstance(null);})
//     *
//     * @since JAXB2.0
//     */
//    public static JAXBContext newInstance( Source[] externalBindings, Class... classesToBeBound )
//        throws JAXBException {
//
//        // empty class list is not an error, because the context will still include
//        // spec-specified classes like String and Integer.
//        // if(classesToBeBound.length==0)
//        //    throw new IllegalArgumentException();
//
//        // but it is an error to have nulls in it.
//        for( int i=classesToBeBound.length-1; i>=0; i-- )
//            if(classesToBeBound[i]==null)
//                throw new IllegalArgumentException();
//
//        return ContextFinder.find(externalBindings,classesToBeBound);
//    }

    /**
     * <p>
     * Obtain a new instance of a <tt>JAXBContext</tt> class.
     *
     * <p>
     * The client application must supply a list of classes that the new
     * context object needs to recognize.
     *
     * Not only the new context will recognize all the classes specified,
     * but it will also recognize any classes that are directly/indirectly
     * referenced statically from the specified classes. Subclasses of
     * referenced classes nor <tt>&#64;XmlTransient</tt> referenced classes
     * are not registered with JAXBContext.
     *
     * For example, in the following Java code, if you do
     * <tt>newInstance(Foo.class)</tt>, the newly created {@link JAXBContext}
     * will recognize both <tt>Foo</tt> and <tt>Bar</tt>, but not <tt>Zot</tt> or <tt>FooBar</tt>:
     * <pre>
     * class Foo {
     *      &#64;XmlTransient FooBar c;
     *      Bar b;
     * }
     * class Bar { int x; }
     * class Zot extends Bar { int y; }
     * class FooBar { }
     * </pre>
     *
     * Therefore, a typical client application only needs to specify the
     * top-level classes, but it needs to be careful.
     *
     * <p>
     * Note that for each java package registered with JAXBContext,
     * when the optional package annotations exist, they must be processed.
     * (see JLS, Section 7.4.1 "Named Packages").
     *
     * <p>
     * The steps involved in discovering the JAXB implementation is discussed in the class javadoc.
     *
     * @param classesToBeBound
     *      list of java classes to be recognized by the new {@link JAXBContext}.
     *      Can be empty, in which case a {@link JAXBContext} that only knows about
     *      spec-defined classes will be returned.
     *
     * @return
     *      A new instance of a <tt>JAXBContext</tt>. Always non-null valid object.
     *
     * @throws JAXBException
     *      if an error was encountered while creating the
     *      <tt>JAXBContext</tt>, such as (but not limited to):
     * <ol>
     *  <li>No JAXB implementation was discovered
     *  <li>Classes use JAXB annotations incorrectly
     *  <li>Classes have colliding annotations (i.e., two classes with the same type name)
     *  <li>The JAXB implementation was unable to locate
     *      provider-specific out-of-band information (such as additional
     *      files generated at the development time.)
     * </ol>
     *
     * @throws IllegalArgumentException
     *      if the parameter contains {@code null} (i.e., {@code newInstance(null);})
     *
     * @since JAXB2.0
     */
    public static JAXBContext newInstance( Class... classesToBeBound )
        throws JAXBException {

        return newInstance(classesToBeBound,Collections.<String,Object>emptyMap());
    }

    /**
     * <p>
     * Obtain a new instance of a <tt>JAXBContext</tt> class.
     *
     * <p>
     * An overloading of {@link JAXBContext#newInstance(Class...)}
     * to configure 'properties' for this instantiation of {@link JAXBContext}.
     *
     * <p>
     * The interpretation of properties is up to implementations. Implementations should
     * throw <tt>JAXBException</tt> if it finds properties that it doesn't understand.
     *
     * @param classesToBeBound
     *      list of java classes to be recognized by the new {@link JAXBContext}.
     *      Can be empty, in which case a {@link JAXBContext} that only knows about
     *      spec-defined classes will be returned.
     * @param properties
     *      provider-specific properties. Can be null, which means the same thing as passing
     *      in an empty map.
     *
     * @return
     *      A new instance of a <tt>JAXBContext</tt>. Always non-null valid object.
     *
     * @throws JAXBException
     *      if an error was encountered while creating the
     *      <tt>JAXBContext</tt>, such as (but not limited to):
     * <ol>
     *  <li>No JAXB implementation was discovered
     *  <li>Classes use JAXB annotations incorrectly
     *  <li>Classes have colliding annotations (i.e., two classes with the same type name)
     *  <li>The JAXB implementation was unable to locate
     *      provider-specific out-of-band information (such as additional
     *      files generated at the development time.)
     * </ol>
     *
     * @throws IllegalArgumentException
     *      if the parameter contains {@code null} (i.e., {@code newInstance(null,someMap);})
     *
     * @since JAXB2.0
     */
    public static JAXBContext newInstance( Class[] classesToBeBound, Map<String,?> properties )
        throws JAXBException {

        if (classesToBeBound == null) {
                throw new IllegalArgumentException();
        }

        // but it is an error to have nulls in it.
        for (int i = classesToBeBound.length - 1; i >= 0; i--) {
            if (classesToBeBound[i] == null) {
                throw new IllegalArgumentException();
            }
        }

        return ContextFinder.find(classesToBeBound,properties);
    }

    /**
     * Create an <tt>Unmarshaller</tt> object that can be used to convert XML
     * data into a java content tree.
     *
     * @return an <tt>Unmarshaller</tt> object
     *
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>Unmarshaller</tt> object
     */
    public abstract Unmarshaller createUnmarshaller() throws JAXBException;


    /**
     * Create a <tt>Marshaller</tt> object that can be used to convert a
     * java content tree into XML data.
     *
     * @return a <tt>Marshaller</tt> object
     *
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>Marshaller</tt> object
     */
    public abstract Marshaller createMarshaller() throws JAXBException;


    /**
     * {@link Validator} has been made optional and deprecated in JAXB 2.0.  Please
     * refer to the javadoc for {@link Validator} for more detail.
     * <p>
     * Create a <tt>Validator</tt> object that can be used to validate a
     * java content tree against its source schema.
     *
     * @return a <tt>Validator</tt> object
     *
     * @throws JAXBException if an error was encountered while creating the
     *                       <tt>Validator</tt> object
     * @deprecated since JAXB2.0
     */
    public abstract Validator createValidator() throws JAXBException;

    /**
     * Creates a <tt>Binder</tt> object that can be used for
     * associative/in-place unmarshalling/marshalling.
     *
     * @param domType select the DOM API to use by passing in its DOM Node class.
     *
     * @return always a new valid <tt>Binder</tt> object.
     *
     * @throws UnsupportedOperationException
     *      if DOM API corresponding to <tt>domType</tt> is not supported by
     *      the implementation.
     *
     * @since JAXB2.0
     */
    public <T> Binder<T> createBinder(Class<T> domType) {
        // to make JAXB 1.0 implementations work, this method must not be
        // abstract
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a <tt>Binder</tt> for W3C DOM.
     *
     * @return always a new valid <tt>Binder</tt> object.
     *
     * @since JAXB2.0
     */
    public Binder<Node> createBinder() {
        return createBinder(Node.class);
    }

    /**
     * Creates a <tt>JAXBIntrospector</tt> object that can be used to
     * introspect JAXB objects.
     *
     * @return
     *      always return a non-null valid <tt>JAXBIntrospector</tt> object.
     *
     * @throws UnsupportedOperationException
     *      Calling this method on JAXB 1.0 implementations will throw
     *      an UnsupportedOperationException.
     *
     * @since JAXB2.0
     */
    public JAXBIntrospector createJAXBIntrospector() {
        // to make JAXB 1.0 implementations work, this method must not be
        // abstract
        throw new UnsupportedOperationException();
    }

    /**
     * Generates the schema documents for this context.
     *
     * @param outputResolver
     *      this object controls the output to which schemas
     *      will be sent.
     *
     * @throws IOException
     *      if {@link SchemaOutputResolver} throws an {@link IOException}.
     *
     * @throws UnsupportedOperationException
     *      Calling this method on JAXB 1.0 implementations will throw
     *      an UnsupportedOperationException.
     *
     * @since JAXB 2.0
     */
    public void generateSchema(SchemaOutputResolver outputResolver) throws IOException  {
        // to make JAXB 1.0 implementations work, this method must not be
        // abstract
        throw new UnsupportedOperationException();
    }

    private static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return (ClassLoader) java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction() {
                        public java.lang.Object run() {
                            return Thread.currentThread().getContextClassLoader();
                        }
                    });
        }
    }

}
