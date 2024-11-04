package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate10 extends InputTemplate {
    public InputTemplate10() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;
        String statics= """
                //InputTemplate10
                public static int iFld = \\{val};
                """;
        String call= """
                for (int i = \\{init1}; i < \\{limit1}; i++) {
                    test_\\{uniqueId}(i % 2 == 0);
                }
                """;
        String method= """
                public static void test_\\{uniqueId}(boolean flag) {
                    for (int i = \\{init2}; i < \\{limit2}; i++)  {
                        // Fast loop: If is true -> iFld++
                        // Slow loop: If is false -> no update of iFld
                        if (flag) {
                            iFld++;
                            \\{template1}
                        }
                        \\{template2}
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
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("val", val);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
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
