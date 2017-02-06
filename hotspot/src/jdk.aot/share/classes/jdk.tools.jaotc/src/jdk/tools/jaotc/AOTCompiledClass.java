/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Class encapsulating Graal-compiled output of a Java class. The compilation result of all methods
 * of a class {@code className} are maintained in an array list.
 */
public class AOTCompiledClass {

    public static class AOTKlassData {
        int gotIndex; // Index (offset/8) to the got in the .metaspace.got section
        int classId;  // Unique ID
        // Offset to compiled methods data in the .methods.offsets section.
        int compiledMethodsOffset;
        // Offset to dependent methods data.
        int dependentMethodsOffset;
        long fingerprint;           // Class fingerprint

        private final String name;
        private boolean isArray;

        /**
         * List of dependent compiled methods which have a reference to this class.
         */
        private ArrayList<CompiledMethodInfo> dependentMethods;

        public AOTKlassData(BinaryContainer binaryContainer, String name, long fingerprint, int classId) {
            this.dependentMethods = new ArrayList<>();
            this.classId = classId;
            this.fingerprint = fingerprint;
            this.gotIndex = binaryContainer.addTwoSlotMetaspaceSymbol(name);
            this.compiledMethodsOffset = -1; // Not compiled classes do not have compiled methods.
            this.dependentMethodsOffset = -1;
            this.name = name;
            this.isArray = name.length() > 0 && name.charAt(0) == '[';
        }

        public long getFingerprint() {
            return fingerprint;
        }

        /**
         * Add a method to the list of dependent methods.
         */
        public synchronized boolean addDependentMethod(CompiledMethodInfo cm) {
            return dependentMethods.add(cm);
        }

        /**
         * Return the array list of dependent class methods.
         *
         * @return array list of dependent methods
         */
        public ArrayList<CompiledMethodInfo> getDependentMethods() {
            return dependentMethods;
        }

        /**
         * Returns if this class has dependent methods.
         *
         * @return true if dependent methods exist, false otherwise
         */
        public boolean hasDependentMethods() {
            return !dependentMethods.isEmpty();
        }

        public void setCompiledMethodsOffset(int offset) {
            compiledMethodsOffset = offset;
        }

        protected void putAOTKlassData(BinaryContainer binaryContainer, ReadOnlyDataContainer container) {
            int cntDepMethods = dependentMethods.size();
            // Create array of dependent methods IDs. First word is count.
            ReadOnlyDataContainer dependenciesContainer = binaryContainer.getKlassesDependenciesContainer();
            this.dependentMethodsOffset = binaryContainer.addMethodsCount(cntDepMethods, dependenciesContainer);
            for (CompiledMethodInfo methodInfo : dependentMethods) {
                dependenciesContainer.appendInt(methodInfo.getCodeId());
            }
            verify();

            // @formatter:off
            /*
             * The offsets layout should match AOTKlassData structure in AOT JVM runtime
             */
            int offset = container.getByteStreamSize();
            container.createSymbol(offset, Kind.OBJECT, Binding.GLOBAL, 0, name);
                      // Add index (offset/8) to the got in the .metaspace.got section
            container.appendInt(gotIndex).
                      // Add unique ID
                      appendInt(classId).
                      // Add the offset to compiled methods data in the .metaspace.offsets section.
                      appendInt(compiledMethodsOffset).
                      // Add the offset to dependent methods data in the .metaspace.offsets section.
                      appendInt(dependentMethodsOffset).
                      // Add fingerprint.
                      appendLong(fingerprint);
            // @formatter:on
        }

        private void verify() {
            assert gotIndex > 0 : "incorrect gotIndex: " + gotIndex + " for klass: " + name;
            assert isArray || fingerprint != 0 : "incorrect fingerprint: " + fingerprint + " for klass: " + name;
            assert compiledMethodsOffset >= -1 : "incorrect compiledMethodsOffset: " + compiledMethodsOffset + " for klass: " + name;
            assert dependentMethodsOffset >= -1 : "incorrect dependentMethodsOffset: " + dependentMethodsOffset + " for klass: " + name;
            assert classId >= 0 : "incorrect classId: " + classId + " for klass: " + name;
        }

    }

    private final HotSpotResolvedObjectType resolvedJavaType;

    /**
     * List of all collected class data.
     */
    private static Map<String, AOTKlassData> klassData = new HashMap<>();

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
    public AOTCompiledClass(ArrayList<CompiledMethodInfo> compiledMethods) {
        this.resolvedJavaType = null;
        this.compiledMethods = compiledMethods;
        this.representsStubs = true;
    }

    /**
     * Construct an object with compiled versions of the named class.
     */
    public AOTCompiledClass(ResolvedJavaType resolvedJavaType) {
        this.resolvedJavaType = (HotSpotResolvedObjectType) resolvedJavaType;
        this.compiledMethods = new ArrayList<>();
        this.representsStubs = false;
    }

