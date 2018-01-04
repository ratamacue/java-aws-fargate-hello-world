package com.davidron.fargatedemo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.regions.Regions;
import com.cj.adsystems.deployment.fargate.Deployment;

public class DeploymentImpl implements Deployment{
	private static final String DOCKER_PORT = "dockerport";
	private static final String CPU = "cpu";
	private static final String MEMORY = "memory";
	public static final String DOCKER_TAG = "dockertag";
	public static final String ROLE_ARN = "rolearn";
	public static final String AWS_ID = "awsid";
	public static final String APP_NAME = "appname";
	public static final String CLUSTERNAME="clustername";
	private static final String SUBNETS = "subnets";
	private Regions region = Regions.US_EAST_1; //Fargate requires US_EAST_1 for now.
	private String ENVIRONMENT_NAME = "lab"; //Support for this needs to be added maybe?  Or maybe just allow people to use clustername and app name.

	private Options options = new Options()
			//.addOption("region", true, "AWS Region"); //Only US_EAST_1 is supported for fargate currently.
			//.addOption( "help", "print this message" ) //Messing it up prints the help.  Good enough :)
			.addRequiredOption(CLUSTERNAME, CLUSTERNAME, true, "The name of this cluster.")
			.addRequiredOption(APP_NAME, APP_NAME, true, "Name of this application.")
			.addRequiredOption(AWS_ID, AWS_ID, true, "The AWS ID you use to log in (probably 12 numbers)")
			.addRequiredOption(ROLE_ARN, ROLE_ARN, true, "AWS Role Arn.  Should look like \"arn:aws:iam::123456123456:role/role-name\"")
			.addRequiredOption(DOCKER_TAG, DOCKER_TAG, true, "What tag we use for the Docker image")
			.addRequiredOption(MEMORY, MEMORY, true, "The upper limit of how much RAM this application consumes")
			.addRequiredOption(CPU, CPU, true, "The upper limit of how much CPU this application consumes.  See the AWS documentation on a \"Task Definition\"")
			.addRequiredOption(SUBNETS, SUBNETS, true, "Comma-separated list of VPC Subnets.  Example: \"subnet-6f752a45,subnet-361d1240\"")
			.addRequiredOption(DOCKER_PORT, DOCKER_PORT, true, "The port defined in the dockerfile");
				
	private CommandLine opts;
	
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
		return opts.getOptionValue(APP_NAME);
	}

	@Override
	public String getAwsRegistryId() {
		return opts.getOptionValue(AWS_ID);
	}

	@Override
	public String getDockerTag() {
		return opts.getOptionValue(DOCKER_TAG);
	}

	@Override
	public Integer getMemory() {
		try{
			return Integer.parseInt(opts.getOptionValue(MEMORY));
		}catch (Exception e) {
			
			throw new RuntimeException("Trouble parsing integer memory value of "+opts.getOptionValue(MEMORY), e);
		}
	}

	@Override
	public Integer getCPU() {
		try{
			return Integer.parseInt(opts.getOptionValue(CPU));
		}catch (Exception e) {
			
			throw new RuntimeException("Trouble parsing integer cpu value of "+opts.getOptionValue(CPU), e);
		}
	}

	@Override
	public Set<String> getVpcSubnets() {
		return new HashSet<>(Arrays.asList(opts.getOptionValues(SUBNETS)));
	}

	@Override
	public Integer getDockerPort() {
		try{
			return Integer.parseInt(opts.getOptionValue(DOCKER_PORT));
		}catch (Exception e) {
			
			throw new RuntimeException("Trouble parsing integer dockerport value of "+opts.getOptionValue(DOCKER_PORT), e);
		}
	}

	@Override
	public String getRoleArn() {
		return opts.getOptionValue(ROLE_ARN);
	}
	
}
