#!/bin/sh

chmod 777 logs
chmod 777 temp
find ./logs -print0 | xargs -0 chmod 777
find ./temp -print0 | xargs -0 chmod 777