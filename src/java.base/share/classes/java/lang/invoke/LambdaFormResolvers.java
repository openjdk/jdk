/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import java.lang.invoke.LambdaForm.Name;
import java.lang.invoke.LambdaForm.NamedFunction;
import java.util.Arrays;

import static java.lang.invoke.LambdaForm.arguments;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_invokeVirtual;
import static java.lang.invoke.MethodHandleStatics.UNSAFE;
import static java.lang.invoke.MethodTypeForm.LF_RESOLVER;

/**
 * Manage resolving LambdaForms, either by lazily spinning up bytecode for them
 * or looking up pregenerated methods that implement them.
 */
class LambdaFormResolvers {

    public static MemberName resolverFor(LambdaForm form, MethodType basicType) {
        LambdaForm lform = basicType.form().cachedLambdaForm(LF_RESOLVER);
        if (lform != null) {
            return lform.vmentry;
        }
        MemberName memberName = LambdaForm.resolveFrom(LambdaForm.Kind.RESOLVER.methodName, basicType, LambdaFormResolvers.Holder.class);
        if (memberName != null) {
            lform = LambdaForm.createWrapperForResolver(memberName);
        } else {
            lform = makeResolverForm(basicType);
            assert lform.methodType() == form.methodType()
                    : "type mismatch: " + lform.methodType() + " != " + form.methodType();
        }
        lform = basicType.form().setCachedLambdaForm(LF_RESOLVER, lform);
        return lform.vmentry;
    }

    static LambdaForm makeResolverForm(MethodType basicType) {
        final int THIS_MH   = 0;  // the target MH
        final int ARG_BASE  = 1;  // start of incoming arguments
        final int ARG_LIMIT = ARG_BASE + basicType.parameterCount() - 1; // -1 to skip receiver MH

        int nameCursor = ARG_LIMIT;
        final int RESOLVE = nameCursor++;
        final int INVOKE  = nameCursor++;

        Name[] names = arguments(nameCursor - ARG_LIMIT, basicType);

        names[RESOLVE] = new Name(ResolveHolder.NF_resolve, names[THIS_MH]);

        // do not use a basic invoker handle here to avoid max arity problems
        Object[] args = Arrays.copyOf(names, basicType.parameterCount()); // forward all args
        MethodType invokerType = basicType.dropParameterTypes(0, 1); // drop leading MH
        // Avoid resolving member name so that native wrapper doesn't get generated eagerly
        MemberName invokeBasic = new MemberName(MethodHandle.class, "invokeBasic", invokerType, REF_invokeVirtual);
        assert InvokerBytecodeGenerator.isStaticallyInvocable(invokeBasic);
        names[INVOKE] = new Name(new NamedFunction(invokeBasic), args);

        LambdaForm lform = LambdaForm.create(basicType.parameterCount(), names, INVOKE, LambdaForm.Kind.RESOLVER);
        lform.forceCompileToBytecode(); // no cycles, compile this now
        return lform;
    }

    // pre-generated resolvers

    static {
        // The Holder class will contain pre-generated Resolvers resolved
        // speculatively using resolveOrNull. However, that doesn't initialize the class,
        // which subtly breaks inlining etc. By forcing initialization of the Holder
        // class we avoid these issues.
        UNSAFE.ensureClassInitialized(LambdaFormResolvers.Holder.class);
    }

    /* Placeholder class for Resolvers generated ahead of time */
    final class Holder {}

    private static final class ResolveHolder {
        static final NamedFunction NF_resolve;

        static {
            try {
                NF_resolve = new NamedFunction(ResolveHolder.class.getDeclaredMethod("resolve", MethodHandle.class));
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        public static void resolve(MethodHandle mh) {
            mh.form.resolve(mh.form.methodType());
        }
    }

}
