/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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
package org.omg.PortableServer;

import org.omg.CORBA.ORB;
import org.omg.PortableServer.portable.Delegate;

/**
 * Defines the native <code>Servant</code> type. In Java, the
 * <code>Servant</code> type is mapped to the Java
 * <code>org.omg.PortableServer.Servant</code> class.
 * It serves as the base class for all POA servant
 * implementations and provides a number of methods that may
 * be invoked by the application programmer, as well as methods
 * which are invoked by the POA itself and may be overridden by
 * the user to control aspects of servant behavior.
 * Based on IDL to Java spec. (CORBA V2.3.1) ptc/00-01-08.pdf.
 */

abstract public class Servant {

    private transient Delegate _delegate = null;
    /**
     * Gets the ORB vendor-specific implementation of
     * <code>PortableServer::Servant</code>.
     * @return <code>_delegate</code> the ORB vendor-specific
     * implementation of <code>PortableServer::Servant</code>.
     */
    final public Delegate _get_delegate() {
        if (_delegate == null) {
            throw
                new
                org.omg.CORBA.BAD_INV_ORDER
                ("The Servant has not been associated with an ORB instance");
        }
        return _delegate;
    }

    /**
     * Supports the Java ORB portability
     * interfaces by providing a method for classes that support
     * ORB portability through delegation to set their delegate.
     * @param delegate ORB vendor-specific implementation of
     *                 the <code>PortableServer::Servant</code>.
     */
    final public void _set_delegate(Delegate delegate) {
        _delegate = delegate;
    }

    /**
     * Allows the servant to obtain the object reference for
     * the target CORBA object it is incarnating for that request.
     * @return <code>this_object</code> <code>Object</code> reference
     * associated with the request.
     */
    final public org.omg.CORBA.Object _this_object() {
        return _get_delegate().this_object(this);
    }

    /**
     * Allows the servant to obtain the object reference for
     * the target CORBA Object it is incarnating for that request.
     * @param orb ORB with which the servant is associated.
     * @return <code>_this_object</code> reference associated with the request.
     */
    final public org.omg.CORBA.Object _this_object(ORB orb) {
        try {
            ((org.omg.CORBA_2_3.ORB)orb).set_delegate(this);
        }
        catch(ClassCastException e) {
            throw
                new
                org.omg.CORBA.BAD_PARAM
                ("POA Servant requires an instance of org.omg.CORBA_2_3.ORB");
        }
        return _this_object();
    }

    /**
     * Returns the instance of the ORB
     * currently associated with the <code>Servant</code> (convenience method).
     * @return <code>orb</code> the instance of the ORB currently
     * associated with the <code>Servant</code>.
     */
    final public ORB _orb() {
        return _get_delegate().orb(this);
    }

    /**
     * Allows easy execution of common methods, equivalent to
     * <code>PortableServer::Current:get_POA</code>.
     * @return <code>poa</code> POA associated with the servant.
     */
    final public POA _poa() {
        return _get_delegate().poa(this);
    }

    /**
     * Allows easy execution of
     * common methods, equivalent
     * to calling <code>PortableServer::Current::get_object_id</code>.
     * @return <code>object_id</code> the <code>Object</code> ID associated
     * with this servant.
     */
    final public byte[] _object_id() {
        return _get_delegate().object_id(this);
    }

    /**
     * Returns the
     * root POA from the ORB instance associated with the servant.
     * Subclasses may override this method to return a different POA.
     * @return <code>default_POA</code> the POA associated with the
     * <code>Servant</code>.
     */
    public POA _default_POA() {
        return _get_delegate().default_POA(this);
    }

    /**
     * Checks to see if the specified <code>repository_id</code> is present
     * on the list returned by <code>_all_interfaces()</code> or is the
     * <code>repository_id</code> for the generic CORBA Object.
     * @param repository_id the <code>repository_id</code>
     *          to be checked in the repository list or against the id
     *          of generic CORBA objects.
     * @return <code>is_a</code> boolean indicating whether the specified
     *          <code>repository_id</code> is
     *         in the repository list or is same as a generic CORBA
     *         object.
     */
    public boolean _is_a(String repository_id) {
        return _get_delegate().is_a(this, repository_id);
    }

