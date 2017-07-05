/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
package org.omg.CORBA.portable;

import org.omg.CORBA.Request;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NVList;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.Context;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.SystemException;


/**
 *  The common base class for all stub classes; provides default implementations
 *  of the <code>org.omg.CORBA.Object</code> methods. All method implementations are
 *  forwarded to a <code>Delegate</code> object stored in the <code>ObjectImpl</code>
 *  instance.  <code>ObjectImpl</code> allows for portable stubs because the
 *  <code>Delegate</code> can be implemented by a different vendor-specific ORB.
 */

abstract public class ObjectImpl implements org.omg.CORBA.Object
{

    /**
     * The field that stores the <code>Delegate</code> instance for
     * this <code>ObjectImpl</code> object. This <code>Delegate</code>
     * instance can be implemented by a vendor-specific ORB.  Stub classes,
     * which are derived from this <code>ObjectImpl</code> class, can be
     * portable because they delegate all of the methods called on them to this
     * <code>Delegate</code> object.
     */
    private transient Delegate __delegate;


    /**
     * Retrieves the reference to the vendor-specific <code>Delegate</code>
     * object to which this <code>ObjectImpl</code> object delegates all
     * methods invoked on it.
     *
     * @return the Delegate contained in this ObjectImpl instance
     * @throws BAD_OPERATION if the delegate has not been set
     * @see #_set_delegate
     */
    public Delegate _get_delegate() {
        if (__delegate == null)
            throw new BAD_OPERATION("The delegate has not been set!");
        return __delegate;
    }


    /**
     * Sets the Delegate for this <code>ObjectImpl</code> instance to the given
     * <code>Delegate</code> object.  All method invocations on this
     * <code>ObjectImpl</code> object will be forwarded to this delegate.
     *
     * @param delegate the <code>Delegate</code> instance to which
     *        all method calls on this <code>ObjectImpl</code> object
     *        will be delegated; may be implemented by a third-party ORB
     * @see #_get_delegate
     */
    public void _set_delegate(Delegate delegate) {
        __delegate = delegate;
    }

    /**
     * Retrieves a string array containing the repository identifiers
     * supported by this <code>ObjectImpl</code> object.  For example,
     * for a stub, this method returns information about all the
     * interfaces supported by the stub.
     *
     * @return the array of all repository identifiers supported by this
     *         <code>ObjectImpl</code> instance
     */
    public abstract String[] _ids();


    /**
     * Returns a duplicate of this <code>ObjectImpl</code> object.
     *
     * @return an <code>orb.omg.CORBA.Object</code> object that is
     *         a duplicate of this object
     */
    public org.omg.CORBA.Object _duplicate() {
        return _get_delegate().duplicate(this);
    }

    /**
     * Releases the resources associated with this <code>ObjectImpl</code> object.
     */
    public void _release() {
        _get_delegate().release(this);
    }

    /**
     * Checks whether the object identified by the given repository
     * identifier is an <code>ObjectImpl</code> object.
     *
     * @param repository_id a <code>String</code> object with the repository
     *        identifier to check
     * @return <code>true</code> if the object identified by the given
     *         repository id is an instance of <code>ObjectImpl</code>;
     *         <code>false</code> otherwise
     */
    public boolean _is_a(String repository_id) {
        return _get_delegate().is_a(this, repository_id);
    }

    /**
     * Checks whether the the given <code>ObjectImpl</code> object is
     * equivalent to this <code>ObjectImpl</code> object.
     *
     * @param that an instance of <code>ObjectImpl</code> to compare with
     *        this <code>ObjectImpl</code> object
     * @return <code>true</code> if the given object is equivalent
     *         to this <code>ObjectImpl</code> object;
     *         <code>false</code> otherwise
     */
    public boolean _is_equivalent(org.omg.CORBA.Object that) {
        return _get_delegate().is_equivalent(this, that);
    }

    /**
     * Checks whether the server object for this <code>ObjectImpl</code>
     * object has been destroyed.
     *
     * @return <code>true</code> if the ORB knows authoritatively that the
     *         server object does not exist; <code>false</code> otherwise
     */
    public boolean _non_existent() {
        return _get_delegate().non_existent(this);
    }

