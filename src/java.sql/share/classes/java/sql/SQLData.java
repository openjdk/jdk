/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.sql;

/**
 * The interface used for the custom mapping of an SQL user-defined type (UDT) to
 * a class in the Java programming language. The class object for a class
 * implementing the <code>SQLData</code> interface will be entered in the
 * appropriate <code>Connection</code> object's type map along with the SQL
 * name of the UDT for which it is a custom mapping.
 * <P>
 * Typically, a <code>SQLData</code> implementation
 * will define a field for each attribute of an SQL structured type or a
 * single field for an SQL <code>DISTINCT</code> type. When the UDT is
 * retrieved from a data source with the <code>ResultSet.getObject</code>
 * method, it will be mapped as an instance of this class.  A programmer
 * can operate on this class instance just as on any other object in the
 * Java programming language and then store any changes made to it by
 * calling the <code>PreparedStatement.setObject</code> method,
 * which will map it back to the SQL type.
 * <p>
 * It is expected that the implementation of the class for a custom
 * mapping will be done by a tool.  In a typical implementation, the
 * programmer would simply supply the name of the SQL UDT, the name of
 * the class to which it is being mapped, and the names of the fields to
 * which each of the attributes of the UDT is to be mapped.  The tool will use
 * this information to implement the <code>SQLData.readSQL</code> and
 * <code>SQLData.writeSQL</code> methods.  The <code>readSQL</code> method
 * calls the appropriate <code>SQLInput</code> methods to read
 * each attribute from an <code>SQLInput</code> object, and the
 * <code>writeSQL</code> method calls <code>SQLOutput</code> methods
 * to write each attribute back to the data source via an
 * <code>SQLOutput</code> object.
 * <P>
 * An application programmer will not normally call <code>SQLData</code> methods
 * directly, and the <code>SQLInput</code> and <code>SQLOutput</code> methods
 * are called internally by <code>SQLData</code> methods, not by application code.
 *
 * @since 1.2
 */
public interface SQLData {

 /**
  * Returns the fully-qualified
  * name of the SQL user-defined type that this object represents.
  * This method is called by the JDBC driver to get the name of the
  * UDT instance that is being mapped to this instance of
  * <code>SQLData</code>.
  *
  * @return the type name that was passed to the method <code>readSQL</code>
  *            when this object was constructed and populated
  * @exception SQLException if there is a database access error
  * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
  * this method
  * @since 1.2
  */
  String getSQLTypeName() throws SQLException;

 /**
  * Populates this object with data read from the database.
  * The implementation of the method must follow this protocol:
  * <UL>
  * <LI>It must read each of the attributes or elements of the SQL
  * type  from the given input stream.  This is done
  * by calling a method of the input stream to read each
  * item, in the order that they appear in the SQL definition
  * of the type.
  * <LI>The method <code>readSQL</code> then
  * assigns the data to appropriate fields or
  * elements (of this or other objects).
  * Specifically, it must call the appropriate <i>reader</i> method
  * (<code>SQLInput.readString</code>, <code>SQLInput.readBigDecimal</code>,
  * and so on) method(s) to do the following:
  * for a distinct type, read its single data element;
  * for a structured type, read a value for each attribute of the SQL type.
  * </UL>
  * The JDBC driver initializes the input stream with a type map
  * before calling this method, which is used by the appropriate
  * <code>SQLInput</code> reader method on the stream.
  *
  * @param stream the <code>SQLInput</code> object from which to read the data for
  * the value that is being custom mapped
  * @param typeName the SQL type name of the value on the data stream
  * @exception SQLException if there is a database access error
  * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
  * this method
  * @see SQLInput
  * @since 1.2
  */
  void readSQL (SQLInput stream, String typeName) throws SQLException;

  /**
  * Writes this object to the given SQL data stream, converting it back to
  * its SQL value in the data source.
  * The implementation of the method must follow this protocol:<BR>
  * It must write each of the attributes of the SQL type
  * to the given output stream.  This is done by calling a
  * method of the output stream to write each item, in the order that
  * they appear in the SQL definition of the type.
  * Specifically, it must call the appropriate <code>SQLOutput</code> writer
  * method(s) (<code>writeInt</code>, <code>writeString</code>, and so on)
  * to do the following: for a Distinct Type, write its single data element;
  * for a Structured Type, write a value for each attribute of the SQL type.
  *
  * @param stream the <code>SQLOutput</code> object to which to write the data for
  * the value that was custom mapped
  * @exception SQLException if there is a database access error
  * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
  * this method
  * @see SQLOutput
  * @since 1.2
  */
  void writeSQL (SQLOutput stream) throws SQLException;
}
