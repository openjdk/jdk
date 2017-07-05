/*
 * Copyright (c) 1996, 1999, Oracle and/or its affiliates. All rights reserved.
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
 * An object containing the information necessary for
 * invoking a method.  This class is
 * the cornerstone of the ORB Dynamic
 * Invocation Interface (DII), which allows dynamic creation and
 * invocation of requests.
 * A server cannot tell the difference between a client
 * invocation using a client stub and a request using the DII.
 * <P>
 * A <code>Request</code> object consists of:
 * <UL>
 * <LI>the name of the operation to be invoked
 * <LI>an <code>NVList</code> containing arguments for the operation.<BR>
 * Each item in the list is a <code>NamedValue</code> object, which has three
 * parts:
 *  <OL>
 *    <LI>the name of the argument
 *    <LI>the value of the argument (as an <code>Any</code> object)
 *    <LI>the argument mode flag indicating whether the argument is
 *        for input, output, or both
 *  </OL>
 * </UL>
 * <P>
 * <code>Request</code> objects may also contain additional information,
 * depending on how an operation was defined in the original IDL
 * interface definition.  For example, where appropriate, they may contain
 * a <code>NamedValue</code> object to hold the return value or exception,
 * a context, a list of possible exceptions, and a list of
 * context strings that need to be resolved.
 * <P>
 * New <code>Request</code> objects are created using one of the
 * <code>create_request</code> methods in the <code>Object</code> class.
 * In other words, a <code>create_request</code> method is performed on the
 * object which is to be invoked.
 *
 * @see org.omg.CORBA.NamedValue
 *
 */

public abstract class Request {

    /**
     * Retrieves the the target object reference.
     *
     * @return                  the object reference that points to the
     *                    object implementation for the method
     *                    to be invoked
     */

    public abstract org.omg.CORBA.Object target();

    /**
     * Retrieves the name of the method to be invoked.
     *
     * @return                  the name of the method to be invoked
     */

    public abstract String operation();

    /**
     * Retrieves the <code>NVList</code> object containing the arguments
     * to the method being invoked.  The elements in the list are
     * <code>NamedValue</code> objects, with each one describing an argument
     * to the method.
     *
     * @return  the <code>NVList</code> object containing the arguments
     *                  for the method
     *
     */

    public abstract NVList arguments();

    /**
     * Retrieves the <code>NamedValue</code> object containing the return
     * value for the method.
     *
     * @return          the <code>NamedValue</code> object containing the result
     *                          of the method
     */

    public abstract NamedValue result();

    /**
     * Retrieves the <code>Environment</code> object for this request.
     * It contains the exception that the method being invoked has
     * thrown (after the invocation returns).
     *
     *
     * @return  the <code>Environment</code> object for this request
     */

    public abstract Environment env();

    /**
     * Retrieves the <code>ExceptionList</code> object for this request.
     * This list contains <code>TypeCode</code> objects describing the
     * exceptions that may be thrown by the method being invoked.
     *
     * @return  the <code>ExceptionList</code> object describing the exceptions
     *            that may be thrown by the method being invoked
     */

    public abstract ExceptionList exceptions();

    /**
     * Retrieves the <code>ContextList</code> object for this request.
     * This list contains context <code>String</code>s that need to
     * be resolved and sent with the invocation.
     *
     *
     * @return                  the list of context strings whose values
     *                          need to be resolved and sent with the
     *                          invocation.
     */

    public abstract ContextList contexts();

    /**
     * Retrieves the <code>Context</code> object for this request.
     * This is a list of properties giving information about the
     * client, the environment, or the circumstances of this request.
     *
     * @return          the <code>Context</code> object that is to be used
     *                          to resolve any context strings whose
     *                          values need to be sent with the invocation
     */

    public abstract Context ctx();

    /**
     * Sets this request's <code>Context</code> object to the one given.
     *
     * @param c         the new <code>Context</code> object to be used for
     *                          resolving context strings
     */

    public abstract void ctx(Context c);


    /**
     * Creates an input argument and adds it to this <code>Request</code>
     * object.
     *
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the input argument added
     */

    public abstract Any add_in_arg();

    /**
     * Creates an input argument with the given name and adds it to
     * this <code>Request</code> object.
     *
     * @param name              the name of the argument being added
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the input argument added
     */

    public abstract Any add_named_in_arg(String name);

    /**
     * Adds an input/output argument to this <code>Request</code> object.
     *
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the input/output argument added
     */

    public abstract Any add_inout_arg();

    /**
     * Adds an input/output argument with the given name to this
     * <code>Request</code> object.
     *
     * @param name              the name of the argument being added
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the input/output argument added
     */

    public abstract Any add_named_inout_arg(String name);


    /**
     * Adds an output argument to this <code>Request</code> object.
     *
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the output argument added
     */

    public abstract Any add_out_arg();

    /**
     * Adds an output argument with the given name to this
     * <code>Request</code> object.
     *
     * @param name              the name of the argument being added
     * @return          an <code>Any</code> object that contains the
     *                value and typecode for the output argument added
     */

    public abstract Any add_named_out_arg(String name);

    /**
     * Sets the typecode for the return
     * value of the method.
     *
     * @param tc                        the <code>TypeCode</code> object containing type information
     *                   for the return value
     */

    public abstract void set_return_type(TypeCode tc);

    /**
     * Returns the <code>Any</code> object that contains the value for the
     * result of the method.
     *
     * @return                  an <code>Any</code> object containing the value and
     *                   typecode for the return value
     */

    public abstract Any return_value();

    /**
     * Makes a synchronous invocation using the
     * information in the <code>Request</code> object. Exception information is
     * placed into the <code>Request</code> object's environment object.
     */

    public abstract void invoke();

    /**
     * Makes a oneway invocation on the
     * request. In other words, it does not expect or wait for a
     * response. Note that this can be used even if the operation was
     * not declared as oneway in the IDL declaration. No response or
     * exception information is returned.
     */

    public abstract void send_oneway();

    /**
     * Makes an asynchronous invocation on
     * the request. In other words, it does not wait for a response before it
     * returns to the user. The user can then later use the methods
     * <code>poll_response</code> and <code>get_response</code> to get
     * the result or exception information for the invocation.
     */

    public abstract void send_deferred();

    /**
     * Allows the user to determine
     * whether a response has been received for the invocation triggered
     * earlier with the <code>send_deferred</code> method.
     *
     * @return          <code>true</code> if the method response has
     *                          been received; <code>false</code> otherwise
     */

    public abstract boolean poll_response();

    /**
     * Allows the user to access the
     * response for the invocation triggered earlier with the
     * <code>send_deferred</code> method.
     *
     * @exception WrongTransaction  if the method <code>get_response</code> was invoked
     * from a different transaction's scope than the one from which the
     * request was originally sent. See the OMG Transaction Service specification
     * for details.
     */

    public abstract void get_response() throws WrongTransaction;

};
