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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.error.Error;
import org.objectweb.fractal.adl.merger.NodeMerger;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.cli.CmdFlag;
import org.ow2.mind.cli.InvalidCommandLineException;
import org.ow2.mind.cli.Options;
import org.ow2.mind.diff.dot.DumpDotGenerator;

public class Launcher  extends org.ow2.mind.Launcher {

	protected static final String PROGRAM_NAME_PROPERTY_NAME = "minddiff.launcher.name";
	protected static final String ID_PREFIX                  = "org.ow2.mind.unit.test.";

	protected final CmdFlag 		helpOpt					= new CmdFlag(
			ID_PREFIX
			+ "Help",
			"h", "help",
			"Print this help and exit");

	protected final CmdFlag 		versionOpt				= new CmdFlag(
			ID_PREFIX
			+ "Version",
			"v", "version",
			"Print version number and exit");

	protected final CmdFlag 		extensionPointsListOpt	= new CmdFlag(
			ID_PREFIX
			+ "PrintExtensionPoints",
			null,
			"extension-points",
			"Print the list of available extension points and exit.");

	protected final Options 		options					= new Options();

	public static Logger			logger					= FractalADLLogManager.getLogger("visual-diff");

	protected Loader 				loaderItf;
	protected NodeFactory			nodeFactoryItf;
	protected NodeMerger 			nodeMergerItf;

	//-- diff-specific informations

	// base = origin, head = latest
	String baseAdlName 				= null; 
	String headAdlName 				= null;

	//-- compiler configuration

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

		// Create copies for separate manipulations and loading
		// This allows loading the same fully-qualified-name definition from different sources
		Map<Object, Object> baseContext = new HashMap<Object, Object>(compilerContext);
		Map<Object, Object> headContext = new HashMap<Object, Object>(compilerContext);

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

		// Note: How to handle separate source-paths for the two component libraries ?
		// TODO: introduce --base-src-path and --head-src-path

		final List<Object> result = new ArrayList<Object>();

		logger.info("Launching graphical diff files generation...");
		logger.info("BASE: " + baseAdlName + " - HEAD: " + headAdlName);

		// load both definitions
		try {
			baseArchDef = loaderItf.load(baseAdlName, baseContext);
		} catch (ADLException e) {
			logger.severe("Error: could not load BASE definition");
		}

		try {
			headArchDef = loaderItf.load(headAdlName, headContext);
		} catch (ADLException e) {
			logger.severe("Error: could not load HEAD definition");
		}

		// Do the job
		try {
			logger.info("Starting component definition trees analysis...");
			ArchitecturesComparator archComparator = new ArchitecturesComparator(loaderItf, nodeFactoryItf, nodeMergerItf);
			resultDefinitionTree = archComparator.compareDefinitionTrees(baseArchDef, headArchDef, baseContext, headContext);
			logger.info("Finished.");
		} catch (ADLException e) {
			logger.severe("Error: could not compare definitions !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		//

		DumpDotGenerator dotGenerator = injector.getInstance(DumpDotGenerator.class);
		try {
			dotGenerator.generateDot(resultDefinitionTree, compilerContext);
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
