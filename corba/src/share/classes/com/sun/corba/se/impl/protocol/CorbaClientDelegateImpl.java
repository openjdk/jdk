/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import java.util.Iterator;
import java.util.HashMap;

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NVList;
import org.omg.CORBA.Request;
import org.omg.CORBA.TypeCode;

import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.RemarshalException;
import org.omg.CORBA.portable.ServantObject;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.ClientInvocationInfo;
import com.sun.corba.se.pept.protocol.ClientRequestDispatcher;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.ContactInfoList;
import com.sun.corba.se.pept.transport.ContactInfoListIterator;

import com.sun.corba.se.spi.presentation.rmi.StubAdapter;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.CorbaClientDelegate ;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator;

import com.sun.corba.se.impl.corba.RequestImpl;
import com.sun.corba.se.impl.protocol.CorbaInvocationInfo;
import com.sun.corba.se.impl.transport.CorbaContactInfoListImpl;
import com.sun.corba.se.impl.util.JDKBridge;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;

// implements com.sun.corba.se.impl.core.ClientRequestDispatcher
// so RMI-IIOP Util.isLocal can call ClientRequestDispatcher.useLocalInvocation.

/**
 * @author Harold Carr
 */
public class CorbaClientDelegateImpl extends CorbaClientDelegate
{
    private ORB orb;
    private ORBUtilSystemException wrapper ;

    private CorbaContactInfoList contactInfoList;

    public CorbaClientDelegateImpl(ORB orb,
                                   CorbaContactInfoList contactInfoList)
    {
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        this.contactInfoList = contactInfoList;
    }

    //
    // framework.subcontract.Delegate
    //

    public Broker getBroker()
    {
        return orb;
    }

    public ContactInfoList getContactInfoList()
    {
        return contactInfoList;
    }

    //
    // CORBA_2_3.portable.Delegate
    //

    public OutputStream request(org.omg.CORBA.Object self,
                                String operation,
                                boolean responseExpected)
    {
        ClientInvocationInfo invocationInfo =
            orb.createOrIncrementInvocationInfo();
        Iterator contactInfoListIterator =
            invocationInfo.getContactInfoListIterator();
        if (contactInfoListIterator == null) {
            contactInfoListIterator = contactInfoList.iterator();
            invocationInfo.setContactInfoListIterator(contactInfoListIterator);
        }
        if (! contactInfoListIterator.hasNext()) {
            throw ((CorbaContactInfoListIterator)contactInfoListIterator)
                .getFailureException();
        }
        CorbaContactInfo contactInfo = (CorbaContactInfo) contactInfoListIterator.next();
        ClientRequestDispatcher subcontract = contactInfo.getClientRequestDispatcher();
        // Remember chosen subcontract for invoke and releaseReply.
        // NOTE: This is necessary since a stream is not available in
        // releaseReply if there is a client marshaling error or an
        // error in _invoke.
        invocationInfo.setClientRequestDispatcher(subcontract);
        return (OutputStream)
            subcontract.beginRequest(self, operation,
                                     !responseExpected, contactInfo);
    }

    public InputStream invoke(org.omg.CORBA.Object self, OutputStream output)
        throws
            ApplicationException,
            RemarshalException
    {
        ClientRequestDispatcher subcontract = getClientRequestDispatcher();
        return (InputStream)
            subcontract.marshalingComplete((Object)self, (OutputObject)output);
    }

    public void releaseReply(org.omg.CORBA.Object self, InputStream input)
    {
        // NOTE: InputStream may be null (e.g., exception request from PI).
        ClientRequestDispatcher subcontract = getClientRequestDispatcher();
        subcontract.endRequest(orb, self, (InputObject)input);
        orb.releaseOrDecrementInvocationInfo();
    }

    private ClientRequestDispatcher getClientRequestDispatcher()
    {
        return (ClientRequestDispatcher)
            ((CorbaInvocationInfo)orb.getInvocationInfo())
            .getClientRequestDispatcher();
    }

