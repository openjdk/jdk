/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console;

import java.util.HashMap;
import java.util.Map;

/**
 * The KeyMap class contains all bindings from keys to operations.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.6
 */
public class KeyMap {

    public static final String VI_MOVE        = "vi-move";
    public static final String VI_INSERT      = "vi-insert";
    public static final String EMACS          = "emacs";
    public static final String EMACS_STANDARD = "emacs-standard";
    public static final String EMACS_CTLX     = "emacs-ctlx";
    public static final String EMACS_META     = "emacs-meta";

    private static final int KEYMAP_LENGTH = 256;

    private static final Object NULL_FUNCTION = new Object();

    private Object[] mapping = new Object[KEYMAP_LENGTH];
    private Object anotherKey = null;
    private String name;
    private boolean isViKeyMap;

    public KeyMap(String name, boolean isViKeyMap) {
        this(name, new Object[KEYMAP_LENGTH], isViKeyMap);
    }

    protected KeyMap(String name, Object[] mapping, boolean isViKeyMap) {
        this.mapping = mapping;
        this.name = name;
        this.isViKeyMap = isViKeyMap;
    }

    public boolean isViKeyMap() {
        return isViKeyMap;
    }

    public String getName() {
        return name;
    }

    public Object getAnotherKey() {
        return anotherKey;
    }

    public void from(KeyMap other) {
        this.mapping = other.mapping;
        this.anotherKey = other.anotherKey;
    }

