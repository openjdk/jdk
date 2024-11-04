package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate16 extends InputTemplate {
    public InputTemplate16() {}

    @Override
    public CodeSegment getTemplate() {

            String imports= """
                """;

        String statics = """
                //InputTemplate16
                static int zero = \\{val};
                static int limit = \\{limit};
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 static void test_\\{uniqueId}() {
                     for (; limit < \\{limit1}; limit \\{arithm}= \\{stride});
                     for (int i = \\{init}; i < limit; i++) {
                         zero = 0;
                     }
                     // After CCP: zero = 0. Use other "type delaying" template" here depending on when the counted loop should be created.
                     // Create counted loop from i = 0..20 with single loop exit condition (i < 20) after CCP:
                     int i = 0;
                     do {
                        \\{template}
                        i++;
                     } while (i < \\{val1} || zero == \\{val}); // i < 20 becomes only loop exit condition after CCP -> creates a counted loop in next loop opts round.
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template4();
        String template_nes1= template1.getTemplate("j");
        Map<String, String> replacements = new HashMap<>();

        String init = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(INTEGER_VALUES);
        String val = getRandomValueAsString(INTEGER_VALUES);
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"*", "/"});
       String uniqueId = String.valueOf(numTest);
        replacements.put("init", init);
        replacements.put("val1", val1);
        replacements.put("val", val);
        replacements.put("limit1", limit1);
        replacements.put("limit", limit);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("template", template_nes1);
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
        return 1;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }
}
