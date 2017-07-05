/**
 * @test
 * @bug 6945564
 * @summary  Check that the j.l.Character.UnicodeScript
 * @ignore don't run until #6903266 is integrated
 */

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import java.lang.Character.UnicodeScript;

public class CheckScript {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("java CharacterScript script.txt");
            System.exit(1);
        }
        BufferedReader sbfr = new BufferedReader(new FileReader(args[0]));
        Matcher m = Pattern.compile("(\\p{XDigit}+)(?:\\.{2}(\\p{XDigit}+))?\\s+;\\s+(\\w+)\\s+#.*").matcher("");
        String line = null;
        HashMap<String,ArrayList<Integer>> scripts = new HashMap<>();
        while ((line = sbfr.readLine()) != null) {
            if (line.length() <= 1 || line.charAt(0) == '#') {
                continue;
            }
            m.reset(line);
            if (m.matches()) {
                int start = Integer.parseInt(m.group(1), 16);
                int end = (m.group(2)==null)?start
                                            :Integer.parseInt(m.group(2), 16);
                String name = m.group(3).toLowerCase(Locale.ENGLISH);
                ArrayList<Integer> ranges = scripts.get(name);
                if (ranges == null) {
                    ranges = new ArrayList<Integer>();
                    scripts.put(name, ranges);
                }
                ranges.add(start);
                ranges.add(end);
            }
        }
        sbfr.close();
        // check all defined ranges
        Integer[] ZEROSIZEARRAY = new Integer[0];
        for (String name : scripts.keySet()) {
            System.out.println("Checking " + name + "...");
            Integer[] ranges = scripts.get(name).toArray(ZEROSIZEARRAY);
            Character.UnicodeScript expected =
                Character.UnicodeScript.forName(name);

            int off = 0;
            while (off < ranges.length) {
                int start = ranges[off++];
                int end = ranges[off++];
                for (int cp = start; cp <= end; cp++) {
                    Character.UnicodeScript script =
                        Character.UnicodeScript.of(cp);
                    if (script != expected) {
                        throw new RuntimeException(
                            "UnicodeScript failed: cp=" +
                            Integer.toHexString(cp) +
                            ", of(cp)=<" + script + "> but <" +
                            expected + "> is expected");
                   }
                }
            }
        }
        // check all codepoints
        for (int cp = 0; cp < Character.MAX_CODE_POINT; cp++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.UNKNOWN) {
                if (Character.getType(cp) != Character.UNASSIGNED &&
                    Character.getType(cp) != Character.SURROGATE &&
                    Character.getType(cp) != Character.PRIVATE_USE)
                    throw new RuntimeException(
                        "UnicodeScript failed: cp=" +
                        Integer.toHexString(cp) +
                        ", of(cp)=<" + script + "> but UNKNOWN is expected");
            } else {
                Integer[] ranges =
                    scripts.get(script.name().toLowerCase(Locale.ENGLISH))
                           .toArray(ZEROSIZEARRAY);
                int off = 0;
                boolean found = false;
                while (off < ranges.length) {
                    int start = ranges[off++];
                    int end = ranges[off++];
                    if (cp >= start && cp <= end)
                        found = true;
                }
                if (!found) {
                    throw new RuntimeException(
                        "UnicodeScript failed: cp=" +
                        Integer.toHexString(cp) +
                        ", of(cp)=<" + script +
                        "> but NOT in ranges of this script");

                }
            }
        }
    }
}
