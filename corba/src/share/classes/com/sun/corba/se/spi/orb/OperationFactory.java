/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.orb ;

import java.util.StringTokenizer ;

import java.lang.reflect.Array ;

import java.net.URL ;
import java.net.MalformedURLException ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.orbutil.ORBClassLoader ;
import com.sun.corba.se.impl.orbutil.ObjectUtility ;

/** This is a static factory class for commonly used operations
* for property parsing.  The following operations are supported:
* <ul>
* <li>maskErrorAction( Operation op ) executes op and returns the result.  If op throws an
* exception, the result is null.
* <li>indexAction( int arg ) returns the [arg] element of value, which must be an Object[]</li>
* <li>identityAction() return the value</li>
* <li>booleanAction() return a Boolean representing true or false values of the String value</li>
* <li>integerAction() returns an Integer for the String value, which must be a decimal integer</li>
* <li>stringAction() returns the String value</li>
* <li>classAction() returns a class for the String value, as loaded by the ORB classloader</li>
* <li>setFlagAction() always return Boolean.TRUE</li>
* <li>URLAction() returns a java.net.URL for the String value, which must be a valid URL</li>
* <li>integerRangeAction( int min, int max ) returns an Integer for the String value, which must be a
* decimal integer in the range min to max inclusive</li>
* <li>listAction( String sep, Operation ) tokenizes the String value with sep as separator, then
* applies the Operation to each token, and returns an array of the result</li>
* <li>sequenceAction( String, Operation[] ) tokenizes the String value with sep as separator, then
* applies each Operation in the Operation array to successive tokens, and returns an array of the results</li>
* <li>compose( Operation op1, Operation op2 ) is the operation that applies op2 to the result of applying
* op1 to the value</li>
* <li>mapAction( Operation ) applies the Operation to each element of an array of objects, and returns
* an array of the results</li>
* <li>mapSequenceAction( Operation[] ) applies the corresponding element of the Operation array to an
* element of the Object[] value, and returns an array of the results</li>
* <li>convertIntegerToShort coerces an Integer into a Short.</li>
* </ul>
* Other operations can be directly defined, and combined using these basic operations.
*/
public abstract class OperationFactory {
    private OperationFactory() {}

    private static String getString( Object obj )
    {
        if (obj instanceof String)
            return (String)obj ;
        else
            throw new Error( "String expected" ) ;
    }

    private static Object[] getObjectArray( Object obj )
    {
        if (obj instanceof Object[])
            return (Object[])obj ;
        else
            throw new Error( "Object[] expected" ) ;
    }

    private static StringPair getStringPair( Object obj )
    {
        if (obj instanceof StringPair)
            return (StringPair)obj ;
        else
            throw new Error( "StringPair expected" ) ;
    }

    private static abstract class OperationBase implements Operation{
        public boolean equals( Object obj )
        {
            if (this==obj)
                return true ;

            if (!(obj instanceof OperationBase))
                return false ;

            OperationBase other = (OperationBase)obj ;

            return toString().equals( other.toString() ) ;
        }

        public int hashCode()
        {
            return toString().hashCode() ;
        }
    }

    private static class MaskErrorAction extends OperationBase
    {
        private Operation op ;

        public MaskErrorAction( Operation op )
        {
            this.op = op ;
        }

        public Object operate( Object arg )
        {
            try {
                return op.operate( arg ) ;
            } catch (java.lang.Exception exc) {
                return null ;
            }
        }

        public String toString()
        {
            return "maskErrorAction(" + op + ")" ;
        }
    }

    public static Operation maskErrorAction( Operation op )
    {
        return new MaskErrorAction( op ) ;
    }

    private static class IndexAction extends OperationBase
    {
        private int index ;

        public IndexAction( int index )
        {
            this.index = index ;
        }

        public Object operate( Object value )
        {
            return getObjectArray( value )[ index ] ;
        }

        public String toString()
        {
            return "indexAction(" + index + ")" ;
        }
    }

    public static Operation indexAction( int index )
    {
        return new IndexAction( index ) ;
    }

