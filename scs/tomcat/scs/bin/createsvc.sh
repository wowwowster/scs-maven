#!/bin/sh

svcName=$1

configureUserAndPerms()
{
	
	id $svcName 2>&1
	if [ $? -ne 0 ]
	then 
		#user does not exist
		useradd -r -s /bin/false $svcName
	fi
	
	echo "Setting permissions for $svcName on server directory"
	chown $svcName.$svcName -R .
	chown root.root "/etc/init.d/$svcName"
	chmod 744 "/etc/init.d/$svcName"
	find . -print0 | xargs -0 chmod 744
	find . -type d -print0 | xargs -0 chmod 755
	chmod 755 setup.sh
	chmod 777 logs
	chmod 777 temp
	chmod 777 work

}

if [ ! -d /etc/init.d ] 
then 
	echo "The service removal script is not supported on this platform (/etc/init.d does not exist)."
	return 1
fi


if type chkconfig >/dev/null 2>&1
then
	#chkconfig mode
	configureUserAndPerms
	echo "Creating service (chkconfig mode)"
	chkconfig --add $svcName
	chkconfig --level 345 $svcName on
elif type update-rc.d >/dev/null 2>&1
then
	#update-rc.d mode
	configureUserAndPerms
	echo "Creating service (update-rc.d mode)"
	update-rc.d $svcName defaults
else
	echo "The service creation script is not supported on this platform. Please configure auto-startup manually"
	return 2
fi
