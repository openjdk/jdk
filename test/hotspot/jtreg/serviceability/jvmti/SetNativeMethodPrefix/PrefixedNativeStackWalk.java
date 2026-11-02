/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8389611
 * @summary Walking the stack over a JVMTI-prefixed native method must not
 *          underflow the prefix length when the calling method has a longer name.
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:PrefixedNativeStackWalk PrefixedNativeStackWalk
 */

/*
 * Regression test for the unsigned underflow in
 * vframeStreamCommon::skip_prefixed_method_and_wrappers() (vframe.cpp).
 *
 * The agent registers "wrapped_" as a native method prefix, so resolving the
 * native wrapped_go() strips the prefix, finds the non-native go() and binds
 * wrapped_go() to go()'s native entry point. That marks wrapped_go() as a
 * prefixed native method, which is what makes a security stack walk enter
 * skip_prefixed_method_and_wrappers().
 *
 * While the native method runs, the stack is
 *
 *     wrapped_go()                        <- prefixed native
 *     go()                                <- wrapper
 *     callerFrameWithALongerMethodName()  <- same class, longer name
 *     main()
 *
 * and the native code asks the VM for the calling class at depth 1 (see the
 * library) to make it walk that chain. After the wrapper frame is matched the
 * name being stripped is "go", and the next frame in the same class has a
 * longer name, so
 *
 *     size_t prefix_len = prefixed_name_len - name_len;
 *
 * underflows. The old guard, prefix_len <= 0, is never true for an unsigned
 * type, so strcmp() went on to read at prefixed_name + prefix_len, i.e. before
 * the string. The fixed code stops as soon as the name is not shorter.
 *
 * Note that the over-read is a single byte inside the resource area, so an
 * unfixed VM does not reliably fail here; it is undefined behaviour that shows
 * up under a sanitizer build. What this test always covers is the path itself:
 * the walk must terminate at the expected frame and the VM must stay healthy.
 */

public class PrefixedNativeStackWalk {

    // Resolved through the "wrapped_" prefix registered by the agent, so this
    // ends up bound to Java_PrefixedNativeStackWalk_go() and is flagged as a
    // prefixed native method.
    private static native boolean wrapped_go();

    // The wrapper method the prefix mechanism looks for: same class, same
    // signature, not native.
    private static boolean go() {
        return wrapped_go();
    }

    // Calls the wrapper from the same class under a longer name. The length
    // difference to go() is what used to make the prefix length underflow, so
    // this method must keep a name longer than the wrapper's.
    private static boolean callerFrameWithALongerMethodName() {
        return go();
    }

    public static void main(String[] args) {
        if (!callerFrameWithALongerMethodName()) {
            throw new RuntimeException("Stack walk over the prefixed native method failed");
        }
        System.out.println("Test PASSED");
    }
}
