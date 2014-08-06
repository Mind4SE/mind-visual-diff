/**
 * Copyright (C) 2014 Schneider Electric
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
 * Authors: Julien TOUS
 * Contributors: Stéphane Seyvoz
 */

package org.ow2.mind.diff.dot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Map;
import java.util.TreeSet;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.ast.Source;
import org.ow2.mind.adl.implementation.ImplementationLocator;
import org.ow2.mind.diff.DiffHelper;

import com.google.inject.Inject;


public class DotWriter {

	/**
	 * The PrintWriter that will be used for all code generation of this component.
	 */
	private PrintWriter currentPrinter;
	/**
	 * The instance name of this component :
	 * containing all levels of composite from the top level component
	 */
	private String compName;
	/**
	 * The instance name of this component :
	 * as stated on the "contains" line in ADL
	 */
	private String localName;
	/**
	 * The directory where the graphviz files will be located
	 */
	private String buildDir;
	/**
	 * The graphviz file for this component
	 */
	private String fileName;
	/**
	 * The graphviz source code string that will represent the sources 
	 */
	private String srcs="{\ncolor=none;\n";
	/**
	 * A counter for the number of source files
	 */
	private int srcNb=0;
	/**
	 * The graphviz source code string that will represent the server interfaces 
	 */
	private String srvItfs="{rank=source; color=none; ";
	/**
	 * A counter for the number of server interfaces
	 */
	private int srvItfsNb=0;
	/**
	 * The graphviz source code string that will represent the client interfaces 
	 */
	private String cltItfs="{rank=sink; color=none; ";
	/**
	 * A counter for the number of client interfaces
	 */
	private int cltItfsNb=0;
	/**
	 * Either the same as srvItfsNb or cltItfsNb
	 * Used to adapt the size of composite interface boxes
	 */
	private int maxItf=0;
	/**
	 * A graphviz color identifier
	 * Used for the edges (helps visual identification)
	 */
	private int color=1;
	private Map<Object, Object> context;

	/**
	 * Key used for Named Google Guice binding
	 */
	public static final String DUMP_DOT = "DumpDot";

	@Inject
	public ImplementationLocator implementationLocatorItf;

	@Inject
	Loader adlLoaderItf;

