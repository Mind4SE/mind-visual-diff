
@REM  Copyright (C) 2014 Schneider Electric
@REM
@REM  This file is part of "Mind Compiler" is free software: you can redistribute 
@REM  it and/or modify it under the terms of the GNU Lesser General Public License 
@REM  as published by the Free Software Foundation, either version 3 of the 
@REM  License, or (at your option) any later version.
@REM 
@REM  This program is distributed in the hope that it will be useful, but WITHOUT 
@REM  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
@REM  FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
@REM  details.
@REM 
@REM  You should have received a copy of the GNU Lesser General Public License
@REM  along with this program.  If not, see <http://www.gnu.org/licenses/>.
@REM 
@REM  Contact: mind@ow2.org
@REM 
@REM  Authors: Stephane Seyvoz
@REM  Contributors: Schneider Electric Mind4SE
@REM -----------------------------------------------------------------------------
@REM
@REM Optional ENV vars
@REM -----------------
@REM   MIND_OPTS - parameters passed to the Java VM running the mind compiler
@REM     e.g. to specify logging levels, use
@REM       set MIND_OPTS=-Ddefault.console.level=FINE -Ddefault.file.level=FINER
@REM   See documentation for more detail on logging system.

@echo off
setlocal

@REM use the batch path to determine MIND_HOME
pushd %~dp0..\
set MIND_HOME=%cd%
popd

set MIND_RUNTIME=%MIND_HOME%\runtime

@REM Launcher class name
set LAUNCHER=org.ow2.mind.diff.Launcher
@rem echo %~dp0\jar_launcher.bat %LAUNCHER% %MIND_OPTS% -Dmindc.launcher.name=mind-diff %LAUNCHER% %* --base-src-path=%MIND_RUNTIME% --head-src-path=%MIND_RUNTIME%
%~dp0\jar_launcher.bat %LAUNCHER% %MIND_OPTS% -Dmindc.launcher.name=mindc-diff %LAUNCHER% %* --base-src-path=%MIND_RUNTIME% --head-src-path=%MIND_RUNTIME%
