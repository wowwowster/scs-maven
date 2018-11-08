#!/bin/sh

svcName=$1

if [ ! -d /etc/init.d ] 
then 
	echo "The service removal script is not supported on this platform (/etc/init.d does not exist)."
	exit 1
fi

if [ ! -x /etc/init.d/$svcName ] 
then 
	echo "The service startup script does not exist or is not runnable"
	exit 2
fi

if type chkconfig 2>/dev/null
then 
	#chkconfig mode
	usr_name=$(cat /etc/init.d/$svcName | grep -m 1 -P -o "(?<=su -s \\\$\(which sh\) -c \"\\\$CATALINA_HOME/bin/startup\.sh\" ).*")
	echo "Removing service (chkconfig mode)"
	chkconfig --del $svcName
	rm -f /etc/init.d/$svcName
	echo "Removing user $usr_name"
	userdel -f -r $usr_name
	groupdel $usr_name
	exit 0
elif type update-rc.d 2>/dev/null
then
	#update-rc.d mode
	usr_name=$(cat /etc/init.d/$svcName | grep -m 1 -P -o "(?<=su -s \\\$\(which sh\) -c \"\\\$CATALINA_HOME/bin/startup\.sh\" ).*")
	echo "Removing service (update-rc.d mode)"
	update-rc.d -f $svcName remove
	rm -f /etc/init.d/$svcName
	echo "Removing user $usr_name"
	userdel -f -r $usr_name
	groupdel $usr_name
	exit 0
else
	echo "The service removal script is not supported on this platform (neither chkconfig nor update-rc.d is supported)."
	exit 3
fi