    public Object getBound( CharSequence keySeq ) {
        if (keySeq != null && keySeq.length() > 0) {
            KeyMap map = this;
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c > 255) {
                    return Operation.SELF_INSERT;
                }
                if (map.mapping[c] instanceof KeyMap) {
                    if (i == keySeq.length() - 1) {
                        return map.mapping[c];
                    } else {
                        map = (KeyMap) map.mapping[c];
                    }
                } else {
                    return map.mapping[c];
                }
            }
        }
        return null;
    }

    public void bindIfNotBound( CharSequence keySeq, Object function ) {

        bind (this, keySeq, function, true);
    }

    public void bind( CharSequence keySeq, Object function ) {

        bind (this, keySeq, function, false);
    }

    private static void bind( KeyMap map, CharSequence keySeq, Object function ) {

        bind (map, keySeq, function, false);
    }

    private static void bind( KeyMap map, CharSequence keySeq, Object function,
            boolean onlyIfNotBound ) {

        if (keySeq != null && keySeq.length() > 0) {
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c >= map.mapping.length) {
                    return;
                }
                if (i < keySeq.length() - 1) {
                    if (!(map.mapping[c] instanceof KeyMap)) {
                        KeyMap m = new KeyMap("anonymous", false);
                        if (map.mapping[c] != Operation.DO_LOWERCASE_VERSION) {
                            m.anotherKey = map.mapping[c];
                        }
                        map.mapping[c] = m;
                    }
                    map = (KeyMap) map.mapping[c];
                } else {
                    if (function == null) {
                        function = NULL_FUNCTION;
                    }
                    if (map.mapping[c] instanceof KeyMap) {
                        map.anotherKey = function;
                    } else {
                        Object op = map.mapping[c];
                        if (onlyIfNotBound == false
                            || op == null
                            || op == Operation.DO_LOWERCASE_VERSION
                            || op == Operation.VI_MOVEMENT_MODE ) {

                        }

                        map.mapping[c] = function;
                    }
                }
            }
        }
    }

    public void setBlinkMatchingParen(boolean on) {
        if (on) {
            bind( "}", Operation.INSERT_CLOSE_CURLY );
            bind( ")", Operation.INSERT_CLOSE_PAREN );
            bind( "]", Operation.INSERT_CLOSE_SQUARE );
        }
    }

    private static void bindArrowKeys(KeyMap map) {

        // MS-DOS
        bind( map, "\033[0A", Operation.PREVIOUS_HISTORY );
        bind( map, "\033[0B", Operation.BACKWARD_CHAR );
        bind( map, "\033[0C", Operation.FORWARD_CHAR );
        bind( map, "\033[0D", Operation.NEXT_HISTORY );

        // Windows
        bind( map, "\340\000", Operation.KILL_WHOLE_LINE );
        bind( map, "\340\107", Operation.BEGINNING_OF_LINE );
        bind( map, "\340\110", Operation.PREVIOUS_HISTORY );
        bind( map, "\340\111", Operation.BEGINNING_OF_HISTORY );
        bind( map, "\340\113", Operation.BACKWARD_CHAR );
        bind( map, "\340\115", Operation.FORWARD_CHAR );
        bind( map, "\340\117", Operation.END_OF_LINE );
        bind( map, "\340\120", Operation.NEXT_HISTORY );
        bind( map, "\340\121", Operation.END_OF_HISTORY );
        bind( map, "\340\122", Operation.OVERWRITE_MODE );
        bind( map, "\340\123", Operation.DELETE_CHAR );

        bind( map, "\000\107", Operation.BEGINNING_OF_LINE );
        bind( map, "\000\110", Operation.PREVIOUS_HISTORY );
        bind( map, "\000\111", Operation.BEGINNING_OF_HISTORY );
        bind( map, "\000\110", Operation.PREVIOUS_HISTORY );
        bind( map, "\000\113", Operation.BACKWARD_CHAR );
        bind( map, "\000\115", Operation.FORWARD_CHAR );
        bind( map, "\000\117", Operation.END_OF_LINE );
        bind( map, "\000\120", Operation.NEXT_HISTORY );
        bind( map, "\000\121", Operation.END_OF_HISTORY );
        bind( map, "\000\122", Operation.OVERWRITE_MODE );
        bind( map, "\000\123", Operation.DELETE_CHAR );

        bind( map, "\033[A", Operation.PREVIOUS_HISTORY );
        bind( map, "\033[B", Operation.NEXT_HISTORY );
        bind( map, "\033[C", Operation.FORWARD_CHAR );
        bind( map, "\033[D", Operation.BACKWARD_CHAR );
        bind( map, "\033[H", Operation.BEGINNING_OF_LINE );
        bind( map, "\033[F", Operation.END_OF_LINE );

        bind( map, "\033OA", Operation.PREVIOUS_HISTORY );
        bind( map, "\033OB", Operation.NEXT_HISTORY );
        bind( map, "\033OC", Operation.FORWARD_CHAR );
        bind( map, "\033OD", Operation.BACKWARD_CHAR );
        bind( map, "\033OH", Operation.BEGINNING_OF_LINE );
        bind( map, "\033OF", Operation.END_OF_LINE );

        bind( map, "\033[1~", Operation.BEGINNING_OF_LINE);
        bind( map, "\033[4~", Operation.END_OF_LINE);
        bind( map, "\033[3~", Operation.DELETE_CHAR);

        // MINGW32
        bind( map, "\0340H", Operation.PREVIOUS_HISTORY );
        bind( map, "\0340P", Operation.NEXT_HISTORY );
        bind( map, "\0340M", Operation.FORWARD_CHAR );
        bind( map, "\0340K", Operation.BACKWARD_CHAR );
    }

//    public boolean isConvertMetaCharsToAscii() {
//        return convertMetaCharsToAscii;
//    }

