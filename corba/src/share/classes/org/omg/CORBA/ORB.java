/*
 * Copyright 1995-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package org.omg.CORBA;

import org.omg.CORBA.portable.*;
import org.omg.CORBA.ORBPackage.InvalidName;

import java.util.Properties;
import java.applet.Applet;
import java.io.File;
import java.io.FileInputStream;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A class providing APIs for the CORBA Object Request Broker
 * features.  The <code>ORB</code> class also provides
 * "pluggable ORB implementation" APIs that allow another vendor's ORB
 * implementation to be used.
 * <P>
 * An ORB makes it possible for CORBA objects to communicate
 * with each other by connecting objects making requests (clients) with
 * objects servicing requests (servers).
 * <P>
 *
 * The <code>ORB</code> class, which
 * encapsulates generic CORBA functionality, does the following:
 * (Note that items 5 and 6, which include most of the methods in
 * the class <code>ORB</code>, are typically used with the <code>Dynamic Invocation
 * Interface</code> (DII) and the <code>Dynamic Skeleton Interface</code>
 * (DSI).
 * These interfaces may be used by a developer directly, but
 * most commonly they are used by the ORB internally and are
 * not seen by the general programmer.)
 * <OL>
 * <li> initializes the ORB implementation by supplying values for
 *      predefined properties and environmental parameters
 * <li> obtains initial object references to services such as
 * the NameService using the method <code>resolve_initial_references</code>
 * <li> converts object references to strings and back
 * <li> connects the ORB to a servant (an instance of a CORBA object
 * implementation) and disconnects the ORB from a servant
 * <li> creates objects such as
 *   <ul>
 *   <li><code>TypeCode</code>
 *   <li><code>Any</code>
 *   <li><code>NamedValue</code>
 *   <li><code>Context</code>
 *   <li><code>Environment</code>
 *   <li>lists (such as <code>NVList</code>) containing these objects
 *   </ul>
 * <li> sends multiple messages in the DII
 * </OL>
 *
 * <P>
 * The <code>ORB</code> class can be used to obtain references to objects
 * implemented anywhere on the network.
 * <P>
 * An application or applet gains access to the CORBA environment
 * by initializing itself into an <code>ORB</code> using one of
 * three <code>init</code> methods.  Two of the three methods use the properties
 * (associations of a name with a value) shown in the
 * table below.<BR>
 * <TABLE BORDER=1 SUMMARY="Standard Java CORBA Properties">
 * <TR><TH>Property Name</TH>   <TH>Property Value</TH></TR>
 * <CAPTION>Standard Java CORBA Properties:</CAPTION>
 *     <TR><TD>org.omg.CORBA.ORBClass</TD>
 *     <TD>class name of an ORB implementation</TD></TR>
 *     <TR><TD>org.omg.CORBA.ORBSingletonClass</TD>
 *     <TD>class name of the ORB returned by <code>init()</code></TD></TR>
 * </TABLE>
 * <P>
 * These properties allow a different vendor's <code>ORB</code>
 * implementation to be "plugged in."
 * <P>
 * When an ORB instance is being created, the class name of the ORB
 * implementation is located using
 * the following standard search order:<P>
 *
 * <OL>
 *     <LI>check in Applet parameter or application string array, if any
 *
 *     <LI>check in properties parameter, if any
 *
 *     <LI>check in the System properties
 *
 *     <LI>check in the orb.properties file located in the user.home
 *         directory (if any)
 *
 *     <LI>check in the orb.properties file located in the java.home/lib
 *         directory (if any)
 *
 *     <LI>fall back on a hardcoded default behavior (use the Java&nbsp;IDL
 *         implementation)
 * </OL>
 * <P>
 * Note that Java&nbsp;IDL provides a default implementation for the
 * fully-functional ORB and for the Singleton ORB.  When the method
 * <code>init</code> is given no parameters, the default Singleton
 * ORB is returned.  When the method <code>init</code> is given parameters
 * but no ORB class is specified, the Java&nbsp;IDL ORB implementation
 * is returned.
 * <P>
 * The following code fragment creates an <code>ORB</code> object
 * initialized with the default ORB Singleton.
 * This ORB has a
 * restricted implementation to prevent malicious applets from doing
 * anything beyond creating typecodes.
 * It is called a singleton
 * because there is only one instance for an entire virtual machine.
 * <PRE>
 *    ORB orb = ORB.init();
 * </PRE>
 * <P>
 * The following code fragment creates an <code>ORB</code> object
 * for an application.  The parameter <code>args</code>
 * represents the arguments supplied to the application's <code>main</code>
 * method.  Since the property specifies the ORB class to be
 * "SomeORBImplementation", the new ORB will be initialized with
 * that ORB implementation.  If p had been null,
 * and the arguments had not specified an ORB class,
 * the new ORB would have been
 * initialized with the default Java&nbsp;IDL implementation.
 * <PRE>
 *    Properties p = new Properties();
 *    p.put("org.omg.CORBA.ORBClass", "SomeORBImplementation");
 *    ORB orb = ORB.init(args, p);
 * </PRE>
 * <P>
 * The following code fragment creates an <code>ORB</code> object
 * for the applet supplied as the first parameter.  If the given
 * applet does not specify an ORB class, the new ORB will be
 * initialized with the default Java&nbsp;IDL implementation.
 * <PRE>
 *    ORB orb = ORB.init(myApplet, null);
 * </PRE>
 * <P>
 * An application or applet can be initialized in one or more ORBs.
 * ORB initialization is a bootstrap call into the CORBA world.
 * @since   JDK1.2
 */
