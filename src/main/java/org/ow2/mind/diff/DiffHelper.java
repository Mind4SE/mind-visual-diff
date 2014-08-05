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

import org.objectweb.fractal.adl.Definition;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.Component;

public class DiffHelper {

	/** Decoration name */
	private static String isNowPrimitive = "is-now-primitive";
	/** Decoration name */
	private static String isNowComposite = "is-now-composite";

	/**
	 * Decorate the definition to reflect diff status:
	 * BASE definition was Composite, is now Primitive.
	 * @param definition
	 */
	public static void setDefinitionNowPrimitive(Definition definition) {
		definition.astSetDecoration(isNowPrimitive, true);
	}

	/**
	 * Decorate the definition to reflect diff status:
	 * BASE definition was Primitive, is now Composite.
	 * @param definition
	 */
	public static void setDefinitionNowComposite(Definition definition) {
		definition.astSetDecoration(isNowComposite, true);
	}
	
	public static boolean isNowPrimitive(Definition definition) {
		Object nowPrimitive = definition.astGetDecoration(isNowPrimitive);
		if (nowPrimitive != null)
			return ((Boolean) nowPrimitive).booleanValue();
		return false;
	}
	
	public static boolean isNowComposite(Definition definition) {
		Object nowComposite = definition.astGetDecoration(isNowComposite);
		if (nowComposite != null)
			return ((Boolean) nowComposite).booleanValue();
		return false;
	}
	
	/** Decoration name */
	private static String oldComponent = "is-old-component";

	/** Decoration name */
	private static String newComponent = "is-new-component";
	
	public static void setIsOldComponent(
			Component component) {
		component.astSetDecoration(oldComponent, true);
	}
	
	public static void setIsNewComponent(
			Component component) {
		component.astSetDecoration(newComponent, true);
	}

	public static boolean isOldComponent(Component component) {
		Object oldComponentDecoration = component.astGetDecoration(oldComponent);
		if (oldComponentDecoration != null)
			return ((Boolean) oldComponentDecoration).booleanValue();
		return false;
	}
	
	public static boolean isNewComponent(Component component) {
		Object newComponentDecoration = component.astGetDecoration(newComponent);
		if (newComponentDecoration != null)
			return ((Boolean) newComponentDecoration).booleanValue();
		return false;
	}
	
	/** Decoration name */
	private static String oldBinding = "is-old-binding";

	/** Decoration name */
	private static String newBinding = "is-new-binding";

	public static void setIsOldBinding(
			Binding binding) {
		binding.astSetDecoration(oldBinding, true);
	}
	
	public static void setIsNewBinding(
			Binding binding) {
		binding.astSetDecoration(newBinding, true);
	}
	
	public static boolean isOldBinding(Binding binding) {
		Object oldBindingDecoration = binding.astGetDecoration(oldBinding);
		if (oldBindingDecoration != null)
			return ((Boolean) oldBindingDecoration).booleanValue();
		return false;
	}
	
	public static boolean isNewBinding(Binding binding) {
		Object newBindingDecoration = binding.astGetDecoration(newBinding);
		if (newBindingDecoration != null)
			return ((Boolean) newBindingDecoration).booleanValue();
		return false;
	}
	
	/** Decoration name */
	private static String definitionChanged = "definition-changed";

	/**
	 * Decorate the definition to reflect diff status:
	 * BASE sub-component definition != HEAD sub-component definition.
	 * @param definition
	 */
	public static void setSubCompDefChanged(
			Component component) {
		component.astSetDecoration(definitionChanged, true);
	}
	
	public static boolean hasSubCompDefChanged(Component component) {
		Object definitionChangedDecoration = component.astGetDecoration(definitionChanged);
		if (definitionChangedDecoration != null)
			return ((Boolean) definitionChangedDecoration).booleanValue();
		return false;
	}

	/** Decoration name */
	private static String nowBindingContainer = "is-now-binding-container";

	/** Decoration name */
	private static String noMoreBindingContainer = "is-no-more-binding-container";

	public static void setDefNowBindingContainer(
			Definition definition) {
		definition.astSetDecoration(nowBindingContainer, true);
	}

	public static void setDefNoMoreBindingContainer(
			Definition definition) {
		definition.astSetDecoration(noMoreBindingContainer, true);
	}
	
	public static boolean isNowBindingContainer(Definition definition) {
		Object nowBindingContainerDecoration = definition.astGetDecoration(nowBindingContainer);
		if (nowBindingContainerDecoration != null)
			return ((Boolean) nowBindingContainerDecoration).booleanValue();
		return false;
	}
	
	public static boolean isNoMoreBindingContainer(Definition definition) {
		Object noMoreBindingContainerDecoration = definition.astGetDecoration(noMoreBindingContainer);
		if (noMoreBindingContainerDecoration != null)
			return ((Boolean) noMoreBindingContainerDecoration).booleanValue();
		return false;
	}
	
}
