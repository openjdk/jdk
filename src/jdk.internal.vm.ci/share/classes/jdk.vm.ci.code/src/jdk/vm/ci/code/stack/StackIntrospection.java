/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.code.stack;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface StackIntrospection {

    /**
     * Walks the current stack, providing {@link InspectedFrame}s to the visitor that can be used to
     * inspect the stack frame's contents. Iteration continues as long as
     * {@link InspectedFrameVisitor#visitFrame}, which is invoked for every {@link InspectedFrame},
     * returns {@code null}. A non-null return value from {@link InspectedFrameVisitor#visitFrame}
     * indicates that frame iteration should stop.
     *
     * @param initialMethods if this is non-{@code null}, then the stack walk will start at the
     *            first frame whose method is one of these methods.
     * @param matchingMethods if this is non-{@code null}, then only frames whose methods are in
     *            this array are visited
     * @param initialSkip the number of matching methods to skip (including the initial method)
     * @param visitor the visitor that is called for every matching method
     * @return the last result returned by the visitor (which is non-null to indicate that iteration
     *         should stop), or null if the whole stack was iterated.
     */
    <T> T iterateFrames(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, InspectedFrameVisitor<T> visitor);

    /**
     * Returns a snapshot of the stack frames for each thread in {@code threads} up to and including
     * {@code limit}. The returned {@link InspectedFrame}s should be seen as a snapshot in the sense
     * that any client inspecting and possibly mutating the frame contents will do so under the
     * assumption that the underlying threads might have continued, executing potentially
     * invalidating the frame state.
     *
     * Note that the locals of the {@link InspectedFrame}s will be collected as copies when the
     * underlying frame was compiled, whereas they'll be references for interpreted frames. Use
     * {@link InspectedFrame#isVirtual} to determine if locals are virtual copies and
     * {@link InspectedFrame#materializeVirtualObjects} when a materialization is required. This
     * means that if the underlying thread continues to execute and a client later read locals, the
     * values will be either the current value or the value at collecting time. Any client that
     * requires a deterministic read from a local returned from this method, must do so while the
     * thread is suspended throughout executing this method and performing the locals read.
     *
     * Example use cases:
     *
     * A debugger needs access to the content of stack frames such as local variables.
     * In cases where threads execute in the runtime or in native code, it's not possible to obtain
     * a thread suspension hook, for which {@link StackIntrospection#iterateFrames} can be used on
     * the suspended thread. The getStackFrames method enables an immediate stack frames lookup
     * regardless of the status of the underlying (active) thread.
     *
     * Any tool or system that require taking stack frame snapshots will benefit from this method.
     * Whenever there is a need to fetch the stack frames from n threads at a fixed point in time,
     * this method provides a way to do so utilizing at most one single safe point.
     *
     * The following example shows how to read and manipulate an object stored at a known index in a
     * frame:
     * <pre>
     *     StackIntrospection si = ...;
     *     Thread[] suspendedThreads = ...;
     *     InspectedFrame[][] stacks = si.getStackFrames(null, null, 0, -1, suspendedThreads);
     *     for (InspectedFrame[] stack : stacks) {
     *         for (InspectedFrame frame : stack) {
     *             // read the (known MyObject) at index 0
     *             MyObject value = (MyObject) frame.getLocal(0);
     *             // manipulating the object requires the local to be materialized
     *             if (frame.isVirtual(0) {
     *                 // we got a copy of the MyObject, so materialize the frame
     *                 frame.materializeVirtualObjects(false);
     *             }
     *             value.manipulate();
     *         }
     *     }
     * </pre>
     *
     * @param initialMethods if this is non-{@code null}, then the stack walk will start at the
     *            first frame whose method is one of these methods.
     * @param matchingMethods if this is non-{@code null}, then only frames whose methods are in
     *            this array are walked
     * @param initialSkip the number of matching methods to skip (including the initial method)
     * @param limit limit the returned frames to this limit or when -1 return all frames
     * @param threads the input thread array
     * @return a two-dimensional array of stack frames in the order of the input threads array
     */
    InspectedFrame[][] getStackFrames(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, int limit, Thread[] threads);
}