abstract public class ORB {

    //
    // This is the ORB implementation used when nothing else is specified.
    // Whoever provides this class customizes this string to
    // point at their ORB implementation.
    //
    private static final String ORBClassKey = "org.omg.CORBA.ORBClass";
    private static final String ORBSingletonClassKey = "org.omg.CORBA.ORBSingletonClass";

    //
    // The last resort fallback ORB implementation classes in case
    // no ORB implementation class is dynamically configured through
    // properties or applet parameters. Change these values to
    // vendor-specific class names.
    //
    private static final String defaultORB = "com.sun.corba.se.impl.orb.ORBImpl";
    private static final String defaultORBSingleton = "com.sun.corba.se.impl.orb.ORBSingleton";

    //
    // The global instance of the singleton ORB implementation which
    // acts as a factory for typecodes for generated Helper classes.
    // TypeCodes should be immutable since they may be shared across
    // different security contexts (applets). There should be no way to
    // use a TypeCode as a storage depot for illicitly passing
    // information or Java objects between different security contexts.
    //
    static private ORB singleton;

    // Get System property
    private static String getSystemProperty(final String name) {

        // This will not throw a SecurityException because this
        // class was loaded from rt.jar using the bootstrap classloader.
        String propValue = (String) AccessController.doPrivileged(
            new PrivilegedAction() {
                public java.lang.Object run() {
                    return System.getProperty(name);
                }
            }
        );

        return propValue;
    }

    // Get property from orb.properties in either <user.home> or <java-home>/lib
    // directories.
    private static String getPropertyFromFile(final String name) {
        // This will not throw a SecurityException because this
        // class was loaded from rt.jar using the bootstrap classloader.

        String propValue = (String) AccessController.doPrivileged(
            new PrivilegedAction() {
                private Properties getFileProperties( String fileName ) {
                    try {
                        File propFile = new File( fileName ) ;
                        if (!propFile.exists())
                            return null ;

                        Properties props = new Properties() ;
                        FileInputStream fis = new FileInputStream(propFile);
                        try {
                            props.load( fis );
                        } finally {
                            fis.close() ;
                        }

                        return props ;
                    } catch (Exception exc) {
                        return null ;
                    }
                }

                public java.lang.Object run() {
                    String userHome = System.getProperty("user.home");
                    String fileName = userHome + File.separator +
                        "orb.properties" ;
                    Properties props = getFileProperties( fileName ) ;

                    if (props != null) {
                        String value = props.getProperty( name ) ;
                        if (value != null)
                            return value ;
                    }

                    String javaHome = System.getProperty("java.home");
                    fileName = javaHome + File.separator
                        + "lib" + File.separator + "orb.properties";
                    props = getFileProperties( fileName ) ;

                    if (props == null)
                        return null ;
                    else
                        return props.getProperty( name ) ;
                }
            }
        );

        return propValue;
    }

    /**
     * Returns the <code>ORB</code> singleton object. This method always returns the
     * same ORB instance, which is an instance of the class described by the
     * <code>org.omg.CORBA.ORBSingletonClass</code> system property.
     * <P>
     * This no-argument version of the method <code>init</code> is used primarily
     * as a factory for <code>TypeCode</code> objects, which are used by
     * <code>Helper</code> classes to implement the method <code>type</code>.
     * It is also used to create <code>Any</code> objects that are used to
     * describe <code>union</code> labels (as part of creating a <code>
     * TypeCode</code> object for a <code>union</code>).
     * <P>
     * This method is not intended to be used by applets, and in the event
     * that it is called in an applet environment, the ORB it returns
     * is restricted so that it can be used only as a factory for
     * <code>TypeCode</code> objects.  Any <code>TypeCode</code> objects
     * it produces can be safely shared among untrusted applets.
     * <P>
     * If an ORB is created using this method from an applet,
     * a system exception will be thrown if
     * methods other than those for
     * creating <code>TypeCode</code> objects are invoked.
     *
     * @return the singleton ORB
     */
    public static synchronized ORB init() {
        if (singleton == null) {
            String className = getSystemProperty(ORBSingletonClassKey);
            if (className == null)
                className = getPropertyFromFile(ORBSingletonClassKey);
            if (className == null)
                className = defaultORBSingleton;

            singleton = create_impl(className);
        }
        return singleton;
    }