    /**
     * Retrieves the hash code that serves as an ORB-internal identifier for
     * this <code>ObjectImpl</code> object.
     *
     * @param maximum an <code>int</code> indicating the upper bound on the hash
     *        value returned by the ORB
     * @return an <code>int</code> representing the hash code for this
     *         <code>ObjectImpl</code> object
     */
    public int _hash(int maximum) {
        return _get_delegate().hash(this, maximum);
    }

    /**
     * Creates a <code>Request</code> object containing the given method
     * that can be used with the Dynamic Invocation Interface.
     *
     * @param operation the method to be invoked by the new <code>Request</code>
     *        object
     * @return a new <code>Request</code> object initialized with the
     *         given method
     */
    public Request _request(String operation) {
        return _get_delegate().request(this, operation);
    }

    /**
     * Creates a <code>Request</code> object that contains the given context,
     * method, argument list, and container for the result.
     *
     * @param ctx the Context for the request
     * @param operation the method that the new <code>Request</code>
     *        object will invoke
     * @param arg_list the arguments for the method; an <code>NVList</code>
     *        in which each argument is a <code>NamedValue</code> object
     * @param result a <code>NamedValue</code> object to be used for
     *        returning the result of executing the request's method
     * @return a new <code>Request</code> object initialized with the
     *         given context, method, argument list, and container for the
     *         return value
     */
    public Request _create_request(Context ctx,
                                   String operation,
                                   NVList arg_list,
                                   NamedValue result) {
        return _get_delegate().create_request(this,
                                              ctx,
                                              operation,
                                              arg_list,
                                              result);
    }

    /**
     * Creates a <code>Request</code> object that contains the given context,
     * method, argument list, container for the result, exceptions, and
     * list of property names to be used in resolving the context strings.
     * This <code>Request</code> object is for use in the Dynamic
     * Invocation Interface.
     *
     * @param ctx the <code>Context</code> object that contains the
     *        context strings that must be resolved before they are
     *        sent along with the request
     * @param operation the method that the new <code>Request</code>
     *        object will invoke
     * @param arg_list the arguments for the method; an <code>NVList</code>
     *        in which each argument is a <code>NamedValue</code> object
     * @param result a <code>NamedValue</code> object to be used for
     *        returning the result of executing the request's method
     * @param exceptions a list of the exceptions that the given method
     *        throws
     * @param contexts a list of the properties that are needed to
     *        resolve the contexts in <i>ctx</i>; the strings in
     *        <i>contexts</i> are used as arguments to the method
     *        <code>Context.get_values</code>,
     *        which returns the value associated with the given property
     * @return a new <code>Request</code> object initialized with the
     *         given context strings to resolve, method, argument list,
     *         container for the result, exceptions, and list of property
     *         names to be used in resolving the context strings
     */
    public Request _create_request(Context ctx,
                                   String operation,
                                   NVList arg_list,
                                   NamedValue result,
                                   ExceptionList exceptions,
                                   ContextList contexts) {
        return _get_delegate().create_request(this,
                                              ctx,
                                              operation,
                                              arg_list,
                                              result,
                                              exceptions,
                                              contexts);
    }

    /**
     * Retrieves the interface definition for this <code>ObjectImpl</code>
     * object.
     *
     * @return the <code>org.omg.CORBA.Object</code> instance that is the
     *         interface definition for this <code>ObjectImpl</code> object
     */
    public org.omg.CORBA.Object _get_interface_def()
    {
        // First try to call the delegate implementation class's
        // "Object get_interface_def(..)" method (will work for JDK1.2 ORBs).
        // Else call the delegate implementation class's
        // "InterfaceDef get_interface(..)" method using reflection
        // (will work for pre-JDK1.2 ORBs).

        org.omg.CORBA.portable.Delegate delegate = _get_delegate();
        try {
            // If the ORB's delegate class does not implement
            // "Object get_interface_def(..)", this will call
            // get_interface_def(..) on portable.Delegate.
            return delegate.get_interface_def(this);
        }
        catch( org.omg.CORBA.NO_IMPLEMENT ex ) {
            // Call "InterfaceDef get_interface(..)" method using reflection.
            try {
                Class[] argc = { org.omg.CORBA.Object.class };
                java.lang.reflect.Method meth =
                    delegate.getClass().getMethod("get_interface", argc);
                Object[] argx = { this };
                return (org.omg.CORBA.Object)meth.invoke(delegate, argx);
            }
            catch( java.lang.reflect.InvocationTargetException exs ) {
                Throwable t = exs.getTargetException();
                if (t instanceof Error) {
                    throw (Error) t;
                }
                else if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                else {
                    throw new org.omg.CORBA.NO_IMPLEMENT();
                }
            } catch( RuntimeException rex ) {
                throw rex;
            } catch( Exception exr ) {
                throw new org.omg.CORBA.NO_IMPLEMENT();
            }
        }
    }

