package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template2 extends Template {
    public Template2() {}
    public String getTemplate(String variable){
        String statics= """
                Integer $a;
                boolean $flag=\\{val};
                """;
        String method="""
            if ($flag) {
                $a = 1;
            } else {
                $a = 2;
            }
            if ($a == 3) {
                // Not reachable but that's only known after Incremental Boxing Inline
            }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);

        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValue(new String[]{"true", "false"});
        replacements.put("val", val);

        return performReplacements(template_com,replacements);
    }
}