    /**
     * Checks for the existence of an
     * <code>Object</code>.
     * The <code>Servant</code> provides a default implementation of
     * <code>_non_existent()</code> that can be overridden by derived servants.
     * @return <code>non_existent</code> <code>true</code> if that object does
     *           not exist,  <code>false</code> otherwise.
     */
    public boolean _non_existent() {
        return _get_delegate().non_existent(this);
    }

    // Ken and Simon will ask about editorial changes
    // needed in IDL to Java mapping to the following
    // signature.
    /**
     * Returns an object in the Interface Repository
     * which provides type information that may be useful to a program.
     * <code>Servant</code> provides a default implementation of
     * <code>_get_interface()</code>
     * that can be overridden by derived servants if the default
     * behavior is not adequate.
     * @return <code>get_interface</code> type information that corresponds to this servant.
     */
    /*
    public org.omg.CORBA.Object _get_interface() {
        return _get_delegate().get_interface(this);
    }
    */

    // _get_interface_def() replaces the _get_interface() method

    /**
     * Returns an <code>InterfaceDef</code> object as a
     * <code>CORBA::Object</code> that defines the runtime type of the
     * <code>CORBA::Object</code> implemented by the <code>Servant</code>.
     * The invoker of <code>_get_interface_def</code>
     * must narrow the result to an <code>InterfaceDef</code> in order
     * to use it.
     * <P>This default implementation of <code>_get_interface_def()</code>
     * can be overridden
     * by derived servants if the default behavior is not adequate.
     * As defined in the CORBA 2.3.1 specification, section 11.3.1, the
     * default behavior of <code>_get_interface_def()</code> is to use
     * the most derived
     * interface of a static servant or the most derived interface retrieved
     * from a dynamic servant to obtain the <code>InterfaceDef</code>.
     * This behavior must
     * be supported by the <code>Delegate</code> that implements the
     * <code>Servant</code>.
     * @return <code>get_interface_def</code> an <code>InterfaceDef</code>
     * object as a
     * <code>CORBA::Object</code> that defines the runtime type of the
     * <code>CORBA::Object</code> implemented by the <code>Servant</code>.
     */
    public org.omg.CORBA.Object _get_interface_def()
    {
        // First try to call the delegate implementation class's
        // "Object get_interface_def(..)" method (will work for ORBs
        // whose delegates implement this method).
        // Else call the delegate implementation class's
        // "InterfaceDef get_interface(..)" method using reflection
        // (will work for ORBs that were built using an older version
        // of the Delegate interface with a get_interface method
        // but not a get_interface_def method).

        org.omg.PortableServer.portable.Delegate delegate = _get_delegate();
        try {
            // If the ORB's delegate class does not implement
            // "Object get_interface_def(..)", this will throw
            // an AbstractMethodError.
            return delegate.get_interface_def(this);
        } catch( AbstractMethodError aex ) {
            // Call "InterfaceDef get_interface(..)" method using reflection.
            try {
                Class[] argc = { org.omg.PortableServer.Servant.class };
                java.lang.reflect.Method meth =
                     delegate.getClass().getMethod("get_interface", argc);
                Object[] argx = { this };
                return (org.omg.CORBA.Object)meth.invoke(delegate, argx);
            } catch( java.lang.reflect.InvocationTargetException exs ) {
                Throwable t = exs.getTargetException();
                if (t instanceof Error) {
                    throw (Error) t;
                } else if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new org.omg.CORBA.NO_IMPLEMENT();
                }
            } catch( RuntimeException rex ) {
                throw rex;
            } catch( Exception exr ) {
                throw new org.omg.CORBA.NO_IMPLEMENT();
            }
        }
    }

    // methods for which the user must provide an
    // implementation
    /**
     * Used by the ORB to obtain complete type
     * information from the servant.
     * @param poa POA with which the servant is associated.
     * @param objectId is the id corresponding to the object
     *         associated with this servant.
     * @return list of type information for the object.
     */
    abstract public String[] _all_interfaces( POA poa, byte[] objectId);
}
