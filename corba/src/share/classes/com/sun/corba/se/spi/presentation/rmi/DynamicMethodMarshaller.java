/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.presentation.rmi ;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;
import org.omg.CORBA.portable.ApplicationException ;

import java.lang.reflect.Method ;

import java.rmi.RemoteException ;

import com.sun.corba.se.spi.orb.ORB ;

/** Used to read and write arguments and results for a particular method.
*
*/
public interface DynamicMethodMarshaller
{
    /** Returns the method used to create this DynamicMethodMarshaller.
     */
    Method getMethod() ;

    /** Copy the arguments as needed for this particular method.
     * Can be optimized so that as little copying as possible is
     * performed.
     */
    Object[] copyArguments( Object[] args, ORB orb ) throws RemoteException ;

    /** Read the arguments for this method from the InputStream.
     * Returns null if there are no arguments.
     */
    Object[] readArguments( InputStream is ) ;

    /** Write arguments for this method to the OutputStream.
     * Does nothing if there are no arguments.
     */
    void writeArguments( OutputStream os, Object[] args ) ;

    /** Copy the result as needed for this particular method.
     * Can be optimized so that as little copying as possible is
     * performed.
     */
    Object copyResult( Object result, ORB orb ) throws RemoteException ;

    /** Read the result from the InputStream.  Returns null
     * if the result type is null.
     */
    Object readResult( InputStream is ) ;

    /** Write the result to the OutputStream.  Does nothing if
     * the result type is null.
     */
    void writeResult( OutputStream os, Object result ) ;

    /** Returns true iff thr's class is a declared exception (or a subclass of
     * a declared exception) for this DynamicMethodMarshaller's method.
     */
    boolean isDeclaredException( Throwable thr ) ;

    /** Write the repository ID of the exception and the value of the
     * exception to the OutputStream.  ex should be a declared exception
     * for this DynamicMethodMarshaller's method.
     */
    void writeException( OutputStream os, Exception ex ) ;

    /** Reads an exception ID and the corresponding exception from
     * the input stream.  This should be an exception declared in
     * this method.
     */
    Exception readException( ApplicationException ae ) ;
}
