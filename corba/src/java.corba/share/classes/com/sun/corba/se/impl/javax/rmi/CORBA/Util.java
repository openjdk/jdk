/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.javax.rmi.CORBA; // Util (sed marker, don't remove!)

import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.MarshalException;

import java.rmi.server.RMIClassLoader;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Map;
import java.util.WeakHashMap;

import java.io.Serializable;
import java.io.NotSerializableException;

import java.lang.reflect.Constructor;

import javax.rmi.CORBA.ValueHandler;
import javax.rmi.CORBA.Tie;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.AccessException;
import java.rmi.Remote;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.ServerRuntimeException;

import javax.transaction.TransactionRequiredException;
import javax.transaction.TransactionRolledbackException;
import javax.transaction.InvalidTransactionException;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.INV_OBJREF;
import org.omg.CORBA.NO_PERMISSION;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.OBJECT_NOT_EXIST;
import org.omg.CORBA.TRANSACTION_REQUIRED;
import org.omg.CORBA.TRANSACTION_ROLLEDBACK;
import org.omg.CORBA.INVALID_TRANSACTION;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.ACTIVITY_REQUIRED;
import org.omg.CORBA.ACTIVITY_COMPLETED;
import org.omg.CORBA.INVALID_ACTIVITY;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.portable.UnknownException;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;

// This class must be able to function with non-Sun ORBs.
// This means that any of the following com.sun.corba classes
// must only occur in contexts that also handle the non-Sun case.

import com.sun.corba.se.pept.transport.ContactInfoList ;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.protocol.CorbaClientDelegate;
import com.sun.corba.se.spi.transport.CorbaContactInfoList ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher ;
import com.sun.corba.se.spi.copyobject.ReflectiveCopyException ;
import com.sun.corba.se.spi.copyobject.CopierManager ;
import com.sun.corba.se.spi.copyobject.ObjectCopierFactory ;
import com.sun.corba.se.spi.copyobject.ObjectCopier ;
import com.sun.corba.se.impl.io.ValueHandlerImpl;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.logging.OMGSystemException;
import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.util.IdentityHashtable;
import com.sun.corba.se.impl.util.JDKBridge;
import com.sun.corba.se.impl.logging.UtilSystemException;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import sun.corba.SharedSecrets;


/**
 * Provides utility methods that can be used by stubs and ties to
 * perform common operations.
 */
public class Util implements javax.rmi.CORBA.UtilDelegate
{
    // Runs as long as there are exportedServants
    private static KeepAlive keepAlive = null;

    // Maps targets to ties.
    private static IdentityHashtable exportedServants = new IdentityHashtable();

    private static final ValueHandlerImpl valueHandlerSingleton =
        SharedSecrets.getJavaCorbaAccess().newValueHandlerImpl();

    private UtilSystemException utilWrapper = UtilSystemException.get(
                                                  CORBALogDomains.RPC_ENCODING);

    private static Util instance = null;

    public Util() {
        setInstance(this);
    }

    private static void setInstance( Util util ) {
        assert instance == null : "Instance already defined";
        instance = util;
    }

    public static Util getInstance() {
        return instance;
    }

    public static boolean isInstanceDefined() {
        return instance != null;
    }

    // Used by TOAFactory.shutdown to unexport all targets for this
    // particular ORB.  This happens during ORB shutdown.
    public void unregisterTargetsForORB(org.omg.CORBA.ORB orb)
    {
        for (Enumeration e = exportedServants.keys(); e.hasMoreElements(); )
        {
            java.lang.Object key = e.nextElement();
            Remote target = (Remote)(key instanceof Tie ? ((Tie)key).getTarget() : key);

            // Bug 4476347: BAD_OPERATION is thrown if the ties delegate isn't set.
            // We can ignore this because it means the tie is not connected to an ORB.
            try {
                if (orb == getTie(target).orb()) {
                    try {
                        unexportObject(target);
                    } catch( java.rmi.NoSuchObjectException ex ) {
                        // We neglect this exception if at all if it is
                        // raised. It is not harmful.
                    }
                }
            } catch (BAD_OPERATION bad) {
                /* Ignore */
            }
        }
    }

