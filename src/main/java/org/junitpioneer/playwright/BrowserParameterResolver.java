/*
 * Copyright 2016-2021 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junitpioneer.playwright;

import static org.junitpioneer.playwright.PlaywrightUtils.PLAYWRIGHT_NAMESPACE;
import static org.junitpioneer.playwright.PlaywrightUtils.closeResourceLater;
import static org.junitpioneer.playwright.PlaywrightUtils.isPlaywrightExtensionActive;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class BrowserParameterResolver implements ParameterResolver {

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return isPlaywrightExtensionActive(extensionContext)
				&& parameterContext.getParameter().getType() == Browser.class;
	}

	@Override
	public Browser resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		// @formatter:off
		return extensionContext
			.getStore(PLAYWRIGHT_NAMESPACE)
			.getOrComputeIfAbsent(
				"browser",
				__ -> {
					BrowserType browserType = new BrowserTypeParameterResolver()
						.resolveParameter(parameterContext, extensionContext);
					Browser browser = browserType.launch(createLaunchOptions(extensionContext));
					closeResourceLater(extensionContext, browser::close);
					return browser;
				},
				Browser.class);
		// @formatter:on
	}

	private BrowserType.LaunchOptions createLaunchOptions(ExtensionContext extensionContext) {
		return new BrowserType.LaunchOptions();
	}

}
