/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * {@code AccessController} was used with the Security Manager for access
 * control operations and decisions. This feature no longer exists.
 *
 * @author Li Gong
 * @author Roland Schemers
 * @since 1.2
 * @deprecated This class was only useful in conjunction with {@linkplain
 *       SecurityManager the Security Manager}, which is no longer supported.
 *       There is no replacement for the Security Manager or this class.
 */
@Deprecated(since="17", forRemoval=true)
public final class AccessController {

    /**
     * Don't allow anyone to instantiate an {@code AccessController}
     */
    private AccessController() { }

    /**
     * Performs the specified action.
     *
     * <p> If the action's {@code run} method throws an (unchecked)
     * exception, it will propagate through this method.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method
     *
     * @param action the action to be performed
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     * @see #doPrivileged(PrivilegedExceptionAction)
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedAction} with privileges enabled. Running the action
     *     with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     */
    public static <T> T doPrivileged(PrivilegedAction<T> action) {
        T result = action.run();
        return result;
    }

    /**
     * Performs the specified action.
     *
     * <p> If the action's {@code run} method throws an (unchecked)
     * exception, it will propagate through this method.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method
     *
     * @param action the action to be performed
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    NullPointerException if the action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedAction} with privileges enabled and with the current
     *     access control context's domain combiner preserved. Running the
     *     action with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     *
     * @since 1.6
     */
    public static <T> T doPrivilegedWithCombiner(PrivilegedAction<T> action) {
        T result = action.run();
        return result;
    }

    /**
     * Performs the specified action.
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method
     * @param action the action to be performed.
     * @param context ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    NullPointerException if the action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedAction} with privileges enabled and restricted
     *     by the specified {@code AccessControlContext}. Running the
     *     action with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     */
    public static <T> T doPrivileged(PrivilegedAction<T> action,
                                     @SuppressWarnings("removal") AccessControlContext context)
    {
        T result = action.run();
        return result;
    }

    /**
     * Performs the specified action.
     *
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method
     * @param action the action to be performed
     * @param context ignored
     * @param perms ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws NullPointerException if action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedAction} with privileges enabled and restricted
     *     by the specified {@code AccessControlContext} and with a privilege
     *     scope limited by the specified {@code Permission} arguments. Running
     *     the action with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     *
     * @since 1.8
     */
    public static <T> T doPrivileged(PrivilegedAction<T> action,
            @SuppressWarnings("removal") AccessControlContext context,
            Permission... perms) {

        T result = action.run();
        return result;
    }

    /**
     * Performs the specified action.
     *
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method
     * @param action the action to be performed
     * @param context ignored
     * @param perms ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws NullPointerException if action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedAction} with privileges enabled and restricted
     *     by the specified {@code AccessControlContext} and with a privilege
     *     scope limited by the specified {@code Permission} arguments. This
     *     method also originally preserved the current access control context's
     *     domain combiner while the action was performed. Running the action
     *     with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     *
     * @since 1.8
     */
    public static <T> T doPrivilegedWithCombiner(PrivilegedAction<T> action,
            @SuppressWarnings("removal") AccessControlContext context,
            Permission... perms) {

        T result = action.run();
        return result;
    }

