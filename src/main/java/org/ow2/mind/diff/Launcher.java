/**
 * Copyright (C) 2014 Schneider-Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Stephane Seyvoz
 * Contributors: 
 */


package org.ow2.mind.diff;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.error.Error;
import org.objectweb.fractal.adl.merger.NodeMerger;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.cli.CmdOptionBooleanEvaluator;
import org.ow2.mind.cli.CmdPathOption;
import org.ow2.mind.cli.CommandLine;
import org.ow2.mind.cli.CommandLineOptionExtensionHelper;
import org.ow2.mind.cli.InvalidCommandLineException;
import org.ow2.mind.diff.dot.DumpDotGenerator;
import org.ow2.mind.plugin.PluginManager;

import com.google.inject.Injector;

public class Launcher  extends org.ow2.mind.Launcher {

	protected static final String PROGRAM_NAME_PROPERTY_NAME = "minddiff.launcher.name";
	protected static final String ID_PREFIX                  = "org.ow2.mind.diff.";

	protected final CmdPathOption	baseSrcOpt 				= new CmdPathOption(
			ID_PREFIX + "BaseSrcPath",
			null,
			"base-src-path",
			"the search path of ADL,IDL and implementation files for BASE library",
			"<path list>");

	protected final CmdPathOption	headSrcOpt 				= new CmdPathOption(
			ID_PREFIX + "HeadSrcPath",
			null,
			"head-src-path",
			"the search path of ADL,IDL and implementation files for HEAD library",
			"<path list>");

	public static Logger			logger					= FractalADLLogManager.getLogger("visual-diff");

	// compiler components
	protected Injector            	baseInjector;
	protected Injector            	headInjector;

	protected Loader 				loaderItf;
	protected NodeFactory			nodeFactoryItf;
	protected NodeMerger 			nodeMergerItf;

	//-- diff-specific informations

	// base = origin, head = latest
	String baseAdlName 				= null; 
	String headAdlName 				= null;

	Map<Object, Object> baseContext = null;
	Map<Object, Object> headContext = null;

	//-- compiler configuration

	@Override
	protected void addOptions(final PluginManager pluginManagerItf) {
		options.addOptions(baseSrcOpt, headSrcOpt, helpOpt, versionOpt, extensionPointsListOpt);

		options.addOptions(CommandLineOptionExtensionHelper
				.getCommandOptions(pluginManagerItf));
	}

	@Override
	protected void init(final String... args) throws InvalidCommandLineException {
		if (logger.isLoggable(Level.CONFIG)) {
			for (final String arg : args) {
				logger.config("[arg] " + arg);
			}
		}

		/****** Initialization of the PluginManager Component *******/

		final Injector bootStrapPluginManagerInjector = getBootstrapInjector();
		final PluginManager pluginManager = bootStrapPluginManagerInjector
				.getInstance(PluginManager.class);

		addOptions(pluginManager);

		// parse arguments to a CommandLine.
		final CommandLine cmdLine = CommandLine.parseArgs(options, false, args);

		// will raise an exception on error
		checkExclusiveGroups(pluginManager, cmdLine);

		// If help is asked, print it and exit.
		if (helpOpt.isPresent(cmdLine)) {
			printHelp(System.out);
			System.exit(0);
		}

		// If version is asked, print it and exit.
		if (versionOpt.isPresent(cmdLine)) {
			printVersion(System.out);
			System.exit(0);
		}

		// Handle separate source-paths for the two component libraries
		if (baseSrcOpt.isPresent(cmdLine) && headSrcOpt.isPresent(cmdLine)) {

			baseContext = new HashMap<Object, Object>(compilerContext);
			headContext = new HashMap<Object, Object>(compilerContext);

			// Create a fake command-line for head and base, one per context
			final CommandLine baseCmdLine = CommandLine.parseArgs(options, false, args);
			final CommandLine headCmdLine = CommandLine.parseArgs(options, false, args);

			// Populate the 2 fake command lines with SrcPathOption with baseSrcOpt and headSrcOpt values

			// BASE
			CmdPathOption fakeSrcOptForBase = new CmdPathOption(
					org.ow2.mind.Launcher.ID_PREFIX + "SrcPath",
					"S",
					"src-path",
					"the search path of ADL,IDL and implementation files (list of path separated by ':' on Linux or ';' on Windows)",
					"&lt;path list&gt;");
			fakeSrcOptForBase.setValue(baseCmdLine, baseSrcOpt.getValue(cmdLine));


			// HEAD
			CmdPathOption fakeSrcOptForHead = new CmdPathOption(
					org.ow2.mind.Launcher.ID_PREFIX + "SrcPath",
					"S",
					"src-path",
					"the search path of ADL,IDL and implementation files (list of path separated by ':' on Linux or ';' on Windows)",
					"&lt;path list&gt;");
			fakeSrcOptForHead.setValue(headCmdLine, headSrcOpt.getValue(cmdLine));

			// to each context its own src-path
			baseContext.put(CmdOptionBooleanEvaluator.CMD_LINE_CONTEXT_KEY, baseCmdLine);
			headContext.put(CmdOptionBooleanEvaluator.CMD_LINE_CONTEXT_KEY, headCmdLine);

			// invokeOptionHandlers with both contexts
			invokeOptionHandlers(pluginManager, baseCmdLine, baseContext);
			invokeOptionHandlers(pluginManager, headCmdLine, headContext);

			// invoke for default context (pure conf)
			compilerContext
			.put(CmdOptionBooleanEvaluator.CMD_LINE_CONTEXT_KEY, cmdLine);
			invokeOptionHandlers(pluginManager, cmdLine, compilerContext);
		} else {
			// Expect standard --src-path
			compilerContext
			.put(CmdOptionBooleanEvaluator.CMD_LINE_CONTEXT_KEY, cmdLine);

			// invoke for default context
			invokeOptionHandlers(pluginManager, cmdLine, compilerContext);

			baseContext = new HashMap<Object, Object>(compilerContext);
			headContext = new HashMap<Object, Object>(compilerContext);
		}

		// get list of ADL
		adlToExecName = parserADLList(cmdLine.getArguments(), cmdLine);

		// initialize compiler
		initInjector(pluginManager, compilerContext);

		initCompiler();
	}

