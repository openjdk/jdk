package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.Map;

public class InputTemplate3 extends InputTemplate {
    public InputTemplate3() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate3
                static int iArrFld[]=new int[\\{size}];
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

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template5();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("i19");
        String template_nes2= template2.getTemplate("i16");
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String init = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("val1", val1);
        replacements.put("init", init);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("size", size);
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
    public int getNumberOfTests(){
        return 1;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }

}
