/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.channels;

import java.io.IOException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;


/**
 * A channel that can be multiplexed via a {@link Selector}.
 *
 * <p> In order to be used with a selector, an instance of this class must
 * first be <i>registered</i> via the {@link #register(Selector,int,Object)
 * register} method.  This method returns a new {@link SelectionKey} object
 * that represents the channel's registration with the selector.
 *
 * <p> Once registered with a selector, a channel remains registered until it
 * is <i>deregistered</i>.  This involves deallocating whatever resources were
 * allocated to the channel by the selector.
 *
 * <p> A channel cannot be deregistered directly; instead, the key representing
 * its registration must be <i>cancelled</i>.  Cancelling a key requests that
 * the channel be deregistered during the selector's next selection operation.
 * A key may be cancelled explicitly by invoking its {@link
 * SelectionKey#cancel() cancel} method.  All of a channel's keys are cancelled
 * implicitly when the channel is closed, whether by invoking its {@link
 * Channel#close close} method or by interrupting a thread blocked in an I/O
 * operation upon the channel.
 *
 * <p> If the selector itself is closed then the channel will be deregistered,
 * and the key representing its registration will be invalidated, without
 * further delay.
 *
 * <p> A channel may be registered at most once with any particular selector.
 *
 * <p> Whether or not a channel is registered with one or more selectors may be
 * determined by invoking the {@link #isRegistered isRegistered} method.
 *
 * <p> Selectable channels are safe for use by multiple concurrent
 * threads. </p>
 *
 *
 * <a name="bm">
 * <h4>Blocking mode</h4>
 *
 * A selectable channel is either in <i>blocking</i> mode or in
 * <i>non-blocking</i> mode.  In blocking mode, every I/O operation invoked
 * upon the channel will block until it completes.  In non-blocking mode an I/O
 * operation will never block and may transfer fewer bytes than were requested
 * or possibly no bytes at all.  The blocking mode of a selectable channel may
 * be determined by invoking its {@link #isBlocking isBlocking} method.
 *
 * <p> Newly-created selectable channels are always in blocking mode.
 * Non-blocking mode is most useful in conjunction with selector-based
 * multiplexing.  A channel must be placed into non-blocking mode before being
 * registered with a selector, and may not be returned to blocking mode until
 * it has been deregistered.
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 * @see SelectionKey
 * @see Selector
 */

