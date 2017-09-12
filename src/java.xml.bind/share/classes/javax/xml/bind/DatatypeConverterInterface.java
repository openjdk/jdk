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

/**
 * <p>
 * The DatatypeConverterInterface is for JAXB provider use only. A
 * JAXB provider must supply a class that implements this interface.
 * JAXB Providers are required to call the
 * {@link DatatypeConverter#setDatatypeConverter(DatatypeConverterInterface)
 * DatatypeConverter.setDatatypeConverter} api at
 * some point before the first marshal or unmarshal operation (perhaps during
 * the call to JAXBContext.newInstance).  This step is necessary to configure
 * the converter that should be used to perform the print and parse
 * functionality.  Calling this api repeatedly will have no effect - the
 * DatatypeConverter instance passed into the first invocation is the one that
 * will be used from then on.
 *
 * <p>
 * This interface defines the parse and print methods. There is one
 * parse and print method for each XML schema datatype specified in the
 * the default binding Table 5-1 in the JAXB specification.
 *
 * <p>
 * The parse and print methods defined here are invoked by the static parse
 * and print methods defined in the {@link DatatypeConverter DatatypeConverter}
 * class.
 *
 * <p>
 * A parse method for a XML schema datatype must be capable of converting any
 * lexical representation of the XML schema datatype ( specified by the
 * <a href="http://www.w3.org/TR/xmlschema-2/"> XML Schema Part2: Datatypes
 * specification</a> into a value in the value space of the XML schema datatype.
 * If an error is encountered during conversion, then an IllegalArgumentException
 * or a subclass of IllegalArgumentException must be thrown by the method.
 *
 * <p>
 * A print method for a XML schema datatype can output any lexical
 * representation that is valid with respect to the XML schema datatype.
 * If an error is encountered during conversion, then an IllegalArgumentException,
 * or a subclass of IllegalArgumentException must be thrown by the method.
 *
 * <p>
 * The prefix xsd: is used to refer to XML schema datatypes
 * <a href="http://www.w3.org/TR/xmlschema-2/"> XML Schema Part2: Datatypes
 * specification.</a>
 *
 * @author <ul>
 *         <li>Sekhar Vajjhala, Sun Microsystems, Inc.</li>
 *         <li>Joe Fialli, Sun Microsystems Inc.</li>
 *         <li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li>
 *         <li>Ryan Shoemaker,Sun Microsystems Inc.</li>
 *         </ul>
 * @see DatatypeConverter
 * @see ParseConversionEvent
 * @see PrintConversionEvent
 * @since 1.6, JAXB 1.0
 */

public interface DatatypeConverterInterface {
    /**
     * Convert the string argument into a string.
     * @param lexicalXSDString
     *     A lexical representation of the XML Schema datatype xsd:string
     * @return
     *     A string that is the same as the input string.
     */
    public String parseString( String lexicalXSDString );

    /**
     * Convert the string argument into a BigInteger value.
     * @param lexicalXSDInteger
     *     A string containing a lexical representation of
     *     xsd:integer.
     * @return
     *     A BigInteger value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDInteger} is not a valid string representation of a {@link java.math.BigInteger} value.
     */
    public java.math.BigInteger parseInteger( String lexicalXSDInteger );

    /**
     * Convert the string argument into an int value.
     * @param lexicalXSDInt
     *     A string containing a lexical representation of
     *     xsd:int.
     * @return
     *     An int value represented byte the string argument.
     * @throws NumberFormatException {@code lexicalXSDInt} is not a valid string representation of an {@code int} value.
     */
    public int parseInt( String lexicalXSDInt );

    /**
     * Converts the string argument into a long value.
     * @param lexicalXSDLong
     *     A string containing lexical representation of
     *     xsd:long.
     * @return
     *     A long value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDLong} is not a valid string representation of a {@code long} value.
     */
    public long parseLong( String lexicalXSDLong );

    /**
     * Converts the string argument into a short value.
     * @param lexicalXSDShort
     *     A string containing lexical representation of
     *     xsd:short.
     * @return
     *     A short value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDShort} is not a valid string representation of a {@code short} value.
     */
    public short parseShort( String lexicalXSDShort );

    /**
     * Converts the string argument into a BigDecimal value.
     * @param lexicalXSDDecimal
     *     A string containing lexical representation of
     *     xsd:decimal.
     * @return
     *     A BigDecimal value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDDecimal} is not a valid string representation of {@link java.math.BigDecimal}.
     */
    public java.math.BigDecimal parseDecimal( String lexicalXSDDecimal );

