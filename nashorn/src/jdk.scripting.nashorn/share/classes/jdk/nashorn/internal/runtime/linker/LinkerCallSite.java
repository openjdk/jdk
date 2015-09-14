/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import jdk.internal.dynalink.ChainedCallSite;
import jdk.internal.dynalink.DynamicLinker;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.options.Options;


/**
 * Relinkable form of call site.
 */
public class LinkerCallSite extends ChainedCallSite {
    /** Maximum number of arguments passed directly. */
    public static final int ARGLIMIT = 250;

    private static final String PROFILEFILE = Options.getStringProperty("nashorn.profilefile", "NashornProfile.txt");

    private static final MethodHandle INCREASE_MISS_COUNTER = MH.findStatic(MethodHandles.lookup(), LinkerCallSite.class, "increaseMissCount", MH.type(Object.class, String.class, Object.class));
    private static final MethodHandle ON_CATCH_INVALIDATION = MH.findStatic(MethodHandles.lookup(), LinkerCallSite.class, "onCatchInvalidation", MH.type(ChainedCallSite.class, LinkerCallSite.class));

    private int catchInvalidations;

    LinkerCallSite(final NashornCallSiteDescriptor descriptor) {
        super(descriptor);
        if (Context.DEBUG) {
            LinkerCallSite.count.increment();
        }
    }

    @Override
    protected MethodHandle getPruneCatches() {
        return MH.filterArguments(super.getPruneCatches(), 0, ON_CATCH_INVALIDATION);
    }

    /**
     * Action to perform when a catch guard around a callsite triggers. Increases
     * catch invalidation counter
     * @param callSite callsite
     * @return the callsite, so this can be used as argument filter
     */
    @SuppressWarnings("unused")
    private static ChainedCallSite onCatchInvalidation(final LinkerCallSite callSite) {
        ++callSite.catchInvalidations;
        return callSite;
    }

    /**
     * Get the number of catch invalidations that have happened at this call site so far
     * @param callSiteToken call site token, unique to the callsite.
     * @return number of catch invalidations, i.e. thrown exceptions caught by the linker
     */
    public static int getCatchInvalidationCount(final Object callSiteToken) {
        if (callSiteToken instanceof LinkerCallSite) {
            return ((LinkerCallSite)callSiteToken).catchInvalidations;
        }
        return 0;
    }
    /**
     * Construct a new linker call site.
     * @param name     Name of method.
     * @param type     Method type.
     * @param flags    Call site specific flags.
     * @return New LinkerCallSite.
     */
    static LinkerCallSite newLinkerCallSite(final MethodHandles.Lookup lookup, final String name, final MethodType type, final int flags) {
        final NashornCallSiteDescriptor desc = NashornCallSiteDescriptor.get(lookup, name, type, flags);

        if (desc.isProfile()) {
            return ProfilingLinkerCallSite.newProfilingLinkerCallSite(desc);
        }

        if (desc.isTrace()) {
            return new TracingLinkerCallSite(desc);
        }

        return new LinkerCallSite(desc);
    }

    @Override
    public String toString() {
        return getDescriptor().toString();
    }

    /**
     * Get the descriptor for this callsite
     * @return a {@link NashornCallSiteDescriptor}
     */
    public NashornCallSiteDescriptor getNashornDescriptor() {
        return (NashornCallSiteDescriptor)getDescriptor();
    }

    @Override
    public void relink(final GuardedInvocation invocation, final MethodHandle relink) {
        super.relink(invocation, getDebuggingRelink(relink));
    }

    @Override
    public void resetAndRelink(final GuardedInvocation invocation, final MethodHandle relink) {
        super.resetAndRelink(invocation, getDebuggingRelink(relink));
    }

    private MethodHandle getDebuggingRelink(final MethodHandle relink) {
        if (Context.DEBUG) {
            return MH.filterArguments(relink, 0, getIncreaseMissCounter(relink.type().parameterType(0)));
        }
        return relink;
    }

    private MethodHandle getIncreaseMissCounter(final Class<?> type) {
        final MethodHandle missCounterWithDesc = MH.bindTo(INCREASE_MISS_COUNTER, getDescriptor().getName() + " @ " + getScriptLocation());
        if (type == Object.class) {
            return missCounterWithDesc;
        }
        return MH.asType(missCounterWithDesc, missCounterWithDesc.type().changeParameterType(0, type).changeReturnType(type));
    }

