package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate2 extends InputTemplate {
    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;
        String statics = """
                //InputTemplate2
                static int count = 0;
                static int acc = 1;
                """;
        String call = "count_\\{uniqueId}();\n";
        String method = """
                 private static void count_\\{uniqueId}() {
                         boolean cond = false;
                         for (int i = \\{init1}; i < \\{limit1}; i++) {
                             switch (i % 3) {
                                 case 0:
                                     System.out.println("count: " + count);
                                     for (int j = \\{init2}; j < \\{limit2}; j++) {
                                         for (int k = \\{init3}; k < \\{init3}; k++) {
                                             count = acc;
                                         }
                                         if (cond) {
                                             break;
                                         }
                                     }
                                     acc++;
                                     break;
                                 case 1:
                                     \\{template1}
                                     break;
                                 case 2:
                                     \\{template2}
                                     break;
                             }
                         }
                 }
                """;
        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template3();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("i");
        String template_nes2= template2.getTemplate("j");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String init3 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit3 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("init3", init3);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("limit3", limit3);
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
    public int getNumberOfTests(){
        return 1;
    }
    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }
}
