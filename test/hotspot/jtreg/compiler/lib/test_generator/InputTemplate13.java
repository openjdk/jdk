package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate13 extends InputTemplate {
    public InputTemplate13() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                class a {}
                """;

        String call = """
                MainClass_\\{uniqueId} l_\\{uniqueId} = new MainClass_\\{uniqueId}();
                for (int m = \\{init1};;){
                    l_\\{uniqueId}.c(m, m);
                }
                """;


        String method = """
            class  MainClass_\\{uniqueId} {
                int b;
                long[] c(int n, int d) {
                long[] e = new long[\\{size}];
                for (int f = \\{init1}; f < \\{limit1};) {
                    for (int g = \\{init2}; g < \\{limit2}; ++g) {
                        for (long h = \\{init3}; h <\\{limit3}; ++h);
                        n = b;
                        if (g != 1){
                            \\{template1}
                            
                        }else{
                            b = 0;
                            \\{template2}
                        }
                        for (int i = \\{init4}; i < \\{limit4}; ++i);
                        for (int j = \\{init5}; j < \\{limit5}; ++j) {
                            a[] k = {};
                        }
                    }
                }
                return e;
                }
            }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1=new Template1();
        Template template2=new Template4();
        String template_nes1=template1.getTemplate("g");
        String template_nes2=template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(integerValues);
        String init2= getRandomValueAsString(integerValues);
        String init3 = getRandomValueAsString(integerValues);
        String size = getRandomValueAsString(integerValues);
        String init4 = getRandomValueAsString(integerValues);
        String init5 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String limit3 = getRandomValueAsString(integerValues);
        String limit4 = getRandomValueAsString(integerValues);
        String limit5 = getRandomValueAsString(integerValues);
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("uniqueId", uniqueId);
        replacements.put("size", size);
        replacements.put("init3", init3);
        replacements.put("init4", init4);
        replacements.put("init5", init5);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("limit3", limit3);
        replacements.put("limit4", limit4);
        replacements.put("limit5", limit5);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        return replacements;
    }
    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }

    @Override
    public int getNumberOfTests() {
        return 10;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 100;
    }
}
