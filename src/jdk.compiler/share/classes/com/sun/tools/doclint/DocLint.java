package com.sun.tools.doclint;

import java.util.ServiceLoader;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

public abstract class DocLint implements Plugin {
    public static final String XMSGS_OPTION = "-Xmsgs";
    public static final String XMSGS_CUSTOM_PREFIX = "-Xmsgs:";
    public static final String XHTML_VERSION_PREFIX = "-XhtmlVersion:";
    public static final String XCHECK_PACKAGE = "-XcheckPackage:";

    private static ServiceLoader.Provider<DocLint> docLintProvider;

    public abstract boolean isValidOption(String opt);

    public static synchronized DocLint newDocLint() {
        if (docLintProvider == null) {
            docLintProvider = ServiceLoader.load(DocLint.class).stream()
                    .filter(p_ -> p_.get().getName().equals("doclint"))
                    .findFirst()
                    .orElse(new ServiceLoader.Provider<>() {
                        @Override
                        public Class<? extends DocLint> type() {
                            return NoDocLint.class;
                        }

                        @Override
                        public DocLint get() {
                            return new NoDocLint();
                        }
                    });
        }
        return docLintProvider.get();
    }

    private static class NoDocLint extends DocLint {
        @Override
        public String getName() {
            return "doclint-not-available";
        }

        @Override
        public void init(JavacTask task, String... args) {
            // ignore
        }

        @Override
        public boolean isValidOption(String s) {
            return false;
        }
    }
}
