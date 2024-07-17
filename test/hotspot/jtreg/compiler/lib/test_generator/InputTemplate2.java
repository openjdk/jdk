package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputTemplate2 extends InputTemplate {
    @Override
    public CodeSegment getTemplate() {
        Template3 template1 = new Template3();
        Template4 template2 = new Template4();
        String template_nes1= template1.getTemplate(List.of("i"));
        String template_nes2= template2.getTemplate(List.of("j"));
        String imports= """
                """;

        String statics = """
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
        Map<String, String> replacement = Map.ofEntries(
                Map.entry("template1",template_nes1 ),
                Map.entry("template2",template_nes2 ));
        method=doReplacements(method,replacement);

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Map<String, String> replacements = new HashMap<>();

        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String init3 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String limit3 = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        //String arithm = getRandomValue(new String[]{"false", "true"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        //String uniqueId = getUniqueId();
        String uniqueId = String.valueOf(numTest);

        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("init3", init3);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("limit3", limit3);
        //replacements.put("arithm", arithm);
        //replacements.put("stride", stride);
        //replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }
    @Override
    public int getNumberOfTests(){
        return 4;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 4;
    }
}
