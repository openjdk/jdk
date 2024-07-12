package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template2 extends Template {
    public Template2() {}
    public String getTemplate(List<String> values){
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
        String val = getRandomValue(new String[]{"true", "false"});
        replacements.put("val", val);

        return doReplacements(template_com,replacements);
    }
}
