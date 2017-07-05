/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.server.AsyncProviderCallback;

import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

/**
 * Stateful web service support in the JAX-WS RI.
 *
 * <h2>Usage</h2>
 * <p>
 * Application service implementation classes (or providers) who'd like
 * to use the stateful web service support must declare {@link Stateful}
 * annotation on a class. It should also have a <b>public static</b> method/field
 * that takes {@link StatefulWebServiceManager}.
 *
 * <pre>
 * &#64;{@link Stateful} &#64;{@link WebService}
 * class BankAccount {
 *     protected final int id;
 *     private int balance;
 *
 *     BankAccount(int id) { this.id = id; }
 *     &#64;{@link WebMethod}
 *     public synchronized void deposit(int amount) { balance+=amount; }
 *
 *     // either via a public static field
 *     <font color=red>
 *     public static {@link StatefulWebServiceManager}&lt;BankAccount> manager;
 *     </font>
 *     // ... or  via a public static method (the method name could be anything)
 *     <font color=red>
 *     public static void setManager({@link StatefulWebServiceManager}&lt;BankAccount> manager) {
 *        ...
 *     }
 *     </font>
 * }
 * </pre>
 *
 * <p>
 * After your service is deployed but before you receive a first request,
 * the resource injection occurs on the field or the method.
 *
 * <p>
 * A stateful web service class does not need to have a default constructor.
 * In fact, most of the time you want to define a constructor that takes
 * some arguments, so that each instance carries certain state (as illustrated
 * in the above example.)
 *
 * <p>
 * Each instance of a stateful web service class is identified by an unique
 * {@link EndpointReference}. Your application creates an instance of
 * a class, then you'll have the JAX-WS RI assign this unique EPR for the
 * instance as follows:
 *
 * <pre>
 * &#64;{@link WebService}
 * class Bank { // this is ordinary stateless service
 *     &#64;{@link WebMethod}
 *     public synchronized W3CEndpointReference login(int accountId, int pin) {
 *         if(!checkPin(pin))
 *             throw new AuthenticationFailedException("invalid pin");
 *         BankAccount acc = new BankAccount(accountId);
 *         return BankAccount.manager.{@link #export export}(acc);
 *     }
 * }
 * </pre>
 *
 * <p>
 * Typically you then pass this EPR to remote systems. When they send
 * messages to this EPR, the JAX-WS RI makes sure that the particular exported
 * instance associated with that EPR will receive a service invocation.
 *
 * <h2>Things To Consider</h2>
 * <p>
 * When you no longer need to tie an instance to the EPR,
 * use {@link #unexport(Object)} so that the object can be GC-ed
 * (or else you'll leak memory.) You may choose to do so explicitly,
 * or you can rely on the time out by using {@link #setTimeout(long, Callback)}.
 *
 * <p>
 * {@link StatefulWebServiceManager} is thread-safe. It can be safely
 * invoked from multiple threads concurrently.
 *
 * @author Kohsuke Kawaguchi
 * @see StatefulFeature
 * @since 2.1
 */
public interface StatefulWebServiceManager<T> {
    /**
     * Exports an object.
     *
     * <p>
     * This method works like {@link #export(Object)} except that
     * you can obtain the EPR in your choice of addressing version,
     * by passing in the suitable <tt>epr</tt> parameter.
     *
     * @param epr
     *      Either {@link W3CEndpointReference} or {@link MemberSubmissionEndpointReference}.
     *      If other types are specified, this method throws an {@link WebServiceException}.
     * @return
     *      {@link EndpointReference}-subclass that identifies this exported
     *      object.
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> epr, T o);

    /**
     * Exports an object.
     *
     * <p>
     * This method works like {@link #export(Object)} except that
     * you can obtain the EPR in your choice of addressing version,
     * by passing in the suitable <tt>epr</tt> parameter.
     *
     * @param epr
     *      Either {@link W3CEndpointReference} or {@link MemberSubmissionEndpointReference}.
     *      If other types are specified, this method throws an {@link WebServiceException}.
     * @param o
     *      The object to be exported, whose identity be referenced by the returned EPR.
     * @param recipe
     *      The additional data to be put into EPR. Can be null.
     * @return
     *      {@link EndpointReference}-subclass that identifies this exported
     *      object.
     * @since 2.1.1
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> epr, T o, @Nullable EPRRecipe recipe );

    /**
     * Exports an object.
     *
     * <p>
     * JAX-WS RI assigns an unique EPR to the exported object,
     * and from now on, messages that are sent to this EPR will
     * be routed to the given object.
     *
     * <p>
     * The object will be locked in memory, so be sure to
     * {@link #unexport(Object) unexport} it when it's no longer needed.
     *
     * <p>
     * Notice that the obtained EPR contains the address of the service,
     * which depends on the currently processed request. So invoking
     * this method multiple times with the same object may return
     * different EPRs, if such multiple invocations are done while
     * servicing different requests. (Of course all such EPRs point
     * to the same object, so messages sent to those EPRs will be
     * served by the same instance.)
     *
     * @return
     *      {@link W3CEndpointReference} that identifies this exported
     *      object. Always non-null.
     */
    @NotNull W3CEndpointReference export(T o);

