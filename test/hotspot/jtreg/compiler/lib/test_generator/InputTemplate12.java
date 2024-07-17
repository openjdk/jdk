package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate12 extends InputTemplate {
    public InputTemplate12() {}

    @Override
    public CodeSegment getTemplate() {
        String template_nes = """
            int a1 = 77;
            int b1 = 0;
            do {
                a1--;
                b1++;
            } while (a1 > 0);
            """;
        String imports= """
                """;

        String statics = """
                static boolean flag;
                static long lFld;
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 static void test() {
                     long l1 = \\{val1};
                     long l2 = \\{val2};
                     int zero = \\{val3};
                     int limit = \\{limit};
                     for (; limit < \\{limit1}; limit \\{arithm}= \\{stride});
                     for (int i = \\{init1}; i < limit; i++) {
                         zero = 0;
                     }
                     for (int i = \\{init2}; i < \\{limit2}; i++) {
                         iArr[i] = \\{val1}; // Just a reason to pre/main/post (trigger more loop opts)
                         if (flag) { // Triggers Loop Peeling before CCP
                             return;
                         }
                         if (zero > i) { // Folded away after CCP.
                             // DivLs add 30 to the loop body count and we hit LoopUnrollLimit. Add more
                             // statements/DivLs if you want to use a higher LoopUnrollLimit value.
                             // After CCP, these statements are folded away and we can unroll this loop.
                             l1 /= lFld;
                             l2 /= lFld;
                         }
                     }
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Map<String, String> replacements = new HashMap<>();

        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"+", "-"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit", limit);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
       // replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{"-Xcomp",
                "-XX:CompileOnly=Test::test*"};
    }

    @Override
    public int getNumberOfTests() {
        return 10;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 4;
    }
}
