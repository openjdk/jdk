/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

final class TestEditor {
    private final PropertyEditor editor;

    TestEditor(Class type) {
        System.out.println("Property class: " + type);

        this.editor = PropertyEditorManager.findEditor(type);
        if (this.editor == null)
            throw new Error("could not find editor for " + type);

        System.out.println("PropertyEditor class: " + this.editor.getClass());
        validate(null, null);
    }

    void testJava(Object value) {
        this.editor.setValue(value);

        MemoryFileManager manager = new MemoryFileManager();
        Object object = manager.invoke(this.editor.getJavaInitializationString());

        System.out.println("Property value before: " + value);
        System.out.println("Property value after: " + object);

        if (!areEqual(value, object))
            throw new Error("values are not equal");
    }

    void testValue(Object value, String text) {
        this.editor.setValue(value);
        validate(value, text);
    }

    void testText(String text, Object value) {
        this.editor.setAsText(text);
        validate(value, text);
    }

    private void validate(Object value, String text) {
        if (!areEqual(value, this.editor.getValue()))
            throw new Error("value should be " + value);

        if (!areEqual(text, this.editor.getAsText()))
            throw new Error("text should be " + text);
    }

    private static boolean areEqual(Object object1, Object object2) {
        return (object1 == null)
                ? object2 == null
                : object1.equals(object2);
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager {
        private static final String CLASS = "Executor";
        private static final String METHOD = "execute";
        private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();
        private final Map<String, MemoryClass> map = new HashMap<String, MemoryClass>();
        private final MemoryClassLoader loader = new MemoryClassLoader();

        MemoryFileManager() {
            super(COMPILER.getStandardFileManager(null, null, null));
        }

        public Object invoke(String expression) {
            MemorySource file = new MemorySource(CLASS, METHOD, expression);
            if (!COMPILER.getTask(null, this, null, null, null, Arrays.asList(file)).call())
                throw new Error("compilation failed");

            MemoryClass mc = this.map.get(CLASS);
            if (mc == null)
                throw new Error("class not found: " + CLASS);

            Class c = this.loader.loadClass(CLASS, mc.toByteArray());
            try {
                return c.getMethod(METHOD).invoke(null);
            }
            catch (Exception exception) {
                throw new Error(exception);
            }
        }

        public MemoryClass getJavaFileForOutput(Location location, String name, Kind kind, FileObject source) {
            MemoryClass type = this.map.get(name);
            if (type == null) {
                type = new MemoryClass(name);
                this.map.put(name, type);
            }
            return type;
        }
    }

    private static final class MemoryClassLoader extends ClassLoader {
        public Class<?> loadClass(String name, byte[] array) {
            return defineClass(name, array, 0, array.length);
        }
    }

    private static class MemoryObject extends SimpleJavaFileObject {
        protected MemoryObject(String name, Kind kind) {
            super(toURI(name, kind.extension), kind);
        }

        private static URI toURI(String name, String extension) {
            try {
                return new URI("mfm:///" + name.replace('.', '/') + extension);
            }
            catch (URISyntaxException exception) {
                throw new Error(exception);
            }
        }
    }

    private static final class MemoryClass extends MemoryObject {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        MemoryClass(String className) {
            super(className, Kind.CLASS);
        }

        public ByteArrayOutputStream openOutputStream() {
            this.baos.reset();
            return this.baos;
        }

        public byte[] toByteArray() {
            return this.baos.toByteArray();
        }
    }

    private static final class MemorySource extends MemoryObject {
        private final String value;

        MemorySource(String className, String methodName, String expression) {
            super(className, Kind.SOURCE);
            this.value
                    = "public class " + className + " {\n"
                    + "    public static Object " + methodName + "() throws Exception {\n"
                    + "        return " + expression + ";\n"
                    + "    }\n"
                    + "}\n";
        }

        public CharSequence getCharContent(boolean ignore) {
            return this.value;
        }
    }
}
