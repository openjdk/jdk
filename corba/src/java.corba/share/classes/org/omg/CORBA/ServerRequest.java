/*
 * Copyright (c) 1996, 2004, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An object that captures the explicit state of a request
 * for the Dynamic Skeleton Interface (DSI).  This class, the
 * cornerstone of the DSI, is analogous to the <code>Request</code>
 * object in the DII.
 * <P>
 * The ORB is responsible for creating this embodiment of a request,
 * and delivering it to a Dynamic Implementation Routine (DIR).
 * A dynamic servant (a DIR) is created by implementing the
 * <code>DynamicImplementation</code> class,
 * which has a single <code>invoke</code> method.  This method accepts a
 * <code>ServerRequest</code> object.
 *
 * The abstract class <code>ServerRequest</code> defines
 * methods for accessing the
 * method name, the arguments and the context of the request, as
 * well as methods for setting the result of the request either as a
 * return value or an exception. <p>
 *
 * A subtlety with accessing the arguments of the request is that the
 * DIR needs to provide type information about the
 * expected arguments, since there is no compiled information about
 * these. This information is provided through an <code>NVList</code>,
 * which is a list of <code>NamedValue</code> objects.
 * Each <code>NamedValue</code> object
 * contains an <code>Any</code> object, which in turn
 * has a <code>TypeCode</code> object representing the type
 * of the argument. <p>
 *
 * Similarly, type information needs to be provided for the response,
 * for either the expected result or for an exception, so the methods
 * <code>result</code> and <code>except</code> take an <code>Any</code>
 * object as a parameter. <p>
 *
 * @see org.omg.CORBA.DynamicImplementation
 * @see org.omg.CORBA.NVList
 * @see org.omg.CORBA.NamedValue
 *
 */

public abstract class ServerRequest {

    /**
     * Retrieves the name of the operation being
     * invoked. According to OMG IDL's rules, these names must be unique
     * among all operations supported by this object's "most-derived"
     * interface. Note that the operation names for getting and setting
     * attributes are <code>_get_&lt;attribute_name&gt;</code>
     * and <code>_set_&lt;attribute_name&gt;</code>,
     * respectively.
     *
     * @return     the name of the operation to be invoked
     * @deprecated use operation()
     */
    @Deprecated
    public String op_name()
    {
        return operation();
    }


    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception.
     * <P>
     * Retrieves the name of the operation being
     * invoked. According to OMG IDL's rules, these names must be unique
     * among all operations supported by this object's "most-derived"
     * interface. Note that the operation names for getting and setting
     * attributes are <code>_get_&lt;attribute_name&gt;</code>
     * and <code>_set_&lt;attribute_name&gt;</code>,
     * respectively.
     *
     * @return     the name of the operation to be invoked
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code>
     *      package comments for unimplemented features</a>
     */
    public String operation()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    /**
     * Specifies method parameter types and retrieves "in" and "inout"
     * argument values.
     * <P>
     * Note that this method is deprecated; use the method
     * <code>arguments</code> in its place.
     * <P>
     * Unless it calls the method <code>set_exception</code>,
     * the DIR must call this method exactly once, even if the
     * method signature contains no parameters. Once the method <code>
     * arguments</code> or <code>set_exception</code>
     * has been called, calling <code>arguments</code> on the same
     * <code>ServerRequest</code> object
     * will result in a <code>BAD_INV_ORDER</code> system exception.
     * The DIR must pass in to the method <code>arguments</code>
     * an NVList initialized with TypeCodes and Flags
     * describing the parameter types for the operation, in the order in which
     * they appear in the IDL specification (left to right). A
     * potentially-different NVList will be returned from
     * <code>arguments</code>, with the
     * "in" and "inout" argument values supplied. If it does not call
     * the method <code>set_exception</code>,
     * the DIR must supply the returned NVList with return
     * values for any "out" arguments before returning, and may also change
     * the return values for any "inout" arguments.
     *
     * @param params            the arguments of the method, in the
     *                          form of an <code>NVList</code> object
     * @deprecated use the method <code>arguments</code>
     */
    @Deprecated
    public void params(NVList params)
    {
        arguments(params);
    }

    /**
     * Specifies method parameter types and retrieves "in" and "inout"
     * argument values.
     * Unless it calls the method <code>set_exception</code>,
     * the DIR must call this method exactly once, even if the
     * method signature contains no parameters. Once the method <code>
     * arguments</code> or <code>set_exception</code>
     * has been called, calling <code>arguments</code> on the same
     * <code>ServerRequest</code> object
     * will result in a <code>BAD_INV_ORDER</code> system exception.
     * The DIR must pass in to the method <code>arguments</code>
     * an NVList initialized with TypeCodes and Flags
     * describing the parameter types for the operation, in the order in which
     * they appear in the IDL specification (left to right). A
     * potentially-different NVList will be returned from
     * <code>arguments</code>, with the
     * "in" and "inout" argument values supplied. If it does not call
     * the method <code>set_exception</code>,
     * the DIR must supply the returned NVList with return
     * values for any "out" arguments before returning, and it may also change
     * the return values for any "inout" arguments.
     *
     * @param args              the arguments of the method, in the
     *                            form of an NVList
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code>
     *      package comments for unimplemented features</a>
     */
    public void arguments(org.omg.CORBA.NVList args) {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }



