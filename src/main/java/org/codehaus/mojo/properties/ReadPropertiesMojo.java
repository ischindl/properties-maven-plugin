package org.codehaus.mojo.properties;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * The read-project-properties goal reads property files and URLs and stores the properties as project properties. It
 * serves as an alternate to specifying properties in pom.xml. It is especially useful when making properties defined in
 * a runtime resource available at build time.
 *
 * @author <a href="mailto:zarars@gmail.com">Zarar Siddiqi</a>
 * @author <a href="mailto:Krystian.Nowak@gmail.com">Krystian Nowak</a>
 * @version $Id$
 */
@Mojo(name = "read-project-properties",
		defaultPhase = LifecyclePhase.NONE,
		requiresProject = true,
		threadSafe = true)
public class ReadPropertiesMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}",
			readonly = true,
			required = true)
	private MavenProject project;

	/**
	 * The properties files that will be used when reading properties.
	 */
	@Parameter
	private File[] files = new File[0];

	/**
	 * @param files
	 *            The files to set for tests.
	 */
	public void setFiles(File[] files) {
		if (files == null) {
			this.files = new File[0];
		} else {
			this.files = new File[files.length];
			System.arraycopy(files, 0, this.files, 0, files.length);
		}
	}

	/**
	 * The URLs that will be used when reading properties. These may be non-standard URLs of the form
	 * <code>classpath:com/company/resource.properties</code>. Note that the type is not <code>URL</code> for this
	 * reason and therefore will be explicitly checked by this Mojo.
	 */
	@Parameter
	private String[] urls = new String[0];

	/**
	 * Default scope for test access.
	 *
	 * @param urls
	 *            The URLs to set for tests.
	 */
	public void setUrls(String[] urls) {
		if (urls == null) {
			this.urls = null;
		} else {
			this.urls = new String[urls.length];
			System.arraycopy(urls, 0, this.urls, 0, urls.length);
		}
	}

	/**
	 * If the plugin should be quiet if any of the files was not found
	 */
	@Parameter(defaultValue = "false")
	private boolean quiet;

	/**
	 * Prefix that will be added before name of each property.
	 * Can be useful for separating properties with same name from different files.
	 */
	@Parameter
	private String keyPrefix = null;

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	/**
	 * Prefix that will be removed from name of each property.
	 * Can be useful for separating properties with same name for different configurations.
	 */
	@Parameter
	private String configPrefix = null;

	public void setConfigPrefix(String configPrefix) {
		this.configPrefix = configPrefix;
	}

	/**
	 * Used for resolving property placeholders.
	 */
	private final PropertyResolver resolver = new PropertyResolver();

	/** {@inheritDoc} */
	public void execute()
							throws MojoExecutionException,
							MojoFailureException {
		checkParameters();

		loadFiles();

		loadUrls();

		processProperties();

		resolveProperties();
	}

	private void checkParameters()
									throws MojoExecutionException {
		if (files.length > 0 && urls.length > 0) {
			throw new MojoExecutionException("Set files or URLs but not both - otherwise " + "no order of precedence can be guaranteed");
		}
	}

	private void loadFiles()
								throws MojoExecutionException {
		for (int i = 0; i < files.length; i++) {
			load(new FileResource(files[i]));
		}
	}

	private void loadUrls()
							throws MojoExecutionException {
		for (int i = 0; i < urls.length; i++) {
			load(new UrlResource(urls[i]));
		}
	}

	private void load(Resource resource)
											throws MojoExecutionException {
		if (resource.canBeOpened()) {
			loadProperties(resource);
		} else {
			missing(resource);
		}
	}

	private void loadProperties(Resource resource)
													throws MojoExecutionException {
		try (InputStream stream = resource.getInputStream()) {
			getLog().debug("Loading properties from " + resource);

			Properties prop = new Properties();
			prop.load(stream);
			project.getProperties().putAll(prop);
		} catch (IOException e) {
			throw new MojoExecutionException("Error reading properties from " + resource, e);
		}
	}

	/**
	 * resolve prefixes
	 */
	private void processProperties() {
		if (keyPrefix != null || configPrefix != null) {
			String[] prefixes = configPrefix.split("\\.");
			Properties properties = project.getProperties();
			Properties projectProperties = project.getProperties();
			List<String> propertyNames = new ArrayList<String>();
			propertyNames.addAll(properties.stringPropertyNames());
			Collections.sort(propertyNames, new PropertyNameComparator(configPrefix));
			String confPrefix = "";
			int prfLen = 1;
			for (String key : propertyNames) {
				if (prefixes.length >= prfLen && key.split("\\.").length > prfLen) {
					confPrefix = confPrefix + prefixes[prfLen++ - 1] + ".";
				}
				String newKey = key.replaceAll(confPrefix != null ? confPrefix : "", "");
				if (keyPrefix != null)
					newKey = keyPrefix + newKey;
				projectProperties.put(newKey, properties.get(key));
			}
		}
	}

	private void missing(Resource resource)
											throws MojoExecutionException {
		if (quiet) {
			getLog().info("Quiet processing - ignoring properties cannot be loaded from " + resource);
		} else {
			throw new MojoExecutionException("Properties could not be loaded from " + resource);
		}
	}

	private void resolveProperties()
										throws MojoExecutionException,
										MojoFailureException {
		Properties environment = loadSystemEnvironmentPropertiesWhenDefined();
		Properties projectProperties = project.getProperties();

		for (Enumeration<?> n = projectProperties.propertyNames(); n.hasMoreElements();) {
			String k = (String) n.nextElement();
			projectProperties.setProperty(k, getPropertyValue(k, projectProperties, environment));
		}
	}

	private Properties loadSystemEnvironmentPropertiesWhenDefined()
																	throws MojoExecutionException {
		Properties projectProperties = project.getProperties();

		boolean useEnvVariables = false;
		for (Enumeration<?> n = projectProperties.propertyNames(); n.hasMoreElements();) {
			String k = (String) n.nextElement();
			if (projectProperties.get(k) instanceof String) {
				String p = (String) projectProperties.get(k);
				if (p.indexOf("${env.") != -1) {
					useEnvVariables = true;
					break;
				}
			}
		}
		Properties environment = null;
		if (useEnvVariables) {
			try {
				environment = getSystemEnvVars();
			} catch (IOException e) {
				throw new MojoExecutionException("Error getting system environment variables: ", e);
			}
		}
		return environment;
	}

	private String getPropertyValue(String k, Properties p, Properties environment)
																					throws MojoFailureException {
		try {
			return resolver.getPropertyValue(k, p, environment);
		} catch (IllegalArgumentException e) {
			throw new MojoFailureException(e.getMessage());
		}
	}

	/**
	 * Override-able for test purposes.
	 *
	 * @return The shell environment variables, can be empty but never <code>null</code>.
	 * @throws IOException
	 *             If the environment variables could not be queried from the shell.
	 */
	Properties getSystemEnvVars()
									throws IOException {
		return CommandLineUtils.getSystemEnvVars();
	}

	/**
	 * Default scope for test access.
	 *
	 * @param quiet
	 *            Set to <code>true</code> if missing files can be skipped.
	 */
	void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	/**
	 * Default scope for test access.
	 *
	 * @param project
	 *            The test project.
	 */
	void setProject(MavenProject project) {
		this.project = project;
	}

	private static abstract class Resource {

		private InputStream stream;

		public abstract boolean canBeOpened();

		protected abstract InputStream openStream()
													throws IOException;

		public InputStream getInputStream()
											throws IOException {
			if (stream == null) {
				stream = openStream();
			}
			return stream;
		}
	}

	private static class FileResource extends Resource {

		private final File file;

		public FileResource(File file) {
			this.file = file;
		}

		public boolean canBeOpened() {
			return file.exists();
		}

		protected InputStream openStream()
											throws IOException {
			return new BufferedInputStream(new FileInputStream(file));
		}

		public String toString() {
			return "File: " + file;
		}
	}

	private static class UrlResource extends Resource {

		private static final String CLASSPATH_PREFIX = "classpath:";

		private static final String SLASH_PREFIX = "/";

		private final URL url;

		private boolean isMissingClasspathResouce = false;

		private String classpathUrl;

		public UrlResource(String url)
										throws MojoExecutionException {
			if (url.startsWith(CLASSPATH_PREFIX)) {
				String resource = url.substring(CLASSPATH_PREFIX.length(), url.length());
				if (resource.startsWith(SLASH_PREFIX)) {
					resource = resource.substring(1, resource.length());
				}
				this.url = getClass().getClassLoader().getResource(resource);
				if (this.url == null) {
					isMissingClasspathResouce = true;
					classpathUrl = url;
				}
			} else {
				try {
					this.url = new URL(url);
				} catch (MalformedURLException e) {
					throw new MojoExecutionException("Badly formed URL " + url + " - " + e.getMessage());
				}
			}
		}

		public boolean canBeOpened() {
			if (isMissingClasspathResouce) {
				return false;
			}
			try {
				openStream();
			} catch (IOException e) {
				return false;
			}
			return true;
		}

		protected InputStream openStream()
											throws IOException {
			return new BufferedInputStream(url.openStream());
		}

		public String toString() {
			if (!isMissingClasspathResouce) {
				return "URL " + url.toString();
			}
			return classpathUrl;
		}
	}
}
