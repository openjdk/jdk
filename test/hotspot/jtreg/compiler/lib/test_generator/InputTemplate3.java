package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputTemplate3 extends InputTemplate {
    public InputTemplate3() {}

    @Override
    public CodeSegment getTemplate() {
        Template1 template1 = new Template1();
        Template4 template2 = new Template4();
        String template_nes1= template1.getTemplate(List.of("i19"));
        String template_nes2= template2.getTemplate(List.of("i16"));
        String imports= """
                """;

        String statics = """
                static int iArrFld[];
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 public static int test_\\{uniqueId}() {
                     int i16 = \\{val1}, i19 = \\{val2}, i20 = \\{val3};
                         do {
                           for (; i19 < \\{limit1}; i19++) {
                             i20 = 0;
                             \\{template1}
                             try {
                               i20 = iArrFld[i19 - 1];
                             } catch (ArithmeticException a_e) {
                             }
                             \\{template2}
                           }
                           i16++;
                         }
                         while (i16 < \\{limit2});
                         return i20;
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

        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        //String arithm = getRandomValue(new String[]{"+", "-"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        //String uniqueId = getUniqueId();
        String uniqueId = String.valueOf(numTest);

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit1);
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
