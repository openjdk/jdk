/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import jdk.tools.jaotc.AOTDynamicTypeStore.AdapterLocation;
import jdk.tools.jaotc.AOTDynamicTypeStore.AppendixLocation;
import jdk.tools.jaotc.AOTDynamicTypeStore.Location;

/**
 * Class encapsulating Graal-compiled output of a Java class. The compilation result of all methods
 * of a class {@code className} are maintained in an array list.
 */
final class AOTCompiledClass {

    private static AOTDynamicTypeStore dynoStore;

    static void setDynamicTypeStore(AOTDynamicTypeStore s) {
        dynoStore = s;
    }

    static class AOTKlassData {
        private int gotIndex; // Index (offset/8) to the got in the .metaspace.got section
        private int classId;  // Unique ID
        // Offset to compiled methods data in the .methods.offsets section.
        private int compiledMethodsOffset;
        // Offset to dependent methods data.
        private int dependentMethodsOffset;

        private final String metadataName;
        HotSpotResolvedObjectType type;

        /**
         * List of dependent compiled methods which have a reference to this class.
         */
        private ArrayList<CompiledMethodInfo> dependentMethods;

        AOTKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type, int classId) {
            this.dependentMethods = new ArrayList<>();
            this.classId = classId;
            this.type = type;
            this.metadataName = type.isAnonymous() ? "anon<"+ classId + ">": type.getName();
            this.gotIndex = binaryContainer.addTwoSlotKlassSymbol(metadataName);
            this.compiledMethodsOffset = -1; // Not compiled classes do not have compiled methods.
            this.dependentMethodsOffset = -1;
        }

        private String[] getMetaspaceNames() {
            String name = metadataName;
            Set<Location> locs = dynoStore.getDynamicClassLocationsForType(type);
            if (locs == null) {
                return new String[] {name};
            } else {
                ArrayList<String> names = new ArrayList<String>();
                names.add(name);
                for (Location l : locs) {
                    HotSpotResolvedObjectType cpType = l.getHolder();
                    AOTKlassData data = getAOTKlassData(cpType);
                    // We collect dynamic types at parse time, but late inlining
                    // may record types that don't make it into the final graph.
                    // We can safely ignore those here.
                    if (data == null) {
                       // Not a compiled or inlined method
                       continue;
                    }
                    int cpi = l.getCpi();
                    String location = "<"+ data.classId + ":" + cpi + ">";
                    if (l instanceof AdapterLocation) {
                        names.add("adapter" + location);
                        AdapterLocation a = (AdapterLocation)l;
                        names.add("adapter:" + a.getMethodId() + location);
                    } else {
                        assert l instanceof AppendixLocation;
                        names.add("appendix" + location);
                    }
                }
                return names.toArray(new String[names.size()]);
            }
        }

        HotSpotResolvedObjectType getType() {
            return type;
        }

        String getMetadataName() {
            return metadataName;
        }

        /**
         * Add a method to the list of dependent methods.
         */
        synchronized boolean addDependentMethod(CompiledMethodInfo cm) {
            return dependentMethods.add(cm);
        }

        /**
         * Return the array list of dependent class methods.
         *
         * @return array list of dependent methods
         */
        ArrayList<CompiledMethodInfo> getDependentMethods() {
            return dependentMethods;
        }

        /**
         * Returns if this class has dependent methods.
         *
         * @return true if dependent methods exist, false otherwise
         */
        boolean hasDependentMethods() {
            return !dependentMethods.isEmpty();
        }

        void setCompiledMethodsOffset(int offset) {
            compiledMethodsOffset = offset;
        }

        protected void putAOTKlassData(BinaryContainer binaryContainer, ReadOnlyDataContainer container) {
            int cntDepMethods = dependentMethods.size();
            // Create array of dependent methods IDs. First word is count.
            ReadOnlyDataContainer dependenciesContainer = binaryContainer.getKlassesDependenciesContainer();
            this.dependentMethodsOffset = BinaryContainer.addMethodsCount(cntDepMethods, dependenciesContainer);
            for (CompiledMethodInfo methodInfo : dependentMethods) {
                dependenciesContainer.appendInt(methodInfo.getCodeId());
            }

            verify();

            // @formatter:off
            /*
             * The offsets layout should match AOTKlassData structure in AOT JVM runtime
             */
            int offset = container.getByteStreamSize();
            for (String name : getMetaspaceNames()) {
                container.createSymbol(offset, Kind.OBJECT, Binding.GLOBAL, 0, name);
            }
                      // Add index (offset/8) to the got in the .metaspace.got section
            container.appendInt(gotIndex).
                      // Add unique ID
                      appendInt(classId).
                      // Add the offset to compiled methods data in the .metaspace.offsets section.
                      appendInt(compiledMethodsOffset).
                      // Add the offset to dependent methods data in the .metaspace.offsets section.
                      appendInt(dependentMethodsOffset).
                      // Add fingerprint.
                      appendLong(type.getFingerprint());

            // @formatter:on
        }

