/*
 * #%L
 * JSR-223-compliant Jython scripting language plugin.
 * %%
 * Copyright (C) 2008 - 2017 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.scripting.jython;

import org.python.core.*;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.AdaptedScriptLanguage;
import org.scijava.script.ScriptLanguage;

import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * An adapter of the Jython interpreter to the SciJava scripting interface.
 *
 * @author Johannes Schindelin
 * @author Mark Hiner
 * @see ScriptEngine
 */
@Plugin(type = ScriptLanguage.class, name = "Python")
public class JythonScriptLanguage extends AdaptedScriptLanguage {

	@Parameter
	private Context context;

	private Map<Class, String> parameters = new HashMap<>();

	public JythonScriptLanguage() {
		super("jython");
	}

	@Override
	public Object decode(final Object object) {
		if (object instanceof PyNone) return null;
		if (object instanceof PyBoolean) {
			return ((PyBoolean) object).getBooleanValue();
		}
		if (object instanceof PyInteger) {
			return ((PyInteger) object).getValue();
		}
		if (object instanceof PyFloat) {
			return ((PyFloat) object).getValue();
		}
		if (object instanceof PyString) {
			return ((PyString) object).getString();
		}
		if (object instanceof PyObject) {
			// Unwrap Python objects when they wrap Java ones.
			final PyObject pyObj = (PyObject) object;
			final Class<?> javaType = pyObj.getType().getProxyType();
			if (javaType != null) return pyObj.__tojava__(javaType);
		}
		return object;
	}

	@Override
	public void registerParameter(Class objectClass, String objectVariableName) {
		if(!parameters.containsKey(objectClass)) parameters.put(objectClass, objectVariableName);
	}

	@Override
	public String encodeParameter(Class objectClass) {
		return "# @" + objectClass.getSimpleName() + " " + getScriptParameter(objectClass);
	}

	private String getScriptParameter(Class objectClass) {
		if(!parameters.containsKey(objectClass)) throw new NullPointerException("Parameter of type " + objectClass + " not known to language. Use registerParameter() first.");
		return parameters.get(objectClass);
	}

	@Override
	public String encodeUnknownVariable(final String variable)
	{
		return variable + " = ?";
	}

	@Override
	public String encodeVariableFromService(String variableName, String serviceVariableName, final String serviceMethodName)
	{
		return variableName + " = " + serviceVariableName + "." + serviceMethodName + "()";
	}

	@Override
	public String encodeModuleCall(final String moduleName, boolean process, Map<String, Object> inputs, Map<String, String> outputs, Map<Object, String> variables)
	{
		String res = "";
		if(outputs.size() == 1) {
			res += outputs.keySet().toArray()[0] + " = ";
		}
		if(outputs.size() > 1) {
			res += "modfuture = ";
		}
		res += encodeCommandRun(moduleName, process, inputs);
		if(outputs.size() == 1) {
			res += ".get().getOutput(\"" + outputs.values().toArray()[0] + "\")";
		}
		res += encodeOutputVariables(outputs);
		return res;
	}

	private String encodeCommandRun(String command, boolean process, Map<String, Object> inputs) {
		String res = getScriptParameter(CommandService.class) + ".run(\"" + command + "\", ";
		res += process ? "True" : "False";
		if(inputs != null) {
			for (Map.Entry<String,Object> entry : inputs.entrySet()) {
				res += ", \"" + entry.getKey() + "\", " + entry.getValue();
			}
		}
		res += ")";
		return res;
	}

	private String encodeOutputVariables(Map<String, String> outputs) {
		if(outputs.size() < 2) return "";
		String res = "";
		res += "\nmodres = " + getScriptParameter(ModuleService.class) + ".waitFor(modfuture)";
		for (Map.Entry<String,String> entry : outputs.entrySet()) {
			res += "\n" + entry.getKey() + " = modres.getOutput(\"" + entry.getValue() + "\")";
		}
		return res;
	}

}