    private static ORB create_impl(String className) {

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = ClassLoader.getSystemClassLoader();

        try {
            return (ORB) Class.forName(className, true, cl).newInstance();
        } catch (Throwable ex) {
            SystemException systemException = new INITIALIZE(
               "can't instantiate default ORB implementation " + className);
            systemException.initCause(ex);
            throw systemException;
        }
    }

    /**
     * Creates a new <code>ORB</code> instance for a standalone
     * application.  This method may be called from applications
     * only and returns a new fully functional <code>ORB</code> object
     * each time it is called.
     * @param args command-line arguments for the application's <code>main</code>
     *             method; may be <code>null</code>
     * @param props application-specific properties; may be <code>null</code>
     * @return the newly-created ORB instance
     */
    public static ORB init(String[] args, Properties props) {
        //
        // Note that there is no standard command-line argument for
        // specifying the default ORB implementation. For an
        // application you can choose an implementation either by
        // setting the CLASSPATH to pick a different org.omg.CORBA
        // and it's baked-in ORB implementation default or by
        // setting an entry in the properties object or in the
        // system properties.
        //
        String className = null;
        ORB orb;

        if (props != null)
            className = props.getProperty(ORBClassKey);
        if (className == null)
            className = getSystemProperty(ORBClassKey);
        if (className == null)
            className = getPropertyFromFile(ORBClassKey);
        if (className == null)
            className = defaultORB;

        orb = create_impl(className);
        orb.set_parameters(args, props);
        return orb;
    }


    /**
     * Creates a new <code>ORB</code> instance for an applet.  This
     * method may be called from applets only and returns a new
     * fully-functional <code>ORB</code> object each time it is called.
     * @param app the applet; may be <code>null</code>
     * @param props applet-specific properties; may be <code>null</code>
     * @return the newly-created ORB instance
     */
    public static ORB init(Applet app, Properties props) {
        String className;
        ORB orb;

        className = app.getParameter(ORBClassKey);
        if (className == null && props != null)
            className = props.getProperty(ORBClassKey);
        if (className == null)
            className = getSystemProperty(ORBClassKey);
        if (className == null)
            className = getPropertyFromFile(ORBClassKey);
        if (className == null)
            className = defaultORB;

        orb = create_impl(className);
        orb.set_parameters(app, props);
        return orb;
    }

    /**
     * Allows the ORB implementation to be initialized with the given
     * parameters and properties. This method, used in applications only,
     * is implemented by subclass ORB implementations and called
     * by the appropriate <code>init</code> method to pass in its parameters.
     *
     * @param args command-line arguments for the application's <code>main</code>
     *             method; may be <code>null</code>
     * @param props application-specific properties; may be <code>null</code>
     */
    abstract protected void set_parameters(String[] args, Properties props);

    /**
     * Allows the ORB implementation to be initialized with the given
     * applet and parameters. This method, used in applets only,
     * is implemented by subclass ORB implementations and called
     * by the appropriate <code>init</code> method to pass in its parameters.
     *
     * @param app the applet; may be <code>null</code>
     * @param props applet-specific properties; may be <code>null</code>
     */
    abstract protected void set_parameters(Applet app, Properties props);

    /**
     * Connects the given servant object (a Java object that is
     * an instance of the server implementation class)
     * to the ORB. The servant class must
     * extend the <code>ImplBase</code> class corresponding to the interface that is
     * supported by the server. The servant must thus be a CORBA object
     * reference, and inherit from <code>org.omg.CORBA.Object</code>.
     * Servants created by the user can start receiving remote invocations
     * after the method <code>connect</code> has been called. A servant may also be
     * automatically and implicitly connected to the ORB if it is passed as
     * an IDL parameter in an IDL method invocation on a non-local object,
     * that is, if the servant object has to be marshalled and sent outside of the
     * process address space.
     * <P>
     * Calling the method <code>connect</code> has no effect
     * when the servant object is already connected to the ORB.
     * <P>
     * Deprecated by the OMG in favor of the Portable Object Adapter APIs.
     *
     * @param obj The servant object reference
     */
    public void connect(org.omg.CORBA.Object obj) {
        throw new NO_IMPLEMENT();
    }

    /**
     * Destroys the ORB so that its resources can be reclaimed.
     * Any operation invoked on a destroyed ORB reference will throw the
     * <code>OBJECT_NOT_EXIST</code> exception.
     * Once an ORB has been destroyed, another call to <code>init</code>
     * with the same ORBid will return a reference to a newly constructed ORB.<p>
     * If <code>destroy</code> is called on an ORB that has not been shut down,
     * it will start the shut down process and block until the ORB has shut down
     * before it destroys the ORB.<br>
     * If an application calls <code>destroy</code> in a thread that is currently servicing
     * an invocation, the <code>BAD_INV_ORDER</code> system exception will be thrown
     * with the OMG minor code 3, since blocking would result in a deadlock.<p>
     * For maximum portability and to avoid resource leaks, an application should
     * always call <code>shutdown</code> and <code>destroy</code>
     * on all ORB instances before exiting.
     *
     * @throws org.omg.CORBA.BAD_INV_ORDER if the current thread is servicing an invocation
     */
    public void destroy( ) {
        throw new NO_IMPLEMENT();
    }