   /**
     * Maps a SystemException to a RemoteException.
     * @param ex the SystemException to map.
     * @return the mapped exception.
     */
    public RemoteException mapSystemException(SystemException ex)
    {
        if (ex instanceof UnknownException) {
            Throwable orig = ((UnknownException)ex).originalEx;
            if (orig instanceof Error) {
                return new ServerError("Error occurred in server thread",(Error)orig);
            } else if (orig instanceof RemoteException) {
                return new ServerException("RemoteException occurred in server thread",
                    (Exception)orig);
            } else if (orig instanceof RuntimeException) {
                throw (RuntimeException) orig;
            }
        }

        // Build the message string...
        String name = ex.getClass().getName();
        String corbaName = name.substring(name.lastIndexOf('.')+1);
        String status;
        switch (ex.completed.value()) {
            case CompletionStatus._COMPLETED_YES:
                status = "Yes";
                break;
            case CompletionStatus._COMPLETED_NO:
                status = "No";
                break;
            case CompletionStatus._COMPLETED_MAYBE:
            default:
                status = "Maybe";
                break;
        }

        String message = "CORBA " + corbaName + " " + ex.minor + " " + status;

        // Now map to the correct RemoteException type...
        if (ex instanceof COMM_FAILURE) {
            return new MarshalException(message, ex);
        } else if (ex instanceof INV_OBJREF) {
            RemoteException newEx = new NoSuchObjectException(message);
            newEx.detail = ex;
            return newEx;
        } else if (ex instanceof NO_PERMISSION) {
            return new AccessException(message, ex);
        } else if (ex instanceof MARSHAL) {
            return new MarshalException(message, ex);
        } else if (ex instanceof OBJECT_NOT_EXIST) {
            RemoteException newEx = new NoSuchObjectException(message);
            newEx.detail = ex;
            return newEx;
        } else if (ex instanceof TRANSACTION_REQUIRED) {
            RemoteException newEx = new TransactionRequiredException(message);
            newEx.detail = ex;
            return newEx;
        } else if (ex instanceof TRANSACTION_ROLLEDBACK) {
            RemoteException newEx = new TransactionRolledbackException(message);
            newEx.detail = ex;
            return newEx;
        } else if (ex instanceof INVALID_TRANSACTION) {
            RemoteException newEx = new InvalidTransactionException(message);
            newEx.detail = ex;
            return newEx;
        } else if (ex instanceof BAD_PARAM) {
            Exception inner = ex;

            // Pre-Merlin Sun ORBs used the incorrect minor code for
            // this case.  See Java to IDL ptc-00-01-08 1.4.8.
            if (ex.minor == ORBConstants.LEGACY_SUN_NOT_SERIALIZABLE ||
                ex.minor == OMGSystemException.NOT_SERIALIZABLE) {

                if (ex.getMessage() != null)
                    inner = new NotSerializableException(ex.getMessage());
                else
                    inner = new NotSerializableException();

                inner.initCause( ex ) ;
            }

            return new MarshalException(message,inner);
        } else if (ex instanceof ACTIVITY_REQUIRED) {
            try {
                Class<?> cl = SharedSecrets.getJavaCorbaAccess().loadClass(
                               "javax.activity.ActivityRequiredException");
                Class[] params = new Class[2];
                params[0] = java.lang.String.class;
                params[1] = java.lang.Throwable.class;
                Constructor cr = cl.getConstructor(params);
                Object[] args = new Object[2];
                args[0] = message;
                args[1] = ex;
                return (RemoteException) cr.newInstance(args);
            } catch (Throwable e) {
                utilWrapper.classNotFound(
                              e, "javax.activity.ActivityRequiredException");
            }
        } else if (ex instanceof ACTIVITY_COMPLETED) {
            try {
                Class<?> cl = SharedSecrets.getJavaCorbaAccess().loadClass(
                               "javax.activity.ActivityCompletedException");
                Class[] params = new Class[2];
                params[0] = java.lang.String.class;
                params[1] = java.lang.Throwable.class;
                Constructor cr = cl.getConstructor(params);
                Object[] args = new Object[2];
                args[0] = message;
                args[1] = ex;
                return (RemoteException) cr.newInstance(args);
              } catch (Throwable e) {
                  utilWrapper.classNotFound(
                                e, "javax.activity.ActivityCompletedException");
              }
        } else if (ex instanceof INVALID_ACTIVITY) {
            try {
                Class<?> cl = SharedSecrets.getJavaCorbaAccess().loadClass(
                               "javax.activity.InvalidActivityException");
                Class[] params = new Class[2];
                params[0] = java.lang.String.class;
                params[1] = java.lang.Throwable.class;
                Constructor cr = cl.getConstructor(params);
                Object[] args = new Object[2];
                args[0] = message;
                args[1] = ex;
                return (RemoteException) cr.newInstance(args);
              } catch (Throwable e) {
                  utilWrapper.classNotFound(
                                e, "javax.activity.InvalidActivityException");
              }
        }

        // Just map to a generic RemoteException...
        return new RemoteException(message, ex);
    }

