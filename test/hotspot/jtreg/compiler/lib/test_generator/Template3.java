package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template3 extends Template {
    public Template3() {}
    public String getTemplate(String variable){
        String statics= """
                int $val = \\{val};
                """;
        String method="""
            for (int $i = \\{var}; $i < \\{limit}; $i++) {
                    if (($i % 2) == 0) {
                        $val = \\{val1};
                    }
                }
                // val == 1, known after second loop opts round
                // it takes two rounds of unrolling (2x, 4x)
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);

        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val = getRandomValueAsString(INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        replacements.put("val1", val1);
        replacements.put("val", val);
        replacements.put("var", variable);
        replacements.put("limit", limit);

        return performReplacements(template_com,replacements);
    }
}
