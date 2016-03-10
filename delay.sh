#!/bin/bash

case $1 in
	add)
		sudo tc qdisc add dev lo root netem delay $2
		;;
	change)
		sudo tc qdisc change dev lo root netem delay $2
		;;
	remove)
		sudo tc qdisc del dev lo root netem
		;;
	display)
		sudo ping 127.0.0.1
		;;
	*)
		echo -e "Usage:\n\tadd <num+ms>\n\tchange <num+ms>\n\tremove\n\tdisplay\n\t*NOTE: must be a sudoer. Don't need to run script with sudo as it is included in command"
		;;
esac
