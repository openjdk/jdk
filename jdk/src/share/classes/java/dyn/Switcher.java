/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

/**
 * <p>
 * A {@code Switcher} is an object which can publish state transitions to other threads.
 * A switcher is initially in the <em>valid</em> state, but may at any time be
 * changed to the <em>invalid</em> state.  Invalidation cannot be reversed.
 * <p>
 * A single switcher may be used to create any number of guarded method handle pairs.
 * Each guarded pair is wrapped in a new method handle {@code M},
 * which is permanently associated with the switcher that created it.
 * Each pair consists of a target {@code T} and a fallback {@code F}.
 * While the switcher is valid, invocations to {@code M} are delegated to {@code T}.
 * After it is invalidated, invocations are delegated to {@code F}.
 * <p>
 * Invalidation is global and immediate, as if the switcher contained a
 * volatile boolean variable consulted on every call to {@code M}.
 * The invalidation is also permanent, which means the switcher
 * can change state only once.
 * <p>
 * Here is an example of a switcher in action:
 * <blockquote><pre>
MethodType MT_str2 = MethodType.methodType(String.class, String.class);
MethodHandle MH_strcat = MethodHandles.lookup()
    .findVirtual(String.class, "concat", MT_str2);
Switcher switcher = new Switcher();
// the following steps may be repeated to re-use the same switcher:
MethodHandle worker1 = strcat;
MethodHandle worker2 = MethodHandles.permuteArguments(strcat, MT_str2, 1, 0);
MethodHandle worker = switcher.guardWithTest(worker1, worker2);
assertEquals("method", (String) worker.invokeExact("met", "hod"));
switcher.invalidate();
assertEquals("hodmet", (String) worker.invokeExact("met", "hod"));
 * </pre></blockquote>
 * <p>
 * <em>Implementation Note:</em>
 * A switcher behaves as if implemented on top of {@link MutableCallSite},
 * approximately as follows:
 * <blockquote><pre>
public class Switcher {
  private static final MethodHandle
    K_true  = MethodHandles.constant(boolean.class, true),
    K_false = MethodHandles.constant(boolean.class, false);
  private final MutableCallSite mcs;
  private final MethodHandle mcsInvoker;
  public Switcher() {
    this.mcs = new MutableCallSite(K_true);
    this.mcsInvoker = mcs.dynamicInvoker();
  }
  public MethodHandle guardWithTest(
                MethodHandle target, MethodHandle fallback) {
    // Note:  mcsInvoker is of type boolean().
    // Target and fallback may take any arguments, but must have the same type.
    return MethodHandles.guardWithTest(this.mcsInvoker, target, fallback);
  }
  public static void invalidateAll(Switcher[] switchers) {
    List<MutableCallSite> mcss = new ArrayList<>();
    for (Switcher s : switchers)  mcss.add(s.mcs);
    for (MutableCallSite mcs : mcss)  mcs.setTarget(K_false);
    MutableCallSite.sync(mcss.toArray(new MutableCallSite[0]));
  }
}
 * </pre></blockquote>
 * @author Remi Forax, JSR 292 EG
 */
public class Switcher {
    private static final MethodHandle
        K_true  = MethodHandles.constant(boolean.class, true),
        K_false = MethodHandles.constant(boolean.class, false);

    private final MutableCallSite mcs;
    private final MethodHandle mcsInvoker;

    /** Create a switcher. */
    public Switcher() {
        this.mcs = new MutableCallSite(K_true);
        this.mcsInvoker = mcs.dynamicInvoker();
    }

    /**
     * Return a method handle which always delegates either to the target or the fallback.
     * The method handle will delegate to the target exactly as long as the switcher is valid.
     * After that, it will permanently delegate to the fallback.
     * <p>
     * The target and fallback must be of exactly the same method type,
     * and the resulting combined method handle will also be of this type.
     * @see MethodHandles#guardWithTest
     */
    public MethodHandle guardWithTest(MethodHandle target, MethodHandle fallback) {
        if (mcs.getTarget() == K_false)
            return fallback;  // already invalid
        return MethodHandles.guardWithTest(mcsInvoker, target, fallback);
    }

    /** Set all of the given switchers into the invalid state. */
    public static void invalidateAll(Switcher[] switchers) {
        MutableCallSite[] sites = new MutableCallSite[switchers.length];
        int fillp = 0;
        for (Switcher switcher : switchers) {
            sites[fillp++] = switcher.mcs;
            switcher.mcs.setTarget(K_false);
        }
        MutableCallSite.sync(sites);
    }
}
