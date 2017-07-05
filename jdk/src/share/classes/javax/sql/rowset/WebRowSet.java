/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.sql.rowset;

import java.sql.*;
import javax.sql.*;
import javax.naming.*;
import java.io.*;
import java.math.*;
import org.xml.sax.*;

/**
 * The standard interface that all implementations of a <code>WebRowSet</code>
 * must implement.
 * <P>
 * <h3>1.0 Overview</h3>
 * The <code>WebRowSetImpl</code> provides the standard
 * reference implementation, which may be extended if required.
 * <P>
 * The standard WebRowSet XML Schema definition is available at the following
 * URI:
 * <ul>
 * <pre>
 * <a href="http://java.sun.com/xml/ns/jdbc/webrowset.xsd">http://java.sun.com/xml/ns/jdbc/webrowset.xsd</a>
 * </pre>
 * </ul>
 * It describes the standard XML document format required when describing a
 * <code>RowSet</code> object in XML and must be used be all standard implementations
 * of the <code>WebRowSet</code> interface to ensure interoperability. In addition,
 * the <code>WebRowSet</code> schema uses specific SQL/XML Schema annotations,
 * thus ensuring greater cross
 * platform inter-operability. This is an effort currently under way at the ISO
 * organization. The SQL/XML definition is available at the following URI:
 * <ul>
 * <pre>
 * <a href="http://standards.iso.org/iso/9075/2002/12/sqlxml">http://standards.iso.org/iso/9075/2002/12/sqlxml</a>
 * </pre>
 * </ul>
 * The schema definition describes the internal data of a <code>RowSet</code> object
 * in three distinct areas:
 * <UL>
 * <li><b>properties</b></li>
 * These properties describe the standard synchronization provider properties in
 * addition to the more general <code>RowSet</code> properties.
 * <p>
 * <li><b>metadata</b></li>
 * This describes the metadata associated with the tabular structure governed by a
 * <code>WebRowSet</code> object. The metadata described is closely aligned with the
 * metadata accessible in the underlying <code>java.sql.ResultSet</code> interface.
 * <p>
 * <li><b>data</b></li>
 * This describes the original data (the state of data since the last population
 * or last synchronization of the <code>WebRowSet</code> object) and the current
 * data. By keeping track of the delta between the original data and the current data,
 * a <code>WebRowSet</code> maintains
 * the ability to synchronize changes in its data back to the originating data source.
 * </ul>
 * <P>
 * <h3>2.0 WebRowSet States</h3>
 * The following sections demonstrates how a <code>WebRowSet</code> implementation
 * should use the XML Schema to describe update, insert, and delete operations
 * and to describe the state of a <code>WebRowSet</code> object in XML.
 * <p>
 * <h4>2.1 State 1 - Outputting a <code>WebRowSet</code> Object to XML</h3>
 * In this example, a <code>WebRowSet</code> object is created and populated with a simple 2 column,
 * 5 row table from a data source. Having the 5 rows in a <code>WebRowSet</code> object
 * makes it possible to describe them in XML. The
 * metadata describing the various standard JavaBeans properties as defined
 * in the RowSet interface plus the standard properties defined in
 * the <code>CachedRowSet</code><sup><font size=-2>TM</font></sup> interface
 * provide key details that describe WebRowSet
 * properties. Outputting the WebRowSet object to XML using the standard
 * <code>writeXml</code> methods describes the internal properties as follows:
 * <PRE>
 * &lt;<font color=red>properties</font>&gt;
 *       &lt;<font color=red>command</font>&gt;select co1, col2 from test_table&lt;<font color=red>/command</font>&gt;
 *      &lt;<font color=red>concurrency</font>&gt;1&lt;<font color=red>/concurrency</font>&gt;
 *      &lt;<font color=red>datasource/</font>&gt;
 *      &lt;<font color=red>escape-processing</font>&gt;true&lt;<font color=red>/escape-processing</font>&gt;
 *      &lt;<font color=red>fetch-direction</font>&gt;0&lt;<font color=red>/fetch-direction</font>&gt;
 *      &lt;<font color=red>fetch-size</font>&gt;0&lt;<font color=red>/fetch-size</font>&gt;
 *      &lt;<font color=red>isolation-level</font>&gt;1&lt;<font color=red>/isolation-level</font>&gt;
 *      &lt;<font color=red>key-columns/</font>&gt;
 *      &lt;<font color=red>map/</font>&gt;
 *      &lt;<font color=red>max-field-size</font>&gt;0&lt;<font color=red>/max-field-size</font>&gt;
 *      &lt;<font color=red>max-rows</font>&gt;0&lt;<font color=red>/max-rows</font>&gt;
 *      &lt;<font color=red>query-timeout</font>&gt;0&lt;<font color=red>/query-timeout</font>&gt;
 *      &lt;<font color=red>read-only</font>&gt;false&lt;<font color=red>/read-only</font>&gt;
 *      &lt;<font color=red>rowset-type</font>&gt;TRANSACTION_READ_UNCOMMITED&lt;<font color=red>/rowset-type</font>&gt;
 *      &lt;<font color=red>show-deleted</font>&gt;false&lt;<font color=red>/show-deleted</font>&gt;
 *      &lt;<font color=red>table-name/</font>&gt;
 *      &lt;<font color=red>url</font>&gt;jdbc:thin:oracle&lt;<font color=red>/url</font>&gt;
 *      &lt;<font color=red>sync-provider</font>&gt;
 *              &lt;<font color=red>sync-provider-name</font>&gt;.com.rowset.provider.RIOptimisticProvider&lt;<font color=red>/sync-provider-name</font>&gt;
 *              &lt;<font color=red>sync-provider-vendor</font>&gt;Sun Microsystems&lt;<font color=red>/sync-provider-vendor</font>&gt;
 *              &lt;<font color=red>sync-provider-version</font>&gt;1.0&lt;<font color=red>/sync-provider-name</font>&gt;
 *              &lt;<font color=red>sync-provider-grade</font>&gt;LOW&lt;<font color=red>/sync-provider-grade</font>&gt;
 *              &lt;<font color=red>data-source-lock</font>&gt;NONE&lt;<font color=red>/data-source-lock</font>&gt;
 *      &lt;<font color=red>/sync-provider</font>&gt;
 * &lt;<font color=red>/properties</font>&gt;
 * </PRE>
 * The meta-data describing the make up of the WebRowSet is described
 * in XML as detailed below. Note both columns are described between the
 * <code>column-definition</code> tags.
 * <PRE>
 * &lt;<font color=red>metadata</font>&gt;
 *      &lt;<font color=red>column-count</font>&gt;2&lt;<font color=red>/column-count</font>&gt;
 *      &lt;<font color=red>column-definition</font>&gt;
 *              &lt;<font color=red>column-index</font>&gt;1&lt;<font color=red>/column-index</font>&gt;
 *              &lt;<font color=red>auto-increment</font>&gt;false&lt;<font color=red>/auto-increment</font>&gt;
 *              &lt;<font color=red>case-sensitive</font>&gt;true&lt;<font color=red>/case-sensitive</font>&gt;
 *              &lt;<font color=red>currency</font>&gt;false&lt;<font color=red>/currency</font>&gt;
 *              &lt;<font color=red>nullable</font>&gt;1&lt;<font color=red>/nullable</font>&gt;
 *              &lt;<font color=red>signed</font>&gt;false&lt;<font color=red>/signed</font>&gt;
 *              &lt;<font color=red>searchable</font>&gt;true&lt;<font color=red>/searchable</font>&gt;
 *              &lt;<font color=red>column-display-size</font>&gt;10&lt;<font color=red>/column-display-size</font>&gt;
 *              &lt;<font color=red>column-label</font>&gt;COL1&lt;<font color=red>/column-label</font>&gt;
 *              &lt;<font color=red>column-name</font>&gt;COL1&lt;<font color=red>/column-name</font>&gt;
 *              &lt;<font color=red>schema-name/</font>&gt;
 *              &lt;<font color=red>column-precision</font>&gt;10&lt;<font color=red>/column-precision</font>&gt;
 *              &lt;<font color=red>column-scale</font>&gt;0&lt;<font color=red>/column-scale</font>&gt;
 *              &lt;<font color=red>table-name/</font>&gt;
 *              &lt;<font color=red>catalog-name/</font>&gt;
 *              &lt;<font color=red>column-type</font>&gt;1&lt;<font color=red>/column-type</font>&gt;
 *              &lt;<font color=red>column-type-name</font>&gt;CHAR&lt;<font color=red>/column-type-name</font>&gt;
 *      &lt;<font color=red>/column-definition</font>&gt;
 *      &lt;<font color=red>column-definition</font>&gt;
 *              &lt;<font color=red>column-index</font>&gt;2&lt;<font color=red>/column-index</font>&gt;
 *              &lt;<font color=red>auto-increment</font>&gt;false&lt;<font color=red>/auto-increment</font>&gt;
 *              &lt;<font color=red>case-sensitive</font>&gt;false&lt;<font color=red>/case-sensitive</font>&gt;
 *              &lt;<font color=red>currency</font>&gt;false&lt;<font color=red>/currency</font>&gt;
 *              &lt;<font color=red>nullable</font>&gt;1&lt;<font color=red>/nullable</font>&gt;
 *              &lt;<font color=red>signed</font>&gt;true&lt;<font color=red>/signed</font>&gt;
 *              &lt;<font color=red>searchable</font>&gt;true&lt;<font color=red>/searchable</font>&gt;
 *              &lt;<font color=red>column-display-size</font>&gt;39&lt;<font color=red>/column-display-size</font>&gt;
 *              &lt;<font color=red>column-label</font>&gt;COL2&lt;<font color=red>/column-label</font>&gt;
 *              &lt;<font color=red>column-name</font>&gt;COL2&lt;<font color=red>/column-name</font>&gt;
 *              &lt;<font color=red>schema-name/</font>&gt;
 *              &lt;<font color=red>column-precision</font>&gt;38&lt;<font color=red>/column-precision</font>&gt;
 *              &lt;<font color=red>column-scale</font>&gt;0&lt;<font color=red>/column-scale</font>&gt;
 *              &lt;<font color=red>table-name/</font>&gt;
 *              &lt;<font color=red>catalog-name/</font>&gt;
 *              &lt;<font color=red>column-type</font>&gt;3&lt;<font color=red>/column-type</font>&gt;
 *              &lt;<font color=red>column-type-name</font>&gt;NUMBER&lt;<font color=red>/column-type-name</font>&gt;
 *      &lt;<font color=red>/column-definition</font>&gt;
 * &lt;<font color=red>/metadata</font>&gt;
 * </PRE>
 * Having detailed how the properties and metadata are described, the following details
 * how the contents of a <code>WebRowSet</code> object is described in XML. Note, that
 * this describes a <code>WebRowSet</code> object that has not undergone any
 * modifications since its instantiation.
 * A <code>currentRow</code> tag is mapped to each row of the table structure that the
 * <code>WebRowSet</code> object provides. A <code>columnValue</code> tag may contain
 * either the <code>stringData</code> or <code>binaryData</code> tag, according to
 * the SQL type that
 * the XML value is mapping back to. The <code>binaryData</code> tag contains data in the
 * Base64 encoding and is typically used for <code>BLOB</code> and <code>CLOB</code> type data.
 * <PRE>
 * &lt;<font color=red>data</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      firstrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      1
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      secondrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      2
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      thirdrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      3
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fourthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      4
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 * &lt;<font color=red>/data</font>&gt;
 * </PRE>
 * <h4>2.2 State 2 - Deleting a Row</h4>
 * Deleting a row in a <code>WebRowSet</code> object involves simply moving to the row
 * to be deleted and then calling the method <code>deleteRow</code>, as in any other
 * <code>RowSet</code> object.  The following
 * two lines of code, in which <i>wrs</i> is a <code>WebRowSet</code> object, delete
 * the third row.
 * <PRE>
 *     wrs.absolute(3);
 *     wrs.deleteRow();
 * </PRE>
 * The XML description shows the third row is marked as a <code>deleteRow</code>,
 *  which eliminates the third row in the <code>WebRowSet</code> object.
 * <PRE>
 * &lt;<font color=red>data</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      firstrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      1
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      secondrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      2
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>deleteRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      thirdrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      3
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/deleteRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fourthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      4
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 * &lt;<font color=red>/data</font>&gt;
 * </PRE>
 * <h4>2.3 State 3 - Inserting a Row</h4>
 * A <code>WebRowSet</code> object can insert a new row by moving to the insert row,
 * calling the appropriate updater methods for each column in the row, and then
 * calling the method <code>insertRow</code>.
 * <PRE>
 * wrs.moveToInsertRow();
 * wrs.updateString(1, "fifththrow");
 * wrs.updateString(2, "5");
 * wrs.insertRow();
 * </PRE>
 * The following code fragment changes the second column value in the row just inserted.
 * Note that this code applies when new rows are inserted right after the current row,
 * which is why the method <code>next</code> moves the cursor to the correct row.
 * Calling the method <code>acceptChanges</code> writes the change to the data source.
 *
 * <PRE>
 * wrs.moveToCurrentRow();
 * wrs.next();
 * wrs.updateString(2, "V");
 * wrs.acceptChanges();
 * :
 * </PRE>
 * Describing this in XML demonstrates where the Java code inserts a new row and then
 * performs an update on the newly inserted row on an individual field.
 * <PRE>
 * &lt;<font color=red>data</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      firstrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      1
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      secondrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      2
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      newthirdrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      III
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>insertRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fifthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      5
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>updateValue</font>&gt;
 *                      V
 *              &lt;<font color=red>/updateValue</font>&gt;
 *      &lt;<font color=red>/insertRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fourthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      4
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 * &lt;<font color=red>/date</font>&gt;
 * </PRE>
 * <h4>2.4 State 4 - Modifying a Row</h4>
 * Modifying a row produces specific XML that records both the new value and the
 * value that was replaced.  The value that was replaced becomes the original value,
 * and the new value becomes the current value. The following
 * code moves the cursor to a specific row, performs some modifications, and updates
 * the row when complete.
 * <PRE>
 * wrs.absolute(5);
 * wrs.updateString(1, "new4thRow");
 * wrs.updateString(2, "IV");
 * wrs.updateRow();
 * </PRE>
 * In XML, this is described by the <code>modifyRow</code> tag. Both the original and new
 * values are contained within the tag for original row tracking purposes.
 * <PRE>
 * &lt;<font color=red>data</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      firstrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      1
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      secondrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      2
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      newthirdrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      III
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>currentRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fifthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      5
 *              &lt;<font color=red>/columnValue</font>&gt;
 *      &lt;<font color=red>/currentRow</font>&gt;
 *      &lt;<font color=red>modifyRow</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      fourthrow
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>updateValue</font>&gt;
 *                      new4thRow
 *              &lt;<font color=red>/updateValue</font>&gt;
 *              &lt;<font color=red>columnValue</font>&gt;
 *                      4
 *              &lt;<font color=red>/columnValue</font>&gt;
 *              &lt;<font color=red>updateValue</font>&gt;
 *                      IV
 *              &lt;<font color=red>/updateValue</font>&gt;
 *      &lt;<font color=red>/modifyRow</font>&gt;
 * &lt;<font color=red>/data</font>&gt;
 * </PRE>
 *
 * @see javax.sql.rowset.JdbcRowSet
 * @see javax.sql.rowset.CachedRowSet
 * @see javax.sql.rowset.FilteredRowSet
 * @see javax.sql.rowset.JoinRowSet
 */