        private void verify() {
            String name = type.getName();
            assert gotIndex > 0 : "incorrect gotIndex: " + gotIndex + " for klass: " + name;
            long fingerprint = type.getFingerprint();
            assert type.isArray() || fingerprint != 0 : "incorrect fingerprint: " + fingerprint + " for klass: " + name;
            assert compiledMethodsOffset >= -1 : "incorrect compiledMethodsOffset: " + compiledMethodsOffset + " for klass: " + name;
            assert dependentMethodsOffset >= -1 : "incorrect dependentMethodsOffset: " + dependentMethodsOffset + " for klass: " + name;
            assert classId >= 0 : "incorrect classId: " + classId + " for klass: " + name;
        }

    }

    private final HotSpotResolvedObjectType resolvedJavaType;

    /**
     * List of all collected class data.
     */
    private static HashMap<String, AOTKlassData> klassData = new HashMap<>();

    /**
     * List of all methods to be compiled.
     */
    private ArrayList<ResolvedJavaMethod> methods = new ArrayList<>();

    /**
     * List of all compiled class methods.
     */
    private ArrayList<CompiledMethodInfo> compiledMethods;

    /**
     * If this class represents Graal stub code.
     */
    private final boolean representsStubs;

    /**
     * Classes count used to generate unique global method id.
     */
    private static int classesCount = 0;

    /**
     * Construct an object with compiled methods. Intended to be used for code with no corresponding
     * Java method name in the user application.
     *
     * @param compiledMethods AOT compiled methods
     */
    AOTCompiledClass(ArrayList<CompiledMethodInfo> compiledMethods) {
        this.resolvedJavaType = null;
        this.compiledMethods = compiledMethods;
        this.representsStubs = true;
    }

    /**
     * Construct an object with compiled versions of the named class.
     */
    AOTCompiledClass(ResolvedJavaType resolvedJavaType) {
        this.resolvedJavaType = (HotSpotResolvedObjectType) resolvedJavaType;
        this.compiledMethods = new ArrayList<>();
        this.representsStubs = false;
    }

    /**
     * @return the ResolvedJavaType of this class
     */
    ResolvedJavaType getResolvedJavaType() {
        return resolvedJavaType;
    }

    /**
     * Get the list of methods which should be compiled.
     */
    ArrayList<ResolvedJavaMethod> getMethods() {
        ArrayList<ResolvedJavaMethod> m = methods;
        methods = null; // Free - it is not used after that.
        return m;
    }

    /**
     * Get the number of all AOT classes.
     */
    static int getClassesCount() {
        return classesCount;
    }

    /**
     * Get the number of methods which should be compiled.
     *
     * @return number of methods which should be compiled
     */
    int getMethodCount() {
        return methods.size();
    }

    /**
     * Add a method to the list of methods to be compiled.
     */
    void addMethod(ResolvedJavaMethod method) {
        methods.add(method);
    }

    /**
     * Returns if this class has methods which should be compiled.
     *
     * @return true if this class contains methods which should be compiled, false otherwise
     */
    boolean hasMethods() {
        return !methods.isEmpty();
    }

    /**
     * Add a method to the list of compiled methods. This method needs to be thread-safe.
     */
    synchronized boolean addCompiledMethod(CompiledMethodInfo cm) {
        return compiledMethods.add(cm);
    }

    /**
     * Return the array list of compiled class methods.
     *
     * @return array list of compiled methods
     */
    ArrayList<CompiledMethodInfo> getCompiledMethods() {
        return compiledMethods;
    }

    /**
     * Returns if this class has successfully compiled methods.
     *
     * @return true if methods were compiled, false otherwise
     */
    boolean hasCompiledMethods() {
        return !compiledMethods.isEmpty();
    }

    /**
     * Add a klass data.
     */
    synchronized static AOTKlassData addAOTKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        String name = type.getName();
        AOTKlassData data = klassData.get(name);
        if (data != null) {
            assert data.getType() == type : "duplicate classes for name " + name;
        } else {
            data = new AOTKlassData(binaryContainer, type, classesCount++);
            klassData.put(name, data);
        }
        return data;
    }

    private synchronized static AOTKlassData getAOTKlassData(String name) {
        return klassData.get(name);
    }

    synchronized static AOTKlassData getAOTKlassData(HotSpotResolvedObjectType type) {
        String name = type.getName();
        AOTKlassData data =  getAOTKlassData(name);
        assert data == null || data.getType() == type : "duplicate classes for name " + name;
        return data;
    }

    void addAOTKlassData(BinaryContainer binaryContainer) {
        for (CompiledMethodInfo methodInfo : compiledMethods) {
            // Record methods holder
            methodInfo.addDependentKlassData(binaryContainer, resolvedJavaType);
            // Record inlinee classes
            ResolvedJavaMethod[] inlinees = methodInfo.getCompilationResult().getMethods();
            if (inlinees != null) {
                for (ResolvedJavaMethod m : inlinees) {
                    methodInfo.addDependentKlassData(binaryContainer, (HotSpotResolvedObjectType) m.getDeclaringClass());
                }
            }
            // Record classes of fields that were accessed
            ResolvedJavaField[] fields = methodInfo.getCompilationResult().getFields();
            if (fields != null) {
                for (ResolvedJavaField f : fields) {
                    methodInfo.addDependentKlassData(binaryContainer, (HotSpotResolvedObjectType) f.getDeclaringClass());
                }
            }
        }
    }

    synchronized static AOTKlassData addFingerprintKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        if (type.isArray()) {
            return addAOTKlassData(binaryContainer, type);
        }
        assert type.getFingerprint() != 0 : "no fingerprint for " + type.getName();
        AOTKlassData old = getAOTKlassData(type);
        if (old != null) {
            boolean assertsEnabled = false;
            // Next assignment will be executed when asserts are enabled.
            assert assertsEnabled = true;
            if (assertsEnabled) {
                HotSpotResolvedObjectType s = type.getSuperclass();
                if (s != null) {
                    assert getAOTKlassData(s) != null : "fingerprint for super " + s.getName() + " needed for " + type.getName();
                }
                for (HotSpotResolvedObjectType i : type.getInterfaces()) {
                    assert getAOTKlassData(i) != null : "fingerprint for interface " + i.getName() + " needed for " + type.getName();
                }
            }
            return old;
        }

        // Fingerprinting requires super classes and super interfaces
        HotSpotResolvedObjectType s = type.getSuperclass();
        if (s != null) {
            addFingerprintKlassData(binaryContainer, s);
        }
        for (HotSpotResolvedObjectType i : type.getInterfaces()) {
            addFingerprintKlassData(binaryContainer, i);
        }

        return addAOTKlassData(binaryContainer, type);
    }

    /*
     * Put methods data to contained.
     */
    void putMethodsData(BinaryContainer binaryContainer) {
        ReadOnlyDataContainer container = binaryContainer.getMethodsOffsetsContainer();
        int cntMethods = compiledMethods.size();
        int startMethods = BinaryContainer.addMethodsCount(cntMethods, container);
        for (CompiledMethodInfo methodInfo : compiledMethods) {
            methodInfo.addMethodOffsets(binaryContainer, container);
        }
        String name = resolvedJavaType.getName();
        AOTKlassData data = getAOTKlassData(resolvedJavaType);
        assert data != null : "missing data for klass: " + name;
        int cntDepMethods = data.dependentMethods.size();
        assert cntDepMethods > 0 : "no dependent methods for compiled klass: " + name;
        data.setCompiledMethodsOffset(startMethods);
    }

    static void putAOTKlassData(BinaryContainer binaryContainer) {
        // record dynamic types
        Set<HotSpotResolvedObjectType> dynoTypes = dynoStore.getDynamicTypes();
        if (dynoTypes != null) {
            for (HotSpotResolvedObjectType dynoType : dynoTypes) {
                addFingerprintKlassData(binaryContainer, dynoType);
            }
        }

        ReadOnlyDataContainer container = binaryContainer.getKlassesOffsetsContainer();
        for (AOTKlassData data : klassData.values()) {
            data.putAOTKlassData(binaryContainer, container);
        }
    }

    static HotSpotResolvedObjectType getType(Object ref) {
        return (ref instanceof HotSpotResolvedObjectType) ?
            (HotSpotResolvedObjectType)ref :
            ((HotSpotResolvedJavaMethod)ref).getDeclaringClass();
    }

    static String metadataName(HotSpotResolvedObjectType type) {
        AOTKlassData data = getAOTKlassData(type);
        assert data != null : "no data for " + type;
        return getAOTKlassData(type).getMetadataName();
    }

    private static String metadataName(HotSpotResolvedJavaMethod m) {
        return metadataName(m.getDeclaringClass()) + "." + m.getName() + m.getSignature().toMethodDescriptor();
    }

    static String metadataName(Object ref) {
        if (ref instanceof HotSpotResolvedJavaMethod) {
            HotSpotResolvedJavaMethod m = (HotSpotResolvedJavaMethod)ref;
            return metadataName(m);
        } else {
            assert ref instanceof HotSpotResolvedObjectType : "unexpected object type " + ref.getClass().getName();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType)ref;
            return metadataName(type);
        }
    }

    boolean representsStubs() {
        return representsStubs;
    }

    void clear() {
        for (CompiledMethodInfo c : compiledMethods) {
            c.clear();
        }
        this.compiledMethods = null;
        this.methods = null;
    }

}
