package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate3 extends InputTemplate {
    public InputTemplate3() {}

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
                static int iArrFld[];
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                 public static void test_\\{uniqueId}() {
                     int i16 = \\{val1}, i19 = \\{val2}, i20 = \\{val3};
                         do {
                           for (; i19 < \\{limit1}; i19++) {
                             i20 = 0;
                             try {
                               i20 = iArrFld[i19 - 1];
                             } catch (ArithmeticException a_e) {
                             }
                           }
                           i16++;
                         }
                         while (i16 < \\{limit2});
                         return i20;
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = new HashMap<>();

        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        //String arithm = getRandomValue(new String[]{"+", "-"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

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
        return 10;
    }

}
