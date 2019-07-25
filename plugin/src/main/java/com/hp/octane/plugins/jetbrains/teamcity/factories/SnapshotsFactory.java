/*
 *     2017 EntIT Software LLC, a Micro Focus company, L.P.
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

package com.hp.octane.plugins.jetbrains.teamcity.factories;

import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.dto.snapshots.CIBuildStatus;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotPhase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by gullery on 03/04/2016.
 */

public class SnapshotsFactory {
	private static final DTOFactory dtoFactory = DTOFactory.getInstance();

	@Autowired
	private ModelCommonFactory modelCommonFactory;
	@Autowired
	private TCPluginParametersFactory parametersFactory;
	@Autowired
	private ProjectManager projectManager;

	public SnapshotNode createSnapshot(String buildConfigurationId) {
		SBuildType rootJob = projectManager.findBuildTypeByExternalId(buildConfigurationId);
		SnapshotNode result = null;
		if (rootJob != null) {
			result = createSnapshotItem(rootJob, rootJob.getBuildTypeId());

			List<SnapshotNode> snapshotNodesList = createSnapshots(rootJob.getOwnDependencies(), rootJob.getBuildTypeId());
			if (!snapshotNodesList.isEmpty()) {
				SnapshotPhase phase = dtoFactory.newDTO(SnapshotPhase.class)
						.setName("teamcity_dependencies")
						.setBlocking(true)
						.setBuilds(snapshotNodesList);
				List<SnapshotPhase> snapshotPhases = new ArrayList<SnapshotPhase>();
				snapshotPhases.add(phase);
				result.setPhasesPostBuild(snapshotPhases);
			}
		} else {
			//should update the response?
		}
		return result;
	}

	private SnapshotNode createSnapshotItem(SBuildType build, String rootId) {
		//  Option 1: the build is running now and need to retrieve the data from the running object
		SnapshotNode snapshotNode = createRunningBuild(build, rootId);
		//  Option 2: the build in the queue
		if (snapshotNode == null) {
			snapshotNode = createQueueBuild(build, rootId);
		}
		//  Option 3: the build is finished
		if (snapshotNode == null) {
			snapshotNode = createHistoryBuild(build, rootId);
		}
		//  Option 4: if the build not available, create empty build
		if (snapshotNode == null) {
			snapshotNode = createUnavailableBuild(build);
		}
		return snapshotNode;
	}


	private List<SnapshotNode> createSnapshots(List<Dependency> dependencies, String rootId) {
		List<SnapshotNode> result = new LinkedList<>();

		if (dependencies != null && !dependencies.isEmpty()) {
			for (Dependency dependency : dependencies) {
				SBuildType build = dependency.getDependOn();
				SnapshotNode snapshotNode = createSnapshotItem(build, rootId);
				result.add(snapshotNode);
				result.addAll(createSnapshots(build.getOwnDependencies(), rootId));
			}
		}

		return result;
	}


	private SnapshotNode createQueueBuild(SBuildType build, String rootId) {
		SnapshotNode result = null;
		SQueuedBuild queuedBuild = null;

		if (build.isInQueue()) {
			List<SQueuedBuild> queuedBuilds = build.getQueuedBuilds(null);
			if (build.getBuildTypeId().equals(rootId) && !queuedBuilds.isEmpty()) {
				queuedBuild = queuedBuilds.get(0);
			} else {
				for (SQueuedBuild b : queuedBuilds) {
					TriggeredBy trigger = b.getTriggeredBy();
					if (rootId.equals(trigger.getParameters().get("buildTypeId"))) {
						queuedBuild = b;
						break;
					}
				}
			}

			if (queuedBuild != null) {
				result = dtoFactory.newDTO(SnapshotNode.class)
						.setJobCiId(build.getExternalId())
						.setBuildCiId(queuedBuild.getItemId())
						.setName(build.getName())
						.setStatus(CIBuildStatus.QUEUED)
						.setResult(CIBuildResult.UNAVAILABLE);
			}
		}

		return result;
	}

	private SnapshotNode createRunningBuild(SBuildType build, String rootId) {
		SnapshotNode result = null;
		SBuild currentBuild = null;

		List<SRunningBuild> runningBuilds = build.getRunningBuilds();

		if (!runningBuilds.isEmpty()) {
			if (build.getBuildTypeId().equals(rootId)) {
				for (SBuild b : runningBuilds) {
					TriggeredBy trigger = b.getTriggeredBy();
					if (!trigger.getParameters().containsKey("buildTypeId")) {
						currentBuild = b;
						break;
					}
				}
			} else {
				for (SBuild b : runningBuilds) {
					TriggeredBy trigger = b.getTriggeredBy();
					if (rootId.equals(trigger.getParameters().get("buildTypeId"))) {
						currentBuild = b;
						break;
					}
				}
			}
		}

		if (currentBuild != null) {
			result = dtoFactory.newDTO(SnapshotNode.class)
					.setJobCiId(build.getExternalId())
					.setName(build.getName())
					.setBuildCiId(String.valueOf(currentBuild.getBuildId()))
					.setNumber(currentBuild.getBuildNumber())
					.setParameters(parametersFactory.obtainFromBuild(currentBuild))
					.setDuration(currentBuild.getDuration() * 1000)
					.setEstimatedDuration(((SRunningBuild) currentBuild).getDurationEstimate() * 1000)
					.setStartTime(currentBuild.getStartDate().getTime())
					.setCauses(null)
					.setStatus(CIBuildStatus.RUNNING)
					.setResult(CIBuildResult.UNAVAILABLE);
		}

		return result;
	}

	private SnapshotNode createHistoryBuild(SBuildType build, String rootId) {
		SnapshotNode result = null;
		SBuild currentBuild = null;

		List<SFinishedBuild> finishedBuilds = build.getHistory();

		if (!finishedBuilds.isEmpty()) {
			if (build.getBuildTypeId().equals(rootId)) {
				for (SBuild b : finishedBuilds) {
					TriggeredBy trigger = b.getTriggeredBy();
					if (!trigger.getParameters().containsKey("buildTypeId")) {
						currentBuild = b;
						break;
					}
				}
			} else {
				for (SBuild b : finishedBuilds) {
					TriggeredBy trigger = b.getTriggeredBy();
					if (rootId.equals(trigger.getParameters().get("buildTypeId"))) {
						currentBuild = b;
						break;
					}
				}
			}
		}

		if (currentBuild != null) {
			result = dtoFactory.newDTO(SnapshotNode.class)
					.setJobCiId(build.getExternalId())
					.setName(build.getExtendedName())
					.setBuildCiId(String.valueOf(currentBuild.getBuildId()))
					.setNumber(currentBuild.getBuildNumber())
					.setParameters(parametersFactory.obtainFromBuild(currentBuild))
					.setDuration(currentBuild.getDuration() * 1000)
					.setEstimatedDuration(currentBuild.getDuration() * 1000)
					.setStartTime(currentBuild.getStartDate().getTime())
					.setCauses(null)
					.setStatus(CIBuildStatus.FINISHED)
					.setResult(modelCommonFactory.resultFromNativeStatus(currentBuild.getBuildStatus()));
		}

		return result;
	}

	private SnapshotNode createUnavailableBuild(SBuildType build) {
		return dtoFactory.newDTO(SnapshotNode.class)
				.setJobCiId(build.getExternalId())
				.setName(build.getExtendedName())
				.setStatus(CIBuildStatus.UNAVAILABLE)
				.setResult(CIBuildResult.UNAVAILABLE);
	}
}
