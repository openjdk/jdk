/*
 * Copyright (c) 1995, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.omg.CORBA.ORBPackage.InvalidName;

import java.util.Properties;
import java.applet.Applet;
import java.io.File;
import java.io.FileInputStream;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A class providing APIs for the CORBA Object Request Broker
 * features.  The {@code ORB} class also provides
 * "pluggable ORB implementation" APIs that allow another vendor's ORB
 * implementation to be used.
 * <P>
 * An ORB makes it possible for CORBA objects to communicate
 * with each other by connecting objects making requests (clients) with
 * objects servicing requests (servers).
 * <P>
 *
 * The {@code ORB} class, which
 * encapsulates generic CORBA functionality, does the following:
 * (Note that items 5 and 6, which include most of the methods in
 * the class {@code ORB}, are typically used with the
 * {@code Dynamic Invocation Interface} (DII) and
 * the {@code Dynamic Skeleton Interface} (DSI).
 * These interfaces may be used by a developer directly, but
 * most commonly they are used by the ORB internally and are
 * not seen by the general programmer.)
 * <OL>
 * <li> initializes the ORB implementation by supplying values for
 *      predefined properties and environmental parameters
 * <li> obtains initial object references to services such as
 * the NameService using the method {@code resolve_initial_references}
 * <li> converts object references to strings and back
 * <li> connects the ORB to a servant (an instance of a CORBA object
 * implementation) and disconnects the ORB from a servant
 * <li> creates objects such as
 *   <ul>
 *   <li>{@code TypeCode}
 *   <li>{@code Any}
 *   <li>{@code NamedValue}
 *   <li>{@code Context}
 *   <li>{@code Environment}
 *   <li>lists (such as {@code NVList}) containing these objects
 *   </ul>
 * <li> sends multiple messages in the DII
 * </OL>
 *
 * <P>
 * The {@code ORB} class can be used to obtain references to objects
 * implemented anywhere on the network.
 * <P>
 * An application or applet gains access to the CORBA environment
 * by initializing itself into an {@code ORB} using one of
 * three {@code init} methods.  Two of the three methods use the properties
 * (associations of a name with a value) shown in the
 * table below.<BR>
 * <TABLE class="striped">
 * <CAPTION>Standard Java CORBA Properties</CAPTION>
 * <thead>
 * <TR><TH scope="col">Property Name</TH>   <TH scope="col">Property Value</TH></TR>
 * </thead>
 * <tbody style="text-align:left">
 *     <TR><TH scope="row">org.omg.CORBA.ORBClass</TH>
 *     <TD>class name of an ORB implementation</TD></TR>
 *     <TR><TH scope="row">org.omg.CORBA.ORBSingletonClass</TH>
 *     <TD>class name of the ORB returned by {@code init()}</TD></TR>
 * </tbody>
 * </TABLE>
 * <P>
 * These properties allow a different vendor's {@code ORB}
 * implementation to be "plugged in."
 * <P>
 * When an ORB instance is being created, the class name of the ORB
 * implementation is located using
 * the following standard search order:
 *
 * <OL>
 *     <LI>check in Applet parameter or application string array, if any
 *
 *     <LI>check in properties parameter, if any
 *
 *     <LI>check in the System properties, if any
 *
 *     <LI>check in the orb.properties file located in the user.home
 *         directory, if any
 *
 *     <LI>check in the orb.properties file located in the run-time image,
 *         if any
 *
 *     <LI>fall back on a hardcoded default behavior (use the Java&nbsp;IDL
 *         implementation)
 * </OL>
 * <P>
 * Note that Java&nbsp;IDL provides a default implementation for the
 * fully-functional ORB and for the Singleton ORB.  When the method
 * {@code init} is given no parameters, the default Singleton
 * ORB is returned.  When the method {@code init} is given parameters
 * but no ORB class is specified, the Java&nbsp;IDL ORB implementation
 * is returned.
 * <P>
 * The following code fragment creates an {@code ORB} object
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
 * The following code fragment creates an {@code ORB} object
 * for an application.  The parameter {@code args}
 * represents the arguments supplied to the application's {@code main}
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
 * The following code fragment creates an {@code ORB} object
 * for the applet supplied as the first parameter.  If the given
 * applet does not specify an ORB class, the new ORB will be
 * initialized with the default Java&nbsp;IDL implementation.
 * <PRE>
 *    ORB orb = ORB.init(myApplet, null);
 * </PRE>
 * <P>
 * An application or applet can be initialized in one or more ORBs.
 * ORB initialization is a bootstrap call into the CORBA world.
 *
 *
 * @implNote
 * When a singleton ORB is configured via the system property,
 * or orb.properties, it will be
 * located, and loaded via the system class loader.
 * Thus, where appropriate, it is necessary that
 * the classes for this alternative ORBSingleton are available on the application's class path.
 * It should be noted that the singleton ORB is system wide.
 * <P>
 * When a per-application ORB is created via the 2-arg init methods,
 * then it will be located using the thread context class loader.
 * <P>
 * The IDL to Java Language OMG specification documents the ${java.home}/lib directory as the location,
 * in the Java run-time image, to search for orb.properties.
 * This location is not intended for user editable configuration files.
 * Therefore, the implementation first checks the ${java.home}/conf directory for orb.properties,
 * and thereafter the ${java.home}/lib directory.
 *
 * <p>See also {@extLink idl_guides IDL developer's guide}.</p>
 *
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

    // check that access to the class is not restricted by the security manager.
    private static void checkPackageAccess(String name) {
        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            String cname = name.replace('/', '.');
            if (cname.startsWith("[")) {
                int b = cname.lastIndexOf('[') + 2;
                if (b > 1 && b < cname.length()) {
                    cname = cname.substring(b);
                }
            }
            int i = cname.lastIndexOf('.');
            if (i != -1) {
                s.checkPackageAccess(cname.substring(0, i));
            }
        }
    }

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

                    fileName = javaHome + File.separator + "conf"
                            + File.separator + "orb.properties";
                    props = getFileProperties(fileName);

                    if (props != null) {
                        String value = props.getProperty(name);
                        if (value != null)
                            return value;
                    }

                    fileName = javaHome + File.separator + "lib"
                            + File.separator + "orb.properties";
                    props = getFileProperties(fileName);

                    if (props == null)
                        return null;
                    else
                        return props.getProperty(name);
                }
            }
        );

        return propValue;
    }

    /**
     * Returns the {@code ORB} singleton object. This method always returns the
     * same ORB instance, which is an instance of the class described by the
     * {@code org.omg.CORBA.ORBSingletonClass} system property.
     * <P>
     * This no-argument version of the method {@code init} is used primarily
     * as a factory for {@code TypeCode} objects, which are used by
     * {@code Helper} classes to implement the method {@code type}.
     * It is also used to create {@code Any} objects that are used to
     * describe {@code union} labels (as part of creating a
     * {@code TypeCode} object for a {@code union}).
     * <P>
     * This method is not intended to be used by applets, and in the event
     * that it is called in an applet environment, the ORB it returns
     * is restricted so that it can be used only as a factory for
     * {@code TypeCode} objects.  Any {@code TypeCode} objects
     * it produces can be safely shared among untrusted applets.
     * <P>
     * If an ORB is created using this method from an applet,
     * a system exception will be thrown if
     * methods other than those for
     * creating {@code TypeCode} objects are invoked.
     *
     * @return the singleton ORB
     *
     * @implNote
     * When configured via the system property, or orb.properties,
     * the system-wide singleton ORB is located via the
     * system class loader.
     */
    public static synchronized ORB init() {
        if (singleton == null) {
            String className = getSystemProperty(ORBSingletonClassKey);
            if (className == null)
                className = getPropertyFromFile(ORBSingletonClassKey);
            if ((className == null) ||
                    (className.equals("com.sun.corba.se.impl.orb.ORBSingleton"))) {
                singleton = new com.sun.corba.se.impl.orb.ORBSingleton();
            } else {
                singleton = create_impl_with_systemclassloader(className);
            }
        }
        return singleton;
    }

   private static ORB create_impl_with_systemclassloader(String className) {

        try {
            checkPackageAccess(className);
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Class<org.omg.CORBA.ORB> orbBaseClass = org.omg.CORBA.ORB.class;
            Class<?> singletonOrbClass = Class.forName(className, true, cl).asSubclass(orbBaseClass);
            return (ORB)singletonOrbClass.newInstance();
        } catch (Throwable ex) {
            SystemException systemException = new INITIALIZE(
                "can't instantiate default ORB implementation " + className);
            systemException.initCause(ex);
            throw systemException;
        }
    }

    private static ORB create_impl(String className) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = ClassLoader.getSystemClassLoader();

        try {
            checkPackageAccess(className);
            Class<org.omg.CORBA.ORB> orbBaseClass = org.omg.CORBA.ORB.class;
            Class<?> orbClass = Class.forName(className, true, cl).asSubclass(orbBaseClass);
            return (ORB)orbClass.newInstance();
        } catch (Throwable ex) {
            SystemException systemException = new INITIALIZE(
               "can't instantiate default ORB implementation " + className);
            systemException.initCause(ex);
            throw systemException;
        }
    }

    /**
     * Creates a new {@code ORB} instance for a standalone
     * application.  This method may be called from applications
     * only and returns a new fully functional {@code ORB} object
     * each time it is called.
     * @param args command-line arguments for the application's {@code main}
     *             method; may be {@code null}
     * @param props application-specific properties; may be {@code null}
     * @return the newly-created ORB instance
     *
     * @implNote
     * When configured via the system property, or orb.properties,
     * the ORB is located via the thread context class loader.
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
        if ((className == null) ||
                    (className.equals("com.sun.corba.se.impl.orb.ORBImpl"))) {
            orb = new com.sun.corba.se.impl.orb.ORBImpl();
        } else {
            orb = create_impl(className);
        }
        orb.set_parameters(args, props);
        return orb;
    }


    /**
     * Creates a new {@code ORB} instance for an applet.  This
     * method may be called from applets only and returns a new
     * fully-functional {@code ORB} object each time it is called.
     * @param app the applet; may be {@code null}
     * @param props applet-specific properties; may be {@code null}
     * @return the newly-created ORB instance
     *
     * @implNote
     * When configured via the system property, or orb.properties,
     * the ORB is located via the thread context class loader.
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
        if ((className == null) ||
                    (className.equals("com.sun.corba.se.impl.orb.ORBImpl"))) {
            orb = new com.sun.corba.se.impl.orb.ORBImpl();
        } else {
            orb = create_impl(className);
        }
        orb.set_parameters(app, props);
        return orb;
    }

    /**
     * Allows the ORB implementation to be initialized with the given
     * parameters and properties. This method, used in applications only,
     * is implemented by subclass ORB implementations and called
     * by the appropriate {@code init} method to pass in its parameters.
     *
     * @param args command-line arguments for the application's {@code main}
     *             method; may be {@code null}
     * @param props application-specific properties; may be {@code null}
     */
    abstract protected void set_parameters(String[] args, Properties props);

    /**
     * Allows the ORB implementation to be initialized with the given
     * applet and parameters. This method, used in applets only,
     * is implemented by subclass ORB implementations and called
     * by the appropriate {@code init} method to pass in its parameters.
     *
     * @param app the applet; may be {@code null}
     * @param props applet-specific properties; may be {@code null}
     */
    abstract protected void set_parameters(Applet app, Properties props);

    /**
     * Connects the given servant object (a Java object that is
     * an instance of the server implementation class)
     * to the ORB. The servant class must
     * extend the {@code ImplBase} class corresponding to the interface that is
     * supported by the server. The servant must thus be a CORBA object
     * reference, and inherit from {@code org.omg.CORBA.Object}.
     * Servants created by the user can start receiving remote invocations
     * after the method {@code connect} has been called. A servant may also be
     * automatically and implicitly connected to the ORB if it is passed as
     * an IDL parameter in an IDL method invocation on a non-local object,
     * that is, if the servant object has to be marshalled and sent outside of the
     * process address space.
     * <P>
     * Calling the method {@code connect} has no effect
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
     * {@code OBJECT_NOT_EXIST} exception.
     * Once an ORB has been destroyed, another call to {@code init}
     * with the same ORBid will return a reference to a newly constructed ORB.<p>
     * If {@code destroy} is called on an ORB that has not been shut down,
     * it will start the shut down process and block until the ORB has shut down
     * before it destroys the ORB.<br>
     * If an application calls {@code destroy} in a thread that is currently servicing
     * an invocation, the {@code BAD_INV_ORDER} system exception will be thrown
     * with the OMG minor code 3, since blocking would result in a deadlock.<p>
     * For maximum portability and to avoid resource leaks, an application should
     * always call {@code shutdown} and {@code destroy}
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
     * {@code org.omg.CORBA.OBJECT_NOT_EXIST} back to the
     * remote client. Thus the object appears to be destroyed from the
     * point of view of remote clients. Note, however, that local requests issued
     * using the servant  directly do not
     * pass through the ORB; hence, they will continue to be processed by the
     * servant.
     * <P>
     * Calling the method {@code disconnect} has no effect
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
     * @return an array of {@code String} objects that represent
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
     * The resulting {@code String} object may be stored or communicated
     * in any way that a {@code String} object can be manipulated.
     *
     * @param obj the object reference to stringify
     * @return the string representing the object reference
     */
    abstract public String object_to_string(org.omg.CORBA.Object obj);

    /**
     * Converts a string produced by the method {@code object_to_string}
     * back to a CORBA object reference.
     *
     * @param str the string to be converted back to an object reference.  It must
     * be the result of converting an object reference to a string using the
     * method {@code object_to_string}.
     * @return the object reference
     */
    abstract public org.omg.CORBA.Object string_to_object(String str);

    /**
     * Allocates an {@code NVList} with (probably) enough
     * space for the specified number of {@code NamedValue} objects.
     * Note that the specified size is only a hint to help with
     * storage allocation and does not imply the maximum size of the list.
     *
     * @param count  suggested number of {@code NamedValue} objects for
     *               which to allocate space
     * @return the newly-created {@code NVList}
     *
     * @see NVList
     */
    abstract public NVList create_list(int count);

    /**
     * Creates an {@code NVList} initialized with argument
     * descriptions for the operation described in the given
     * {@code OperationDef} object.  This {@code OperationDef} object
     * is obtained from an Interface Repository. The arguments in the
     * returned {@code NVList} object are in the same order as in the
     * original IDL operation definition, which makes it possible for the list
     * to be used in dynamic invocation requests.
     *
     * @param oper      the {@code OperationDef} object to use to create the list
     * @return          a newly-created {@code NVList} object containing
     * descriptions of the arguments to the method described in the given
     * {@code OperationDef} object
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
            Class<?> opDefClass = null;

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if ( cl == null )
                cl = ClassLoader.getSystemClassLoader();
            // if this throws a ClassNotFoundException, it will be caught below.
            opDefClass = Class.forName(opDefClassName, true, cl);

            // OK, we loaded OperationDef. Now try to get the
            // create_operation_list(OperationDef oper) method.
            Class<?>[] argc = { opDefClass };
            java.lang.reflect.Method meth =
                this.getClass().getMethod("create_operation_list", argc);

            // OK, the method exists, so invoke it and be happy.
            java.lang.Object[] argx = { oper };
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
     * Creates a {@code NamedValue} object
     * using the given name, value, and argument mode flags.
     * <P>
     * A {@code NamedValue} object serves as (1) a parameter or return
     * value or (2) a context property.
     * It may be used by itself or
     * as an element in an {@code NVList} object.
     *
     * @param s  the name of the {@code NamedValue} object
     * @param any  the {@code Any} value to be inserted into the
     *             {@code NamedValue} object
     * @param flags  the argument mode flags for the {@code NamedValue}: one of
     * {@code ARG_IN.value}, {@code ARG_OUT.value},
     * or {@code ARG_INOUT.value}.
     *
     * @return  the newly-created {@code NamedValue} object
     * @see NamedValue
     */
    abstract public NamedValue create_named_value(String s, Any any, int flags);

    /**
     * Creates an empty {@code ExceptionList} object.
     *
     * @return  the newly-created {@code ExceptionList} object
     */
    abstract public ExceptionList create_exception_list();

    /**
     * Creates an empty {@code ContextList} object.
     *
     * @return  the newly-created {@code ContextList} object
     * @see ContextList
     * @see Context
     */
    abstract public ContextList create_context_list();

    /**
     * Gets the default {@code Context} object.
     *
     * @return the default {@code Context} object
     * @see Context
     */
    abstract public Context get_default_context();

    /**
     * Creates an {@code Environment} object.
     *
     * @return  the newly-created {@code Environment} object
     * @see Environment
     */
    abstract public Environment create_environment();

    /**
     * Creates a new {@code org.omg.CORBA.portable.OutputStream} into which
     * IDL method parameters can be marshalled during method invocation.
     * @return  the newly-created
     *          {@code org.omg.CORBA.portable.OutputStream} object
     */
    abstract public org.omg.CORBA.portable.OutputStream create_output_stream();

    /**
     * Sends multiple dynamic (DII) requests asynchronously without expecting
     * any responses. Note that oneway invocations are not guaranteed to
     * reach the server.
     *
     * @param req  an array of request objects
     */
    abstract public void send_multiple_requests_oneway(Request[] req);

    /**
     * Sends multiple dynamic (DII) requests asynchronously.
     *
     * @param req  an array of {@code Request} objects
     */
    abstract public void send_multiple_requests_deferred(Request[] req);

    /**
     * Finds out if any of the deferred (asynchronous) invocations have
     * a response yet.
     * @return {@code true} if there is a response available;
     *         {@code false} otherwise
     */
    abstract public boolean poll_next_response();

    /**
     * Gets the next {@code Request} instance for which a response
     * has been received.
     *
     * @return the next {@code Request} object ready with a response
     * @exception WrongTransaction if the method {@code get_next_response}
     * is called from a transaction scope different
     * from the one from which the original request was sent. See the
     * OMG Transaction Service specification for details.
     */
    abstract public Request get_next_response() throws WrongTransaction;

    /**
     * Retrieves the {@code TypeCode} object that represents
     * the given primitive IDL type.
     *
     * @param tcKind    the {@code TCKind} instance corresponding to the
     *                  desired primitive type
     * @return          the requested {@code TypeCode} object
     */
    abstract public TypeCode get_primitive_tc(TCKind tcKind);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code struct}.
     * The {@code TypeCode} object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the {@code struct}
     * @param name      the name of the {@code struct}
     * @param members   an array describing the members of the {@code struct}
     * @return          a newly-created {@code TypeCode} object describing
     *                  an IDL {@code struct}
     */
    abstract public TypeCode create_struct_tc(String id, String name,
                                              StructMember[] members);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code union}.
     * The {@code TypeCode} object is initialized with the given id,
     * name, discriminator type, and members.
     *
     * @param id        the repository id of the {@code union}
     * @param name      the name of the {@code union}
     * @param discriminator_type        the type of the {@code union} discriminator
     * @param members   an array describing the members of the {@code union}
     * @return          a newly-created {@code TypeCode} object describing
     *                  an IDL {@code union}
     */
    abstract public TypeCode create_union_tc(String id, String name,
                                             TypeCode discriminator_type,
                                             UnionMember[] members);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code enum}.
     * The {@code TypeCode} object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the {@code enum}
     * @param name      the name for the {@code enum}
     * @param members   an array describing the members of the {@code enum}
     * @return          a newly-created {@code TypeCode} object describing
     *                  an IDL {@code enum}
     */
    abstract public TypeCode create_enum_tc(String id, String name, String[] members);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code alias}
     * ({@code typedef}).
     * The {@code TypeCode} object is initialized with the given id,
     * name, and original type.
     *
     * @param id        the repository id for the alias
     * @param name      the name for the alias
     * @param original_type
     *                  the {@code TypeCode} object describing the original type
     *                  for which this is an alias
     * @return          a newly-created {@code TypeCode} object describing
     *                  an IDL {@code alias}
     */
    abstract public TypeCode create_alias_tc(String id, String name,
                                             TypeCode original_type);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code exception}.
     * The {@code TypeCode} object is initialized with the given id,
     * name, and members.
     *
     * @param id        the repository id for the {@code exception}
     * @param name      the name for the {@code exception}
     * @param members   an array describing the members of the {@code exception}
     * @return          a newly-created {@code TypeCode} object describing
     *                  an IDL {@code exception}
     */
    abstract public TypeCode create_exception_tc(String id, String name,
                                                 StructMember[] members);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code interface}.
     * The {@code TypeCode} object is initialized with the given id
     * and name.
     *
     * @param id    the repository id for the interface
     * @param name  the name for the interface
     * @return      a newly-created {@code TypeCode} object describing
     *              an IDL {@code interface}
     */

    abstract public TypeCode create_interface_tc(String id, String name);

    /**
     * Creates a {@code TypeCode} object representing a bounded IDL
     * {@code string}.
     * The {@code TypeCode} object is initialized with the given bound,
     * which represents the maximum length of the string. Zero indicates
     * that the string described by this type code is unbounded.
     *
     * @param bound the bound for the {@code string}; cannot be negative
     * @return      a newly-created {@code TypeCode} object describing
     *              a bounded IDL {@code string}
     * @exception BAD_PARAM if bound is a negative value
     */

    abstract public TypeCode create_string_tc(int bound);

    /**
     * Creates a {@code TypeCode} object representing a bounded IDL
     * {@code wstring} (wide string).
     * The {@code TypeCode} object is initialized with the given bound,
     * which represents the maximum length of the wide string. Zero indicates
     * that the string described by this type code is unbounded.
     *
     * @param bound the bound for the {@code wstring}; cannot be negative
     * @return      a newly-created {@code TypeCode} object describing
     *              a bounded IDL {@code wstring}
     * @exception BAD_PARAM if bound is a negative value
     */
    abstract public TypeCode create_wstring_tc(int bound);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code sequence}.
     * The {@code TypeCode} object is initialized with the given bound and
     * element type.
     *
     * @param bound     the bound for the {@code sequence}, 0 if unbounded
     * @param element_type the {@code TypeCode} object describing
     *        the elements contained in the {@code sequence}
     *
     * @return  a newly-created {@code TypeCode} object describing
     *          an IDL {@code sequence}
     */
    abstract public TypeCode create_sequence_tc(int bound, TypeCode element_type);

    /**
     * Creates a {@code TypeCode} object representing a
     * a recursive IDL {@code sequence}.
     * <P>
     * For the IDL {@code struct} Node in following code fragment,
     * the offset parameter for creating its sequence would be 1:
     * <PRE>
     *    Struct Node {
     *        long value;
     *        Sequence &lt;Node&gt; subnodes;
     *    };
     * </PRE>
     *
     * @param bound     the bound for the sequence, 0 if unbounded
     * @param offset    the index to the enclosing {@code TypeCode} object
     *                  that describes the elements of this sequence
     * @return          a newly-created {@code TypeCode} object describing
     *                  a recursive sequence
     * @deprecated Use a combination of create_recursive_tc and create_sequence_tc instead
     * @see #create_recursive_tc(String) create_recursive_tc
     * @see #create_sequence_tc(int, TypeCode) create_sequence_tc
     */
    @Deprecated
    abstract public TypeCode create_recursive_sequence_tc(int bound, int offset);

    /**
     * Creates a {@code TypeCode} object representing an IDL {@code array}.
     * The {@code TypeCode} object is initialized with the given length and
     * element type.
     *
     * @param length    the length of the {@code array}
     * @param element_type  a {@code TypeCode} object describing the type
     *                      of element contained in the {@code array}
     * @return  a newly-created {@code TypeCode} object describing
     *          an IDL {@code array}
     */
    abstract public TypeCode create_array_tc(int length, TypeCode element_type);

    /**
     * Create a {@code TypeCode} object for an IDL native type.
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
     * Create a {@code TypeCode} object for an IDL abstract interface.
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
     * Create a {@code TypeCode} object for an IDL fixed type.
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
     * Create a {@code TypeCode} object for an IDL value type.
     * The concrete_base parameter is the TypeCode for the immediate
     * concrete valuetype base of the valuetype for which the TypeCode
     * is being created.
     * It may be null if the valuetype does not have a concrete base.
     *
     * @param id                 the logical id for the value type.
     * @param name               the name of the value type.
     * @param type_modifier      one of the value type modifier constants:
     *                           VM_NONE, VM_CUSTOM, VM_ABSTRACT or VM_TRUNCATABLE
     * @param concrete_base      a {@code TypeCode} object
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
     * Create a recursive {@code TypeCode} object which
     * serves as a placeholder for a concrete TypeCode during the process of creating
     * TypeCodes which contain recursion. The id parameter specifies the repository id of
     * the type for which the recursive TypeCode is serving as a placeholder. Once the
     * recursive TypeCode has been properly embedded in the enclosing TypeCode which
     * corresponds to the specified repository id, it will function as a normal TypeCode.
     * Invoking operations on the recursive TypeCode before it has been embedded in the
     * enclosing TypeCode will result in a {@code BAD_TYPECODE} exception.
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
     *
     * @param id                 the logical id of the referenced type
     * @return                   the requested TypeCode
     */
    public org.omg.CORBA.TypeCode create_recursive_tc(String id) {
        // implemented in subclass
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Creates a {@code TypeCode} object for an IDL value box.
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
     * Creates an IDL {@code Any} object initialized to
     * contain a {@code Typecode} object whose {@code kind} field
     * is set to {@code TCKind.tc_null}.
     *
     * @return          a newly-created {@code Any} object
     */
    abstract public Any create_any();




    /**
     * Retrieves a {@code Current} object.
     * The {@code Current} interface is used to manage thread-specific
     * information for use by services such as transactions and security.
     *
     * @see <a href="package-summary.html#unimpl"><code>CORBA</code> package
     *      comments for unimplemented features</a>
     *
     * @return          a newly-created {@code Current} object
     * @deprecated      use {@code resolve_initial_references}.
     */
    @Deprecated
    public org.omg.CORBA.Current get_current()
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * This operation blocks the current thread until the ORB has
     * completed the shutdown process, initiated when some thread calls
     * {@code shutdown}. It may be used by multiple threads which
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
     * If the {@code wait_for_completion} parameter
     * is true, this operation blocks until all ORB processing (including
     * processing of currently executing requests, object deactivation,
     * and other object adapter operations) has completed.
     * If an application does this in a thread that is currently servicing
     * an invocation, the {@code BAD_INV_ORDER} system exception
     * will be thrown with the OMG minor code 3,
     * since blocking would result in a deadlock.<br>
     * If the {@code wait_for_completion} parameter is {@code FALSE},
     * then shutdown may not have completed upon return.<p>
     * While the ORB is in the process of shutting down, the ORB operates as normal,
     * servicing incoming and outgoing requests until all requests have been completed.
     * Once an ORB has shutdown, only object reference management operations
     * may be invoked on the ORB or any object reference obtained from it.
     * An application may also invoke the {@code destroy} operation on the ORB itself.
     * Invoking any other operation will throw the {@code BAD_INV_ORDER}
     * system exception with the OMG minor code 4.<p>
     * The {@code ORB.run} method will return after
     * {@code shutdown} has been called.
     *
     * @param wait_for_completion {@code true} if the call
     *        should block until the shutdown is complete;
     *        {@code false} if it should return immediately
     * @throws org.omg.CORBA.BAD_INV_ORDER if the current thread is servicing
     *         an invocation
     */
    public void shutdown(boolean wait_for_completion)
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Returns {@code true} if the ORB needs the main thread to
     * perform some work, and {@code false} if the ORB does not
     * need the main thread.
     *
     * @return {@code true} if there is work pending, meaning that the ORB
     *         needs the main thread to perform some work; {@code false}
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
     * The methods {@code work_pending} and {@code perform_work}
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
     * parameter {@code service_type}, the values defined by
     * constants in the CORBA module. If service information is
     * available for that type, that is returned in the out parameter
     * {@code service_info}, and the operation returns the
     * value {@code true}. If no information for the requested
     * services type is available, the operation returns {@code false}
     *  (i.e., the service is not supported by this ORB).
     *
     * @param service_type a {@code short} indicating the
     *        service type for which information is being requested
     * @param service_info a {@code ServiceInformationHolder} object
     *        that will hold the {@code ServiceInformation} object
     *        produced by this method
     * @return {@code true} if service information is available
     *        for the {@code service_type};
     *        {@code false} if no information for the
     *        requested services type is available
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
     * Creates a new {@code DynAny} object from the given
     * {@code Any} object.
     *
     * @param value the {@code Any} object from which to create a new
     *        {@code DynAny} object
     * @return the new {@code DynAny} object created from the given
     *         {@code Any} object
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
     * Creates a basic {@code DynAny} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynAny} object
     * @return the new {@code DynAny} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
     * Creates a new {@code DynStruct} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynStruct} object
     * @return the new {@code DynStruct} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
     * Creates a new {@code DynSequence} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynSequence} object
     * @return the new {@code DynSequence} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
     * Creates a new {@code DynArray} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynArray} object
     * @return the new {@code DynArray} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
     * Creates a new {@code DynUnion} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynUnion} object
     * @return the new {@code DynUnion} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
     * Creates a new {@code DynEnum} object from the given
     * {@code TypeCode} object.
     *
     * @param type the {@code TypeCode} object from which to create a new
     *        {@code DynEnum} object
     * @return the new {@code DynEnum} object created from the given
     *         {@code TypeCode} object
     * @throws org.omg.CORBA.ORBPackage.InconsistentTypeCode if the given
     *         {@code TypeCode} object is not consistent with the operation.
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
    * {@code create_policy} fails to instantiate a new Policy
    * object due to its inability to interpret the requested type
    * and content of the policy, it raises the {@code PolicyError}
    * exception with the appropriate reason.
    * @param type the {@code PolicyType} of the policy object to
    *        be created
    * @param val the value that will be used to set the initial
    *        state of the {@code Policy} object that is created
    * @return Reference to a newly created {@code Policy} object
    *        of type specified by the {@code type} parameter and
    *        initialized to a state specified by the {@code val}
    *        parameter
    * @throws org.omg.CORBA.PolicyError when the requested
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
