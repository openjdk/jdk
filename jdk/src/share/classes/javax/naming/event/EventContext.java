/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.naming.event;

import javax.naming.Name;
import javax.naming.Context;
import javax.naming.NamingException;


/**
 * Contains methods for registering/deregistering listeners to be notified of
 * events fired when objects named in a context changes.
 *<p>
 *<h1>Target</h1>
 * The name parameter in the <tt>addNamingListener()</tt> methods is referred
 * to as the <em>target</em>. The target, along with the scope, identify
 * the object(s) that the listener is interested in.
 * It is possible to register interest in a target that does not exist, but
 * there might be limitations in the extent to which this can be
 * supported by the service provider and underlying protocol/service.
 *<p>
 * If a service only supports registration for existing
 * targets, an attempt to register for a nonexistent target
 * results in a <tt>NameNotFoundException</tt> being thrown as early as possible,
 * preferably at the time <tt>addNamingListener()</tt> is called, or if that is
 * not possible, the listener will receive the exception through the
 * <tt>NamingExceptionEvent</tt>.
 *<p>
 * Also, for service providers that only support registration for existing
 * targets, when the target that a listener has registered for is
 * subsequently removed from the namespace, the listener is notified
 * via a <tt>NamingExceptionEvent</tt> (containing a
 *<tt>NameNotFoundException</tt>).
 *<p>
 * An application can use the method <tt>targetMustExist()</tt> to check
 * whether a <tt>EventContext</tt> supports registration
 * of nonexistent targets.
 *<p>
 *<h1>Event Source</h1>
 * The <tt>EventContext</tt> instance on which you invoke the
 * registration methods is the <em>event source</em> of the events that are
 * (potentially) generated.
 * The source is <em>not necessarily</em> the object named by the target.
 * Only when the target is the empty name is the object named by the target
 * the source.
 * In other words, the target,
 * along with the scope parameter, are used to identify
 * the object(s) that the listener is interested in, but the event source
 * is the <tt>EventContext</tt> instance with which the listener
 * has registered.
 *<p>
 * For example, suppose a listener makes the following registration:
 *<blockquote><pre>
 *      NamespaceChangeListener listener = ...;
 *      src.addNamingListener("x", SUBTREE_SCOPE, listener);
 *</pre></blockquote>
 * When an object named "x/y" is subsequently deleted, the corresponding
 * <tt>NamingEvent</tt> (<tt>evt</tt>)  must contain:
 *<blockquote><pre>
 *      evt.getEventContext() == src
 *      evt.getOldBinding().getName().equals("x/y")
 *</pre></blockquote>
 *<p>
 * Furthermore, listener registration/deregistration is with
 * the <tt>EventContext</tt>
 * <em>instance</em>, and not with the corresponding object in the namespace.
 * If the program intends at some point to remove a listener, then it needs to
 * keep a reference to the <tt>EventContext</tt> instance on
 * which it invoked <tt>addNamingListener()</tt> (just as
 * it needs to keep a reference to the listener in order to remove it
 * later). It cannot expect to do a <tt>lookup()</tt> and get another instance of
 * a <tt>EventContext</tt> on which to perform the deregistration.
 *<h1>Lifetime of Registration</h1>
 * A registered listener becomes deregistered when:
 *<ul>
 *<li>It is removed using <tt>removeNamingListener()</tt>.
 *<li>An exception is thrown while collecting information about the events.
 *  That is, when the listener receives a <tt>NamingExceptionEvent</tt>.
 *<li><tt>Context.close()</tt> is invoked on the <tt>EventContext</tt>
 * instance with which it has registered.
 </ul>
 * Until that point, a <tt>EventContext</tt> instance that has outstanding
 * listeners will continue to exist and be maintained by the service provider.
 *
 *<h1>Listener Implementations</h1>
 * The registration/deregistration methods accept an instance of
 * <tt>NamingListener</tt>. There are subinterfaces of <tt>NamingListener</tt>
 * for different of event types of <tt>NamingEvent</tt>.
 * For example, the <tt>ObjectChangeListener</tt>
 * interface is for the <tt>NamingEvent.OBJECT_CHANGED</tt> event type.
 * To register interest in multiple event types, the listener implementation
 * should implement multiple <tt>NamingListener</tt> subinterfaces and use a
 * single invocation of <tt>addNamingListener()</tt>.
 * In addition to reducing the number of method calls and possibly the code size
 * of the listeners, this allows some service providers to optimize the
 * registration.
 *
 *<h1>Threading Issues</h1>
 *
 * Like <tt>Context</tt> instances in general, instances of
 * <tt>EventContext</tt> are not guaranteed to be thread-safe.
 * Care must be taken when multiple threads are accessing the same
 * <tt>EventContext</tt> concurrently.
 * See the
 * <a href=package-summary.html#THREADING>package description</a>
 * for more information on threading issues.
 *
 * @author Rosanna Lee
 * @author Scott Seligman
 * @since 1.3
 */

