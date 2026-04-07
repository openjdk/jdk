#!/usr/bin/env bash
# Bash completion for jcmd.
#
# Source this file from an interactive shell after bash-completion is loaded:
#   source /path/to/jcmd-completion.bash
#
# Notes:
# - First argument completes JVM PIDs, main class names, and the trailing
#   component of the main class name.
# - Second argument completes the diagnostic commands reported by
#   `jcmd <target> help`.
# - For `help`, the command list excludes `help` itself to avoid recursion.
# - `GC.heap_dump` gets a small option completer and falls back to file paths.

if ! type complete >/dev/null 2>&1; then
  return 0 2>/dev/null || exit 0
fi

_jcmd__dedupe_words() {
  awk '!seen[$0]++'
}

# list JVM processes on the local machine, except `jcmd` itself.
# Output format: "<pid> <main-class>\n"
function _jcmd__list_processes() {
  jcmd -l 2>/dev/null | awk '
    $1 ~ /^[0-9]+$/ && $2 != "jdk.jcmd/sun.tools.jcmd.JCmd" {
      print $1, $2
    }
  '
}

# query the list of commands known by the target JVM
_jcmd__list_commands_for_pid() {
  local -r pid=$1 # can also be a main class name

  jcmd "${pid}" help 2>/dev/null | awk '
    /^The following commands are available:/ { in_block=1; next }
    /^For more information/ { in_block=0 }
    in_block && $1 != "" { print $1 }
  '
}


_jcmd__complete_first_arg() {
  local -r cur=$1

  # start with general options
  local -a candidates=("-l" "--help")

  local line # <pid> <main-class>
  # IFS= preserves leading/trailing whitespace
  # -r prevents backslash escaping
  while IFS= read -r line; do
    # [[ -z $line ]] && continue
    local pid=${line%% *}
    local main_class=${line#* } # everything after first space
    candidates+=("$pid" "$main_class")
  done < <(_jcmd__list_processes)

  local -r wordlist=$(printf '%s\n' "${candidates[@]}" | _jcmd__dedupe_words | tr '\n' ' ')
  COMPREPLY=( $(compgen -W "$wordlist" -- "$cur") )
}


_jcmd__complete_command() {
  local -r target=$1
  local -r cur=$2

  local -a commands=("-f")

  local cmd
  while IFS= read -r cmd; do
    [[ -z $cmd ]] && continue
    commands+=("$cmd")
  done < <(_jcmd__list_commands_for_pid "$target")

  printf -v wordlist '%s ' "${commands[@]}"
  COMPREPLY=( $(compgen -W "$wordlist" -- "$cur") )
}


_jcmd__complete_help_command() {
  local -r target=$1
  local -r cur=$2

  local -a commands=()

  local cmd
  while IFS= read -r cmd; do
    [[ -z $cmd || $cmd == help ]] && continue
    commands+=("$cmd")
  done < <(_jcmd__list_commands_for_pid "$target")

  printf -v wordlist '%s ' "${commands[@]}"
  COMPREPLY=( $(compgen -W "$wordlist" -- "$cur") )
}


_jcmd_completion() {
  COMPREPLY=()

  # latest argument, potentially incomplete
  local -r cur=${COMP_WORDS[COMP_CWORD]}

  #  COMP_WORDS
  #         An array variable consisting of the individual words in the current command line.
  #         The line is split into words as `readline` would split it, using COMP_WORDBREAKS.
  #         A word is considered complete once it is followed by a space.
  #  COMP_CWORD
  #         An index into ${COMP_WORDS} of the word containing the current cursor position.
  #  Examples:
  #         "jcmd "         -> COMP_CWORD = 1, cur = ""
  #         "jcmd MyA"      -> COMP_CWORD = 1, cur = "MyA"
  #         "jcmd MyApp"    -> COMP_CWORD = 1, cur = "MyApp"
  #         "jcmd MyApp "   -> COMP_CWORD = 2, cur = ""

  # First argument: PID or main class (or -jar TODO(Ivan))
  if [[ ${COMP_CWORD} -eq 1 ]]; then
    _jcmd__complete_first_arg "$cur"
    return 0
  fi

  # target java process ID (always a first argument)
  # local java_process_pid=${COMP_WORDS[1]}
  local -r target=${COMP_WORDS[1]}

  # Second argument: diagnostic command.
  if [[ ${COMP_CWORD} -eq 2 ]]; then
    _jcmd__complete_command "$target" "$cur"
    return 0
  fi


  if [[ ${COMP_CWORD} -eq 3 ]]; then
    if [[ ${COMP_WORDS[2]} == help ]] then
      _jcmd__complete_help_command "$target" "$cur"
    elif [[ ${COMP_WORDS[2]} == "-f" ]]; then
      # autocomplete filename
      compopt -o filenames 2>/dev/null
      COMPREPLY=( $(compgen -f -- "$cur") )
    fi
    return 0
  fi

}


complete -o nosort -F _jcmd_completion jcmd
