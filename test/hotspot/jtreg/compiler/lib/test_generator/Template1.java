package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static compiler.lib.test_generator.InputTemplate.*;
public class Template1 extends Template {
    public String getTemplate(List<String> values){
        String statics= """
                int $a =\\{val};
                long $b = \\{var};
                """;
        String nes_template="""
            do {
                $a--;
                $b++;
            } while ($a > 0);
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),1);
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
        String val = getRandomValueAsString(integerValues);
        replacements.put("val", val);
        replacements.put("var", values.get(0));
        // replacements.put("var1", values.get(1));

        return doReplacements(template_com,replacements);
    }
}