    private static String getScriptLocation() {
        final StackTraceElement caller = DynamicLinker.getLinkedCallSiteLocation();
        return caller == null ? "unknown location" : (caller.getFileName() + ":" + caller.getLineNumber());
    }

    /**
     * Instrumentation - increase the miss count when a callsite misses. Used as filter
     * @param desc descriptor for table entry
     * @param self self reference
     * @return self reference
     */
    public static Object increaseMissCount(final String desc, final Object self) {
        missCount.increment();
        if (r.nextInt(100) < missSamplingPercentage) {
            final AtomicInteger i = missCounts.get(desc);
            if (i == null) {
                missCounts.put(desc, new AtomicInteger(1));
            } else {
                i.incrementAndGet();
            }
        }
        return self;
    }

    /*
     * Debugging call sites.
     */

    private static class ProfilingLinkerCallSite extends LinkerCallSite {
        /** List of all profiled call sites. */
        private static LinkedList<ProfilingLinkerCallSite> profileCallSites = null;

        /** Start time when entered at zero depth. */
        private long startTime;

        /** Depth of nested calls. */
        private int depth;

        /** Total time spent in this call site. */
        private long totalTime;

        /** Total number of times call site entered. */
        private long hitCount;

        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private static final MethodHandle PROFILEENTRY    = MH.findVirtual(LOOKUP, ProfilingLinkerCallSite.class, "profileEntry",    MH.type(Object.class, Object.class));
        private static final MethodHandle PROFILEEXIT     = MH.findVirtual(LOOKUP, ProfilingLinkerCallSite.class, "profileExit",     MH.type(Object.class, Object.class));
        private static final MethodHandle PROFILEVOIDEXIT = MH.findVirtual(LOOKUP, ProfilingLinkerCallSite.class, "profileVoidExit", MH.type(void.class));

        /*
         * Constructor
         */

        ProfilingLinkerCallSite(final NashornCallSiteDescriptor desc) {
           super(desc);
        }

        public static ProfilingLinkerCallSite newProfilingLinkerCallSite(final NashornCallSiteDescriptor desc) {
            if (profileCallSites == null) {
                profileCallSites = new LinkedList<>();

                final Thread profileDumperThread = new Thread(new ProfileDumper());
                Runtime.getRuntime().addShutdownHook(profileDumperThread);
            }

            final ProfilingLinkerCallSite callSite = new ProfilingLinkerCallSite(desc);
            profileCallSites.add(callSite);

            return callSite;
        }

        @Override
        public void setTarget(final MethodHandle newTarget) {
            final MethodType type   = type();
            final boolean    isVoid = type.returnType() == void.class;

            MethodHandle methodHandle = MH.filterArguments(newTarget, 0, MH.bindTo(PROFILEENTRY, this));

            if (isVoid) {
                methodHandle = MH.filterReturnValue(methodHandle, MH.bindTo(PROFILEVOIDEXIT, this));
            } else {
                final MethodType filter = MH.type(type.returnType(), type.returnType());
                methodHandle = MH.filterReturnValue(methodHandle, MH.asType(MH.bindTo(PROFILEEXIT, this), filter));
            }

            super.setTarget(methodHandle);
        }

        /**
         * Start the clock for a profile entry and increase depth
         * @param self argument to filter
         * @return preserved argument
         */
        @SuppressWarnings("unused")
        public Object profileEntry(final Object self) {
            if (depth == 0) {
                startTime = System.nanoTime();
            }

            depth++;
            hitCount++;

            return self;
        }

        /**
         * Decrease depth and stop the clock for a profile entry
         * @param result return value to filter
         * @return preserved argument
         */
        @SuppressWarnings("unused")
        public Object profileExit(final Object result) {
            depth--;

            if (depth == 0) {
                totalTime += System.nanoTime() - startTime;
            }

            return result;
        }

        /**
         * Decrease depth without return value filter
         */
        @SuppressWarnings("unused")
        public void profileVoidExit() {
            depth--;

            if (depth == 0) {
                totalTime += System.nanoTime() - startTime;
            }
        }

