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

/* @test
 * @summary test JNI_GetModule() API
 * @run main/native GetModule
 */

import java.lang.reflect.Module;
import java.lang.management.LockInfo;
public class GetModule {

    static {
        System.loadLibrary("GetModule");
    }

    static native Object callGetModule(java.lang.Class clazz);
    static native void callAddModuleReads(java.lang.reflect.Module from_module,
                                          java.lang.reflect.Module source_module);
    static native boolean callCanReadModule(java.lang.reflect.Module asking_module,
                                            java.lang.reflect.Module source_module);

    public static void main(String[] args) {
        Module module;

        // Module for array of primitives, should be "java.base"
        int[] int_array = {1, 2, 3};
        Module javaBaseModule;
        try {
            javaBaseModule = (Module)callGetModule(int_array.getClass());
            if (!javaBaseModule.getName().equals("java.base")) {
                throw new RuntimeException("Unexpected module name for array of primitives: " +
                                           javaBaseModule.getName());
            }
        } catch(Throwable e) {
            throw new RuntimeException("Unexpected exception for [I: " + e.toString());
        }

        // Module for java.lang.String
        java.lang.String str = "abc";
        try {
            module = (Module)callGetModule(str.getClass());
            if (!module.getName().equals("java.base")) {
                throw new RuntimeException("Unexpected module name for class String: " +
                                           module.getName());
            }
        } catch(Throwable e) {
            throw new RuntimeException("Unexpected exception for String: " + e.toString());
        }

        // Module for java.lang.management.LockInfo
        try {
            LockInfo li = new LockInfo("java.lang.Class", 57);
            module = (Module)callGetModule(li.getClass());
            if (!module.getName().equals("java.management")) {
                throw new RuntimeException("Unexpected module name for class LockInfo: " +
                                           module.getName());
            }
        } catch(Throwable e) {
            throw new RuntimeException("Unexpected exception for LockInfo: " + e.toString());
        }

        // Unnamed module.
        try {
            module = (Module)callGetModule(MyClassLoader.class);
            if (module == null || module.getName() != null) {
                throw new RuntimeException("Bad module for unnamed module");
            }
        } catch(Throwable e) {
            throw new RuntimeException("Unexpected exception for unnamed module: " + e.toString());
        }

        try {
            module = (Module)callGetModule(null);
            throw new RuntimeException("Failed to get expected NullPointerException");
        } catch(NullPointerException e) {
            // Expected
        }


        // Tests for JNI_AddModuleReads() //

        Module javaScriptingModule = javax.script.Bindings.class.getModule();
        if (javaScriptingModule == null) {
            throw new RuntimeException("Failed to get java.scripting module");
        }
        Module javaLoggingModule = java.util.logging.Level.class.getModule();
        if (javaLoggingModule == null) {
            throw new RuntimeException("Failed to get java.logging module");
        }

        if (callCanReadModule(javaLoggingModule, javaScriptingModule)) {
            throw new RuntimeException(
                "Expected FALSE because javaLoggingModule cannot read javaScriptingModule");
        }

        callAddModuleReads(javaLoggingModule, javaScriptingModule);
        callAddModuleReads(javaScriptingModule, GetModule.class.getModule()); // unnamed module

        try {
            callAddModuleReads(null, javaLoggingModule);
            throw new RuntimeException(
                "Expected NullPointerException for bad from_module not thrown");
        } catch(NullPointerException e) {
            // expected
        }

        try {
          callAddModuleReads(javaLoggingModule, null);
          throw new RuntimeException(
                "Expected NullPointerException for bad source_module not thrown");
        } catch(NullPointerException e) {
            // expected
        }


        // Tests for JNI_CanReadModule() //

        if (!callCanReadModule(javaLoggingModule, javaScriptingModule)) {
            throw new RuntimeException(
                "Expected TRUE because javaLoggingModule can read javaScriptingModule");
        }

        if (callCanReadModule(javaBaseModule, javaScriptingModule)) {
            throw new RuntimeException(
                "Expected FALSE because javaBaseModule cannnot read javaScriptingModule");
        }

        try {
            callCanReadModule(javaLoggingModule, null);
            throw new RuntimeException(
                "Expected NullPointerException for bad sourceModule not thrown");
        } catch(NullPointerException e) {
            // expected
        }

        try {
            callCanReadModule(null, javaScriptingModule);
            throw new RuntimeException(
                "Expected NullPointerException for bad asking_module not thrown");
        } catch(NullPointerException e) {
            // expected
        }
    }

    static class MyClassLoader extends ClassLoader { }
}