    /**
     * Writes any java.lang.Object as a CORBA any.
     * @param out the stream in which to write the any.
     * @param obj the object to write as an any.
     */
    public void writeAny( org.omg.CORBA.portable.OutputStream out,
                         java.lang.Object obj)
    {
        org.omg.CORBA.ORB orb = out.orb();

        // Create Any
        Any any = orb.create_any();

        // Make sure we have a connected object...
        java.lang.Object newObj = Utility.autoConnect(obj,orb,false);

        if (newObj instanceof org.omg.CORBA.Object) {
            any.insert_Object((org.omg.CORBA.Object)newObj);
        } else {
            if (newObj == null) {
                // Handle the null case, including backwards
                // compatibility issues
                any.insert_Value(null, createTypeCodeForNull(orb));
            } else {
                if (newObj instanceof Serializable) {
                    // If they're our Any and ORB implementations,
                    // we may want to do type code related versioning.
                    TypeCode tc = createTypeCode((Serializable)newObj, any, orb);
                    if (tc == null)
                        any.insert_Value((Serializable)newObj);
                    else
                        any.insert_Value((Serializable)newObj, tc);
                } else if (newObj instanceof Remote) {
                    ORBUtility.throwNotSerializableForCorba(newObj.getClass().getName());
                } else {
                    ORBUtility.throwNotSerializableForCorba(newObj.getClass().getName());
                }
            }
        }

        out.write_any(any);
    }

    /**
     * When using our own ORB and Any implementations, we need to get
     * the ORB version and create the type code appropriately.  This is
     * to overcome a bug in which the JDK 1.3.x ORBs used a tk_char
     * rather than a tk_wchar to describe a Java char field.
     *
     * This only works in RMI-IIOP with Util.writeAny since we actually
     * know what ORB and stream we're writing with when we insert
     * the value.
     *
     * Returns null if it wasn't possible to create the TypeCode (means
     * it wasn't our ORB or Any implementation).
     *
     * This does not handle null objs.
     */
    private TypeCode createTypeCode(Serializable obj,
                                    org.omg.CORBA.Any any,
                                    org.omg.CORBA.ORB orb) {

        if (any instanceof com.sun.corba.se.impl.corba.AnyImpl &&
            orb instanceof ORB) {

            com.sun.corba.se.impl.corba.AnyImpl anyImpl
                = (com.sun.corba.se.impl.corba.AnyImpl)any;

            ORB ourORB = (ORB)orb;

            return anyImpl.createTypeCodeForClass(obj.getClass(), ourORB);

        } else
            return null;
    }