        static class ProfileDumper implements Runnable {
            @Override
            public void run() {
                PrintWriter out    = null;
                boolean fileOutput = false;

                try {
                    try {
                        out = new PrintWriter(new FileOutputStream(PROFILEFILE));
                        fileOutput = true;
                    } catch (final FileNotFoundException e) {
                        out = Context.getCurrentErr();
                    }

                    dump(out);
                } finally {
                    if (out != null && fileOutput) {
                        out.close();
                    }
                }
            }

            private static void dump(final PrintWriter out) {
                int index = 0;
                for (final ProfilingLinkerCallSite callSite : profileCallSites) {
                   out.println("" + (index++) + '\t' +
                                  callSite.getDescriptor().getName() + '\t' +
                                  callSite.totalTime + '\t' +
                                  callSite.hitCount);
                }
            }
        }
    }

    /**
     * Debug subclass for LinkerCallSite that allows tracing
     */
    private static class TracingLinkerCallSite extends LinkerCallSite {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private static final MethodHandle TRACEOBJECT = MH.findVirtual(LOOKUP, TracingLinkerCallSite.class, "traceObject", MH.type(Object.class, MethodHandle.class, Object[].class));
        private static final MethodHandle TRACEVOID   = MH.findVirtual(LOOKUP, TracingLinkerCallSite.class, "traceVoid", MH.type(void.class, MethodHandle.class, Object[].class));
        private static final MethodHandle TRACEMISS   = MH.findVirtual(LOOKUP, TracingLinkerCallSite.class, "traceMiss", MH.type(void.class, String.class, Object[].class));

        TracingLinkerCallSite(final NashornCallSiteDescriptor desc) {
           super(desc);
        }

        @Override
        public void setTarget(final MethodHandle newTarget) {
            if (!getNashornDescriptor().isTraceEnterExit()) {
                super.setTarget(newTarget);
                return;
            }

            final MethodType type = type();
            final boolean isVoid = type.returnType() == void.class;

            MethodHandle traceMethodHandle = isVoid ? TRACEVOID : TRACEOBJECT;
            traceMethodHandle = MH.bindTo(traceMethodHandle, this);
            traceMethodHandle = MH.bindTo(traceMethodHandle, newTarget);
            traceMethodHandle = MH.asCollector(traceMethodHandle, Object[].class, type.parameterCount());
            traceMethodHandle = MH.asType(traceMethodHandle, type);

            super.setTarget(traceMethodHandle);
        }

        @Override
        public void initialize(final MethodHandle relinkAndInvoke) {
            super.initialize(getFallbackLoggingRelink(relinkAndInvoke));
        }

        @Override
        public void relink(final GuardedInvocation invocation, final MethodHandle relink) {
            super.relink(invocation, getFallbackLoggingRelink(relink));
        }

        @Override
        public void resetAndRelink(final GuardedInvocation invocation, final MethodHandle relink) {
            super.resetAndRelink(invocation, getFallbackLoggingRelink(relink));
        }

        private MethodHandle getFallbackLoggingRelink(final MethodHandle relink) {
            if (!getNashornDescriptor().isTraceMisses()) {
                // If we aren't tracing misses, just return relink as-is
                return relink;
            }
            final MethodType type = relink.type();
            return MH.foldArguments(relink, MH.asType(MH.asCollector(MH.insertArguments(TRACEMISS, 0, this, "MISS " + getScriptLocation() + " "), Object[].class, type.parameterCount()), type.changeReturnType(void.class)));
        }

        private void printObject(final PrintWriter out, final Object arg) {
            if (!getNashornDescriptor().isTraceObjects()) {
                out.print((arg instanceof ScriptObject) ? "ScriptObject" : arg);
                return;
            }

            if (arg instanceof ScriptObject) {
                final ScriptObject object = (ScriptObject)arg;

                boolean isFirst = true;
                final Set<Object> keySet = object.keySet();

                if (keySet.isEmpty()) {
                    out.print(ScriptRuntime.safeToString(arg));
                } else {
                    out.print("{ ");

                    for (final Object key : keySet) {
                        if (!isFirst) {
                            out.print(", ");
                        }

                        out.print(key);
                        out.print(":");

                        final Object value = object.get(key);

                        if (value instanceof ScriptObject) {
                            out.print("...");
                        } else {
                            printObject(out, value);
                        }

                        isFirst = false;
                    }

                    out.print(" }");
                }
             } else {
                out.print(ScriptRuntime.safeToString(arg));
            }
        }

