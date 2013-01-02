package tidystats;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String... args) throws IOException {
        new Main().run(args);
    }

    void run(String... args) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        List<Path> paths = new ArrayList<>();

        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-"))
                throw new IllegalArgumentException(arg);
            else
                break;
        }

        for ( ; i < args.length; i++) {
            Path p = fs.getPath(args[i]);
            paths.add(p);
        }

        for (Path p: paths) {
            scan(p);
        }

        print("%6d files read", files);
        print("%6d files had no errors or warnings", ok);
        print("%6d files reported \"Not all warnings/errors were shown.\"", overflow);
        print("%6d errors found", errs);
        print("%6d warnings found", warns);
        print("%6d recommendations to use CSS", css);
        print("");

        Map<Integer, Set<String>> sortedCounts = new TreeMap<>(
                new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return o2.compareTo(o1);
                    }
                });

        for (Map.Entry<Pattern, Integer> e: counts.entrySet()) {
            Pattern p = e.getKey();
            Integer n = e.getValue();
            Set<String> set = sortedCounts.get(n);
            if (set == null)
                sortedCounts.put(n, (set = new TreeSet<>()));
            set.add(p.toString());
        }

        for (Map.Entry<Integer, Set<String>> e: sortedCounts.entrySet()) {
            for (String p: e.getValue()) {
                if (p.startsWith(".*")) p = p.substring(2);
                print("%6d: %s", e.getKey(), p);
            }
        }
    }

    void scan(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            for (Path c: Files.newDirectoryStream(p)) {
                scan(c);
            }
        } else if (isTidyFile(p)) {
            scan(Files.readAllLines(p, Charset.defaultCharset()));
        }
    }

    boolean isTidyFile(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".tidy");
    }

    void scan(List<String> lines) {
        Matcher m;
        files++;
        for (String line: lines) {
            if (okPattern.matcher(line).matches()) {
                ok++;
            } else if ((m = countPattern.matcher(line)).matches()) {
                warns += Integer.valueOf(m.group(1));
                errs += Integer.valueOf(m.group(2));
                if (m.group(3) != null)
                    overflow++;
            } else if ((m = guardPattern.matcher(line)).matches()) {
                boolean found = false;
                for (Pattern p: patterns) {
                    if ((m = p.matcher(line)).matches()) {
                        found = true;
                        count(p);
                        break;
                    }
                }
                if (!found)
                    System.err.println("Unrecognized line: " + line);
            } else if (cssPattern.matcher(line).matches()) {
                css++;
            }
        }
    }

    Map<Pattern, Integer> counts = new HashMap<>();
    void count(Pattern p) {
        Integer i = counts.get(p);
        counts.put(p, (i == null) ? 1 : i + 1);
    }

    void print(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    Pattern okPattern = Pattern.compile("No warnings or errors were found.");
    Pattern countPattern = Pattern.compile("([0-9]+) warnings, ([0-9]+) errors were found!.*?(Not all warnings/errors were shown.)?");
    Pattern cssPattern = Pattern.compile("You are recommended to use CSS.*");
    Pattern guardPattern = Pattern.compile("line [0-9]+ column [0-9]+ - (Error|Warning):.*");

    Pattern[] patterns = {
        Pattern.compile(".*Error: <.*> is not recognized!"),
        Pattern.compile(".*Error: missing quote mark for attribute value"),
        Pattern.compile(".*Warning: <.*> anchor \".*\" already defined"),
        Pattern.compile(".*Warning: <.*> attribute \".*\" has invalid value \".*\""),
        Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
        Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
        Pattern.compile(".*Warning: <.*> attribute with missing trailing quote mark"),
        Pattern.compile(".*Warning: <.*> dropping value \".*\" for repeated attribute \".*\""),
        Pattern.compile(".*Warning: <.*> inserting \".*\" attribute"),
        Pattern.compile(".*Warning: <.*> is probably intended as </.*>"),
        Pattern.compile(".*Warning: <.*> isn't allowed in <.*> elements"),
        Pattern.compile(".*Warning: <.*> lacks \".*\" attribute"),
        Pattern.compile(".*Warning: <.*> missing '>' for end of tag"),
        Pattern.compile(".*Warning: <.*> proprietary attribute \".*\""),
        Pattern.compile(".*Warning: <.*> unexpected or duplicate quote mark"),
        Pattern.compile(".*Warning: <a> cannot copy name attribute to id"),
        Pattern.compile(".*Warning: <a> escaping malformed URI reference"),
        Pattern.compile(".*Warning: <blockquote> proprietary attribute \"pre\""),
        Pattern.compile(".*Warning: discarding unexpected <.*>"),
        Pattern.compile(".*Warning: discarding unexpected </.*>"),
        Pattern.compile(".*Warning: entity \".*\" doesn't end in ';'"),
        Pattern.compile(".*Warning: inserting implicit <.*>"),
        Pattern.compile(".*Warning: inserting missing 'title' element"),
        Pattern.compile(".*Warning: missing <!DOCTYPE> declaration"),
        Pattern.compile(".*Warning: missing <.*>"),
        Pattern.compile(".*Warning: missing </.*> before <.*>"),
        Pattern.compile(".*Warning: nested emphasis <.*>"),
        Pattern.compile(".*Warning: plain text isn't allowed in <.*> elements"),
        Pattern.compile(".*Warning: replacing <p> by <br>"),
        Pattern.compile(".*Warning: replacing invalid numeric character reference .*"),
        Pattern.compile(".*Warning: replacing unexpected .* by </.*>"),
        Pattern.compile(".*Warning: trimming empty <.*>"),
        Pattern.compile(".*Warning: unescaped & or unknown entity \".*\""),
        Pattern.compile(".*Warning: unescaped & which should be written as &amp;"),
        Pattern.compile(".*Warning: using <br> in place of <p>")
    };

    int files;
    int ok;
    int warns;
    int errs;
    int css;
    int overflow;
}

