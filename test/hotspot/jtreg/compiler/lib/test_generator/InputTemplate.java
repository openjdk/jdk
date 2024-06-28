package compiler.lib.test_generator;

import java.math.BigInteger;
import java.util.Map;

public class InputTemplate {
    private final BigInteger num;
    private final int init;
    private final int limit;
    private final int stride;
    private final String arithm;

    public InputTemplate(BigInteger num, @TemplateGenerator.IntParam int init, @TemplateGenerator.IntParam int limit, @TemplateGenerator.IntParam(nonZero = true) int stride, @TemplateGenerator.StringParam(values = {"+", "-"}) String arithm) {
        this.num = num;
        this.init = init;
        this.limit = limit;
        this.stride = stride;
        this.arithm = arithm;
    }

    String thing = "synchronized (new Object()) { }\n";

    private CodeSegments getTemplate() {
        Result res = new Result();
        res.statics = """
                static long lFld;
                static A a = new A();
                static boolean flag;
                static class A {
                    int i;
                }
                """;

        res.call = "test\\{num}();\n";
        res.call = TemplateGenerator.fillTemplate(res.call, Map.of(
                "num", num.toString()
        ));
        res.method = """
                 public static void test\\{num}() {
                     long limit = lFld;
                     for (int i =\\{init}; i < \\{limit}; i \\{arithm}= \\{stride}) {
                         // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
                         for (long j = 0; j < limit; j+=2147483648L) {
                             a.i += 34; // NullCheck with a trap on the false path as a reason to peel
                             \\{thing}
                             if (j > 0) { // After peeling: condition always true, loop is folded away.
                                 break;
                             }
                         }
                     }
                 }
                """;
        res.method = TemplateGenerator.fillTemplate(res.method, getRandomReplacements());
        return new CodeSegments(res.statics, res.call, res.method, num.intValue());
    }
    //private String getJavaCode() {}
    private Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = Map.ofEntries(
                Map.entry("num", num.toString()),
                Map.entry("init", Integer.toString(init)),
                Map.entry("limit", Integer.toString(limit)),
                Map.entry("arithm", arithm),
                Map.entry("stride", Integer.toString(stride)),
                Map.entry("thing", thing)
        );
        return replacements;
    }





}
