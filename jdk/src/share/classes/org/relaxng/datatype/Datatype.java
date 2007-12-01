/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package org.relaxng.datatype;

/**
 * Datatype object.
 *
 * This object has the following functionality:
 *
 * <ol>
 *  <li> functionality to identify a class of character sequences. This is
 *       done through the isValid method.
 *
 *  <li> functionality to produce a "value object" from a character sequence and
 *               context information.
 *
 *  <li> functionality to test the equality of two value objects.
 * </ol>
 *
 * This interface also defines the createStreamingValidator method,
 * which is intended to efficiently support the validation of
 * large character sequences.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface Datatype {

        /**
         * Checks if the specified 'literal' matches this Datatype
         * with respect to the current context.
         *
         * @param literal
         *              the lexical representation to be checked.
         * @param context
         *              If this datatype is context-dependent
         *              (i.e. the {@link #isContextDependent} method returns true),
         *              then the caller must provide a non-null valid context object.
         *              Otherwise, the caller can pass null.
         *
         * @return
         *              true if the 'literal' is a member of this Datatype;
         *              false if it's not a member of this Datatype.
         */
        boolean isValid( String literal, ValidationContext context );

        /**
         * Similar to the isValid method but throws an exception with diagnosis
         * in case of errors.
         *
         * <p>
         * If the specified 'literal' is a valid lexical representation for this
         * datatype, then this method must return without throwing any exception.
         * If not, the callee must throw an exception (with diagnosis message,
         * if possible.)
         *
         * <p>
         * The application can use this method to provide detailed error message
         * to users. This method is kept separate from the isValid method to
         * achieve higher performance during normal validation.
         *
         * @exception DatatypeException
         *              If the given literal is invalid, then this exception is thrown.
         *              If the callee supports error diagnosis, then the exception should
         *              contain a diagnosis message.
         */
        void checkValid( String literal, ValidationContext context )
                throws DatatypeException;

        /**
         * Creates an instance of a streaming validator for this type.
         *
         * <p>
         * By using streaming validators instead of the isValid method,
         * the caller can avoid keeping the entire string, which is
         * sometimes quite big, in memory.
         *
         * @param context
         *              If this datatype is context-dependent
         *              (i.e. the {@link #isContextDependent} method returns true),
         *              then the caller must provide a non-null valid context object.
         *              Otherwise, the caller can pass null.
         *              The callee may keep a reference to this context object
         *              only while the returned streaming validator is being used.
         */
        DatatypeStreamingValidator createStreamingValidator( ValidationContext context );

        /**
         * Converts lexcial value and the current context to the corresponding
         * value object.
         *
         * <p>
         * The caller cannot generally assume that the value object is
         * a meaningful Java object. For example, the caller cannot expect
         * this method to return <code>java.lang.Number</code> type for
         * the "integer" type of XML Schema Part 2.
         *
         * <p>
         * Also, the caller cannot assume that the equals method and
         * the hashCode method of the value object are consistent with
         * the semantics of the datatype. For that purpose, the sameValue
         * method and the valueHashCode method have to be used. Note that
         * this means you cannot use classes like
         * <code>java.util.Hashtable</code> to store the value objects.
         *
         * <p>
         * The returned value object should be used solely for the sameValue
         * and valueHashCode methods.
         *
         * @param context
         *              If this datatype is context-dependent
         *              (when the {@link #isContextDependent} method returns true),
         *              then the caller must provide a non-null valid context object.
         *              Otherwise, the caller can pass null.
         *
         * @return      null
         *              when the given lexical value is not a valid lexical
         *              value for this type.
         */
        Object createValue( String literal, ValidationContext context );

        /**
         * Tests the equality of two value objects which were originally
         * created by the createValue method of this object.
         *
         * The behavior is undefined if objects not created by this type
         * are passed. It is the caller's responsibility to ensure that
         * value objects belong to this type.
         *
         * @return
         *              true if two value objects are considered equal according to
         *              the definition of this datatype; false if otherwise.
         */
        boolean sameValue( Object value1, Object value2 );


        /**
         * Computes the hash code for a value object,
         * which is consistent with the sameValue method.
         *
         * @return
         *              hash code for the specified value object.
         */
        int valueHashCode( Object value );




        /**
         * Indicates that the datatype doesn't have ID/IDREF semantics.
         *
         * This value is one of the possible return values of the
         * {@link #getIdType} method.
         */
        public static final int ID_TYPE_NULL = 0;

        /**
         * Indicates that RELAX NG compatibility processors should
         * treat this datatype as having ID semantics.
         *
         * This value is one of the possible return values of the
         * {@link #getIdType} method.
         */
        public static final int ID_TYPE_ID = 1;

        /**
         * Indicates that RELAX NG compatibility processors should
         * treat this datatype as having IDREF semantics.
         *
         * This value is one of the possible return values of the
         * {@link #getIdType} method.
         */
        public static final int ID_TYPE_IDREF = 2;

        /**
         * Indicates that RELAX NG compatibility processors should
         * treat this datatype as having IDREFS semantics.
         *
         * This value is one of the possible return values of the
         * {@link #getIdType} method.
         */
        public static final int ID_TYPE_IDREFS = 3;

        /**
         * Checks if the ID/IDREF semantics is associated with this
         * datatype.
         *
         * <p>
         * This method is introduced to support the RELAX NG DTD
         * compatibility spec. (Of course it's always free to use
         * this method for other purposes.)
         *
         * <p>
         * If you are implementing a datatype library and have no idea about
         * the "RELAX NG DTD compatibility" thing, just return
         * <code>ID_TYPE_NULL</code> is fine.
         *
         * @return
         *              If this datatype doesn't have any ID/IDREF semantics,
         *              it returns {@link #ID_TYPE_NULL}. If it has such a semantics
         *              (for example, XSD:ID, XSD:IDREF and comp:ID type), then
         *              it returns {@link #ID_TYPE_ID}, {@link #ID_TYPE_IDREF} or
         *              {@link #ID_TYPE_IDREFS}.
         */
        public int getIdType();


        /**
         * Checks if this datatype may need a context object for
         * the validation.
         *
         * <p>
         * The callee must return true even when the context
         * is not always necessary. (For example, the "QName" type
         * doesn't need a context object when validating unprefixed
         * string. But nonetheless QName must return true.)
         *
         * <p>
         * XSD's <code>string</code> and <code>short</code> types
         * are examples of context-independent datatypes.
         * Its <code>QName</code> and <code>ENTITY</code> types
         * are examples of context-dependent datatypes.
         *
         * <p>
         * When a datatype is context-independent, then
         * the {@link #isValid} method, the {@link #checkValid} method,
         * the {@link #createStreamingValidator} method and
         * the {@link #createValue} method can be called without
         * providing a context object.
         *
         * @return
         *              <b>true</b> if this datatype is context-dependent
         *              (it needs a context object sometimes);
         *
         *              <b>false</b> if this datatype is context-<b>in</b>dependent
         *              (it never needs a context object).
         */
        public boolean isContextDependent();
}