    /**
     * Returns a reference to the ORB associated with this object and
     * its delegate.  This is the <code>ORB</code> object that created
     * the delegate.
     *
     * @return the <code>ORB</code> instance that created the
     *          <code>Delegate</code> object contained in this
     *          <code>ObjectImpl</code> object
     */
    public org.omg.CORBA.ORB _orb() {
        return _get_delegate().orb(this);
    }


    /**
     * Retrieves the <code>Policy</code> object for this
     * <code>ObjectImpl</code> object that has the given
     * policy type.
     *
     * @param policy_type an int indicating the policy type
     * @return the <code>Policy</code> object that is the specified policy type
     *         and that applies to this <code>ObjectImpl</code> object
     * @see org.omg.CORBA.PolicyOperations#policy_type
     */
    public org.omg.CORBA.Policy _get_policy(int policy_type) {
        return _get_delegate().get_policy(this, policy_type);
    }

    /**
     * Retrieves a list of the domain managers for this
     * <code>ObjectImpl</code> object.
     *
     * @return an array containing the <code>DomainManager</code>
     *         objects for this instance of <code>ObjectImpl</code>
     */
    public org.omg.CORBA.DomainManager[] _get_domain_managers() {
        return _get_delegate().get_domain_managers(this);
    }

    /**
     * Sets this <code>ObjectImpl</code> object's override type for
     * the given policies to the given instance of
     * <code>SetOverrideType</code>.
     *
     * @param policies an array of <code>Policy</code> objects with the
     *         policies that will replace the current policies or be
     *         added to the current policies
     * @param set_add either <code>SetOverrideType.SET_OVERRIDE</code>,
     *         indicating that the given policies will replace any existing
     *         ones, or <code>SetOverrideType.ADD_OVERRIDE</code>, indicating
     *         that the given policies should be added to any existing ones
     * @return an <code>Object</code> with the given policies replacing or
     *         added to its previous policies
     */
    public org.omg.CORBA.Object
        _set_policy_override(org.omg.CORBA.Policy[] policies,
                             org.omg.CORBA.SetOverrideType set_add) {
        return _get_delegate().set_policy_override(this, policies,
                                                   set_add);
    }

    /**
     * Checks whether this <code>ObjectImpl</code> object is implemented
     * by a local servant.  If so, local invocation API's may be used.
     *
     * @return <code>true</code> if this object is implemented by a local
     *         servant; <code>false</code> otherwise
     */
    public boolean _is_local() {
        return _get_delegate().is_local(this);
    }

    /**
     * Returns a Java reference to the local servant that should be used for sending
     * a request for the method specified. If this <code>ObjectImpl</code>
     * object is a local stub, it will invoke the <code>_servant_preinvoke</code>
     * method before sending a request in order to obtain the
     * <code>ServantObject</code> instance to use.
     * <P>
     * If a <code>ServantObject</code> object is returned, its <code>servant</code>
     * field has been set to an object of the expected type (Note: the object may
     * or may not be the actual servant instance). The local stub may cast
     * the servant field to the expected type, and then invoke the operation
     * directly. The <code>ServantRequest</code> object is valid for only one
     * invocation and cannot be used for more than one invocation.
     *
     * @param operation a <code>String</code> containing the name of the method
     *        to be invoked. This name should correspond to the method name as
     *        it would be encoded in a GIOP request.
     *
     * @param expectedType a <code>Class</code> object representing the
     *        expected type of the servant that is returned. This expected
     *        type is the <code>Class</code> object associated with the
     *        operations class for the stub's interface. For example, a
     *        stub for an interface <code>Foo</code> would pass the
     *        <code>Class</code> object for the <code>FooOperations</code>
     *        interface.
     *
     * @return (1) a <code>ServantObject</code> object, which may or may
     *         not be the actual servant instance, or (2) <code>null</code> if
     *         (a) the servant is not local or (b) the servant has ceased to
     *         be local due to a ForwardRequest from a POA ServantManager
     * @throws org.omg.CORBA.BAD_PARAM if the servant is not the expected type
     */
    public ServantObject _servant_preinvoke(String operation,
                                            Class expectedType) {
        return _get_delegate().servant_preinvoke(this, operation,
                                                 expectedType);
    }

