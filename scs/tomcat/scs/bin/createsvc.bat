@echo off

rem CD to cur dir (short named)
%~d0
cd %~sdp0
cd ..
cd ..
SET CUR_DIR=%CD%

SET JAVA_HOME=%CUR_DIR%\jdk
SET CATALINA_HOME=%CUR_DIR%
SET CATALINA_BASE=%CUR_DIR%
CALL bin\service.bat install %1
