/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.security.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import sun.security.util.ResourcesMgr;

/**
 * <p> A {@code Subject} represents a grouping of related information
 * for a single entity, such as a person.
 * Such information includes the Subject's identities as well as
 * its security-related attributes
 * (passwords and cryptographic keys, for example).
 *
 * <p> Subjects may potentially have multiple identities.
 * Each identity is represented as a {@code Principal}
 * within the {@code Subject}.  Principals simply bind names to a
 * {@code Subject}.  For example, a {@code Subject} that happens
 * to be a person, Alice, might have two Principals:
 * one which binds "Alice Bar", the name on her driver license,
 * to the {@code Subject}, and another which binds,
 * "999-99-9999", the number on her student identification card,
 * to the {@code Subject}.  Both Principals refer to the same
 * {@code Subject} even though each has a different name.
 *
 * <p> A {@code Subject} may also own security-related attributes,
 * which are referred to as credentials.
 * Sensitive credentials that require special protection, such as
 * private cryptographic keys, are stored within a private credential
 * {@code Set}.  Credentials intended to be shared, such as
 * public key certificates or Kerberos server tickets are stored
 * within a public credential {@code Set}.
 *
 * <p> To retrieve all the Principals associated with a {@code Subject},
 * invoke the {@code getPrincipals} method.  To retrieve
 * all the public or private credentials belonging to a {@code Subject},
 * invoke the {@code getPublicCredentials} method or
 * {@code getPrivateCredentials} method, respectively.
 * To modify the returned {@code Set} of Principals and credentials,
 * use the methods defined in the {@code Set} class.
 * For example:
 * <pre>
 *      Subject subject;
 *      Principal principal;
 *      Object credential;
 *
 *      // add a Principal and credential to the Subject
 *      subject.getPrincipals().add(principal);
 *      subject.getPublicCredentials().add(credential);
 * </pre>
 *
 * <p> This {@code Subject} class implements {@code Serializable}.
 * While the Principals associated with the {@code Subject} are serialized,
 * the credentials associated with the {@code Subject} are not.
 * Note that the {@code java.security.Principal} class
 * does not implement {@code Serializable}.  Therefore, all concrete
 * {@code Principal} implementations associated with Subjects
 * must implement {@code Serializable}.
 *
 * <h2>Deprecated Methods and Replacements</h2>
 *
 * <p> The following methods in this class for user-based authorization
 * that are dependent on Security Manager APIs are deprecated for removal:
 * <ul>
 *     <li>{@link #getSubject(AccessControlContext)}
 *     <li>{@link #doAs(Subject, PrivilegedAction)}
 *     <li>{@link #doAs(Subject, PrivilegedExceptionAction)}
 *     <li>{@link #doAsPrivileged(Subject, PrivilegedAction, AccessControlContext)}
 *     <li>{@link #doAsPrivileged(Subject, PrivilegedExceptionAction, AccessControlContext)}
 * </ul>
 * Methods {@link #current()} and {@link #callAs(Subject, Callable)}
 * are replacements for these methods, where {@code current} is equivalent to
 * {@code getSubject(AccessController.getContext())} (as originally specified)
 * and {@code callAs} is similar to {@code doAs} except that the
 * input type and exceptions thrown are slightly different.
 *
 * <p> A {@code doAs} or {@code callAs} call
 * binds a {@code Subject} object to the period of execution of an action,
 * and the subject can be retrieved using the {@code current} method inside
 * the action. This subject can be inherited by child threads if they are
 * started and terminate within the execution of its parent thread using
 * structured concurrency.
 *
 * @since 1.4
 * @see java.security.Principal
 * @see java.security.DomainCombiner
 */
