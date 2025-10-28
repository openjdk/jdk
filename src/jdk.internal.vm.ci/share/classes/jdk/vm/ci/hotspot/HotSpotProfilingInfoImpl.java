/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

final class HotSpotProfilingInfoImpl implements HotSpotProfilingInfo {

    private final HotSpotMethodData methodData;
    private final HotSpotResolvedJavaMethod method;

    private boolean isMature;
    private int position;
    private int hintPosition;
    private int hintBCI;
    private HotSpotMethodDataAccessor dataAccessor;

    private boolean includeNormal;
    private boolean includeOSR;

    HotSpotProfilingInfoImpl(HotSpotMethodData methodData, HotSpotResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
        this.methodData = methodData;
        this.method = method;
        if (!method.getDeclaringClass().isLinked()) {
            throw new IllegalArgumentException(method.format("%H.%n(%p) must be linked"));
        }
        this.includeNormal = includeNormal;
        this.includeOSR = includeOSR;
        this.isMature = methodData.isProfileMature();
        hintPosition = 0;
        hintBCI = -1;
    }

    @Override
    public int getCodeSize() {
        return method.getCodeSize();
    }

    @Override
    public int getDecompileCount() {
        return methodData.getDecompileCount();
    }

    @Override
    public int getOverflowRecompileCount() {
        return methodData.getOverflowRecompileCount();
    }

    @Override
    public int getOverflowTrapCount() {
        return methodData.getOverflowTrapCount();
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        if (!isMature) {
            return null;
        }
        findBCI(bci);
        return dataAccessor.getTypeProfile(methodData, position);
    }

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        if (!isMature) {
            return null;
        }
        findBCI(bci);
        return dataAccessor.getMethodProfile(methodData, position);
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        if (!isMature) {
            return -1;
        }
        findBCI(bci);
        return dataAccessor.getBranchTakenProbability(methodData, position);
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        if (!isMature) {
            return null;
        }
        findBCI(bci);
        return dataAccessor.getSwitchProbabilities(methodData, position);
    }

    @Override
    public TriState getExceptionSeen(int bci) {
        if (!findBCI(bci)) {
            // There might data in the extra data section but all accesses to that memory must be
            // under a lock so go into VM to get the data.
            int exceptionSeen = compilerToVM().methodDataExceptionSeen(methodData.methodDataPointer, bci);
            if (exceptionSeen == -1) {
                return TriState.UNKNOWN;
            }
            return TriState.get(exceptionSeen != 0);
        }
        return dataAccessor.getExceptionSeen(methodData, position);
    }

    @Override
    public TriState getNullSeen(int bci) {
        findBCI(bci);
        return dataAccessor.getNullSeen(methodData, position);
    }

    @Override
    public int getExecutionCount(int bci) {
        if (!isMature) {
            return -1;
        }
        findBCI(bci);
        return dataAccessor.getExecutionCount(methodData, position);
    }

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        int count = 0;
        if (includeNormal) {
            count += methodData.getDeoptimizationCount(reason);
        }
        if (includeOSR) {
            count += methodData.getOSRDeoptimizationCount(reason);
        }
        return count;
    }

    private boolean findBCI(int targetBCI) {
        assert targetBCI >= 0 : "invalid BCI";

        if (methodData.hasNormalData()) {
            int currentPosition = targetBCI < hintBCI ? 0 : hintPosition;
            HotSpotMethodDataAccessor currentAccessor;
            while ((currentAccessor = methodData.getNormalData(currentPosition)) != null) {
                int currentBCI = currentAccessor.getBCI(methodData, currentPosition);
                if (currentBCI == targetBCI) {
                    normalDataFound(currentAccessor, currentPosition, currentBCI);
                    return true;
                } else if (currentBCI > targetBCI) {
                    break;
                }
                currentPosition = currentPosition + currentAccessor.getSize(methodData, currentPosition);
            }
        }
        noDataFound(false);
        return false;
    }

    private void normalDataFound(HotSpotMethodDataAccessor data, int pos, int bci) {
        setCurrentData(data, pos);
        this.hintPosition = position;
        this.hintBCI = bci;
    }

    private void noDataFound(boolean exceptionPossiblyNotRecorded) {
        HotSpotMethodDataAccessor accessor = HotSpotMethodData.getNoDataAccessor(exceptionPossiblyNotRecorded);
        setCurrentData(accessor, -1);
    }

    private void setCurrentData(HotSpotMethodDataAccessor dataAccessor, int position) {
        this.dataAccessor = dataAccessor;
        this.position = position;
    }

    @Override
    public boolean isMature() {
        return isMature;
    }

    public void ignoreMature() {
        isMature = true;
    }

    @Override
    public String toString() {
        return "HotSpotProfilingInfo<" + this.toString(null, "; ") + ">";
    }

    @Override
    public void setMature() {
        isMature = true;
    }

    /**
     * {@code MethodData::_jvmci_ir_size} (currently) supports at most one JVMCI compiler IR type
     * which will be determined by the first JVMCI compiler that calls
     * {@link #setCompilerIRSize(Class, int)}.
     */
    private static volatile Class<?> supportedCompilerIRType;

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int size) {
        if (supportedCompilerIRType == null) {
            synchronized (HotSpotProfilingInfoImpl.class) {
                if (supportedCompilerIRType == null) {
                    supportedCompilerIRType = irType;
                }
            }
        }
        if (supportedCompilerIRType != irType) {
            return false;
        }
        methodData.setCompiledIRSize(size);
        return true;
    }

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        if (irType == supportedCompilerIRType) {
            return methodData.getCompiledIRSize();
        }
        return -1;
    }
}
