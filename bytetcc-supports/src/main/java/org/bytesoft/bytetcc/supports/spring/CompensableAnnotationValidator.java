package org.bytesoft.bytetcc.supports.spring;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bytesoft.compensable.Compensable;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class CompensableAnnotationValidator implements BeanFactoryPostProcessor {

	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		Map<String, Class<?>> otherServiceMap = new HashMap<String, Class<?>>();
		Map<String, Compensable> compensables = new HashMap<String, Compensable>();

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		String[] beanNameArray = beanFactory.getBeanDefinitionNames();
		for (int i = 0; beanNameArray != null && i < beanNameArray.length; i++) {
			String beanName = beanNameArray[i];
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			String className = beanDef.getBeanClassName();
			try {
				Class<?> clazz = cl.loadClass(className);
				Compensable compensable = clazz.getAnnotation(Compensable.class);
				if (compensable == null) {
					otherServiceMap.put(beanName, clazz);
					continue;
				} else {
					compensables.put(beanName, compensable);
				}

				Class<?> interfaceClass = compensable.interfaceClass();
				if (interfaceClass.isInterface() == false) {
					throw new IllegalStateException("Compensable's interfaceClass must be a interface.");
				}
				this.validateTransactionalAnnotation(interfaceClass, clazz);
			} catch (ClassNotFoundException ex) {
				throw new FatalBeanException(ex.getMessage(), ex);
			} catch (IllegalStateException ex) {
				throw new FatalBeanException(ex.getMessage(), ex);
			}
		}

		Iterator<Map.Entry<String, Compensable>> itr = compensables.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, Compensable> entry = itr.next();
			Compensable compensable = entry.getValue();
			Class<?> interfaceClass = compensable.interfaceClass();
			String confirmableKey = compensable.confirmableKey();
			String cancellableKey = compensable.cancellableKey();
			if (StringUtils.isNotBlank(confirmableKey)) {
				if (compensables.containsKey(confirmableKey)) {
					throw new FatalBeanException("The confirm bean cannot be a compensable service!");
				}
				Class<?> clazz = otherServiceMap.get(confirmableKey);
				if (clazz != null) {
					try {
						this.validateTransactionalAnnotation(interfaceClass, clazz);
					} catch (IllegalStateException ex) {
						throw new FatalBeanException(ex.getMessage(), ex);
					}
				}
			}

			if (StringUtils.isNotBlank(cancellableKey)) {
				if (compensables.containsKey(cancellableKey)) {
					throw new FatalBeanException("The cancel bean cannot be a compensable service!");
				}
				Class<?> clazz = otherServiceMap.get(cancellableKey);
				if (clazz != null) {
					try {
						this.validateTransactionalAnnotation(interfaceClass, clazz);
					} catch (IllegalStateException ex) {
						throw new FatalBeanException(ex.getMessage(), ex);
					}
				}
			}
		}
	}

	private void validateTransactionalAnnotation(Class<?> interfaceClass, Class<?> clazz) throws IllegalStateException {
		Method[] methodArray = interfaceClass.getDeclaredMethods();
		for (int i = 0; i < methodArray.length; i++) {
			Method interfaceMethod = methodArray[i];
			String methodName = interfaceMethod.getName();
			Class<?>[] parameterTypeArray = interfaceMethod.getParameterTypes();
			Method method = null;
			try {
				method = clazz.getMethod(methodName, parameterTypeArray);
			} catch (NoSuchMethodException ex) {
				throw new IllegalStateException(ex);
			} catch (SecurityException ex) {
				throw new IllegalStateException(ex);
			}
			Transactional transactional = method.getAnnotation(Transactional.class);
			if (transactional == null) {
				throw new IllegalStateException(String.format(
						"Method(%s) must be specificed a Transactional annotation!", method));
			}
			Propagation propagation = transactional.propagation();
			if (Propagation.REQUIRED.equals(propagation) == false //
					&& Propagation.MANDATORY.equals(propagation) == false //
					&& Propagation.REQUIRES_NEW.equals(propagation) == false) {
				throw new IllegalStateException(String.format("Method(%s) not support propagation level: %s!", method,
						propagation.name()));
			}
		}
	}

}
