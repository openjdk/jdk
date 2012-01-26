#

# section 0 = [PID]:
# section 1 = "The following commands are available:"
# section 2 = <list of commands, one command per line>
# section 3 = blank line
# section 4 = "For more information about a specific command use 'help <command>'."

BEGIN	{
    totallines=0; matched=0; section=0;
}

# match the first line (PID of the JVM followed by ':')
/^[0-9]+:/{
    if(section==0) {
	matched++;
	section=1;
    }
}

/^The following commands are available:$/{
    if(section==1) {
	matched++;
	section=2;
    }
}

# match a command name
/^[a-z|A-Z][a-z|A-Z|0-9|\.|_]*$/{
    if(section==2) {
	matched++;
    }
}

/^$/{
    if(section==2) {
	matched++;
	section=4;
    }
}

/^For more information about a specific command use 'help <command>'\.$/{
    if(section==4) {
	matched++;
	section=5;
    }
}

{ totallines++; print $0 }

END {
    if ((totallines > 0) && (matched == totallines)) {
	exit 0
    }
    else {
	exit 1
    }
}