    /**
     * This is used to create the TypeCode for a null reference.
     * It also handles backwards compatibility with JDK 1.3.x.
     *
     * This method will not return null.
     */
    private TypeCode createTypeCodeForNull(org.omg.CORBA.ORB orb)
    {
        if (orb instanceof ORB) {

            ORB ourORB = (ORB)orb;

            // Preserve backwards compatibility with Kestrel and Ladybird
            // by not fully implementing interop issue resolution 3857,
            // and returning a null TypeCode with a tk_value TCKind.
            // If we're not talking to Kestrel or Ladybird, fall through
            // to the abstract interface case (also used for foreign ORBs).
            if (!ORBVersionFactory.getFOREIGN().equals(ourORB.getORBVersion()) &&
                ORBVersionFactory.getNEWER().compareTo(ourORB.getORBVersion()) > 0) {

                return orb.get_primitive_tc(TCKind.tk_value);
            }
        }

        // Use tk_abstract_interface as detailed in the resolution

        // REVISIT: Define this in IDL and get the ID in generated code
        String abstractBaseID = "IDL:omg.org/CORBA/AbstractBase:1.0";

        return orb.create_abstract_interface_tc(abstractBaseID, "");
    }

    /**
     * Reads a java.lang.Object as a CORBA any.
     * @param in the stream from which to read the any.
     * @return the object read from the stream.
     */
    public Object readAny(InputStream in)
    {
        Any any = in.read_any();
        if ( any.type().kind().value() == TCKind._tk_objref )
            return any.extract_Object ();
        else
            return any.extract_Value();
    }

    /**
     * Writes a java.lang.Object as a CORBA Object. If {@code obj} is
     * an exported RMI-IIOP server object, the tie is found
     * and wired to {@code obj}, then written to {@code out.write_Object(org.omg.CORBA.Object)}.
     * If {@code obj} is a CORBA Object, it is written to
     * {@code out.write_Object(org.omg.CORBA.Object)}.
     * @param out the stream in which to write the object.
     * @param obj the object to write.
     */
    public void writeRemoteObject(OutputStream out, java.lang.Object obj)
    {
        // Make sure we have a connected object, then
        // write it out...

        Object newObj = Utility.autoConnect(obj,out.orb(),false);
        out.write_Object((org.omg.CORBA.Object)newObj);
    }

    /**
     * Writes a java.lang.Object as either a value or a CORBA Object.
     * If {@code obj} is a value object or a stub object, it is written to
     * {@code out.write_abstract_interface(java.lang.Object)}. If {@code obj} is an exported
     * RMI-IIOP server object, the tie is found and wired to {@code obj},
     * then written to {@code out.write_abstract_interface(java.lang.Object)}.
     * @param out the stream in which to write the object.
     * @param obj the object to write.
     */
    public void writeAbstractObject( OutputStream out, java.lang.Object obj )
    {
        // Make sure we have a connected object, then
        // write it out...

        Object newObj = Utility.autoConnect(obj,out.orb(),false);
        ((org.omg.CORBA_2_3.portable.OutputStream)out).write_abstract_interface(newObj);
    }

    /**
     * Registers a target for a tie. Adds the tie to an internal table and calls
     * {@link Tie#setTarget} on the tie object.
     * @param tie the tie to register.
     * @param target the target for the tie.
     */
    public void registerTarget(javax.rmi.CORBA.Tie tie, java.rmi.Remote target)
    {
        synchronized (exportedServants) {
            // Do we already have this target registered?
            if (lookupTie(target) == null) {
                // No, so register it and set the target...
                exportedServants.put(target,tie);
                tie.setTarget(target);

                // Do we need to instantiate our keep-alive thread?
                if (keepAlive == null) {
                    // Yes. Instantiate our keep-alive thread and start
                    // it up...
                    keepAlive = (KeepAlive)AccessController.doPrivileged(new PrivilegedAction() {
                        public java.lang.Object run() {
                            return new KeepAlive();
                        }
                    });
                    keepAlive.start();
                }
            }
        }
    }

    /**
     * Removes the associated tie from an internal table and calls {@link Tie#deactivate}
     * to deactivate the object.
     * @param target the object to unexport.
     */
    public void unexportObject(java.rmi.Remote target)
        throws java.rmi.NoSuchObjectException
    {
        synchronized (exportedServants) {
            Tie cachedTie = lookupTie(target);
            if (cachedTie != null) {
                exportedServants.remove(target);
                Utility.purgeStubForTie(cachedTie);
                Utility.purgeTieAndServant(cachedTie);
                try {
                    cleanUpTie(cachedTie);
                } catch (BAD_OPERATION e) {
                    // ignore
                } catch (org.omg.CORBA.OBJ_ADAPTER e) {
                    // This can happen when the target was never associated with a POA.
                    // We can safely ignore this case.
                }

                // Is it time to shut down our keep alive thread?
                if (exportedServants.isEmpty()) {
                    keepAlive.quit();
                    keepAlive = null;
                }
            } else {
                throw new java.rmi.NoSuchObjectException("Tie not found" );
            }
        }
    }