public interface WebRowSet extends CachedRowSet {

   /**
    * Reads a <code>WebRowSet</code> object in its XML format from the given
    * <code>Reader</code> object.
    *
    * @param reader the <code>java.io.Reader</code> stream from which this
    *        <code>WebRowSet</code> object will be populated

    * @throws SQLException if a database access error occurs
    */
    public void readXml(java.io.Reader reader) throws SQLException;

    /**
     * Reads a stream based XML input to populate this <code>WebRowSet</code>
     * object.
     *
     * @param iStream the <code>java.io.InputStream</code> from which this
     *        <code>WebRowSet</code> object will be populated
     * @throws SQLException if a data source access error occurs
     * @throws IOException if an IO exception occurs
     */
    public void readXml(java.io.InputStream iStream) throws SQLException, IOException;

   /**
    * Populates this <code>WebRowSet</code> object with
    * the contents of the given <code>ResultSet</code> object and writes its
    * data, properties, and metadata
    * to the given <code>Writer</code> object in XML format.
    * <p>
    * NOTE: The <code>WebRowSet</code> cursor may be moved to write out the
    * contents to the XML data source. If implemented in this way, the cursor <b>must</b>
    * be returned to its position just prior to the <code>writeXml()</code> call.
    *
    * @param rs the <code>ResultSet</code> object with which to populate this
    *        <code>WebRowSet</code> object
    * @param writer the <code>java.io.Writer</code> object to write to.
    * @throws SQLException if an error occurs writing out the rowset
    *          contents in XML format
    */
    public void writeXml(ResultSet rs, java.io.Writer writer) throws SQLException;