        private void tracePrint(final PrintWriter out, final String tag, final Object[] args, final Object result) {
            //boolean isVoid = type().returnType() == void.class;
            out.print(Debug.id(this) + " TAG " + tag);
            out.print(getDescriptor().getName() + "(");

            if (args.length > 0) {
                printObject(out, args[0]);
                for (int i = 1; i < args.length; i++) {
                    final Object arg = args[i];
                    out.print(", ");

                    if (!(arg instanceof ScriptObject && ((ScriptObject)arg).isScope())) {
                        printObject(out, arg);
                    } else {
                        out.print("SCOPE");
                    }
                }
            }

            out.print(")");

            if (tag.equals("EXIT  ")) {
                out.print(" --> ");
                printObject(out, result);
            }

            out.println();
        }

        /**
         * Trace event. Wrap an invocation with a return value
         *
         * @param mh     invocation handle
         * @param args   arguments to call
         *
         * @return return value from invocation
         *
         * @throws Throwable if invocation fails or throws exception/error
         */
        @SuppressWarnings("unused")
        public Object traceObject(final MethodHandle mh, final Object... args) throws Throwable {
            final PrintWriter out = Context.getCurrentErr();
            tracePrint(out, "ENTER ", args, null);
            final Object result = mh.invokeWithArguments(args);
            tracePrint(out, "EXIT  ", args, result);

            return result;
        }

        /**
         * Trace event. Wrap an invocation that returns void
         *
         * @param mh     invocation handle
         * @param args   arguments to call
         *
         * @throws Throwable if invocation fails or throws exception/error
         */
        @SuppressWarnings("unused")
        public void traceVoid(final MethodHandle mh, final Object... args) throws Throwable {
            final PrintWriter out = Context.getCurrentErr();
            tracePrint(out, "ENTER ", args, null);
            mh.invokeWithArguments(args);
            tracePrint(out, "EXIT  ", args, null);
        }

        /**
         * Tracer function that logs a callsite miss
         *
         * @param desc callsite descriptor string
         * @param args arguments to function
         *
         * @throws Throwable if invocation fails or throws exception/error
         */
        @SuppressWarnings("unused")
        public void traceMiss(final String desc, final Object... args) throws Throwable {
            tracePrint(Context.getCurrentErr(), desc, args, null);
        }
    }

    // counters updated in debug mode
    private static LongAdder count;
    private static final HashMap<String, AtomicInteger> missCounts = new HashMap<>();
    private static LongAdder missCount;
    private static final Random r = new Random();
    private static final int missSamplingPercentage = Options.getIntProperty("nashorn.tcs.miss.samplePercent", 1);

    static {
        if (Context.DEBUG) {
            count = new LongAdder();
            missCount = new LongAdder();
        }
    }

    @Override
    protected int getMaxChainLength() {
        return 8;
    }

    /**
     * Get the callsite count
     * @return the count
     */
    public static long getCount() {
        return count.longValue();
    }

    /**
     * Get the callsite miss count
     * @return the missCount
     */
    public static long getMissCount() {
        return missCount.longValue();
    }

    /**
     * Get given miss sampling percentage for sampler. Default is 1%. Specified with -Dnashorn.tcs.miss.samplePercent=x
     * @return miss sampling percentage
     */
    public static int getMissSamplingPercentage() {
        return missSamplingPercentage;
    }

    /**
     * Dump the miss counts collected so far to a given output stream
     * @param out print stream
     */
    public static void getMissCounts(final PrintWriter out) {
        final ArrayList<Entry<String, AtomicInteger>> entries = new ArrayList<>(missCounts.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<String, AtomicInteger>>() {
            @Override
            public int compare(final Entry<String, AtomicInteger> o1, final Entry<String, AtomicInteger> o2) {
                return o2.getValue().get() - o1.getValue().get();
            }
        });

        for (final Entry<String, AtomicInteger> entry : entries) {
            out.println("  " + entry.getKey() + "\t" + entry.getValue().get());
        }
    }

}
