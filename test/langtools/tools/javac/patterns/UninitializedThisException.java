/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8332106
 * @summary Verify the synthetic catch clauses are generated correctly for constructors
 * @enablePreview
 * @compile UninitializedThisException.java
 * @run main UninitializedThisException
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.Supplier;

public class UninitializedThisException extends Base {

    public UninitializedThisException(String s1, String s2) {
        super(s1, s2);
    }

    public UninitializedThisException(R o1, R o2, R o3) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-nest(" + o2.fail() + ")" +
                    "-post(" + o3.fail() + ")");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        this(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, R o2, R o3) {
        out.println("-nest(" + o2.fail() + ")" +
                    "-post(" + o3.fail() + ")");
        String val1 = o1;
        out.println("check1");
        this(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, String o2, R o3) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-post(" + o3.fail() + ")");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        this(val1, o2);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, R o2, String o3) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-nest(" + o2.fail() + ")");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        this(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, String o2, String o3) {
        out.println("-pre(" + o1.fail() + ")");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        this(val1, o2);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, R o2, String o3) {
        out.println("-nest(" + o2.fail() + ")");
        String val1 = o1;
        out.println("check1");
        this(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, String o2, R o3) {
        out.println("-post(" + o3.fail() + ")");
        String val1 = o1;
        out.println("check1");
        this(val1, o2);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, R o2, R o3, boolean superMarker) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-nest(" + o2.fail() + ")" +
                    "-post(" + o3.fail() + ")" +
                    "-super");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        super(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, R o2, R o3, boolean superMarker) {
        out.println("-nest(" + o2.fail() + ")" +
                    "-post(" + o3.fail() + ")" +
                    "-super");
        String val1 = o1;
        out.println("check1");
        super(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, String o2, R o3, boolean superMarker) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-post(" + o3.fail() + ")" +
                    "-super");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        super(val1, o2);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, R o2, String o3, boolean superMarker) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-nest(" + o2.fail() + ")" +
                    "-super");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        super(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(R o1, String o2, String o3, boolean superMarker) {
        out.println("-pre(" + o1.fail() + ")" +
                    "-super");
        String val1 = o1 instanceof R(String s, _) ? s : null;
        out.println("check1");
        super(val1, o2);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, R o2, String o3, boolean superMarker) {
        out.println("-nest(" + o2.fail() + ")" +
                    "-super");
        String val1 = o1;
        out.println("check1");
        super(val1, o2 instanceof R(String s, _) ? s : null);
        out.println("check2");
        String val2 = o3;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public UninitializedThisException(String o1, String o2, R o3, boolean superMarker) {
        out.println("-post(" + o3.fail() + ")" +
                    "-super");
        String val1 = o1;
        out.println("check1");
        super(val1, o2);
        out.println("check2");
        String val2 = o3 instanceof R(String s, _) ? s : null;
        out.println("check3");
        Objects.requireNonNull(val2);
    }

    public static void main(String... args) {
        runAndCatch(() -> new UninitializedThisException(new R("", true), new R("", false), new R("", false)));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", true), new R("", false)));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", false), new R("", true)));
        new UninitializedThisException(new R("", false), new R("", false), new R("", false));

        out.println();

        runAndCatch(() -> new UninitializedThisException("", new R("", true), new R("", false)));
        runAndCatch(() -> new UninitializedThisException("", new R("", false), new R("", true)));
        new UninitializedThisException("", new R("", false), new R("", false));

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), "", new R("", false)));
        runAndCatch(() -> new UninitializedThisException(new R("", false), "", new R("", true)));
        new UninitializedThisException(new R("", false), "", new R("", false));

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), new R("", false), ""));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", true), ""));
        new UninitializedThisException(new R("", false), new R("", false), "");

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), "", ""));
        new UninitializedThisException(new R("", false), "", "");

        out.println();

        runAndCatch(() -> new UninitializedThisException("", new R("", true), ""));
        new UninitializedThisException("", new R("", false), "");

        out.println();

        runAndCatch(() -> new UninitializedThisException("", "", new R("", true)));
        new UninitializedThisException("", "", new R("", false));

        runAndCatch(() -> new UninitializedThisException(new R("", true), new R("", false), new R("", false), true));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", true), new R("", false), true));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", false), new R("", true), true));
        new UninitializedThisException(new R("", false), new R("", false), new R("", false), true);

        out.println();

        runAndCatch(() -> new UninitializedThisException("", new R("", true), new R("", false), true));
        runAndCatch(() -> new UninitializedThisException("", new R("", false), new R("", true), true));
        new UninitializedThisException("", new R("", false), new R("", false), true);

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), "", new R("", false), true));
        runAndCatch(() -> new UninitializedThisException(new R("", false), "", new R("", true), true));
        new UninitializedThisException(new R("", false), "", new R("", false), true);

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), new R("", false), "", true));
        runAndCatch(() -> new UninitializedThisException(new R("", false), new R("", true), "", true));
        new UninitializedThisException(new R("", false), new R("", false), "", true);

        out.println();

        runAndCatch(() -> new UninitializedThisException(new R("", true), "", "", true));
        new UninitializedThisException(new R("", false), "", "", true);

        out.println();

        runAndCatch(() -> new UninitializedThisException("", new R("", true), "", true));
        new UninitializedThisException("", new R("", false), "", true);

        out.println();

        runAndCatch(() -> new UninitializedThisException("", "", new R("", true), true));
        new UninitializedThisException("", "", new R("", false), true);

        String actualLog = log.toString().replaceAll("\\R", "\n");
        String expectedLog = EXPECTED_LOG_PATTERN.replace("${super}", "") +
                             EXPECTED_LOG_PATTERN.replace("${super}", "-super");

        if (!Objects.equals(actualLog, expectedLog)) {
            throw new AssertionError("Expected log:\n" + expectedLog +
                                     ", but got: " + actualLog);
        }
    }

    static final String EXPECTED_LOG_PATTERN =
            """
            -pre(true)-nest(false)-post(false)${super}
            -pre(false)-nest(true)-post(false)${super}
            check1
            -pre(false)-nest(false)-post(true)${super}
            check1
            check2
            -pre(false)-nest(false)-post(false)${super}
            check1
            check2
            check3

            -nest(true)-post(false)${super}
            check1
            -nest(false)-post(true)${super}
            check1
            check2
            -nest(false)-post(false)${super}
            check1
            check2
            check3

            -pre(true)-post(false)${super}
            -pre(false)-post(true)${super}
            check1
            check2
            -pre(false)-post(false)${super}
            check1
            check2
            check3

            -pre(true)-nest(false)${super}
            -pre(false)-nest(true)${super}
            check1
            -pre(false)-nest(false)${super}
            check1
            check2
            check3

            -pre(true)${super}
            -pre(false)${super}
            check1
            check2
            check3

            -nest(true)${super}
            check1
            -nest(false)${super}
            check1
            check2
            check3

            -post(true)${super}
            check1
            check2
            -post(false)${super}
            check1
            check2
            check3
            """;

    static final StringWriter log = new StringWriter();
    static final PrintWriter out = new PrintWriter(log);

    static void runAndCatch(Supplier<Object> toRun) {
        try {
            toRun.get();
            throw new AssertionError("Didn't get the expected exception!");
        } catch (MatchException ex) {
            //OK
        }
    }
    record R(String s, boolean fail) {
        public String s() {
            if (fail) {
                throw new NullPointerException();
            } else {
                return s;
            }
        }
    }
}
class Base {
    public Base(String s1, String s2) {
        Objects.requireNonNull(s1);
        Objects.requireNonNull(s2);
    }
}