public interface EventContext extends Context {
    /**
     * Constant for expressing interest in events concerning the object named
     * by the target.
     *<p>
     * The value of this constant is <tt>0</tt>.
     */
    public final static int OBJECT_SCOPE = 0;

    /**
     * Constant for expressing interest in events concerning objects
     * in the context named by the target,
     * excluding the context named by the target.
     *<p>
     * The value of this constant is <tt>1</tt>.
     */
    public final static int ONELEVEL_SCOPE = 1;

    /**
     * Constant for expressing interest in events concerning objects
     * in the subtree of the object named by the target, including the object
     * named by the target.
     *<p>
     * The value of this constant is <tt>2</tt>.
     */
    public final static int SUBTREE_SCOPE = 2;


    /**
     * Adds a listener for receiving naming events fired
     * when the object(s) identified by a target and scope changes.
     *
     * The event source of those events is this context. See the
     * class description for a discussion on event source and target.
     * See the descriptions of the constants <tt>OBJECT_SCOPE</tt>,
     * <tt>ONELEVEL_SCOPE</tt>, and <tt>SUBTREE_SCOPE</tt> to see how
     * <tt>scope</tt> affects the registration.
     *<p>
     * <tt>target</tt> needs to name a context only when <tt>scope</tt> is
     * <tt>ONELEVEL_SCOPE</tt>.
     * <tt>target</tt> may name a non-context if <tt>scope</tt> is either
     * <tt>OBJECT_SCOPE</tt> or <tt>SUBTREE_SCOPE</tt>.  Using
     * <tt>SUBTREE_SCOPE</tt> for a non-context might be useful,
     * for example, if the caller does not know in advance whether <tt>target</tt>
     * is a context and just wants to register interest in the (possibly
     * degenerate subtree) rooted at <tt>target</tt>.
     *<p>
     * When the listener is notified of an event, the listener may
     * in invoked in a thread other than the one in which
     * <tt>addNamingListener()</tt> is executed.
     * Care must be taken when multiple threads are accessing the same
     * <tt>EventContext</tt> concurrently.
     * See the
     * <a href=package-summary.html#THREADING>package description</a>
     * for more information on threading issues.
     *
     * @param target A nonnull name to be resolved relative to this context.
     * @param scope One of <tt>OBJECT_SCOPE</tt>, <tt>ONELEVEL_SCOPE</tt>, or
     * <tt>SUBTREE_SCOPE</tt>.
     * @param l  The nonnull listener.
     * @exception NamingException If a problem was encountered while
     * adding the listener.
     * @see #removeNamingListener
     */
    void addNamingListener(Name target, int scope, NamingListener l)
        throws NamingException;

    /**
     * Adds a listener for receiving naming events fired
     * when the object named by the string target name and scope changes.
     *
     * See the overload that accepts a <tt>Name</tt> for details.
     *
     * @param target The nonnull string name of the object resolved relative
     * to this context.
     * @param scope One of <tt>OBJECT_SCOPE</tt>, <tt>ONELEVEL_SCOPE</tt>, or
     * <tt>SUBTREE_SCOPE</tt>.
     * @param l  The nonnull listener.
     * @exception NamingException If a problem was encountered while
     * adding the listener.
     * @see #removeNamingListener
     */
    void addNamingListener(String target, int scope, NamingListener l)
        throws NamingException;

    /**
     * Removes a listener from receiving naming events fired
     * by this <tt>EventContext</tt>.
     * The listener may have registered more than once with this
     * <tt>EventContext</tt>, perhaps with different target/scope arguments.
     * After this method is invoked, the listener will no longer
     * receive events with this <tt>EventContext</tt> instance
     * as the event source (except for those events already in the process of
     * being dispatched).
     * If the listener was not, or is no longer, registered with
     * this <tt>EventContext</tt> instance, this method does not do anything.
     *
     * @param l  The nonnull listener.
     * @exception NamingException If a problem was encountered while
     * removing the listener.
     * @see #addNamingListener
     */
    void removeNamingListener(NamingListener l) throws NamingException;

    /**
     * Determines whether a listener can register interest in a target
     * that does not exist.
     *
     * @return true if the target must exist; false if the target need not exist.
     * @exception NamingException If the context's behavior in this regard cannot
     * be determined.
     */
    boolean targetMustExist() throws NamingException;
}
