#!/usr/bin/perl
# author V.Varbanovski @nu11secur1ty
use strict;
use warnings;
use diagnostics;


my $string = "Leshta i salata\nTarator s mastika";
      my($start) = ($string =~ /\A(.*?) /);
	our @lines = $string =~ /^(.*?) /gm;
print "First word: $start\n","Line starts: @lines\n";
