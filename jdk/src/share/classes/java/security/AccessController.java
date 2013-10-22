/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import sun.security.util.Debug;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * <p> The AccessController class is used for access control operations
 * and decisions.
 *
 * <p> More specifically, the AccessController class is used for
 * three purposes:
 *
 * <ul>
 * <li> to decide whether an access to a critical system
 * resource is to be allowed or denied, based on the security policy
 * currently in effect,<p>
 * <li>to mark code as being "privileged", thus affecting subsequent
 * access determinations, and<p>
 * <li>to obtain a "snapshot" of the current calling context so
 * access-control decisions from a different context can be made with
 * respect to the saved context. </ul>
 *
 * <p> The {@link #checkPermission(Permission) checkPermission} method
 * determines whether the access request indicated by a specified
 * permission should be granted or denied. A sample call appears
 * below. In this example, {@code checkPermission} will determine
 * whether or not to grant "read" access to the file named "testFile" in
 * the "/temp" directory.
 *
 * <pre>
 *
 * FilePermission perm = new FilePermission("/temp/testFile", "read");
 * AccessController.checkPermission(perm);
 *
 * </pre>
 *
 * <p> If a requested access is allowed,
 * {@code checkPermission} returns quietly. If denied, an
 * AccessControlException is
 * thrown. AccessControlException can also be thrown if the requested
 * permission is of an incorrect type or contains an invalid value.
 * Such information is given whenever possible.
 *
 * Suppose the current thread traversed m callers, in the order of caller 1
 * to caller 2 to caller m. Then caller m invoked the
 * {@code checkPermission} method.
 * The {@code checkPermission} method determines whether access
 * is granted or denied based on the following algorithm:
 *
 *  <pre> {@code
 * for (int i = m; i > 0; i--) {
 *
 *     if (caller i's domain does not have the permission)
 *         throw AccessControlException
 *
 *     else if (caller i is marked as privileged) {
 *         if (a context was specified in the call to doPrivileged)
 *             context.checkPermission(permission)
 *         if (limited permissions were specified in the call to doPrivileged) {
 *             for (each limited permission) {
 *                 if (the limited permission implies the requested permission)
 *                     return;
 *             }
 *         } else
 *             return;
 *     }
 * }
 *
 * // Next, check the context inherited when the thread was created.
 * // Whenever a new thread is created, the AccessControlContext at
 * // that time is stored and associated with the new thread, as the
 * // "inherited" context.
 *
 * inheritedContext.checkPermission(permission);
 * }</pre>
 *
 * <p> A caller can be marked as being "privileged"
 * (see {@link #doPrivileged(PrivilegedAction) doPrivileged} and below).
 * When making access control decisions, the {@code checkPermission}
 * method stops checking if it reaches a caller that
 * was marked as "privileged" via a {@code doPrivileged}
 * call without a context argument (see below for information about a
 * context argument). If that caller's domain has the
 * specified permission and at least one limiting permission argument (if any)
 * implies the requested permission, no further checking is done and
 * {@code checkPermission}
 * returns quietly, indicating that the requested access is allowed.
 * If that domain does not have the specified permission, an exception
 * is thrown, as usual. If the caller's domain had the specified permission
 * but it was not implied by any limiting permission arguments given in the call
 * to {@code doPrivileged} then the permission checking continues
 * until there are no more callers or another {@code doPrivileged}
 * call matches the requested permission and returns normally.
 *
 * <p> The normal use of the "privileged" feature is as follows. If you
 * don't need to return a value from within the "privileged" block, do
 * the following:
 *
 *  <pre> {@code
 * somemethod() {
 *     ...normal code here...
 *     AccessController.doPrivileged(new PrivilegedAction<Void>() {
 *         public Void run() {
 *             // privileged code goes here, for example:
 *             System.loadLibrary("awt");
 *             return null; // nothing to return
 *         }
 *     });
 *     ...normal code here...
 * }}</pre>
 *
 * <p>
 * PrivilegedAction is an interface with a single method, named
 * {@code run}.
 * The above example shows creation of an implementation
 * of that interface; a concrete implementation of the
 * {@code run} method is supplied.
 * When the call to {@code doPrivileged} is made, an
 * instance of the PrivilegedAction implementation is passed
 * to it. The {@code doPrivileged} method calls the
 * {@code run} method from the PrivilegedAction
 * implementation after enabling privileges, and returns the
 * {@code run} method's return value as the
 * {@code doPrivileged} return value (which is
 * ignored in this example).
 *
 * <p> If you need to return a value, you can do something like the following:
 *
 *  <pre> {@code
 * somemethod() {
 *     ...normal code here...
 *     String user = AccessController.doPrivileged(
 *         new PrivilegedAction<String>() {
 *         public String run() {
 *             return System.getProperty("user.name");
 *             }
 *         });
 *     ...normal code here...
 * }}</pre>
 *
 * <p>If the action performed in your {@code run} method could
 * throw a "checked" exception (those listed in the {@code throws} clause
 * of a method), then you need to use the
 * {@code PrivilegedExceptionAction} interface instead of the
 * {@code PrivilegedAction} interface:
 *
 *  <pre> {@code
 * somemethod() throws FileNotFoundException {
 *     ...normal code here...
 *     try {
 *         FileInputStream fis = AccessController.doPrivileged(
 *         new PrivilegedExceptionAction<FileInputStream>() {
 *             public FileInputStream run() throws FileNotFoundException {
 *                 return new FileInputStream("someFile");
 *             }
 *         });
 *     } catch (PrivilegedActionException e) {
 *         // e.getException() should be an instance of FileNotFoundException,
 *         // as only "checked" exceptions will be "wrapped" in a
 *         // PrivilegedActionException.
 *         throw (FileNotFoundException) e.getException();
 *     }
 *     ...normal code here...
 *  }}</pre>
 *
 * <p> Be *very* careful in your use of the "privileged" construct, and
 * always remember to make the privileged code section as small as possible.
 * You can pass {@code Permission} arguments to further limit the
 * scope of the "privilege" (see below).
 *
 *
 * <p> Note that {@code checkPermission} always performs security checks
 * within the context of the currently executing thread.
 * Sometimes a security check that should be made within a given context
 * will actually need to be done from within a
 * <i>different</i> context (for example, from within a worker thread).
 * The {@link #getContext() getContext} method and
 * AccessControlContext class are provided
 * for this situation. The {@code getContext} method takes a "snapshot"
 * of the current calling context, and places
 * it in an AccessControlContext object, which it returns. A sample call is
 * the following:
 *
 * <pre>
 *
 * AccessControlContext acc = AccessController.getContext()
 *
 * </pre>
 *
 * <p>
 * AccessControlContext itself has a {@code checkPermission} method
 * that makes access decisions based on the context <i>it</i> encapsulates,
 * rather than that of the current execution thread.
 * Code within a different context can thus call that method on the
 * previously-saved AccessControlContext object. A sample call is the
 * following:
 *
 * <pre>
 *
 * acc.checkPermission(permission)
 *
 * </pre>
 *
 * <p> There are also times where you don't know a priori which permissions
 * to check the context against. In these cases you can use the
 * doPrivileged method that takes a context. You can also limit the scope
 * of the privileged code by passing additional {@code Permission}
 * parameters.
 *
 *  <pre> {@code
 * somemethod() {
 *     AccessController.doPrivileged(new PrivilegedAction<Object>() {
 *         public Object run() {
 *             // Code goes here. Any permission checks within this
 *             // run method will require that the intersection of the
 *             // caller's protection domain and the snapshot's
 *             // context have the desired permission. If a requested
 *             // permission is not implied by the limiting FilePermission
 *             // argument then checking of the thread continues beyond the
 *             // caller of doPrivileged.
 *         }
 *     }, acc, new FilePermission("/temp/*", read));
 *     ...normal code here...
 * }}</pre>
 * <p> Passing a limiting {@code Permission} argument of an instance of
 * {@code AllPermission} is equivalent to calling the equivalent
 * {@code doPrivileged} method without limiting {@code Permission}
 * arguments. Passing a zero length array of {@code Permission} disables
 * the code privileges so that checking always continues beyond the caller of
 * that {@code doPrivileged} method.
 *
 * @see AccessControlContext
 *
 * @author Li Gong
 * @author Roland Schemers
 */

public final class AccessController {

    /**
     * Don't allow anyone to instantiate an AccessController
     */
    private AccessController() { }

    /**
     * Performs the specified {@code PrivilegedAction} with privileges
     * enabled. The action is performed with <i>all</i> of the permissions
     * possessed by the caller's protection domain.
     *
     * <p> If the action's {@code run} method throws an (unchecked)
     * exception, it will propagate through this method.
     *
     * <p> Note that any DomainCombiner associated with the current
     * AccessControlContext will be ignored while the action is performed.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @param action the action to be performed.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     * @see #doPrivileged(PrivilegedExceptionAction)
     * @see #doPrivilegedWithCombiner(PrivilegedAction)
     * @see java.security.DomainCombiner
     */

    @CallerSensitive
    public static native <T> T doPrivileged(PrivilegedAction<T> action);

    /**
     * Performs the specified {@code PrivilegedAction} with privileges
     * enabled. The action is performed with <i>all</i> of the permissions
     * possessed by the caller's protection domain.
     *
     * <p> If the action's {@code run} method throws an (unchecked)
     * exception, it will propagate through this method.
     *
     * <p> This method preserves the current AccessControlContext's
     * DomainCombiner (which may be null) while the action is performed.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @param action the action to be performed.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see java.security.DomainCombiner
     *
     * @since 1.6
     */
    @CallerSensitive
    public static <T> T doPrivilegedWithCombiner(PrivilegedAction<T> action) {
        AccessControlContext acc = getStackAccessControlContext();
        if (acc == null) {
            return AccessController.doPrivileged(action);
        }
        DomainCombiner dc = acc.getAssignedCombiner();
        return AccessController.doPrivileged(action,
                                             preserveCombiner(dc, Reflection.getCallerClass()));
    }


    /**
     * Performs the specified {@code PrivilegedAction} with privileges
     * enabled and restricted by the specified {@code AccessControlContext}.
     * The action is performed with the intersection of the permissions
     * possessed by the caller's protection domain, and those possessed
     * by the domains represented by the specified {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     * @param action the action to be performed.
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null}, then no additional restriction is applied.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedExceptionAction,AccessControlContext)
     */
    @CallerSensitive
    public static native <T> T doPrivileged(PrivilegedAction<T> action,
                                            AccessControlContext context);


    /**
     * Performs the specified {@code PrivilegedAction} with privileges
     * enabled and restricted by the specified
     * {@code AccessControlContext} and with a privilege scope limited
     * by specified {@code Permission} arguments.
     *
     * The action is performed with the intersection of the permissions
     * possessed by the caller's protection domain, and those possessed
     * by the domains represented by the specified
     * {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     * @param action the action to be performed.
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null},
     *                then no additional restriction is applied.
     * @param perms the {@code Permission} arguments which limit the
     *              scope of the caller's privileges. The number of arguments
     *              is variable.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @throws NullPointerException if action or perms or any element of
     *         perms is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedExceptionAction,AccessControlContext)
     *
     * @since 1.8
     */
    @CallerSensitive
    public static <T> T doPrivileged(PrivilegedAction<T> action,
        AccessControlContext context, Permission... perms) {

        AccessControlContext parent = getContext();
        if (perms == null) {
            throw new NullPointerException("null permissions parameter");
        }
        Class <?> caller = Reflection.getCallerClass();
        return AccessController.doPrivileged(action, createWrapper(null,
            caller, parent, context, perms));
    }


    /**
     * Performs the specified {@code PrivilegedAction} with privileges
     * enabled and restricted by the specified
     * {@code AccessControlContext} and with a privilege scope limited
     * by specified {@code Permission} arguments.
     *
     * The action is performed with the intersection of the permissions
     * possessed by the caller's protection domain, and those possessed
     * by the domains represented by the specified
     * {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * <p> This method preserves the current AccessControlContext's
     * DomainCombiner (which may be null) while the action is performed.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     * @param action the action to be performed.
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null},
     *                then no additional restriction is applied.
     * @param perms the {@code Permission} arguments which limit the
     *              scope of the caller's privileges. The number of arguments
     *              is variable.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @throws NullPointerException if action or perms or any element of
     *         perms is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedExceptionAction,AccessControlContext)
     * @see java.security.DomainCombiner
     *
     * @since 1.8
     */
    @CallerSensitive
    public static <T> T doPrivilegedWithCombiner(PrivilegedAction<T> action,
        AccessControlContext context, Permission... perms) {

        AccessControlContext parent = getContext();
        DomainCombiner dc = parent.getCombiner();
        if (dc == null && context != null) {
            dc = context.getCombiner();
        }
        if (perms == null) {
            throw new NullPointerException("null permissions parameter");
        }
        Class <?> caller = Reflection.getCallerClass();
        return AccessController.doPrivileged(action, createWrapper(dc, caller,
            parent, context, perms));
    }

    /**
     * Performs the specified {@code PrivilegedExceptionAction} with
     * privileges enabled.  The action is performed with <i>all</i> of the
     * permissions possessed by the caller's protection domain.
     *
     * <p> If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     *
     * <p> Note that any DomainCombiner associated with the current
     * AccessControlContext will be ignored while the action is performed.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @param action the action to be performed
     *
     * @return the value returned by the action's {@code run} method
     *
     * @exception PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedExceptionAction,AccessControlContext)
     * @see #doPrivilegedWithCombiner(PrivilegedExceptionAction)
     * @see java.security.DomainCombiner
     */
    @CallerSensitive
    public static native <T> T
        doPrivileged(PrivilegedExceptionAction<T> action)
        throws PrivilegedActionException;


    /**
     * Performs the specified {@code PrivilegedExceptionAction} with
     * privileges enabled.  The action is performed with <i>all</i> of the
     * permissions possessed by the caller's protection domain.
     *
     * <p> If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     *
     * <p> This method preserves the current AccessControlContext's
     * DomainCombiner (which may be null) while the action is performed.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @param action the action to be performed.
     *
     * @return the value returned by the action's {@code run} method
     *
     * @exception PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedExceptionAction,AccessControlContext)
     * @see java.security.DomainCombiner
     *
     * @since 1.6
     */
    @CallerSensitive
    public static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action)
        throws PrivilegedActionException
    {
        AccessControlContext acc = getStackAccessControlContext();
        if (acc == null) {
            return AccessController.doPrivileged(action);
        }
        DomainCombiner dc = acc.getAssignedCombiner();
        return AccessController.doPrivileged(action,
                                             preserveCombiner(dc, Reflection.getCallerClass()));
    }

    /**
     * preserve the combiner across the doPrivileged call
     */
    private static AccessControlContext preserveCombiner(DomainCombiner combiner,
                                                         Class<?> caller)
    {
        return createWrapper(combiner, caller, null, null, null);
    }

    /**
     * Create a wrapper to contain the limited privilege scope data.
     */
    private static AccessControlContext
        createWrapper(DomainCombiner combiner, Class<?> caller,
                      AccessControlContext parent, AccessControlContext context,
                      Permission[] perms)
    {
        ProtectionDomain callerPD = getCallerPD(caller);
        // check if caller is authorized to create context
        if (context != null && !context.isAuthorized() &&
            System.getSecurityManager() != null &&
            !callerPD.impliesCreateAccessControlContext())
        {
            ProtectionDomain nullPD = new ProtectionDomain(null, null);
            return new AccessControlContext(new ProtectionDomain[] { nullPD });
        } else {
            return new AccessControlContext(callerPD, combiner, parent,
                                            context, perms);
        }
    }

    private static ProtectionDomain getCallerPD(final Class <?> caller) {
        ProtectionDomain callerPd = doPrivileged
            (new PrivilegedAction<ProtectionDomain>() {
            public ProtectionDomain run() {
                return caller.getProtectionDomain();
            }
        });

        return callerPd;
    }

    /**
     * Performs the specified {@code PrivilegedExceptionAction} with
     * privileges enabled and restricted by the specified
     * {@code AccessControlContext}.  The action is performed with the
     * intersection of the permissions possessed by the caller's
     * protection domain, and those possessed by the domains represented by the
     * specified {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     * @param action the action to be performed
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null}, then no additional restriction is applied.
     *
     * @return the value returned by the action's {@code run} method
     *
     * @exception PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @exception NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     */
    @CallerSensitive
    public static native <T> T
        doPrivileged(PrivilegedExceptionAction<T> action,
                     AccessControlContext context)
        throws PrivilegedActionException;


    /**
     * Performs the specified {@code PrivilegedExceptionAction} with
     * privileges enabled and restricted by the specified
     * {@code AccessControlContext} and with a privilege scope limited by
     * specified {@code Permission} arguments.
     *
     * The action is performed with the intersection of the permissions
     * possessed by the caller's protection domain, and those possessed
     * by the domains represented by the specified
     * {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     * @param action the action to be performed.
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null},
     *                then no additional restriction is applied.
     * @param perms the {@code Permission} arguments which limit the
     *              scope of the caller's privileges. The number of arguments
     *              is variable.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @throws PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws NullPointerException if action or perms or any element of
     *         perms is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     *
     * @since 1.8
     */
    @CallerSensitive
    public static <T> T doPrivileged(PrivilegedExceptionAction<T> action,
                                     AccessControlContext context, Permission... perms)
        throws PrivilegedActionException
    {
        AccessControlContext parent = getContext();
        if (perms == null) {
            throw new NullPointerException("null permissions parameter");
        }
        Class <?> caller = Reflection.getCallerClass();
        return AccessController.doPrivileged(action, createWrapper(null, caller, parent, context, perms));
    }


    /**
     * Performs the specified {@code PrivilegedExceptionAction} with
     * privileges enabled and restricted by the specified
     * {@code AccessControlContext} and with a privilege scope limited by
     * specified {@code Permission} arguments.
     *
     * The action is performed with the intersection of the permissions
     * possessed by the caller's protection domain, and those possessed
     * by the domains represented by the specified
     * {@code AccessControlContext}.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * <p> This method preserves the current AccessControlContext's
     * DomainCombiner (which may be null) while the action is performed.
     * <p>
     * If a security manager is installed and the specified
     * {@code AccessControlContext} was not created by system code and the
     * caller's {@code ProtectionDomain} has not been granted the
     * {@literal "createAccessControlContext"}
     * {@link java.security.SecurityPermission}, then the action is performed
     * with no permissions.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     * @param action the action to be performed.
     * @param context an <i>access control context</i>
     *                representing the restriction to be applied to the
     *                caller's domain's privileges before performing
     *                the specified action.  If the context is
     *                {@code null},
     *                then no additional restriction is applied.
     * @param perms the {@code Permission} arguments which limit the
     *              scope of the caller's privileges. The number of arguments
     *              is variable.
     *
     * @return the value returned by the action's {@code run} method.
     *
     * @throws PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws NullPointerException if action or perms or any element of
     *         perms is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     * @see java.security.DomainCombiner
     *
     * @since 1.8
     */
    @CallerSensitive
    public static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action,
                                                 AccessControlContext context,
                                                 Permission... perms)
        throws PrivilegedActionException
    {
        AccessControlContext parent = getContext();
        DomainCombiner dc = parent.getCombiner();
        if (dc == null && context != null) {
            dc = context.getCombiner();
        }
        if (perms == null) {
            throw new NullPointerException("null permissions parameter");
        }
        Class <?> caller = Reflection.getCallerClass();
        return AccessController.doPrivileged(action, createWrapper(dc, caller,
            parent, context, perms));
    }

    /**
     * Returns the AccessControl context. i.e., it gets
     * the protection domains of all the callers on the stack,
     * starting at the first class with a non-null
     * ProtectionDomain.
     *
     * @return the access control context based on the current stack or
     *         null if there was only privileged system code.
     */

    private static native AccessControlContext getStackAccessControlContext();


    /**
     * Returns the "inherited" AccessControl context. This is the context
     * that existed when the thread was created. Package private so
     * AccessControlContext can use it.
     */

    static native AccessControlContext getInheritedAccessControlContext();

    /**
     * This method takes a "snapshot" of the current calling context, which
     * includes the current Thread's inherited AccessControlContext and any
     * limited privilege scope, and places it in an AccessControlContext object.
     * This context may then be checked at a later point, possibly in another thread.
     *
     * @see AccessControlContext
     *
     * @return the AccessControlContext based on the current context.
     */

    public static AccessControlContext getContext()
    {
        AccessControlContext acc = getStackAccessControlContext();
        if (acc == null) {
            // all we had was privileged system code. We don't want
            // to return null though, so we construct a real ACC.
            return new AccessControlContext(null, true);
        } else {
            return acc.optimize();
        }
    }

    /**
     * Determines whether the access request indicated by the
     * specified permission should be allowed or denied, based on
     * the current AccessControlContext and security policy.
     * This method quietly returns if the access request
     * is permitted, or throws an AccessControlException otherwise. The
     * getPermission method of the AccessControlException returns the
     * {@code perm} Permission object instance.
     *
     * @param perm the requested permission.
     *
     * @exception AccessControlException if the specified permission
     *            is not permitted, based on the current security policy.
     * @exception NullPointerException if the specified permission
     *            is {@code null} and is checked based on the
     *            security policy currently in effect.
     */

    public static void checkPermission(Permission perm)
        throws AccessControlException
    {
        //System.err.println("checkPermission "+perm);
        //Thread.currentThread().dumpStack();

        if (perm == null) {
            throw new NullPointerException("permission can't be null");
        }

        AccessControlContext stack = getStackAccessControlContext();
        // if context is null, we had privileged system code on the stack.
        if (stack == null) {
            Debug debug = AccessControlContext.getDebug();
            boolean dumpDebug = false;
            if (debug != null) {
                dumpDebug = !Debug.isOn("codebase=");
                dumpDebug &= !Debug.isOn("permission=") ||
                    Debug.isOn("permission=" + perm.getClass().getCanonicalName());
            }

            if (dumpDebug && Debug.isOn("stack")) {
                Thread.dumpStack();
            }

            if (dumpDebug && Debug.isOn("domain")) {
                debug.println("domain (context is null)");
            }

            if (dumpDebug) {
                debug.println("access allowed "+perm);
            }
            return;
        }

        AccessControlContext acc = stack.optimize();
        acc.checkPermission(perm);
    }
}