    /**
     * @return the ResolvedJavaType of this class
     */
    public ResolvedJavaType getResolvedJavaType() {
        return resolvedJavaType;
    }

    /**
     * Get the list of methods which should be compiled.
     */
    public ArrayList<ResolvedJavaMethod> getMethods() {
        ArrayList<ResolvedJavaMethod> m = methods;
        methods = null; // Free - it is not used after that.
        return m;
    }

    /**
     * Get the number of all AOT classes.
     */
    public static int getClassesCount() {
        return classesCount;
    }

    /**
     * Get the number of methods which should be compiled.
     *
     * @return number of methods which should be compiled
     */
    public int getMethodCount() {
        return methods.size();
    }

    /**
     * Add a method to the list of methods to be compiled.
     */
    public void addMethod(ResolvedJavaMethod method) {
        methods.add(method);
    }

    /**
     * Returns if this class has methods which should be compiled.
     *
     * @return true if this class contains methods which should be compiled, false otherwise
     */
    public boolean hasMethods() {
        return !methods.isEmpty();
    }

    /**
     * Add a method to the list of compiled methods. This method needs to be thread-safe.
     */
    public synchronized boolean addCompiledMethod(CompiledMethodInfo cm) {
        return compiledMethods.add(cm);
    }

    /**
     * Return the array list of compiled class methods.
     *
     * @return array list of compiled methods
     */
    public ArrayList<CompiledMethodInfo> getCompiledMethods() {
        return compiledMethods;
    }

    /**
     * Returns if this class has successfully compiled methods.
     *
     * @return true if methods were compiled, false otherwise
     */
    public boolean hasCompiledMethods() {
        return !compiledMethods.isEmpty();
    }

    /**
     * Add a klass data.
     */
    public synchronized static AOTKlassData addAOTKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        String name = type.getName();
        long fingerprint = type.getFingerprint();
        AOTKlassData data = klassData.get(name);
        if (data != null) {
            assert data.getFingerprint() == fingerprint : "incorrect fingerprint data for klass: " + name;
        } else {
            data = new AOTKlassData(binaryContainer, name, fingerprint, classesCount++);
            klassData.put(name, data);
        }
        return data;
    }

    public synchronized static AOTKlassData getAOTKlassData(String name) {
        return klassData.get(name);
    }

    public synchronized static AOTKlassData getAOTKlassData(HotSpotResolvedObjectType type) {
        return getAOTKlassData(type.getName());
    }

    public void addAOTKlassData(BinaryContainer binaryContainer) {
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

    public synchronized static AOTKlassData addFingerprintKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        if (type.isArray()) {
            return addAOTKlassData(binaryContainer, type);
        }
        assert type.getFingerprint() != 0 : "no fingerprint for " + type.getName();
        AOTKlassData old = getAOTKlassData(type);
        if (old != null) {
            boolean assertsEnabled = false;
            assert assertsEnabled = true;
            if (assertsEnabled) {
                HotSpotResolvedObjectType s = type.getSuperclass();
                if (s != null) {
                    assert getAOTKlassData(s) != null : "fingerprint super " + s.getName() + " needed for " + type.getName();
                }
                for (HotSpotResolvedObjectType i : type.getInterfaces()) {
                    assert getAOTKlassData(i) != null : "fingerprint super " + i.getName() + " needed for " + type.getName();
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
    public void putMethodsData(BinaryContainer binaryContainer) {
        ReadOnlyDataContainer container = binaryContainer.getMethodsOffsetsContainer();
        int cntMethods = compiledMethods.size();
        int startMethods = binaryContainer.addMethodsCount(cntMethods, container);
        for (CompiledMethodInfo methodInfo : compiledMethods) {
            methodInfo.addMethodOffsets(binaryContainer, container);
        }
        String name = resolvedJavaType.getName();
        AOTKlassData data = klassData.get(name);
        assert data != null : "missing data for klass: " + name;
        assert data.getFingerprint() == resolvedJavaType.getFingerprint() : "incorrect fingerprint for klass: " + name;
        int cntDepMethods = data.dependentMethods.size();
        assert cntDepMethods > 0 : "no dependent methods for compiled klass: " + name;
        data.setCompiledMethodsOffset(startMethods);
    }

    public static void putAOTKlassData(BinaryContainer binaryContainer) {
        ReadOnlyDataContainer container = binaryContainer.getKlassesOffsetsContainer();
        for (AOTKlassData data : klassData.values()) {
            data.putAOTKlassData(binaryContainer, container);
        }
    }

    public boolean representsStubs() {
        return representsStubs;
    }

    public void clear() {
        for (CompiledMethodInfo c : compiledMethods) {
            c.clear();
        }
        this.compiledMethods = null;
        this.methods = null;
    }

}
