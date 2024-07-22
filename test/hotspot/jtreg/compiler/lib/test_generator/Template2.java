package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template2 extends Template {
    public Template2() {}
    public String getTemplate(String c){
        String statics= """
                Integer $a;
                boolean $flag=\\{val};
                """;
        String nes_template="""
            if ($flag) {
                $a = 1;
            } else {
                $a = 2;
            }
            if ($a == 3) {
                // Not reachable but that's only known after Incremental Boxing Inline
            }
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),2);

        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValue(new String[]{"true", "false"});
        replacements.put("val", val);

        return doReplacements(template_com,replacements);
    }
}