    public org.omg.CORBA.Object get_interface_def(org.omg.CORBA.Object obj)
    {
        InputStream is = null;
        // instantiate the stub
        org.omg.CORBA.Object stub = null ;

        try {
            OutputStream os = request(null, "_interface", true);
            is = (InputStream) invoke((org.omg.CORBA.Object)null, os);

            org.omg.CORBA.Object objimpl =
                (org.omg.CORBA.Object) is.read_Object();

            // check if returned object is of correct type
            if ( !objimpl._is_a("IDL:omg.org/CORBA/InterfaceDef:1.0") )
                throw wrapper.wrongInterfaceDef(CompletionStatus.COMPLETED_MAYBE);

            try {
                stub = (org.omg.CORBA.Object)
                    JDKBridge.loadClass("org.omg.CORBA._InterfaceDefStub").
                        newInstance();
            } catch (Exception ex) {
                throw wrapper.noInterfaceDefStub( ex ) ;
            }

            org.omg.CORBA.portable.Delegate del =
                StubAdapter.getDelegate( objimpl ) ;
            StubAdapter.setDelegate( stub, del ) ;
        } catch (ApplicationException e) {
            // This cannot happen.
            throw wrapper.applicationExceptionInSpecialMethod( e ) ;
        } catch (RemarshalException e) {
            return get_interface_def(obj);
        } finally {
            releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
        }

        return stub;
    }

    public boolean is_a(org.omg.CORBA.Object obj, String dest)
    {
        // dest is the typeId of the interface to compare against.
        // repositoryIds is the list of typeIds that the stub knows about.

        // First we look for an answer using local information.

        String [] repositoryIds = StubAdapter.getTypeIds( obj ) ;
        String myid = contactInfoList.getTargetIOR().getTypeId();
        if ( dest.equals(myid) ) {
            return true;
        }
        for ( int i=0; i<repositoryIds.length; i++ ) {
            if ( dest.equals(repositoryIds[i]) ) {
                return true;
            }
        }

        // But repositoryIds may not be complete, so it may be necessary to
        // go to server.

        InputStream is = null;
        try {
            OutputStream os = request(null, "_is_a", true);
            os.write_string(dest);
            is = (InputStream) invoke((org.omg.CORBA.Object) null, os);

            return is.read_boolean();

        } catch (ApplicationException e) {
            // This cannot happen.
            throw wrapper.applicationExceptionInSpecialMethod( e ) ;
        } catch (RemarshalException e) {
            return is_a(obj, dest);
        } finally {
            releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
        }
    }

    public boolean non_existent(org.omg.CORBA.Object obj)
    {
        InputStream is = null;
        try {
            OutputStream os = request(null, "_non_existent", true);
            is = (InputStream) invoke((org.omg.CORBA.Object)null, os);

            return is.read_boolean();

        } catch (ApplicationException e) {
            // This cannot happen.
            throw wrapper.applicationExceptionInSpecialMethod( e ) ;
        } catch (RemarshalException e) {
            return non_existent(obj);
        } finally {
            releaseReply((org.omg.CORBA.Object)null, (InputStream)is);
        }
    }

    public org.omg.CORBA.Object duplicate(org.omg.CORBA.Object obj)
    {
        return obj;
    }

    public void release(org.omg.CORBA.Object obj)
    {
        // DO NOT clear out internal variables to release memory
        // This delegate may be pointed-to by other objrefs.
    }

    // obj._get_delegate() == this due to the argument passing conventions in
    // portable.ObjectImpl, so we just ignore obj here.
    public boolean is_equivalent(org.omg.CORBA.Object obj,
                                 org.omg.CORBA.Object ref)
    {
        if ( ref == null )
            return false;

        // If ref is a local object, it is not a Stub!
        if (!StubAdapter.isStub(ref))
            return false ;

        Delegate del = StubAdapter.getDelegate(ref) ;
        if (del == null)
            return false ;

        // Optimize the x.is_equivalent( x ) case
        if (del == this)
            return true;

        // If delegate was created by a different ORB, return false
        if (!(del instanceof CorbaClientDelegateImpl))
            return false ;

        CorbaClientDelegateImpl corbaDelegate = (CorbaClientDelegateImpl)del ;
        CorbaContactInfoList ccil =
            (CorbaContactInfoList)corbaDelegate.getContactInfoList() ;
        return this.contactInfoList.getTargetIOR().isEquivalent(
            ccil.getTargetIOR() );
    }

