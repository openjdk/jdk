/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Standard interfaces and base classes for JDBC <code>RowSet</code>
 * implementations. This package contains interfaces and classes
 * that a standard <code>RowSet</code> implementation either implements or extends.
 *
 * <h2>Table of Contents</h2>
 * <ul>
 * <li><a href="#pkgspec">1.0 Package Specification</a>
 * <li><a href="#stdrowset">2.0 Standard RowSet Definitions</a>
 * <li><a href="#impl">3.0 Implementer's Guide</a>
 * <li><a href="#relspec">4.0 Related Specifications</a>
 * <li><a href="#reldocs">5.0 Related Documentation</a>
 * </ul>
 *
 * <h3><a id="pkgspec">1.0 Package Specification</a></h3>
 * This package specifies five standard JDBC <code>RowSet</code> interfaces.
 * All five extend the
 * <a href="{@docRoot}/java.sql/javax/sql/RowSet.html">RowSet</a> interface described in the JDBC 3.0
 * specification.  It is anticipated that additional definitions
 * of more specialized JDBC <code>RowSet</code> types will emerge as this technology
 * matures. Future definitions <i>should</i> be specified as subinterfaces using
 * inheritance similar to the way it is used in this specification.
 * <p>
 * <i>Note:</i> The interface definitions provided in this package form the basis for
 * all compliant JDBC <code>RowSet</code> implementations. Vendors and more advanced
 * developers who intend to provide their own compliant <code>RowSet</code> implementations
 * should pay particular attention to the assertions detailed in specification
 * interfaces.
 *
 * <h3><a id="stdrowset">2.0 Standard RowSet Definitions</a></h3>
 * <ul>
 * <li><a href="JdbcRowSet.html"><b><code>JdbcRowSet</code></b></a> - A wrapper around
 * a <code>ResultSet</code> object that makes it possible to use the result set as a
 * JavaBeans&trade; component. Thus,
 * a <code>JdbcRowSet</code> object can be a Bean that any tool
 * makes available for assembling an application as part of a component based
 * architecture. A <code>JdbcRowSet</code> object is a connected <code>RowSet</code>
 * object, that is, it
 * <b>must</b> continually maintain its connection to its data source using a JDBC
 * technology-enabled driver ("JDBC driver"). In addition, a <code>JdbcRowSet</code>
 * object provides a fully updatable and scrollable tabular
 * data structure as defined in the JDBC 3.0 specification.
 *
 * <li><a href="CachedRowSet.html">
 * <b><code>CachedRowSet</code>&trade;</b></a>
 *  - A <code>CachedRowSet</code> object is a JavaBeans&trade;
 * component that is scrollable, updatable, serializable, and generally disconnected from
 * the source of its data. A <code>CachedRowSet</code> object
 * typically contains rows from a result set, but it can also contain rows from any
 * file with a tabular format, such as a spreadsheet. <code>CachedRowSet</code> implementations
 * <b>must</b> use the <code>SyncFactory</code> to manage and obtain pluggable
 * <code>SyncProvider</code> objects to provide synchronization between the
 * disconnected <code>RowSet</code> object and the originating data source.
 * Typically a <code>SyncProvider</code> implementation relies upon a JDBC
 * driver to obtain connectivity to a particular data source.
 * Further details on this mechanism are discussed in the <a
 * href="spi/package-summary.html"><code>javax.sql.rowset.spi</code></a> package
 * specification.
 *
 * <li><a href="WebRowSet.html"><b><code>WebRowSet</code></b></a> - A
 * <code>WebRowSet</code> object is an extension of <code>CachedRowSet</code>
 * that can read and write a <code>RowSet</code> object in a well formed XML format.
 * This class calls an <a href="spi/XmlReader.html"><code>XmlReader</code></a> object
 * (an extension of the <a href="{@docRoot}/java.sql/javax/sql/RowSetReader.html"><code>RowSetReader</code></a>
 * interface) to read a rowset in XML format. It calls an
 * <a href="spi/XmlWriter.html"><code>XmlWriter</code></a> object (an extension of the
 * <a href="{@docRoot}/java.sql/javax/sql/RowSetWriter.html"><code>RowSetWriter</code></a> interface)
 * to write a rowset in XML format. The reader and writer required by
 * <code>WebRowSet</code> objects are provided by the
 * <code>SyncFactory</code> in the form of <code>SyncProvider</code>
 * implementations. In order to ensure well formed XML usage, a standard generic XML
 * Schema is defined and published at
 * <a href="http://java.sun.com/xml/ns/jdbc/webrowset.xsd">
 * <code>http://java.sun.com/xml/ns/jdbc/webrowset.xsd</code></a>.
 *
 * <li><a href="FilteredRowSet.html"><b><code>FilteredRowSet</code></b></a> - A
 * <code>FilteredRowSet</code> object provides filtering functionality in a programmatic
 * and extensible way. There are many instances when a <code>RowSet</code> <code>object</code>
 * has a need to provide filtering in its contents without sacrificing the disconnected
 * environment, thus saving the expense of having to create a connection to the data source.
 * Solutions to this need vary from providing heavyweight full scale
 * SQL query abilities, to portable components, to more lightweight
 * approaches. A <code>FilteredRowSet</code> object consumes
 * an implementation of the <a href="Predicate.html"><code>Predicate</code></a>
 * interface, which <b>may</b> define a filter at run time. In turn, a
 * <code>FilteredRowSet</code> object is tasked with enforcing the set filter for both
 * inbound and outbound read and write operations. That is, all filters can be
 * considered as bi-directional. No standard filters are defined;
 * however, sufficient mechanics are specified to permit any required filter to be
 * implemented.
 *
 * <li><a href="JoinRowSet.html"><b><code>JoinRowSet</code></b></a> - The <code>JoinRowSet</code>
 * interface  describes a mechanism by which relationships can be established between
 * two or more standard <code>RowSet</code> implementations. Any number of <code>RowSet</code>
 * objects can be added to a <code>JoinRowSet</code> object provided  the <code>RowSet</code>objects
 * can be related  in a SQL <code>JOIN</code> like fashion. By definition, the SQL <code>JOIN</code>
 * statement  is used to combine the data contained in two (<i>or more</i>) relational
 * database tables based upon a common attribute. By establishing and then enforcing
 * column matches, a <code>JoinRowSet</code> object establishes relationships between
 * <code>RowSet</code> instances without the need to touch the originating data source.
 * </ul>
 *
 * <h3><a id="impl">3.0 Implementer's Guide</a></h3>
 * Compliant implementations of JDBC <code>RowSet</code> Implementations
 * <b>must</b> follow the assertions described in this specification. In accordance
 * with the terms of the <a href="http://www.jcp.org">Java Community Process</a>, a
 * Test Compatibility Kit (TCK) can be licensed to ensure compatibility with the
 * specification. The following paragraphs outline a number of starting points for
 * implementers of the standard JDBC <code>RowSet</code> definitions. Implementers
 * should also consult the <i>Implementer's Guide</i> in the <a
 * href="spi/package-summary.html">javax.sql.rowset.spi</a> package for guidelines
 * on <a href="spi/SyncProvider.html"><code>SyncProvider</code></a> implementations.
 *
 * <ul>
 * <li><b>3.1 Constructor</b>
 * <p>
 *   All <code>RowSet</code> implementations <strong>must</strong> provide a
 * no-argument constructor.
 * </li>
 * <li><b>3.2 Role of the <code>BaseRowSet</code> Class</b>
 * <p>
 * A compliant JDBC <code>RowSet</code> implementation <b>must</b> implement one or more
 * standard interfaces specified in this package and <b>may</b> extend the
 * <a href="BaseRowSet.html"><code>BaseRowSet</code></a> abstract class. For example, a
 * <code>CachedRowSet</code> implementation must implement the <code>CachedRowSet</code>
 * interface and extend the <code>BaseRowSet</code> abstract class. The
 * <code>BaseRowSet</code> class provides the standard architecture on which all
 * <code>RowSet</code> implementations should be built, regardless of whether the
 * <code>RowSet</code> objects exist in a connected or disconnected environment.
 * The <code>BaseRowSet</code> abstract class provides any <code>RowSet</code> implementation
 * with its base functionality, including property manipulation and event notification
 * that is fully compliant with <a href="http://java.sun.com/products/javabeans">JavaBeans</a>
 * component requirements. As an example, all implementations provided in the
 * reference implementations (contained in the <code>com.sun.rowset</code> package) use
 * the <code>BaseRowSet</code> class as a basis for their implementations.
 * <P>
 * The following table illustrates the features that the <code>BaseRowSet</code>
 * abstract class provides.
 * <blockquote>
 *   <table class="striped" style="vertical-align:top; width:75%">
 *     <caption>Features in <code>BaseRowSet</code></caption>
 *         <thead>
 *           <tr>
 *             <th scope="col">Feature</th>
 *             <th scope="col">Details</th>
 *           </tr>
 *         </thead>
 *         <tbody>
 *           <tr>
 *             <th scope="row">Properties</th>
 *             <td>Provides standard JavaBeans property manipulation
 * mechanisms to allow applications to get and set <code>RowSet</code> command and
 * property  values. Refer to the   documentation of the <code>javax.sql.RowSet</code>
 * interface  (available in the JDBC 3.0 specification) for more details on
 * the standard  <code>RowSet</code> properties.</td>
 *           </tr>
 *           <tr>
 *             <th scope="row">Event notification</th>
 *             <td>Provides standard JavaBeans event notifications
 * to registered event listeners. Refer to the documentation of <code>javax.sql.RowSetEvent</code>
 * interface (available in the JDBC 3.0 specification) for
 * more details on how  to register and handle standard RowSet events generated
 * by  compliant implementations.</td>
 *           </tr>
 *           <tr>
 *             <th scope="row">Setters for a RowSet object's command</th>
 *             <td>Provides a complete set of setter methods
 *                for setting RowSet command parameters.</td>
 *           </tr>
 *           <tr>
 *             <th scope="row">Streams</th>
 *             <td>Provides fields for storing of stream instances
 * in addition to providing a set of constants for stream type designation.</td>
 *           </tr>
 *     </tbody>
 *   </table>
 *   </blockquote>
 *
 * <li><b>3.3 Connected RowSet Requirements</b>
 * <p>
 * The <code>JdbcRowSet</code> describes a <code>RowSet</code> object that <b>must</b> always
 * be connected to the originating data source. Implementations of the <code>JdbcRowSet</code>
 * should ensure that this connection is provided solely by a JDBC driver.
 * Furthermore, <code>RowSet</code> objects that are implementations of the
 * <code>JdbcRowSet</code> interface and are therefore operating in a connected environment
 * do not use the <code>SyncFactory</code> to obtain a <code>RowSetReader</code> object
 * or a <code>RowSetWriter</code> object. They can safely rely on the JDBC driver to
 * supply their needs by virtue of the presence of an underlying updatable and scrollable
 * <code>ResultSet</code> implementation.
 *
 * <li>
 * <b>3.4 Disconnected RowSet Requirements</b>
 * <p>
 * A disconnected <code>RowSet</code> object, such as a <code>CachedRowSet</code> object,
 * <b>should</b> delegate
 * connection management to a <code>SyncProvider</code> object provided by the
 * <code>SyncFactory</code>. To ensure fully disconnected semantics, all
 * disconnected <code>RowSet</code> objects <b>must</b> ensure
 * that the original connection made to the data source to populate the <code>RowSet</code>
 * object is closed to permit the garbage collector to recover and release resources. The
 * <code>SyncProvider</code> object ensures that the critical JDBC properties are
 * maintained in order to re-establish a connection to the data source when a
 * synchronization is required. A disconnected <code>RowSet</code> object should
 * therefore ensure that no
 * extraneous references remain on the <code>Connection</code> object.
 *
 * <li><b>3.5 Role of RowSetMetaDataImpl</b>
 * <p>
 * The <code>RowsetMetaDataImpl</code> class is a utility class that provides an implementation of the
 * <a href="{@docRoot}/java.sql/javax/sql/RowSetMetaData.html">RowSetMetaData</a> interface, supplying standard setter
 * method implementations for metadata for both connected and disconnected
 * <code>RowSet</code> objects. All implementations are free to use this standard
 * implementation but are not required to do so.
 *
 * <li><b>3.6 RowSetWarning Class</b>
 * <p>
 * The <code>RowSetWarning</code> class provides warnings that can be set
 * on <code>RowSet</code> implementations.
 * Similar to <a href="{@docRoot}/java.sql/java/sql/SQLWarning.html">SQLWarning</a> objects,
 * <code>RowSetWarning</code>  objects are silently chained to the object whose method
 * caused the warning to be thrown. All <code>RowSet</code> implementations <b>should</b>
 * ensure that this chaining occurs if a warning is generated and also ensure that the
 * warnings are available via the <code>getRowSetWarnings</code> method defined in either
 * the <code>JdbcRowSet</code> interface or the <code>CachedRowSet</code> interface.
 * After a warning has been retrieved with one of the
 * <code>getRowSetWarnings</code> methods, the <code>RowSetWarning</code> method
 * <code>getNextWarning</code> can be called on it to retrieve any warnings that might
 * be chained on it.  If a warning is returned, <code>getNextWarning</code> can be called
 * on it, and so on until there are no more warnings.
 *
 * <li><b>3.7 The Joinable Interface</b>
 * <P>
 * The <code>Joinable</code> interface provides both connected and disconnected
 * <code>RowSet</code> objects with the capability to be added to a
 * <code>JoinRowSet</code> object in an SQL <code>JOIN</code> operation.
 * A <code>RowSet</code> object that has  implemented the <code>Joinable</code>
 * interface can set a match column, retrieve a match column, or unset a match column.
 * A <code>JoinRowSet</code> object can then use the <code>RowSet</code> object's
 * match column as a basis for adding the <code>RowSet</code> object.
 * </li>
 *
 * <li><b>3.8 The RowSetFactory Interface</b>
 *   <p>
 *       A <code>RowSetFactory</code> implementation <strong>must</strong>
 *       be provided.
 * </li>
 * </ul>
 *
 * <h3><a id="relspec">4.0 Related Specifications</a></h3>
 * <ul>
 * <li><a href="https://jcp.org/en/jsr/detail?id=221">JDBC 4.3 Specification</a>
 * <li><a href="http://www.w3.org/XML/Schema">XML Schema</a>
 * </ul>
 *
 * <h3><a id="reldocs">5.0 Related Documentation</a></h3>
 * <ul>
 * <li><a href="http://docs.oracle.com/javase/tutorial/jdbc/basics/rowset.html">
 * JDBC RowSet Tutorial</a>
 *</ul>
 */
package javax.sql.rowset;
