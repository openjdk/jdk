/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil ;

import java.util.Arrays ;

public abstract class ObjectWriter {
    public static ObjectWriter make( boolean isIndenting,
        int initialLevel, int increment )
    {
        if (isIndenting)
            return new IndentingObjectWriter( initialLevel, increment ) ;
        else
            return new SimpleObjectWriter() ;
    }

    public abstract void startObject( Object obj ) ;

    public abstract void startElement() ;

    public abstract void endElement() ;

    public abstract void endObject( String str ) ;

    public abstract void endObject() ;

    public String toString() { return result.toString() ; }

    public void append( boolean arg ) { result.append( arg ) ; }

    public void append( char arg ) { result.append( arg ) ; }

    public void append( short arg ) { result.append( arg ) ; }

    public void append( int arg ) { result.append( arg ) ; }

    public void append( long arg ) { result.append( arg ) ; }

    public void append( float arg ) { result.append( arg ) ; }

    public void append( double arg ) { result.append( arg ) ; }

    public void append( String arg ) { result.append( arg ) ; }

//=================================================================================================
// Implementation
//=================================================================================================

    protected StringBuffer result ;

    protected ObjectWriter()
    {
        result = new StringBuffer() ;
    }

    protected void appendObjectHeader( Object obj )
    {
        result.append( obj.getClass().getName() ) ;
        result.append( "<" ) ;
        result.append( System.identityHashCode( obj ) ) ;
        result.append( ">" ) ;
        Class compClass = obj.getClass().getComponentType() ;

        if (compClass != null) {
            result.append( "[" ) ;
            if (compClass == boolean.class) {
                boolean[] arr = (boolean[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == byte.class) {
                byte[] arr = (byte[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == short.class) {
                short[] arr = (short[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == int.class) {
                int[] arr = (int[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == long.class) {
                long[] arr = (long[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == char.class) {
                char[] arr = (char[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == float.class) {
                float[] arr = (float[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else if (compClass == double.class) {
                double[] arr = (double[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            } else { // array of object
                java.lang.Object[] arr = (java.lang.Object[])obj ;
                result.append( arr.length ) ;
                result.append( "]" ) ;
            }
        }

        result.append( "(" ) ;
    }

    /** Expected patterns:
    * startObject endObject( str )
    *   header( elem )\n
    * startObject ( startElement append* endElement ) * endObject
    *   header(\n
    *       append*\n *
    *   )\n
    */
    private static class IndentingObjectWriter extends ObjectWriter {
        private int level ;
        private int increment ;

        public IndentingObjectWriter( int initialLevel, int increment )
        {
            this.level = initialLevel ;
            this.increment = increment ;
            startLine() ;
        }

        private void startLine()
        {
            char[] fill = new char[ level * increment ] ;
            Arrays.fill( fill, ' ' ) ;
            result.append( fill ) ;
        }

        public void startObject( java.lang.Object obj )
        {
            appendObjectHeader( obj ) ;
            level++ ;
        }

        public void startElement()
        {
            result.append( "\n" ) ;
            startLine() ;
        }

        public void endElement()
        {
        }

        public void endObject( String str )
        {
            level-- ;
            result.append( str ) ;
            result.append( ")" ) ;
        }

        public void endObject( )
        {
            level-- ;
            result.append( "\n" ) ;
            startLine() ;
            result.append( ")" ) ;
        }
    }

    private static class SimpleObjectWriter extends ObjectWriter {
        public void startObject( java.lang.Object obj )
        {
            appendObjectHeader( obj ) ;
            result.append( " " ) ;
        }

        public void startElement()
        {
            result.append( " " ) ;
        }

        public void endObject( String str )
        {
            result.append( str ) ;
            result.append( ")" ) ;
        }

        public void endElement()
        {
        }

        public void endObject()
        {
            result.append( ")" ) ;
        }
    }
}
