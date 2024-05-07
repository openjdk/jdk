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
 * @summary Testing ClassFile constant pool cloning.
 * @run junit ConstantPoolCopyTest
 */
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassReader;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.DynamicConstantPoolEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.SplitConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.ConstantPool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConstantPoolCopyTest {
    private static ClassModel[] rtJarToClassLow(FileSystem fs) {
        try {
            var cc = ClassFile.of();
            var modules = Stream.of(
                    Files.walk(fs.getPath("modules/java.base/java")),
                    Files.walk(fs.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")))
                    .flatMap(p -> p)
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .map(ConstantPoolCopyTest::readAllBytes)
                    .map(bytes -> cc.parse(bytes))
                    .toArray(ClassModel[]::new);
            return modules;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static byte[] readAllBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    static ClassModel[] classes() {
        return rtJarToClassLow(FileSystems.getFileSystem(URI.create("jrt:/")));
    }

    @ParameterizedTest
    @MethodSource("classes")
    void cloneConstantPool(ClassModel c) throws Exception {
        ConstantPool cp = c.constantPool();
        ConstantPoolBuilder cp2 = new SplitConstantPool((ClassReader) cp);

        assertEquals(cp2.size(), cp.size(), "Cloned constant pool must be same size");

        for (int i = 1; i < cp.size();) {
            PoolEntry cp1i = cp.entryByIndex(i);
            PoolEntry cp2i = cp2.entryByIndex(i);
            assertTrue(representsTheSame(cp1i, cp2i), cp2i + " does not represent the same constant pool entry as " + cp1i);
            i+= cp1i.width();
        }

        // Bootstrap methods
        assertEquals(cp.bootstrapMethodCount(), cp2.bootstrapMethodCount());
        for (int i = 0; i<cp.bootstrapMethodCount(); i++)
            assertTrue(sameBootstrap(cp.bootstrapMethodEntry(i), cp2.bootstrapMethodEntry(i)));
    }

    // This differs from a value-based equals in that the constant pool field can differ while the constants still
    // represent the same
    boolean representsTheSame(PoolEntry first, PoolEntry second) {
        if (first instanceof Utf8Entry i) {
            if (second instanceof Utf8Entry j) {
                return i.stringValue().equals(j.stringValue());
            } else {
                return false;
            }
        } else if (first instanceof ClassEntry i) {
            if (second instanceof ClassEntry j) {
                return i.asInternalName().equals(j.asInternalName());
            } else {
                return false;
            }
        } else if (first instanceof PackageEntry i) {
            if (second instanceof PackageEntry j) {
                return i.name().stringValue().equals(j.name().stringValue());
            } else {
                return false;
            }
        } else if (first instanceof ModuleEntry i) {
            if (second instanceof ModuleEntry j) {
                return i.name().stringValue().equals(j.name().stringValue());
            } else {
                return false;
            }
        } else if (first instanceof NameAndTypeEntry i) {
            if (second instanceof NameAndTypeEntry j) {
                return i.name().stringValue().equals(j.name().stringValue()) && i.type().stringValue().equals(j.type().stringValue());
            } else {
                return false;
            }
        } else if (first instanceof FieldRefEntry i) {
            if (second instanceof FieldRefEntry j) {
                return sameMemberRef(i,j);
            } else {
                return false;
            }
        } else if (first instanceof MethodRefEntry i) {
            if (second instanceof MethodRefEntry j) {
                return sameMemberRef(i, j);
            } else {
                return false;
            }
        } else if (first instanceof InterfaceMethodRefEntry i) {
            if (second instanceof InterfaceMethodRefEntry j) {
                return sameMemberRef(i, j);
            } else {
                return false;
            }
        } else if (first instanceof InvokeDynamicEntry i) {
            if (second instanceof InvokeDynamicEntry j) {
                return sameDynamic(i, j);
            } else {
                return false;
            }
        } else if (first instanceof ConstantDynamicEntry i) {
            if (second instanceof ConstantDynamicEntry j) {
                return sameDynamic(i, j);
            } else {
                return false;
            }
        } else if (first instanceof MethodHandleEntry i) {
            if (second instanceof MethodHandleEntry j) {
                return i.kind() == j.kind() && sameMemberRef(i.reference(), j.reference());
            } else {
                return false;
            }
        } else if (first instanceof MethodTypeEntry i) {
            if (second instanceof MethodTypeEntry j) {
                return representsTheSame(i.descriptor(), j.descriptor());
            }
        } else if (first instanceof StringEntry i) {
            if (second instanceof StringEntry j) {
                return i.stringValue().equals(j.stringValue());
            } else {
                return false;
            }
        } else if (first instanceof IntegerEntry i) {
            if (second instanceof IntegerEntry j) {
                return i.intValue() == j.intValue();
            } else {
                return false;
            }
        } else if (first instanceof FloatEntry i) {
            if (second instanceof FloatEntry j) {
                return Float.isNaN(i.floatValue()) ? Float.isNaN(j.floatValue()) : i.floatValue() == j.floatValue();
            } else {
                return false;
            }
        } else if (first instanceof LongEntry i) {
            if (second instanceof LongEntry j) {
                return i.longValue() == j.longValue();
            } else {
                return false;
            }
        } else if (first instanceof DoubleEntry i) {
            if (second instanceof DoubleEntry j) {
                return Double.isNaN(i.doubleValue()) ? Double.isNaN(j.doubleValue()) : i.doubleValue() == j.doubleValue();
            } else {
                return false;
            }
        }

        return false;
    }

    boolean sameMemberRef(MemberRefEntry first, MemberRefEntry second) {
        return first.owner().asInternalName().equals(second.owner().asInternalName()) &&
               representsTheSame(first.nameAndType(), second.nameAndType());
    }

    boolean sameDynamic(DynamicConstantPoolEntry first, DynamicConstantPoolEntry second) {
        return sameBootstrap(first.bootstrap(), second.bootstrap()) &&
                representsTheSame(first.nameAndType(), second.nameAndType());
    }

    boolean sameBootstrap(BootstrapMethodEntry first, BootstrapMethodEntry second) {
        if (representsTheSame(first.bootstrapMethod(), second.bootstrapMethod()) &&
                first.arguments().size() == second.arguments().size()) {
            var firstArgs = first.arguments();
            var secondArgs = second.arguments();

            for (int i = 0; i < firstArgs.size(); ++i) {
                if (!representsTheSame(firstArgs.get(i), secondArgs.get(i)))
                    return false;
            }

            return true;
        }

        return false;
    }
}
