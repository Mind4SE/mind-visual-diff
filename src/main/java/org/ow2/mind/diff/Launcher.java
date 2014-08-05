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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.NodeUtil;
import org.objectweb.fractal.adl.error.Error;
import org.objectweb.fractal.adl.merger.NodeMerger;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.CommonASTHelper;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.ImplementationContainer;
import org.ow2.mind.cli.CmdFlag;
import org.ow2.mind.cli.InvalidCommandLineException;
import org.ow2.mind.cli.Options;
import org.ow2.mind.diff.dot.DumpDotGenerator;
import org.ow2.mind.idl.IDLLoader;
import org.ow2.mind.io.OutputFileLocator;

public class Launcher  extends org.ow2.mind.Launcher {

	protected static final String PROGRAM_NAME_PROPERTY_NAME = "mindunit.launcher.name";
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

	protected static Logger			logger					= FractalADLLogManager.getLogger("mindunit");

	protected NodeFactory			nodeFactoryItf;
	protected Loader 				loaderItf;
	protected IDLLoader 			idlLoaderItf;
	protected OutputFileLocator 	outputFileLocatorItf;
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
		nodeFactoryItf = injector.getInstance(NodeFactory.class);
		loaderItf = injector.getInstance(Loader.class);
		idlLoaderItf = injector.getInstance(IDLLoader.class);
		outputFileLocatorItf = injector.getInstance(OutputFileLocator.class);
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

