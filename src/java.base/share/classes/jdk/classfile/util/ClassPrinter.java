/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.classfile.util;

import java.util.function.Consumer;

import jdk.classfile.ClassModel;
import jdk.classfile.MethodModel;
import jdk.classfile.impl.ClassPrinterImpl;

/**
 *
 */
public sealed interface ClassPrinter permits ClassPrinterImpl {

    public enum VerbosityLevel {MEMBERS_ONLY, CRITICAL_ATTRIBUTES, TRACE_ALL}

     void printClass(ClassModel classModel);

     void printMethod(MethodModel methodModel);

     static ClassPrinter jsonPrinter(VerbosityLevel verbosity, Consumer<String> output) {
         return new ClassPrinterImpl(ClassPrinterImpl.JSON, verbosity, output);
     }

     static ClassPrinter xmlPrinter(VerbosityLevel verbosity, Consumer<String> output) {
         return new ClassPrinterImpl(ClassPrinterImpl.XML, verbosity, output);
     }

     static ClassPrinter yamlPrinter(VerbosityLevel verbosity, Consumer<String> output) {
         return new ClassPrinterImpl(ClassPrinterImpl.YAML, verbosity, output);
     }
}