    /**
     * Disconnects the given servant object from the ORB. After this method returns,
     * the ORB will reject incoming remote requests for the disconnected
     * servant and will send the exception
     * <code>org.omg.CORBA.OBJECT_NOT_EXIST</code> back to the
     * remote client. Thus the object appears to be destroyed from the
     * point of view of remote clients. Note, however, that local requests issued
     * using the servant  directly do not
     * pass through the ORB; hence, they will continue to be processed by the
     * servant.
     * <P>
     * Calling the method <code>disconnect</code> has no effect
     * if the servant is not connected to the ORB.
     * <P>
     * Deprecated by the OMG in favor of the Portable Object Adapter APIs.
     *
     * @param obj The servant object to be disconnected from the ORB
     */
    public void disconnect(org.omg.CORBA.Object obj) {
        throw new NO_IMPLEMENT();
    }

    //
    // ORB method implementations.
    //
    // We are trying to accomplish 2 things at once in this class.
    // It can act as a default ORB implementation front-end,
    // creating an actual ORB implementation object which is a
    // subclass of this ORB class and then delegating the method
    // implementations.
    //
    // To accomplish the delegation model, the 'delegate' private instance
    // variable is set if an instance of this class is created directly.
    //

    /**
     * Returns a list of the initially available CORBA object references,
     * such as "NameService" and "InterfaceRepository".
     *
     * @return an array of <code>String</code> objects that represent
     *         the object references for CORBA services
     *         that are initially available with this ORB
     */
    abstract public String[] list_initial_services();

    /**
     * Resolves a specific object reference from the set of available
     * initial service names.
     *
     * @param object_name the name of the initial service as a string
     * @return  the object reference associated with the given name
     * @exception InvalidName if the given name is not associated with a
     *                         known service
     */
    abstract public org.omg.CORBA.Object resolve_initial_references(String object_name)
        throws InvalidName;

    /**
     * Converts the given CORBA object reference to a string.
     * Note that the format of this string is predefined by IIOP, allowing
     * strings generated by a different ORB to be converted back into an object
     * reference.
     * <P>
     * The resulting <code>String</code> object may be stored or communicated
     * in any way that a <code>String</code> object can be manipulated.
     *
     * @param obj the object reference to stringify
     * @return the string representing the object reference
     */
    abstract public String object_to_string(org.omg.CORBA.Object obj);

    /**
     * Converts a string produced by the method <code>object_to_string</code>
     * back to a CORBA object reference.
     *
     * @param str the string to be converted back to an object reference.  It must
     * be the result of converting an object reference to a string using the
     * method <code>object_to_string</code>.
     * @return the object reference
     */
    abstract public org.omg.CORBA.Object string_to_object(String str);

    /**
     * Allocates an <code>NVList</code> with (probably) enough
     * space for the specified number of <code>NamedValue</code> objects.
     * Note that the specified size is only a hint to help with
     * storage allocation and does not imply the maximum size of the list.
     *
     * @param count  suggested number of <code>NamedValue</code> objects for
     *               which to allocate space
     * @return the newly-created <code>NVList</code>
     *
     * @see NVList
     */
    abstract public NVList create_list(int count);

    /**
     * Creates an <code>NVList</code> initialized with argument
     * descriptions for the operation described in the given
     * <code>OperationDef</code> object.  This <code>OperationDef</code> object
     * is obtained from an Interface Repository. The arguments in the
     * returned <code>NVList</code> object are in the same order as in the
     * original IDL operation definition, which makes it possible for the list
     * to be used in dynamic invocation requests.
     *
     * @param oper      the <code>OperationDef</code> object to use to create the list
     * @return          a newly-created <code>NVList</code> object containing
     * descriptions of the arguments to the method described in the given
     * <code>OperationDef</code> object
     *
     * @see NVList
     */
    public NVList create_operation_list(org.omg.CORBA.Object oper)
    {
        // If we came here, it means that the actual ORB implementation
        // did not have a create_operation_list(...CORBA.Object oper) method,
        // so lets check if it has a create_operation_list(OperationDef oper)
        // method.
        try {
            // First try to load the OperationDef class
            String opDefClassName = "org.omg.CORBA.OperationDef";
            Class opDefClass = null;

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if ( cl == null )
                cl = ClassLoader.getSystemClassLoader();
            // if this throws a ClassNotFoundException, it will be caught below.
            opDefClass = Class.forName(opDefClassName, true, cl);

            // OK, we loaded OperationDef. Now try to get the
            // create_operation_list(OperationDef oper) method.
            Class[] argc = { opDefClass };
            java.lang.reflect.Method meth =
                this.getClass().getMethod("create_operation_list", argc);

            // OK, the method exists, so invoke it and be happy.
            Object[] argx = { oper };
            return (org.omg.CORBA.NVList)meth.invoke(this, argx);
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
        }
        catch( RuntimeException ex ) {
            throw ex;
        }
        catch( Exception exr ) {
            throw new org.omg.CORBA.NO_IMPLEMENT();
        }
    }


