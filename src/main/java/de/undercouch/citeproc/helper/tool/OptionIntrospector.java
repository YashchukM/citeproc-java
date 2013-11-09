// Copyright 2013 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package de.undercouch.citeproc.helper.tool;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inspects classes and builds an {@link OptionGroup} based on its
 * methods annotated with {@link OptionDesc}
 * @author Michel Kraemer
 */
public class OptionIntrospector {
	/**
	 * An identifier for all options generated by this class
	 */
	public static class ID {
		/**
		 * Hidden constructor. Instances of this class should only be
		 * created by the introspector
		 */
		private ID() {
			//nothing to do
		}
	}
	
	private static class PropertyID extends ID {
		private final Method setter;
		
		/**
		 * Hidden constructor
		 * @param setter a setter that injects the command line value
		 */
		private PropertyID(Method setter) {
			this.setter = setter;
		}
		
		/**
		 * @return the setter that injects the command line value
		 */
		Method getSetter() {
			return setter;
		}
	}
	
	private static class CommandID extends PropertyID {
		private final Class<? extends Command> cmdClass;
		
		/**
		 * Hidden constructor
		 * @param setter a setter that injects the command line value
		 * @param cmdClass the class of the command to inject
		 */
		private CommandID(Method setter, Class<? extends Command> cmdClass) {
			super(setter);
			this.cmdClass = cmdClass;
		}
		
		/**
		 * @return the class of the command to inject
		 */
		Class<? extends Command> getCommandClass() {
			return cmdClass;
		}
	}
	
	private static class IntrospectedDesc<T>
			implements Comparable<IntrospectedDesc<T>>{
		final T desc;
		final Method setter;
		final int priority;
		
		IntrospectedDesc(T desc, Method setter, int priority) {
			this.desc = desc;
			this.setter = setter;
			this.priority = priority;
		}

		@Override
		public int compareTo(IntrospectedDesc<T> o) {
			return (priority < o.priority ? -1 : (priority == o.priority ? 0 : 1));
		}
	}
	
	/**
	 * A default ID for arguments that are neither options nor commands
	 */
	public static ID DEFAULT_ID = new ID();
	
	/**
	 * Inspects one or more classes and builds an {@link OptionGroup} based on
	 * the methods annotated with {@link OptionDesc}
	 * @param classes the classes to inspect
	 * @return the option group
	 * @throws IntrospectionException if one of the classes could not be inspected
	 */
	public static OptionGroup<ID> introspect(Class<?>... classes) throws IntrospectionException {
		List<IntrospectedDesc<OptionDesc>> options =
				new ArrayList<IntrospectedDesc<OptionDesc>>();
		List<IntrospectedDesc<CommandDesc>> commands =
				new ArrayList<IntrospectedDesc<CommandDesc>>();

		for (Class<?> cls : classes) {
			BeanInfo bi = Introspector.getBeanInfo(cls);
			PropertyDescriptor[] pds = bi.getPropertyDescriptors();
			if (pds == null) {
				continue;
			}
			
			for (PropertyDescriptor pd : pds) {
				Method setter = pd.getWriteMethod();
				if (setter == null) {
					//this property can't be set
					continue;
				}
				OptionDesc od = setter.getAnnotation(OptionDesc.class);
				if (od != null) {
					options.add(new IntrospectedDesc<OptionDesc>(od, setter, od.priority()));
				} else {
					List<CommandDesc> cds = getCommandDescs(setter);
					for (CommandDesc cd : cds) {
						commands.add(new IntrospectedDesc<CommandDesc>(cd, setter, cd.priority()));
					}
				}
			}
		}
		
		Collections.sort(options);
		Collections.sort(commands);
		
		OptionBuilder<ID> b = new OptionBuilder<ID>();
		for (IntrospectedDesc<OptionDesc> o : options) {
			OptionDesc od = o.desc;
			String shortName = od.shortName().isEmpty() ? null : od.shortName();
			String argumentName = od.argumentName().isEmpty() ? null : od.argumentName();
			b.add(new PropertyID(o.setter), od.longName(), shortName, od.description(),
					argumentName, od.argumentType());
		}
		for (IntrospectedDesc<CommandDesc> c : commands) {
			CommandDesc cd = c.desc;
			b.addCommand(new CommandID(c.setter, cd.command()), cd.longName(),
					cd.description());
		}
		return b.build();
	}
	
