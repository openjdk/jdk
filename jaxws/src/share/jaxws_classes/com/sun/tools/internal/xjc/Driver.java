/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.internal.xjc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Iterator;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.writer.ZipCodeWriter;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.tools.DefaultAuthenticator;
import com.sun.tools.internal.xjc.generator.bean.BeanGenerator;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.reader.gbind.Expression;
import com.sun.tools.internal.xjc.reader.gbind.Graph;
import com.sun.tools.internal.xjc.reader.internalizer.DOMForest;
import com.sun.tools.internal.xjc.reader.xmlschema.ExpressionBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.parser.XMLSchemaInternalizationLogic;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;
import com.sun.tools.internal.xjc.util.NullStream;
import com.sun.tools.internal.xjc.util.Util;
import com.sun.tools.internal.xjc.writer.SignatureWriter;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchemaSet;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * Command Line Interface of XJC.
 */
public class Driver {

    public static void main(final String[] args) throws Exception {
        // use the platform default proxy if available.
        // see sun.net.spi.DefaultProxySelector for details.
        try {
            System.setProperty("java.net.useSystemProxies","true");
        } catch (SecurityException e) {
            // failing to set this property isn't fatal
        }

        if( Util.getSystemProperty(Driver.class,"noThreadSwap")!=null )
            _main(args);    // for the ease of debugging

        // run all the work in another thread so that the -Xss option
        // will take effect when compiling a large schema. See
        // http://developer.java.sun.com/developer/bugParade/bugs/4362291.html
        final Throwable[] ex = new Throwable[1];

        Thread th = new Thread() {
            @Override
            public void run() {
                try {
                    _main(args);
                } catch( Throwable e ) {
                    ex[0]=e;
                }
            }
        };
        th.start();
        th.join();

        if(ex[0]!=null) {
            // re-throw
            if( ex[0] instanceof Exception )
                throw (Exception)ex[0];
            else
                throw (Error)ex[0];
        }
    }

    private static void _main( String[] args ) throws Exception {
        try {
            System.exit(run( args, System.out, System.out ));
        } catch (BadCommandLineException e) {
            // there was an error in the command line.
            // print usage and abort.
            if(e.getMessage()!=null) {
                System.out.println(e.getMessage());
                System.out.println();
            }

            usage(e.getOptions(),false);
            System.exit(-1);
        }
    }



    /**
     * Performs schema compilation and prints the status/error into the
     * specified PrintStream.
     *
     * <p>
     * This method could be used to trigger XJC from other tools,
     * such as Ant or IDE.
     *
     * @param    args
     *      specified command line parameters. If there is an error
     *      in the parameters, {@link BadCommandLineException} will
     *      be thrown.
     * @param    status
     *      Status report of the compilation will be sent to this object.
     *      Useful to update users so that they will know something is happening.
     *      Only ignorable messages should be sent to this stream.
     *
     *      This parameter can be null to suppress messages.
     *
     * @param    out
     *      Various non-ignorable output (error messages, etc)
     *      will go to this stream.
     *
     * @return
     *      If the compiler runs successfully, this method returns 0.
     *      All non-zero values indicate an error. The error message
     *      will be sent to the specified PrintStream.
     */
    public static int run(String[] args, final PrintStream status, final PrintStream out)
        throws Exception {

        class Listener extends XJCListener {
            ConsoleErrorReporter cer = new ConsoleErrorReporter(out==null?new PrintStream(new NullStream()):out);

            @Override
            public void generatedFile(String fileName, int count, int total) {
                message(fileName);
            }
            @Override
            public void message(String msg) {
                if(status!=null)
                    status.println(msg);
            }

            public void error(SAXParseException exception) {
                cer.error(exception);
            }

            public void fatalError(SAXParseException exception) {
                cer.fatalError(exception);
            }

            public void warning(SAXParseException exception) {
                cer.warning(exception);
            }

            public void info(SAXParseException exception) {
                cer.info(exception);
            }
        }

        return run(args,new Listener());
    }

