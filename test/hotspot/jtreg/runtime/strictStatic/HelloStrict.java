/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8888888
 * @summary demo of tracking of strict static fields
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:EnforceStrictStatics=3 HelloStrict
 */

import java.lang.reflect.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.invoke.*;

public interface HelloStrict {
    @interface Strict { }  // placeholder, ignored
    class Aregular_OK {
        @Strict static final String F1__STRICT = "hello";
        @Strict static final int    F2__STRICT = 42;
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Anulls_OK {
        @Strict static String F1__STRICT = null;
        @Strict static int    F2__STRICT = 0;
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Arepeat_OK3 {
        @Strict static String F1__STRICT = "hello";
        @Strict static int    F2__STRICT = 42;
        static {
            System.out.print("(making second putstatic)");
            F2__STRICT = 43;
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Aupdate_OK23 {
        @Strict static String F1__STRICT = "hello";
        @Strict static int    F2__STRICT = 42;
        static {
            System.out.print("(making getstatic and second putstatic)");
            F2__STRICT++;
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Bnoinit_BAD {
        @Strict static String F1__STRICT;
        @Strict static int    F2__STRICT;
        static {
            if (false) {
                F1__STRICT = "hello";
                F2__STRICT = 42;
            }
            //FAIL
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Brbefore_BAD {
        @Strict static String F1__STRICT;
        @Strict static int    F2__STRICT;
        static {
            int x = F2__STRICT; //FAIL
            F1__STRICT = "hello";
            F2__STRICT = 42;
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Cwreflective_OK {
        @Strict static String F1__STRICT;
        @Strict static int    F2__STRICT;
        static {
            Field FIELD_F1 = findField(Cwreflective_OK.class, "F1__STRICT");
            Field FIELD_F2 = findField(Cwreflective_OK.class, "F2__STRICT");
            if (STRICTS_ARE_FINALS) {
                // reflective setting of larval static finals not implemented
                F1__STRICT = "hello";
                F2__STRICT = 42;
            } else {
                putstaticReflective(FIELD_F1, "hello");
                putstaticReflective(FIELD_F2, 42);
            }
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    class Creflbefore_BAD {
        @Strict static String F1__STRICT;
        @Strict static int    F2__STRICT;
        static {
            Field FIELD_F1 = findField(Creflbefore_BAD.class, "F1__STRICT");
            Field FIELD_F2 = findField(Creflbefore_BAD.class, "F2__STRICT");
            int x = (int) getstaticReflective(FIELD_F2);  //FAIL
            System.out.print("(early read of F2="+x+")");
            if (STRICTS_ARE_FINALS) {
                // reflective setting of larval static finals not implemented
                F1__STRICT = "hello";
                F2__STRICT = 42;
            } else {
                putstaticReflective(FIELD_F1, "hello");
                putstaticReflective(FIELD_F2, 42);
            }
        }
        public String toString() { return displayString(this, F1__STRICT, F2__STRICT); }
    }
    static Class<?>[] TEST_CLASSES = {
        Aregular_OK.class,
        Anulls_OK.class,
        Arepeat_OK3.class,
        Aupdate_OK23.class,
        Bnoinit_BAD.class,
        Brbefore_BAD.class,
        Cwreflective_OK.class,
        Creflbefore_BAD.class,
    };
    static String displayString(Object x, Object f1, Object f2) {
        return (displayName(x.getClass()) + "(" + f1 + "," + f2 + ")");
    }
    static String displayName(Class<?> cls) {
        var n = cls.getSimpleName();
        int dl = n.indexOf('$');
        if (dl > 0)  n = n.substring(dl+1);
        int sl = n.indexOf('/');
        if (sl > 0) n = n.substring(0, sl+1);
        return n;
    }
    static Field findField(Class<?>cls, String name) {
        return noroe(()-> {
                var f = cls.getDeclaredField(name);
                if (!Modifier.isStatic(f.getModifiers()) ||
                    !Modifier.isStrict(f.getModifiers()) ||
                    (STRICTS_ARE_FINALS &&
                     !Modifier.isFinal(f.getModifiers())))
                    throw new AssertionError("not strict static: "+f);
                if (Modifier.isFinal(f.getModifiers())) {
                    f.setAccessible(true);
                }
                return f;
            });
    }
    static Object getstaticReflective(Field f) {
        return noroe(()-> f.get(null) );
    }
    static void putstaticReflective(Field f, Object x) {
        noroe(()->{ f.set(null, x); return null; });
    }
    interface NoROE<T> { T get() throws ReflectiveOperationException; }
    static <T> T noroe(NoROE<T> fn) {
        try {
            return fn.get();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
    static void tryClass(Class<?> cls) {
        String cn = displayName(cls);
        System.out.print(cn + ": ");
        try {
            @SuppressWarnings("deprecation")
            Object obj = cls.newInstance();
            System.out.println(obj);
            if (!cn.contains("_OK")) {
                System.out.printf("FAILED: bad class %s did NOT throw\n", cn);
                FAILS[0]++;
            }
        } catch (Throwable ex) {
            if (ex instanceof ExceptionInInitializerError && ex.getCause() != null)
                ex = ex.getCause();
            if (!(cn.contains("_BAD") || (MODE != 1 && cn.contains(MODE+"")))) {
                System.out.printf("FAILED: ok class %s threw\n", cn);
                FAILS[1]++;
            } else if (!(ex instanceof IllegalStateException)) {
                System.out.printf("FAILED: bad class %s threw wrong exception\n", cn);
                FAILS[2]++;                FAILS[1]++;
            } else if (!VERBOSE) {
                var msg = ex.getMessage();
                int clip = msg.indexOf("HelloStrict$");
                if (clip > 0)  msg = msg.substring(0, clip) + "...";
                var modemsg = (cn.contains("_BAD") ? "" : "(MODE="+MODE+") ");
                System.out.println("ok: "+modemsg+"threw ISE: "+msg);
                return;
            }
            ex.printStackTrace();
        }
    }
    int[] FAILS = new int[3];
    boolean HIDDEN_ONLY = true;
    boolean VERBOSE = Boolean.getBoolean("VERBOSE");
    int MODE = Integer.getInteger("MODE", 1);
    boolean STRICTS_ARE_FINALS = Boolean.getBoolean("STRICTS_ARE_FINALS");
    static void tryClass(Class<?> cls, boolean tryHidden) {
        if (!(HIDDEN_ONLY && tryHidden)){
            tryClass(cls);
        }
        if (tryHidden) {
            Class<?> scls = null;
            try {
                scls = strictify(cls);
            } catch (Exception ex) {
                System.out.println("strictify " + cls);
                ex.printStackTrace();
            }
            if (scls != null)  tryClass(scls);
        }
    }
    static void main(String... av) {
        System.out.println("VERBOSE="+VERBOSE+
                           " HIDDEN_ONLY="+HIDDEN_ONLY+
                           " STRICTS_ARE_FINALS="+STRICTS_ARE_FINALS);
        for (var cls : TEST_CLASSES) {
            tryClass(cls, true);
        }
        int fails = 0;
        for (int f : FAILS)  fails += f;
        if (fails != 0) {
            var msg = String.format("FAILED: %d failures [ok,unset,rbw]=%s",
                                    fails, java.util.Arrays.toString(FAILS));
            throw new AssertionError(msg);
        }
        /*
          Expected output (MODE=1):
          ---
          Aregular_OK/: Aregular_OK/(hello,42)
          Anulls_OK/: Anulls_OK/(null,0)
          Arepeat_OK3/: (making second putstatic)Arepeat_OK3/(hello,43)
          Aupdate_OK23/: (making getstatic and second putstatic)Aupdate_OK23/(hello,43)
          Bnoinit_BAD/: ok: threw ISE: Strict static "F1__STRICT" is unset after initialization of ...
          Brbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          Cwreflective_OK/: Cwreflective_OK/(hello,42)
          Creflbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          ---

          Expected output (MODE=2, STRICTS_ARE_FINALS=true):
          ---
          Aregular_OK/: Aregular_OK/(hello,42)
          Anulls_OK/: Anulls_OK/(null,0)
          Arepeat_OK3/: (making second putstatic)Arepeat_OK3/(hello,43)
          Aupdate_OK23/: (making getstatic and second putstatic)ok: (MODE=2) threw ISE: Strict static "F2__STRICT" is set after read (as final) in ...
          Bnoinit_BAD/: ok: threw ISE: Strict static "F1__STRICT" is unset after initialization of ...
          Brbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          Cwreflective_OK/: Cwreflective_OK/(hello,42)
          Creflbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          ---

          Expected output (MODE=3, STRICTS_ARE_FINALS=true):
          ---
          Aregular_OK/: Aregular_OK/(hello,42)
          Anulls_OK/: Anulls_OK/(null,0)
          Arepeat_OK3/: (making second putstatic)ok: (MODE=3) threw ISE: Strict static "F2__STRICT" is set twice (as final) in ...
          Aupdate_OK23/: (making getstatic and second putstatic)ok: (MODE=3) threw ISE: Strict static "F2__STRICT" is set twice (as final) in ...
          Bnoinit_BAD/: ok: threw ISE: Strict static "F1__STRICT" is unset after initialization of ...
          Brbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          Cwreflective_OK/: Cwreflective_OK/(hello,42)
          Creflbefore_BAD/: ok: threw ISE: Strict static "F2__STRICT" is unset before first read in ...
          ---
         */
    }

    // Classfile transform to inject ACC_STRICT on fields named foo__STRICT.
    // This temporary trick avoids dependencies on toolchains.
    static Class<?> strictify(Class<?> c) throws Exception {
        var fullName = c.getName();
        var lastDot = fullName.lastIndexOf('.');
        var uri = c.getResource(fullName.substring(Math.min(lastDot, -1) + 1) + ".class").toURI();
        ClassModel model = null;
        if (uri.getScheme().equals("jar")) {
            var parts = uri.toString().split("!");
            if (parts.length == 2) {
                try (var fs = java.nio.file.FileSystems.
                     newFileSystem(java.net.URI.create(parts[0]), new java.util.HashMap<>())) {
                    model = ClassFile.of().parse(fs.getPath(parts[1]));
                }
            }
        }
        if (model == null)  model = ClassFile.of().parse(java.nio.file.Paths.get(uri));
        FieldTransform addStrict = (b, e) -> {
            if (e instanceof AccessFlags af)
                b.withFlags(af.flagsMask() | Modifier.STRICT
                            | (STRICTS_ARE_FINALS ? Modifier.FINAL : 0));
            else b.with(e);
        };
        ClassTransform addStrictToFields = (b, e) -> {
            if (e instanceof FieldModel fm &&
                fm.fieldName().stringValue().endsWith("__STRICT"))
                b.transformField(fm, addStrict);
            else if (e instanceof Attribute a &&
                     a.attributeName().equalsString("InnerClasses"))
                { }
            else b.with(e);
        };
        var classBytes = ClassFile.of().transformClass(model, addStrictToFields);
        var vererrs = ClassFile.of().verify(classBytes);
        if (vererrs != null && !vererrs.isEmpty()) {
            System.out.println(vererrs);
            var cm = ClassFile.of().parse(classBytes);
            System.out.println("" + cm + cm.fields() + cm.methods() + cm.methods().get(0).code().orElseThrow().elementList());
        }
        try {
            return MethodHandles.lookup().defineHiddenClass(classBytes, false).lookupClass();
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }
}
