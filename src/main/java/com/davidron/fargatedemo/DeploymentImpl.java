package com.davidron.fargatedemo;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.regions.Regions;
import com.cj.adsystems.deployment.fargate.Deployment;

public class DeploymentImpl implements Deployment{
	private Regions region = Regions.US_EAST_1; //Fargate requires US_EAST_1 for now.
	public static final String CLUSTERNAME="clustername";
	
	
	private Options options = new Options()
			//.addOption("region", true, "AWS Region"); //Only US_EAST_1 is supported for fargate currently.
			//.addOption( "help", "print this message" ) //Messing it up prints the help.  Good enough :)
			.addRequiredOption(CLUSTERNAME, CLUSTERNAME, true, "The name of this cluster.");
	
	
	/*
	 * Remove these and replace with string constants that tie to opts above.
	 */
	private String APPLICATION_NAME = "fargate-demo";
	private String ENVIRONMENT_NAME = "lab";
	private String AWS_REGISTRY_ID="727586729164";
	private String DOCKER_TAG = "deploy";
	private Integer MEMORY = 512;
	private Integer CPU = 256;
	private Set<String> VPC_SUBNETS=Stream.of("subnet-6f752a45", "subnet-361d1240", "subnet-14154d4c", "subnet-b64d718b", "subnet-d7c941db", "subnet-d5bdddb0").collect(Collectors.toSet());
	private Integer DOCKERPORT = 8080;
	private String roleARN = String.format("arn:aws:iam::%s:role/AdSystemsFargateManagerRole", AWS_REGISTRY_ID);
	private CommandLine opts;
	/*
	 * End remove these
	 */
	
	
	public DeploymentImpl(String[] args) throws ParseException {
		try {
			this.opts = new DefaultParser().parse(options, args);
		}catch(MissingOptionException e) {
			new HelpFormatter().printHelp( "./deploy.sh", options );
			throw e;
		}
	}
	
	@Override
	public Regions getRegion() {
		return region;
	}

	@Override
	public String getClusterName() {
		return opts.getOptionValue("clustername");
	}

	@Override
	public String getApplicationName() {
		return APPLICATION_NAME;
	}

	@Override
	public String getAwsRegistryId() {
		return AWS_REGISTRY_ID;
	}

	@Override
	public String getDockerTag() {
		return DOCKER_TAG;
	}

	@Override
	public Integer getMemory() {
		return MEMORY;
	}

	@Override
	public Integer getCPU() {
		return CPU;
	}

	@Override
	public Set<String> getVpcSubnets() {
		return VPC_SUBNETS;
	}

	@Override
	public Integer getDockerPort() {
		return DOCKERPORT;
	}

	@Override
	public String getRoleArn() {
		return roleARN;
	}
	

}
