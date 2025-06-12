/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Implementation of {@link InstalledCode} for code installed as an {@code nmethod}. The address of
 * the {@code nmethod} is stored in {@link InstalledCode#address} and the value of
 * {@code nmethod::verified_entry_point()} is in {@link InstalledCode#entryPoint}.
 */
public class HotSpotNmethod extends HotSpotInstalledCode {

    /**
     * This (indirect) {@code Method*} reference is safe since class redefinition preserves all
     * methods associated with nmethods in the code cache.
     */
    private final HotSpotResolvedJavaMethodImpl method;

    /**
     * Specifies whether the {@code nmethod} associated with this object is the code executed by
     * default HotSpot linkage when a normal Java call to {@link #method} is made. That is, does
     * {@code this.method.metadataHandle->_code} == {@code this.address}. If not, then the
     * {@code nmethod} can only be invoked via a reference to this object. An example of this is the
     * trampoline mechanism used by Truffle: https://goo.gl/LX88rZ.
     */
    private final boolean isDefault;

    /**
     * Determines whether this object is in the oops table of the nmethod.
     * <p>
     * If this object is in the oops table, the VM uses the oops table entry to update this object's
     * {@link #address} and {@link #entryPoint} fields when the state of the nmethod changes. The
     * nmethod will be unloadable when this object dies.
     * <p>
     * Otherwise, the nmethod's unloadability is not changed when this object dies.
     */
    boolean inOopsTable() {
        return compileIdSnapshot != 0;
    }

    /**
     * If this field is 0, this object is in the oops table of the nmethod. Otherwise, the value of
     * the field records the nmethod's compile identifier. This value is used to confirm if an entry
     * in the code cache retrieved by {@link #address} is indeed the nmethod represented by this
     * object.
     *
     * @see #inOopsTable
     */
    private final long compileIdSnapshot;

    /**
     * Identify the reason that caused this nmethod to be invalidated.
     * A value of -1 means that the nmethod was not invalidated.
     */
    private int invalidationReason;

    HotSpotNmethod(HotSpotResolvedJavaMethodImpl method, String name, boolean isDefault, long compileId) {
        super(name);
        this.method = method;
        this.isDefault = isDefault;
        boolean inOopsTable = !IS_IN_NATIVE_IMAGE && !isDefault;
        this.compileIdSnapshot = inOopsTable ? 0L : compileId;
        this.invalidationReason = -1;
        assert inOopsTable || compileId != 0L : this;
    }

    /**
     * Attaches {@code log} to this object. If {@code log.managesFailedSpeculations() == true}, this
     * ensures the failed speculation list lives at least as long as this object.
     */
    public void setSpeculationLog(HotSpotSpeculationLog log) {
        this.speculationLog = log;
    }

    /**
     * The speculation log containing speculations embedded in the nmethod.
     *
     * If {@code speculationLog.managesFailedSpeculations() == true}, this field ensures the failed
     * speculation list lives at least as long as this object. This prevents deoptimization from
     * appending to an already freed list.
     */
    @SuppressWarnings("unused") private HotSpotSpeculationLog speculationLog;

    /**
     * Determines if the nmethod associated with this object is the compiled entry point for
     * {@link #getMethod()}.
     */
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean isValid() {
        if (compileIdSnapshot != 0L) {
            compilerToVM().updateHotSpotNmethod(this);
        }
        return super.isValid();
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    /**
     * Invalidate this nmethod using the reason specified in {@code invalidationReason} and
     * optionally deoptimize the method if {@code deoptimize} is set.
     * @param deoptimize whether or not to deoptimize the method.
     * @param invalidationReason invalidation reason code.
     */
    public void invalidate(boolean deoptimize, int invalidationReason) {
        compilerToVM().invalidateHotSpotNmethod(this, deoptimize, invalidationReason);
    }

    @Override
    public void invalidate(boolean deoptimize) {
        invalidate(deoptimize, jvmciInvalidationReason());
    }

    @Override
    public long getAddress() {
        if (compileIdSnapshot != 0L) {
            compilerToVM().updateHotSpotNmethod(this);
        }
        return super.getAddress();
    }

    @Override
    public long getEntryPoint() {
        if (compileIdSnapshot != 0L) {
            return 0;
        }
        return super.getEntryPoint();
    }

    @Override
    public String toString() {
        return String.format("HotSpotNmethod[method=%s, codeBlob=0x%x, isDefault=%b, name=%s, inOopsTable=%s]",
                        method, getAddress(), isDefault, name, inOopsTable());
    }

    private boolean checkArgs(Object... args) {
        JavaType[] sig = method.toParameterTypes();
        assert args.length == sig.length : method.format("%H.%n(%p): expected ") + sig.length + " args, got " + args.length;
        for (int i = 0; i < sig.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                assert sig[i].getJavaKind() == JavaKind.Object : method.format("%H.%n(%p): expected arg ") + i + " to be Object, not " + sig[i];
            } else if (sig[i].getJavaKind() != JavaKind.Object) {
                assert sig[i].getJavaKind().toBoxedJavaClass() == arg.getClass() : method.format("%H.%n(%p): expected arg ") + i + " to be " + sig[i] + ", not " + arg.getClass();
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * It's possible for the HotSpot runtime to sweep nmethods at any point in time. As a result,
     * there is no guarantee that calling this method will execute the wrapped nmethod. Instead, it
     * may end up executing the bytecode of the associated {@link #getMethod() Java method}. Only if
     * {@link #isValid()} is {@code true} after returning can the caller be sure that the nmethod
     * was executed. If {@link #isValid()} is {@code false}, then the only way to determine if the
     * nmethod was executed is to test for some side-effect specific to the nmethod (e.g., update to
     * a field) that is not performed by the bytecode of the associated {@link #getMethod() Java
     * method}.
     */
    @Override
    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        if (IS_IN_NATIVE_IMAGE) {
            throw new HotSpotJVMCIUnsupportedOperationError("Cannot execute nmethod via mirror in native image");
        }
        assert checkArgs(args);
        return compilerToVM().executeHotSpotNmethod(args, this);
    }

    @Override
    public long getStart() {
        return isValid() ? super.getStart() : 0;
    }

    /**
     * @return an integer representing the reason why this nmethod was invalidated.
     */
    public int getInvalidationReason() {
        return invalidationReason;
    }

    /**
     * @return a String describing the reason why this nmethod was invalidated.
     */
    public String getInvalidationReasonDescription() {
        return compilerToVM().getInvalidationReasonDescription(this.getInvalidationReason());
    }

    private static int jvmciInvalidationReason() {
        return HotSpotJVMCIRuntime.runtime().config.getConstant("nmethod::InvalidationReason::JVMCI_INVALIDATE", Integer.class);
    }
}
