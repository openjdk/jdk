package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate2 extends InputTemplate {
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
                                     break;
                                 case 2:
                                     break;
                                 }
                             }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
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
        String uniqueId = getUniqueId();

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
        return 5;
    }
}
