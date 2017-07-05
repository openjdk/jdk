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

package com.sun.corba.se.impl.presentation.rmi ;

import java.io.Serializable ;
import java.io.Externalizable ;

import javax.rmi.PortableRemoteObject ;
import javax.rmi.CORBA.Util ;

import java.rmi.RemoteException ;
import java.rmi.UnexpectedException ;

import org.omg.CORBA.UserException ;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;
import org.omg.CORBA.portable.ApplicationException ;

import java.lang.reflect.Method ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class ExceptionHandlerImpl implements ExceptionHandler
{
    private ExceptionRW[] rws ;

    private final ORBUtilSystemException wrapper ;

///////////////////////////////////////////////////////////////////////////////
// ExceptionRW interface and implementations.
// Used to read and write exceptions.
///////////////////////////////////////////////////////////////////////////////

    public interface ExceptionRW
    {
        Class getExceptionClass() ;

        String getId() ;

        void write( OutputStream os, Exception ex ) ;

        Exception read( InputStream is ) ;
    }

    public abstract class ExceptionRWBase implements ExceptionRW
    {
        private Class cls ;
        private String id ;

        public ExceptionRWBase( Class cls )
        {
            this.cls = cls ;
        }

        public Class getExceptionClass()
        {
            return cls ;
        }

        public String getId()
        {
            return id ;
        }

        void setId( String id )
        {
            this.id = id ;
        }
    }

    public class ExceptionRWIDLImpl extends ExceptionRWBase
    {
        private Method readMethod ;
        private Method writeMethod ;

        public ExceptionRWIDLImpl( Class cls )
        {
            super( cls ) ;

            String helperName = cls.getName() + "Helper" ;
            ClassLoader loader = cls.getClassLoader() ;
            Class helperClass ;

            try {
                helperClass = Class.forName( helperName, true, loader ) ;
                Method idMethod = helperClass.getDeclaredMethod( "id", null ) ;
                setId( (String)idMethod.invoke( null, null ) ) ;
            } catch (Exception ex) {
                throw wrapper.badHelperIdMethod( ex, helperName ) ;
            }

            try {
                Class[] argTypes = new Class[] {
                    org.omg.CORBA.portable.OutputStream.class, cls } ;
                writeMethod = helperClass.getDeclaredMethod( "write",
                    argTypes ) ;
            } catch (Exception ex) {
                throw wrapper.badHelperWriteMethod( ex, helperName ) ;
            }

            try {
                Class[] argTypes = new Class[] {
                    org.omg.CORBA.portable.InputStream.class } ;
                readMethod = helperClass.getDeclaredMethod( "read", argTypes ) ;
            } catch (Exception ex) {
                throw wrapper.badHelperReadMethod( ex, helperName ) ;
            }
        }

        public void write( OutputStream os, Exception ex )
        {
            try {
                Object[] args = new Object[] { os, ex } ;
                writeMethod.invoke( null, args ) ;
            } catch (Exception exc) {
                throw wrapper.badHelperWriteMethod( exc,
                    writeMethod.getDeclaringClass().getName() ) ;
            }
        }

        public Exception read( InputStream is )
        {
            try {
                Object[] args = new Object[] { is } ;
                return (Exception)readMethod.invoke( null, args ) ;
            } catch (Exception ex) {
                throw wrapper.badHelperReadMethod( ex,
                    readMethod.getDeclaringClass().getName() ) ;
            }
        }
    }

    public class ExceptionRWRMIImpl extends ExceptionRWBase
    {
        public ExceptionRWRMIImpl( Class cls )
        {
            super( cls ) ;
            setId( IDLNameTranslatorImpl.getExceptionId( cls ) ) ;
        }

        public void write( OutputStream os, Exception ex )
        {
            os.write_string( getId() ) ;
            os.write_value( ex, getExceptionClass() ) ;
        }

        public Exception read( InputStream is )
        {
            is.read_string() ; // read and ignore!
            return (Exception)is.read_value( getExceptionClass() ) ;
        }
    }

///////////////////////////////////////////////////////////////////////////////

    public ExceptionHandlerImpl( Class[] exceptions )
    {
        wrapper = ORBUtilSystemException.get(
            CORBALogDomains.RPC_PRESENTATION ) ;

        int count = 0 ;
        for (int ctr=0; ctr<exceptions.length; ctr++) {
            Class cls = exceptions[ctr] ;
            if (!RemoteException.class.isAssignableFrom(cls))
                count++ ;
        }

        rws = new ExceptionRW[count] ;

        int index = 0 ;
        for (int ctr=0; ctr<exceptions.length; ctr++) {
            Class cls = exceptions[ctr] ;
            if (!RemoteException.class.isAssignableFrom(cls)) {
                ExceptionRW erw = null ;
                if (UserException.class.isAssignableFrom(cls))
                    erw = new ExceptionRWIDLImpl( cls ) ;
                else
                    erw = new ExceptionRWRMIImpl( cls ) ;

                /* The following check is not performed
                 * in order to maintain compatibility with
                 * rmic.  See bug 4989312.

                // Check for duplicate repository ID
                String repositoryId = erw.getId() ;
                int duplicateIndex = findDeclaredException( repositoryId ) ;
                if (duplicateIndex > 0) {
                    ExceptionRW duprw = rws[duplicateIndex] ;
                    String firstClassName =
                        erw.getExceptionClass().getName() ;
                    String secondClassName =
                        duprw.getExceptionClass().getName() ;
                    throw wrapper.duplicateExceptionRepositoryId(
                        firstClassName, secondClassName, repositoryId ) ;
                }

                */

                rws[index++] = erw ;
            }
        }
    }

    private int findDeclaredException( Class cls )
    {
        for (int ctr = 0; ctr < rws.length; ctr++) {
            Class next = rws[ctr].getExceptionClass() ;
            if (next.isAssignableFrom(cls))
                return ctr ;
        }

        return -1 ;
    }

    private int findDeclaredException( String repositoryId )
    {
        for (int ctr=0; ctr<rws.length; ctr++) {
            // This may occur when rws has not been fully
            // populated, in which case the search should just fail.
            if (rws[ctr]==null)
                return -1 ;

            String rid = rws[ctr].getId() ;
            if (repositoryId.equals( rid ))
                return ctr ;
        }

        return -1 ;
    }

    public boolean isDeclaredException( Class cls )
    {
        return findDeclaredException( cls ) >= 0 ;
    }

    public void writeException( OutputStream os, Exception ex )
    {
        int index = findDeclaredException( ex.getClass() ) ;
        if (index < 0)
            throw wrapper.writeUndeclaredException( ex,
                ex.getClass().getName() ) ;

        rws[index].write( os, ex ) ;
    }

    public Exception readException( ApplicationException ae )
    {
        // Note that the exception ID is present in both ae
        // and in the input stream from ae.  The exception
        // reader must actually read the exception ID from
        // the stream.
        InputStream is = (InputStream)ae.getInputStream() ;
        String excName = ae.getId() ;
        int index = findDeclaredException( excName ) ;
        if (index < 0) {
            excName = is.read_string() ;
            Exception res = new UnexpectedException( excName ) ;
            res.initCause( ae ) ;
            return res ;
        }

        return rws[index].read( is ) ;
    }

    // This is here just for the dynamicrmiiiop test
    public ExceptionRW getRMIExceptionRW( Class cls )
    {
        return new ExceptionRWRMIImpl( cls ) ;
    }
}
