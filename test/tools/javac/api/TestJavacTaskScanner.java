/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4813736
 * @summary Additional functionality test of task and JSR 269
 * @author  Peter von der Ah\u00e9
 * @library ./lib
 * @run main TestJavacTaskScanner TestJavacTaskScanner.java
 */

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.*;
import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;

import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class TestJavacTaskScanner extends ToolTester {

    final JavacTaskImpl task;
    final Elements elements;
    final Types types;

    int numTokens;
    int numParseTypeElements;
    int numAllMembers;

    TestJavacTaskScanner(File file) {
        final Iterable<? extends JavaFileObject> compilationUnits =
            fm.getJavaFileObjects(new File[] {file});
        StandardJavaFileManager fm = getLocalFileManager(tool, null, null);
        task = (JavacTaskImpl)tool.getTask(null, fm, null, null, null, compilationUnits);
        task.getContext().put(ScannerFactory.scannerFactoryKey,
                new MyScanner.Factory(task.getContext(), this));
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

        System.out.println("#tokens: " + numTokens);
        System.out.println("#parseTypeElements: " + numParseTypeElements);
        System.out.println("#allMembers: " + numAllMembers);

        check(numTokens, "#Tokens", 1222);
        check(numParseTypeElements, "#parseTypeElements", 158);
        check(numAllMembers, "#allMembers", 52);
    }

    void check(int value, String name, int expected) {
        // allow some slop in the comparison to allow for minor edits in the
        // test and in the platform
        if (value < expected * 9 / 10)
            throw new Error(name + " lower than expected; expected " + expected + "; found: " + value);
        if (value > expected * 11 / 10)
            throw new Error(name + " higher than expected; expected " + expected + "; found: " + value);
    }

    void testParseType(TypeElement clazz) {
        DeclaredType type = (DeclaredType)task.parseType("List<String>", clazz);
        for (Element member : elements.getAllMembers((TypeElement)type.asElement())) {
            TypeMirror mt = types.asMemberOf(type, member);
            System.out.format("%s : %s -> %s%n", member.getSimpleName(), member.asType(), mt);
            numParseTypeElements++;
        }
    }

    public static void main(String... args) throws IOException {
        String srcdir = System.getProperty("test.src");
        new TestJavacTaskScanner(new File(srcdir, args[0])).run();
    }

    private void testGetAllMembers(TypeElement clazz) {
        for (Element member : elements.getAllMembers(clazz)) {
            System.out.format("%s : %s%n", member.getSimpleName(), member.asType());
            numAllMembers++;
        }
    }

    /* Similar to ToolTester.getFileManager, except that this version also ensures
     * javac classes will be available on the classpath.  The javac classes are assumed
     * to be on the classpath used to run this test (this is true when using jtreg).
     * The classes are found by obtaining the URL for a sample javac class, using
     * getClassLoader().getResource(), and then deconstructing the URL to find the
     * underlying directory or jar file to place on the classpath.
     */
    public StandardJavaFileManager getLocalFileManager(JavaCompiler tool,
                                                        DiagnosticListener<JavaFileObject> dl,
                                                        Charset encoding) {
        File javac_classes;
        try {
            final String javacMainClass = "com/sun/tools/javac/Main.class";
            URL url = getClass().getClassLoader().getResource(javacMainClass);
            if (url == null)
                throw new Error("can't locate javac classes");
            URI uri = url.toURI();
            String scheme = uri.getScheme();
            String ssp = uri.getSchemeSpecificPart();
            if (scheme.equals("jar")) {
                javac_classes = new File(new URI(ssp.substring(0, ssp.indexOf("!/"))));
            } else if (scheme.equals("file")) {
                javac_classes = new File(ssp.substring(0, ssp.indexOf(javacMainClass)));
            } else
                throw new Error("unknown URL: " + url);
        } catch (URISyntaxException e) {
            throw new Error(e);
        }
        System.err.println("javac_classes: " + javac_classes);

        StandardJavaFileManager fm = tool.getStandardFileManager(dl, null, encoding);
        try {
            fm.setLocation(SOURCE_PATH,  Arrays.asList(test_src));
            fm.setLocation(CLASS_PATH,   Arrays.asList(test_classes, javac_classes));
            fm.setLocation(CLASS_OUTPUT, Arrays.asList(test_classes));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return fm;
    }
}

class MyScanner extends Scanner {

    public static class Factory extends ScannerFactory {
        public Factory(Context context, TestJavacTaskScanner test) {
            super(context);
            this.test = test;
        }

        @Override
        public Scanner newScanner(CharSequence input, boolean keepDocComments) {
            if (input instanceof CharBuffer) {
                return new MyScanner(this, (CharBuffer)input, test);
            } else {
                char[] array = input.toString().toCharArray();
                return newScanner(array, array.length, keepDocComments);
            }
        }

        @Override
        public Scanner newScanner(char[] input, int inputLength, boolean keepDocComments) {
            return new MyScanner(this, input, inputLength, test);
        }

        private TestJavacTaskScanner test;
    }
    protected MyScanner(ScannerFactory fac, CharBuffer buffer, TestJavacTaskScanner test) {
        super(fac, buffer);
        this.test = test;
    }
    protected MyScanner(ScannerFactory fac, char[] input, int inputLength, TestJavacTaskScanner test) {
        super(fac, input, inputLength);
        this.test = test;
    }

    public void nextToken() {
        super.nextToken();
        Token tk = token();
        System.err.format("Saw token %s %n", tk.kind);
        test.numTokens++;
    }

    private TestJavacTaskScanner test;

}
