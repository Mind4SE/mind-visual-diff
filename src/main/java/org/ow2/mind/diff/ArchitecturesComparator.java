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
import java.util.List;
import java.util.Map;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.NodeUtil;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.merger.NodeMerger;
import org.ow2.mind.CommonASTHelper;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.ast.ImplementationContainer;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.ast.Source;

public class ArchitecturesComparator {

	protected Loader 				loaderItf;
	protected NodeFactory			nodeFactoryItf;
	protected NodeMerger 			nodeMergerItf;


	public ArchitecturesComparator(Loader loaderItf, NodeFactory nodeFactoryItf, NodeMerger nodeMergerItf) {
		this.loaderItf = loaderItf;
		this.nodeFactoryItf = nodeFactoryItf;
		this.nodeMergerItf = nodeMergerItf;			
	}

	/**
	 * Create a new Definition based on the "HEAD" version (latest), enriched with
	 * information about created/removed sub nodes, for later serialization. 
	 * 
	 * @param baseArchDef the old definition
	 * @param headArchDef the new definition
	 * @param baseContext the compiler context for the old definition
	 * @param headContext the compiler context for the new definition
	 * @return the newly created definition, with merged nodes from head and base, decorated with diff information (@see DiffHelper for decorations primitives)
	 * @throws ADLException 
	 */
	public Definition compareDefinitionTrees(Definition baseArchDef,
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

				// Are sub-components and bindings different ?
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

				// test should always return true
				if (baseArchDef instanceof ImplementationContainer && headArchDef instanceof ImplementationContainer)
					result = comparePrimitivesContent((ImplementationContainer) baseArchDef, (ImplementationContainer) headArchDef, baseContext, headContext, (ImplementationContainer) result);
			}
		}

		if (baseArchDef instanceof InterfaceContainer) {
			if (headArchDef instanceof InterfaceContainer) {
				// Both

				// are provided and required interfaces different ?
				result = (Definition) CommonASTHelper.turnsTo(result, InterfaceContainer.class, nodeFactoryItf, nodeMergerItf);
				result = compareProvidedRequiredInterfaces((InterfaceContainer) baseArchDef, (InterfaceContainer) headArchDef, baseContext, headContext, (InterfaceContainer) result);
			} else {
				// convert to InterfaceContainer and directly use the modified type
				InterfaceContainer resultAsItfContainer = CommonASTHelper.turnsTo(result, InterfaceContainer.class, nodeFactoryItf, nodeMergerItf);
				result = (Definition) resultAsItfContainer;

				// all interfaces are old
				for (Interface currItf : ((InterfaceContainer) baseArchDef).getInterfaces()) {
					Interface cloneItf = NodeUtil.cloneNode(currItf);
					DiffHelper.setIsOldInterface(cloneItf);
					resultAsItfContainer.addInterface(cloneItf);
				}
			}
		} else {
			if (headArchDef instanceof InterfaceContainer) {
				// Changed: did not have interfaces -> Now does

				// convert to InterfaceContainer and directly use the modified type
				InterfaceContainer resultAsItfContainer = CommonASTHelper.turnsTo(result, InterfaceContainer.class, nodeFactoryItf, nodeMergerItf);
				result = (Definition) resultAsItfContainer;

				// all interfaces are new
				for (Interface currItf : ((InterfaceContainer) headArchDef).getInterfaces()) {
					Interface cloneItf = NodeUtil.cloneNode(currItf);
					DiffHelper.setIsNewInterface(cloneItf);
					resultAsItfContainer.addInterface(cloneItf);
				}
			} else {
				// None have interfaces, do nothing
			}
		}

		return result;
	}



	private Definition compareProvidedRequiredInterfaces(
			InterfaceContainer baseArchDef, InterfaceContainer headArchDef,
			Map<Object, Object> baseContext, Map<Object, Object> headContext,
			InterfaceContainer result) {

		Interface[] baseInterfaces = baseArchDef.getInterfaces();
		Interface[] headInterfaces = headArchDef.getInterfaces();

		// modifiable lists to keep track of who was met, and who is remaining
		List<Interface> baseInterfacesList = new ArrayList<Interface>(Arrays.asList(baseInterfaces));
		List<Interface> headInterfacesList = new ArrayList<Interface>(Arrays.asList(headInterfaces));

		for (Interface currHeadItf : headInterfaces) {
			MindInterface currHeadInterface = (MindInterface) currHeadItf;

			// Try to find the same sub-component in the original definition
			for (Interface currBaseItf : baseInterfaces) {
				MindInterface currBaseInterface = (MindInterface) currBaseItf;


				// Definitions would be needed for deeper comparisons
				//				IDL currBaseItfIDL = idlLoaderItf.load(currBaseInterface.getSignature(), baseContext);
				//				IDL currHeadItfIDL = idlLoaderItf.load(currHeadInterface.getSignature(), headContext);

				if (currHeadInterface.getName().equals(currBaseInterface.getName())) {
					// Instance is common to BASE and HEAD


					// Two interfaces can have the same instance name but having changed role ! (provided -> required / required -> provided)
					if (currHeadInterface.getRole().equals(currBaseInterface.getRole())) {

						// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
						// and treat them specially
						baseInterfacesList.remove(currBaseInterface);
						headInterfacesList.remove(currHeadInterface);

						// Add a clone of the sub-component in our new definition
						Interface cloneItf = NodeUtil.cloneNode(currHeadInterface);
						result.addInterface(cloneItf);

						// If the common instance has a different definition, signal it and do sub-diff
						if (!currBaseInterface.getSignature().equals(currBaseInterface.getSignature()))
							DiffHelper.setInterfaceDefinitionChanged(cloneItf);
					}

				}
			}
		}

		// all the remaining referenced components exist in BASE but not in HEAD
		for (Interface currInterface : baseInterfacesList) {
			Interface cloneItf = NodeUtil.cloneNode(currInterface);
			DiffHelper.setIsOldInterface(cloneItf);
			result.addInterface(cloneItf);
		}

		// all the remaining referenced components exist in HEAD but not in BASE
		for (Interface currInterface : headInterfacesList) {
			Interface cloneItf = NodeUtil.cloneNode(currInterface);
			DiffHelper.setIsNewInterface(cloneItf);
			result.addInterface(cloneItf);
		}

		return (Definition) result;
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

				// Definitions are needed for deeper comparisons
				Definition currBaseSubDef = ASTHelper.getResolvedComponentDefinition(currBaseSubComponent, loaderItf, baseContext);
				Definition currHeadSubDef = ASTHelper.getResolvedComponentDefinition(currHeadSubComponent, loaderItf, headContext);

				if (currHeadSubComponent.getName().equals(currBaseSubComponent.getName())) {
					// Instance is common to BASE and HEAD

					// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
					// and treat them specially
					baseComponentsList.remove(currBaseSubComponent);
					headComponentsList.remove(currHeadSubComponent);

					// Add a clone of the sub-component in our new definition
					Component cloneComp = NodeUtil.cloneNode(currHeadSubComponent);
					result.addComponent(cloneComp);

					// If the common instance has a different definition, signal it and do sub-diff
					if (!currHeadSubDef.getName().equals(currBaseSubDef.getName()))
						DiffHelper.setSubCompDefChanged(cloneComp);

					// Recursion
					// for all identical or modified sub-component definitions (but not the completely new or old)
					Definition subResultDef = compareDefinitionTrees(currBaseSubDef, currHeadSubDef, baseContext, headContext);

					ASTHelper.setResolvedComponentDefinition(cloneComp, subResultDef);
					DefinitionReference subResultDefRef = ASTHelper.newDefinitionReference(nodeFactoryItf, subResultDef.getName());
					ASTHelper.setResolvedDefinition(subResultDefRef, subResultDef);

					cloneComp.setDefinitionReference(subResultDefRef);
				}
			}
		}

		// all the remaining referenced components exist in BASE but not in HEAD
		for (Component currComponent : baseComponentsList) {
			Component cloneComp = NodeUtil.cloneNode(currComponent);
			DiffHelper.setIsOldComponent(cloneComp);
			result.addComponent(cloneComp);
			
			// post-treatment
			// we need to decorate all sub-nodes as being old as well
			Definition currBaseSubDef = ASTHelper.getResolvedComponentDefinition(cloneComp, loaderItf, baseContext);
			decorateAllSubNodesAsOld(currBaseSubDef, baseContext);
		}

		// all the remaining referenced components exist in HEAD but not in BASE
		for (Component currComponent : headComponentsList) {
			Component cloneComp = NodeUtil.cloneNode(currComponent);
			DiffHelper.setIsNewComponent(cloneComp);
			result.addComponent(cloneComp);
			
			// post-treatment
			// we need to decorate all sub-nodes as being new as well
			Definition currHeadSubDef = ASTHelper.getResolvedComponentDefinition(cloneComp, loaderItf, headContext);
			decorateAllSubNodesAsNew(currHeadSubDef, headContext);
		}

		//-- 2) handle bindings

		if (baseArchDef instanceof BindingContainer) {
			if (headArchDef instanceof BindingContainer) {

				// Let's do the job
				result = (ComponentContainer) CommonASTHelper.turnsTo((Definition) result, BindingContainer.class, nodeFactoryItf, nodeMergerItf);
				result = (ComponentContainer) compareBindings((BindingContainer) baseArchDef, (BindingContainer) headArchDef, baseContext, headContext, (BindingContainer) result);

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

	private void decorateAllSubNodesAsNew(Definition currHeadSubDef, Map<Object, Object> headContext) throws ADLException {
		
		if (currHeadSubDef instanceof InterfaceContainer)
			for (Interface currItf : ((InterfaceContainer) currHeadSubDef).getInterfaces())
				DiffHelper.setIsNewInterface(currItf);
		
		if (currHeadSubDef instanceof BindingContainer)
			for (Binding currBinding : ((BindingContainer) currHeadSubDef).getBindings())
				DiffHelper.setIsNewBinding(currBinding);
		
		if (currHeadSubDef instanceof ImplementationContainer)
			for (Source currSource : ((ImplementationContainer) currHeadSubDef).getSources())
				DiffHelper.setIsNewSource(currSource);
		
		if (currHeadSubDef instanceof ComponentContainer)
			for (Component currComp : ((ComponentContainer) currHeadSubDef).getComponents()) {
				DiffHelper.setIsNewComponent(currComp);
				Definition currCompDef = ASTHelper.getResolvedComponentDefinition(currComp, loaderItf, headContext);
				
				// recursion
				decorateAllSubNodesAsNew(currCompDef, headContext);
			}
		
	}

	private void decorateAllSubNodesAsOld(Definition currBaseSubDef, Map<Object, Object> baseContext) throws ADLException {
		
		if (currBaseSubDef instanceof InterfaceContainer)
			for (Interface currItf : ((InterfaceContainer) currBaseSubDef).getInterfaces())
				DiffHelper.setIsOldInterface(currItf);
		
		if (currBaseSubDef instanceof BindingContainer)
			for (Binding currBinding : ((BindingContainer) currBaseSubDef).getBindings())
				DiffHelper.setIsOldBinding(currBinding);
		
		if (currBaseSubDef instanceof ImplementationContainer)
			for (Source currSource : ((ImplementationContainer) currBaseSubDef).getSources())
				DiffHelper.setIsOldSource(currSource);
		
		if (currBaseSubDef instanceof ComponentContainer)
			for (Component currComp : ((ComponentContainer) currBaseSubDef).getComponents()) {
				DiffHelper.setIsOldComponent(currComp);
				Definition currCompDef = ASTHelper.getResolvedComponentDefinition(currComp, loaderItf, baseContext);
				
				// recursion
				decorateAllSubNodesAsOld(currCompDef, baseContext);
			}
		
	}

	private BindingContainer compareBindings(BindingContainer baseArchDef,
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

		return result;
	}

	private Definition comparePrimitivesContent(ImplementationContainer baseArchDef,
			ImplementationContainer headArchDef, Map<Object, Object> baseContext,
			Map<Object, Object> headContext, ImplementationContainer result) {

		Source[] baseSources = baseArchDef.getSources();
		Source[] headSources = headArchDef.getSources();

		// modifiable lists to keep track of who was met, and who is remaining
		List<Source> baseSourcesList = new ArrayList<Source>(Arrays.asList(baseSources));
		List<Source> headSourcesList = new ArrayList<Source>(Arrays.asList(headSources));

		for (Source currHeadSource : headSources) {

			// Try to find the same sub-component in the original definition
			for (Source currBaseSource : baseSources) {

				// file
				String currHeadSourcePath = currHeadSource.getPath();
				String currBaseSourcePath = currBaseSource.getPath();

				// inline
				String currHeadSourceCCode = currHeadSource.getCCode();
				String currBaseSourceCCode = currBaseSource.getCCode();

				if (currHeadSourcePath != null && currBaseSourcePath != null) {

					// only file name comparison, not content
					if (currHeadSource.getPath().equals(currBaseSourcePath)) {
						// Instance is common to BASE and HEAD

						// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
						// and treat them specially
						baseSourcesList.remove(currBaseSource);
						headSourcesList.remove(currHeadSource);

						// Add a clone of the sub-component in our new definition
						Source cloneItf = NodeUtil.cloneNode(currHeadSource);
						result.addSource(cloneItf);
					}
				} else {
					// with inline C code we do a full diff
					if (currHeadSourceCCode != null && currBaseSourceCCode != null) {
						// Instance is common to BASE and HEAD

						// Remove reference from list, so that in the end we know all the remaining ones (not found in HEAD)
						// and treat them specially
						baseSourcesList.remove(currBaseSource);
						headSourcesList.remove(currHeadSource);

						// Add a clone of the sub-component in our new definition
						Source cloneItf = NodeUtil.cloneNode(currHeadSource);
						result.addSource(cloneItf);
					}
				}
			}
		}

		// all the remaining referenced components exist in BASE but not in HEAD
		for (Source currSource : baseSourcesList) {
			Source cloneItf = NodeUtil.cloneNode(currSource);
			DiffHelper.setIsOldSource(cloneItf);
			result.addSource(cloneItf);
		}

		// all the remaining referenced components exist in HEAD but not in BASE
		for (Source currSource : headSourcesList) {
			Source cloneItf = NodeUtil.cloneNode(currSource);
			DiffHelper.setIsNewSource(cloneItf);
			result.addSource(cloneItf);
		}

		return (Definition) result;

	}

}
