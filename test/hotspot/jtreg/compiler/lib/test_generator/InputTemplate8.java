package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate8 extends InputTemplate {
    public InputTemplate8() {}

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
                static boolean flag, flag2;
                static int iFld;
                """;

        String call = "for (int i = \\{init1}; i < \\{limit1}; i++) {\n" +
                "        flag = i % 2 == 0;\n" +
                "        flag2 = i % 3 == 0;\n" +
                "        test();\n" +
                "    }";

        String method = """
                 public static void test() {
                     int a;
                     int b;
                     for (int i = \\{init2}; i < \\{limit2}; i \\{arithm}= \\{stride}); // Make sure to run with loop opts.
                     if (flag) {
                         a = \\{val1};
                     } else {
                         a = \\{val2};
                     }
                     // Region
                     // --- BLOCK start ---
                     // CMoveI(Bool(CmpI(flag2))), a, 23)
                     if (flag2) {
                         b = \\{val1}; // Use b = a to have an additional Bool -> then Split If only clones down CmpI
                     } else {
                         b = \\{val3};
                     }
                     iFld = b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK
                     // --- BLOCK end ---
                     if (a > \\{val3}) { // If to split -> need to empty BLOCK
                         iFld = \\{val1};
                     }
                     if (flag2) { // Reuse of Bool(CmpI(flag2)) such that we need to clone CmpI(flag2) down
                         iFld = \\{val2};
                     }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Map<String, String> replacements = new HashMap<>();

        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"*", "/"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        //replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{"-XX:CompileOnly=Test::test*",
        "-Xbatch"};
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