public abstract class SelectableChannel
    extends AbstractInterruptibleChannel
    implements Channel
{

    /**
     * Initializes a new instance of this class.
     */
    protected SelectableChannel() { }

    /**
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     */
    public abstract SelectorProvider provider();

    /**
     * Returns an <a href="SelectionKey.html#opsets">operation set</a>
     * identifying this channel's supported operations.  The bits that are set
     * in this integer value denote exactly the operations that are valid for
     * this channel.  This method always returns the same value for a given
     * concrete channel class. </p>
     *
     * @return  The valid-operation set
     */
    public abstract int validOps();

    // Internal state:
    //   keySet, may be empty but is never null, typ. a tiny array
    //   boolean isRegistered, protected by key set
    //   regLock, lock object to prevent duplicate registrations
    //   boolean isBlocking, protected by regLock

    /**
     * Tells whether or not this channel is currently registered with any
     * selectors.  A newly-created channel is not registered.
     *
     * <p> Due to the inherent delay between key cancellation and channel
     * deregistration, a channel may remain registered for some time after all
     * of its keys have been cancelled.  A channel may also remain registered
     * for some time after it is closed.  </p>
     *
     * @return <tt>true</tt> if, and only if, this channel is registered
     */
    public abstract boolean isRegistered();
    //
    // sync(keySet) { return isRegistered; }

    /**
     * Retrieves the key representing the channel's registration with the given
     * selector.  </p>
     *
     * @return  The key returned when this channel was last registered with the
     *          given selector, or <tt>null</tt> if this channel is not
     *          currently registered with that selector
     */
    public abstract SelectionKey keyFor(Selector sel);
    //
    // sync(keySet) { return findKey(sel); }

    /**
     * Registers this channel with the given selector, returning a selection
     * key.
     *
     * <p> If this channel is currently registered with the given selector then
     * the selection key representing that registration is returned.  The key's
     * interest set will have been changed to <tt>ops</tt>, as if by invoking
     * the {@link SelectionKey#interestOps(int) interestOps(int)} method.  If
     * the <tt>att</tt> argument is not <tt>null</tt> then the key's attachment
     * will have been set to that value.  A {@link CancelledKeyException} will
     * be thrown if the key has already been cancelled.
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so it is registered and the resulting new key is returned.
     * The key's initial interest set will be <tt>ops</tt> and its attachment
     * will be <tt>att</tt>.
     *
     * <p> This method may be invoked at any time.  If this method is invoked
     * while another invocation of this method or of the {@link
     * #configureBlocking(boolean) configureBlocking} method is in progress
     * then it will first block until the other operation is complete.  This
     * method will then synchronize on the selector's key set and therefore may
     * block if invoked concurrently with another registration or selection
     * operation involving the same selector. </p>
     *
     * <p> If this channel is closed while this operation is in progress then
     * the key returned by this method will have been cancelled and will
     * therefore be invalid. </p>
     *
     * @param  sel
     *         The selector with which this channel is to be registered
     *
     * @param  ops
     *         The interest set for the resulting key
     *
     * @param  att
     *         The attachment for the resulting key; may be <tt>null</tt>
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  ClosedSelectorException
     *          If the selector is closed
     *
     * @throws  IllegalBlockingModeException
     *          If this channel is in blocking mode
     *
     * @throws  IllegalSelectorException
     *          If this channel was not created by the same provider
     *          as the given selector
     *
     * @throws  CancelledKeyException
     *          If this channel is currently registered with the given selector
     *          but the corresponding key has already been cancelled
     *
     * @throws  IllegalArgumentException
     *          If a bit in the <tt>ops</tt> set does not correspond to an
     *          operation that is supported by this channel, that is, if
     *          <tt>set & ~validOps() != 0</tt>
     *
     * @return  A key representing the registration of this channel with
     *          the given selector
     */
    public abstract SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException;
    //
    // sync(regLock) {
    //   sync(keySet) { look for selector }
    //   if (channel found) { set interest ops -- may block in selector;
    //                        return key; }
    //   create new key -- may block somewhere in selector;
    //   sync(keySet) { add key; }
    //   attach(attachment);
    //   return key;
    // }

    /**
     * Registers this channel with the given selector, returning a selection
     * key.
     *
     * <p> An invocation of this convenience method of the form
     *
     * <blockquote><tt>sc.register(sel, ops)</tt></blockquote>
     *
     * behaves in exactly the same way as the invocation
     *
     * <blockquote><tt>sc.{@link
     * #register(java.nio.channels.Selector,int,java.lang.Object)
     * register}(sel, ops, null)</tt></blockquote>
     *
     * @param  sel
     *         The selector with which this channel is to be registered
     *
     * @param  ops
     *         The interest set for the resulting key
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  ClosedSelectorException
     *          If the selector is closed
     *
     * @throws  IllegalBlockingModeException
     *          If this channel is in blocking mode
     *
     * @throws  IllegalSelectorException
     *          If this channel was not created by the same provider
     *          as the given selector
     *
     * @throws  CancelledKeyException
     *          If this channel is currently registered with the given selector
     *          but the corresponding key has already been cancelled
     *
     * @throws  IllegalArgumentException
     *          If a bit in <tt>ops</tt> does not correspond to an operation
     *          that is supported by this channel, that is, if <tt>set &
     *          ~validOps() != 0</tt>
     *
     * @return  A key representing the registration of this channel with
     *          the given selector
     */
    public final SelectionKey register(Selector sel, int ops)
        throws ClosedChannelException
    {
        return register(sel, ops, null);
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> If this channel is registered with one or more selectors then an
     * attempt to place it into blocking mode will cause an {@link
     * IllegalBlockingModeException} to be thrown.
     *
     * <p> This method may be invoked at any time.  The new blocking mode will
     * only affect I/O operations that are initiated after this method returns.
     * For some implementations this may require blocking until all pending I/O
     * operations are complete.
     *
     * <p> If this method is invoked while another invocation of this method or
     * of the {@link #register(Selector, int) register} method is in progress
     * then it will first block until the other operation is complete. </p>
     *
     * @param  block  If <tt>true</tt> then this channel will be placed in
     *                blocking mode; if <tt>false</tt> then it will be placed
     *                non-blocking mode
     *
     * @return  This selectable channel
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  IllegalBlockingModeException
     *          If <tt>block</tt> is <tt>true</tt> and this channel is
     *          registered with one or more selectors
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    public abstract SelectableChannel configureBlocking(boolean block)
        throws IOException;
    //
    // sync(regLock) {
    //   sync(keySet) { throw IBME if block && isRegistered; }
    //   change mode;
    // }

    /**
     * Tells whether or not every I/O operation on this channel will block
     * until it completes.  A newly-created channel is always in blocking mode.
     *
     * <p> If this channel is closed then the value returned by this method is
     * not specified. </p>
     *
     * @return <tt>true</tt> if, and only if, this channel is in blocking mode
     */
    public abstract boolean isBlocking();

    /**
     * Retrieves the object upon which the {@link #configureBlocking
     * configureBlocking} and {@link #register register} methods synchronize.
     * This is often useful in the implementation of adaptors that require a
     * specific blocking mode to be maintained for a short period of time.
     * </p>
     *
     * @return  The blocking-mode lock object
     */
    public abstract Object blockingLock();

}
