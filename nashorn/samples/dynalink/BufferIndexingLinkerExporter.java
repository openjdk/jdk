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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
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
 * This linker adds array-like indexing and "length" property to nio Buffer objects.
 */
public final class BufferIndexingLinkerExporter extends GuardingDynamicLinkerExporter {
    static {
        System.out.println("pluggable dynalink buffer indexing linker loaded");
    }

    private static final MethodHandle BUFFER_LIMIT;
    private static final MethodHandle BYTEBUFFER_GET;
    private static final MethodHandle BYTEBUFFER_PUT;
    private static final MethodHandle CHARBUFFER_GET;
    private static final MethodHandle CHARBUFFER_PUT;
    private static final MethodHandle SHORTBUFFER_GET;
    private static final MethodHandle SHORTBUFFER_PUT;
    private static final MethodHandle INTBUFFER_GET;
    private static final MethodHandle INTBUFFER_PUT;
    private static final MethodHandle LONGBUFFER_GET;
    private static final MethodHandle LONGBUFFER_PUT;
    private static final MethodHandle FLOATBUFFER_GET;
    private static final MethodHandle FLOATBUFFER_PUT;
    private static final MethodHandle DOUBLEBUFFER_GET;
    private static final MethodHandle DOUBLEBUFFER_PUT;

    // guards
    private static final MethodHandle IS_BUFFER;
    private static final MethodHandle IS_BYTEBUFFER;
    private static final MethodHandle IS_CHARBUFFER;
    private static final MethodHandle IS_SHORTBUFFER;
    private static final MethodHandle IS_INTBUFFER;
    private static final MethodHandle IS_LONGBUFFER;
    private static final MethodHandle IS_FLOATBUFFER;
    private static final MethodHandle IS_DOUBLEBUFFER;

    private static final MethodType GUARD_TYPE;

    static {
        Lookup look = Lookup.PUBLIC;
        BUFFER_LIMIT = look.findVirtual(Buffer.class, "limit", MethodType.methodType(int.class));
        BYTEBUFFER_GET = look.findVirtual(ByteBuffer.class, "get",
                MethodType.methodType(byte.class, int.class));
        BYTEBUFFER_PUT = look.findVirtual(ByteBuffer.class, "put",
                MethodType.methodType(ByteBuffer.class, int.class, byte.class));
        CHARBUFFER_GET = look.findVirtual(CharBuffer.class, "get",
                MethodType.methodType(char.class, int.class));
        CHARBUFFER_PUT = look.findVirtual(CharBuffer.class, "put",
                MethodType.methodType(CharBuffer.class, int.class, char.class));
        SHORTBUFFER_GET = look.findVirtual(ShortBuffer.class, "get",
                MethodType.methodType(short.class, int.class));
        SHORTBUFFER_PUT = look.findVirtual(ShortBuffer.class, "put",
                MethodType.methodType(ShortBuffer.class, int.class, short.class));
        INTBUFFER_GET = look.findVirtual(IntBuffer.class, "get",
                MethodType.methodType(int.class, int.class));
        INTBUFFER_PUT = look.findVirtual(IntBuffer.class, "put",
                MethodType.methodType(IntBuffer.class, int.class, int.class));
        LONGBUFFER_GET = look.findVirtual(LongBuffer.class, "get",
                MethodType.methodType(long.class, int.class));
        LONGBUFFER_PUT = look.findVirtual(LongBuffer.class, "put",
                MethodType.methodType(LongBuffer.class, int.class, long.class));
        FLOATBUFFER_GET = look.findVirtual(FloatBuffer.class, "get",
                MethodType.methodType(float.class, int.class));
        FLOATBUFFER_PUT = look.findVirtual(FloatBuffer.class, "put",
                MethodType.methodType(FloatBuffer.class, int.class, float.class));
        DOUBLEBUFFER_GET = look.findVirtual(DoubleBuffer.class, "get",
                MethodType.methodType(double.class, int.class));
        DOUBLEBUFFER_PUT = look.findVirtual(DoubleBuffer.class, "put",
                MethodType.methodType(DoubleBuffer.class, int.class, double.class));

        GUARD_TYPE = MethodType.methodType(boolean.class, Object.class);
        IS_BUFFER = Guards.isInstance(Buffer.class, GUARD_TYPE);
        IS_BYTEBUFFER = Guards.isInstance(ByteBuffer.class, GUARD_TYPE);
        IS_CHARBUFFER = Guards.isInstance(CharBuffer.class, GUARD_TYPE);
        IS_SHORTBUFFER = Guards.isInstance(ShortBuffer.class, GUARD_TYPE);
        IS_INTBUFFER = Guards.isInstance(IntBuffer.class, GUARD_TYPE);
        IS_LONGBUFFER = Guards.isInstance(LongBuffer.class, GUARD_TYPE);
        IS_FLOATBUFFER = Guards.isInstance(FloatBuffer.class, GUARD_TYPE);
        IS_DOUBLEBUFFER = Guards.isInstance(DoubleBuffer.class, GUARD_TYPE);
    }