    /**
     * Creates a <code>NamedValue</code> object
     * using the given name, value, and argument mode flags.
     * <P>
     * A <code>NamedValue</code> object serves as (1) a parameter or return
     * value or (2) a context property.
     * It may be used by itself or
     * as an element in an <code>NVList</code> object.
     *
     * @param s  the name of the <code>NamedValue</code> object
     * @param any  the <code>Any</code> value to be inserted into the
     *             <code>NamedValue</code> object
     * @param flags  the argument mode flags for the <code>NamedValue</code>: one of
     * <code>ARG_IN.value</code>, <code>ARG_OUT.value</code>,
     * or <code>ARG_INOUT.value</code>.
     *
     * @return  the newly-created <code>NamedValue</code> object
     * @see NamedValue
     */
    abstract public NamedValue create_named_value(String s, Any any, int flags);

    /**
     * Creates an empty <code>ExceptionList</code> object.
     *
     * @return  the newly-created <code>ExceptionList</code> object
     */
    abstract public ExceptionList create_exception_list();

    /**
     * Creates an empty <code>ContextList</code> object.
     *
     * @return  the newly-created <code>ContextList</code> object
     * @see ContextList
     * @see Context
     */
    abstract public ContextList create_context_list();

    /**
     * Gets the default <code>Context</code> object.
     *
     * @return the default <code>Context</code> object
     * @see Context
     */
    abstract public Context get_default_context();

    /**
     * Creates an <code>Environment</code> object.
     *
     * @return  the newly-created <code>Environment</code> object
     * @see Environment
     */
    abstract public Environment create_environment();

    /**
     * Creates a new <code>org.omg.CORBA.portable.OutputStream</code> into which
     * IDL method parameters can be marshalled during method invocation.
     * @return          the newly-created
     *              <code>org.omg.CORBA.portable.OutputStream</code> object
     */
    abstract public org.omg.CORBA.portable.OutputStream create_output_stream();

    /**
     * Sends multiple dynamic (DII) requests asynchronously without expecting
     * any responses. Note that oneway invocations are not guaranteed to
     * reach the server.
     *
     * @param req               an array of request objects
     */
    abstract public void send_multiple_requests_oneway(Request[] req);

    /**
     * Sends multiple dynamic (DII) requests asynchronously.
     *
     * @param req               an array of <code>Request</code> objects
     */
    abstract public void send_multiple_requests_deferred(Request[] req);

    /**
     * Finds out if any of the deferred (asynchronous) invocations have
     * a response yet.
     * @return <code>true</code> if there is a response available;
     *         <code> false</code> otherwise
     */
    abstract public boolean poll_next_response();

    /**
     * Gets the next <code>Request</code> instance for which a response
     * has been received.
     *
     * @return          the next <code>Request</code> object ready with a response
     * @exception WrongTransaction if the method <code>get_next_response</code>
     * is called from a transaction scope different
     * from the one from which the original request was sent. See the
     * OMG Transaction Service specification for details.
     */
    abstract public Request get_next_response() throws WrongTransaction;

    /**
     * Retrieves the <code>TypeCode</code> object that represents
     * the given primitive IDL type.
     *
     * @param tcKind    the <code>TCKind</code> instance corresponding to the
     *                  desired primitive type
     * @return          the requested <code>TypeCode</code> object
     */
    abstract public TypeCode get_primitive_tc(TCKind tcKind);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>struct</code>.
     * The <code>TypeCode</code> object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the <code>struct</code>
     * @param name      the name of the <code>struct</code>
     * @param members   an array describing the members of the <code>struct</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>struct</code>
     */
    abstract public TypeCode create_struct_tc(String id, String name,
                                              StructMember[] members);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>union</code>.
     * The <code>TypeCode</code> object is initialized with the given id,
     * name, discriminator type, and members.
     *
     * @param id        the repository id of the <code>union</code>
     * @param name      the name of the <code>union</code>
     * @param discriminator_type        the type of the <code>union</code> discriminator
     * @param members   an array describing the members of the <code>union</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>union</code>
     */
    abstract public TypeCode create_union_tc(String id, String name,
                                             TypeCode discriminator_type,
                                             UnionMember[] members);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>enum</code>.
     * The <code>TypeCode</code> object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the <code>enum</code>
     * @param name      the name for the <code>enum</code>
     * @param members   an array describing the members of the <code>enum</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>enum</code>
     */
    abstract public TypeCode create_enum_tc(String id, String name, String[] members);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>alias</code>
     * (<code>typedef</code>).
     * The <code>TypeCode</code> object is initialized with the given id,
     * name, and original type.
     *
     * @param id        the repository id for the alias
     * @param name      the name for the alias
     * @param original_type
     *                  the <code>TypeCode</code> object describing the original type
     *          for which this is an alias
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>alias</code>
     */
    abstract public TypeCode create_alias_tc(String id, String name,
                                             TypeCode original_type);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>exception</code>.
     * The <code>TypeCode</code> object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the <code>exception</code>
     * @param name      the name for the <code>exception</code>
     * @param members   an array describing the members of the <code>exception</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>exception</code>
     */
    abstract public TypeCode create_exception_tc(String id, String name,
                                                 StructMember[] members);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>interface</code>.
     * The <code>TypeCode</code> object is initialized with the given id
     * and name.
     *
     * @param id        the repository id for the interface
     * @param name      the name for the interface
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>interface</code>
     */

