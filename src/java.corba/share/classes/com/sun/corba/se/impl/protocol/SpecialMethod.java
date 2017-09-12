/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol ;

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.SystemException ;
import org.omg.CORBA.NO_IMPLEMENT ;
import org.omg.CORBA.OBJECT_NOT_EXIST ;
import org.omg.CORBA.CompletionStatus ;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;

import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;

import com.sun.corba.se.spi.oa.NullServant ;

public abstract class SpecialMethod {
    public abstract boolean isNonExistentMethod() ;
    public abstract String getName();
    public abstract CorbaMessageMediator invoke(java.lang.Object servant,
                                                CorbaMessageMediator request,
                                                byte[] objectId,
                                                ObjectAdapter objectAdapter);

    public static final SpecialMethod getSpecialMethod(String operation) {
        for(int i = 0; i < methods.length; i++)
            if (methods[i].getName().equals(operation))
                return methods[i];
        return null;
    }

    static SpecialMethod[] methods = {
        new IsA(),
        new GetInterface(),
        new NonExistent(),
        new NotExistent()
    };
}

class NonExistent extends SpecialMethod {
    public boolean isNonExistentMethod()
    {
        return true ;
    }

    public String getName() {           // _non_existent
        return "_non_existent";
    }

    public CorbaMessageMediator invoke(java.lang.Object servant,
                                       CorbaMessageMediator request,
                                       byte[] objectId,
                                       ObjectAdapter objectAdapter)
    {
        boolean result = (servant == null) || (servant instanceof NullServant) ;
        CorbaMessageMediator response =
            request.getProtocolHandler().createResponse(request, null);
        ((OutputStream)response.getOutputObject()).write_boolean(result);
        return response;
    }
}

class NotExistent extends NonExistent {
    public String getName() {           // _not_existent
        return "_not_existent";
    }
}

class IsA extends SpecialMethod  {      // _is_a
    public boolean isNonExistentMethod()
    {
        return false ;
    }

    public String getName() {
        return "_is_a";
    }
    public CorbaMessageMediator invoke(java.lang.Object servant,
                                       CorbaMessageMediator request,
                                       byte[] objectId,
                                       ObjectAdapter objectAdapter)
    {
        if ((servant == null) || (servant instanceof NullServant)) {
            ORB orb = (ORB)request.getBroker() ;
            ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
                CORBALogDomains.OA_INVOCATION ) ;

            return request.getProtocolHandler().createSystemExceptionResponse(
                request, wrapper.badSkeleton(), null);
        }

        String[] ids = objectAdapter.getInterfaces( servant, objectId );
        String clientId =
            ((InputStream)request.getInputObject()).read_string();
        boolean answer = false;
        for(int i = 0; i < ids.length; i++)
            if (ids[i].equals(clientId)) {
                answer = true;
                break;
            }

        CorbaMessageMediator response =
            request.getProtocolHandler().createResponse(request, null);
        ((OutputStream)response.getOutputObject()).write_boolean(answer);
        return response;
    }
}

class GetInterface extends SpecialMethod  {     // _get_interface
    public boolean isNonExistentMethod()
    {
        return false ;
    }

    public String getName() {
        return "_interface";
    }
    public CorbaMessageMediator invoke(java.lang.Object servant,
                                       CorbaMessageMediator request,
                                       byte[] objectId,
                                       ObjectAdapter objectAdapter)
    {
        ORB orb = (ORB)request.getBroker() ;
        ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.OA_INVOCATION ) ;

        if ((servant == null) || (servant instanceof NullServant)) {
            return request.getProtocolHandler().createSystemExceptionResponse(
                request, wrapper.badSkeleton(), null);
        } else {
            return request.getProtocolHandler().createSystemExceptionResponse(
                request, wrapper.getinterfaceNotImplemented(), null);
        }
    }
}

// End of file.