    /**
     * Converts the string argument into a float value.
     * @param lexicalXSDFloat
     *     A string containing lexical representation of
     *     xsd:float.
     * @return
     *     A float value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDFloat} is not a valid string representation of a {@code float} value.
     */
    public float parseFloat( String lexicalXSDFloat );

    /**
     * Converts the string argument into a double value.
     * @param lexicalXSDDouble
     *     A string containing lexical representation of
     *     xsd:double.
     * @return
     *     A double value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDDouble} is not a valid string representation of a {@code double} value.
     */
    public double parseDouble( String lexicalXSDDouble );

    /**
     * Converts the string argument into a boolean value.
     * @param lexicalXSDBoolean
     *     A string containing lexical representation of
     *     xsd:boolean.
     * @return
     *     A boolean value represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:boolean.
     */
    public boolean parseBoolean( String lexicalXSDBoolean );

    /**
     * Converts the string argument into a byte value.
     * @param lexicalXSDByte
     *     A string containing lexical representation of
     *     xsd:byte.
     * @return
     *     A byte value represented by the string argument.
     * @throws NumberFormatException {@code lexicalXSDByte} does not contain a parseable byte.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:byte.
     */
    public byte parseByte( String lexicalXSDByte );

    /**
     * Converts the string argument into a QName value.
     *
     * <p>
     * String parameter {@code lexicalXSDQname} must conform to lexical value space specifed at
     * <a href="http://www.w3.org/TR/xmlschema-2/#QName">XML Schema Part 2:Datatypes specification:QNames</a>
     *
     * @param lexicalXSDQName
     *     A string containing lexical representation of xsd:QName.
     * @param nsc
     *     A namespace context for interpreting a prefix within a QName.
     * @return
     *     A QName value represented by the string argument.
     * @throws IllegalArgumentException  if string parameter does not conform to XML Schema Part 2 specification or
     *      if namespace prefix of {@code lexicalXSDQname} is not bound to a URI in NamespaceContext {@code nsc}.
     */
    public javax.xml.namespace.QName parseQName( String lexicalXSDQName,
                                             javax.xml.namespace.NamespaceContext nsc);

    /**
     * Converts the string argument into a Calendar value.
     * @param lexicalXSDDateTime
     *     A string containing lexical representation of
     *     xsd:datetime.
     * @return
     *     A Calendar object represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:dateTime.
     */
    public java.util.Calendar parseDateTime( String lexicalXSDDateTime );

    /**
     * Converts the string argument into an array of bytes.
     * @param lexicalXSDBase64Binary
     *     A string containing lexical representation
     *     of xsd:base64Binary.
     * @return
     *     An array of bytes represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:base64Binary
     */
    public byte[] parseBase64Binary( String lexicalXSDBase64Binary );

    /**
     * Converts the string argument into an array of bytes.
     * @param lexicalXSDHexBinary
     *     A string containing lexical representation of
     *     xsd:hexBinary.
     * @return
     *     An array of bytes represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:hexBinary.
     */
    public byte[] parseHexBinary( String lexicalXSDHexBinary );

    /**
     * Converts the string argument into a long value.
     * @param lexicalXSDUnsignedInt
     *     A string containing lexical representation
     *     of xsd:unsignedInt.
     * @return
     *     A long value represented by the string argument.
     * @throws NumberFormatException if string parameter can not be parsed into a {@code long} value.
     */
    public long parseUnsignedInt( String lexicalXSDUnsignedInt );

    /**
     * Converts the string argument into an int value.
     * @param lexicalXSDUnsignedShort
     *     A string containing lexical
     *     representation of xsd:unsignedShort.
     * @return
     *     An int value represented by the string argument.
     * @throws NumberFormatException if string parameter can not be parsed into an {@code int} value.
     */
    public int parseUnsignedShort( String lexicalXSDUnsignedShort );

    /**
     * Converts the string argument into a Calendar value.
     * @param lexicalXSDTime
     *     A string containing lexical representation of
     *     xsd:Time.
     * @return
     *     A Calendar value represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:Time.
     */
    public java.util.Calendar parseTime( String lexicalXSDTime );

    /**
     * Converts the string argument into a Calendar value.
     * @param lexicalXSDDate
     *     A string containing lexical representation of
     *     xsd:Date.
     * @return
     *     A Calendar value represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space defined in XML Schema Part 2: Datatypes for xsd:Date.
     */
    public java.util.Calendar parseDate( String lexicalXSDDate );

    /**
     * Return a string containing the lexical representation of the
     * simple type.
     * @param lexicalXSDAnySimpleType
     *     A string containing lexical
     *     representation of the simple type.
     * @return
     *     A string containing the lexical representation of the
     *     simple type.
     */
    public String parseAnySimpleType( String lexicalXSDAnySimpleType );