    abstract public TypeCode create_interface_tc(String id, String name);

    /**
     * Creates a <code>TypeCode</code> object representing a bounded IDL
     * <code>string</code>.
     * The <code>TypeCode</code> object is initialized with the given bound,
     * which represents the maximum length of the string. Zero indicates
     * that the string described by this type code is unbounded.
     *
     * @param bound     the bound for the <code>string</code>; cannot be negative
     * @return          a newly-created <code>TypeCode</code> object describing
     *              a bounded IDL <code>string</code>
     * @exception BAD_PARAM if bound is a negative value
     */

    abstract public TypeCode create_string_tc(int bound);

    /**
     * Creates a <code>TypeCode</code> object representing a bounded IDL
     * <code>wstring</code> (wide string).
     * The <code>TypeCode</code> object is initialized with the given bound,
     * which represents the maximum length of the wide string. Zero indicates
     * that the string described by this type code is unbounded.
     *
     * @param bound     the bound for the <code>wstring</code>; cannot be negative
     * @return          a newly-created <code>TypeCode</code> object describing
     *              a bounded IDL <code>wstring</code>
     * @exception BAD_PARAM if bound is a negative value
     */
    abstract public TypeCode create_wstring_tc(int bound);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>sequence</code>.
     * The <code>TypeCode</code> object is initialized with the given bound and
     * element type.
     *
     * @param bound     the bound for the <code>sequence</code>, 0 if unbounded
     * @param element_type
     *                  the <code>TypeCode</code> object describing the elements
     *          contained in the <code>sequence</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>sequence</code>
     */
    abstract public TypeCode create_sequence_tc(int bound, TypeCode element_type);

    /**
     * Creates a <code>TypeCode</code> object representing a
     * a recursive IDL <code>sequence</code>.
     * <P>
     * For the IDL <code>struct</code> Node in following code fragment,
     * the offset parameter for creating its sequence would be 1:
     * <PRE>
     *    Struct Node {
     *        long value;
     *        Sequence &lt;Node&gt; subnodes;
     *    };
     * </PRE>
     *
     * @param bound     the bound for the sequence, 0 if unbounded
     * @param offset    the index to the enclosing <code>TypeCode</code> object
     *                  that describes the elements of this sequence
     * @return          a newly-created <code>TypeCode</code> object describing
     *                   a recursive sequence
     * @deprecated Use a combination of create_recursive_tc and create_sequence_tc instead
     * @see #create_recursive_tc(String) create_recursive_tc
     * @see #create_sequence_tc(int, TypeCode) create_sequence_tc
     */
    @Deprecated
    abstract public TypeCode create_recursive_sequence_tc(int bound, int offset);

    /**
     * Creates a <code>TypeCode</code> object representing an IDL <code>array</code>.
     * The <code>TypeCode</code> object is initialized with the given length and
     * element type.
     *
     * @param length    the length of the <code>array</code>
     * @param element_type  a <code>TypeCode</code> object describing the type
     *                      of element contained in the <code>array</code>
     * @return          a newly-created <code>TypeCode</code> object describing
     *              an IDL <code>array</code>
     */
    abstract public TypeCode create_array_tc(int length, TypeCode element_type);

    /**
     * Create a <code>TypeCode</code> object for an IDL native type.
     *
     * @param id        the logical id for the native type.
     * @param name      the name of the native type.
     * @return          the requested TypeCode.
     */
    public org.omg.CORBA.TypeCode create_native_tc(String id,
                                                   String name)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Create a <code>TypeCode</code> object for an IDL abstract interface.
     *
     * @param id        the logical id for the abstract interface type.
     * @param name      the name of the abstract interface type.
     * @return          the requested TypeCode.
     */
    public org.omg.CORBA.TypeCode create_abstract_interface_tc(
                                                               String id,
                                                               String name)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    /**
     * Create a <code>TypeCode</code> object for an IDL fixed type.
     *
     * @param digits    specifies the total number of decimal digits in the number
     *                  and must be from 1 to 31 inclusive.
     * @param scale     specifies the position of the decimal point.
     * @return          the requested TypeCode.
     */
    public org.omg.CORBA.TypeCode create_fixed_tc(short digits, short scale)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    // orbos 98-01-18: Objects By Value -- begin


