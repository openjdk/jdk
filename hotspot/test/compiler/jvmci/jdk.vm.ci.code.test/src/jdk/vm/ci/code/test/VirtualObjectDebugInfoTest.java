/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /
 * @modules jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.meta
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.code.site
 *          jdk.vm.ci/jdk.vm.ci.runtime
 *          jdk.vm.ci/jdk.vm.ci.amd64
 *          jdk.vm.ci/jdk.vm.ci.sparc
 * @compile CodeInstallationTest.java DebugInfoTest.java TestAssembler.java amd64/AMD64TestAssembler.java sparc/SPARCTestAssembler.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.code.test.VirtualObjectDebugInfoTest
 */

package jdk.vm.ci.code.test;

import java.util.ArrayList;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VirtualObjectDebugInfoTest extends DebugInfoTest {

    private static class TestClass {

        private long longField;
        private int intField;
        private float floatField;
        private Object[] arrayField;

        TestClass() {
            this.longField = 8472;
            this.intField = 42;
            this.floatField = 3.14f;
            this.arrayField = new Object[]{Integer.valueOf(58), this, null, Integer.valueOf(17), "Hello, World!"};
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestClass)) {
                return false;
            }

            TestClass other = (TestClass) o;
            if (this.longField != other.longField || this.intField != other.intField || this.floatField != other.floatField || this.arrayField.length != other.arrayField.length) {
                return false;
            }

            for (int i = 0; i < this.arrayField.length; i++) {
                // break cycle
                if (this.arrayField[i] == this && other.arrayField[i] == other) {
                    continue;
                }

                if (!Objects.equals(this.arrayField[i], other.arrayField[i])) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    public static TestClass buildObject() {
        return new TestClass();
    }

    private VirtualObject[] compileBuildObject(TestAssembler asm, JavaValue[] values) {
        TestClass template = new TestClass();
        ArrayList<VirtualObject> vobjs = new ArrayList<>();

        ResolvedJavaType retType = metaAccess.lookupJavaType(TestClass.class);
        VirtualObject ret = VirtualObject.get(retType, vobjs.size());
        vobjs.add(ret);
        values[0] = ret;

        ResolvedJavaType arrayType = metaAccess.lookupJavaType(Object[].class);
        VirtualObject array = VirtualObject.get(arrayType, vobjs.size());
        vobjs.add(array);

        // build array for ret.arrayField
        ResolvedJavaType integerType = metaAccess.lookupJavaType(Integer.class);
        JavaValue[] arrayContent = new JavaValue[template.arrayField.length];
        JavaKind[] arrayKind = new JavaKind[template.arrayField.length];
        for (int i = 0; i < arrayContent.length; i++) {
            arrayKind[i] = JavaKind.Object;
            if (template.arrayField[i] == null) {
                arrayContent[i] = JavaConstant.NULL_POINTER;
            } else if (template.arrayField[i] == template) {
                arrayContent[i] = ret;
            } else if (template.arrayField[i] instanceof Integer) {
                int value = (Integer) template.arrayField[i];
                VirtualObject boxed = VirtualObject.get(integerType, vobjs.size());
                vobjs.add(boxed);
                arrayContent[i] = boxed;
                boxed.setValues(new JavaValue[]{JavaConstant.forInt(value)}, new JavaKind[]{JavaKind.Int});
            } else if (template.arrayField[i] instanceof String) {
                String value = (String) template.arrayField[i];
                Register reg = asm.emitLoadPointer((HotSpotConstant) constantReflection.forString(value));
                arrayContent[i] = reg.asValue(target.getLIRKind(JavaKind.Object));
            } else {
                Assert.fail("unexpected value");
            }
        }
        array.setValues(arrayContent, arrayKind);

        // build return object
        ResolvedJavaField[] fields = retType.getInstanceFields(true);
        JavaValue[] retContent = new JavaValue[fields.length];
        JavaKind[] retKind = new JavaKind[fields.length];
        for (int i = 0; i < fields.length; i++) {
            retKind[i] = fields[i].getJavaKind();
            switch (retKind[i]) {
                case Long: // template.longField
                    retContent[i] = JavaConstant.forLong(template.longField);
                    break;
                case Int: // template.intField
                    Register intReg = asm.emitLoadInt(template.intField);
                    retContent[i] = asm.emitIntToStack(intReg);
                    break;
                case Float: // template.floatField
                    Register fReg = asm.emitLoadFloat(template.floatField);
                    retContent[i] = fReg.asValue(target.getLIRKind(JavaKind.Float));
                    break;
                case Object: // template.arrayField
                    retContent[i] = array;
                    break;
                default:
                    Assert.fail("unexpected field");
            }
        }
        ret.setValues(retContent, retKind);

        return vobjs.toArray(new VirtualObject[0]);
    }

    @Test
    public void testBuildObject() {
        test(this::compileBuildObject, getMethod("buildObject"), 7, JavaKind.Object);
    }
}
