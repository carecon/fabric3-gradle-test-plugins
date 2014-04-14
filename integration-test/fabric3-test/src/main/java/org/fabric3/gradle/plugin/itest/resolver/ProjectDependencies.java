/*
 * Fabric3
 * Copyright (c) 2009-2013 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
*/
package org.fabric3.gradle.plugin.itest.resolver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.contribution.FileContributionSource;
import org.fabric3.plugin.resolver.Resolver;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;

/**
 * Returns the set of artifacts a project depends on.
 */
public class ProjectDependencies {

    /**
     * The set of artifacts a project depends on.
     *
     * @param project  the project
     * @param filter   artifacts to filter from the result
     * @param resolver the artifact resolver
     * @return the dependencies
     * @throws GradleException
     */
    public static Set<URL> calculateProjectDependencies(Project project, Set<Artifact> filter, Resolver resolver) {
        Set<URL> artifacts = new HashSet<>();
        for (Configuration configuration : project.getConfigurations()) {
            for (Dependency dependency : configuration.getDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    Project dependencyProject = ((ProjectDependency) dependency).getDependencyProject();
                    try {
                        File artifact = findArtifact(dependencyProject);
                        artifacts.add(artifact.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new GradleException(e.getMessage(), e);
                    }

                } else {
                    Artifact artifact = new DefaultArtifact(dependency.getGroup(), dependency.getName(), "jar", dependency.getVersion());
                    if (!filter.contains(artifact)) {
                        try {
                            Set<Artifact> list = resolver.resolveTransitively(artifact);
                            for (Artifact resolved : list) {
                                File pathElement = resolved.getFile();
                                URL url = pathElement.toURI().toURL();
                                artifacts.add(url);
                            }

                        } catch (DependencyResolutionException | MalformedURLException e) {
                            throw new GradleException(e.getMessage(), e);
                        }
                    }

                }
            }
        }
        return artifacts;
    }

    /**
     * Creates a contribution source for a project.
     *
     * @param project the project
     * @return the source
     * @throws GradleException
     */
    public static ContributionSource createSource(Project project) throws GradleException {
        File artifact = findArtifact(project);
        try {
            URI uri = URI.create(artifact.getName());
            return new FileContributionSource(uri, artifact.toURI().toURL(), -1, false);
        } catch (MalformedURLException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    public static File findArtifact(Project project) throws GradleException {
        File[] files = new File(project.getBuildDir() + File.separator + "libs").listFiles();
        File source;
        if (files == null || files.length == 0) {
            throw new GradleException("Archive not found for contribution project: " + project.getName());
        } else if (files.length > 1) {
            // More than one archive. Check if a WAR is produced and use that as sometimes the JAR task may not be disabled in a webapp project, resulting
            // in multiple artifacts.
            int war = -1;
            for (File file : files) {
                if (file.getName().endsWith(".war")) {
                    war++;
                    break;
                }
            }
            if (war == -1) {
                throw new GradleException("Contribution project has multiple library archives: " + project.getName());
            }
            source = files[war];
        } else {
            source = files[0];
        }
        return source;
    }

}
