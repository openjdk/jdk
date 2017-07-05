/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.rmi.CORBA;

import java.rmi.RemoteException;

import org.omg.CORBA.ORB;
import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.Any;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.ObjectImpl;

import javax.rmi.CORBA.Tie;
import java.rmi.Remote;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException ;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.rmi.server.RMIClassLoader;

import com.sun.corba.se.impl.orbutil.GetPropertyAction;

/**
 * Provides utility methods that can be used by stubs and ties to
 * perform common operations.
 */
public class Util {

    // This can only be set at static initialization time (no sync necessary).
    private static final javax.rmi.CORBA.UtilDelegate utilDelegate;
    private static final String UtilClassKey = "javax.rmi.CORBA.UtilClass";

    static {
        utilDelegate = (javax.rmi.CORBA.UtilDelegate)createDelegate(UtilClassKey);
    }

    private Util(){}

    /**
     * Maps a SystemException to a RemoteException.
     * @param ex the SystemException to map.
     * @return the mapped exception.
     */
    public static RemoteException mapSystemException(SystemException ex) {

        if (utilDelegate != null) {
            return utilDelegate.mapSystemException(ex);
        }
        return null;
    }

    /**
     * Writes any java.lang.Object as a CORBA any.
     * @param out the stream in which to write the any.
     * @param obj the object to write as an any.
     */
    public static void writeAny(OutputStream out, Object obj) {

        if (utilDelegate != null) {
            utilDelegate.writeAny(out, obj);
        }
    }

    /**
     * Reads a java.lang.Object as a CORBA any.
     * @param in the stream from which to read the any.
     * @return the object read from the stream.
     */
    public static Object readAny(InputStream in) {

        if (utilDelegate != null) {
            return utilDelegate.readAny(in);
        }
        return null;
    }

    /**
     * Writes a java.lang.Object as a CORBA Object. If {@code obj} is
     * an exported RMI-IIOP server object, the tie is found
     * and wired to {@code obj}, then written to
     * {@code out.write_Object(org.omg.CORBA.Object)}.
     * If {@code obj} is a CORBA Object, it is written to
     * {@code out.write_Object(org.omg.CORBA.Object)}.
     * @param out the stream in which to write the object.
     * @param obj the object to write.
     */
    public static void writeRemoteObject(OutputStream out,
                                         java.lang.Object obj) {

        if (utilDelegate != null) {
            utilDelegate.writeRemoteObject(out, obj);
        }

    }

    /**
     * Writes a java.lang.Object as either a value or a CORBA Object.
     * If {@code obj} is a value object or a stub object, it is written to
     * {@code out.write_abstract_interface(java.lang.Object)}.
     * If {@code obj} is an exported
     * RMI-IIOP server object, the tie is found and wired to {@code obj},
     * then written to {@code out.write_abstract_interface(java.lang.Object)}.
     * @param out the stream in which to write the object.
     * @param obj the object to write.
     */
    public static void writeAbstractObject(OutputStream out,
                                           java.lang.Object obj) {

        if (utilDelegate != null) {
            utilDelegate.writeAbstractObject(out, obj);
        }
    }

    /**
     * Registers a target for a tie. Adds the tie to an internal table and calls
     * {@link Tie#setTarget} on the tie object.
     * @param tie the tie to register.
     * @param target the target for the tie.
     */
    public static void registerTarget(javax.rmi.CORBA.Tie tie,
                                      java.rmi.Remote target) {

        if (utilDelegate != null) {
            utilDelegate.registerTarget(tie, target);
        }

    }

    /**
     * Removes the associated tie from an internal table
     * and calls {@link Tie#deactivate}
     * to deactivate the object.
     * @param target the object to unexport.
     */
    public static void unexportObject(java.rmi.Remote target)
        throws java.rmi.NoSuchObjectException
    {

        if (utilDelegate != null) {
            utilDelegate.unexportObject(target);
        }

    }

    /**
     * Returns the tie (if any) for a given target object.
     * @return the tie or null if no tie is registered for the given target.
     */
    public static Tie getTie (Remote target) {

        if (utilDelegate != null) {
            return utilDelegate.getTie(target);
        }
        return null;
    }


    /**
     * Returns a singleton instance of a class that implements the
     * {@link ValueHandler} interface.
     * @return a class which implements the ValueHandler interface.
     */
    public static ValueHandler createValueHandler() {

        if (utilDelegate != null) {
            return utilDelegate.createValueHandler();
        }
        return null;
    }

    /**
     * Returns the codebase, if any, for the given class.
     * @param clz the class to get a codebase for.
     * @return a space-separated list of URLs, or null.
     */
    public static String getCodebase(java.lang.Class clz) {
        if (utilDelegate != null) {
            return utilDelegate.getCodebase(clz);
        }
        return null;
    }

