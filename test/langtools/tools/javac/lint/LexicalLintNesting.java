/*
 * @test /nodynamiccopyright/
 * @bug 8224228
 * @summary Verify lexical lint warnings handle nested declarations with SuppressWarnings correctly
 * @compile/fail/ref=LexicalLintNesting.out -XDrawDiagnostics -Xlint:text-blocks -Werror LexicalLintNesting.java
 */

//@SuppressWarnings("text-blocks")
public class LexicalLintNesting {

    //@SuppressWarnings("text-blocks")
    /* WARNING HERE */ String s1 = """
        trailing space here:\u0020
        """;

    @SuppressWarnings("text-blocks")
    String s2 = """
        trailing space here:\u0020
        """;

    //@SuppressWarnings("text-blocks")
    public static class Nested1 {

        @SuppressWarnings("text-blocks")
        String s3 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        /* WARNING HERE */ String s4 = """
            trailing space here:\u0020
            """;

        @SuppressWarnings("text-blocks")
        public static class Nested1A {

            //@SuppressWarnings("text-blocks")
            String s5 = """
                trailing space here:\u0020
                """;

            @SuppressWarnings("text-blocks")
            String s6 = """
                trailing space here:\u0020
                """;

        }

        @SuppressWarnings("text-blocks")
        String s7 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        /* WARNING HERE */ String s8 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        public static class Nested1B {

            @SuppressWarnings("text-blocks")
            String s9 = """
                trailing space here:\u0020
                """;

            //@SuppressWarnings("text-blocks")
            /* WARNING HERE */ String s10 = """
                trailing space here:\u0020
                """;

        }

        @SuppressWarnings("text-blocks")
        String s11 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        /* WARNING HERE */ String s12 = """
            trailing space here:\u0020
            """;

    }

    @SuppressWarnings("text-blocks")
    String s13 = """
        trailing space here:\u0020
        """;

    //@SuppressWarnings("text-blocks")
    /* WARNING HERE */ String s14 = """
        trailing space here:\u0020
        """;

    @SuppressWarnings("text-blocks")
    public static class Nested2 {

        @SuppressWarnings("text-blocks")
        String s15 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        String s16 = """
            trailing space here:\u0020
            """;

        @SuppressWarnings("text-blocks")
        public static class Nested2A {

            //@SuppressWarnings("text-blocks")
            String s17 = """
                trailing space here:\u0020
                """;

            @SuppressWarnings("text-blocks")
            String s18 = """
                trailing space here:\u0020
                """;        // SHOULD NOT get a warning here

        }

        @SuppressWarnings("text-blocks")
        String s19 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        String s20 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        public static class Nested2B {

            @SuppressWarnings("text-blocks")
            String s21 = """
                trailing space here:\u0020
                """;

            //@SuppressWarnings("text-blocks")
            String s22 = """
                trailing space here:\u0020
                """;

        }

        @SuppressWarnings("text-blocks")
        String s23 = """
            trailing space here:\u0020
            """;

        //@SuppressWarnings("text-blocks")
        String s24 = """
            trailing space here:\u0020
            """;

    }

    //@SuppressWarnings("text-blocks")
    /* WARNING HERE */ String s25 = """
        trailing space here:\u0020
        """;

    @SuppressWarnings("text-blocks")
    String s26 = """
        trailing space here:\u0020
        """;
}
