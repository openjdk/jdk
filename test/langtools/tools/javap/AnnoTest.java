/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8156694
 * @summary javap should render annotations in a friendly way
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.*;
import java.lang.annotation.*;
import javax.lang.model.element.ElementKind;

public class AnnoTest {
    public static void main(String... args) throws Exception {
        new AnnoTest().run();
    }

    void run() throws Exception {
        String testClasses = System.getProperty("test.classes");
        String out = javap("-v", "-classpath", testClasses, A.class.getName());

        String nl = System.getProperty("line.separator");
        out = out.replaceAll(nl, "\n");

        if (out.contains("\n\n\n"))
            error("double blank line found");

        expect(out,
                "RuntimeVisibleAnnotations:\n" +
                "  0: #18(#19=B#20)\n" +
                "    AnnoTest$ByteAnno(\n" +
                "      value=(byte) 42\n" +
                "    )\n" +
                "  1: #23(#19=S#24)\n" +
                "    AnnoTest$ShortAnno(\n" +
                "      value=(short) 3\n" +
                "    )");
        expect(out,
                "RuntimeInvisibleAnnotations:\n" +
                "  0: #28(#19=[J#29,J#31,J#33,J#35,J#37])\n" +
                "    AnnoTest$ArrayAnno(\n" +
                "      value=[1l,2l,3l,4l,5l]\n" +
                "    )\n" +
                "  1: #41(#19=Z#42)\n" +
                "    AnnoTest$BooleanAnno(\n" +
                "      value=false\n" +
                "    )\n" +
                "  2: #45(#46=c#47)\n" +
                "    AnnoTest$ClassAnno(\n" +
                "      type=class Ljava/lang/Object;\n" +
                "    )\n" +
                "  3: #50(#51=e#52.#53)\n" +
                "    AnnoTest$EnumAnno(\n" +
                "      kind=Ljavax/lang/model/element/ElementKind;.PACKAGE\n" +
                "    )\n" +
                "  4: #56(#19=I#57)\n" +
                "    AnnoTest$IntAnno(\n" +
                "      value=2\n" +
                "    )\n" +
                "  5: #60()\n" +
                "    AnnoTest$IntDefaultAnno\n" +
                "  6: #63(#64=s#65)\n" +
                "    AnnoTest$NameAnno(\n" +
                "      name=\"NAME\"\n" +
                "    )\n" +
                "  7: #68(#69=D#70,#72=F#73)\n" +
                "    AnnoTest$MultiAnno(\n" +
                "      d=3.14159d\n" +
                "      f=2.71828f\n" +
                "    )\n" +
                "  8: #76()\n" +
                "    AnnoTest$SimpleAnno\n" +
                "  9: #79(#19=@#56(#19=I#80))\n" +
                "    AnnoTest$AnnoAnno(\n" +
                "      value=@AnnoTest$IntAnno(\n" +
                "        value=5\n" +
                "      )\n" +
                "    )");
        expect(out,
                "RuntimeInvisibleTypeAnnotations:\n" +
                "  0: #84(): CLASS_EXTENDS, type_index=0\n" +
                "    AnnoTest$TypeAnno");

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    String javap(String... args) throws Exception {
        StringWriter sw = new StringWriter();
        int rc;
        try (PrintWriter out = new PrintWriter(sw)) {
            rc = com.sun.tools.javap.Main.run(args, out);
        }
        System.out.println(sw.toString());
        if (rc < 0)
            throw new Exception("javap exited, rc=" + rc);
        return sw.toString();
    }

    void expect(String text, String expect) {
        if (!text.contains(expect))
            error("expected text not found");
    }

    void error(String msg) {
        System.out.println("Error: " + msg);
        errors++;
    }

    int errors;

    /* Simple test classes to run through javap. */
    public @interface SimpleAnno { }
    public @interface BooleanAnno { boolean value(); }
    public @interface IntAnno { int value(); }
    public @interface IntDefaultAnno { int value() default 3; }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ByteAnno { byte value(); }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ShortAnno { short value(); }

    public @interface NameAnno { String name(); }
    public @interface ArrayAnno { long[] value(); }
    public @interface EnumAnno { ElementKind kind(); }
    public @interface ClassAnno { Class<?> type(); }
    public @interface MultiAnno { float f(); double d(); }

    public @interface AnnoAnno { IntAnno value(); }

    @Target(ElementType.TYPE_USE)
    public @interface TypeAnno { }

    @ArrayAnno({1, 2, 3, 4, 5})
    @BooleanAnno(false)
    @ByteAnno(42)
    @ClassAnno(type = Object.class)
    @EnumAnno(kind = ElementKind.PACKAGE)
    @IntAnno(2)
    @IntDefaultAnno
    @NameAnno(name = "NAME")
    @MultiAnno(d = 3.14159, f = 2.71828f)
    @ShortAnno(3)
    @SimpleAnno
    @AnnoAnno(@IntAnno(5))
    public abstract class A implements @TypeAnno Runnable { }
}
