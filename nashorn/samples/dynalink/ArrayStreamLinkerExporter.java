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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.CompositeOperation;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingDynamicLinkerExporter;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;
import jdk.dynalink.linker.support.Lookup;

/**
 * This is a dynalink pluggable linker (see http://openjdk.java.net/jeps/276).
 * This linker adds "stream" property to Java arrays. The appropriate Stream
 * type object is returned for "stream" property on Java arrays. Note that
 * the dynalink beans linker just adds "length" property and Java array objects
 * don't have any other property. "stream" property does not conflict with anything
 * else!
 */
public final class ArrayStreamLinkerExporter extends GuardingDynamicLinkerExporter {
    static {
        System.out.println("pluggable dynalink array stream linker loaded");
    }

    public static Object arrayToStream(Object array) {
        if (array instanceof int[]) {
            return IntStream.of((int[])array);
        } else if (array instanceof long[]) {
            return LongStream.of((long[])array);
        } else if (array instanceof double[]) {
            return DoubleStream.of((double[])array);
        } else if (array instanceof Object[]) {
            return Stream.of((Object[])array);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static final MethodType GUARD_TYPE = MethodType.methodType(Boolean.TYPE, Object.class);
    private static final MethodHandle ARRAY_TO_STREAM = Lookup.PUBLIC.findStatic(
            ArrayStreamLinkerExporter.class, "arrayToStream",
            MethodType.methodType(Object.class, Object.class));

    @Override
    public List<GuardingDynamicLinker> get() {
        final ArrayList<GuardingDynamicLinker> linkers = new ArrayList<>();
        linkers.add(new TypeBasedGuardingDynamicLinker() {
            @Override
            public boolean canLinkType(final Class<?> type) {
                return type == Object[].class || type == int[].class ||
                       type == long[].class || type == double[].class;
            }

            @Override
            public GuardedInvocation getGuardedInvocation(LinkRequest request,
                LinkerServices linkerServices) throws Exception {
                final Object self = request.getReceiver();
                if (self == null || !canLinkType(self.getClass())) {
                    return null;
                }

                CallSiteDescriptor desc = request.getCallSiteDescriptor();
                Operation op = desc.getOperation();
                Object name = NamedOperation.getName(op);
                boolean getProp = CompositeOperation.contains(
                        NamedOperation.getBaseOperation(op),
                        StandardOperation.GET_PROPERTY);
                if (getProp && "stream".equals(name)) {
                    return new GuardedInvocation(ARRAY_TO_STREAM,
                        Guards.isOfClass(self.getClass(), GUARD_TYPE));
                }

                return null;
            }
        });
        return linkers;
    }
}
