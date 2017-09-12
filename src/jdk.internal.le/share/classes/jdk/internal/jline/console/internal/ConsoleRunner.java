/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.internal;

import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.completer.ArgumentCompleter;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.history.FileHistory;
import jdk.internal.jline.internal.Configuration;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

// FIXME: Clean up API and move to jline.console.runner package

/**
 * A pass-through application that sets the system input stream to a
 * {@link ConsoleReader} and invokes the specified main method.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @since 2.7
 */
public class ConsoleRunner
{
    public static final String property = "jline.history";

    // FIXME: This is really ugly... re-write this

    public static void main(final String[] args) throws Exception {
        List<String> argList = new ArrayList<String>(Arrays.asList(args));
        if (argList.size() == 0) {
            usage();
            return;
        }

        String historyFileName = System.getProperty(ConsoleRunner.property, null);

        String mainClass = argList.remove(0);
        ConsoleReader reader = new ConsoleReader();

        if (historyFileName != null) {
            reader.setHistory(new FileHistory(new File(Configuration.getUserHome(),
                String.format(".jline-%s.%s.history", mainClass, historyFileName))));
        }
        else {
            reader.setHistory(new FileHistory(new File(Configuration.getUserHome(),
                String.format(".jline-%s.history", mainClass))));
        }

        String completors = System.getProperty(ConsoleRunner.class.getName() + ".completers", "");
        List<Completer> completorList = new ArrayList<Completer>();

        for (StringTokenizer tok = new StringTokenizer(completors, ","); tok.hasMoreTokens();) {
            @SuppressWarnings("deprecation")
            Object obj = Class.forName(tok.nextToken()).newInstance();
            completorList.add((Completer) obj);
        }

        if (completorList.size() > 0) {
            reader.addCompleter(new ArgumentCompleter(completorList));
        }

        ConsoleReaderInputStream.setIn(reader);

        try {
            Class<?> type = Class.forName(mainClass);
            Method method = type.getMethod("main", String[].class);
            method.invoke(null);
        }
        finally {
            // just in case this main method is called from another program
            ConsoleReaderInputStream.restoreIn();
        }
    }

    private static void usage() {
        System.out.println("Usage: \n   java " + "[-Djline.history='name'] "
            + ConsoleRunner.class.getName()
            + " <target class name> [args]"
            + "\n\nThe -Djline.history option will avoid history"
            + "\nmangling when running ConsoleRunner on the same application."
            + "\n\nargs will be passed directly to the target class name.");
    }
}