    /**
     * This method overrides the org.omg.CORBA.portable.Delegate.equals method,
     * and does the equality check based on IOR equality.
     */
    public boolean equals(org.omg.CORBA.Object self, java.lang.Object other)
    {
        if (other == null)
            return false ;

        if (!StubAdapter.isStub(other)) {
            return false;
        }

        Delegate delegate = StubAdapter.getDelegate( other ) ;
        if (delegate == null)
            return false ;

        if (delegate instanceof CorbaClientDelegateImpl) {
            CorbaClientDelegateImpl otherDel = (CorbaClientDelegateImpl)
                delegate ;
            IOR otherIor = otherDel.contactInfoList.getTargetIOR();
            return this.contactInfoList.getTargetIOR().equals(otherIor);
        }

        // Come here if other is not implemented by our ORB.
        return false;
    }

    public int hashCode(org.omg.CORBA.Object obj)
    {
        return this.hashCode() ;
    }

    public int hash(org.omg.CORBA.Object obj, int maximum)
    {
        int h = this.hashCode();
        if ( h > maximum )
            return 0;
        return h;
    }

    public Request request(org.omg.CORBA.Object obj, String operation)
    {
        return new RequestImpl(orb, obj, null, operation, null, null, null,
                               null);
    }

    public Request create_request(org.omg.CORBA.Object obj,
                                  Context ctx,
                                  String operation,
                                  NVList arg_list,
                                  NamedValue result)
    {
        return new RequestImpl(orb, obj, ctx, operation, arg_list,
                               result, null, null);
    }

    public Request create_request(org.omg.CORBA.Object obj,
                                  Context ctx,
                                  String operation,
                                  NVList arg_list,
                                  NamedValue result,
                                  ExceptionList exclist,
                                  ContextList ctxlist)
    {
        return new RequestImpl(orb, obj, ctx, operation, arg_list, result,
                               exclist, ctxlist);
    }

    public org.omg.CORBA.ORB orb(org.omg.CORBA.Object obj)
    {
        return this.orb;
    }

    /**
     * Returns true if this object is implemented by a local servant.
     *
     * REVISIT: locatedIOR should be replaced with a method call that
     *      returns the current IOR for this request (e.g. ContactInfoChooser).
     *
     * @param self The object reference which delegated to this delegate.
     * @return true only if the servant incarnating this object is located in
     * this ORB.
     */
    public boolean is_local(org.omg.CORBA.Object self)
    {
        // XXX this need to check isNextCallValid
        return contactInfoList.getEffectiveTargetIOR().getProfile().
            isLocal();
    }

    public ServantObject servant_preinvoke(org.omg.CORBA.Object self,
                                           String operation,
                                           Class expectedType)
    {
        return
            contactInfoList.getLocalClientRequestDispatcher()
            .servant_preinvoke(self, operation, expectedType);
    }

    public void servant_postinvoke(org.omg.CORBA.Object self,
                                   ServantObject servant)
    {
        contactInfoList.getLocalClientRequestDispatcher()
            .servant_postinvoke(self, servant);
    }

    // XXX Should this be public?
    /* Returns the codebase for object reference provided.
     * @param self the object reference whose codebase needs to be returned.
     * @return the codebase as a space delimited list of url strings or
     * null if none.
     */
    public String get_codebase(org.omg.CORBA.Object self)
    {
        if (contactInfoList.getTargetIOR() != null) {
            return contactInfoList.getTargetIOR().getProfile().getCodebase();
        }
        return null;
    }

    public String toString(org.omg.CORBA.Object self)
    {
        return contactInfoList.getTargetIOR().stringify();
    }

    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public int hashCode()
    {
        return this.contactInfoList.hashCode();
    }
}

// End of file.
