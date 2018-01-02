package com.cj.adsystems.deployment;


import java.util.logging.Logger;

public class Deploy {

	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {

		new Deployer().deploy();
	}


	
}
