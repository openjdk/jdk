/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.nio.channels.*;
import java.util.concurrent.*;
import java.security.AccessController;
import sun.security.action.GetIntegerAction;

/**
 * Defines static methods to invoke a completion handler or arbitrary task.
 */

class Invoker {
    private Invoker() { }

    // maximum number of completion handlers that may be invoked on the current
    // thread before it re-directs invocations to the thread pool. This helps
    // avoid stack overflow and lessens the risk of starvation.
    private static final int maxHandlerInvokeCount = AccessController.doPrivileged(
        new GetIntegerAction("sun.nio.ch.maxCompletionHandlersOnStack", 16));

    // Per-thread object with reference to channel group and a counter for
    // the number of completion handlers invoked. This should be reset to 0
    // when all completion handlers have completed.
    static class GroupAndInvokeCount {
        private final AsynchronousChannelGroupImpl group;
        private int handlerInvokeCount;
        GroupAndInvokeCount(AsynchronousChannelGroupImpl group) {
            this.group = group;
        }
        AsynchronousChannelGroupImpl group() {
            return group;
        }
        int invokeCount() {
            return handlerInvokeCount;
        }
        void setInvokeCount(int value) {
            handlerInvokeCount = value;
        }
        void resetInvokeCount() {
            handlerInvokeCount = 0;
        }
        void incrementInvokeCount() {
            handlerInvokeCount++;
        }
    }
    private static final ThreadLocal<GroupAndInvokeCount> myGroupAndInvokeCount =
        new ThreadLocal<GroupAndInvokeCount>() {
            @Override protected GroupAndInvokeCount initialValue() {
                return null;
            }
        };

    /**
     * Binds this thread to the given group
     */
    static void bindToGroup(AsynchronousChannelGroupImpl group) {
        myGroupAndInvokeCount.set(new GroupAndInvokeCount(group));
    }

    /**
     * Returns the GroupAndInvokeCount object for this thread.
     */
    static GroupAndInvokeCount getGroupAndInvokeCount() {
        return myGroupAndInvokeCount.get();
    }

    /**
     * Returns true if the current thread is in a channel group's thread pool
     */
    static boolean isBoundToAnyGroup() {
        return myGroupAndInvokeCount.get() != null;
    }

    /**
     * Returns true if the current thread is in the given channel's thread
     * pool and we haven't exceeded the maximum number of handler frames on
     * the stack.
     */
    static boolean mayInvokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
                                   AsynchronousChannelGroupImpl group)
    {
        if ((myGroupAndInvokeCount != null) &&
            (myGroupAndInvokeCount.group() == group) &&
            (myGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount))
        {
            return true;
        }
        return false;
    }

    /**
     * Invoke handler without checking the thread identity or number of handlers
     * on the thread stack.
     */
    @SuppressWarnings("unchecked")
    static <V,A> void invokeUnchecked(CompletionHandler<V,? super A> handler,
                                      AbstractFuture<V,A> result)
    {
        if (handler != null && !result.isCancelled()) {
            Throwable exc = result.exception();
            if (exc == null) {
                handler.completed(result.value(), result.attachment());
            } else {
                handler.failed(exc, result.attachment());
            }

            // clear interrupt
            Thread.interrupted();
        }
    }


    /**
     * Invoke handler after incrementing the invoke count.
     */
    static <V,A> void invokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
                                   CompletionHandler<V,? super A> handler,
                                   AbstractFuture<V,A> result)
    {
        myGroupAndInvokeCount.incrementInvokeCount();
        invokeUnchecked(handler, result);
    }

    /**
     * Invokes the handler. If the current thread is in the channel group's
     * thread pool then the handler is invoked directly, otherwise it is
     * invoked indirectly.
     */
    static <V,A> void invoke(CompletionHandler<V,? super A> handler,
                             AbstractFuture<V,A> result)
    {
        if (handler != null) {
            boolean invokeDirect = false;
            boolean identityOkay = false;
            GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
            if (thisGroupAndInvokeCount != null) {
                AsynchronousChannel channel = result.channel();
                if ((thisGroupAndInvokeCount.group() == ((Groupable)channel).group()))
                    identityOkay = true;
                if (identityOkay &&
                    (thisGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount))
                {
                    // group match
                    invokeDirect = true;
                }
            }
            if (invokeDirect) {
                thisGroupAndInvokeCount.incrementInvokeCount();
                invokeUnchecked(handler, result);
            } else {
                try {
                    invokeIndirectly(handler, result);
                } catch (RejectedExecutionException ree) {
                    // channel group shutdown; fallback to invoking directly
                    // if the current thread has the right identity.
                    if (identityOkay) {
                        invokeUnchecked(handler, result);
                    } else {
                        throw new ShutdownChannelGroupException();
                    }
                }
            }
        }
    }

    /**
     * Invokes the handler "indirectly" in the channel group's thread pool.
     */
    static <V,A> void invokeIndirectly(final CompletionHandler<V,? super A> handler,
                                       final AbstractFuture<V,A> result)
    {
        if (handler != null) {
            AsynchronousChannel channel = result.channel();
            try {
                ((Groupable)channel).group().executeOnPooledThread(new Runnable() {
                    public void run() {
                        GroupAndInvokeCount thisGroupAndInvokeCount =
                            myGroupAndInvokeCount.get();
                        if (thisGroupAndInvokeCount != null)
                            thisGroupAndInvokeCount.setInvokeCount(1);
                        invokeUnchecked(handler, result);
                    }
                });
            } catch (RejectedExecutionException ree) {
                throw new ShutdownChannelGroupException();
            }
        }
    }

    /**
     * Invokes the handler "indirectly" in the given Executor
     */
    static <V,A> void invokeIndirectly(final CompletionHandler<V,? super A> handler,
                                       final AbstractFuture<V,A> result,
                                       Executor executor)
    {
        if (handler != null) {
            try {
                executor.execute(new Runnable() {
                    public void run() {
                        invokeUnchecked(handler, result);
                    }
                });
            } catch (RejectedExecutionException ree) {
                throw new ShutdownChannelGroupException();
            }
        }
    }

    /**
     * Invokes the given task on the thread pool associated with the given
     * channel. If the current thread is in the thread pool then the task is
     * invoked directly.
     */
    static void invokeOnThreadInThreadPool(Groupable channel,
                                           Runnable task)
    {
        boolean invokeDirect;
        GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
        AsynchronousChannelGroupImpl targetGroup = channel.group();
        if (thisGroupAndInvokeCount == null) {
            invokeDirect = false;
        } else {
            invokeDirect = (thisGroupAndInvokeCount.group == targetGroup);
        }
        try {
            if (invokeDirect) {
                task.run();
            } else {
                targetGroup.executeOnPooledThread(task);
            }
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }
}
