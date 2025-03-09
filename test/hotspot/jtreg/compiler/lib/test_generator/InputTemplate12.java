package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate12 extends InputTemplate {
    public InputTemplate12() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate12
                static boolean flag;
                static long lFld;
                static int []iArr=new int[\\{size}];
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 static void test_\\{uniqueId}() {
                     long l1 = \\{val1};
                     long l2 = \\{val2};
                     int zero = \\{val3};
                     int limit = \\{limit};
                     for (; limit < \\{limit1}; limit \\{arithm}= \\{stride});
                     for (int i = \\{init1}; i < limit; i++) {
                         zero = 0;
                     }
                     for (int i = \\{init2}; i < \\{size}; i++) {
                         iArr[i] = \\{val1}; // Just a reason to pre/main/post (trigger more loop opts)
                         if (flag) { // Triggers Loop Peeling before CCP
                             \\{template1}
                         }
                         if (zero > i) { // Folded away after CCP.
                             // DivLs add 30 to the loop body count and we hit LoopUnrollLimit. Add more
                             // statements/DivLs if you want to use a higher LoopUnrollLimit value.
                             // After CCP, these statements are folded away and we can unroll this loop.
                             l1 /= lFld;
                             l2 /= lFld;
                             \\{template2}
                         }
                     }
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }
    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template1();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("l1");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("size", size);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit", limit);
        replacements.put("limit1", limit1);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{"-Xcomp",
                "-XX:CompileOnly=Test::test*",
                };
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