    /**
     * Specifies any return value for the call.
     * <P>
     * Note that this method is deprecated; use the method
     * <code>set_result</code> in its place.
     * <P>
     * Unless the method
     * <code>set_exception</code> is called, if the invoked method
     * has a non-void result type, the method <code>set_result</code>
     * must be called exactly once before the DIR returns.
     * If the operation has a void result type, the method
     * <code>set_result</code> may optionally be
     * called once with an <code>Any</code> object whose type is
     * <code>tk_void</code>. Calling the method <code>set_result</code> before
     * the method <code>arguments</code> has been called or after
     * the method <code>set_result</code> or <code>set_exception</code> has been
     * called will result in a BAD_INV_ORDER exception. Calling the method
     * <code>set_result</code> without having previously called
     * the method <code>ctx</code> when the IDL operation contains a
     * context expression, or when the NVList passed to arguments did not
     * describe all parameters passed by the client, may result in a MARSHAL
     * system exception.
     *
     * @param any an <code>Any</code> object containing the return value to be set
     * @deprecated use the method <code>set_result</code>
     */
    @Deprecated
    public void result(Any any)
    {
        set_result(any);
    }


    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception.
     * <P>
     * Specifies any return value for the call. Unless the method
     * <code>set_exception</code> is called, if the invoked method
     * has a non-void result type, the method <code>set_result</code>
     * must be called exactly once before the DIR returns.
     * If the operation has a void result type, the method
     * <code>set_result</code> may optionally be
     * called once with an <code>Any</code> object whose type is
     * <code>tk_void</code>. Calling the method <code>set_result</code> before
     * the method <code>arguments</code> has been called or after
     * the method <code>set_result</code> or <code>set_exception</code> has been
     * called will result in a BAD_INV_ORDER exception. Calling the method
     * <code>set_result</code> without having previously called
     * the method <code>ctx</code> when the IDL operation contains a
     * context expression, or when the NVList passed to arguments did not
     * describe all parameters passed by the client, may result in a MARSHAL
     * system exception.
     *
     * @param any an <code>Any</code> object containing the return value to be set
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code>
     *      package comments for unimplemented features</a>
     */
    public void set_result(org.omg.CORBA.Any any)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    /**
     * The DIR may call set_exception at any time to return an exception to the
     * client. The Any passed to set_exception must contain either a system
     * exception or a user exception specified in the raises expression
     * of the invoked operation's IDL definition. Passing in an Any that does
     * not
     * contain an exception will result in a BAD_PARAM system exception. Passing
     * in an unlisted user exception will result in either the DIR receiving a
     * BAD_PARAM system exception or in the client receiving an
     * UNKNOWN_EXCEPTION system exception.
     *
     * @param any       the <code>Any</code> object containing the exception
     * @deprecated use set_exception()
     */
    @Deprecated
    public void except(Any any)
    {
        set_exception(any);
    }

    /**
     * Throws an <code>org.omg.CORBA.NO_IMPLEMENT</code> exception.
     * <P>
     * Returns the given exception to the client.  This method
     * is invoked by the DIR, which may call it at any time.
     * The <code>Any</code> object  passed to this method must
     * contain either a system
     * exception or one of the user exceptions specified in the
     * invoked operation's IDL definition. Passing in an
     * <code>Any</code> object that does not contain an exception
     * will cause a BAD_PARAM system exception to be thrown. Passing
     * in an unlisted user exception will result in either the DIR receiving a
     * BAD_PARAM system exception or in the client receiving an
     * UNKNOWN_EXCEPTION system exception.
     *
     * @param any       the <code>Any</code> object containing the exception
     * @exception BAD_PARAM if the given <code>Any</code> object does not
     *                      contain an exception or the exception is an
     *                      unlisted user exception
     * @exception UNKNOWN_EXCEPTION if the given exception is an unlisted
     *                              user exception and the DIR did not
     *                              receive a BAD_PARAM exception
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code>
     *      package comments for unimplemented features</a>
     */
    public void set_exception(Any any)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Returns the context information specified in IDL for the operation
     * when the operation is not an attribute access and the operation's IDL
     * definition contains a context expression; otherwise it returns
     * a nil <code>Context</code> reference. Calling the method
     * <code>ctx</code> before the method <code>arguments</code> has
     * been called or after the method <code>ctx</code>,
     * <code>set_result</code>, or <code>set_exception</code>
     * has been called will result in a
     * BAD_INV_ORDER system exception.
     *
     * @return                  the context object that is to be used
     *                          to resolve any context strings whose
     *                          values need to be sent with the invocation.
     * @exception BAD_INV_ORDER if (1) the method <code>ctx</code> is called
     *                          before the method <code>arguments</code> or
     *                          (2) the method <code>ctx</code> is called
     *                          after calling <code>set_result</code> or
     *                          <code>set_exception</code>
     */
    public abstract Context ctx();

}
