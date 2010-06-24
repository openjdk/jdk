/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.pcosnaming;


import org.omg.CORBA.Object;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Policy;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.RequestProcessingPolicyValue;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.ServantRetentionPolicyValue;

import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CosNaming.NamingContextExtPackage.*;

import com.sun.corba.se.impl.naming.cosnaming.NamingContextDataStore;
import com.sun.corba.se.impl.naming.cosnaming.NamingUtils;

import com.sun.corba.se.impl.naming.namingutil.INSURLHandler;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.logging.NamingSystemException;

import java.io.Serializable;
import java.util.Hashtable;

/**
 * Class NamingContextImpl implements the org.omg.CosNaming::NamingContext and
 * NamingContextExt interface.
 * <p>
 * The operations bind(), rebind(), bind_context() and rebind_context()
 * are all really implemented by doBind(). resolve() is really implemented
 * by doResolve(), unbind() by doUnbind(). list(), new_context() and
 * destroy() uses the NamingContextDataStore interface directly. All the
 * doX() methods are public static.
 * They synchronize on the NamingContextDataStore object.
 * <p>
 * None of the methods here are Synchronized because These methods will be
 * invoked from Super class's doBind( ), doResolve( ) which are already
 * Synchronized.
 */


public class NamingContextImpl
    extends NamingContextExtPOA
    implements NamingContextDataStore, Serializable
{

    // The ORB is required to do string_to_object() operations
    // All the references are stored in the files in the form of IOR strings
    private transient ORB orb;

    // The ObjectKey will be in the format NC<Index> which uniquely identifies
    // The NamingContext internaly
    private final String objKey;

    // Hash table contains all the entries in the NamingContexts. The
    // CORBA.Object references will be stored in the form of IOR strings
    // and the Child Naming Contexts will have it's key as the entry in the
    // table. This table is written into File everytime an update is made
    // on this context.
    private final Hashtable theHashtable = new Hashtable( );

    // The NameServiceHandle is required to get the ObjectId from the
    // NamingContext's references. These references are created using
    // POA in the NameService.
    private transient NameService theNameServiceHandle;

    // ServantManager is the single point of contact to Read, Write and
    // Update the NamingContextFile
    private transient ServantManagerImpl theServantManagerImplHandle;

    // All the INS (Interoperable Naming Service) methods are defined in this class
    // All the calls to INS will be delegated to this class.
    private transient com.sun.corba.se.impl.naming.cosnaming.InterOperableNamingImpl insImpl;

    private transient NamingSystemException readWrapper ;

    private transient NamingSystemException updateWrapper ;

    private static POA biPOA = null;

    /**
     * Create a naming context servant.
     * Runs the super constructor.
     * @param orb an ORB object.
     * @param objKey as String
     * @param TheNameService as NameService
     * @param TheServantManagerImpl as ServantManagerImpl
     * @exception java.lang.Exception a Java exception.
     */

    public NamingContextImpl(ORB orb, String objKey,
        NameService theNameService, ServantManagerImpl theServantManagerImpl  )
        throws Exception
    {
        super();

        this.orb = orb;
        readWrapper = NamingSystemException.get( orb,
            CORBALogDomains.NAMING_READ ) ;
        updateWrapper = NamingSystemException.get( orb,
            CORBALogDomains.NAMING_UPDATE ) ;

        debug = true ; // orb.namingDebugFlag ;
        this.objKey = objKey;
        theNameServiceHandle = theNameService;
        theServantManagerImplHandle = theServantManagerImpl;
        insImpl =
            new com.sun.corba.se.impl.naming.cosnaming.InterOperableNamingImpl();
    }

    com.sun.corba.se.impl.naming.cosnaming.InterOperableNamingImpl getINSImpl( )
    {
        if( insImpl == null )
        {
            // insImpl will be null if the NamingContext graph is rebuilt from
            // the persistence store.
            insImpl =
                new com.sun.corba.se.impl.naming.cosnaming.InterOperableNamingImpl();
        }
        return insImpl;
    }


    public void setRootNameService( NameService theNameService ) {
        theNameServiceHandle = theNameService;
    }

    public void setORB( ORB theOrb ) {
        orb = theOrb;
    }

    public void setServantManagerImpl(
                ServantManagerImpl theServantManagerImpl )
    {
        theServantManagerImplHandle = theServantManagerImpl;
    }

    public POA getNSPOA( ) {
        return theNameServiceHandle.getNSPOA( );
    }




   /**
   * Bind an object under a name in this NamingContext. If the name
   * contains multiple (n) components, n-1 will be resolved in this
   * NamingContext and the object bound in resulting NamingContext.
   * An exception is thrown if a binding with the supplied name already
   * exists. If the
   * object to be bound is a NamingContext it will not participate in
   * a recursive resolve.
   * @param n a sequence of NameComponents which is the name under which
   * the object will be bound.
   * @param obj the object reference to be bound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound The supplied name
   * is already bound.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doBind
   */
   public void bind(NameComponent[] n, org.omg.CORBA.Object obj)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound
    {
        if( obj == null ) {
            throw updateWrapper.objectIsNull() ;
        }

        if (debug)
            dprint("bind " + nameToString(n) + " to " + obj);
        // doBind implements all four flavors of binding
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doBind(impl,n,obj,false,BindingType.nobject);
    }

   /**
   * Bind a NamingContext under a name in this NamingContext. If the name
   * contains multiple (n) components, n-1 will be resolved in this
   * NamingContext and the object bound in resulting NamingContext.
   * An exception is thrown if a binding with the supplied name already
   * exists. The NamingContext will participate in recursive resolving.
   * @param n a sequence of NameComponents which is the name under which
   * the object will be bound.
   * @param obj the NamingContect object reference to be bound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object is
   * already bound under the supplied name.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doBind
   */
   public void bind_context(NameComponent[] n, NamingContext nc)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound
    {
        if( nc == null ) {
            throw updateWrapper.objectIsNull() ;
        }
        // doBind implements all four flavors of binding
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doBind(impl,n,nc,false,BindingType.ncontext);
    }

 /**
   * Bind an object under a name in this NamingContext. If the name
   * contains multiple (n) components, n-1 will be resolved in this
   * NamingContext and the object bound in resulting NamingContext.
   * If a binding under the supplied name already exists it will be
   * unbound first. If the
   * object to be bound is a NamingContext it will not participate in
   * a recursive resolve.
   * @param n a sequence of NameComponents which is the name under which
   * the object will be bound.
   * @param obj the object reference to be bound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doBind
   */
   public  void rebind(NameComponent[] n, org.omg.CORBA.Object obj)
        throws       org.omg.CosNaming.NamingContextPackage.NotFound,
                     org.omg.CosNaming.NamingContextPackage.CannotProceed,
                     org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        if( obj == null )
        {
            throw updateWrapper.objectIsNull() ;
        }
        try {
            if (debug)
                dprint("rebind " + nameToString(n) + " to " + obj);
            // doBind implements all four flavors of binding
            NamingContextDataStore impl = (NamingContextDataStore)this;
            doBind(impl,n,obj,true,BindingType.nobject);
        } catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound ex) {
            // This should not happen
            throw updateWrapper.namingCtxRebindAlreadyBound( ex ) ;
        }
    }

   /**
   * Bind a NamingContext under a name in this NamingContext. If the name
   * contains multiple (n) components, the first n-1 components will be
   * resolved in this
   * NamingContext and the object bound in resulting NamingContext.
   * If a binding under the supplied name already exists it will be
   * unbound first. The NamingContext will participate in recursive resolving.
   * @param n a sequence of NameComponents which is the name under which
   * the object will be bound.
   * @param obj the object reference to be bound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doBind
   */
   public  void rebind_context(NameComponent[] n, NamingContext nc)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        try {
            if (debug)
                dprint("rebind_context " + nameToString(n) + " to " + nc);
            // doBind implements all four flavors of binding
            NamingContextDataStore impl = (NamingContextDataStore)this;
            doBind(impl,n,nc,true,BindingType.ncontext);
        } catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound ex) {
            // This should not happen
            throw updateWrapper.namingCtxRebindAlreadyBound( ex ) ;
        }
    }

   /**
   * Resolve a name in this NamingContext and return the object reference
   * bound to the name. If the name contains multiple (n) components,
   * the first component will be resolved in this NamingContext and the
   * remaining components resolved in the resulting NamingContext, provided
   * that the NamingContext bound to the first component of the name was
   * bound with bind_context().
   * @param n a sequence of NameComponents which is the name to be resolved.
   * @return the object reference bound under the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doResolve
   */
   public  org.omg.CORBA.Object resolve(NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        if (debug)
            dprint("resolve " + nameToString(n));
        // doResolve actually resolves
        NamingContextDataStore impl = (NamingContextDataStore)this;
        return doResolve(impl,n);
    }

   /**
   * Remove a binding from this NamingContext. If the name contains
   * multiple (n) components, the first n-1 components will be resolved
   * from this NamingContext and the final component unbound in
   * the resulting NamingContext.
   * @param n a sequence of NameComponents which is the name to be unbound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see doUnbind
   */
   public  void unbind(NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        if (debug)
            dprint("unbind " + nameToString(n));
        // doUnbind actually unbinds
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doUnbind(impl,n);
    }

   /**
   * List the contents of this NamingContest. A sequence of bindings
   * is returned (a BindingList) containing up to the number of requested
   * bindings, and a BindingIterator object reference is returned for
   * iterating over the remaining bindings.
   * @param how_many The number of requested bindings in the BindingList.
   * @param bl The BindingList as an out parameter.
   * @param bi The BindingIterator as an out parameter.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see BindingListHolder
   * @see BindingIteratorImpl
   */
    public  void list(int how_many, BindingListHolder bl, BindingIteratorHolder bi)
    {
        if (debug)
            dprint("list(" + how_many + ")");
        // List actually generates the list
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            impl.List(how_many,bl,bi);
        }
        if (debug && bl.value != null)
            dprint("list(" + how_many + ") -> bindings[" + bl.value.length +
                   "] + iterator: " + bi.value);
    }


   /**
   * Create a NamingContext object and return its object reference.
   * @return an object reference for a new NamingContext object implemented
   * by this Name Server.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   */
    public synchronized NamingContext new_context()
    {
        // Create actually creates a new naming context
        if (debug)
            dprint("new_context()");
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            return impl.NewContext();
        }
    }


   /**
   * Create a new NamingContext, bind it in this Naming Context and return
   * its object reference. This is equivalent to using new_context() followed
   * by bind_context() with the supplied name and the object reference for
   * the newly created NamingContext.
   * @param n a sequence of NameComponents which is the name to be unbound.
   * @return an object reference for a new NamingContext object implemented
   * by this Name Server, bound to the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object is
   * already bound under the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see new_context
   * @see bind_context
   */
    public  NamingContext bind_new_context(NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        NamingContext nc = null;
        NamingContext rnc = null;
        try {
            if (debug)
                dprint("bind_new_context " + nameToString(n));
            // The obvious solution:
            nc = this.new_context();
            this.bind_context(n,nc);
            rnc = nc;
            nc = null;
        } finally {
            try {
                if(nc != null)
                    nc.destroy();
            } catch (org.omg.CosNaming.NamingContextPackage.NotEmpty e) {
            }
        }
        return rnc;
    }

    /**
   * Destroy this NamingContext object. If this NamingContext contains
   * no bindings, the NamingContext is deleted.
   * @exception org.omg.CosNaming.NamingContextPackage.NotEmpty This NamingContext
   * is not empty (i.e., contains bindings).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   */
    public  void destroy()
        throws org.omg.CosNaming.NamingContextPackage.NotEmpty
    {
        if (debug)
            dprint("destroy ");
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            if (impl.IsEmpty() == true)
                // The context is empty so it can be destroyed
                impl.Destroy();
            else
                // This context is not empty!
                throw new org.omg.CosNaming.NamingContextPackage.NotEmpty();
        }
    }

      /**
   * Implements all four flavors of binding. It uses Resolve() to
   * check if a binding already exists (for bind and bind_context), and
   * unbind() to ensure that a binding does not already exist.
   * If the length of the name is 1, then Bind() is called with
   * the name and the object to bind. Otherwise, the first component
   * of the name is resolved in this NamingContext and the appropriate
   * form of bind passed to the resulting NamingContext.
   * This method is static for maximal reuse - even for extended naming
   * context implementations where the recursive semantics still apply.
   * @param impl an implementation of NamingContextDataStore
   * @param n a sequence of NameComponents which is the name under which
   * the object will be bound.
   * @param obj the object reference to be bound.
   * @param rebind Replace an existing binding or not.
   * @param bt Type of binding (as object or as context).
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the first component of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object is
   * already bound under the supplied name.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see resolve
   * @see unbind
   * @see bind
   * @see bind_context
   * @see rebind
   * @see rebind_context
   */
    private void doBind(NamingContextDataStore impl,
                              NameComponent[] n,
                              org.omg.CORBA.Object obj,
                              boolean rebind,
                              org.omg.CosNaming.BindingType bt)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound
    {
        // Valid name?
        if (n.length < 1)
            throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

    // At bottom level?
        if (n.length == 1) {
            // The identifier must be set
            if( (n[0].id.length() == 0) && (n[0].kind.length() == 0) )
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

            // Ensure synchronization of backend
            synchronized (impl) {
                // Yes: bind object in this context under the name
                BindingTypeHolder bth = new BindingTypeHolder();
                if (rebind) {
                    org.omg.CORBA.Object objRef = impl.Resolve( n[0], bth );
                    if( objRef != null ) {
                        // Refer Naming Service Doc:00-11-01 section 2.2.3.4
                        // If there is an object already bound with the name
                        // and the binding type is not ncontext a NotFound
                        // Exception with a reason of not a context has to be
                        // raised.
                        // Fix for bug Id: 4384628
                        if ( bth.value.value() == BindingType.nobject.value() ) {
                            if ( bt.value() == BindingType.ncontext.value() ) {
                                throw new NotFound(NotFoundReason.not_context, n);
                            }
                        } else {
                            // Previously a Context was bound and now trying to
                            // bind Object. It is invalid.
                            if ( bt.value() == BindingType.nobject.value() ) {
                                throw new NotFound(NotFoundReason.not_object, n);
                            }
                        }
                        impl.Unbind(n[0]);
                    }
                } else {
                    if (impl.Resolve(n[0],bth) != null)
                        throw new org.omg.CosNaming.NamingContextPackage.AlreadyBound();
                }

                // Now there are no other bindings under this name
                impl.Bind(n[0],obj,bt);
            }
        } else {
            // No: bind in a different context
            NamingContext context = resolveFirstAsContext(impl,n);

            // Compute tail
            NameComponent[] tail = new NameComponent[n.length - 1];
            System.arraycopy(n,1,tail,0,n.length-1);

      // How should we propagate the bind
            switch (bt.value()) {
            case BindingType._nobject:
                {
                    // Bind as object
                    if (rebind)
                        context.rebind(tail,obj);
                    else
                        context.bind(tail,obj);
                }
                break;
            case BindingType._ncontext:
                {
                    // Narrow to a naming context using Java casts. It must work.
                    NamingContext objContext = (NamingContext)obj;
                    // Bind as context
                    if (rebind)
                        context.rebind_context(tail,objContext);
                    else
                        context.bind_context(tail,objContext);
                }
                break;
            default:
                // This should not happen
                throw updateWrapper.namingCtxBadBindingtype() ;
            }
        }
    }


   /**
   * Implements resolving names in this NamingContext. The first component
   * of the supplied name is resolved in this NamingContext by calling
   * Resolve(). If there are no more components in the name, the
   * resulting object reference is returned. Otherwise, the resulting object
   * reference must have been bound as a context and be narrowable to
   * a NamingContext. If this is the case, the remaining
   * components of the name is resolved in the resulting NamingContext.
   * This method is static for maximal reuse - even for extended naming
   * context implementations where the recursive semantics still apply.
   * @param impl an implementation of NamingContextDataStore
   * @param n a sequence of NameComponents which is the name to be resolved.
   * @return the object reference bound under the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the first component of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see resolve
   */
    public static org.omg.CORBA.Object doResolve(NamingContextDataStore impl,
                                                 NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        org.omg.CORBA.Object obj = null;
        BindingTypeHolder bth = new BindingTypeHolder();

        // Length must be greater than 0
        if (n.length < 1)
            throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

        // The identifier must be set
        if (n.length == 1) {
            synchronized (impl) {
                // Resolve first level in this context
                obj = impl.Resolve(n[0],bth);
            }
            if (obj == null) {
                // Object was not found
                throw new org.omg.CosNaming.NamingContextPackage.NotFound(NotFoundReason.missing_node,n);
            }
            return obj;
        } else {
            // n.length > 1
            if ( (n[1].id.length() == 0) && (n[1].kind.length() == 0 ) )
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

            NamingContext context = resolveFirstAsContext(impl,n);

            // Compute restOfName = name[1..length]
            NameComponent[] tail = new NameComponent[n.length -1];
            System.arraycopy(n,1,tail,0,n.length-1);

            // Resolve rest of name in context
            return context.resolve(tail);
        }
    }

    /**
   * Implements unbinding bound names in this NamingContext. If the
   * name contains only one component, the name is unbound in this
   * NamingContext using Unbind(). Otherwise, the first component
   * of the name is resolved in this NamingContext and
   * unbind passed to the resulting NamingContext.
   * This method is static for maximal reuse - even for extended naming
   * context implementations where the recursive semantics still apply.
   * @param impl an implementation of NamingContextDataStore
   * @param n a sequence of NameComponents which is the name to be unbound.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with multiple
   * components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the n-1 components of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see resolve
   */
    public static void doUnbind(NamingContextDataStore impl,
                                NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if (n.length < 1)
            throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

        // Unbind here?
        if (n.length == 1) {
            // The identifier must be set
            if ( (n[0].id.length() == 0) && (n[0].kind.length() == 0 ) )
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();

            org.omg.CORBA.Object objRef = null;
            synchronized (impl) {
                // Yes: unbind in this context
                objRef = impl.Unbind(n[0]);
            }

            if (objRef == null)
                // It was not bound
                throw new org.omg.CosNaming.NamingContextPackage.NotFound(NotFoundReason.missing_node,n);
            // Done
            return;
        } else {
            // No: unbind in a different context

      // Resolve first  - must be resolveable
            NamingContext context = resolveFirstAsContext(impl,n);

            // Compute tail
            NameComponent[] tail = new NameComponent[n.length - 1];
            System.arraycopy(n,1,tail,0,n.length-1);

      // Propagate unbind to this context
            context.unbind(tail);
        }
    }

     /**
   * Implements resolving a NameComponent in this context and
   * narrowing it to CosNaming::NamingContext. It will throw appropriate
   * exceptions if not found or not narrowable.
   * @param impl an implementation of NamingContextDataStore
   * @param n a NameComponents which is the name to be found.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound The
   * first component could not be resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the first component of the supplied name.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see resolve
   */
    protected static NamingContext resolveFirstAsContext(NamingContextDataStore impl,
                                                         NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound {
        org.omg.CORBA.Object topRef = null;
        BindingTypeHolder bth = new BindingTypeHolder();
        NamingContext context = null;

        synchronized (impl) {
            // Resolve first  - must be resolveable
            topRef = impl.Resolve(n[0],bth);
            if (topRef == null) {
                // It was not bound
                throw new org.omg.CosNaming.NamingContextPackage.NotFound(NotFoundReason.missing_node,n);
            }
        }

        // Was it bound as a context?
        if (bth.value != BindingType.ncontext) {
            // It was not a context
            throw new org.omg.CosNaming.NamingContextPackage.NotFound(NotFoundReason.not_context,n);
        }

        // Narrow to a naming context
        try {
            context = NamingContextHelper.narrow(topRef);
        } catch (org.omg.CORBA.BAD_PARAM ex) {
            // It was not a context
            throw new org.omg.CosNaming.NamingContextPackage.NotFound(NotFoundReason.not_context,n);
        }

        // Hmm. must be ok
        return context;
    }

    public static String nameToString(NameComponent[] name)
    {
        StringBuffer s = new StringBuffer("{");
        if (name != null || name.length > 0) {
            for (int i=0;i<name.length;i++) {
                if (i>0)
                    s.append(",");
                s.append("[").
                    append(name[i].id).
                    append(",").
                    append(name[i].kind).
                    append("]");
            }
        }
        s.append("}");
        return s.toString();
    }

    // Debugging aids.
    private static boolean debug ;

    private static void dprint(String msg) {
        NamingUtils.dprint("NamingContextImpl("  +
                           Thread.currentThread().getName() + " at " +
                           System.currentTimeMillis() +
                           " ems): " + msg);
    }


    /**
    * Implements all flavors of binding( bind and bindcontext)
    * This method will be called from the superclass's doBind( ) method
    * which takes care of all the conditions before calling this method.
    * i.e., It checks whether the Name is already Bounded, Then in the
    * case of rebind it calls Unbind first.
    * This method does one level binding only, To have n-level binding
    * with compound names, doBind( ) calls this method recursively.
    * @param n a sequence of NameComponents which is the name under which
    * the object will be bound.
    * @param obj the object reference to be bound.
    * @param bt Type of binding (as object or as context).
    * @exception org.omg.CosNaming.NamingContextPackage.NotFound  raised
    * if the NameComoponent list is invalid
    * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed
    * Could not proceed in resolving the Name from the given NameComponent
    * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object
    * is already bound under the supplied name.
    * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
    * system exceptions
    * @see Resolve
    * @see Unbind
    */
    public void Bind(NameComponent n, org.omg.CORBA.Object obj, BindingType bt)
    {
        if( obj == null ) {
            // Raise a Valid Exception and Return
            return;
        }

        InternalBindingKey key = new InternalBindingKey(n);
        InternalBindingValue value;

        try {
            if( bt.value() == BindingType._nobject ) {
                // If the BindingType is an ObjectRef then Stringify this ref and
                // Store it in InternalBindingValue instance. This is required
                // because the Object References has to be stored in file
                value = new InternalBindingValue(bt, orb.object_to_string(obj) );
                value.setObjectRef( obj );
            } else {
                // If the BindingType is a NamingContext then get it's object key
                // from the NameService and store it in the Internal Binding Value instance
                String theNCKey = theNameServiceHandle.getObjectKey( obj );
                value = new InternalBindingValue( bt, theNCKey );
                value.setObjectRef( obj );
            }

            InternalBindingValue oldValue =
                (InternalBindingValue)this.theHashtable.put(key,value);

            if( oldValue != null) {
                // There was an entry with this name in the Hashtable and hence throw CTX_ALREADY_BOUND
                // exception
                throw updateWrapper.namingCtxRebindAlreadyBound() ;
            } else {
                try {
                    // Everything went smooth so update the NamingContext file with the
                    // latest Hashtable image
                    theServantManagerImplHandle.updateContext( objKey, this );
                } catch( Exception e ) {
                    // Something went wrong while updating the context
                    // so speak the error
                    throw updateWrapper.bindUpdateContextFailed( e ) ;
                }
            }
        } catch( Exception e ) {
            // Something went wrong while Binding the Object Reference
            // Speak the error again.
            throw updateWrapper.bindFailure( e ) ;
        }
    }

    /**
    * This method resolves the NamingContext or Object Reference for one level
    * The doResolve( ) method calls Resolve( ) recursively to resolve n level
    * Names.
    * @param n a sequence of NameComponents which is the name to be resolved.
    * @param bt Type of binding (as object or as context).
    * @return the object reference bound under the supplied name.
    * @exception org.omg.CosNaming.NamingContextPackage.NotFound Neither a NamingContext
    * or a Corba Object reference not found under this Name
    * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
    * in resolving the the supplied name.
    * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
    * is invalid (i.e., has length less than 1).
    * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
    * @see Bind
    */
    public Object Resolve(NameComponent n, BindingTypeHolder bth)
        throws SystemException
    {
        if( ( n.id.length() == 0 ) &&( n.kind.length() == 0 ) ) {
            // If the NameComponent list has no entry then it means the current
            // context was requested
            bth.value = BindingType.ncontext;
            return theNameServiceHandle.getObjectReferenceFromKey(
                this.objKey );
        }

        InternalBindingKey key = new InternalBindingKey(n);
        InternalBindingValue value =
            (InternalBindingValue) this.theHashtable.get(key);

        if( value == null ) {
            // No entry was found for the given name and hence return NULL
            // NamingContextDataStore throws appropriate exception if
            // required.
            return null;
        }

        Object theObjectFromStringifiedReference = null;
        bth.value = value.theBindingType;

        try {
            // Check whether the entry found in the Hashtable starts with NC
            // Which means it's a name context. So get the NamingContext reference
            // from ServantManager, which would either return from the cache or
            // read it from the File.
            if( value.strObjectRef.startsWith( "NC" ) ) {
                bth.value = BindingType.ncontext;
                return theNameServiceHandle.getObjectReferenceFromKey( value.strObjectRef );
            } else {
                // Else, It is a Object Reference. Check whether Object Reference
                // can be obtained directly, If not then convert the stringified
                // reference to object and return.
                theObjectFromStringifiedReference = value.getObjectRef( );

                if (theObjectFromStringifiedReference == null ) {
                    try {
                        theObjectFromStringifiedReference =
                        orb.string_to_object( value.strObjectRef );
                        value.setObjectRef( theObjectFromStringifiedReference );
                    } catch( Exception e ) {
                        throw readWrapper.resolveConversionFailure(
                            CompletionStatus.COMPLETED_MAYBE, e );
                    }
                }
            }
        } catch ( Exception e ) {
            throw readWrapper.resolveFailure(
                CompletionStatus.COMPLETED_MAYBE, e );
        }

        return theObjectFromStringifiedReference;
    }

   /**
   * This method Unbinds the NamingContext or Object Reference for one level
   * The doUnbind( ) method from superclass calls Unbind() to recursively
   * Unbind using compound Names.
   * @param n a sequence of NameComponents which is the name to be resolved.
   * @return the object reference bound under the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound Neither a NamingContext
   * or a Corba Object reference not found under this Name
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not proceed
   * in resolving the the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied name
   * is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   * @see Bind
   */

    public Object Unbind(NameComponent n) throws SystemException
    {
        try {
            InternalBindingKey key = new InternalBindingKey(n);
            InternalBindingValue value = null;

            try {
                value = (InternalBindingValue) this.theHashtable.remove(key);
            } catch( Exception e ) {
                // Ignore the exception in Hashtable.remove
            }

            theServantManagerImplHandle.updateContext( objKey, this );

            if( value == null ) {
                return null;
            }

            if( value.strObjectRef.startsWith( "NC" ) ) {
                theServantManagerImplHandle.readInContext( value.strObjectRef );
                Object theObjectFromStringfiedReference =
                theNameServiceHandle.getObjectReferenceFromKey( value.strObjectRef );
                return theObjectFromStringfiedReference;
            } else {
                Object theObjectFromStringifiedReference = value.getObjectRef( );

                if( theObjectFromStringifiedReference == null ) {
                    theObjectFromStringifiedReference =
                    orb.string_to_object( value.strObjectRef );
                }

                return theObjectFromStringifiedReference;
            }
        } catch( Exception e ) {
            throw updateWrapper.unbindFailure( CompletionStatus.COMPLETED_MAYBE, e );
        }
    }

   /**
   * List the contents of this NamingContext. It creates a new
   * PersistentBindingIterator object and passes it a clone of the
   * hash table and an orb object. It then uses the
   * newly created object to return the required number of bindings.
   * @param how_many The number of requested bindings in the BindingList.
   * @param bl The BindingList as an out parameter.
   * @param bi The BindingIterator as an out parameter.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   */

    public void List(int how_many, BindingListHolder bl,
                     BindingIteratorHolder bi) throws SystemException
    {
        if( biPOA == null ) {
            createbiPOA( );
        }
        try {
            PersistentBindingIterator bindingIterator =
                new PersistentBindingIterator(this.orb,
                (Hashtable)this.theHashtable.clone(), biPOA);
            // Have it set the binding list
            bindingIterator.list(how_many,bl);

            byte[] objectId = biPOA.activate_object( bindingIterator );
            org.omg.CORBA.Object obj = biPOA.id_to_reference( objectId );

            // Get the object reference for the binding iterator servant
            org.omg.CosNaming.BindingIterator bindingRef =
                org.omg.CosNaming.BindingIteratorHelper.narrow( obj );

            bi.value = bindingRef;
        } catch (org.omg.CORBA.SystemException e) {
            throw e;
        } catch( Exception e ) {
            throw readWrapper.transNcListGotExc( e ) ;
        }
    }

    private synchronized void createbiPOA( ) {
        if( biPOA != null ) {
            return;
        }
        try {
            POA rootPOA = (POA) orb.resolve_initial_references(
                ORBConstants.ROOT_POA_NAME );
            rootPOA.the_POAManager().activate( );

            int i = 0;
            Policy[] poaPolicy = new Policy[3];
            poaPolicy[i++] = rootPOA.create_lifespan_policy(
                LifespanPolicyValue.TRANSIENT);
            poaPolicy[i++] = rootPOA.create_id_assignment_policy(
                IdAssignmentPolicyValue.SYSTEM_ID);
            poaPolicy[i++] = rootPOA.create_servant_retention_policy(
                ServantRetentionPolicyValue.RETAIN);
            biPOA = rootPOA.create_POA("BindingIteratorPOA", null, poaPolicy );
            biPOA.the_POAManager().activate( );
        } catch( Exception e ) {
            throw readWrapper.namingCtxBindingIteratorCreate( e ) ;
        }
    }


   /**
   * Create a NamingContext object and return its object reference.
   * @return an object reference for a new NamingContext object implemented
   * by this Name Server.
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   */
    public NamingContext NewContext() throws SystemException
    {
        try {
            return  theNameServiceHandle.NewContext( );
        } catch( org.omg.CORBA.SystemException e ) {
            throw e;
        } catch( Exception e ) {
            throw updateWrapper.transNcNewctxGotExc( e ) ;
        }
     }


   /**
   * Destroys the NamingContext.
   */
    public void Destroy() throws SystemException
    {
        // XXX note that orb.disconnect is illegal here, since the
        // POA is used.  However, there may be some associated state
        // that needs to be cleaned up in ServerManagerImpl which we will
        // look into further at another time.
        /*
        // XXX This needs to be replaced by cleaning up the
        // file that backs up the naming context.  No explicit
        // action is necessary at the POA level, since this is
        // created with the non-retain policy.
        /*
        try { orb.disconnect(
            theNameServiceHandle.getObjectReferenceFromKey( this.objKey ) );
        } catch( org.omg.CORBA.SystemException e ) {
            throw e;
        } catch( Exception e ) {
            throw updateWrapper.transNcDestroyGotEx( e ) ;
        }
        */
    }

    /**
    * This operation creates a stringified name from the array of Name
    * components.
    * @param n Name of the object <p>
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.<p>
    *
    */
    public String to_string(org.omg.CosNaming.NameComponent[] n)
         throws org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if ( (n == null ) || (n.length == 0) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }

        String theStringifiedName = getINSImpl().convertToString( n );

        if( theStringifiedName == null )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }

        return theStringifiedName;
    }

    /**
    * This operation  converts a Stringified Name into an  equivalent array
    * of Name Components.
    * @param sn Stringified Name of the object <p>
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.<p>
    *
    */
    public org.omg.CosNaming.NameComponent[] to_name(String sn)
         throws org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if  ( (sn == null ) || (sn.length() == 0) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }
        org.omg.CosNaming.NameComponent[] theNameComponents =
                getINSImpl().convertToNameComponent( sn );
        if( ( theNameComponents == null ) || (theNameComponents.length == 0 ) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }
        for( int i = 0; i < theNameComponents.length; i++ ) {
            // If there is a name component whose id and kind null or
            // zero length string, then an invalid name exception needs to be
            // raised.
            if ( ( ( theNameComponents[i].id  == null )
                 ||( theNameComponents[i].id.length() == 0 ) )
               &&( ( theNameComponents[i].kind == null )
                 ||( theNameComponents[i].kind.length() == 0 ) ) ) {
                throw new InvalidName();
            }
        }
        return theNameComponents;
    }

    /**
    * This operation creates a URL based "iiopname://" format name
    * from the Stringified Name of the object.
    * @param addr internet based address of the host machine where
    * Name Service is running <p>
    * @param sn Stringified Name of the object <p>
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.<p>
    * @exception org.omg.CosNaming.NamingContextPackage.InvalidAddress
    * Indicates the internet based address of the host machine is
    * incorrect <p>
    *
    */

    public String to_url(String addr, String sn)
        throws org.omg.CosNaming.NamingContextExtPackage.InvalidAddress,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if  ( (sn == null ) || (sn.length() == 0) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }
        if( addr == null )
        {
                throw new org.omg.CosNaming.NamingContextExtPackage.InvalidAddress();
        }
        String urlBasedAddress = null;
        try {
            urlBasedAddress = getINSImpl().createURLBasedAddress( addr, sn );
        } catch (Exception e ) {
            urlBasedAddress = null;
        }
        // Extra check to see that corba name url created is valid as per
        // INS spec grammer.
        try {
            INSURLHandler.getINSURLHandler().parseURL( urlBasedAddress );
        } catch( BAD_PARAM e ) {
            throw new
                org.omg.CosNaming.NamingContextExtPackage.InvalidAddress();
        }
        return urlBasedAddress;
    }

    /**
     * This operation resolves the Stringified name into the object
     * reference.
     * @param sn Stringified Name of the object <p>
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound
     * Indicates there is no object reference for the given name. <p>
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed
     * Indicates that the given compound name is incorrect <p>
     * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
     * Indicates the name does not identify a binding.<p>
     *
     */
    public org.omg.CORBA.Object resolve_str(String sn)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        org.omg.CORBA.Object theObject = null;
        // Name valid?
        if  ( (sn == null ) || (sn.length() == 0) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }
        org.omg.CosNaming.NameComponent[] theNameComponents =
                getINSImpl().convertToNameComponent( sn );
        if( ( theNameComponents == null ) || (theNameComponents.length == 0 ) )
        {
                throw new org.omg.CosNaming.NamingContextPackage.InvalidName();
        }
        theObject = resolve( theNameComponents );
        return theObject;
    }

   /**
   * This is a Debugging Method
   */
    public boolean IsEmpty()
    {
        return this.theHashtable.isEmpty();
    }

   /**
   * This is a Debugging Method
   */
    public void printSize( )
    {
        System.out.println( "Hashtable Size = " + theHashtable.size( ) );
        java.util.Enumeration e = theHashtable.keys( );
        for( ; e.hasMoreElements(); )
        {
              InternalBindingValue thevalue =
                        (InternalBindingValue) this.theHashtable.get(e.nextElement());
                if( thevalue != null )
                {
                        System.out.println( "value = " + thevalue.strObjectRef);
                }
        }
    }

}