    /**
     * Is called by the local stub after it has invoked an operation
     * on the local servant that was previously retrieved from a
     * call to the method <code>_servant_preinvoke</code>.
     * The <code>_servant_postinvoke</code> method must be called
     * if the <code>_servant_preinvoke</code>
     * method returned a non-null value, even if an exception was thrown
     * by the method invoked by the servant. For this reason, the call
     * to the method <code>_servant_postinvoke</code> should be placed
     * in a Java <code>finally</code> clause.
     *
     * @param servant the instance of the <code>ServantObject</code>
     *        returned by the <code>_servant_preinvoke</code> method
     */
    public void _servant_postinvoke(ServantObject servant) {
        _get_delegate().servant_postinvoke(this, servant);
    }

    /*
     * The following methods were added by orbos/98-04-03: Java to IDL
     * Mapping. These are used by RMI over IIOP.
     */

    /**
     * Returns an <code>OutputStream</code> object to use for marshalling
     * the arguments of the given method.  This method is called by a stub,
     * which must indicate if a response is expected, that is, whether or not
     * the call is oneway.
     *
     * @param operation         a String giving the name of the method.
     * @param responseExpected  a boolean -- <code>true</code> if the
     *         request is not one way, that is, a response is expected
     * @return an <code>OutputStream</code> object for dispatching the request
     */
    public OutputStream _request(String operation,
                                 boolean responseExpected) {
        return _get_delegate().request(this, operation, responseExpected);
    }

    /**
     * Invokes an operation and returns an <code>InputStream</code>
     * object for reading the response. The stub provides the
     * <code>OutputStream</code> object that was previously returned by a
     * call to the <code>_request</code> method. The method specified
     * as an argument to <code>_request</code> when it was
     * called previously is the method that this method invokes.
     * <P>
     * If an exception occurs, the <code>_invoke</code> method may throw an
     * <code>ApplicationException</code> object that contains an InputStream from
     * which the user exception state may be unmarshalled.
     *
     * @param output  an OutputStream object for dispatching the request
     * @return an <code>InputStream</code> object containing the marshalled
     *         response to the method invoked
     * @throws ApplicationException if the invocation
     *         meets application-defined exception
     * @throws RemarshalException if the invocation leads
     *         to a remarshalling error
     * @see #_request
     */
    public InputStream _invoke(OutputStream output)
        throws ApplicationException, RemarshalException {
        return _get_delegate().invoke(this, output);
    }

    /**
     * Releases the given
     * reply stream back to the ORB when unmarshalling has
     * completed after a call to the method <code>_invoke</code>.
     * Calling this method is optional for the stub.
     *
     * @param input  the <code>InputStream</code> object that was returned
     *        by the <code>_invoke</code> method or the
     *        <code>ApplicationException.getInputStream</code> method;
     *        may be <code>null</code>, in which case this method does
     *        nothing
     * @see #_invoke
     */
    public void _releaseReply(InputStream input) {
        _get_delegate().releaseReply(this, input);
    }

    /**
     * Returns a <code>String</code> object that represents this
     * <code>ObjectImpl</code> object.
     *
     * @return the <code>String</code> representation of this object
     */
    public String toString() {
        if ( __delegate != null )
           return __delegate.toString(this);
        else
           return getClass().getName() + ": no delegate set";
    }

    /**
     * Returns the hash code for this <code>ObjectImpl</code> object.
     *
     * @return the hash code for this object
     */
    public int hashCode() {
        if ( __delegate != null )
           return __delegate.hashCode(this);
        else
            return super.hashCode();
    }

    /**
     * Compares this <code>ObjectImpl</code> object with the given one
     * for equality.
     *
     *@param obj the object with which to compare this object
     *@return <code>true</code> if the two objects are equal;
     *        <code>false</code> otherwise
     */
    public boolean equals(java.lang.Object obj) {
        if ( __delegate != null )
           return __delegate.equals(this, obj);
        else
           return (this==obj);
    }
}