    protected void cleanUpTie(Tie cachedTie)
        throws java.rmi.NoSuchObjectException
    {
        cachedTie.setTarget(null);
        cachedTie.deactivate();
    }

    /**
     * Returns the tie (if any) for a given target object.
     * @return the tie or null if no tie is registered for the given target.
     */
    public Tie getTie (Remote target)
    {
        synchronized (exportedServants) {
            return lookupTie(target);
        }
    }

    /**
     * An unsynchronized version of getTie() for internal use.
     */
    private static Tie lookupTie (Remote target)
    {
        Tie result = (Tie)exportedServants.get(target);
        if (result == null && target instanceof Tie) {
            if (exportedServants.contains(target)) {
                result = (Tie)target;
            }
        }
        return result;
    }

    /**
     * Returns a singleton instance of a class that implements the
     * {@link ValueHandler} interface.
     * @return a class which implements the ValueHandler interface.
     */
    public ValueHandler createValueHandler()
    {
        return valueHandlerSingleton;
    }

    /**
     * Returns the codebase, if any, for the given class.
     * @param clz the class to get a codebase for.
     * @return a space-separated list of URLs, or null.
     */
    public String getCodebase(java.lang.Class clz) {
        return RMIClassLoader.getClassAnnotation(clz);
    }

    /**
     * Returns a class instance for the specified class.
     * @param className the name of the class.
     * @param remoteCodebase a space-separated list of URLs at which
     * the class might be found. May be null.
     * @param loader a class whose ClassLoader may be used to
     * load the class if all other methods fail.
     * @return the {@code Class} object representing the loaded class.
     * @exception ClassNotFoundException if class cannot be loaded.
     */
    public Class loadClass( String className, String remoteCodebase,
        ClassLoader loader) throws ClassNotFoundException
    {
        return JDKBridge.loadClass(className,remoteCodebase,loader);
    }

    /**
     * The {@code isLocal} method has the same semantics as the
     * ObjectImpl._is_local method, except that it can throw a RemoteException.
     * (no it doesn't but the spec says it should.)
     *
     * The {@code _is_local()} method is provided so that stubs may determine
     * if a particular object is implemented by a local servant and hence local
     * invocation APIs may be used.
     *
     * @param stub the stub to test.
     *
     * @return The {@code _is_local()} method returns true if
     * the servant incarnating the object is located in the same process as
     * the stub and they both share the same ORB instance.  The {@code _is_local()}
     * method returns false otherwise. The default behavior of {@code _is_local()} is
     * to return false.
     *
     * @throws RemoteException The Java to IDL specification does to
     * specify the conditions that cause a RemoteException to be thrown.
     */
    public boolean isLocal(javax.rmi.CORBA.Stub stub) throws RemoteException
    {
        boolean result = false ;

        try {
            org.omg.CORBA.portable.Delegate delegate = stub._get_delegate() ;
            if (delegate instanceof CorbaClientDelegate) {
                // For the Sun ORB
                CorbaClientDelegate cdel = (CorbaClientDelegate)delegate ;
                ContactInfoList cil = cdel.getContactInfoList() ;
                if (cil instanceof CorbaContactInfoList) {
                    CorbaContactInfoList ccil = (CorbaContactInfoList)cil ;
                    LocalClientRequestDispatcher lcs = ccil.getLocalClientRequestDispatcher() ;
                    result = lcs.useLocalInvocation( null ) ;
                }
            } else {
                // For a non-Sun ORB
                result = delegate.is_local( stub ) ;
            }
        } catch (SystemException e) {
            throw javax.rmi.CORBA.Util.mapSystemException(e);
        }

        return result ;
    }

