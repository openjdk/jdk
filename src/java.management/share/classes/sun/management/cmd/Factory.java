package sun.management.cmd;

import sun.management.cmd.annotation.Command;
import sun.management.cmd.annotation.Parameter;

import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static sun.management.cmd.Constant.*;

/**
 * Command factory
 *
 * @author Denghui Dong
 */
public class Factory {

    private static final Set<String> COMMANDS = new HashSet<>();

    private static final Object LOCK = new Object();

    private static final String[] RESERVED_DOMAIN = {
        "Compiler.",
        "GC.",
        "JFR.",
        "JVMTI.",
        "ManagementAgent.",
        "System.",
        "Thread.",
        "VM."
    };

    /* <domain>.[sub-domain.]<command> */
    private static final Pattern
        CMD_NAME_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z_0-9]*\\.)+[A-Za-z_][A-Za-z_0-9]*$");

    private static final Pattern
        PARAM_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z_0-9]*$");

    static {
        // make sure that the management lib is loaded within
        // java.lang.management.ManagementFactory
        try {
            MethodHandles.lookup().ensureInitialized(ManagementFactory.class);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    private final int flags;

    private final boolean enabled;

    private final String disabledMessage;

    private CmdMeta command;

    private ParamMeta[] options;

    private ParamMeta[] arguments;

    private Factory(Class<? extends Executable> clazz) {
        Command cmd = getCommandAnnotation(clazz);
        checkFlags(cmd.exportFlags());
        this.flags = cmd.exportFlags();
        this.enabled = cmd.enabled();
        this.disabledMessage = cmd.disabledMessage();
        init(clazz);
    }

    private Factory(String name, String description, String impact, String[] permission, int flags, boolean enabled,
                    String disabledMessage, Executable executable) {
        checkFlags(flags);
        this.flags = flags;
        this.enabled = enabled;
        this.disabledMessage = disabledMessage;
        init(name, description, impact, permission, executable);
    }

    private static void checkFlags(int exportFlags) {
        if ((exportFlags & ~FULL_EXPORT) != 0 || (exportFlags & FULL_EXPORT) == 0) {
            throw new IllegalArgumentException("Illegal export flags: " + exportFlags);
        }
    }

    /**
     * Registers a command by class annotated by @Command
     */
    public static void register(Class<? extends Executable> clazz) {
        Command meta = getCommandAnnotation(clazz);
        checkName(clazz, meta.name());
        doRegister(new Factory(clazz));
    }

    /**
     * Registers a command by name and executable
     */
    public static void register(String name, Executable executable) {
        register(name, DEFAULT_DESCRIPTION, DEFAULT_IMPACT, DEFAULT_PERMISSION,
                 FULL_EXPORT, true, DEFAULT_DISABLED_MESSAGE, executable);
    }

    /**
     * Registers a command by full description
     */
    public static void register(String name, String description, String impact,
                                String[] permission, int exportFlags, boolean enabled, String disabledMessage,
                                Executable executable) {
        checkName(executable.getClass(), name);
        doRegister(new Factory(name, description, impact, permission, exportFlags, enabled, disabledMessage,
                               executable));
    }

    private static boolean isReservedName(Class<? extends Executable> clazz, String name) {
        String cn = clazz.getName();
        if (!cn.startsWith("java.") && !cn.startsWith("jdk.") && !cn.startsWith("javax.") && !cn.startsWith("sun.")) {
            for (String prefix : RESERVED_DOMAIN) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkName(Class<? extends Executable> clazz, String name) {
        if (!CMD_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Illegal command name: " + name);
        }

        if (isReservedName(clazz, name)) {
            throw new IllegalArgumentException("Reserved command name: " + name);
        }

        synchronized (LOCK) {
            if (COMMANDS.contains(name)) {
                throw new IllegalArgumentException("Duplicated command: " + name);
            }
        }
    }

    private static void doRegister(Factory factory) {
        String name = factory.command.name;
        synchronized (LOCK) {
            checkName(factory.command.clazz, name);
            doRegister0(factory);
            COMMANDS.add(name);
        }
    }

    private static native void doRegister0(Factory factory);

    private static Command getCommandAnnotation(Class<? extends Executable> clazz) {
        Command meta = clazz.getDeclaredAnnotation(Command.class);
        if (meta == null) {
            throw new IllegalArgumentException("Should be annotated by @Command");
        }
        return meta;
    }

    private void init(Class<? extends Executable> clazz) {
        initCmdMeta(clazz);
        initParamMeta();
    }

    private void init(String name, String description, String impact, String[] permission, Executable executable) {
        initCmdMeta(name, description, impact, permission, executable);
        initParamMeta();
    }

    private void initCmdMeta(Class<? extends Executable> clazz) {
        Command meta = getCommandAnnotation(clazz);

        try {
            clazz.getDeclaredConstructor();
        } catch (Exception e) {
            throw new IllegalArgumentException("Should have a default constructor");
        }

        this.command = new CmdMeta(meta.name(), meta.description(), meta.impact(), meta.permission(), clazz, null);
    }

    private void initCmdMeta(String name, String description, String impact, String[] permission,
                             Executable executable) {
        this.command = new CmdMeta(name, description, impact, permission, executable.getClass(), executable);
    }

    private void initParamMeta() {
        Field[] declaredFields = command.clazz.getDeclaredFields();
        List<ParamMeta> options = new ArrayList<>();
        List<ParamMeta> arguments = new ArrayList<>();
        int maxOrdinal = -1;
        Set<Integer> indexes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Parameter paramMeta = field.getDeclaredAnnotation(Parameter.class);
            if (paramMeta == null) {
                continue;
            }

            String typeDesc = parameterType(field);

            String name = paramMeta.name();

            if (names.contains(name)) {
                throw new IllegalArgumentException("Duplicated parameter name: " + paramMeta.name());
            }

            if (!PARAM_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Illegal parameter name");
            }

            if (paramMeta.ordinal() < -1) {
                throw new IllegalArgumentException("Illegal ordinal: " + paramMeta.ordinal());
            }

            names.add(paramMeta.name());

            ParamMeta meta = new ParamMeta(
                name,
                paramMeta.description(),
                paramMeta.ordinal(),
                paramMeta.defaultValue(),
                paramMeta.isMandatory(),
                typeDesc,
                field
            );

            if (meta.ordinal == -1) {
                options.add(meta);
            } else {
                if (indexes.contains(meta.ordinal)) {
                    throw new IllegalArgumentException("Duplicated ordinal: " + meta.ordinal);
                }
                indexes.add(meta.ordinal);
                maxOrdinal = Math.max(maxOrdinal, meta.ordinal);
                arguments.add(meta);
            }
        }

        if (indexes.size() != maxOrdinal + 1) {
            throw new IllegalArgumentException("Illegal ordinals");
        }

        arguments.sort(Comparator.comparingInt(p -> p.ordinal));
        this.options = options.toArray(new ParamMeta[0]);
        this.arguments = arguments.toArray(new ParamMeta[0]);
    }

    private String parameterType(Field field) {
        Class<?> type = field.getType();

        if (type == String.class) {
            return "STRING";
        } else if (type == long.class) {
            return "LONG";
        } else if (type == int.class) {
            return "INT";
        } else if (type == boolean.class) {
            return "BOOLEAN";
        } else {
            throw new IllegalArgumentException("Illegal parameter type");
        }
    }

    private int lookupOption(Arg arg) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].name.equals(arg.key)) {
                return i;
            }
        }
        return -1;
    }

    private void readValue(Executable cmd, Field field, String val) throws IllegalAccessException {
        Class<?> type = field.getType();
        field.setAccessible(true);
        if (type == String.class) {
            field.set(cmd, val);
            return;
        } else if (type == long.class) {
            field.set(cmd, Long.parseLong(val));
            return;
        } else if (type == int.class) {
            field.set(cmd, Integer.parseInt(val));
            return;
        } else if (type == boolean.class) {
            if (Boolean.TRUE.toString().equalsIgnoreCase(val)) {
                field.set(cmd, true);
                return;
            } else if (Boolean.FALSE.toString().equalsIgnoreCase(val)) {
                field.set(cmd, false);
                return;
            }
            throw new IllegalArgumentException("Illegal boolean value");
        }
        throw new IllegalStateException("Should not reach here");
    }

    /**
     * invoked by native
     */
    Executable buildCommand(String args, char delim) throws Exception {
        Executable command = this.command.executable;
        if (command == null) {
            Class<? extends Executable> clazz = this.command.clazz;
            Constructor<? extends Executable> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            command = constructor.newInstance();
        }

        int nextArg = 0;
        ArgItr argItr = new ArgItr(args, delim);
        boolean[] optionIsSet = new boolean[options.length];
        boolean[] argumentIsSet = new boolean[arguments.length];
        while (argItr.hasNext()) {
            Arg arg = argItr.next();
            int index = lookupOption(arg);
            if (index != -1) {
                ParamMeta option = options[index];
                if (arg.value != null) {
                    readValue(command, option.field, arg.value);
                    optionIsSet[index] = true;
                }
            } else if (nextArg < arguments.length) {
                readValue(command, arguments[nextArg].field, arg.key);
                argumentIsSet[nextArg++] = true;
            } else {
                throw new IllegalArgumentException("Unknown argument in command.");
            }
        }

        // check mandatory
        for (int i = 0; i < arguments.length; i++) {
            if (!argumentIsSet[i]) {
                ParamMeta argument = arguments[i];
                if (argument.defaultValue.length() > 0) {
                    readValue(command, argument.field, argument.defaultValue);
                    continue;
                }
                if (argument.isMandatory) {
                    throw new IllegalArgumentException("The argument '" + argument.name + "' is mandatory.");
                }
            }
        }

        for (int i = 0; i < options.length; i++) {
            if (!optionIsSet[i]) {
                ParamMeta option = options[i];
                if (option.defaultValue.length() > 0) {
                    readValue(command, option.field, option.defaultValue);
                    continue;
                }
                if (option.isMandatory) {
                    throw new IllegalArgumentException("The option '" + option.name + "' is mandatory.");
                }
            }
        }

        return command;
    }

    private static class CmdMeta {
        String name;

        String description;

        final String impact;

        final String permissionClass;

        final String permissionName;

        final String permissionAction;

        final Class<? extends Executable> clazz;

        final Executable executable;

        public CmdMeta(String name, String description, String impact, String[] permission,
                       Class<? extends Executable> clazz, Executable executable) {
            if (permission.length != 3) {
                throw new IllegalArgumentException("Illegal permission");
            }
            this.name = name;
            this.description = Optional.ofNullable(description).orElse(DEFAULT_DESCRIPTION);
            this.impact = Optional.ofNullable(impact).orElse(DEFAULT_IMPACT);
            this.permissionClass = Optional.ofNullable(permission[0]).orElse(EMPTY_VALUE);
            this.permissionName = Optional.ofNullable(permission[1]).orElse(EMPTY_VALUE);
            this.permissionAction = Optional.ofNullable(permission[2]).orElse(EMPTY_VALUE);
            this.clazz = clazz;
            this.executable = executable;
        }
    }

    private static class ParamMeta {
        final String name;

        final String description;

        final int ordinal;

        final String defaultValue;

        final boolean isMandatory;

        final String type;

        final Field field;

        public ParamMeta(String name, String description, int ordinal, String defaultValue, boolean isMandatory,
                         String type, Field field) {
            this.name = name;
            this.description = Optional.ofNullable(description).orElse(DEFAULT_DESCRIPTION);
            this.ordinal = ordinal;
            this.defaultValue = Optional.ofNullable(defaultValue).orElse(EMPTY_VALUE);
            this.isMandatory = isMandatory;
            this.type = type;
            this.field = field;
        }
    }

    private static class Arg {
        String key;

        String value;
    }

    private static class ArgItr implements Iterator<Arg> {
        String args;
        char delim;

        int cursor;
        Arg cur;

        ArgItr(String args, char delim) {
            this.args = args;
            this.delim = delim;
            step();
        }

        private void matchNextQuote(char quote) {
            while (cursor < args.length() - 1) {
                cursor++;
                if (args.charAt(cursor) == quote && args.charAt(cursor - 1) != '\\') {
                    break;
                }
            }
            if (args.charAt(cursor) != quote) {
                throw new IllegalArgumentException("Format error in diagnostic command arguments");
            }
        }

        private void step() {
            if (cursor >= args.length()) {
                return;
            }

            while (args.charAt(cursor) == delim) {
                if (++cursor >= args.length()) {
                    return;
                }
            }

            int keyStart = cursor;
            boolean hadQuotes = false;
            do {
                char c = args.charAt(cursor);
                if (c == '=' || c == delim) {
                    break;
                }
                if (c == '\"' || c == '\'') {
                    keyStart++;
                    hadQuotes = true;
                    matchNextQuote(c);
                    break;
                }
            } while (++cursor < args.length());

            String key = args.substring(keyStart, cursor);
            if (key.length() == 0) {
                return;
            }

            if (hadQuotes) {
                cursor++;
            }

            String val = null;
            if (cursor < args.length() && args.charAt(cursor) == '=') {
                cursor++;
                int valStart = cursor;
                hadQuotes = false;

                while (cursor < args.length()) {
                    char c = args.charAt(cursor);
                    if (c == delim) {
                        break;
                    }
                    if (c == '\"' || c == '\'') {
                        valStart++;
                        hadQuotes = true;
                        matchNextQuote(c);
                        break;
                    }
                    cursor++;
                }

                val = args.substring(valStart, cursor);
                if (hadQuotes) {
                    cursor++;
                }
            }

            cur = new Arg();
            cur.key = key;
            cur.value = val;
        }

        @Override
        public boolean hasNext() {
            return cur != null;
        }

        @Override
        public Arg next() {
            if (cur == null) {
                throw new NoSuchElementException();
            }
            Arg res = cur;
            cur = null;
            step();
            return res;
        }
    }
}
