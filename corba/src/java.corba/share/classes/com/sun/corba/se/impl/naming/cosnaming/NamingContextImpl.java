/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

// Imports for Logging
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.corba.se.impl.orbutil.LogKeywords;

// Import general CORBA classes
import org.omg.CORBA.Object;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.CompletionStatus;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.Servant;

// Import org.omg.CosNaming classes
import org.omg.CosNaming.BindingType;
import org.omg.CosNaming.BindingTypeHolder;
import org.omg.CosNaming.BindingListHolder;
import org.omg.CosNaming.BindingIteratorHolder;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextHelper;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CosNaming._NamingContextImplBase;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtPOA;
import org.omg.CosNaming.NamingContextExtPackage.*;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import com.sun.corba.se.impl.naming.cosnaming.NamingContextDataStore;

import com.sun.corba.se.impl.naming.namingutil.INSURLHandler;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.impl.logging.NamingSystemException ;

import com.sun.corba.se.spi.orb.ORB;

/**
 * Class NamingContextImpl implements the org.omg.CosNaming::NamingContext
 * interface, but does not implement the methods associated with
 * maintaining the "table" of current bindings in a NamingContext.
 * Instead, this implementation assumes that the derived implementation
 * implements the NamingContextDataStore interface, which has the necessary
 * methods. This allows multiple
 * NamingContext implementations that differ in storage of the bindings,
 * as well as implementations of interfaces derived from
 * CosNaming::NamingContext that still reuses the implementation.
 * <p>
 * The operations bind(), rebind(), bind_context() and rebind_context()
 * are all really implemented by doBind(). resolve() is really implemented
 * by doResolve(), unbind() by doUnbind(). list(), new_context() and
 * destroy() uses the NamingContextDataStore interface directly. All the
 * doX() methods are public static.
 * They synchronize on the NamingContextDataStore object.
 * <p>
 * An implementation a NamingContext must extend this class and implement
 * the NamingContextDataStore interface with the operations:
 * Bind(), Resolve(),
 * Unbind(), List(), NewContext() and Destroy(). Calls
 * to these methods are synchronized; these methods should
 * therefore not be synchronized.
 */
