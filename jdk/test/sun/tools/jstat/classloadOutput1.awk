#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
# Loaded       Time Inited       Time Shared  Kbytes   LoadTime SysClass  Kbytes   LoadTime     Lookup      Parse Linked       Time Verified       Time AppClass  Kbytes      AppCL DefineClass       Time FindClass       Time Delegation URLCL Read
#    956      0.115    777      0.032      0     0.0      0.000      956  3437.5      0.085      0.013      0.045    918      0.032      917      0.011       13     1.0      0.003           1      0.000         1      0.004      0.005      0.000
#

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^Loaded       Time Inited       Time Shared  Kbytes   LoadTime SysClass  Kbytes   LoadTime     Lookup      Parse Linked       Time Verified       Time AppClass  Kbytes      AppCL DefineClass       Time FindClass       Time Delegation URLCL Read$/ {
	    headerlines++;
	}

/^[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9][ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9][ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9][ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/ {
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1) && (totallines == 2)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
