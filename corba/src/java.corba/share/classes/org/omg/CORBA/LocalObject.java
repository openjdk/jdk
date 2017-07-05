/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;
import org.omg.CORBA.portable.*;


/**
 * <P>Used as a base class for implementation of a local IDL interface in the
 * Java language mapping.  It is a class which implements all the operations
 * in the <tt>org.omg.CORBA.Object</tt> interface.
 * <P>Local interfaces are implemented by using CORBA::LocalObject
 *  to provide implementations of <code>Object</code> pseudo
 *  operations and any other ORB-specific support mechanisms that are
 *  appropriate for such objects.  Object implementation techniques are
 *  inherently language-mapping specific.  Therefore, the
 *  <code>LocalObject</code> type is not defined in IDL, but is specified
 *  in each language mapping.
 *  <P>Methods that do not apply to local objects throw
 *  an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with the message,
 *  "This is a locally contrained object."  Attempting to use a
 *  <TT>LocalObject</TT> to create a DII request results in NO_IMPLEMENT
 *  system exception.  Attempting to marshal or stringify a
 *  <TT>LocalObject</TT> results in a MARSHAL system exception.  Narrowing
 *  and widening references to <TT>LocalObjects</TT> must work as for regular
 *  object references.
 *  <P><code>LocalObject</code> is to be used as the base class of locally
 *  constrained objects, such as those in the PortableServer module.
 *  The specification here is based on the CORBA Components
 *  Volume I - orbos/99-07-01<P>
 * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
 */

public class LocalObject implements org.omg.CORBA.Object
{
    private static String reason = "This is a locally constrained object.";

    /**
     * Constructs a default <code>LocalObject</code> instance.
     */
    public LocalObject() {}

    /**
     * <P>Determines whether the two object references are equivalent,
     * so far as the ORB can easily determine. Two object references are equivalent
     * if they are identical. Two distinct object references which in fact refer to
     * the same object are also equivalent. However, ORBs are not required
     * to attempt determination of whether two distinct object references
     * refer to the same object, since such determination could be impractically
     * expensive.
     * <P>Default implementation of the org.omg.CORBA.Object method. <P>
     *
     * @param that the object reference with which to check for equivalence
     * @return <code>true</code> if this object reference is known to be
     *         equivalent to the given object reference.
     *         Note that <code>false</code> indicates only that the two
     *         object references are distinct, not necessarily that
     *         they reference distinct objects.
     */
    public boolean _is_equivalent(org.omg.CORBA.Object that) {
        return equals(that) ;
    }

    /**
     * Always returns <code>false</code>.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     *
     * @return <code>false</code>
     */
    public boolean _non_existent() {
        return false;
    }

