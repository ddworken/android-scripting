# NOTE: Derived from ../../lib/../xlib/arm/POSIX.pm.
# Changes made here will be lost when autosplit is run again.
# See AutoSplit.pm.
package POSIX;

#line 447 "../../lib/../xlib/arm/POSIX.pm (autosplit into ../../lib/../xlib/arm/auto/POSIX/exit.al)"
sub exit {
    usage "exit(status)" if @_ != 1;
    CORE::exit($_[0]);
}

# end of POSIX::exit
1;