	/**
	 * Here we use the standard compiler initialization + A number of internals usually coming later.
	 */
	@Override
	protected void initCompiler() {
		// Standard init for errorManager and adlCompiler
		super.initCompiler();

		// Our additions
		loaderItf = injector.getInstance(Loader.class);
		nodeFactoryItf = injector.getInstance(NodeFactory.class);
		nodeMergerItf = injector.getInstance(NodeMerger.class);
	}

	/**
	 * 
	 */
	@Override
	public List<Object> compile(final List<Error> errors,
			final List<Error> warnings) throws InvalidCommandLineException {

		Definition resultDefinitionTree = null;

		Definition baseArchDef = null;
		Definition headArchDef = null;

		if (adlToExecName.size() == 0) {
			throw new InvalidCommandLineException("no definition name is specified.",
					1);
		} else if (adlToExecName.size() == 1) {
			// Same name for both sources
			baseAdlName = adlToExecName.keySet().toArray(new String[1])[0];
			headAdlName = baseAdlName;
		} else if (adlToExecName.size() == 2) {
			String[] defsNames = adlToExecName.keySet().toArray(new String[2]);
			baseAdlName = defsNames[0];
			headAdlName = defsNames[1];
		} else
			throw new InvalidCommandLineException("too many arguments were specified.",
					1);

		final List<Object> result = new ArrayList<Object>();

		logger.info("Launching graphical diff files generation...");
		logger.info("BASE: " + baseAdlName + " - HEAD: " + headAdlName);

		// load both definitions
		try {
			logger.info("Loading BASE architecture...");
			baseArchDef = loaderItf.load(baseAdlName, baseContext);
			logger.info("Loading HEAD architecture...");
			headArchDef = loaderItf.load(headAdlName, headContext);

			// Do the job
			logger.info("Starting component definition trees analysis...");
			ArchitecturesComparator archComparator = new ArchitecturesComparator(loaderItf, nodeFactoryItf, nodeMergerItf);
			resultDefinitionTree = archComparator.compareDefinitionTrees(baseArchDef, headArchDef, baseContext, headContext);
			logger.info("Finished.");
		} catch (ADLException e) {
			logger.severe("An error occured: ");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		//

		DumpDotGenerator dotGenerator = injector.getInstance(DumpDotGenerator.class);
		try {
			dotGenerator.generateDot(resultDefinitionTree, baseContext, headContext);
			logger.info("Successful.");
		} catch (ADLException e) {
			logger.severe("Error: could not generate .gv files !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (errors != null) errors.addAll(errorManager.getErrors());
		if (warnings != null) warnings.addAll(errorManager.getWarnings());

		return result;
	}

	@Override
	protected void printUsage(final PrintStream ps) {
		ps.println("Usage: ");
		ps.println("1) " + getProgramName()
				+ " [OPTIONS] <definition>");
		ps.println("  where <definition> is the name of a component to"
				+ " be compared between --base-src-path and --head-src-path versions");
		ps.println("2) " + getProgramName()
				+ " [OPTIONS] <baseDefinition> <headDefinition>");
		ps.println("  where <baseDefinition> and <headDefinition> are names of components to"
				+ " be compared either in a unique --src-path or between --base-src-path and --head-src-path");
		ps.println("Note: Base/Head versions == Old/New");
	}

	/**
	 * Entry point.
	 * 
	 * @param args
	 */
	public static void main(final String... args) {

		final Launcher l = new Launcher();

		try {
			l.init(args);
			l.compile(null, null);
		} catch (final InvalidCommandLineException e) {
			l.handleException(e);
		}

		if (!l.errorManager.getErrors().isEmpty()) System.exit(1);
	}
}