    /**
     * Performs schema compilation and prints the status/error into the
     * specified PrintStream.
     *
     * <p>
     * This method could be used to trigger XJC from other tools,
     * such as Ant or IDE.
     *
     * @param    args
     *        specified command line parameters. If there is an error
     *        in the parameters, {@link BadCommandLineException} will
     *        be thrown.
     * @param    listener
     *      Receives messages from XJC reporting progress/errors.
     *
     * @return
     *      If the compiler runs successfully, this method returns 0.
     *      All non-zero values indicate an error. The error message
     *      will be sent to the specified PrintStream.
     */
    public static int run(String[] args, @NotNull final XJCListener listener) throws BadCommandLineException {

        // recognize those special options before we start parsing options.
        for (String arg : args) {
            if (arg.equals("-version")) {
                listener.message(Messages.format(Messages.VERSION));
                return -1;
            }
            if (arg.equals("-fullversion")) {
                listener.message(Messages.format(Messages.FULLVERSION));
                return -1;
            }
        }

        final OptionsEx opt = new OptionsEx();
        opt.setSchemaLanguage(Language.XMLSCHEMA);  // disable auto-guessing
        try {
            opt.parseArguments(args);
        } catch (WeAreDone e) {
            if (opt.proxyAuth != null) {
                DefaultAuthenticator.reset();
            }
            return -1;
        } catch(BadCommandLineException e) {
            if (opt.proxyAuth != null) {
                DefaultAuthenticator.reset();
            }
            e.initOptions(opt);
            throw e;
        }

        // display a warning if the user specified the default package
        // this should work, but is generally a bad idea
        if(opt.defaultPackage != null && opt.defaultPackage.length()==0) {
            listener.message(Messages.format(Messages.WARNING_MSG, Messages.format(Messages.DEFAULT_PACKAGE_WARNING)));
        }


        // set up the context class loader so that the user-specified classes
        // can be loaded from there
        final ClassLoader contextClassLoader = SecureLoader.getContextClassLoader();
        SecureLoader.setContextClassLoader(opt.getUserClassLoader(contextClassLoader));

        // parse a grammar file
        //-----------------------------------------
        try {
            if( !opt.quiet ) {
                listener.message(Messages.format(Messages.PARSING_SCHEMA));
            }

            final boolean[] hadWarning = new boolean[1];

            ErrorReceiver receiver = new ErrorReceiverFilter(listener) {
                @Override
                public void info(SAXParseException exception) {
                    if(opt.verbose)
                        super.info(exception);
                }
                @Override
                public void warning(SAXParseException exception) {
                    hadWarning[0] = true;
                    if(!opt.quiet)
                        super.warning(exception);
                }
                @Override
                public void pollAbort() throws AbortException {
                    if(listener.isCanceled())
                        throw new AbortException();
                }
            };

            if( opt.mode==Mode.FOREST ) {
                // dump DOM forest and quit
                ModelLoader loader  = new ModelLoader( opt, new JCodeModel(), receiver );
                try {
                    DOMForest forest = loader.buildDOMForest(new XMLSchemaInternalizationLogic());
                    forest.dump(System.out);
                    return 0;
                } catch (SAXException e) {
                    // the error should have already been reported
                } catch (IOException e) {
                    receiver.error(e);
                }

                return -1;
            }

            if( opt.mode==Mode.GBIND ) {
                try {
                    XSSchemaSet xss = new ModelLoader(opt, new JCodeModel(), receiver).loadXMLSchema();
                    Iterator<XSComplexType> it = xss.iterateComplexTypes();
                    while (it.hasNext()) {
                        XSComplexType ct =  it.next();
                        XSParticle p = ct.getContentType().asParticle();
                        if(p==null)     continue;

                        Expression tree = ExpressionBuilder.createTree(p);
                        System.out.println("Graph for "+ct.getName());
                        System.out.println(tree.toString());
                        Graph g = new Graph(tree);
                        System.out.println(g.toString());
                        System.out.println();
                    }
                    return 0;
                } catch (SAXException e) {
                    // the error should have already been reported
                }
                return -1;
            }

            Model model = ModelLoader.load( opt, new JCodeModel(), receiver );

            if (model == null) {
                listener.message(Messages.format(Messages.PARSE_FAILED));
                return -1;
            }

            if( !opt.quiet ) {
                listener.message(Messages.format(Messages.COMPILING_SCHEMA));
            }

            switch (opt.mode) {
            case SIGNATURE :
                try {
                    SignatureWriter.write(
                        BeanGenerator.generate(model,receiver),
                        new OutputStreamWriter(System.out));
                    return 0;
                } catch (IOException e) {
                    receiver.error(e);
                    return -1;
                }

            case CODE :
            case DRYRUN :
            case ZIP :
                {
                    // generate actual code
                    receiver.debug("generating code");
                    {// don't want to hold outline in memory for too long.
                        Outline outline = model.generateCode(opt,receiver);
                        if(outline==null) {
                            listener.message(
                                Messages.format(Messages.FAILED_TO_GENERATE_CODE));
                            return -1;
                        }

                        listener.compiled(outline);
                    }

                    if( opt.mode == Mode.DRYRUN )
                        break;  // enough

                    // then print them out
                    try {
                        CodeWriter cw;
                        if( opt.mode==Mode.ZIP ) {
                            OutputStream os;
                            if(opt.targetDir.getPath().equals("."))
                                os = System.out;
                            else
                                os = new FileOutputStream(opt.targetDir);

                            cw = opt.createCodeWriter(new ZipCodeWriter(os));
                        } else
                            cw = opt.createCodeWriter();

                        if( !opt.quiet ) {
                            cw = new ProgressCodeWriter(cw,listener, model.codeModel.countArtifacts());
                        }
                        model.codeModel.build(cw);
                    } catch (IOException e) {
                        receiver.error(e);
                        return -1;
                    }

                    break;
                }
            default :
                assert false;
            }

            if(opt.debugMode) {
                try {
                    new FileOutputStream(new File(opt.targetDir,hadWarning[0]?"hadWarning":"noWarning")).close();
                } catch (IOException e) {
                    receiver.error(e);
                    return -1;
                }
            }

            return 0;
        } catch( StackOverflowError e ) {
            if(opt.verbose)
                // in the debug mode, propagate the error so that
                // the full stack trace will be dumped to the screen.
                throw e;
            else {
                // otherwise just print a suggested workaround and
                // quit without filling the user's screen
                listener.message(Messages.format(Messages.STACK_OVERFLOW));
                return -1;
            }
        } finally {
            if (opt.proxyAuth != null) {
                DefaultAuthenticator.reset();
            }
        }
    }