	/**
	 * Returns all {@link CommandDesc} annotations attached to a method
	 * @param setter the method to inspect
	 * @return the annotations or an empty list
	 */
	private static List<CommandDesc> getCommandDescs(Method setter) {
		List<CommandDesc> result = new ArrayList<CommandDesc>();
		CommandDesc cd = setter.getAnnotation(CommandDesc.class);
		if (cd != null) {
			result.add(cd);
		}
		CommandDescList cdl = setter.getAnnotation(CommandDescList.class);
		if (cdl != null) {
			for (CommandDesc c : cdl.value()) {
				result.add(c);
			}
		}
		return result;
	}
	
	/**
	 * Injects the given values into one or more objects
	 * @param values the command line values
	 * @param objects the objects to inject the values into
	 * @throws InvocationTargetException if a value could not be injected
	 * @throws IllegalAccessException if a value could not be injected
	 * @throws InstantiationException if a command could not be instantiated
	 */
	public static void evaluate(List<Value<ID>> values, Object... objects)
			throws IllegalAccessException, InvocationTargetException, InstantiationException {
		List<String> unknown = new ArrayList<String>();
		for (Value<ID> v : values) {
			Object arg = v.getValue();
			if (arg == null) {
				arg = true;
			}
			
			ID id = v.getId();
			if (id instanceof CommandID) {
				CommandID cid = (CommandID)id;
				invokeAllSetters(cid.getSetter(), objects, cid.getCommandClass().newInstance());
			} else if (id instanceof PropertyID) {
				invokeAllSetters(((PropertyID)id).getSetter(), objects, arg);
			} else if (id == DEFAULT_ID) {
				unknown.add(v.getValue().toString());
			} else {
				//should never happen
				throw new IllegalStateException("Unknown option identifier");
			}
		}
		
		if (!unknown.isEmpty()) {
			boolean found = false;
			for (Object obj : objects) {
				Method setter = getUnknownArgumentSetter(obj.getClass());
				if (setter != null) {
					setter.invoke(obj, unknown);
					found = true;
				}
			}
			if (!found) {
				throw new RuntimeException("No bean property for unknown "
						+ "arguments found");
			}
		}
	}
	
	/**
	 * Invokes the given setter on all compatible objects
	 * @param setter the setter
	 * @param objects an array of objects to iterate
	 * @param args the arguments for the setter
	 * @throws IllegalAccessException if the setter is not accessible
	 * @throws InvocationTargetException if the setter caused an exception
	 */
	private static void invokeAllSetters(Method setter, Object[] objects, Object... args)
			throws IllegalAccessException, InvocationTargetException {
		for (Object obj : objects) {
			if (setter.getDeclaringClass().isAssignableFrom(obj.getClass())) {
				setter.invoke(obj, args);
			}
		}
	}
	
	/**
	 * Finds the setter method that accepts unknown arguments. Returns the
	 * first method found.
	 * @param cls the class to inspect
	 * @return the setter or null if there is no such setter
	 */
	private static Method getUnknownArgumentSetter(Class<?> cls) {
		BeanInfo bi;
		try {
			bi = Introspector.getBeanInfo(cls);
		} catch (IntrospectionException e) {
			throw new RuntimeException("Could not inspect bean for property "
					+ "that accepts unknown arguments");
		}
		
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		if (pds != null) {
			for (PropertyDescriptor pd : pds) {
				Method setter = pd.getWriteMethod();
				if (setter == null) {
					//this property can't be set
					continue;
				}
				UnknownAttributes od = setter.getAnnotation(UnknownAttributes.class);
				if (od != null) {
					return setter;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Checks if one of the given classes accepts unknown arguments with default IDs
	 * @param classes the classes to inspect
	 * @return true if one of the classes accepts unknown arguments, false otherwise
	 */
	public static boolean hasUnknownArguments(Class<?>... classes) {
		for (Class<?> cls : classes) {
			if (getUnknownArgumentSetter(cls) != null) {
				return true;
			}
		}
		return false;
	}
}
