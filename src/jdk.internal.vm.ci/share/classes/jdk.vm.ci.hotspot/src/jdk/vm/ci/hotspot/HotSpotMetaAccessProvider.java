/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;
import static jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl.fromObjectClass;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

// JaCoCo Exclude

/**
 * HotSpot implementation of {@link MetaAccessProvider}.
 */
public class HotSpotMetaAccessProvider implements MetaAccessProvider {

    protected final HotSpotJVMCIRuntimeProvider runtime;

    public HotSpotMetaAccessProvider(HotSpotJVMCIRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class parameter was null");
        }
        return runtime.fromClass(clazz);
    }

    public HotSpotResolvedObjectType lookupJavaType(JavaConstant constant) {
        if (constant.isNull() || !(constant instanceof HotSpotObjectConstant)) {
            return null;
        }
        return ((HotSpotObjectConstant) constant).getType();
    }

    public Signature parseMethodDescriptor(String signature) {
        return new HotSpotSignature(runtime, signature);
    }

    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        return runtime.getCompilerToVM().asResolvedJavaMethod(Objects.requireNonNull(reflectionMethod));
    }

    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        Class<?> fieldHolder = reflectionField.getDeclaringClass();

        HotSpotResolvedObjectType holder = fromObjectClass(fieldHolder);
        if (Modifier.isStatic(reflectionField.getModifiers())) {
            final long offset = UNSAFE.staticFieldOffset(reflectionField);
            for (ResolvedJavaField field : holder.getStaticFields()) {
                if (offset == ((HotSpotResolvedJavaField) field).offset()) {
                    return field;
                }
            }
        } else {
            final long offset = UNSAFE.objectFieldOffset(reflectionField);
            for (ResolvedJavaField field : holder.getInstanceFields(false)) {
                if (offset == ((HotSpotResolvedJavaField) field).offset()) {
                    return field;
                }
            }
        }

        throw new JVMCIError("unresolved field %s", reflectionField);
    }

    private static int intMaskRight(int n) {
        assert n <= 32;
        return n == 32 ? -1 : (1 << n) - 1;
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        HotSpotVMConfig config = runtime.getConfig();
        int actionValue = convertDeoptAction(action);
        int reasonValue = convertDeoptReason(reason);
        int debugValue = debugId & intMaskRight(config.deoptimizationDebugIdBits);
        JavaConstant c = JavaConstant.forInt(
                        ~((debugValue << config.deoptimizationDebugIdShift) | (reasonValue << config.deoptimizationReasonShift) | (actionValue << config.deoptimizationActionShift)));
        assert c.asInt() < 0;
        return c;
    }

    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        HotSpotVMConfig config = runtime.getConfig();
        int reasonValue = ((~constant.asInt()) >> config.deoptimizationReasonShift) & intMaskRight(config.deoptimizationReasonBits);
        DeoptimizationReason reason = convertDeoptReason(reasonValue);
        return reason;
    }

    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        HotSpotVMConfig config = runtime.getConfig();
        int actionValue = ((~constant.asInt()) >> config.deoptimizationActionShift) & intMaskRight(config.deoptimizationActionBits);
        DeoptimizationAction action = convertDeoptAction(actionValue);
        return action;
    }

    public int decodeDebugId(JavaConstant constant) {
        HotSpotVMConfig config = runtime.getConfig();
        return ((~constant.asInt()) >> config.deoptimizationDebugIdShift) & intMaskRight(config.deoptimizationDebugIdBits);
    }

    public int convertDeoptAction(DeoptimizationAction action) {
        HotSpotVMConfig config = runtime.getConfig();
        switch (action) {
            case None:
                return config.deoptActionNone;
            case RecompileIfTooManyDeopts:
                return config.deoptActionMaybeRecompile;
            case InvalidateReprofile:
                return config.deoptActionReinterpret;
            case InvalidateRecompile:
                return config.deoptActionMakeNotEntrant;
            case InvalidateStopCompiling:
                return config.deoptActionMakeNotCompilable;
            default:
                throw new JVMCIError("%s", action);
        }
    }

    public DeoptimizationAction convertDeoptAction(int action) {
        HotSpotVMConfig config = runtime.getConfig();
        if (action == config.deoptActionNone) {
            return DeoptimizationAction.None;
        }
        if (action == config.deoptActionMaybeRecompile) {
            return DeoptimizationAction.RecompileIfTooManyDeopts;
        }
        if (action == config.deoptActionReinterpret) {
            return DeoptimizationAction.InvalidateReprofile;
        }
        if (action == config.deoptActionMakeNotEntrant) {
            return DeoptimizationAction.InvalidateRecompile;
        }
        if (action == config.deoptActionMakeNotCompilable) {
            return DeoptimizationAction.InvalidateStopCompiling;
        }
        throw new JVMCIError("%d", action);
    }

    public int convertDeoptReason(DeoptimizationReason reason) {
        HotSpotVMConfig config = runtime.getConfig();
        switch (reason) {
            case None:
                return config.deoptReasonNone;
            case NullCheckException:
                return config.deoptReasonNullCheck;
            case BoundsCheckException:
                return config.deoptReasonRangeCheck;
            case ClassCastException:
                return config.deoptReasonClassCheck;
            case ArrayStoreException:
                return config.deoptReasonArrayCheck;
            case UnreachedCode:
                return config.deoptReasonUnreached0;
            case TypeCheckedInliningViolated:
                return config.deoptReasonTypeCheckInlining;
            case OptimizedTypeCheckViolated:
                return config.deoptReasonOptimizedTypeCheck;
            case NotCompiledExceptionHandler:
                return config.deoptReasonNotCompiledExceptionHandler;
            case Unresolved:
                return config.deoptReasonUnresolved;
            case JavaSubroutineMismatch:
                return config.deoptReasonJsrMismatch;
            case ArithmeticException:
                return config.deoptReasonDiv0Check;
            case RuntimeConstraint:
                return config.deoptReasonConstraint;
            case LoopLimitCheck:
                return config.deoptReasonLoopLimitCheck;
            case Aliasing:
                return config.deoptReasonAliasing;
            case TransferToInterpreter:
                return config.deoptReasonTransferToInterpreter;
            default:
                throw new JVMCIError("%s", reason);
        }
    }

    public DeoptimizationReason convertDeoptReason(int reason) {
        HotSpotVMConfig config = runtime.getConfig();
        if (reason == config.deoptReasonNone) {
            return DeoptimizationReason.None;
        }
        if (reason == config.deoptReasonNullCheck) {
            return DeoptimizationReason.NullCheckException;
        }
        if (reason == config.deoptReasonRangeCheck) {
            return DeoptimizationReason.BoundsCheckException;
        }
        if (reason == config.deoptReasonClassCheck) {
            return DeoptimizationReason.ClassCastException;
        }
        if (reason == config.deoptReasonArrayCheck) {
            return DeoptimizationReason.ArrayStoreException;
        }
        if (reason == config.deoptReasonUnreached0) {
            return DeoptimizationReason.UnreachedCode;
        }
        if (reason == config.deoptReasonTypeCheckInlining) {
            return DeoptimizationReason.TypeCheckedInliningViolated;
        }
        if (reason == config.deoptReasonOptimizedTypeCheck) {
            return DeoptimizationReason.OptimizedTypeCheckViolated;
        }
        if (reason == config.deoptReasonNotCompiledExceptionHandler) {
            return DeoptimizationReason.NotCompiledExceptionHandler;
        }
        if (reason == config.deoptReasonUnresolved) {
            return DeoptimizationReason.Unresolved;
        }
        if (reason == config.deoptReasonJsrMismatch) {
            return DeoptimizationReason.JavaSubroutineMismatch;
        }
        if (reason == config.deoptReasonDiv0Check) {
            return DeoptimizationReason.ArithmeticException;
        }
        if (reason == config.deoptReasonConstraint) {
            return DeoptimizationReason.RuntimeConstraint;
        }
        if (reason == config.deoptReasonLoopLimitCheck) {
            return DeoptimizationReason.LoopLimitCheck;
        }
        if (reason == config.deoptReasonAliasing) {
            return DeoptimizationReason.Aliasing;
        }
        if (reason == config.deoptReasonTransferToInterpreter) {
            return DeoptimizationReason.TransferToInterpreter;
        }
        throw new JVMCIError("%x", reason);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        if (constant.getJavaKind() == JavaKind.Object) {
            HotSpotResolvedObjectType lookupJavaType = lookupJavaType(constant);

            if (lookupJavaType == null) {
                return 0;
            } else {
                if (lookupJavaType.isArray()) {
                    int length = Array.getLength(((HotSpotObjectConstantImpl) constant).object());
                    ResolvedJavaType elementType = lookupJavaType.getComponentType();
                    JavaKind elementKind = elementType.getJavaKind();
                    final int headerSize = getArrayBaseOffset(elementKind);
                    int sizeOfElement = getArrayIndexScale(elementKind);
                    int log2ElementSize = CodeUtil.log2(sizeOfElement);
                    return computeArrayAllocationSize(length, headerSize, log2ElementSize);
                }
                return lookupJavaType.instanceSize();
            }
        } else {
            return constant.getJavaKind().getByteCount();
        }
    }

    /**
     * Computes the size of the memory chunk allocated for an array. This size accounts for the
     * array header size, body size and any padding after the last element to satisfy object
     * alignment requirements.
     *
     * @param length the number of elements in the array
     * @param headerSize the size of the array header
     * @param log2ElementSize log2 of the size of an element in the array
     * @return the size of the memory chunk
     */
    public int computeArrayAllocationSize(int length, int headerSize, int log2ElementSize) {
        HotSpotVMConfig config = runtime.getConfig();
        int alignment = config.objectAlignment;
        int size = (length << log2ElementSize) + headerSize + (alignment - 1);
        int mask = ~(alignment - 1);
        return size & mask;
    }
}