    public static String getBuildID() {
        return Messages.format(Messages.BUILD_ID);
    }


    /**
     * Operation mode.
     */
    private static enum Mode {
        // normal mode. compile the code
        CODE,

        // dump the signature of the generated code
        SIGNATURE,

        // dump DOMForest
        FOREST,

        // same as CODE but don't produce any Java source code
        DRYRUN,

        // same as CODE but pack all the outputs into a zip and dumps to stdout
        ZIP,

        // testing a new binding mode
        GBIND
    }


    /**
     * Command-line arguments processor.
     *
     * <p>
     * This class contains options that only make sense
     * for the command line interface.
     */
    static class OptionsEx extends Options
    {
        /** Operation mode. */
        protected Mode mode = Mode.CODE;

        /** A switch that determines the behavior in the BGM mode. */
        public boolean noNS = false;

        /** Parse XJC-specific options. */
        @Override
        public int parseArgument(String[] args, int i) throws BadCommandLineException {
            if (args[i].equals("-noNS")) {
                noNS = true;
                return 1;
            }
            if (args[i].equals("-mode")) {
                i++;
                if (i == args.length)
                    throw new BadCommandLineException(
                        Messages.format(Messages.MISSING_MODE_OPERAND));

                String mstr = args[i].toLowerCase();

                for( Mode m : Mode.values() ) {
                    if(m.name().toLowerCase().startsWith(mstr) && mstr.length()>2) {
                        mode = m;
                        return 2;
                    }
                }

                throw new BadCommandLineException(
                    Messages.format(Messages.UNRECOGNIZED_MODE, args[i]));
            }
            if (args[i].equals("-help")) {
                usage(this,false);
                throw new WeAreDone();
            }
            if (args[i].equals("-private")) {
                usage(this,true);
                throw new WeAreDone();
            }

            return super.parseArgument(args, i);
        }
    }

    /**
     * Used to signal that we've finished processing.
     */
    private static final class WeAreDone extends BadCommandLineException {}


    /**
     * Prints the usage screen and exits the process.
     *
     * @param opts
     *      If the parsing of options have started, set a partly populated
     *      {@link Options} object.
     */
    public static void usage( @Nullable Options opts, boolean privateUsage ) {
        System.out.println(Messages.format(Messages.DRIVER_PUBLIC_USAGE));
        if (privateUsage) {
            System.out.println(Messages.format(Messages.DRIVER_PRIVATE_USAGE));
        }

        if( opts!=null && !opts.getAllPlugins().isEmpty()) {
            System.out.println(Messages.format(Messages.ADDON_USAGE));
            for (Plugin p : opts.getAllPlugins()) {
                System.out.println(p.getUsage());
            }
        }
    }
}
