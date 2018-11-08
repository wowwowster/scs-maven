@ECHO off

CACLS .\logs /T /C /G Users:F
CACLS .\temp /T /C /G Users:F