//    public void setConvertMetaCharsToAscii(boolean convertMetaCharsToAscii) {
//        this.convertMetaCharsToAscii = convertMetaCharsToAscii;
//    }

    public static boolean isMeta( char c ) {
        return c > 0x7f && c <= 0xff;
    }

    public static char unMeta( char c ) {
        return (char) (c & 0x7F);
    }

    public static char meta( char c ) {
        return (char) (c | 0x80);
    }

    public static Map<String, KeyMap> keyMaps() {
        Map<String, KeyMap> keyMaps = new HashMap<String, KeyMap>();

        KeyMap emacs = emacs();
        bindArrowKeys(emacs);
        keyMaps.put(EMACS, emacs);
        keyMaps.put(EMACS_STANDARD, emacs);
        keyMaps.put(EMACS_CTLX, (KeyMap) emacs.getBound("\u0018"));
        keyMaps.put(EMACS_META, (KeyMap) emacs.getBound("\u001b"));

        KeyMap viMov = viMovement();
        bindArrowKeys(viMov);
        keyMaps.put(VI_MOVE, viMov);
        keyMaps.put("vi-command", viMov);

        KeyMap viIns = viInsertion();
        bindArrowKeys(viIns);
        keyMaps.put(VI_INSERT, viIns);
        keyMaps.put("vi", viIns);

        return keyMaps;
    }

    public static KeyMap emacs() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] ctrl = new Object[] {
                        // Control keys.
                        Operation.SET_MARK,                 /* Control-@ */
                        Operation.BEGINNING_OF_LINE,        /* Control-A */
                        Operation.BACKWARD_CHAR,            /* Control-B */
                        Operation.INTERRUPT,                /* Control-C */
                        Operation.EXIT_OR_DELETE_CHAR,      /* Control-D */
                        Operation.END_OF_LINE,              /* Control-E */
                        Operation.FORWARD_CHAR,             /* Control-F */
                        Operation.ABORT,                    /* Control-G */
                        Operation.BACKWARD_DELETE_CHAR,     /* Control-H */
                        Operation.COMPLETE,                 /* Control-I */
                        Operation.ACCEPT_LINE,              /* Control-J */
                        Operation.KILL_LINE,                /* Control-K */
                        Operation.CLEAR_SCREEN,             /* Control-L */
                        Operation.ACCEPT_LINE,              /* Control-M */
                        Operation.NEXT_HISTORY,             /* Control-N */
                        null,                               /* Control-O */
                        Operation.PREVIOUS_HISTORY,         /* Control-P */
                        Operation.QUOTED_INSERT,            /* Control-Q */
                        Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                        Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                        Operation.TRANSPOSE_CHARS,          /* Control-T */
                        Operation.UNIX_LINE_DISCARD,        /* Control-U */
                        Operation.QUOTED_INSERT,            /* Control-V */
                        Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                        emacsCtrlX(),                       /* Control-X */
                        Operation.YANK,                     /* Control-Y */
                        null,                               /* Control-Z */
                        emacsMeta(),                        /* Control-[ */
                        null,                               /* Control-\ */
                        Operation.CHARACTER_SEARCH,         /* Control-] */
                        null,                               /* Control-^ */
                        Operation.UNDO,                     /* Control-_ */
                };
        System.arraycopy( ctrl, 0, map, 0, ctrl.length );
        for (int i = 32; i < 256; i++) {
            map[i] = Operation.SELF_INSERT;
        }
        map[DELETE] = Operation.BACKWARD_DELETE_CHAR;
        return new KeyMap(EMACS, map, false);
    }

    public static final char CTRL_D = (char) 4;
    public static final char CTRL_G = (char) 7;
    public static final char CTRL_H = (char) 8;
    public static final char CTRL_I = (char) 9;
    public static final char CTRL_J = (char) 10;
    public static final char CTRL_M = (char) 13;
    public static final char CTRL_R = (char) 18;
    public static final char CTRL_S = (char) 19;
    public static final char CTRL_U = (char) 21;
    public static final char CTRL_X = (char) 24;
    public static final char CTRL_Y = (char) 25;
    public static final char ESCAPE = (char) 27; /* Ctrl-[ */
    public static final char CTRL_OB = (char) 27; /* Ctrl-[ */
    public static final char CTRL_CB = (char) 29; /* Ctrl-] */

    public static final int DELETE = (char) 127;

    public static KeyMap emacsCtrlX() {
        Object[] map = new Object[KEYMAP_LENGTH];
        map[CTRL_G] = Operation.ABORT;
        map[CTRL_R] = Operation.RE_READ_INIT_FILE;
        map[CTRL_U] = Operation.UNDO;
        map[CTRL_X] = Operation.EXCHANGE_POINT_AND_MARK;
        map['('] = Operation.START_KBD_MACRO;
        map[')'] = Operation.END_KBD_MACRO;
        for (int i = 'A'; i <= 'Z'; i++) {
            map[i] = Operation.DO_LOWERCASE_VERSION;
        }
        map['e'] = Operation.CALL_LAST_KBD_MACRO;
        map[DELETE] = Operation.KILL_LINE;
        return new KeyMap(EMACS_CTLX, map, false);
    }

    public static KeyMap emacsMeta() {
        Object[] map = new Object[KEYMAP_LENGTH];
        map[CTRL_G] = Operation.ABORT;
        map[CTRL_H] = Operation.BACKWARD_KILL_WORD;
        map[CTRL_I] = Operation.TAB_INSERT;
        map[CTRL_J] = Operation.VI_EDITING_MODE;
        map[CTRL_M] = Operation.VI_EDITING_MODE;
        map[CTRL_R] = Operation.REVERT_LINE;
        map[CTRL_Y] = Operation.YANK_NTH_ARG;
        map[CTRL_OB] = Operation.COMPLETE;
        map[CTRL_CB] = Operation.CHARACTER_SEARCH_BACKWARD;
        map[' '] = Operation.SET_MARK;
        map['#'] = Operation.INSERT_COMMENT;
        map['&'] = Operation.TILDE_EXPAND;
        map['*'] = Operation.INSERT_COMPLETIONS;
        map['-'] = Operation.DIGIT_ARGUMENT;
        map['.'] = Operation.YANK_LAST_ARG;
        map['<'] = Operation.BEGINNING_OF_HISTORY;
        map['='] = Operation.POSSIBLE_COMPLETIONS;
        map['>'] = Operation.END_OF_HISTORY;
        map['?'] = Operation.POSSIBLE_COMPLETIONS;
        for (int i = 'A'; i <= 'Z'; i++) {
            map[i] = Operation.DO_LOWERCASE_VERSION;
        }
        map['\\'] = Operation.DELETE_HORIZONTAL_SPACE;
        map['_'] = Operation.YANK_LAST_ARG;
        map['b'] = Operation.BACKWARD_WORD;
        map['c'] = Operation.CAPITALIZE_WORD;
        map['d'] = Operation.KILL_WORD;
        map['f'] = Operation.FORWARD_WORD;
        map['l'] = Operation.DOWNCASE_WORD;
        map['p'] = Operation.NON_INCREMENTAL_REVERSE_SEARCH_HISTORY;
        map['r'] = Operation.REVERT_LINE;
        map['t'] = Operation.TRANSPOSE_WORDS;
        map['u'] = Operation.UPCASE_WORD;
        map['y'] = Operation.YANK_POP;
        map['~'] = Operation.TILDE_EXPAND;
        map[DELETE] = Operation.BACKWARD_KILL_WORD;
        return new KeyMap(EMACS_META, map, false);
    }

    public static KeyMap viInsertion() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] ctrl = new Object[] {
                        // Control keys.
                        null,                               /* Control-@ */
                        Operation.SELF_INSERT,              /* Control-A */
                        Operation.SELF_INSERT,              /* Control-B */
                        Operation.SELF_INSERT,              /* Control-C */
                        Operation.VI_EOF_MAYBE,             /* Control-D */
                        Operation.SELF_INSERT,              /* Control-E */
                        Operation.SELF_INSERT,              /* Control-F */
                        Operation.SELF_INSERT,              /* Control-G */
                        Operation.BACKWARD_DELETE_CHAR,     /* Control-H */
                        Operation.COMPLETE,                 /* Control-I */
                        Operation.ACCEPT_LINE,              /* Control-J */
                        Operation.SELF_INSERT,              /* Control-K */
                        Operation.SELF_INSERT,              /* Control-L */
                        Operation.ACCEPT_LINE,              /* Control-M */
                        Operation.MENU_COMPLETE,            /* Control-N */
                        Operation.SELF_INSERT,              /* Control-O */
                        Operation.MENU_COMPLETE_BACKWARD,   /* Control-P */
                        Operation.SELF_INSERT,              /* Control-Q */
                        Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                        Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                        Operation.TRANSPOSE_CHARS,          /* Control-T */
                        Operation.UNIX_LINE_DISCARD,        /* Control-U */
                        Operation.QUOTED_INSERT,            /* Control-V */
                        Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                        Operation.SELF_INSERT,              /* Control-X */
                        Operation.YANK,                     /* Control-Y */
                        Operation.SELF_INSERT,              /* Control-Z */
                        Operation.VI_MOVEMENT_MODE,         /* Control-[ */
                        Operation.SELF_INSERT,              /* Control-\ */
                        Operation.SELF_INSERT,              /* Control-] */
                        Operation.SELF_INSERT,              /* Control-^ */
                        Operation.UNDO,                     /* Control-_ */
                };
        System.arraycopy( ctrl, 0, map, 0, ctrl.length );
        for (int i = 32; i < 256; i++) {
            map[i] = Operation.SELF_INSERT;
        }
        map[DELETE] = Operation.BACKWARD_DELETE_CHAR;
        return new KeyMap(VI_INSERT, map, false);
    }

    public static KeyMap viMovement() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] low = new Object[] {
                        // Control keys.
                        null,                               /* Control-@ */
                        null,                               /* Control-A */
                        null,                               /* Control-B */
                        Operation.INTERRUPT,                /* Control-C */
                        /*
                         * ^D is supposed to move down half a screen. In bash
                         * appears to be ignored.
                         */
                        Operation.VI_EOF_MAYBE,             /* Control-D */
                        Operation.EMACS_EDITING_MODE,       /* Control-E */
                        null,                               /* Control-F */
                        Operation.ABORT,                    /* Control-G */
                        Operation.BACKWARD_CHAR,            /* Control-H */
                        null,                               /* Control-I */
                        Operation.VI_MOVE_ACCEPT_LINE,      /* Control-J */
                        Operation.KILL_LINE,                /* Control-K */
                        Operation.CLEAR_SCREEN,             /* Control-L */
                        Operation.VI_MOVE_ACCEPT_LINE,      /* Control-M */
                        Operation.VI_NEXT_HISTORY,          /* Control-N */
                        null,                               /* Control-O */
                        Operation.VI_PREVIOUS_HISTORY,      /* Control-P */
                        /*
                         * My testing with readline is the ^Q is ignored.
                         * Maybe this should be null?
                         */
                        Operation.QUOTED_INSERT,            /* Control-Q */

                        /*
                         * TODO - Very broken.  While in forward/reverse
                         * history search the VI keyset should go out the
                         * window and we need to enter a very simple keymap.
                         */
                        Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                        /* TODO */
                        Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                        Operation.TRANSPOSE_CHARS,          /* Control-T */
                        Operation.UNIX_LINE_DISCARD,        /* Control-U */
                        /* TODO */
                        Operation.QUOTED_INSERT,            /* Control-V */
                        Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                        null,                               /* Control-X */
                        /* TODO */
                        Operation.YANK,                     /* Control-Y */
                        null,                               /* Control-Z */
                        emacsMeta(),                        /* Control-[ */
                        null,                               /* Control-\ */
                        /* TODO */
                        Operation.CHARACTER_SEARCH,         /* Control-] */
                        null,                               /* Control-^ */
                        /* TODO */
                        Operation.UNDO,                     /* Control-_ */
                        Operation.FORWARD_CHAR,             /* SPACE */
                        null,                               /* ! */
                        null,                               /* " */
                        Operation.VI_INSERT_COMMENT,        /* # */
                        Operation.END_OF_LINE,              /* $ */
                        Operation.VI_MATCH,                 /* % */
                        Operation.VI_TILDE_EXPAND,          /* & */
                        null,                               /* ' */
                        null,                               /* ( */
                        null,                               /* ) */
                        /* TODO */
                        Operation.VI_COMPLETE,              /* * */
                        Operation.VI_NEXT_HISTORY,          /* + */
                        Operation.VI_CHAR_SEARCH,           /* , */
                        Operation.VI_PREVIOUS_HISTORY,      /* - */
                        /* TODO */
                        Operation.VI_REDO,                  /* . */
                        Operation.VI_SEARCH,                /* / */
                        Operation.VI_BEGNNING_OF_LINE_OR_ARG_DIGIT, /* 0 */
                        Operation.VI_ARG_DIGIT,             /* 1 */
                        Operation.VI_ARG_DIGIT,             /* 2 */
                        Operation.VI_ARG_DIGIT,             /* 3 */
                        Operation.VI_ARG_DIGIT,             /* 4 */
                        Operation.VI_ARG_DIGIT,             /* 5 */
                        Operation.VI_ARG_DIGIT,             /* 6 */
                        Operation.VI_ARG_DIGIT,             /* 7 */
                        Operation.VI_ARG_DIGIT,             /* 8 */
                        Operation.VI_ARG_DIGIT,             /* 9 */
                        null,                               /* : */
                        Operation.VI_CHAR_SEARCH,           /* ; */
                        null,                               /* < */
                        Operation.VI_COMPLETE,              /* = */
                        null,                               /* > */
                        Operation.VI_SEARCH,                /* ? */
                        null,                               /* @ */
                        Operation.VI_APPEND_EOL,            /* A */
                        Operation.VI_PREV_WORD,             /* B */
                        Operation.VI_CHANGE_TO_EOL,         /* C */
                        Operation.VI_DELETE_TO_EOL,         /* D */
                        Operation.VI_END_WORD,              /* E */
                        Operation.VI_CHAR_SEARCH,           /* F */
                        /* I need to read up on what this does */
                        Operation.VI_FETCH_HISTORY,         /* G */
                        null,                               /* H */
                        Operation.VI_INSERT_BEG,            /* I */
                        null,                               /* J */
                        null,                               /* K */
                        null,                               /* L */
                        null,                               /* M */
                        Operation.VI_SEARCH_AGAIN,          /* N */
                        null,                               /* O */
                        Operation.VI_PUT,                   /* P */
                        null,                               /* Q */
                        /* TODO */
                        Operation.VI_REPLACE,               /* R */
                        Operation.VI_KILL_WHOLE_LINE,       /* S */
                        Operation.VI_CHAR_SEARCH,           /* T */
                        /* TODO */
                        Operation.REVERT_LINE,              /* U */
                        null,                               /* V */
                        Operation.VI_NEXT_WORD,             /* W */
                        Operation.VI_RUBOUT,                /* X */
                        Operation.VI_YANK_TO,               /* Y */
                        null,                               /* Z */
                        null,                               /* [ */
                        Operation.VI_COMPLETE,              /* \ */
                        null,                               /* ] */
                        Operation.VI_FIRST_PRINT,           /* ^ */
                        Operation.VI_YANK_ARG,              /* _ */
                        Operation.VI_GOTO_MARK,             /* ` */
                        Operation.VI_APPEND_MODE,           /* a */
                        Operation.VI_PREV_WORD,             /* b */
                        Operation.VI_CHANGE_TO,             /* c */
                        Operation.VI_DELETE_TO,             /* d */
                        Operation.VI_END_WORD,              /* e */
                        Operation.VI_CHAR_SEARCH,           /* f */
                        null,                               /* g */
                        Operation.BACKWARD_CHAR,            /* h */
                        Operation.VI_INSERTION_MODE,        /* i */
                        Operation.NEXT_HISTORY,             /* j */
                        Operation.PREVIOUS_HISTORY,         /* k */
                        Operation.FORWARD_CHAR,             /* l */
                        Operation.VI_SET_MARK,              /* m */
                        Operation.VI_SEARCH_AGAIN,          /* n */
                        null,                               /* o */
                        Operation.VI_PUT,                   /* p */
                        null,                               /* q */
                        Operation.VI_CHANGE_CHAR,           /* r */
                        Operation.VI_SUBST,                 /* s */
                        Operation.VI_CHAR_SEARCH,           /* t */
                        Operation.UNDO,                     /* u */
                        null,                               /* v */
                        Operation.VI_NEXT_WORD,             /* w */
                        Operation.VI_DELETE,                /* x */
                        Operation.VI_YANK_TO,               /* y */
                        null,                               /* z */
                        null,                               /* { */
                        Operation.VI_COLUMN,                /* | */
                        null,                               /* } */
                        Operation.VI_CHANGE_CASE,           /* ~ */
                        Operation.VI_DELETE                 /* DEL */
                };
        System.arraycopy( low, 0, map, 0, low.length );
        for (int i = 128; i < 256; i++) {
            map[i] = null;
        }
        return new KeyMap(VI_MOVE, map, false);
    }
}