public abstract class NamingContextImpl
    extends NamingContextExtPOA
    implements NamingContextDataStore
{

    protected POA nsPOA;
    private Logger readLogger, updateLogger, lifecycleLogger;
    private NamingSystemException wrapper ;
    private static NamingSystemException staticWrapper =
        NamingSystemException.get( CORBALogDomains.NAMING_UPDATE ) ;

    // The grammer for Parsing and Building Interoperable Stringified Names
    // are implemented in this class
    private InterOperableNamingImpl insImpl;
    /**
     * Create a naming context servant.
     * Runs the super constructor.
     * @param orb an ORB object.
     * @exception java.lang.Exception a Java exception.
     */
    public NamingContextImpl(ORB orb, POA poa) throws java.lang.Exception {
        super();
        this.orb = orb;
        wrapper = NamingSystemException.get( orb,
            CORBALogDomains.NAMING_UPDATE ) ;

        insImpl = new InterOperableNamingImpl( );
        this.nsPOA = poa;
        readLogger = orb.getLogger( CORBALogDomains.NAMING_READ);
        updateLogger = orb.getLogger( CORBALogDomains.NAMING_UPDATE);
        lifecycleLogger = orb.getLogger(
            CORBALogDomains.NAMING_LIFECYCLE);
    }

    public POA getNSPOA( ) {
        return nsPOA;
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
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could
     * not proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object
     * is already bound under the supplied name.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doBind
     */
    public void bind(NameComponent[] n, org.omg.CORBA.Object obj)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound
    {
        if( obj == null )
        {
            updateLogger.warning( LogKeywords.NAMING_BIND +
                " unsuccessful because NULL Object cannot be Bound " );
            throw wrapper.objectIsNull() ;
        }
        // doBind implements all four flavors of binding
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doBind(impl,n,obj,false,BindingType.nobject);
        if( updateLogger.isLoggable( Level.FINE  ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine( LogKeywords.NAMING_BIND_SUCCESS + " Name = " +
                NamingUtils.getDirectoryStructuredName( n ) );
        }
    }


    /**
     * Bind a NamingContext under a name in this NamingContext. If the name
     * contains multiple (n) components, n-1 will be resolved in this
     * NamingContext and the object bound in resulting NamingContext.
     * An exception is thrown if a binding with the supplied name already
     * exists. The NamingContext will participate in recursive resolving.
     * @param n a sequence of NameComponents which is the name under which
     * the object will be bound.
     * @param nc the NamingContext object reference to be bound.
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could
     * not proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object
     * is already bound under the supplied name.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doBind
     */
    public void bind_context(NameComponent[] n, NamingContext nc)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName,
               org.omg.CosNaming.NamingContextPackage.AlreadyBound
    {
        if( nc == null ) {
            updateLogger.warning( LogKeywords.NAMING_BIND_FAILURE +
                " NULL Context cannot be Bound " );
            throw new BAD_PARAM( "Naming Context should not be null " );
        }
        // doBind implements all four flavors of binding
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doBind(impl,n,nc,false,BindingType.ncontext);
        if( updateLogger.isLoggable( Level.FINE ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine( LogKeywords.NAMING_BIND_SUCCESS + " Name = " +
                NamingUtils.getDirectoryStructuredName( n ) );
        }
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
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
     * proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doBind
     */
    public  void rebind(NameComponent[] n, org.omg.CORBA.Object obj)
        throws       org.omg.CosNaming.NamingContextPackage.NotFound,
                     org.omg.CosNaming.NamingContextPackage.CannotProceed,
                     org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        if( obj == null )
        {
            updateLogger.warning( LogKeywords.NAMING_REBIND_FAILURE +
                " NULL Object cannot be Bound " );
            throw wrapper.objectIsNull() ;
        }
        try {
            // doBind implements all four flavors of binding
            NamingContextDataStore impl = (NamingContextDataStore)this;
            doBind(impl,n,obj,true,BindingType.nobject);
        } catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound ex) {
            updateLogger.warning( LogKeywords.NAMING_REBIND_FAILURE +
                NamingUtils.getDirectoryStructuredName( n ) +
                " is already bound to a Naming Context" );
            // This should not happen
            throw wrapper.namingCtxRebindAlreadyBound( ex ) ;
        }
        if( updateLogger.isLoggable( Level.FINE  ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine( LogKeywords.NAMING_REBIND_SUCCESS + " Name = " +
                NamingUtils.getDirectoryStructuredName( n ) );
        }
    }

    /**
     * Bind a NamingContext under a name in this NamingContext. If the name
     * contains multiple (n) components, the first n-1 components will be
     * resolved in this NamingContext and the object bound in resulting
     * NamingContext. If a binding under the supplied name already exists it
     * will be unbound first. The NamingContext will participate in recursive
     * resolving.
     * @param n a sequence of NameComponents which is the name under which
     * the object will be bound.
     * @param nc the object reference to be bound.
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
     * proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doBind
     */
    public  void rebind_context(NameComponent[] n, NamingContext nc)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        if( nc == null )
        {
            updateLogger.warning( LogKeywords.NAMING_REBIND_FAILURE +
                " NULL Context cannot be Bound " );
            throw wrapper.objectIsNull() ;
        }
        try {
            // doBind implements all four flavors of binding
            NamingContextDataStore impl = (NamingContextDataStore)this;
            doBind(impl,n,nc,true,BindingType.ncontext);
        } catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound ex) {
            // This should not happen
            updateLogger.warning( LogKeywords.NAMING_REBIND_FAILURE +
                NamingUtils.getDirectoryStructuredName( n ) +
                " is already bound to a CORBA Object" );
            throw wrapper.namingCtxRebindctxAlreadyBound( ex ) ;
        }
        if( updateLogger.isLoggable( Level.FINE ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine( LogKeywords.NAMING_REBIND_SUCCESS + " Name = " +
                NamingUtils.getDirectoryStructuredName( n ) );
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
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
     * proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doResolve
     */
    public  org.omg.CORBA.Object resolve(NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // doResolve actually resolves
        NamingContextDataStore impl = (NamingContextDataStore)this;
        org.omg.CORBA.Object obj = doResolve(impl,n);
        if( obj != null ) {
            if( readLogger.isLoggable( Level.FINE ) ) {
                 readLogger.fine( LogKeywords.NAMING_RESOLVE_SUCCESS +
                 " Name: " + NamingUtils.getDirectoryStructuredName( n ) );
            }
        } else {
             readLogger.warning( LogKeywords.NAMING_RESOLVE_FAILURE +
                 " Name: " + NamingUtils.getDirectoryStructuredName( n ) );
        }
        return obj;
    }


    /**
     * Remove a binding from this NamingContext. If the name contains
     * multiple (n) components, the first n-1 components will be resolved
     * from this NamingContext and the final component unbound in
     * the resulting NamingContext.
     * @param n a sequence of NameComponents which is the name to be unbound.
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
     * proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see doUnbind
     */
    public  void unbind(NameComponent[] n)
        throws org.omg.CosNaming.NamingContextPackage.NotFound,
               org.omg.CosNaming.NamingContextPackage.CannotProceed,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // doUnbind actually unbinds
        NamingContextDataStore impl = (NamingContextDataStore)this;
        doUnbind(impl,n);
        if( updateLogger.isLoggable( Level.FINE ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine( LogKeywords.NAMING_UNBIND_SUCCESS +
                " Name: " + NamingUtils.getDirectoryStructuredName( n ) );
        }
    }

    /**
     * List the contents of this NamingContest. A sequence of bindings
     * is returned (a BindingList) containing up to the number of requested
     * bindings, and a BindingIterator object reference is returned for
     * iterating over the remaining bindings.
     * @param how_many The number of requested bindings in the BindingList.
     * @param bl The BindingList as an out parameter.
     * @param bi The BindingIterator as an out parameter.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see BindingListHolder
     * @see BindingIteratorImpl
     */
    public  void list(int how_many, BindingListHolder bl,
        BindingIteratorHolder bi)
    {
        // List actually generates the list
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            impl.List(how_many,bl,bi);
        }
        if( readLogger.isLoggable( Level.FINE ) && (bl.value != null )) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            readLogger.fine ( LogKeywords.NAMING_LIST_SUCCESS +
                "list(" + how_many + ") -> bindings[" + bl.value.length +
                "] + iterator: " + bi.value);
        }
    }

    /**
     * Create a NamingContext object and return its object reference.
     * @return an object reference for a new NamingContext object implemented
     * by this Name Server.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     */
    public synchronized NamingContext new_context()
    {
        // Create actually creates a new naming context
        lifecycleLogger.fine( "Creating New Naming Context " );
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            NamingContext nctx = impl.NewContext();
            if( nctx != null ) {
                lifecycleLogger.fine( LogKeywords.LIFECYCLE_CREATE_SUCCESS );
            } else {
                // If naming context is null, then that must be a serious
                // error.
                lifecycleLogger.severe ( LogKeywords.LIFECYCLE_CREATE_FAILURE );
            }
            return nctx;
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
     * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object
     * is already bound under the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
     * proceed in resolving the n-1 components of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
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
        if( updateLogger.isLoggable( Level.FINE ) ) {
            // isLoggable call to make sure that we save some precious
            // processor cycles, if there is no need to log.
            updateLogger.fine ( LogKeywords.NAMING_BIND +
                "New Context Bound To " +
                NamingUtils.getDirectoryStructuredName( n ) );
        }
        return rnc;
    }

    /**
     * Destroy this NamingContext object. If this NamingContext contains
     * no bindings, the NamingContext is deleted.
     * @exception org.omg.CosNaming.NamingContextPackage.NotEmpty This
     * NamingContext is not empty (i.e., contains bindings).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     */
    public  void destroy()
        throws org.omg.CosNaming.NamingContextPackage.NotEmpty
    {
        lifecycleLogger.fine( "Destroying Naming Context " );
        NamingContextDataStore impl = (NamingContextDataStore)this;
        synchronized (impl) {
            if (impl.IsEmpty() == true) {
                // The context is empty so it can be destroyed
                impl.Destroy();
                lifecycleLogger.fine ( LogKeywords.LIFECYCLE_DESTROY_SUCCESS );
            }
            else {
                // This context is not empty!
                // Not a fatal error, warning should do.
                lifecycleLogger.warning( LogKeywords.LIFECYCLE_DESTROY_FAILURE +
                    " NamingContext children are not destroyed still.." );
                throw new NotEmpty();
            }
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
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
     * multiple components was supplied, but the first component could not be
     * resolved.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not     * proceed
     * in resolving the first component of the supplied name.
     * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The
     * supplied name is invalid (i.e., has length less than 1).
     * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound An object
     * is already bound under the supplied name.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA
     * system exceptions.
     * @see resolve
     * @see unbind
     * @see bind
     * @see bind_context
     * @see rebind
     * @see rebind_context
     */
    public static void doBind(NamingContextDataStore impl,
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
            throw new InvalidName();

    // At bottom level?
        if (n.length == 1) {
            // The identifier must be set
            if ( (n[0].id.length() == 0) && (n[0].kind.length() == 0 ) ) {
                throw new InvalidName();
            }

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
                        if ( bth.value.value() == BindingType.nobject.value() ){
                            if ( bt.value() == BindingType.ncontext.value() ) {
                                throw new NotFound(
                                    NotFoundReason.not_context, n);
                            }
                        } else {
                            // Previously a Context was bound and now trying to
                            // bind Object. It is invalid.
                            if ( bt.value() == BindingType.nobject.value() ) {
                                throw new NotFound(
                                    NotFoundReason.not_object, n);
                            }
                        }
                        impl.Unbind(n[0]);
                    }

                } else {
                    if (impl.Resolve(n[0],bth) != null)
                        // "Resistence is futile." [Borg pickup line]
                        throw new AlreadyBound();
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
                    // Narrow to a naming context using Java casts. It must
                    // work.
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
                throw staticWrapper.namingCtxBadBindingtype() ;
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
   * @exception org.omg.CosNaming.NamingContextPackage.NotFound A name with
   * multiple components was supplied, but the first component could not be
   * resolved.
   * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed Could not
   * proceed
   * in resolving the first component of the supplied name.
   * @exception org.omg.CosNaming.NamingContextPackage.InvalidName The supplied
   * name is invalid (i.e., has length less than 1).
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system
   * exceptions.
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
            throw new InvalidName();

        // The identifier must be set
        if (n.length == 1) {
            synchronized (impl) {
                // Resolve first level in this context
                obj = impl.Resolve(n[0],bth);
            }
            if (obj == null) {
                // Object was not found
                throw new NotFound(NotFoundReason.missing_node,n);
            }
            return obj;
        } else {
            // n.length > 1
            if ( (n[1].id.length() == 0) && (n[1].kind.length() == 0) ) {
                throw new InvalidName();
            }

            NamingContext context = resolveFirstAsContext(impl,n);

            // Compute restOfName = name[1..length]
            NameComponent[] tail = new NameComponent[n.length -1];
            System.arraycopy(n,1,tail,0,n.length-1);

            // Resolve rest of name in context
            try {
                // First try to resolve using the local call, this should work
                // most of the time unless there are federated naming contexts.
                Servant servant = impl.getNSPOA().reference_to_servant(
                    context );
                return doResolve(((NamingContextDataStore)servant), tail) ;
            } catch( Exception e ) {
                return context.resolve(tail);
            }
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
            throw new InvalidName();

    // Unbind here?
        if (n.length == 1) {
            // The identifier must be set
            if ( (n[0].id.length() == 0) && (n[0].kind.length() == 0 ) ) {
                throw new InvalidName();
            }

            org.omg.CORBA.Object objRef = null;
            synchronized (impl) {
                // Yes: unbind in this context
                objRef = impl.Unbind(n[0]);
            }

            if (objRef == null)
                // It was not bound
                throw new NotFound(NotFoundReason.missing_node,n);
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
                throw new NotFound(NotFoundReason.missing_node,n);
            }
        }

        // Was it bound as a context?
        if (bth.value != BindingType.ncontext) {
            // It was not a context
            throw new NotFound(NotFoundReason.not_context,n);
        }

        // Narrow to a naming context
        try {
            context = NamingContextHelper.narrow(topRef);
        } catch (org.omg.CORBA.BAD_PARAM ex) {
            // It was not a context
            throw new NotFound(NotFoundReason.not_context,n);
        }

        // Hmm. must be ok
        return context;
    }


   /**
    * This operation creates a stringified name from the array of Name
    * components.
    * @param n Name of the object
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.
    */
    public String to_string(org.omg.CosNaming.NameComponent[] n)
         throws org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if ( (n == null ) || (n.length == 0) )
        {
                throw new InvalidName();
        }
        NamingContextDataStore impl = (NamingContextDataStore)this;

        String theStringifiedName = insImpl.convertToString( n );

        if( theStringifiedName == null )
        {
                throw new InvalidName();
        }

        return theStringifiedName;
    }


   /**
    * This operation  converts a Stringified Name into an  equivalent array
    * of Name Components.
    * @param sn Stringified Name of the object
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.
    */
    public org.omg.CosNaming.NameComponent[] to_name(String sn)
         throws org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if  ( (sn == null ) || (sn.length() == 0) )
        {
                throw new InvalidName();
        }
        NamingContextDataStore impl = (NamingContextDataStore)this;
        org.omg.CosNaming.NameComponent[] theNameComponents =
                insImpl.convertToNameComponent( sn );
        if( ( theNameComponents == null ) || (theNameComponents.length == 0 ) )
        {
                throw new InvalidName();
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
    * Name Service is running
    * @param sn Stringified Name of the object
    * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
    * Indicates the name does not identify a binding.
    * @exception org.omg.CosNaming.NamingContextPackage.InvalidAddress
    * Indicates the internet based address of the host machine is
    * incorrect
    */

    public String to_url(String addr, String sn)
        throws org.omg.CosNaming.NamingContextExtPackage.InvalidAddress,
               org.omg.CosNaming.NamingContextPackage.InvalidName
    {
        // Name valid?
        if  ( (sn == null ) || (sn.length() == 0) )
        {
            throw new InvalidName();
        }
        if( addr == null )
        {
            throw new
                org.omg.CosNaming.NamingContextExtPackage.InvalidAddress();
        }
        NamingContextDataStore impl = (NamingContextDataStore)this;
        String urlBasedAddress = null;
        urlBasedAddress = insImpl.createURLBasedAddress( addr, sn );
        // Extra check to see that corba name url created is valid as per
        // INS spec grammer.
        try {
            INSURLHandler.getINSURLHandler( ).parseURL( urlBasedAddress );
        } catch( BAD_PARAM e ) {
            throw new
                org.omg.CosNaming.NamingContextExtPackage.InvalidAddress();
        }
        return urlBasedAddress;
    }

    /**
     * This operation resolves the Stringified name into the object
     * reference.
     * @param sn Stringified Name of the object
     * @exception org.omg.CosNaming.NamingContextPackage.NotFound
     * Indicates there is no object reference for the given name.
     * @exception org.omg.CosNaming.NamingContextPackage.CannotProceed
     * Indicates that the given compound name is incorrect
     * @exception org.omg.CosNaming.NamingContextExtPackage.InvalidName
     * Indicates the name does not identify a binding.
     * @exception org.omg.CosNaming.NamingContextPackage.AlreadyBound
     * Indicates the name is already bound.
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
                throw new InvalidName();
        }
        NamingContextDataStore impl = (NamingContextDataStore)this;
        org.omg.CosNaming.NameComponent[] theNameComponents =
                insImpl.convertToNameComponent( sn );

        if( ( theNameComponents == null ) || (theNameComponents.length == 0 ) )
        {
                throw new InvalidName();
        }
        theObject = resolve( theNameComponents );
        return theObject;
    }


    transient protected ORB orb;

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
    public static final boolean debug = false;

    private static void dprint(String msg) {
        NamingUtils.dprint("NamingContextImpl("  +
                           Thread.currentThread().getName() + " at " +
                           System.currentTimeMillis() +
                           " ems): " + msg);
    }
}