    /**
     * Exports an object (for {@link AsyncProvider asynchronous web services}.)
     *
     * <p>
     * This method works like {@link #export(Class,Object)} but it
     * takes an extra {@link WebServiceContext} that represents the request currently
     * being processed by the caller (the JAX-WS RI remembers this when the service
     * processing is synchronous, and that's why this parameter is only needed for
     * asynchronous web services.)
     *
     * <h3>Why {@link WebServiceContext} is needed?</h3>
     * <p>
     * The obtained EPR contains address, such as host name. The server does not
     * know what its own host name is (or there are more than one of them),
     * so this value is determined by what the current client thinks the server name is.
     * This is why we need to take {@link WebServiceContext}. Pass in the
     * object given to {@link AsyncProvider#invoke(Object, AsyncProviderCallback,WebServiceContext)}.
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> eprType, @NotNull WebServiceContext context, T o);

    /**
     * Exports an object.
     *
     * <p>
     * <b>This method is not meant for application code.</b>
     * This is for {@link Tube}s that wish to use stateful web service support.
     *
     * @param currentRequest
     *      The request that we are currently processing. This is used to infer the address in EPR.
     * @see #export(Class, WebServiceContext, Object)
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> eprType, @NotNull Packet currentRequest, T o);

    /**
     * The same as {@link #export(Class, Packet, Object)} except
     * that it takes {@link EPRRecipe}.
     *
     * @param recipe
     *      See {@link #export(Class, Object, EPRRecipe)}.
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> eprType, @NotNull Packet currentRequest, T o, EPRRecipe recipe);

    /**
     * Exports an object.
     *
     * @deprecated
     *      This method is provided as a temporary workaround, and we'll eventually try to remove it.
     *
     * @param endpointAddress
     *      The endpoint address URL. Normally, this information is determined by other inputs,
     *      like {@link Packet} or {@link WebServiceContext}.
     */
    @NotNull <EPR extends EndpointReference> EPR export(Class<EPR> eprType, String endpointAddress, T o);

    /**
     * Unexports the given instance.
     *
     * <p>
     * JAX-WS will release a strong reference to unexported objects,
     * and they will never receive further requests (requests targeted
     * for those unexported objects will be served by the fallback object.)
     *
     * @param o
     *      if null, this method will be no-op.
     */
    void unexport(@Nullable T o);

    /**
     * Checks if the given EPR represents an object that has been exported from this manager.
     *
     * <p>
     * This method can be used to have two endpoints in the same application communicate
     * locally.
     *
     * @return null if the EPR is not exported from this manager.
     */
    @Nullable T resolve(@NotNull EndpointReference epr);

    /**
     * Sets the "fallback" instance.
     *
     * <p>
     * When the incoming request does not have the necessary header to
     * distinguish instances of <tt>T</tt>, or when the header is present
     * but its value does not correspond with any of the active exported
     * instances known to the JAX-WS, then the JAX-WS RI will try to
     * route the request to the fallback instance.
     *
     * <p>
     * This provides the application an opportunity to perform application
     * specific error recovery.
     *
     * <p>
     * If no fallback instance is provided, then the JAX-WS RI will
     * send back the fault. By default, no fallback instance is set.
     *
     * <p>
     * This method can be invoked any time, but most often you'd like to
     * use one instance at the get-go. The following code example
     * illustrates how to do this:
     *
     * <pre>
     * &#64;{@link WebService}
     * class BankAccount {
     *     ... continuting from the example in class javadoc ...
     *
     *     &#64;{@link Resource} static void setManager({@link StatefulWebServiceManager} manager) {
     *        manager.setFallbackInstance(new BankAccount(0) {
     *            &#64;{@link Override}
     *            void deposit(int amount) {
     *                putToAuditRecord(id);
     *                if(thisLooksBad())   callPolice();
     *                throw new {@link WebServiceException}("No such bank account exists");
     *            }
     *        });
     *     }
     * }
     * </pre>
     *
     * @param o
     *      Can be null.
     */
    void setFallbackInstance(T o);

    /**
     * Configures timeout for exported instances.
     *
     * <p>
     * When configured, the JAX-WS RI will internally use a timer
     * so that exported objects that have not received any request
     * for the given amount of minutes will be automatically unexported.
     *
     * <p>
     * At some point after the time out has occurred for an instance,
     * the JAX-WS RI will invoke the {@link Callback} to notify the application
     * that the time out has reached. Application then has a choice of
     * either let the object go unexported, or {@link #touch(Object) touch}
     * let the object live for another round of timer interval.
     *
     * <p>
     * If no callback is set, the expired object will automatically unexported.
     *
     * <p>
     * When you call this method multiple times, its effect on existing
     * instances are unspecified, although deterministic.
     *
     * @param milliseconds
     *      The time out interval. Specify 0 to cancel the timeout timer.
     *      Note that this only guarantees that time out does not occur
     *      at least until this amount of time has elapsed. It does not
     *      guarantee that the time out will always happen right after
     *      the timeout is reached.
     * @param callback
     *      application may choose to install a callback to control the
     *      timeout behavior.
     */
    void setTimeout(long milliseconds, @Nullable Callback<T> callback);

    /**
     * Resets the time out timer for the given instance.
     *
     * <p>
     * If the object is null, not exported, or already unexported, this
     * method will be no-op.
     */
    void touch(T o);

    /**
     * Used by {@link StatefulWebServiceManager#setTimeout(long, Callback)}
     * to determine what to do when the time out is reached.
     */
    interface Callback<T> {
        /**
         * Application has a chance to decide if the object should be unexported,
         * or kept alive.
         *
         * <p>
         * The application should either unexport the object, or touch the object
         * from within this callback.
         * If no action is taken, the object will remain exported until it is
         * manually unexported.
         *
         * @param timedOutObject
         *      The object that reached the time out.
         * @param manager
         *      The manager instance that you exported the object to.
         */
        void onTimeout(@NotNull T timedOutObject, @NotNull StatefulWebServiceManager<T> manager);
    }
}