   /**
    * Populates this <code>WebRowSet</code> object with
    * the contents of the given <code>ResultSet</code> object and writes its
    * data, properties, and metadata
    * to the given <code>OutputStream</code> object in XML format.
    * <p>
    * NOTE: The <code>WebRowSet</code> cursor may be moved to write out the
    * contents to the XML data source. If implemented in this way, the cursor <b>must</b>
    * be returned to its position just prior to the <code>writeXml()</code> call.
    *
    * @param rs the <code>ResultSet</code> object with which to populate this
    *        <code>WebRowSet</code> object
    * @param oStream the <code>java.io.OutputStream</code> to write to
    * @throws SQLException if a data source access error occurs
    * @throws IOException if a IO exception occurs
    */
    public void writeXml(ResultSet rs, java.io.OutputStream oStream) throws SQLException, IOException;

   /**
    * Writes the data, properties, and metadata for this <code>WebRowSet</code> object
    * to the given <code>Writer</code> object in XML format.
    *
    * @param writer the <code>java.io.Writer</code> stream to write to
    * @throws SQLException if an error occurs writing out the rowset
    *          contents to XML
    */
    public void writeXml(java.io.Writer writer) throws SQLException;

    /**
     * Writes the data, properties, and metadata for this <code>WebRowSet</code> object
     * to the given <code>OutputStream</code> object in XML format.
     *
     * @param oStream the <code>java.io.OutputStream</code> stream to write to
     * @throws SQLException if a data source access error occurs
     * @throws IOException if a IO exception occurs
     */
    public void writeXml(java.io.OutputStream oStream) throws SQLException, IOException;

    /**
     * The public identifier for the XML Schema definition that defines the XML
     * tags and their valid values for a <code>WebRowSet</code> implementation.
     */
    public static String PUBLIC_XML_SCHEMA =
        "--//Sun Microsystems, Inc.//XSD Schema//EN";

    /**
     * The URL for the XML Schema definition file that defines the XML tags and
     * their valid values for a <code>WebRowSet</code> implementation.
     */
    public static String SCHEMA_SYSTEM_ID = "http://java.sun.com/xml/ns/jdbc/webrowset.xsd";
}
