package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate8 extends InputTemplate {
    public InputTemplate8() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate8
                static boolean flag, flag2;
                static int iFld;
                """;
        String call = """
                for (int i = \\{init1}; i < \\{limit1}; i++) {
                    flag = i % 2 == 0;
                    flag2 = i % 3 == 0;
                    test_\\{uniqueId}();
                }
                """;

        String method = """
                 public static void test_\\{uniqueId}() {
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
                         \\{template1}
                     }
                     iFld = b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK
                     // --- BLOCK end ---
                     if (a > \\{val3}) { // If to split -> need to empty BLOCK
                         iFld = \\{val1};
                         \\{template2}
                     }
                     if (flag2) { // Reuse of Bool(CmpI(flag2)) such that we need to clone CmpI(flag2) down
                         iFld = \\{val2};
                     }
                 }
                """;
        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template1();
        Template template2 = new Template10();
        String template_nes1= template1.getTemplate("a");
        String template_nes2= template2.getTemplate("b");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"*", "/"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
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
        return 1;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }
}