    /**
     * Returns a class instance for the specified class.
     * <P>The spec for this method is the "Java to IDL language
     * mapping", ptc/00-01-06.
     * <P>In Java SE Platform, this method works as follows:
     * <UL><LI>Find the first non-null {@code ClassLoader} on the
     * call stack and attempt to load the class using this
     * {@code ClassLoader}.
     * <LI>If the first step fails, and if {@code remoteCodebase}
     * is non-null and
     * {@code useCodebaseOnly} is false, then call
     * {@code java.rmi.server.RMIClassLoader.loadClass(remoteCodebase, className)}.
     * <LI>If {@code remoteCodebase} is null or {@code useCodebaseOnly}
     * is true, then call {@code java.rmi.server.RMIClassLoader.loadClass(className)}.
     * <LI>If a class was not successfully loaded by step 1, 2, or 3,
     * and {@code loader} is non-null, then call {@code loader.loadClass(className)}.
     * <LI>If a class was successfully loaded by step 1, 2, 3, or 4, then
     *  return the loaded class, else throw {@code ClassNotFoundException}.
     * </UL>
     *
     * @param className the name of the class.
     * @param remoteCodebase a space-separated list of URLs at which
     * the class might be found. May be null.
     * @param loader a {@code ClassLoader} that may be used to
     * load the class if all other methods fail.
     * @return the {@code Class} object representing the loaded class.
     * @exception ClassNotFoundException if class cannot be loaded.
     */
    public static Class loadClass(String className,
                                  String remoteCodebase,
                                  ClassLoader loader)
        throws ClassNotFoundException {
        if (utilDelegate != null) {
            return utilDelegate.loadClass(className,remoteCodebase,loader);
        }
        return null ;
    }


    /**
     * The {@code isLocal} method has the same semantics as the
     * {@code ObjectImpl._is_local}
     * method, except that it can throw a {@code RemoteException}.
     *
     * The {@code _is_local()} method is provided so that stubs may determine if a
     * particular object is implemented by a local servant and hence local
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
     * @throws RemoteException The Java to IDL specification does not
     * specify the conditions that cause a {@code RemoteException} to be thrown.
     */
    public static boolean isLocal(Stub stub) throws RemoteException {

        if (utilDelegate != null) {
            return utilDelegate.isLocal(stub);
        }

        return false;
    }

    /**
     * Wraps an exception thrown by an implementation
     * method.  It returns the corresponding client-side exception.
     * @param orig the exception to wrap.
     * @return the wrapped exception.
     */
    public static RemoteException wrapException(Throwable orig) {

        if (utilDelegate != null) {
            return utilDelegate.wrapException(orig);
        }

        return null;
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
    public static Object[] copyObjects (Object[] obj, ORB orb)
        throws RemoteException {

        if (utilDelegate != null) {
            return utilDelegate.copyObjects(obj, orb);
        }

        return null;
    }

    /**
     * Copies or connects an object. Used by local stubs to copy
     * an actual parameter, result object, or exception.
     * @param obj the object to copy.
     * @param orb the ORB.
     * @return the copy or connected object.
     * @exception RemoteException if the object could not be copied or connected.
     */
    public static Object copyObject (Object obj, ORB orb)
        throws RemoteException {

        if (utilDelegate != null) {
            return utilDelegate.copyObject(obj, orb);
        }
        return null;
    }

    // Same code as in PortableRemoteObject. Can not be shared because they
    // are in different packages and the visibility needs to be package for
    // security reasons. If you know a better solution how to share this code
    // then remove it from PortableRemoteObject. Also in Stub.java
    private static Object createDelegate(String classKey) {
        String className = (String)
            AccessController.doPrivileged(new GetPropertyAction(classKey));
        if (className == null) {
            Properties props = getORBPropertiesFile();
            if (props != null) {
                className = props.getProperty(classKey);
            }
        }

        if (className == null) {
            return new com.sun.corba.se.impl.javax.rmi.CORBA.Util();
        }

        try {
            return loadDelegateClass(className).newInstance();
        } catch (ClassNotFoundException ex) {
            INITIALIZE exc = new INITIALIZE( "Cannot instantiate " + className);
            exc.initCause( ex ) ;
            throw exc ;
        } catch (Exception ex) {
            INITIALIZE exc = new INITIALIZE( "Error while instantiating" + className);
            exc.initCause( ex ) ;
            throw exc ;
        }
    }

    private static Class loadDelegateClass( String className )  throws ClassNotFoundException
    {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            // ignore, then try RMIClassLoader
        }

        try {
            return RMIClassLoader.loadClass(className);
        } catch (MalformedURLException e) {
            String msg = "Could not load " + className + ": " + e.toString();
            ClassNotFoundException exc = new ClassNotFoundException( msg ) ;
            throw exc ;
        }
    }
    /**
     * Load the orb.properties file.
     */
    private static Properties getORBPropertiesFile ()
    {
        return (Properties) AccessController.doPrivileged(
            new GetORBPropertiesFileAction());
    }

}
