
	.inline _getReturnAddr,0
	! Warning: don't use in leaf methods - their ret addr is not in %i7.
	mov		%i7, %o0
	.end


	.inline	_flushInstruction,1
	flush	%o0
	stbar
	.end