    private static class SuffixAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return getStringPair( value ).getFirst() ;
        }

        public String toString() { return "suffixAction" ; }
    }

    private static Operation suffixActionImpl = new SuffixAction() ;

    private static class ValueAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return getStringPair( value ).getSecond() ;
        }

        public String toString() { return "valueAction" ; }
    }

    private static Operation valueActionImpl = new ValueAction() ;

    private static class IdentityAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return value ;
        }

        public String toString() { return "identityAction" ; }
    }

    private static Operation identityActionImpl = new IdentityAction() ;

    private static class BooleanAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return new Boolean( getString( value ) ) ;
        }

        public String toString() { return "booleanAction" ; }
    }

    private static Operation booleanActionImpl = new BooleanAction() ;

    private static class IntegerAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return new Integer( getString( value ) ) ;
        }

        public String toString() { return "integerAction" ; }
    }

    private static Operation integerActionImpl = new IntegerAction() ;

    private static class StringAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return value ;
        }

        public String toString() { return "stringAction" ; }
    }

    private static Operation stringActionImpl = new StringAction() ;

    private static class ClassAction extends OperationBase
    {
        public Object operate( Object value )
        {
            String className = getString( value ) ;

            try {
                Class result = ORBClassLoader.loadClass( className ) ;
                return result ;
            } catch (Exception exc) {
                ORBUtilSystemException wrapper = ORBUtilSystemException.get(
                    CORBALogDomains.ORB_LIFECYCLE ) ;
                throw wrapper.couldNotLoadClass( exc, className ) ;
            }
        }

        public String toString() { return "classAction" ; }
    }

    private static Operation classActionImpl = new ClassAction() ;

    private static class SetFlagAction extends OperationBase
    {
        public Object operate( Object value )
        {
            return Boolean.TRUE ;
        }

        public String toString() { return "setFlagAction" ; }
    }

    private static Operation setFlagActionImpl = new SetFlagAction() ;

    private static class URLAction extends OperationBase
    {
        public Object operate( Object value )
        {
            String val = (String)value ;
            try {
                return new URL( val ) ;
            } catch (MalformedURLException exc) {
                ORBUtilSystemException wrapper = ORBUtilSystemException.get(
                    CORBALogDomains.ORB_LIFECYCLE ) ;
                throw wrapper.badUrl( exc, val ) ;
            }
        }

        public String toString() { return "URLAction" ; }
    }

    private static Operation URLActionImpl = new URLAction() ;

    public static Operation identityAction()
    {
        return identityActionImpl ;
    }

    public static Operation suffixAction()
    {
        return suffixActionImpl ;
    }

    public static Operation valueAction()
    {
        return valueActionImpl ;
    }

    public static Operation booleanAction()
    {
        return booleanActionImpl ;
    }

    public static Operation integerAction()
    {
        return integerActionImpl ;
    }

    public static Operation stringAction()
    {
        return stringActionImpl ;
    }

    public static Operation classAction()
    {
        return classActionImpl ;
    }

    public static Operation setFlagAction()
    {
        return setFlagActionImpl ;
    }

    public static Operation URLAction()
    {
        return URLActionImpl ;
    }

    private static class IntegerRangeAction extends OperationBase
    {
        private int min ;
        private int max ;

        IntegerRangeAction( int min, int max )
        {
            this.min = min ;
            this.max = max ;
        }

        public Object operate( Object value )
        {
            int result = Integer.parseInt( getString( value ) ) ;
            if ((result >= min) && (result <= max))
                return new Integer( result ) ;
            else
                throw new IllegalArgumentException(
                    "Property value " + result + " is not in the range " +
                    min + " to " + max ) ;
        }

        public String toString() {
            return "integerRangeAction(" + min + "," + max + ")" ;
        }
    }

    public static Operation integerRangeAction( int min, int max )
    {
        return new IntegerRangeAction( min, max ) ;
    }

    private static class ListAction extends OperationBase {
        private String sep ;
        private Operation act ;

        ListAction( String sep, Operation act )
        {
            this.sep = sep ;
            this.act = act ;
        }

        // Note that this method carefully constructs an array of the type
        // of the first result, rather than just using Object[], which is
        // not convertible into the correct type.  Also note that no tokens
        // results in a null result.
        public Object operate( Object value )
        {
            StringTokenizer st = new StringTokenizer( getString( value ),
                sep ) ;
            int length = st.countTokens() ;
            Object result = null ;
            int ctr = 0 ;
            while (st.hasMoreTokens()) {
                String next = st.nextToken() ;
                Object val = act.operate( next ) ;
                if (result == null)
                    result = Array.newInstance( val.getClass(), length ) ;
                Array.set( result, ctr++, val ) ;
            }

            return result ;
        }

        public String toString() {
            return "listAction(separator=\"" + sep +
                "\",action=" + act + ")" ;
        }
    }

    public static Operation listAction( String sep, Operation act )
    {
        return new ListAction( sep, act ) ;
    }

    private static class SequenceAction extends OperationBase
    {
        private String sep ;
        private Operation[] actions ;

        SequenceAction( String sep, Operation[] actions )
        {
            this.sep = sep ;
            this.actions = actions ;
        }

        public Object operate( Object value )
        {
            StringTokenizer st = new StringTokenizer( getString( value ),
                sep ) ;

            int numTokens = st.countTokens() ;
            if (numTokens != actions.length)
                throw new Error(
                    "Number of tokens and number of actions do not match" ) ;

            int ctr = 0 ;
            Object[] result = new Object[ numTokens ] ;
            while (st.hasMoreTokens()) {
                Operation act = actions[ctr] ;
                String next = st.nextToken() ;
                result[ctr++] = act.operate( next ) ;
            }

            return result ;
        }

        public String toString() {
            return "sequenceAction(separator=\"" + sep +
                "\",actions=" +
                ObjectUtility.compactObjectToString(actions) + ")" ;
        }
    }

    public static Operation sequenceAction( String sep,
        Operation[] actions )
    {
        return new SequenceAction( sep, actions ) ;
    }

    private static class ComposeAction extends OperationBase
    {
        private Operation op1 ;
        private Operation op2 ;

        ComposeAction( Operation op1, Operation op2 )
        {
            this.op1 = op1 ;
            this.op2 = op2 ;
        }

        public Object operate( Object value )
        {
            return op2.operate( op1.operate( value ) ) ;
        }

        public String toString() {
            return "composition(" + op1 + "," + op2 + ")" ;
        }
    }

    public static Operation compose( Operation op1, Operation op2 )
    {
        return new ComposeAction( op1, op2 ) ;
    }

    private static class MapAction extends OperationBase
    {
        Operation op ;

        MapAction( Operation op )
        {
            this.op = op ;
        }

        public Object operate( Object value )
        {
            Object[] values = (Object[])value ;
            Object[] result = new Object[ values.length ] ;
            for (int ctr=0; ctr<values.length; ctr++ )
                result[ctr] = op.operate( values[ctr] ) ;
            return result ;
        }

        public String toString() {
            return "mapAction(" + op + ")" ;
        }
    }

    public static Operation mapAction( Operation op )
    {
        return new MapAction( op ) ;
    }

    private static class MapSequenceAction extends OperationBase
    {
        private Operation[] op ;

        public MapSequenceAction( Operation[] op )
        {
            this.op = op ;
        }

        // XXX Does this correctly handle array types?  It seems
        // that hetereogeneous arrays work this way, while
        // homogeneous arrays need to use Array.newInstance tricks.
        public Object operate( Object value )
        {
            Object[] values = (Object[])value ;
            Object[] result = new Object[ values.length ] ;
            for (int ctr=0; ctr<values.length; ctr++ )
                result[ctr] = op[ctr].operate( values[ctr] ) ;
            return result ;
        }

        public String toString() {
            return "mapSequenceAction(" +
                ObjectUtility.compactObjectToString(op) + ")" ;
        }
    }

    public static Operation mapSequenceAction( Operation[] op )
    {
        return new MapSequenceAction( op ) ;
    }

    private static class ConvertIntegerToShort extends OperationBase
    {
        public Object operate( Object value )
        {
            Integer val = (Integer)value ;
            return new Short( val.shortValue() ) ;
        }

        public String toString() {
            return "ConvertIntegerToShort" ;
        }
    }

    private static Operation convertIntegerToShortImpl = new ConvertIntegerToShort() ;

    public static Operation convertIntegerToShort()
    {
        return convertIntegerToShortImpl ;
    }
}