    /**
     * Converts the string argument into a string.
     * @param val
     *     A string value.
     * @return
     *     A string containing a lexical representation of xsd:string
     */
    public String printString( String val );

    /**
     * Converts a BigInteger value into a string.
     * @param val
     *     A BigInteger value
     * @return
     *     A string containing a lexical representation of xsd:integer
     * @throws IllegalArgumentException {@code val} is null.
     */
    public String printInteger( java.math.BigInteger val );

    /**
     * Converts an int value into a string.
     * @param val
     *     An int value
     * @return
     *     A string containing a lexical representation of xsd:int
     */
    public String printInt( int val );


    /**
     * Converts a long value into a string.
     * @param val
     *     A long value
     * @return
     *     A string containing a lexical representation of xsd:long
     */
    public String printLong( long val );

    /**
     * Converts a short value into a string.
     * @param val
     *     A short value
     * @return
     *     A string containing a lexical representation of xsd:short
     */
    public String printShort( short val );

    /**
     * Converts a BigDecimal value into a string.
     * @param val
     *     A BigDecimal value
     * @return
     *     A string containing a lexical representation of xsd:decimal
     * @throws IllegalArgumentException {@code val} is null.
     */
    public String printDecimal( java.math.BigDecimal val );

    /**
     * Converts a float value into a string.
     * @param val
     *     A float value
     * @return
     *     A string containing a lexical representation of xsd:float
     */
    public String printFloat( float val );

    /**
     * Converts a double value into a string.
     * @param val
     *     A double value
     * @return
     *     A string containing a lexical representation of xsd:double
     */
    public String printDouble( double val );

    /**
     * Converts a boolean value into a string.
     * @param val
     *     A boolean value
     * @return
     *     A string containing a lexical representation of xsd:boolean
     */
    public String printBoolean( boolean val );

    /**
     * Converts a byte value into a string.
     * @param val
     *     A byte value
     * @return
     *     A string containing a lexical representation of xsd:byte
     */
    public String printByte( byte val );

    /**
     * Converts a QName instance into a string.
     * @param val
     *     A QName value
     * @param nsc
     *     A namespace context for interpreting a prefix within a QName.
     * @return
     *     A string containing a lexical representation of QName
     * @throws IllegalArgumentException if {@code val} is null or
     * if {@code nsc} is non-null or {@code nsc.getPrefix(nsprefixFromVal)} is null.
     */
    public String printQName( javax.xml.namespace.QName val,
                              javax.xml.namespace.NamespaceContext nsc );

    /**
     * Converts a Calendar value into a string.
     * @param val
     *     A Calendar value
     * @return
     *     A string containing a lexical representation of xsd:dateTime
     * @throws IllegalArgumentException if {@code val} is null.
     */
    public String printDateTime( java.util.Calendar val );

    /**
     * Converts an array of bytes into a string.
     * @param val
     *     an array of bytes
     * @return
     *     A string containing a lexical representation of xsd:base64Binary
     * @throws IllegalArgumentException if {@code val} is null.
     */
    public String printBase64Binary( byte[] val );

    /**
     * Converts an array of bytes into a string.
     * @param val
     *     an array of bytes
     * @return
     *     A string containing a lexical representation of xsd:hexBinary
     * @throws IllegalArgumentException if {@code val} is null.
     */
    public String printHexBinary( byte[] val );

    /**
     * Converts a long value into a string.
     * @param val
     *     A long value
     * @return
     *     A string containing a lexical representation of xsd:unsignedInt
     */
    public String printUnsignedInt( long val );

    /**
     * Converts an int value into a string.
     * @param val
     *     An int value
     * @return
     *     A string containing a lexical representation of xsd:unsignedShort
     */
    public String printUnsignedShort( int val );

    /**
     * Converts a Calendar value into a string.
     * @param val
     *     A Calendar value
     * @return
     *     A string containing a lexical representation of xsd:time
     * @throws IllegalArgumentException if {@code val} is null.
     */
    public String printTime( java.util.Calendar val );

    /**
     * Converts a Calendar value into a string.
     * @param val
     *     A Calendar value
     * @return
     *     A string containing a lexical representation of xsd:date
     * @throws IllegalArgumentException if {@code val} is null.
     */
    public String printDate( java.util.Calendar val );

    /**
     * Converts a string value into a string.
     * @param val
     *     A string value
     * @return
     *     A string containing a lexical representation of xsd:AnySimpleType
     */
    public String printAnySimpleType( String val );
}
