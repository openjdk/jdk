package compiler.lib.test_generator;

import java.math.BigInteger;
import java.util.Map;

import static compiler.lib.test_generator.TemplateGenerator.fillTemplate;

public class InputTemplate1 extends InputTemplate {
    public InputTemplate1(BigInteger num, @TemplateGenerator.IntParam int init, @TemplateGenerator.IntParam int limit, @TemplateGenerator.IntParam(nonZero = true) int stride, @TemplateGenerator.StringParam(values = {"+", "-"}) String arithm) {
        super(num,init,limit,stride,arithm);
    }
    static String thing = "synchronized (new Object()) { }\n";


    public static CodeSegment getTemplate() {
        Result res=new Result();
        res.statics = """
               static long lFld;
                static A a = new A();
                static boolean flag;
                static class A {
                    int i;
                }
                """;

        res.call = "test\\{num}();\n";

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

        Map<String, String> replacements = getRandomReplacements(num);
        res.call =fillTemplate(res.call, Map.of(
                "num", num.toString()));
        res.method = fillTemplate(res.method, replacements);

        return new CodeSegment(res.statics,res.call,res.method,num);// String with replacements or a class with statics, call and method as fields;

    }
    private static Map<String, String> getRandomReplacements(BigInteger num) {
        Map<String, String> replacement= Map.ofEntries(
                Map.entry("num", num.toString()),
                Map.entry("init", Integer.toString(init)),
                Map.entry("limit", Integer.toString(limit)),
                Map.entry("arithm", arithm),
                Map.entry("stride", Integer.toString(stride)),
                Map.entry("thing", thing)
        );
        return replacement;
    }
}