public final class Subject implements java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = -8308522755600156056L;

    /**
     * A {@code Set} that provides a view of all of this
     * Subject's Principals
     *
     * @serial Each element in this set is a
     *          {@code java.security.Principal}.
     *          The set is a {@code Subject.SecureSet}.
     */
    @SuppressWarnings("serial") // Not statically typed as Serializable
    Set<Principal> principals;

    /**
     * Sets that provide a view of all of this
     * Subject's Credentials
     */
    transient Set<Object> pubCredentials;
    transient Set<Object> privCredentials;

    /**
     * Whether this Subject is read-only
     *
     * @serial
     */
    private volatile boolean readOnly;

    private static final int PRINCIPAL_SET = 1;
    private static final int PUB_CREDENTIAL_SET = 2;
    private static final int PRIV_CREDENTIAL_SET = 3;

    private static final ProtectionDomain[] NULL_PD_ARRAY
        = new ProtectionDomain[0];

    /**
     * Create an instance of a {@code Subject}
     * with an empty {@code Set} of Principals and empty
     * Sets of public and private credentials.
     *
     * <p> The newly constructed Sets check whether this {@code Subject}
     * has been set read-only before permitting subsequent modifications.
     * These Sets also prohibit null elements, and attempts to add, query,
     * or remove a null element will result in a {@code NullPointerException}.
     */
    public Subject() {

        this.principals = Collections.synchronizedSet
                        (new SecureSet<>(this, PRINCIPAL_SET));
        this.pubCredentials = Collections.synchronizedSet
                        (new SecureSet<>(this, PUB_CREDENTIAL_SET));
        this.privCredentials = Collections.synchronizedSet
                        (new SecureSet<>(this, PRIV_CREDENTIAL_SET));
    }

    /**
     * Create an instance of a {@code Subject} with
     * Principals and credentials.
     *
     * <p> The Principals and credentials from the specified Sets
     * are copied into newly constructed Sets.
     * These newly created Sets check whether this {@code Subject}
     * has been set read-only before permitting subsequent modifications.
     * These Sets also prohibit null elements, and attempts to add, query,
     * or remove a null element will result in a {@code NullPointerException}.
     *
     * @param readOnly true if the {@code Subject} is to be read-only,
     *          and false otherwise.
     *
     * @param principals the {@code Set} of Principals
     *          to be associated with this {@code Subject}.
     *
     * @param pubCredentials the {@code Set} of public credentials
     *          to be associated with this {@code Subject}.
     *
     * @param privCredentials the {@code Set} of private credentials
     *          to be associated with this {@code Subject}.
     *
     * @throws NullPointerException if the specified
     *          {@code principals}, {@code pubCredentials},
     *          or {@code privCredentials} are {@code null},
     *          or a null value exists within any of these three
     *          Sets.
     */
    public Subject(boolean readOnly, Set<? extends Principal> principals,
                   Set<?> pubCredentials, Set<?> privCredentials) {
        LinkedList<Principal> principalList
                = collectionNullClean(principals);
        LinkedList<Object> pubCredsList
                = collectionNullClean(pubCredentials);
        LinkedList<Object> privCredsList
                = collectionNullClean(privCredentials);

        this.principals = Collections.synchronizedSet(
                new SecureSet<>(this, PRINCIPAL_SET, principalList));
        this.pubCredentials = Collections.synchronizedSet(
                new SecureSet<>(this, PUB_CREDENTIAL_SET, pubCredsList));
        this.privCredentials = Collections.synchronizedSet(
                new SecureSet<>(this, PRIV_CREDENTIAL_SET, privCredsList));
        this.readOnly = readOnly;
    }

    /**
     * Set this {@code Subject} to be read-only.
     *
     * <p> Modifications (additions and removals) to this Subject's
     * {@code Principal} {@code Set} and
     * credential Sets will be disallowed.
     * The {@code destroy} operation on this Subject's credentials will
     * still be permitted.
     *
     * <p> Subsequent attempts to modify the Subject's {@code Principal}
     * and credential Sets will result in an
     * {@code IllegalStateException} being thrown.
     * Also, once a {@code Subject} is read-only,
     * it can not be reset to being writable again.
     */
    public void setReadOnly() {
        this.readOnly = true;
    }

    /**
     * Query whether this {@code Subject} is read-only.
     *
     * @return true if this {@code Subject} is read-only, false otherwise.
     */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Throws {@code UnsupportedOperationException}. A replacement API
     * named {@link #current()} has been added which can be used to obtain
     * the current subject.
     *
     * @param  acc ignored
     *
     * @return  n/a
     *
     * @throws UnsupportedOperationException always
     *
     * @deprecated This method used to get the subject associated with the
     *       provided {@link AccessControlContext}, which was only useful in
     *       conjunction with {@linkplain SecurityManager the Security Manager},
     *       which is no longer supported. This method has been changed to
     *       always throw {@code UnsupportedOperationException}. A replacement
     *       API named {@link #current()} has been added which can be used to
     *       obtain the current subject. There is no replacement for the
     *       Security Manager.
     *
     * @see #current()
     */
    @SuppressWarnings("removal")
    @Deprecated(since="17", forRemoval=true)
    public static Subject getSubject(final AccessControlContext acc) {
        throw new UnsupportedOperationException("getSubject is not supported");
    }

    private static final ScopedValue<Subject> SCOPED_SUBJECT =
            ScopedValue.newInstance();

    /**
     * Returns the current subject.
     *
     * <p> The current subject is installed by the {@link #callAs} method.
     * When {@code callAs(subject, action)} is called, {@code action} is
     * executed with {@code subject} as its current subject which can be
     * retrieved by this method. After {@code action} is finished, the current
     * subject is reset to its previous value. The current
     * subject is {@code null} before the first call of {@code callAs()}.
     *
     * <p> This method returns the
     * {@code Subject} bound to the period of the execution of the current
     * thread.
     *
     * @return the current subject, or {@code null} if a current subject is
     *      not installed or the current subject is set to {@code null}.
     * @see #callAs(Subject, Callable)
     * @since 18
     */
    public static Subject current() {
        return SCOPED_SUBJECT.isBound() ? SCOPED_SUBJECT.get() : null;
    }

    /**
     * Executes a {@code Callable} with {@code subject} as the
     * current subject.
     *
     * <p> This method launches {@code action} and binds {@code subject} to the
     * period of its execution.
     *
     * @param subject the {@code Subject} that the specified {@code action}
     *               will run as.  This parameter may be {@code null}.
     * @param action the code to be run with {@code subject} as its current
     *               subject. Must not be {@code null}.
     * @param <T> the type of value returned by the {@code call} method
     *            of {@code action}
     * @return the value returned by the {@code call} method of {@code action}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws CompletionException if {@code action.call()} throws an exception.
     *      The cause of the {@code CompletionException} is set to the exception
     *      thrown by {@code action.call()}.
     * @see #current()
     * @since 18
     */
    public static <T> T callAs(final Subject subject,
            final Callable<T> action) throws CompletionException {
        Objects.requireNonNull(action);
        try {
            return ScopedValue.where(SCOPED_SUBJECT, subject).call(action::call);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Perform work as a particular {@code Subject}.
     *
     * <p> This method launches {@code action} and binds {@code subject} to the
     * period of its execution.
     *
     * @param subject the {@code Subject} that the specified
     *                  {@code action} will run as.  This parameter
     *                  may be {@code null}.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @param action the code to be run as the specified
     *                  {@code Subject}.
     *
     * @return the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @throws NullPointerException if the {@code PrivilegedAction}
     *                  is {@code null}.
     *
     * @deprecated This method originally performed the specified
     *       {@code PrivilegedAction} with privileges enabled. Running the
     *       action with privileges enabled was only useful in conjunction
     *       with {@linkplain SecurityManager the Security Manager}, which is
     *       no longer supported. This method has been changed to launch the
     *       action as is and bind the subject to the period of its execution.
     *       A replacement API named {@link #callAs} has been added which can
     *       be used to perform the same work. There is no replacement for the
     *       Security Manager.
     *
     * @see #callAs(Subject, Callable)
     */
    @Deprecated(since="18", forRemoval=true)
    public static <T> T doAs(final Subject subject,
                        final java.security.PrivilegedAction<T> action) {

        Objects.requireNonNull(action,
                ResourcesMgr.getString("invalid.null.action.provided"));

        try {
            return callAs(subject, action::run);
        } catch (CompletionException ce) {
            var cause = ce.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            } else if (cause instanceof Error er) {
                throw er;
            } else {
                throw new AssertionError(ce);
            }
        }
    }

    /**
     * Perform work as a particular {@code Subject}.
     *
     * <p> This method launches {@code action} and binds {@code subject} to the
     * period of its execution.
     *
     * @param subject the {@code Subject} that the specified
     *                  {@code action} will run as.  This parameter
     *                  may be {@code null}.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @param action the code to be run as the specified
     *                  {@code Subject}.
     *
     * @return the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @throws PrivilegedActionException if the
     *                  {@code PrivilegedExceptionAction.run}
     *                  method throws a checked exception.
     *
     * @throws NullPointerException if the specified
     *                  {@code PrivilegedExceptionAction} is
     *                  {@code null}.
     *
     * @deprecated This method originally performed the specified
     *       {@code PrivilegedExceptionAction} with privileges enabled.
     *       Running the action with privileges enabled was only useful in
     *       conjunction with {@linkplain SecurityManager the Security Manager},
     *       which is no longer supported. This method has been changed to
     *       launch the action as is and bind the subject to the period of its
     *       execution. A replacement API named {@link #callAs} has been added
     *       which can be used to perform the same work. There is no
     *       replacement for the Security Manager.
     *
     * @see #callAs(Subject, Callable)
     */
    @Deprecated(since="18", forRemoval=true)
    public static <T> T doAs(final Subject subject,
                        final java.security.PrivilegedExceptionAction<T> action)
                        throws java.security.PrivilegedActionException {

        Objects.requireNonNull(action,
                ResourcesMgr.getString("invalid.null.action.provided"));

        try {
            return callAs(subject, action::run);
        } catch (CompletionException ce) {
            var cause = ce.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            } else if (cause instanceof Error er) {
                throw er;
            } else if (cause instanceof Exception e) {
                throw new PrivilegedActionException(e);
            } else {
                throw new PrivilegedActionException(ce);
            }
        }
    }

    /**
     * Perform work as a particular {@code Subject}.
     *
     * <p> This method launches {@code action} and binds {@code subject} to
     * the period of its execution.
     *
     * @param subject the {@code Subject} that the specified
     *                  {@code action} will run as.  This parameter
     *                  may be {@code null}.
     *
     * @param <T> the type of the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @param action the code to be run as the specified
     *                  {@code Subject}.
     *
     * @param acc ignored
     *
     * @return the value returned by the PrivilegedAction's
     *                  {@code run} method.
     *
     * @throws NullPointerException if the {@code PrivilegedAction}
     *                  is {@code null}.
     *
     * @deprecated This method originally performed the specified
     *       {@code PrivilegedAction} with privileges enabled and restricted
     *       by the specified {@code AccessControlContext}. Running the
     *       action with privileges enabled was only useful in conjunction
     *       with {@linkplain SecurityManager the Security Manager}, which is
     *       no longer supported. This method has been changed to ignore the
     *       {@code AccessControlContext} and launch the action as is and bind
     *       the subject to the period of its execution. A replacement API
     *       named {@link #callAs} has been added which can be used to perform
     *       the same work. There is no replacement for the Security Manager.
     *
     * @see #callAs(Subject, Callable)
     */
    @SuppressWarnings("removal")
    @Deprecated(since="17", forRemoval=true)
    public static <T> T doAsPrivileged(final Subject subject,
                        final java.security.PrivilegedAction<T> action,
                        final java.security.AccessControlContext acc) {

        Objects.requireNonNull(action,
                ResourcesMgr.getString("invalid.null.action.provided"));

        try {
            return callAs(subject, action::run);
        } catch (CompletionException ce) {
            var cause = ce.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            } else if (cause instanceof Error er) {
                throw er;
            } else {
                throw new AssertionError(ce);
            }
        }
    }

    /**
     * Perform work as a particular {@code Subject}.
     *
     * <p> This method launches {@code action} and binds {@code subject} to
     * the period of its execution.
     *
     * @param subject the {@code Subject} that the specified
     *                  {@code action} will run as.  This parameter
     *                  may be {@code null}.
     *
     * @param <T> the type of the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @param action the code to be run as the specified
     *                  {@code Subject}.
     *
     * @param acc ignored
     *
     * @return the value returned by the
     *                  PrivilegedExceptionAction's {@code run} method.
     *
     * @throws PrivilegedActionException if the
     *                  {@code PrivilegedExceptionAction.run}
     *                  method throws a checked exception.
     *
     * @throws NullPointerException if the specified
     *                  {@code PrivilegedExceptionAction} is
     *                  {@code null}.
     *
     * @deprecated This method originally performed the specified
     *       {@code PrivilegedExceptionAction} with privileges enabled and
     *       restricted by the specified {@code AccessControlContext}. Running
     *       the action with privileges enabled was only useful in conjunction
     *       with {@linkplain SecurityManager the Security Manager}, which is
     *       no longer supported. This method has been changed to ignore the
     *       {@code AccessControlContext} and launch the action as is and bind
     *       the subject to the period of its execution. A replacement API
     *       named {@link #callAs} has been added which can be used to perform
     *       the same work. There is no replacement for the Security Manager.
     *
     * @see #callAs(Subject, Callable)
     */
    @SuppressWarnings("removal")
    @Deprecated(since="17", forRemoval=true)
    public static <T> T doAsPrivileged(final Subject subject,
                        final java.security.PrivilegedExceptionAction<T> action,
                        final java.security.AccessControlContext acc)
                        throws java.security.PrivilegedActionException {

        Objects.requireNonNull(action,
                ResourcesMgr.getString("invalid.null.action.provided"));

        try {
            return callAs(subject, action::run);
        } catch (CompletionException ce) {
            var cause = ce.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            } else if (cause instanceof Error er) {
                throw er;
            } else if (cause instanceof Exception e) {
                throw new PrivilegedActionException(e);
            } else {
                throw new PrivilegedActionException(ce);
            }
        }
    }

    /**
     * Return the {@code Set} of Principals associated with this
     * {@code Subject}.  Each {@code Principal} represents
     * an identity for this {@code Subject}.
     *
     * <p> The returned {@code Set} is backed by this Subject's
     * internal {@code Principal} {@code Set}.  Any modification
     * to the returned {@code Set} affects the internal
     * {@code Principal} {@code Set} as well.
     *
     * @return  the {@code Set} of Principals associated with this
     *          {@code Subject}.
     */
    public Set<Principal> getPrincipals() {

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return principals;
    }

    /**
     * Return a {@code Set} of Principals associated with this
     * {@code Subject} that are instances or subclasses of the specified
     * {@code Class}.
     *
     * <p> The returned {@code Set} is not backed by this Subject's
     * internal {@code Principal} {@code Set}.  A new
     * {@code Set} is created and returned for each method invocation.
     * Modifications to the returned {@code Set}
     * will not affect the internal {@code Principal} {@code Set}.
     *
     * @param <T> the type of the class modeled by {@code c}
     *
     * @param c the returned {@code Set} of Principals will all be
     *          instances of this class.
     *
     * @return a {@code Set} of Principals that are instances of the
     *          specified {@code Class}.
     *
     * @throws NullPointerException if the specified {@code Class}
     *          is {@code null}.
     */
    public <T extends Principal> Set<T> getPrincipals(Class<T> c) {

        Objects.requireNonNull(c,
                ResourcesMgr.getString("invalid.null.Class.provided"));

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return new ClassSet<>(PRINCIPAL_SET, c);
    }

    /**
     * Return the {@code Set} of public credentials held by this
     * {@code Subject}.
     *
     * <p> The returned {@code Set} is backed by this Subject's
     * internal public Credential {@code Set}.  Any modification
     * to the returned {@code Set} affects the internal public
     * Credential {@code Set} as well.
     *
     * @return  a {@code Set} of public credentials held by this
     *          {@code Subject}.
     */
    public Set<Object> getPublicCredentials() {

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return pubCredentials;
    }

    /**
     * Return the {@code Set} of private credentials held by this
     * {@code Subject}.
     *
     * <p> The returned {@code Set} is backed by this Subject's
     * internal private Credential {@code Set}.  Any modification
     * to the returned {@code Set} affects the internal private
     * Credential {@code Set} as well.
     *
     * @return  a {@code Set} of private credentials held by this
     *          {@code Subject}.
     */
    public Set<Object> getPrivateCredentials() {

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return privCredentials;
    }

    /**
     * Return a {@code Set} of public credentials associated with this
     * {@code Subject} that are instances or subclasses of the specified
     * {@code Class}.
     *
     * <p> The returned {@code Set} is not backed by this Subject's
     * internal public Credential {@code Set}.  A new
     * {@code Set} is created and returned for each method invocation.
     * Modifications to the returned {@code Set}
     * will not affect the internal public Credential {@code Set}.
     *
     * @param <T> the type of the class modeled by {@code c}
     *
     * @param c the returned {@code Set} of public credentials will all be
     *          instances of this class.
     *
     * @return a {@code Set} of public credentials that are instances
     *          of the  specified {@code Class}.
     *
     * @throws NullPointerException if the specified {@code Class}
     *          is {@code null}.
     */
    public <T> Set<T> getPublicCredentials(Class<T> c) {

        Objects.requireNonNull(c,
                ResourcesMgr.getString("invalid.null.Class.provided"));

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return new ClassSet<>(PUB_CREDENTIAL_SET, c);
    }

    /**
     * Return a {@code Set} of private credentials associated with this
     * {@code Subject} that are instances or subclasses of the specified
     * {@code Class}.
     *
     * <p> The returned {@code Set} is not backed by this Subject's
     * internal private Credential {@code Set}.  A new
     * {@code Set} is created and returned for each method invocation.
     * Modifications to the returned {@code Set}
     * will not affect the internal private Credential {@code Set}.
     *
     * @param <T> the type of the class modeled by {@code c}
     *
     * @param c the returned {@code Set} of private credentials will all be
     *          instances of this class.
     *
     * @return a {@code Set} of private credentials that are instances
     *          of the  specified {@code Class}.
     *
     * @throws NullPointerException if the specified {@code Class}
     *          is {@code null}.
     */
    public <T> Set<T> getPrivateCredentials(Class<T> c) {

        Objects.requireNonNull(c,
                ResourcesMgr.getString("invalid.null.Class.provided"));

        // always return an empty Set instead of null
        // so LoginModules can add to the Set if necessary
        return new ClassSet<>(PRIV_CREDENTIAL_SET, c);
    }

    /**
     * Compares the specified Object with this {@code Subject}
     * for equality.  Returns true if the given object is also a Subject
     * and the two {@code Subject} instances are equivalent.
     * More formally, two {@code Subject} instances are
     * equal if their {@code Principal} and {@code Credential}
     * Sets are equal.
     *
     * @param o Object to be compared for equality with this
     *          {@code Subject}.
     *
     * @return true if the specified Object is equal to this
     *          {@code Subject}.
     */
    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (o instanceof final Subject that) {

            // check the principal and credential sets
            Set<Principal> thatPrincipals;
            synchronized(that.principals) {
                // avoid deadlock from dual locks
                thatPrincipals = new HashSet<>(that.principals);
            }
            if (!principals.equals(thatPrincipals)) {
                return false;
            }

            Set<Object> thatPubCredentials;
            synchronized(that.pubCredentials) {
                // avoid deadlock from dual locks
                thatPubCredentials = new HashSet<>(that.pubCredentials);
            }
            if (!pubCredentials.equals(thatPubCredentials)) {
                return false;
            }

            Set<Object> thatPrivCredentials;
            synchronized(that.privCredentials) {
                // avoid deadlock from dual locks
                thatPrivCredentials = new HashSet<>(that.privCredentials);
            }
            return privCredentials.equals(thatPrivCredentials);
        }
        return false;
    }

    /**
     * Return the String representation of this {@code Subject}.
     *
     * @return the String representation of this {@code Subject}.
     */
    @Override
    public String toString() {

        String s = ResourcesMgr.getString("Subject.");
        String suffix = "";

        synchronized(principals) {
            for (Principal p : principals) {
                suffix = suffix + ResourcesMgr.getString(".Principal.") +
                        p.toString() + ResourcesMgr.getString("NEWLINE");
            }
        }

        synchronized(pubCredentials) {
            for (Object o : pubCredentials) {
                suffix = suffix +
                        ResourcesMgr.getString(".Public.Credential.") +
                        o.toString() + ResourcesMgr.getString("NEWLINE");
            }
        }

        synchronized(privCredentials) {
            Iterator<Object> pI = privCredentials.iterator();
            while (pI.hasNext()) {
                try {
                    Object o = pI.next();
                    suffix += ResourcesMgr.getString
                                    (".Private.Credential.") +
                                    o.toString() +
                                    ResourcesMgr.getString("NEWLINE");
                } catch (SecurityException se) {
                    suffix += ResourcesMgr.getString
                            (".Private.Credential.inaccessible.");
                    break;
                }
            }
        }
        return s + suffix;
    }

    /**
     * {@return a hashcode for this {@code Subject}}
     */
    @Override
    public int hashCode() {

        /*
         * The hashcode is derived exclusive or-ing the
         * hashcodes of this Subject's Principals and credentials.
         *
         * If a particular credential was destroyed
         * ({@code credential.hashCode()} throws an
         * {@code IllegalStateException}),
         * the hashcode for that credential is derived via:
         * {@code credential.getClass().toString().hashCode()}.
         */

        int hashCode = 0;

        synchronized(principals) {
            for (Principal p : principals) {
                hashCode ^= p.hashCode();
            }
        }

        synchronized(pubCredentials) {
            for (Object pubCredential : pubCredentials) {
                hashCode ^= getCredHashCode(pubCredential);
            }
        }
        return hashCode;
    }

    /**
     * get a credential's hashcode
     */
    private int getCredHashCode(Object o) {
        try {
            return o.hashCode();
        } catch (IllegalStateException ise) {
            return o.getClass().toString().hashCode();
        }
    }

    /**
     * Writes this object out to a stream (i.e., serializes it).
     *
     * @param  oos the {@code ObjectOutputStream} to which data is written
     * @throws IOException if an I/O error occurs
     */
    @java.io.Serial
    private void writeObject(java.io.ObjectOutputStream oos)
                throws java.io.IOException {
        synchronized(principals) {
            oos.defaultWriteObject();
        }
    }

    /**
     * Reads this object from a stream (i.e., deserializes it)
     *
     * @param  s the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @SuppressWarnings("unchecked")
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {

        ObjectInputStream.GetField gf = s.readFields();

        readOnly = gf.get("readOnly", false);

        Set<Principal> inputPrincs = (Set<Principal>)gf.get("principals", null);

        Objects.requireNonNull(inputPrincs,
                ResourcesMgr.getString("invalid.null.input.s."));

        // Rewrap the principals into a SecureSet
        try {
            LinkedList<Principal> principalList = collectionNullClean(inputPrincs);
            principals = Collections.synchronizedSet(new SecureSet<>
                                (this, PRINCIPAL_SET, principalList));
        } catch (NullPointerException npe) {
            // Sometimes people deserialize the principals set only.
            // Subject is not accessible, so just don't fail.
            principals = Collections.synchronizedSet
                        (new SecureSet<>(this, PRINCIPAL_SET));
        }

        // The Credential {@code Set} is not serialized, but we do not
        // want the default deserialization routine to set it to null.
        this.pubCredentials = Collections.synchronizedSet
                        (new SecureSet<>(this, PUB_CREDENTIAL_SET));
        this.privCredentials = Collections.synchronizedSet
                        (new SecureSet<>(this, PRIV_CREDENTIAL_SET));
    }

    /**
     * Tests for null-clean collections (both non-null reference and
     * no null elements)
     *
     * @param coll A {@code Collection} to be tested for null references
     *
     * @throws NullPointerException if the specified collection is either
     *            {@code null} or contains a {@code null} element
     */
    private static <E> LinkedList<E> collectionNullClean(
            Collection<? extends E> coll) {

        Objects.requireNonNull(coll,
                ResourcesMgr.getString("invalid.null.input.s."));

        LinkedList<E> output = new LinkedList<>();
        for (E e : coll) {
            output.add(Objects.requireNonNull(e,
                    ResourcesMgr.getString("invalid.null.input.s.")));
        }
        return output;
    }

    /**
     * Prevent modifications unless caller has permission.
     *
     * @serial include
     */
    private static class SecureSet<E>
        implements Set<E>, java.io.Serializable {

        @java.io.Serial
        private static final long serialVersionUID = 7911754171111800359L;

        /**
         * @serialField this$0 Subject The outer Subject instance.
         * @serialField elements LinkedList The elements in this set.
         */
        @java.io.Serial
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("this$0", Subject.class),
            new ObjectStreamField("elements", LinkedList.class),
            new ObjectStreamField("which", int.class)
        };

        Subject subject;
        LinkedList<E> elements;

        /**
         * @serial An integer identifying the type of objects contained
         *      in this set.  If {@code which == 1},
         *      this is a Principal set and all the elements are
         *      of type {@code java.security.Principal}.
         *      If {@code which == 2}, this is a public credential
         *      set and all the elements are of type {@code Object}.
         *      If {@code which == 3}, this is a private credential
         *      set and all the elements are of type {@code Object}.
         */
        private int which;

        SecureSet(Subject subject, int which) {
            this.subject = subject;
            this.which = which;
            this.elements = new LinkedList<>();
        }

        SecureSet(Subject subject, int which, LinkedList<E> list) {
            this.subject = subject;
            this.which = which;
            this.elements = list;
        }

        public int size() {
            return elements.size();
        }

        public Iterator<E> iterator() {
            final LinkedList<E> list = elements;
            return new Iterator<>() {
                final ListIterator<E> i = list.listIterator(0);

                public boolean hasNext() {
                    return i.hasNext();
                }

                public E next() {
                    return i.next();
                }

                public void remove() {

                    if (subject.isReadOnly()) {
                        throw new IllegalStateException(ResourcesMgr.getString
                                ("Subject.is.read.only"));
                    }

                    i.remove();
                }
            };
        }

        public boolean add(E o) {

            Objects.requireNonNull(o,
                    ResourcesMgr.getString("invalid.null.input.s."));

            if (subject.isReadOnly()) {
                throw new IllegalStateException
                        (ResourcesMgr.getString("Subject.is.read.only"));
            }

            switch (which) {
            case Subject.PRINCIPAL_SET:
                if (!(o instanceof Principal)) {
                    throw new SecurityException(ResourcesMgr.getString
                        ("attempting.to.add.an.object.which.is.not.an.instance.of.java.security.Principal.to.a.Subject.s.Principal.Set"));
                }
                break;
            default:
                // ok to add Objects of any kind to credential sets
                break;
            }

            // check for duplicates
            if (!elements.contains(o))
                return elements.add(o);
            else {
                return false;
            }
        }

        public boolean remove(Object o) {

            Objects.requireNonNull(o,
                    ResourcesMgr.getString("invalid.null.input.s."));

            final Iterator<E> e = iterator();
            while (e.hasNext()) {
                E next = e.next();

                if (next.equals(o)) {
                    e.remove();
                    return true;
                }
            }
            return false;
        }

        public boolean contains(Object o) {

            Objects.requireNonNull(o,
                    ResourcesMgr.getString("invalid.null.input.s."));

            final Iterator<E> e = iterator();
            while (e.hasNext()) {
                E next = e.next();

                if (next.equals(o)) {
                    return true;
                }
            }
            return false;
        }

        public boolean addAll(Collection<? extends E> c) {
            boolean result = false;

            c = collectionNullClean(c);

            for (E item : c) {
                result |= this.add(item);
            }

            return result;
        }

        public boolean removeAll(Collection<?> c) {
            c = collectionNullClean(c);

            boolean modified = false;
            final Iterator<E> e = iterator();
            while (e.hasNext()) {
                E next = e.next();

                for (Object o : c) {
                    if (next.equals(o)) {
                        e.remove();
                        modified = true;
                        break;
                    }
                }
            }
            return modified;
        }

        public boolean containsAll(Collection<?> c) {
            c = collectionNullClean(c);

            for (Object item : c) {
                if (!this.contains(item)) {
                    return false;
                }
            }

            return true;
        }

        public boolean retainAll(Collection<?> c) {
            c = collectionNullClean(c);

            boolean modified = false;
            final Iterator<E> e = iterator();
            while (e.hasNext()) {
                E next = e.next();

                if (c.contains(next) == false) {
                    e.remove();
                    modified = true;
                }
            }

            return modified;
        }

        public void clear() {
            final Iterator<E> e = iterator();
            while (e.hasNext()) {
                E next = e.next();
                e.remove();
            }
        }

        public boolean isEmpty() {
            return elements.isEmpty();
        }

        public Object[] toArray() {
            return elements.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return elements.toArray(a);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof Set)) {
                return false;
            }

            Collection<?> c = (Collection<?>) o;
            if (c.size() != size()) {
                return false;
            }

            try {
                return containsAll(c);
            } catch (ClassCastException | NullPointerException unused) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (E obj : this) {
                h += Objects.hashCode(obj);
            }
            return h;
        }

        /**
         * Writes this object out to a stream (i.e., serializes it).
         *
         * @param  oos the {@code ObjectOutputStream} to which data is written
         * @throws IOException if an I/O error occurs
         */
        @java.io.Serial
        private void writeObject(java.io.ObjectOutputStream oos)
                throws java.io.IOException {

            ObjectOutputStream.PutField fields = oos.putFields();
            fields.put("this$0", subject);
            fields.put("elements", elements);
            fields.put("which", which);
            oos.writeFields();
        }

        /**
         * Restores the state of this object from the stream.
         *
         * @param  ois the {@code ObjectInputStream} from which data is read
         * @throws IOException if an I/O error occurs
         * @throws ClassNotFoundException if a serialized class cannot be loaded
         */
        @SuppressWarnings("unchecked")
        @java.io.Serial
        private void readObject(ObjectInputStream ois)
            throws IOException, ClassNotFoundException
        {
            ObjectInputStream.GetField fields = ois.readFields();
            subject = (Subject) fields.get("this$0", null);
            which = fields.get("which", 0);

            LinkedList<E> tmp = (LinkedList<E>) fields.get("elements", null);

            elements = Subject.collectionNullClean(tmp);
        }

    }

    /**
     * This class implements a {@code Set} which returns only
     * members that are an instance of a specified Class.
     */
    private class ClassSet<T> extends AbstractSet<T> {

        private final int which;
        private final Class<T> c;
        private final Set<T> set;

        ClassSet(int which, Class<T> c) {
            this.which = which;
            this.c = c;
            set = new HashSet<>();

            switch (which) {
            case Subject.PRINCIPAL_SET:
                synchronized(principals) { populateSet(); }
                break;
            case Subject.PUB_CREDENTIAL_SET:
                synchronized(pubCredentials) { populateSet(); }
                break;
            default:
                synchronized(privCredentials) { populateSet(); }
                break;
            }
        }

        @SuppressWarnings("unchecked")
        private void populateSet() {
            final Iterator<?> iterator;
            switch(which) {
            case Subject.PRINCIPAL_SET:
                iterator = Subject.this.principals.iterator();
                break;
            case Subject.PUB_CREDENTIAL_SET:
                iterator = Subject.this.pubCredentials.iterator();
                break;
            default:
                iterator = Subject.this.privCredentials.iterator();
                break;
            }

            while (iterator.hasNext()) {
                Object next = iterator.next();
                if (c.isAssignableFrom(next.getClass())) {
                    set.add((T)next);
                }
            }
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        public Iterator<T> iterator() {
            return set.iterator();
        }

        @Override
        public boolean add(T o) {

            if (!c.isAssignableFrom(o.getClass())) {
                MessageFormat form = new MessageFormat(ResourcesMgr.getString
                        ("attempting.to.add.an.object.which.is.not.an.instance.of.class"));
                Object[] source = {c.toString()};
                throw new SecurityException(form.format(source));
            }

            return set.add(o);
        }
    }
}
