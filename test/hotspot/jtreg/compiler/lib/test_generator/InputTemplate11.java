package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate11 extends InputTemplate {
    public InputTemplate11() {}

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
                static int iFld, iFld2;
                static boolean flag;
                static int[] iArr = new int[\\{size}];
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 public static void test_\\{uniqueId}() {
                     int zero =\\{val};
                     int limit = \\{limit};
                     for (; limit < \\{limit1}; limit \\{arithm}= \\{stride});
                     for (int i = \\{init1}; i < limit; i++) {
                         zero = 0;
                     }
                     for (int i = \\{init2}; i < \\{limit2}; i++) {
                         if (flag) { // 1) Triggers Loop Peeling
                             return;
                         }
                         int k = iFld + i * zero; // Loop variant before CCP
                         if (k == \\{val}) { // 2) After CCP: Loop invariant -> triggers Loop Unswitching
                             iFld2 = \\{val};
                         } else {
                             iArr[i] = \\{val}; // 3) After Loop Unswitching: Triggers Pre/Main/Post and then 4) Range Check Elimination
                         }
                     }
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = new HashMap<>();

        String val = getRandomValueAsString(integerValues);
        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("val", val);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit", limit);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        //replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{"-Xcomp",
                "-XX:LoopMaxUnroll=0",
                "-XX:CompileOnly=Test::test*"};
    }

    @Override
    public int getNumberOfTests() {
        return 10;
    }
}