    /**
     * Performs the specified action.
     *
     * <p> If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method
     *
     * @param action the action to be performed
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws    NullPointerException if the action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedExceptionAction} with privileges enabled. Running
     *     the action with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     */
    public static <T> T
        doPrivileged(PrivilegedExceptionAction<T> action)
        throws PrivilegedActionException
    {
        try {
            T result = action.run();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    /**
     * Performs the specified action.
     *
     * <p> If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method
     *
     * @param action the action to be performed
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws    NullPointerException if the action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedExceptionAction} with privileges enabled and with
     *     the current access control context's domain combiner preserved.
     *     Running the action with privileges enabled was only useful in
     *     conjunction with {@linkplain SecurityManager the Security Manager},
     *     which is no longer supported. This method has been changed to run
     *     the action as is, and has equivalent behavior as if there were no
     *     Security Manager enabled. There is no replacement for the Security
     *     Manager or this method.
     *
     * @since 1.6
     */
    public static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action)
        throws PrivilegedActionException
    {
        try {
            T result = action.run();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    /**
     * Performs the specified action.
     * <p>
     * If the action's {@code run} method throws an <i>unchecked</i>
     * exception, it will propagate through this method.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method
     * @param action the action to be performed
     * @param context ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws    PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws    NullPointerException if the action is {@code null}
     *
     * @see #doPrivileged(PrivilegedAction)
     * @see #doPrivileged(PrivilegedAction,AccessControlContext)
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedExceptionAction} with privileges enabled and
     *     restricted by the specified {@code AccessControlContext}. Running the
     *     action with privileges enabled was only useful in conjunction with
     *     {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     */
    public static <T> T
        doPrivileged(PrivilegedExceptionAction<T> action,
                     @SuppressWarnings("removal") AccessControlContext context)
        throws PrivilegedActionException
    {
        try {
            T result = action.run();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    /**
     * Performs the specified action.
     *
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method
     * @param action the action to be performed
     * @param context ignored
     * @param perms ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws NullPointerException if action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedExceptionAction} with privileges enabled and
     *     restricted by the specified {@code AccessControlContext} and with a
     *     privilege scope limited by the specified {@code Permission}
     *     arguments. Running the action with privileges enabled was only useful
     *     in conjunction with {@linkplain SecurityManager the Security Manager},
     *     which is no longer supported. This method has been changed to run
     *     the action as is, and has equivalent behavior as if there were no
     *     Security Manager enabled. There is no replacement for the Security
     *     Manager or this method.
     *
     * @since 1.8
     */
    public static <T> T doPrivileged(PrivilegedExceptionAction<T> action,
            @SuppressWarnings("removal") AccessControlContext context,
            Permission... perms)
        throws PrivilegedActionException
    {
        try {
            T result = action.run();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    private static PrivilegedActionException wrapException(Exception e) {
         return new PrivilegedActionException(e);
    }

    /**
     * Performs the specified action.
     *
     * <p>
     * If the action's {@code run} method throws an (unchecked) exception,
     * it will propagate through this method.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method
     * @param action the action to be performed
     * @param context ignored
     * @param perms ignored
     *
     * @return the value returned by the action's {@code run} method
     *
     * @throws PrivilegedActionException if the specified action's
     *         {@code run} method threw a <i>checked</i> exception
     * @throws NullPointerException if action is {@code null}
     *
     * @apiNote This method originally performed the specified
     *     {@code PrivilegedExceptionAction} with privileges enabled and
     *     restricted by the specified {@code AccessControlContext} and with a
     *     privilege scope limited by the specified {@code Permission}
     *     arguments. This method also preserved the current access control
     *     context's domain combiner while the action was performed. Running
     *     the action with privileges enabled was only useful in conjunction
     *     with {@linkplain SecurityManager the Security Manager}, which is no
     *     longer supported. This method has been changed to run the action as
     *     is, and has equivalent behavior as if there were no Security Manager
     *     enabled. There is no replacement for the Security Manager or this
     *     method.
     *
     * @since 1.8
     */
    public static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action,
                                                 @SuppressWarnings("removal") AccessControlContext context,
                                                 Permission... perms)
        throws PrivilegedActionException
    {
        try {
            T result = action.run();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    @SuppressWarnings("removal")
    private static AccessControlContext NO_PERMISSIONS_ACC =
        new AccessControlContext(new ProtectionDomain[] {
            new ProtectionDomain(null, null)
    });

    /**
     * Returns an {@code AccessControlContext} where the {@code checkPermission}
     * method always throws an {@code AccessControlException} and the
     * {@code getDomainCombiner} method always returns {@code null}.
     *
     * @return an {@code AccessControlContext} as specified above
     *
     * @see AccessControlContext
     * @apiNote This method originally returned a snapshot of the current
     *       calling context, which included the current thread's access
     *       control context and any limited privilege scope. This method has
     *       been changed to always return an innocuous
     *       {@code AccessControlContext} that fails all permission checks.
     *       This method was only useful in conjunction with
     *       {@linkplain SecurityManager the Security Manager}, which is no
     *       longer supported. There is no replacement for the Security Manager
     *       or this method.
     */
    @SuppressWarnings("removal")
    public static AccessControlContext getContext()
    {
        return NO_PERMISSIONS_ACC;
    }

    /**
     * Throws {@code AccessControlException}.
     *
     * @param perm ignored
     * @throws    AccessControlException always
     *
     * @apiNote This method originally determined whether the access request
     *       indicated by the specified permission should be allowed or denied,
     *       based on the current {@code AccessControlContext} and security
     *       policy. This method has been changed to always throw
     *       {@code AccessControlException}. This method was only useful in
     *       conjunction with {@linkplain SecurityManager the Security Manager},
     *       which is no longer supported. There is no replacement for the
     *       Security Manager or this method.
     */
    @SuppressWarnings("removal")
    public static void checkPermission(Permission perm)
        throws AccessControlException
    {
        throw new AccessControlException("checking permissions is not supported");
    }
}