    /**
     * Returns a hash value that is consistent for the
     * lifetime of the object, using the given number as the maximum.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @param maximum an <code>int</code> identifying maximum value of
     *                  the hashcode
     * @return this instance's hashcode
     */
    public int _hash(int maximum) {
        return hashCode() ;
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."  This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     *
     * @param repository_id a <code>String</code>
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public boolean _is_a(String repository_id) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @return a duplicate of this <code>LocalObject</code> instance.
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.Object _duplicate() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public void _release() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     *
     * @param operation a <code>String</code> giving the name of an operation
     *        to be performed by the request that is returned
     * @return a <code>Request</code> object with the given operation
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public Request _request(String operation) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     *
     * @param ctx          a <code>Context</code> object containing
     *                     a list of properties
     * @param operation    the <code>String</code> representing the name of the
     *                     method to be invoked
     * @param arg_list     an <code>NVList</code> containing the actual arguments
     *                     to the method being invoked
     * @param result       a <code>NamedValue</code> object to serve as a
     *                     container for the method's return value
     * @return a new <code>Request</code> object initialized with the given
     * arguments
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public Request _create_request(Context ctx,
                                   String operation,
                                   NVList arg_list,
                                   NamedValue result) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     *
     * @param ctx          a <code>Context</code> object containing
     *                     a list of properties
     * @param operation    the name of the method to be invoked
     * @param arg_list     an <code>NVList</code> containing the actual arguments
     *                     to the method being invoked
     * @param result       a <code>NamedValue</code> object to serve as a
     *                     container for the method's return value
     * @param exceptions   an <code>ExceptionList</code> object containing a
     *                     list of possible exceptions the method can throw
     * @param contexts     a <code>ContextList</code> object containing a list of
     *                     context strings that need to be resolved and sent
     *                     with the
     *                     <code>Request</code> instance
     * @return the new <code>Request</code> object initialized with the given
     * arguments
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public Request _create_request(Context ctx,
                                   String operation,
                                   NVList arg_list,
                                   NamedValue result,
                                   ExceptionList exceptions,
                                   ContextList contexts) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object." This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.Object _get_interface()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.Object _get_interface_def()
    {
        // First try to call the delegate implementation class's
        // "Object get_interface_def(..)" method (will work for JDK1.2
        // ORBs).
        // Else call the delegate implementation class's
        // "InterfaceDef get_interface(..)" method using reflection
        // (will work for pre-JDK1.2 ORBs).

        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @return the ORB instance that created the Delegate contained in this
     * <code>ObjectImpl</code>
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.ORB _orb() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object." This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @param policy_type  an <code>int</code>
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.Policy _get_policy(int policy_type) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }


    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object." This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.DomainManager[] _get_domain_managers() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object." This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.
     *
     * @param policies an array
     * @param set_add a flag
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public org.omg.CORBA.Object
        _set_policy_override(org.omg.CORBA.Policy[] policies,
                             org.omg.CORBA.SetOverrideType set_add) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }


    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * Returns <code>true</code> for this <code>LocalObject</code> instance.<P>
     * @return <code>true</code> always
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public boolean _is_local() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @param operation a <code>String</code> indicating which operation
     *                  to preinvoke
     * @param expectedType the class of the type of operation mentioned above
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local object
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public ServantObject _servant_preinvoke(String operation,
                                            Class expectedType) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @param servant the servant object on which to post-invoke
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public void _servant_postinvoke(ServantObject servant) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /*
     * The following methods were added by orbos/98-04-03: Java to IDL
     * Mapping. These are used by RMI over IIOP.
     */

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.
     * <P>Called by a stub to obtain an OutputStream for
     * marshaling arguments. The stub must supply the operation name,
     * and indicate if a response is expected (i.e is this a oneway
     * call).<P>
     * @param operation the name of the operation being requested
     * @param responseExpected <code>true</code> if a response is expected,
     *                         <code>false</code> if it is a one-way call
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public OutputStream _request(String operation,
                                 boolean responseExpected) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.
     * <P>Called to invoke an operation. The stub provides an
     * <code>OutputStream</code> that was previously returned by a
     * <code>_request()</code>
     * call. <code>_invoke</code> returns an <code>InputStream</code> which
     * contains the
     * marshaled reply. If an exception occurs, <code>_invoke</code> may throw an
     * <code>ApplicationException</code> object which contains an
     * <code>InputStream</code> from
     * which the user exception state may be unmarshaled.<P>
     * @param output the <code>OutputStream</code> to invoke
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @throws ApplicationException If an exception occurs,
     * <code>_invoke</code> may throw an
     * <code>ApplicationException</code> object which contains
     * an <code>InputStream</code> from
     * which the user exception state may be unmarshaled.
     * @throws RemarshalException If an exception occurs,
     * <code>_invoke</code> may throw an
     * <code>ApplicationException</code> object which contains
     * an <code>InputStream</code> from
     * which the user exception state may be unmarshaled.
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public InputStream _invoke(OutputStream output)
        throws ApplicationException, RemarshalException
    {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object."
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.
     * <P>May optionally be called by a stub to release a
     * reply stream back to the ORB when the unmarshaling has
     * completed. The stub passes the <code>InputStream</code> returned by
     * <code>_invoke()</code> or
     * <code>ApplicationException.getInputStream()</code>.
     * A null
     * value may also be passed to <code>_releaseReply</code>, in which case the
     * method is a no-op.<P>
     * @param input the reply stream back to the ORB or null
     * @exception NO_IMPLEMENT
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public void _releaseReply(InputStream input) {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception with
     * the message "This is a locally constrained object." This method
     * does not apply to local objects and is therefore not implemented.
     * This method is the default implementation of the
     * <code>org.omg.CORBA.Object</code> method.<P>
     * @return NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @exception NO_IMPLEMENT because this is a locally constrained object
     *      and this method does not apply to local objects
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */

    public boolean validate_connection() {
        throw new org.omg.CORBA.NO_IMPLEMENT(reason);
    }
}