		logger.info("Launching graphical diff files generation... (base: " + baseAdlName + " - head: " + headAdlName + ")");

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
			resultDefinitionTree = compareDefinitionTrees(baseArchDef, headArchDef, baseContext, headContext);
		} catch (ADLException e) {
			logger.severe("Error: could not compare definitions !");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		//

		DumpDotGenerator dotGenerator = injector.getInstance(DumpDotGenerator.class);
		try {
			dotGenerator.generateDot(resultDefinitionTree, compilerContext);
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
	 * Create a new Definition based on the "HEAD" version (latest), enriched with
	 * information about created/removed sub nodes, for later serialization. 
	 * 
	 * @param baseArchDef the old definition
	 * @param headArchDef the new definition
	 * @param baseContext the compiler context for the old definition
	 * @param headContext the compiler context for the new definition
	 * @return
	 * @throws ADLException 
	 */
	private Definition compareDefinitionTrees(Definition baseArchDef,
			Definition headArchDef, Map<Object, Object> baseContext,
			Map<Object, Object> headContext) throws ADLException {

		// Create a new result definition
		Definition result = CommonASTHelper.newNode(nodeFactoryItf, "definition", Definition.class);
		result.setName(headArchDef.getName() + "_DiffResult");

		// Compare natures
		if (ASTHelper.isComposite(baseArchDef)) {
			if (ASTHelper.isComposite(headArchDef)) {
				// Both Composite

				// convert to ComponentContainer and directly use the merged type
				result = (Definition) CommonASTHelper.turnsTo(result, ComponentContainer.class, nodeFactoryItf, nodeMergerItf);

				// Are sub-components and bindings different ? (TODO: recursion)
				result = compareCompositesContent((ComponentContainer) baseArchDef, (ComponentContainer) headArchDef, baseContext, headContext, (ComponentContainer) result);
			} else {
				// Changed: Composite -> Primitive
				DiffHelper.setDefinitionNowPrimitive(result);
			}
		} else {
			if (ASTHelper.isComposite(headArchDef)) {
				// Changed: Primitive -> Composite
				DiffHelper.setDefinitionNowComposite(result);
			} else {
				// Both Primitive

				// convert to ImplementationContainer and directly use the modified type
				result = (Definition) CommonASTHelper.turnsTo(result, ImplementationContainer.class, nodeFactoryItf, nodeMergerItf);

				// Are sources and data different ?
				if (baseArchDef instanceof ImplementationContainer && headArchDef instanceof ImplementationContainer)
					comparePrimitivesContent((ImplementationContainer) baseArchDef, (ImplementationContainer) headArchDef, baseContext, headContext, (ImplementationContainer) result);
			}
		}

		// TODO: Compare provided and required interfaces

		return result;
	}



	private Definition compareCompositesContent(ComponentContainer baseArchDef,
			ComponentContainer headArchDef, Map<Object, Object> baseContext,
			Map<Object, Object> headContext, ComponentContainer result) throws ADLException {

		//-- 1) handle sub-components

		Component[] baseComponents = baseArchDef.getComponents();
		Component[] headComponents = headArchDef.getComponents();

		// modifiable lists to keep track of who was met, and who is remaining
		List<Component> baseComponentsList = new ArrayList<Component>(Arrays.asList(baseComponents));
		List<Component> headComponentsList = new ArrayList<Component>(Arrays.asList(headComponents));

		for (Component currHeadSubComponent : headComponents) {

			// Try to find the same sub-component in the original definition
			for (Component currBaseSubComponent : baseComponents) {
				if (currHeadSubComponent.getName().equals(currBaseSubComponent.getName())) {
					// Instance is common to BASE and HEAD

					// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
					// and treat them specially
					baseComponentsList.remove(currBaseSubComponent);
					headComponentsList.remove(currHeadSubComponent);

					// Are types equivalent ?
					Definition currHeadSubDef = ASTHelper.getResolvedComponentDefinition(currHeadSubComponent, loaderItf, headContext); // HEAD == Result (cloneTree)
					Definition currBaseSubDef = ASTHelper.getResolvedComponentDefinition(currBaseSubComponent, loaderItf, baseContext);

					// Add a clone of the sub-component in our new definition
					Component cloneComp = NodeUtil.cloneNode(currHeadSubComponent);
					result.addComponent(cloneComp);

					// If the common instance has a different definition, signal it
					if (!currHeadSubDef.getName().equals(currBaseSubDef.getName())) {
						DiffHelper.setSubCompDefChanged(cloneComp);
					}
				}
			}
		}

		// all the remaining referenced components exist in BASE but not in HEAD
		for (Component currComponent : baseComponentsList) {
			Component cloneComp = NodeUtil.cloneNode(currComponent);
			DiffHelper.setIsOldComponent(cloneComp);
			result.addComponent(cloneComp);
		}

		// all the remaining referenced components exist in HEAD but not in BASE
		for (Component currComponent : headComponentsList) {
			Component cloneComp = NodeUtil.cloneNode(currComponent);
			DiffHelper.setIsNewComponent(cloneComp);
			result.addComponent(cloneComp);
		}

		//-- 2) handle bindings

		if (baseArchDef instanceof BindingContainer) {
			if (headArchDef instanceof BindingContainer) {

				// Let's do the job
				result = (ComponentContainer) CommonASTHelper.turnsTo((Definition) result, BindingContainer.class, nodeFactoryItf, nodeMergerItf);
				compareBindings((BindingContainer) baseArchDef, (BindingContainer) headArchDef, baseContext, headContext, (BindingContainer) result);

			} else {
				// Was BindingContainer -> No more
				DiffHelper.setDefNoMoreBindingContainer((Definition) result);

				result = (ComponentContainer) CommonASTHelper.turnsTo((Definition) result, BindingContainer.class, nodeFactoryItf, nodeMergerItf);

				// Populate with old bindings
				for (Binding currBinding : ((BindingContainer) baseArchDef).getBindings()) {
					Binding cloneBinding = NodeUtil.cloneNode(currBinding);
					DiffHelper.setIsOldBinding(cloneBinding);
					((BindingContainer) result).addBinding(cloneBinding);
				}
			}
		} else {
			if (headArchDef instanceof BindingContainer) {
				// Was NOT BindingContainer -> Now is
				DiffHelper.setDefNowBindingContainer((Definition) result);

				result = (ComponentContainer) CommonASTHelper.turnsTo((Definition) result, BindingContainer.class, nodeFactoryItf, nodeMergerItf);

				// Populate with new bindings
				for (Binding currBinding : ((BindingContainer) headArchDef).getBindings()) {
					Binding cloneBinding = NodeUtil.cloneNode(currBinding);
					DiffHelper.setIsNewBinding(cloneBinding);
					((BindingContainer) result).addBinding(cloneBinding);
				}

			} else {
				// No change: none contain bindings
			}
		}

		return (Definition) result;

	}

	private void compareBindings(BindingContainer baseArchDef,
			BindingContainer headArchDef, Map<Object, Object> baseContext,
			Map<Object, Object> headContext, BindingContainer result) throws ADLException {

		Binding[] baseBindings = baseArchDef.getBindings();
		Binding[] headBindings = headArchDef.getBindings();

		// modifiable lists to keep track of who was met, and who is remaining
		List<Binding> baseBindingsList = new ArrayList<Binding>(Arrays.asList(baseBindings));
		List<Binding> headBindingsList = new ArrayList<Binding>(Arrays.asList(headBindings));
		
		for (Binding currHeadBinding : headBindings) {

			String headFromComponent 		= currHeadBinding.getFromComponent();
			String headFromInterface 		= currHeadBinding.getFromInterface();
			String headFromInterfaceNumber 	= currHeadBinding.getFromInterfaceNumber() != null ? currHeadBinding.getFromInterfaceNumber() : "";
			String headToComponent 			= currHeadBinding.getToComponent();
			String headToInterface 			= currHeadBinding.getToInterface();
			String headToInterfaceNumber 	= currHeadBinding.getToInterfaceNumber() != null ? currHeadBinding.getToInterfaceNumber() : "";
			
			// Try to find the same binding in the original definition
			for (Binding currBaseBinding : baseBindings) {

				String baseFromComponent 		= currBaseBinding.getFromComponent();
				String baseFromInterface 		= currBaseBinding.getFromInterface();
				String baseFromInterfaceNumber 	= currBaseBinding.getFromInterfaceNumber() != null ? currBaseBinding.getFromInterfaceNumber() : "" ;
				String baseToComponent 			= currBaseBinding.getToComponent();
				String baseToInterface 			= currBaseBinding.getToInterface();
				String baseToInterfaceNumber 	= currBaseBinding.getToInterfaceNumber() != null ? currBaseBinding.getToInterfaceNumber() : "" ;
				
				if (headFromComponent.equals(baseFromComponent)
						&& headFromInterface.equals(baseFromInterface)
						&& headFromInterfaceNumber.equals(baseFromInterfaceNumber)
						&& headToComponent.equals(baseToComponent)
						&& headToInterface.equals(baseToInterface)
						&& headToInterfaceNumber.equals(baseToInterfaceNumber)) {
				
					// Binding is common to BASE and HEAD

					// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
					// and treat them specially
					baseBindingsList.remove(currBaseBinding);
					headBindingsList.remove(currHeadBinding);

					// Add a clone of the binding in our new definition
					Binding cloneBinding = NodeUtil.cloneNode(currHeadBinding);
					result.addBinding(cloneBinding);
				}
			}
		}

		// all the remaining referenced bindings exist in BASE but not in HEAD
		for (Binding currBinding : baseBindingsList) {
			Binding cloneBinding = NodeUtil.cloneNode(currBinding);
			DiffHelper.setIsOldBinding(cloneBinding);
			result.addBinding(cloneBinding);
		}

		// all the remaining referenced components bindings in HEAD but not in BASE
		for (Binding currBinding : headBindingsList) {
			Binding cloneBinding = NodeUtil.cloneNode(currBinding);
			DiffHelper.setIsNewBinding(cloneBinding);
			result.addBinding(cloneBinding);
		}

	}

	private void comparePrimitivesContent(ImplementationContainer baseArchDef,
			ImplementationContainer headArchDef, Map<Object, Object> baseContext,
			Map<Object, Object> headContext, ImplementationContainer result) {
		// TODO Auto-generated method stub

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