    // locate the first standard operation from the call descriptor
    private static StandardOperation getFirstStandardOperation(final CallSiteDescriptor desc) {
        final Operation base = NamedOperation.getBaseOperation(desc.getOperation());
        if (base instanceof StandardOperation) {
            return (StandardOperation)base;
        } else if (base instanceof CompositeOperation) {
            final CompositeOperation cop = (CompositeOperation)base;
            for(int i = 0; i < cop.getOperationCount(); ++i) {
                final Operation op = cop.getOperation(i);
                if (op instanceof StandardOperation) {
                    return (StandardOperation)op;
                }
            }
        }
        return null;
    }

    @Override
    public List<GuardingDynamicLinker> get() {
        final ArrayList<GuardingDynamicLinker> linkers = new ArrayList<>();
        linkers.add(new TypeBasedGuardingDynamicLinker() {
            @Override
            public boolean canLinkType(final Class<?> type) {
                return Buffer.class.isAssignableFrom(type);
            }

            @Override
            public GuardedInvocation getGuardedInvocation(LinkRequest request,
                LinkerServices linkerServices) throws Exception {
                final Object self = request.getReceiver();
                if (self == null || !canLinkType(self.getClass())) {
                    return null;
                }

                CallSiteDescriptor desc = request.getCallSiteDescriptor();
                StandardOperation op = getFirstStandardOperation(desc);
                if (op == null) {
                    return null;
                }

                switch (op) {
                    case GET_ELEMENT:
                        return linkGetElement(self);
                    case SET_ELEMENT:
                        return linkSetElement(self);
                    case GET_PROPERTY: {
                        Object name = NamedOperation.getName(desc.getOperation());
                        if ("length".equals(name)) {
                            return linkLength();
                        }
                    }
                }

                return null;
            }
        });
        return linkers;
    }

    private static GuardedInvocation linkGetElement(Object self) {
        MethodHandle method = null;
        MethodHandle guard = null;
        if (self instanceof ByteBuffer) {
            method = BYTEBUFFER_GET;
            guard = IS_BYTEBUFFER;
        } else if (self instanceof CharBuffer) {
            method = CHARBUFFER_GET;
            guard = IS_CHARBUFFER;
        } else if (self instanceof ShortBuffer) {
            method = SHORTBUFFER_GET;
            guard = IS_SHORTBUFFER;
        } else if (self instanceof IntBuffer) {
            method = INTBUFFER_GET;
            guard = IS_INTBUFFER;
        } else if (self instanceof LongBuffer) {
            method = LONGBUFFER_GET;
            guard = IS_LONGBUFFER;
        } else if (self instanceof FloatBuffer) {
            method = FLOATBUFFER_GET;
            guard = IS_FLOATBUFFER;
        } else if (self instanceof DoubleBuffer) {
            method = DOUBLEBUFFER_GET;
            guard = IS_DOUBLEBUFFER;
        }

        return method != null? new GuardedInvocation(method, guard) : null;
    }

    private static GuardedInvocation linkSetElement(Object self) {
        MethodHandle method = null;
        MethodHandle guard = null;
        if (self instanceof ByteBuffer) {
            method = BYTEBUFFER_PUT;
            guard = IS_BYTEBUFFER;
        } else if (self instanceof CharBuffer) {
            method = CHARBUFFER_PUT;
            guard = IS_CHARBUFFER;
        } else if (self instanceof ShortBuffer) {
            method = SHORTBUFFER_PUT;
            guard = IS_SHORTBUFFER;
        } else if (self instanceof IntBuffer) {
            method = INTBUFFER_PUT;
            guard = IS_INTBUFFER;
        } else if (self instanceof LongBuffer) {
            method = LONGBUFFER_PUT;
            guard = IS_LONGBUFFER;
        } else if (self instanceof FloatBuffer) {
            method = FLOATBUFFER_PUT;
            guard = IS_FLOATBUFFER;
        } else if (self instanceof DoubleBuffer) {
            method = DOUBLEBUFFER_PUT;
            guard = IS_DOUBLEBUFFER;
        }

        return method != null? new GuardedInvocation(method, guard) : null;
    }

    private static GuardedInvocation linkLength() {
        return new GuardedInvocation(BUFFER_LIMIT, IS_BUFFER);
    }
}
