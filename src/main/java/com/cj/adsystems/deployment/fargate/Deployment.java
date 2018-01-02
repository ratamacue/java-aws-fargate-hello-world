package com.cj.adsystems.deployment.fargate;

import java.util.Set;

import com.amazonaws.regions.Regions;

public interface Deployment {

	Regions getRegion();

	String getClusterName();

	String getApplicationName();

	String getAwsRegistryId();

	String getDockerTag();

	Integer getMemory();

	Integer getCPU();

	Set<String> getVpcSubnets();

	Integer getDockerPort();

	String getRoleArn();

}