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

import sun.jvm.hotspot.memory.SymbolTable;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

/**
 * This class is launched in a sub-process by the main test,
 * SASymbolTableTest.java.
 *
 * It uses SA to connect to another JVM process, whose PID is specified in args[].
 * The purpose of the test is to validate that we can walk the SymbolTable
 * and CompactHashTable of the other process. Everything should work regardless
 * of whether the other process runs in CDS mode or not.
 *
 * Note: CompactHashTable is used only when CDS is enabled.
 */
public class SASymbolTableTestAgent extends Tool {
    public SASymbolTableTestAgent() {
        super();
    }
    public static void main(String args[]) {
        SASymbolTableTestAgent tool = new SASymbolTableTestAgent();
        tool.execute(args);
    }

    static String[] commonNames = {
        "java/lang/Object",
        "java/lang/String",
        "java/lang/Class",
        "java/lang/Cloneable",
        "java/lang/ClassLoader",
        "java/io/Serializable",
        "java/lang/System",
        "java/lang/Throwable",
        "java/lang/Error",
        "java/lang/ThreadDeath",
        "java/lang/Exception",
        "java/lang/RuntimeException",
        "java/lang/SecurityManager",
        "java/security/ProtectionDomain",
        "java/security/AccessControlContext",
        "java/security/SecureClassLoader",
        "java/lang/ClassNotFoundException",
        "java/lang/NoClassDefFoundError",
        "java/lang/LinkageError",
        "java/lang/ClassCastException",
        "java/lang/ArrayStoreException",
        "java/lang/VirtualMachineError",
        "java/lang/OutOfMemoryError",
        "java/lang/StackOverflowError",
        "java/lang/IllegalMonitorStateException",
        "java/lang/ref/Reference",
        "java/lang/ref/SoftReference",
        "java/lang/ref/WeakReference",
        "java/lang/ref/FinalReference",
        "java/lang/ref/PhantomReference",
        "java/lang/ref/Finalizer",
        "java/lang/Thread",
        "java/lang/ThreadGroup",
        "java/util/Properties",
        "java/lang/reflect/AccessibleObject",
        "java/lang/reflect/Field",
        "java/lang/reflect/Method",
        "java/lang/reflect/Constructor",
        "java/lang/invoke/MethodHandle",
        "java/lang/invoke/MemberName",
        "java/lang/invoke/MethodHandleNatives",
        "java/lang/invoke/MethodType",
        "java/lang/BootstrapMethodError",
        "java/lang/invoke/CallSite",
        "java/lang/invoke/ConstantCallSite",
        "java/lang/invoke/MutableCallSite",
        "java/lang/invoke/VolatileCallSite",
        "java/lang/StringBuffer",
        "java/lang/StringBuilder",
        "java/io/ByteArrayInputStream",
        "java/io/File",
        "java/net/URLClassLoader",
        "java/net/URL",
        "java/util/jar/Manifest",
        "java/security/CodeSource",
    };

    static String[] badNames = {
        "java/lang/badbadbad",
        "java/io/badbadbadbad",
        "this*symbol*must*not*exist"
    };

    public void run() {
        System.out.println("SASymbolTableTestAgent: starting");
        VM vm = VM.getVM();
        SymbolTable table = vm.getSymbolTable();

        // (a) These are names that are likely to exist in the symbol table
        //     of a JVM after start-up. They were taken from vmSymbols.hpp
        //     during the middle of JDK9 development.
        //
        //     The purpose is not to check that each name must exist (a future
        //     version of JDK may not preload some of the classes).
        //
        //     The purpose of this loops is to ensure that we check a lot of symbols,
        //     so we will (most likely) hit on both VALUE_ONLY_BUCKET_TYPE and normal bucket type
        //     in CompactHashTable.probe().
        for (String n : commonNames) {
            Symbol s = table.probe(n);
            System.out.format("%-40s = %s\n", n, s);
        }

        System.out.println("======================================================================");

        // (b) Also test a few strings that are known to not exist in the table. This will
        //     both the compact table (if it exists) and the regular table to be walked.
        for (String n : badNames) {
            Symbol s = table.probe(n);
            System.out.format("%-40s = %s\n", n, s);
        }
    }
}
