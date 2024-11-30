/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides reference-object classes, which support a limited degree
 * of interaction with the garbage collector.  A program may use a
 * reference object to maintain a reference to some other object in
 * such a way that the latter object may still be reclaimed by the
 * collector.  A program may also arrange to be notified some time
 * after the collector has determined that the reachability of a given
 * object has changed.
 *
 * <h2>Reference Objects</h2>
 *
 * A <em>reference object</em> encapsulates a reference to some other
 * object so that the reference itself may be examined and manipulated
 * like any other object.  Three types of reference objects are
 * provided, each weaker than the last: <em>soft</em>, <em>weak</em>,
 * and <em>phantom</em>.  Each type corresponds to a different level
 * of reachability, as defined below.  Soft references are for
 * implementing memory-sensitive caches, weak references are for
 * implementing canonicalizing mappings that do not prevent their keys
 * (or values) from being reclaimed, and phantom references are for
 * scheduling post-mortem cleanup actions.
 * Post-mortem cleanup actions can also be registered and managed by a
 * {@link java.lang.ref.Cleaner}.
 *
 * <p> Each reference-object type is implemented by a subclass of the
 * abstract base {@link java.lang.ref.Reference} class.
 * An instance of one of these subclasses encapsulates a single
 * reference to a particular object, called the <em>referent</em>.
 * Every reference object provides methods for getting and clearing
 * the reference.  Aside from the clearing operation reference objects
 * are otherwise immutable, so no {@code set} operation is
 * provided.  A program may further subclass these subclasses, adding
 * whatever fields and methods are required for its purposes, or it
 * may use these subclasses without change.
 *
 * <a id="reachability"></a>
 * <h2>Reachability</h2>
 *
 * A <em>reachable</em> object is any object that can be accessed in any potential
 * continuing computation from any
 * {@linkplain java.lang.Thread#isAlive live thread} (as stated in JLS {@jls 12.6.1}).
 *
 * <p> Going from strongest to weakest, the different levels of
 * reachability reflect the life cycle of an object.  They are
 * operationally defined as follows:
 *
 * <ul>
 *
 * <li> An object is <em>strongly reachable</em> if it is reachable and if it
 * can be accessed without traversing the referent of a Reference object.
 *
 * <li> An object is <em>softly reachable</em> if it is not strongly
 * reachable but can be reached by traversing a soft reference.
 *
 * <li> An object is <em>weakly reachable</em> if it is neither
 * strongly nor softly reachable but can be reached by traversing a
 * weak reference.  When the weak references to a weakly-reachable
 * object are cleared, the object becomes eligible for finalization.
 *
 * <li> An object is <em>phantom reachable</em> if it is neither
 * strongly, softly, nor weakly reachable, it has been finalized, and
 * some phantom reference refers to it.
 *
 * <li> Finally, an object is <em>unreachable</em>, and therefore
 * eligible for reclamation, when it is not reachable in any of the
 * above ways.
 *
 * </ul>
 *
 * <h2>Notification</h2>
 *
 * A program may request to be notified of changes in an object's
 * reachability by <em>registering</em> an appropriate reference
 * object with a {@link java.lang.ref.ReferenceQueue}.
 * This is done by providing the reference queue as
 * a constructor argument when creating the reference object.
 * Some time after the garbage collector
 * determines that the reachability of the referent has changed to correspond
 * with the type of the reference, it will clear the
 * reference and add it to the associated queue.  At this point, the
 * reference is considered to be <em>enqueued</em>.  The program learns of the
 * referent's change in reachability when the associated reference becomes
 * available on the queue. The program may remove references from a queue
 * (that is, <em>dequeue</em> them) using the {@link ReferenceQueue#poll()} or
 * {@link ReferenceQueue#remove()} methods. Additional state needed to respond to a
 * referent's change in reachability can be stored in the fields of a custom
 * reference subclass, and accessed when the reference is returned from the
 * queue.
 *
 * <p> The relationship between a registered reference object and its
 * queue is one-sided.  That is, a queue does not keep track of the
 * references that are registered with it.  If a registered reference
 * becomes unreachable itself, then it will never be enqueued.  It is
 * the responsibility of the program to ensure
 * that reference objects remain reachable for as long as the program is
 * interested in their referents.
 *
 * <p> While some programs will choose to dedicate a thread to
 * removing reference objects from one or more queues and processing
 * them, this is by no means necessary.  A tactic that often works
 * well is to examine a reference queue in the course of performing
 * some other fairly-frequent action.  For example, a hashtable that
 * uses weak references to implement weak keys could poll its
 * reference queue each time the table is accessed.  This is how the
 * {@link java.util.WeakHashMap} class works.  Because
 * the {@link java.lang.ref.ReferenceQueue#poll
 * ReferenceQueue.poll} method simply checks an internal data
 * structure, this check will add little overhead to the hashtable
 * access methods.
 *
 * <a id="MemoryConsistency"></a>
 * <h2>Memory Consistency Properties</h2>
 * Certain interactions between references, reference queues, and the garbage
 * collector form
 * <a href="{@docRoot}/java.base/java/util/concurrent/package-summary.html#MemoryVisibility"><i>happens-before</i></a>
 * relationships:
 *
 * <ul>
 *
 * <li>Actions in a thread prior to calling
 * {@link Reference#reachabilityFence Reference.reachabilityFence(x)}
 * <i>happen-before</i> the garbage collector clears any reference to {@code x}.</li>
 *
 * <li>The clearing of a reference by the garbage collector <i>happens-before</i>
 * the garbage collector enqueues the reference.</li>
 *
 * <li>The enqueueing of a reference (by the garbage collector, or
 * by a successful call to {@link Reference#enqueue}) <i>happens-before</i>
 * the reference is removed from the queue (<em>dequeued</em>).</li>
 *
 * <li>The dequeuing of a reference to a
 * {@linkplain Cleaner#register(Object object, Runnable action) registered}
 * object, by the Cleaner thread, <i>happens-before</i> the Cleaner thread runs
 * the cleaning action for that object.</li>
 *
 * </ul>
 * The above chain of <i>happens-before</i> edges ensures that actions in a
 * thread prior to a {@link Reference#reachabilityFence Reference.reachabilityFence(x)}
 * <i>happen-before</i> cleanup code for {@code x} runs on a Cleaner thread.
 * In particular, changes to the state of {@code x} made before
 * {@code reachabilityFence(x)} will be visible to the cleanup code running on
 * a Cleaner thread without additional synchronization.
 * See JLS {@jls 17.4.5}.
 *
 * <p>
 * The interaction between references, finalizers, and the garbage collector
 * also forms a <em>happens-before</em> relationship:
 *
 * <ul>
 * <li>Actions in a thread prior to calling
 * {@link Reference#reachabilityFence Reference.reachabilityFence(x)}
 * <i>happen-before</i> the finalizer for {@code x} is run by a finalizer thread.</li>
 * </ul>
 *
 * This ensures that actions in a thread prior to a
 * {@link Reference#reachabilityFence Reference.reachabilityFence(x)}
 * <i>happen-before</i> cleanup code for {@code x} runs on a finalizer thread.
 * In particular, changes to the state of {@code x} made before
 * {@code reachabilityFence(x)} will be visible to the cleanup code running on
 * a finalizer thread without additional synchronization.
 * See JLS {@jls 17.4.5}.
 *
 * @author        Mark Reinhold
 * @since         1.2
 */
package java.lang.ref;
