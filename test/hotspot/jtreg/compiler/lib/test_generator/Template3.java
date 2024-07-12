package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template3 extends Template {
    public Template3() {}
    public String getTemplate(List<String> values){
        String statics= """
                int $val = \\{val};
                """;
        String nes_template="""
                for (int $i = \\{init}; $i < \\{limit}; $i++) {
                        if (($i % 2) == 0) {
                            $val = \\{val1};
                        }
                    }
                    // val == 1, known after second loop opts round
                    // it takes two rounds of unrolling (2x, 4x)
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),2);
        /*StringBuilder res=new StringBuilder();
        int count=0;
        for (int i=0;i<template_com.length();i++) {
            char c=template_com.charAt(i);
            if (c=='%'&&count<values.size()) {
                res.append(values.get(count));
                count++;
            }
            else {
                res.append(c);
            }
        }
         */

        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(integerValues);
        String val = getRandomValueAsString(integerValues);
        String init = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(integerValues);
        replacements.put("val1", val1);
        replacements.put("val", val);
        replacements.put("init", init);
        replacements.put("limit", limit);

        return doReplacements(template_com,replacements);
    }
}
