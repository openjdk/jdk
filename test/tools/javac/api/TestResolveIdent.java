/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug     6374357 6308351
 * @summary PackageElement.getEnclosedElements() throws ClassReader$BadClassFileException
 * @author  Peter von der Ah\u00e9
 * @run main TestResolveIdent
 */

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.JavacFileManager;
import java.io.File;
import java.io.IOException;
import javax.lang.model.element.TypeElement;
import javax.tools.ToolProvider;

public class TestResolveIdent {

    @SuppressWarnings("deprecation")
    static Class<?> getDeprecatedClass() {
        return java.io.StringBufferInputStream.class;
    }

    public static void main(String[] args) throws IOException {
        javax.tools.JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavacTaskImpl task = (JavacTaskImpl)tool.getTask(null, null, null, null, null, null);
        JavaCompiler compiler = JavaCompiler.instance(task.getContext());
        System.out.println(compiler.resolveIdent(getDeprecatedClass().getCanonicalName()));
    }

}