    /**
     * Create a <code>TypeCode</code> object for an IDL value type.
     * The concrete_base parameter is the TypeCode for the immediate
     * concrete valuetype base of the valuetype for which the TypeCode
     * is being created.
     * It may be null if the valuetype does not have a concrete base.
     *
     * @param id                 the logical id for the value type.
     * @param name               the name of the value type.
     * @param type_modifier      one of the value type modifier constants:
     *                           VM_NONE, VM_CUSTOM, VM_ABSTRACT or VM_TRUNCATABLE
     * @param concrete_base      a <code>TypeCode</code> object
     *                           describing the concrete valuetype base
     * @param members            an array containing the members of the value type
     * @return                   the requested TypeCode
     */
    public org.omg.CORBA.TypeCode create_value_tc(String id,
                                                  String name,
                                                  short type_modifier,
                                                  TypeCode concrete_base,
                                                  ValueMember[] members)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Create a recursive <code>TypeCode</code> object which
     * serves as a placeholder for a concrete TypeCode during the process of creating
     * TypeCodes which contain recursion. The id parameter specifies the repository id of
     * the type for which the recursive TypeCode is serving as a placeholder. Once the
     * recursive TypeCode has been properly embedded in the enclosing TypeCode which
     * corresponds to the specified repository id, it will function as a normal TypeCode.
     * Invoking operations on the recursive TypeCode before it has been embedded in the
     * enclosing TypeCode will result in a <code>BAD_TYPECODE</code> exception.
     * <P>
     * For example, the following IDL type declaration contains recursion:
     * <PRE>
     *    Struct Node {
     *        Sequence&lt;Node&gt; subnodes;
     *    };
     * </PRE>
     * <P>
     * To create a TypeCode for struct Node, you would invoke the TypeCode creation
     * operations as shown below:
     * <PRE>
     * String nodeID = "IDL:Node:1.0";
     * TypeCode recursiveSeqTC = orb.create_sequence_tc(0, orb.create_recursive_tc(nodeID));
     * StructMember[] members = { new StructMember("subnodes", recursiveSeqTC, null) };
     * TypeCode structNodeTC = orb.create_struct_tc(nodeID, "Node", members);
     * </PRE>
     * <P>
     * Also note that the following is an illegal IDL type declaration:
     * <PRE>
     *    Struct Node {
     *        Node next;
     *    };
     * </PRE>
     * <P>
     * Recursive types can only appear within sequences which can be empty.
     * That way marshaling problems, when transmitting the struct in an Any, are avoided.
     * <P>
     * @param id                 the logical id of the referenced type
     * @return                   the requested TypeCode
     */
    public org.omg.CORBA.TypeCode create_recursive_tc(String id) {
        // implemented in subclass
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a <code>TypeCode</code> object for an IDL value box.
     *
     * @param id                 the logical id for the value type
     * @param name               the name of the value type
     * @param boxed_type         the TypeCode for the type
     * @return                   the requested TypeCode
     */
    public org.omg.CORBA.TypeCode create_value_box_tc(String id,
                                                      String name,
                                                      TypeCode boxed_type)
    {
        // implemented in subclass
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    // orbos 98-01-18: Objects By Value -- end

    /**
     * Creates an IDL <code>Any</code> object initialized to
     * contain a <code>Typecode</code> object whose <code>kind</code> field
     * is set to <code>TCKind.tc_null</code>.
     *
     * @return          a newly-created <code>Any</code> object
     */
    abstract public Any create_any();




    /**
     * Retrieves a <code>Current</code> object.
     * The <code>Current</code> interface is used to manage thread-specific
     * information for use by services such as transactions and security.
     *
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     *
     * @return          a newly-created <code>Current</code> object
     * @deprecated      use <code>resolve_initial_references</code>.
     */
    @Deprecated
    public org.omg.CORBA.Current get_current()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * This operation blocks the current thread until the ORB has
     * completed the shutdown process, initiated when some thread calls
     * <code>shutdown</code>. It may be used by multiple threads which
     * get all notified when the ORB shuts down.
     *
     */
    public void run()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Instructs the ORB to shut down, which causes all
     * object adapters to shut down, in preparation for destruction.<br>
     * If the <code>wait_for_completion</code> parameter
     * is true, this operation blocks until all ORB processing (including
     * processing of currently executing requests, object deactivation,
     * and other object adapter operations) has completed.
     * If an application does this in a thread that is currently servicing
     * an invocation, the <code>BAD_INV_ORDER</code> system exception
     * will be thrown with the OMG minor code 3,
     * since blocking would result in a deadlock.<br>
     * If the <code>wait_for_completion</code> parameter is <code>FALSE</code>,
     * then shutdown may not have completed upon return.<p>
     * While the ORB is in the process of shutting down, the ORB operates as normal,
     * servicing incoming and outgoing requests until all requests have been completed.
     * Once an ORB has shutdown, only object reference management operations
     * may be invoked on the ORB or any object reference obtained from it.
     * An application may also invoke the <code>destroy</code> operation on the ORB itself.
     * Invoking any other operation will throw the <code>BAD_INV_ORDER</code>
     * system exception with the OMG minor code 4.<p>
     * The <code>ORB.run</code> method will return after
     * <code>shutdown</code> has been called.
     *
     * @param wait_for_completion <code>true</code> if the call
     *        should block until the shutdown is complete;
     *        <code>false</code> if it should return immediately
     * @throws org.omg.CORBA.BAD_INV_ORDER if the current thread is servicing
     *         an invocation
     */
    public void shutdown(boolean wait_for_completion)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Returns <code>true</code> if the ORB needs the main thread to
     * perform some work, and <code>false</code> if the ORB does not
     * need the main thread.
     *
     * @return <code>true</code> if there is work pending, meaning that the ORB
     *         needs the main thread to perform some work; <code>false</code>
     *         if there is no work pending and thus the ORB does not need the
     *         main thread
     *
     */
    public boolean work_pending()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Performs an implementation-dependent unit of work if called
     * by the main thread. Otherwise it does nothing.
     * The methods <code>work_pending</code> and <code>perform_work</code>
     * can be used in
     * conjunction to implement a simple polling loop that multiplexes
     * the main thread among the ORB and other activities.
     *
     */
    public void perform_work()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Used to obtain information about CORBA facilities and services
     * that are supported by this ORB. The service type for which
     * information is being requested is passed in as the in
     * parameter <tt>service_type</tt>, the values defined by
     * constants in the CORBA module. If service information is
     * available for that type, that is returned in the out parameter
     * <tt>service_info</tt>, and the operation returns the
     * value <tt>true</tt>. If no information for the requested
     * services type is available, the operation returns <tt>false</tt>
     *  (i.e., the service is not supported by this ORB).
     * <P>
     * @param service_type a <code>short</code> indicating the
     *        service type for which information is being requested
     * @param service_info a <code>ServiceInformationHolder</code> object
     *        that will hold the <code>ServiceInformation</code> object
     *        produced by this method
     * @return <code>true</code> if service information is available
     *        for the <tt>service_type</tt>;
     *         <tt>false</tt> if no information for the
     *         requested services type is available
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     */
    public boolean get_service_information(short service_type,
                                           ServiceInformationHolder service_info)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    // orbos 98-01-18: Objects By Value -- begin

    /**
     * Creates a new <code>DynAny</code> object from the given
     * <code>Any</code> object.
     * <P>
     * @param value the <code>Any</code> object from which to create a new
     *        <code>DynAny</code> object
     * @return the new <code>DynAny</code> object created from the given
     *         <code>Any</code> object
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynAny create_dyn_any(org.omg.CORBA.Any value)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a basic <code>DynAny</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynAny</code> object
     * @return the new <code>DynAny</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynAny create_basic_dyn_any(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a new <code>DynStruct</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynStruct</code> object
     * @return the new <code>DynStruct</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynStruct create_dyn_struct(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a new <code>DynSequence</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynSequence</code> object
     * @return the new <code>DynSequence</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynSequence create_dyn_sequence(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    /**
     * Creates a new <code>DynArray</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynArray</code> object
     * @return the new <code>DynArray</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynArray create_dyn_array(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a new <code>DynUnion</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynUnion</code> object
     * @return the new <code>DynUnion</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynUnion create_dyn_union(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a new <code>DynEnum</code> object from the given
     * <code>TypeCode</code> object.
     * <P>
     * @param type the <code>TypeCode</code> object from which to create a new
     *        <code>DynEnum</code> object
     * @return the new <code>DynEnum</code> object created from the given
     *         <code>TypeCode</code> object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         <code>TypeCode</code> object is not consistent with the operation.
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     * @deprecated Use the new <a href="../DynamicAny/DynAnyFactory.html">DynAnyFactory</a> API instead
     */
    @Deprecated
    public org.omg.CORBA.DynEnum create_dyn_enum(org.omg.CORBA.TypeCode type) throws org.omg.CORBA.ORBPackage.InconsistentTypeCode
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
    * Can be invoked to create new instances of policy objects
    * of a specific type with specified initial state. If
    * <tt>create_policy</tt> fails to instantiate a new Policy
    * object due to its inability to interpret the requested type
    * and content of the policy, it raises the <tt>PolicyError</tt>
    * exception with the appropriate reason.
    * @param type the <tt>PolicyType</tt> of the policy object to
    *        be created
    * @param val the value that will be used to set the initial
    *        state of the <tt>Policy</tt> object that is created
    * @return Reference to a newly created <tt>Policy</tt> object
    *        of type specified by the <tt>type</tt> parameter and
    *        initialized to a state specified by the <tt>val</tt>
    *        parameter
    * @throws <tt>org.omg.CORBA.PolicyError</tt> when the requested
    *        policy is not supported or a requested initial state
    *        for the policy is not supported.
    */
    public org.omg.CORBA.Policy create_policy(int type, org.omg.CORBA.Any val)
        throws org.omg.CORBA.PolicyError
    {
        // Currently not implemented until PIORB.
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
}
