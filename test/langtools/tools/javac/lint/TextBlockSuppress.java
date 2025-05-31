/*
 * @test /nodynamiccopyright/
 * @bug 8224228
 * @summary Verify SuppressWarnings works for LintCategore.TEXT_BLOCKS
 * @compile/fail/ref=TextBlockSuppress.out -XDrawDiagnostics -Xlint:text-blocks -Werror TextBlockSuppress.java
 */

public class TextBlockSuppress {

    public static class Example1 {
        public void method() {
            String s = """
                trailing space here:\u0020
                """;        // SHOULD get a warning here
        }
    }

    @SuppressWarnings("text-blocks")
    public static class Example2 {
        public void method() {
            String s = """
                trailing space here:\u0020
                """;        // SHOULD NOT get a warning here
        }
    }

    public static class Example3 {
        @SuppressWarnings("text-blocks")
        public void method() {
            String s = """
                trailing space here:\u0020
                """;        // SHOULD NOT get a warning here
        }
    }

    public static class Example4 {
        {
            String s = """
                trailing space here:\u0020
                """;        // SHOULD get a warning here
        }
    }

    @SuppressWarnings("text-blocks")
    public static class Example5 {
        {
            String s = """
                trailing space here:\u0020
                """;        // SHOULD NOT get a warning here
        }
    }

    public static class Example6 {
        public void method() {
            @SuppressWarnings("text-blocks")
            String s = """
                trailing space here:\u0020
                """;        // SHOULD NOT get a warning here
        }
    }
}
