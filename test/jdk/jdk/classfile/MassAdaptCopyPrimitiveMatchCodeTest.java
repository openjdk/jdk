/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing Classfile massive class adaptation.
 * @run junit MassAdaptCopyPrimitiveMatchCodeTest
 */
import helpers.InstructionModelToCodeBuilder;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.instruction.InvokeInstruction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MassAdaptCopyPrimitiveMatchCodeTest.
 */
class MassAdaptCopyPrimitiveMatchCodeTest {

    final static List<Path> testClasses(Path which) {
        try {
            return Files.walk(which)
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().endsWith(".class"))
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("Test failed in set-up - " + ex.getMessage(), ex);
        }
    }

    final static List<Path> testClasses = testClasses(
            //FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util")
            //Path.of("target", "classes")
            Paths.get(URI.create(MassAdaptCopyPrimitiveMatchCodeTest.class.getResource("MassAdaptCopyPrimitiveMatchCodeTest.class").toString())).getParent()
    );

    String base;
    boolean failure;

    @Test
    @Disabled("for a reason...")
    public void testCodeMatch() throws Exception {
        for (Path path : testClasses) {
            try {
                copy(path.toString(),
                        Files.readAllBytes(path));
                if (failure) {
                    fail("Copied bytecode does not match: " + path);
                }
            } catch(Throwable ex) {
                System.err.printf("FAIL: MassAdaptCopyPrimitiveMatchCodeTest - %s%n", ex.getMessage());
                ex.printStackTrace(System.err);
                throw ex;
            }
        }
    }

    void copy(String name, byte[] bytes) throws Exception {
        //System.err.printf("MassAdaptCopyPrimitiveMatchCodeTest - %s%n", name);
        var cc = Classfile.of();
        ClassModel cm =cc.parse(bytes);
        Map<String, byte[]> m2b = new HashMap<>();
        Map<String, CodeAttribute> m2c = new HashMap<>();
        byte[] resultBytes =
                cc.transform(cm, (cb, e) -> {
                    if (e instanceof MethodModel mm) {
                        Optional<CodeModel> code = mm.code();
                        if (code.isPresent()) {
                            CodeAttribute cal = (CodeAttribute) code.get();
                            byte[] mbytes = cal.codeArray();
                            String key = methodToKey(mm);
                            m2b.put(key, mbytes);
                            m2c.put(key, cal);
                        }
                        cb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel xm)
                                mb.transformCode(xm, (xr, xe) -> InstructionModelToCodeBuilder.toBuilder(xe, xr));
                            else
                                mb.with(me);
                        });
                    }
                    else
                        cb.with(e);
                });
        //TODO: work-around to compiler bug generating multiple constant pool entries within records
        if (cm.findAttribute(Attributes.RECORD).isPresent()) {
            System.err.printf("MassAdaptCopyPrimitiveMatchCodeTest: Ignored because it is a record%n         - %s%n", name);
            return;
        }
        ClassModel rcm = cc.parse(resultBytes);
        for (MethodModel rmm : rcm.methods()) {
            Optional<CodeModel> code = rmm.code();
            if (code.isPresent()) {
                CodeModel codeModel = code.get();
                CodeAttribute rcal = (CodeAttribute) codeModel;
                String key = methodToKey(rmm);
                byte[] rbytes = rcal.codeArray();
                byte[] obytes = m2b.get(key);
                if (!Arrays.equals(rbytes, obytes)) {
                    System.err.printf("Copy has mismatched bytecode -- Method: %s.%s%n", name, rmm.methodName().stringValue());
                    boolean secondFailure = false;
                    failure = true;
                    int rlen = rcal.codeLength();
                    CodeAttribute ocal = m2c.get(key);
                    int olen = ocal.codeLength();
                    if (rlen != olen) {
                        System.err.printf("  Lengths do not match: orig != copy: %d != %d%n", olen, rlen);
                    }
                    int len = Math.max(rlen, olen);
                    // file instructions
                    CodeElement[] rima = new Instruction[len];
                    CodeElement[] oima = new Instruction[len];
                    int bci = 0;
                    for (CodeElement im : codeModel) {
                        if (im instanceof Instruction i) {
                            rima[bci] = im;
                            bci += i.sizeInBytes();
                        }
                    }
                    bci = 0;
                    for (CodeElement im : ((CodeModel) ocal)) {
                        if (im instanceof Instruction i) {
                            oima[bci] = im;
                            bci += i.sizeInBytes();
                        }
                    }
                    // find first bad BCI and instruction
                    int bciCurrentInstruction = -1;
                    int bciFirstBadInstruction = -1;
                    for (int i = 0; i < len; ++i) {
                        if (oima[i] != null) {
                            bciCurrentInstruction = i;
                        }
                        if (obytes[i] != rbytes[i]) {
                            if (bciFirstBadInstruction < 0) {
                                System.err.printf("  bytecode differs firstly at BCI [%d]; expected value is <%d> but was <%d>. Instruction BCI %d%n",
                                        i, obytes[i], rbytes[i], bciCurrentInstruction);
                                bciFirstBadInstruction = bciCurrentInstruction;
                            } else {
                                secondFailure = true;
                                break;
                            }
                        }
                    }
                    System.err.printf("  BCI  Orig Copy Original ---> Copy%n");
                    for (int i = 0; i < len; ++i) {
                        System.err.printf("  %4d ", i);
                        if (i < olen)
                            System.err.printf("%4d ", obytes[i] & 0xFF);
                        else
                            System.err.printf("     ");
                        if (i < rlen)
                            System.err.printf("%4d ", rbytes[i] & 0xFF);
                        else
                            System.err.printf("     ");
                        CodeElement oim = oima[i];
                        if (oim != null)
                            System.err.printf("%s  ", oim);
                        CodeElement rim = rima[i];
                        if (rim != null)
                            System.err.printf("---> %s  ", rim);
                        System.err.printf("%n");
                        if (bciFirstBadInstruction == i
                                && oim instanceof InvokeInstruction oii
                                && rim instanceof InvokeInstruction rii) {
                            if (oii.isInterface() == rii.isInterface()
                                    && oii.name().stringValue().equals(rii.name().stringValue())
                                    && oii.owner().asInternalName().equals(rii.owner().asInternalName())
                                    && oii.type().stringValue().equals(rii.type().stringValue())
                                    && oii.count() == rii.count()
                                    && oii.sizeInBytes() == rii.sizeInBytes()
                                    && oii.opcode() == rii.opcode()) {
                                // they match, so was duplicate CP entries, e.g Object.clone()
                                // get a pass if this was the only failure
                                System.err.printf("NVM - duplicate CP entry -- ignored%n");
                                failure = secondFailure;
                            }
                        }
                    }
                }
            }
        }
    }

    String methodToKey(MethodModel mm) {
        return mm.methodName().stringValue() + "@" + mm.methodType().stringValue() + (mm.flags().has(AccessFlag.STATIC) ? "$" : "!");
    }


}
