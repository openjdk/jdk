/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8004931
 * @compile NoBeans.java
 * @summary A compile-only test to ensure that implementations of Packer
 *   and Unpacker can be compiled without implementating the
 *   addPropertyChangeListener and removePropertyChangeListener methods.
 */

import java.io.*;
import java.util.*;
import java.util.jar.*;

public class NoBeans {

    static class MyPacker implements Pack200.Packer {
        public SortedMap<String,String> properties() { return null; }
        public void pack(JarFile in, OutputStream out) { }
        public void pack(JarInputStream in, OutputStream out) { }
    }

    static class MyUnpacker implements Pack200.Unpacker {
        public SortedMap<String,String> properties() { return null; }
        public void unpack(InputStream in, JarOutputStream out) { }
        public void unpack(File in, JarOutputStream out) { }
    }
}