	/**
	 * Initialize the DotWriter with the associated instance info 
	 * @param dir the build directory for the output file
	 * @param name the full instance name (path in the instance diagram)
	 * @param component The "type" of the component
	 * @param cont the context
	 */
	public void init(String dir, String name, Component component, Map<Object, Object> context) {
		this.context = context;
		try {
			compName = name;
			final int i = name.lastIndexOf('.');
			if (i == -1 ) { 
				localName = name;
			} else {
				localName = name.substring(i + 1);
			}
			buildDir = dir;
			fileName = buildDir + File.separator + compName + ".gv";
			currentPrinter = new PrintWriter( new FileWriter( fileName ) );
			String adlSource = null;
			if (component!=null)
				try {
					//get adlSource in the form /absolute/path/comp.adl:[line,column]-[line,column]
					adlSource = ASTHelper.getResolvedDefinition(component.getDefinitionReference(), adlLoaderItf, context).astGetSource();
					//removing line information. (using lastIndexOf instead of split[0] as ":" is a valid path character)
					if (adlSource != null) // Do  not test os if the source is null 
					{
						if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
							adlSource = adlSource.substring(1,adlSource.lastIndexOf(":"));
						} else {
							//Somehow windows paths come here with an extra "/" in front of the Drive letter.
							adlSource = adlSource.substring(0,adlSource.lastIndexOf(":"));
						}
					}
				} catch (ADLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			writeHeader(adlSource);
		} catch ( final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Write the header of the graphviz source code
	 * @param adlSource The ADL file describing the component 
	 */
	private void writeHeader(String adlSource) {
		currentPrinter.println("digraph " + localName + " {");
		currentPrinter.println("rankdir=LR;");
		currentPrinter.println("ranksep=3;");
		currentPrinter.println("subgraph cluster_membrane {" );
		if (adlSource != null) currentPrinter.println("URL=\"" + adlSource + "\"");
		currentPrinter.println("penwidth=15;");
		currentPrinter.println("color=blue;");
		currentPrinter.println("style=rounded;");
		//currentPrinter.println("height=20;"); // max number of itf /50*18 


	}

	/**
	 * Write the graphviz source code for a contained subcomponent 
	 * @param component the subcomponent
	 */
	public void addSubComponent(Component component) {
		try {
			int clientItf = 0;
			int serverItf = 0;
			DefinitionReference defRef = component.getDefinitionReference();
			final Definition definition = ASTHelper.getResolvedDefinition(defRef, adlLoaderItf, context);
			
			// default
			String color = "black";
			if (DiffHelper.isNewComponent(component))
				color = "chartreuse3";
			else if (DiffHelper.isOldComponent(component))
				color = "red3";
			else if (DiffHelper.hasSubCompDefChanged(component))
				color = "darkgoldenrod2";
			
			currentPrinter.print(component.getName() + "Comp [URL=\"" + compName + "." + component.getName() + ".gv\",shape=Mrecord,style=filled,fillcolor=lightgrey,color=" + color + ",label=\"" + component.getName() + " | {{ " );
			if (definition instanceof InterfaceContainer) {

				TreeSet<MindInterface> interfaces = new TreeSet<MindInterface>(new MindInterfaceComparator());
				for (Interface itf : ((InterfaceContainer) definition).getInterfaces())
					interfaces.add((MindInterface) itf); 
				//final Interface[] interfaces = ((InterfaceContainer) definition).getInterfaces();
				//			for (int i = 0; i < interfaces.length; i++) {
				//				final MindInterface itf = (MindInterface) interfaces[i];
				for (MindInterface itf : interfaces) {
					if (itf.getRole().equals(TypeInterface.SERVER_ROLE)) {
						if ( serverItf !=0 ) currentPrinter.print(" | ");
						currentPrinter.print("<" + itf.getName() + "> " + itf.getName());
						serverItf++;
						//itf.getSignature()); //TODO might put this info somwhere latter
					}
				}
				currentPrinter.print(" } | | { ");
				//			for (int i = 0; i < interfaces.length; i++) {
				//				final MindInterface itf = (MindInterface) interfaces[i];	
				for (MindInterface itf : interfaces) {	
					if (itf.getRole().equals(TypeInterface.CLIENT_ROLE)) {
						if ( clientItf !=0 ) currentPrinter.print(" | ");
						currentPrinter.print("<" + itf.getName() + "> " + itf.getName());
						clientItf++;
						//itf.getSignature());
					}
				}
				currentPrinter.print(" }} | \" ];");
				currentPrinter.println("");
				if (clientItf > maxItf) maxItf = clientItf;
				if (serverItf > maxItf) maxItf = serverItf;
			}
		} catch (final ADLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Add a binding to the graphviz source code
	 * @param binding : the Binding
	 */
	public void addBinding(Binding binding) {
		
		color = 9;
		if (DiffHelper.isNewBinding(binding))
			color = 3;
		else if (DiffHelper.isOldBinding(binding))
			color = 1;
		
		String fc = binding.getFromComponent();
		String fi = binding.getFromInterface();
		String tc = binding.getToComponent();
		String ti = binding.getToInterface();
		String from = null;
		String to = null;
		if (fc.equals("this"))
			from = "Srv" + fi;
		else
			from = fc + "Comp:" + fi;

		if (tc.equals("this")) 
			to = "Clt" + ti;
		else
			to = tc + "Comp:" + ti;
		currentPrinter.println( from + "->" + to + "[colorscheme=\"set19\" color=" + color + "];");	
	}

	/**
	 * Add a source file in  the graphviz source code 
	 * @param source : the source File
	 */
	public void addSource(Source source) {
		String srcPath=source.getPath();
		if (srcPath != null) {
			URL url = implementationLocatorItf.findSource(srcPath, context);
			String s;
			File f; f = new File( url.getPath() );
			
			String color = "black";
			if (DiffHelper.isNewSource(source))
				color = "chartreuse3";
			else if (DiffHelper.isOldSource(source))
				color = "red3";
			
			s = "\", URL=\"" + f.getAbsolutePath() + "\"";
			srcs=srcs + srcNb + "[shape=note,label=\"" + source.getPath() + s + ",color="+ color +"];\n";
			srcNb++;
		}
	}

	/**
	 * Add a server interface to the graphviz source code.
	 * @param itfName : the name of the interface instance (as on the "provides" line in ADL)
	 * @param itfURI : the source file path for the .itf file.
	 */
	public void addServer(String itfName, String itfURI, String color) {
		srvItfs=srvItfs + "Srv" + itfName + " [shape=record,style=filled,fillcolor=firebrick2,penwidth=2,color=" + color + ",label=\"" + itfName + "\", URL=\"" + itfURI + "\", height=1 ];";
		srvItfsNb++;
	}

	/**
	 * Add a client interface to the graphviz source code.
	 * @param itfName : the name of the interface instance (as on the "requires" line in ADL)
	 * @param itfURI : the source file path for the .itf file.
	 * @param color 
	 */
	public void addClient(String itfName, String itfURI, String color) {
		cltItfs=cltItfs + "Clt" + itfName + " [shape=record,style=filled,fillcolor=palegreen,penwidth=2,color=" + color + ",label=\"" + itfName + "\", URL=\"" + itfURI + "\", height=1 ];";
		cltItfsNb++;	
	}

	public void close() {
		writeFooter();

	}

	/**
	 * Write the footer for the graphviz source file.
	 * (Closes the opened structures)
	 */
	private void writeFooter() {
		if (cltItfsNb > maxItf) maxItf=cltItfsNb;
		if (srvItfsNb > maxItf) maxItf=srvItfsNb;
		if (srcNb > maxItf) maxItf=srcNb;
		srvItfs=srvItfs + "}";
		cltItfs=cltItfs + "}";
		srcs=srcs + "}\n";
		if (srvItfsNb > 0) currentPrinter.println(srvItfs);
		if (cltItfsNb > 0) currentPrinter.println(cltItfs);
		if (srcNb > 0) currentPrinter.println(srcs);
		currentPrinter.println("}");
		currentPrinter.println("}");
		currentPrinter.close();
	}

}
