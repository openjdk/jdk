/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.beans.BeansLinker;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingDynamicLinkerExporter;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;
import jdk.dynalink.linker.support.Lookup;

/**
 * This is a dynalink pluggable linker (see http://openjdk.java.net/jeps/276).
 * This linker routes missing methods to Smalltalk-style doesNotUnderstand method.
 * Object of any Java class that implements MissingMethodHandler is handled by this linker.
 * For any method call, if a matching Java method is found, it is called. If there is no
 * method by that name, then MissingMethodHandler.doesNotUnderstand is called.
 */
public final class MissingMethodLinkerExporter extends GuardingDynamicLinkerExporter {
    static {
        System.out.println("pluggable dynalink missing method linker loaded");
    }

    // represents a MissingMethod - just stores as name and also serves a guard type
    public static class MissingMethod {
        private final String name;

        public MissingMethod(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // MissingMethodHandler.doesNotUnderstand method
    private static final MethodHandle DOES_NOT_UNDERSTAND;

    // type of MissingMethodHandler - but "this" and String args are flipped
    private static final MethodType FLIPPED_DOES_NOT_UNDERSTAND_TYPE;

    // "is this a MissingMethod?" guard
    private static final MethodHandle IS_MISSING_METHOD;

    // MissingMethod object->it's name filter
    private static final MethodHandle MISSING_METHOD_TO_NAME;

    static {
        DOES_NOT_UNDERSTAND = Lookup.PUBLIC.findVirtual(
            MissingMethodHandler.class,
            "doesNotUnderstand",
            MethodType.methodType(Object.class, String.class, Object[].class));
        FLIPPED_DOES_NOT_UNDERSTAND_TYPE =
            MethodType.methodType(Object.class, String.class, MissingMethodHandler.class, Object[].class);
        IS_MISSING_METHOD = Guards.isOfClass(MissingMethod.class,
            MethodType.methodType(Boolean.TYPE, Object.class));
        MISSING_METHOD_TO_NAME = Lookup.PUBLIC.findVirtual(MissingMethod.class,
            "getName", MethodType.methodType(String.class));
    }

    @Override
    public List<GuardingDynamicLinker> get() {
        final ArrayList<GuardingDynamicLinker> linkers = new ArrayList<>();
        final BeansLinker beansLinker = new BeansLinker();
        linkers.add(new TypeBasedGuardingDynamicLinker() {
            // only handles MissingMethodHandler and MissingMethod objects
            @Override
            public boolean canLinkType(final Class<?> type) {
                return
                    MissingMethodHandler.class.isAssignableFrom(type) ||
                    type == MissingMethod.class;
            }

            @Override
            public GuardedInvocation getGuardedInvocation(final LinkRequest request,
                final LinkerServices linkerServices) throws Exception {
                final Object self = request.getReceiver();
                final CallSiteDescriptor desc = request.getCallSiteDescriptor();

                // any method call is done by two steps. Step (1) GET_METHOD and (2) is CALL
                // For step (1), we check if GET_METHOD can succeed by Java linker, if so
                // we return that method object. If not, we return a MissingMethod object.
                if (self instanceof MissingMethodHandler) {
                    // Check if this is a named GET_METHOD first.
                    final Operation namedOp = desc.getOperation();
                    final Operation namespaceOp = NamedOperation.getBaseOperation(namedOp);
                    final Operation op = NamespaceOperation.getBaseOperation(namespaceOp);

                    final boolean isGetMethod = op == StandardOperation.GET && StandardNamespace.findFirst(namespaceOp) == StandardNamespace.METHOD;
                    final Object name = NamedOperation.getName(namedOp);
                    if (isGetMethod && name instanceof String) {
                        final GuardingDynamicLinker javaLinker = beansLinker.getLinkerForClass(self.getClass());
                        GuardedInvocation inv;
                        try {
                            inv = javaLinker.getGuardedInvocation(request, linkerServices);
                        } catch (final Throwable th) {
                            inv = null;
                        }

                        final String nameStr = name.toString();
                        if (inv == null) {
                            // use "this" for just guard and drop it -- return a constant Method handle
                            // that returns a newly created MissingMethod object
                            final MethodHandle mh = MethodHandles.constant(Object.class, new MissingMethod(nameStr));
                            inv = new GuardedInvocation(
                                MethodHandles.dropArguments(mh, 0, Object.class),
                                Guards.isOfClass(self.getClass(), MethodType.methodType(Boolean.TYPE, Object.class)));
                        }

                        return inv;
                    }
                } else if (self instanceof MissingMethod) {
                    // This is step (2). We call MissingMethodHandler.doesNotUnderstand here
                    // Check if this is this a CALL first.
                    final boolean isCall = NamedOperation.getBaseOperation(desc.getOperation()) == StandardOperation.CALL;
                    if (isCall) {
                        MethodHandle mh = DOES_NOT_UNDERSTAND;

                        // flip "this" and method name (String)
                        mh = MethodHandles.permuteArguments(mh, FLIPPED_DOES_NOT_UNDERSTAND_TYPE, 1, 0, 2);

                        // collect rest of the arguments as vararg
                        mh = mh.asCollector(Object[].class, desc.getMethodType().parameterCount() - 2);

                        // convert MissingMethod object to it's name
                        mh = MethodHandles.filterArguments(mh, 0, MISSING_METHOD_TO_NAME);
                        return new GuardedInvocation(mh, IS_MISSING_METHOD);
                    }
                }

                return null;
            }
        });
        return linkers;
    }
}