    /**
     * Wraps an exception thrown by an implementation
     * method.  It returns the corresponding client-side exception.
     * @param orig the exception to wrap.
     * @return the wrapped exception.
     */
    public RemoteException wrapException(Throwable orig)
    {
        if (orig instanceof SystemException) {
            return mapSystemException((SystemException)orig);
        }

        if (orig instanceof Error) {
            return new ServerError("Error occurred in server thread",(Error)orig);
        } else if (orig instanceof RemoteException) {
            return new ServerException("RemoteException occurred in server thread",
                                       (Exception)orig);
        } else if (orig instanceof RuntimeException) {
            throw (RuntimeException) orig;
        }

        if (orig instanceof Exception)
            return new UnexpectedException( orig.toString(), (Exception)orig );
        else
            return new UnexpectedException( orig.toString());
    }

    /**
     * Copies or connects an array of objects. Used by local stubs
     * to copy any number of actual parameters, preserving sharing
     * across parameters as necessary to support RMI semantics.
     * @param obj the objects to copy or connect.
     * @param orb the ORB.
     * @return the copied or connected objects.
     * @exception RemoteException if any object could not be copied or connected.
     */
    public Object[] copyObjects (Object[] obj, org.omg.CORBA.ORB orb)
        throws RemoteException
    {
        if (obj == null)
            // Bug fix for 5018613: JCK test expects copyObjects to throw
            // NPE when obj==null.  This is actually not in the spec, since
            // obj is not really an RMI-IDL data type, but we follow our
            // test here, and force this error to be thrown.
            throw new NullPointerException() ;

        Class compType = obj.getClass().getComponentType() ;
        if (Remote.class.isAssignableFrom( compType ) && !compType.isInterface()) {
            // obj is an array of remote impl types.  This
            // causes problems with stream copier, so we copy
            // it over to an array of Remotes instead.
            Remote[] result = new Remote[obj.length] ;
            System.arraycopy( (Object)obj, 0, (Object)result, 0, obj.length ) ;
            return (Object[])copyObject( result, orb ) ;
        } else
            return (Object[])copyObject( obj, orb ) ;
    }

    /**
     * Copies or connects an object. Used by local stubs to copy
     * an actual parameter, result object, or exception.
     * @param obj the object to copy.
     * @param orb the ORB.
     * @return the copy or connected object.
     * @exception RemoteException if the object could not be copied or connected.
     */
    public Object copyObject (Object obj, org.omg.CORBA.ORB orb)
        throws RemoteException
    {
        if (orb instanceof ORB) {
            ORB lorb = (ORB)orb ;

            try {
                try {
                    // This gets the copier for the current invocation, which was
                    // previously set by preinvoke.
                    return lorb.peekInvocationInfo().getCopierFactory().make().copy( obj ) ;
                } catch (java.util.EmptyStackException exc) {
                    // copyObject was invoked outside of an invocation, probably by
                    // a test.  Get the default copier from the ORB.
                    // XXX should we just make the default copier available directly
                    // and avoid constructing one on each call?
                    CopierManager cm = lorb.getCopierManager() ;
                    ObjectCopier copier = cm.getDefaultObjectCopierFactory().make() ;
                    return copier.copy( obj ) ;
                }
            } catch (ReflectiveCopyException exc) {
                RemoteException rexc = new RemoteException() ;
                rexc.initCause( exc ) ;
                throw rexc ;
            }
        } else {
            org.omg.CORBA_2_3.portable.OutputStream out =
                (org.omg.CORBA_2_3.portable.OutputStream)orb.create_output_stream();
            out.write_value((Serializable)obj);
            org.omg.CORBA_2_3.portable.InputStream in =
                (org.omg.CORBA_2_3.portable.InputStream)out.create_input_stream();
            return in.read_value();
        }
    }
}

class KeepAlive extends Thread
{
    boolean quit = false;

    public KeepAlive ()
    {
        super(null, null, "Servant-KeepAlive-Thread", 0, false);
        setDaemon(false);
    }

    public synchronized void run ()
    {
        while (!quit) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    public synchronized void quit ()
    {
        quit = true;
        notifyAll();
    }
}
