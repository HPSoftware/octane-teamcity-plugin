/*
 *     Copyright 2017 Hewlett-Packard Development Company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.hp.octane.plugins.jetbrains.teamcity;

/**
 * Created by lazara on 23/12/2015.
 */

import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.spi.CIPluginServices;
import com.hp.octane.plugins.jetbrains.teamcity.actions.ConfigurationActionsController;
import com.hp.octane.plugins.jetbrains.teamcity.actions.GenericOctaneActionsController;
import com.hp.octane.plugins.jetbrains.teamcity.configuration.OctaneConfigStructure;
import com.hp.octane.plugins.jetbrains.teamcity.configuration.TCConfigurationService;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerExtension;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.UUID;

public class OctaneTeamCityPlugin implements ServerExtension {
	private static final Logger logger = LogManager.getLogger(OctaneTeamCityPlugin.class);
	public static final String PLUGIN_NAME = OctaneTeamCityPlugin.class.getSimpleName().toLowerCase();

	@Autowired
	private ProjectManager projectManager;
	@Autowired
	private SBuildServer buildServer;
	@Autowired
	private CIPluginServices pluginServices;
	@Autowired
	private GenericOctaneActionsController genericController;
	@Autowired
	private ConfigurationActionsController configurationController;
	@Autowired
	private TCConfigurationService configurationService;
	@Autowired
	private PluginDescriptor pluginDescriptor;
	@Autowired
	private WebControllerManager webControllerManager;

	private OctaneConfigStructure config;

	private static String rootServerUrl = null;
	@PostConstruct
	private void initPlugin() {
		buildServer.registerExtension(ServerExtension.class, PLUGIN_NAME, this);
		registerControllers();
		config = configurationService.readConfig();
		ensureServerInstanceID();
		OctaneSDK.init(pluginServices);
		logger.info("ALM Octane CI Plugin initialized; current configuration: " + config);
	}

	public ProjectManager getProjectManager() {
		return projectManager;
	}

	public PluginDescriptor getDescriptor() {
		return pluginDescriptor;
	}

	public OctaneConfigStructure getConfig() {
		return config;
	}

	private void registerControllers() {
		webControllerManager.registerController("/nga/**", genericController);
		webControllerManager.registerController("/octane-rest/**", configurationController);
	}

	private void ensureServerInstanceID() {
		if (config == null) {
			config = new OctaneConfigStructure();
		}
		String identity = config.getIdentity();
		if (identity == null || identity.equals("")) {
			config.setIdentity(UUID.randomUUID().toString());
			config.setIdentityFrom(String.valueOf(new Date().getTime()));
			configurationService.saveConfig(config);

		}
	}

	public static void setRootURL(String rootUrl){

		if(rootUrl != null && !rootUrl.isEmpty()){
			rootServerUrl = rootUrl;
			if(rootUrl.endsWith("/")){
				rootServerUrl = rootUrl.substring(0,rootUrl.length()-1);
			}
		}
	}

	public String getServerUrl(){
		return rootServerUrl;
	}
}
