package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template5 extends Template {
    public Template5() {}
    public String getTemplate(String variable){
        String statics= """
                class MyValue{
                    int nonNull;
                    public MyValue(int $nonNull) {}
                }
                MyValue $nonNull = new MyValue(\\{val1});
                MyValue $val = null;
                int $zero = \\{val2};

                """;
        String method="""
            int $limit = \\{val3};
            for (; $limit < 4; $limit *= 2);
            for (int $i = \\{var}; $i < $limit; $i++) {
                $val = $nonNull;
                $zero = 0;
            }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }
}
