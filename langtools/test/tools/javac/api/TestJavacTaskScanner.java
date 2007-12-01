/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug     4813736
 * @summary Additional functionality test of task and JSR 269
 * @author  Peter von der Ah\u00e9
 * @ignore "misuse" of context breaks with 6358786
 * @run main TestJavacTaskScanner TestJavacTaskScanner.java
 */

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.*; // XXX
import com.sun.tools.javac.util.*; // XXX
import java.io.*;
import java.nio.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import javax.tools.JavaFileManager;

public class TestJavacTaskScanner implements Runnable {

    final JavacTaskImpl task;
    final Elements elements;
    final Types types;

    TestJavacTaskScanner(JavacTaskImpl task) {
        this.task = task;
        elements = task.getElements();
        types = task.getTypes();
    }

    public void run() {
        Iterable<? extends TypeElement> toplevels;
        try {
            toplevels = task.enter(task.parse());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        for (TypeElement clazz : toplevels) {
            System.out.format("Testing %s:%n%n", clazz.getSimpleName());
            testParseType(clazz);
            testGetAllMembers(clazz);
            System.out.println();
            System.out.println();
            System.out.println();
        }
    }

    void testParseType(TypeElement clazz) {
        DeclaredType type = (DeclaredType)task.parseType("List<String>", clazz);
        for (Element member : elements.getAllMembers((TypeElement)type.asElement())) {
            TypeMirror mt = types.asMemberOf(type, member);
            System.out.format("%s : %s -> %s%n", member.getSimpleName(), member.asType(), mt);
        }
    }

    public static void main(String... args) throws IOException {
        JavaCompilerTool tool = ToolProvider.defaultJavaCompiler();
        JavaFileManager fm = tool.getStandardFileManager();
        String srcdir = System.getProperty("test.src");
        File file = new File(srcdir, args[0]);
        JavacTaskImpl task = (JavacTaskImpl)tool.run(null, fm.getFileForInput(file.toString()));
        MyScanner.Factory.preRegister(task.getContext());
        TestJavacTaskScanner tester = new TestJavacTaskScanner(task);
        tester.run();
    }

    private void testGetAllMembers(TypeElement clazz) {
        for (Element member : elements.getAllMembers(clazz)) {
            System.out.format("%s : %s", member.getSimpleName(), member.asType());
        }
    }
}

class MyScanner extends Scanner {

    public static class Factory extends Scanner.Factory {
        public static void preRegister(final Context context) {
            context.put(scannerFactoryKey, new Context.Factory<Scanner.Factory>() {
                public Factory make() {
                    return new Factory(context);
                }
            });
        }
        public Factory(Context context) {
            super(context);
        }

        @Override
        public Scanner newScanner(CharSequence input) {
            if (input instanceof CharBuffer) {
                return new MyScanner(this, (CharBuffer)input);
            } else {
                char[] array = input.toString().toCharArray();
                return newScanner(array, array.length);
            }
        }

        @Override
        public Scanner newScanner(char[] input, int inputLength) {
            return new MyScanner(this, input, inputLength);
        }
    }
    protected MyScanner(Factory fac, CharBuffer buffer) {
        super(fac, buffer);
    }
    protected MyScanner(Factory fac, char[] input, int inputLength) {
        super(fac, input, inputLength);
    }

    public void nextToken() {
        super.nextToken();
        System.err.format("Saw token %s (%s)%n", token(), name());
    }
}
