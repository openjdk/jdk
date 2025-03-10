package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate15 extends InputTemplate {
    public InputTemplate15() {}

    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;

        String statics = """
                //InputTemplate15
                static int iFld, iFld2;
                static boolean flag;
                static int[] iArr = new int[\\{size}];
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 public static void test_\\{uniqueId}() {
                     int zero = \\{val};
                     int limit = \\{limit};
                     for (; limit < \\{limit1}; limit \\{arithm}= \\{stride});
                     for (int i = \\{init1}; i < limit; i++) {
                         zero = 0;
                     }
                     for (int i = 0; i < \\{limit2}; i++) {
                         if (flag) { // 1) Triggers Loop Peeling
                             \\{template1}
                         }
                         int k = iFld + i * zero; // Loop variant before CCP
                         if (k == \\{val}) { // 2) After CCP: Loop invariant -> triggers Loop Unswitching
                             iFld2 = \\{val};
                         } else {
                             iArr[i] = \\{val}; // 3) After Loop Unswitching: Triggers Pre/Main/Post and then 4) Range Check Elimination
                             \\{template2}
                         }
                     }
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template2();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("j");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValueAsString(INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String init1 = getRandomValueAsString(INTEGER_VALUES);

        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"*", "/"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("size", size);

        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("val", val);
        replacements.put("limit", limit);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{"-Xcomp","-XX:LoopMaxUnroll=0","-XX:CompileOnly=Test::test*"};
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
