package de.yarnseemannsgarn.ec2_cloud_renderer;

import java.util.Properties;
import java.util.Set;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.collect.ImmutableSet;

public class App 
{

	public static int PARAMETERS = 3;
	public static String INVALID_SYNTAX = "Invalid number of parameters. Syntax is: accesskeyid secretkey instances";

	public static void main( String[] args )
	{
		if (args.length < PARAMETERS)
			throw new IllegalArgumentException(INVALID_SYNTAX);

		// Parse args
		String accesskeyid = args[0];
		String secretkey = args[1];
		int instances = Integer.parseInt(args[2]);

		// Other params
		String instanceType = InstanceType.T1_MICRO;
		OsFamily osFamily = OsFamily.AMZN_LINUX;
		String location = "us-west-1";

		// Set properties due to https://jclouds.apache.org/guides/aws-ec2/
		Properties prop = new Properties();
		prop.setProperty("jclouds.relax-hostname", "true");
		prop.setProperty("jclouds.trust-all-certs", "true");

		// Modules
		Iterable<com.google.inject.Module> modules = ImmutableSet.<com.google.inject.Module>of(new SshjSshClientModule(),new SLF4JLoggingModule(), new EnterpriseConfigurationModule());

		// Launch instance
		System.out.println( "Initialize Computing Service" );
		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(accesskeyid, secretkey)
				.overrides(prop)
				.modules(modules)
				.buildView(ComputeServiceContext.class);
		ComputeService client = context.getComputeService(); 

		System.out.println( "Launch " + instances + " instances" );
		Template template = client.templateBuilder()
				.hardwareId(instanceType)
				.osFamily(osFamily)
				.locationId(location)
				.build();

		Set<? extends NodeMetadata> nodes = null;
		try {
			nodes = client.createNodesInGroup("homework5", instances, template);					
		} catch (RunNodesException e) {
			System.err.println(e.getMessage());
			for (NodeMetadata node : e.getNodeErrors().keySet())
				client.destroyNode(node.getId());
			System.exit(1);
		}

		System.out.println( "Instances launched! Ids:" );		
		for(NodeMetadata node : nodes){
			System.out.println(node.getId());
		}

		// SSH
		System.out.println("SSH to instances");
		for(NodeMetadata node : nodes){
			SshClient ssh = context.utils().sshForNode().apply(node);
			try {
				ssh.connect();
				System.out.println(ssh.exec("hostname").getOutput());
			} finally {
				if (ssh != null)
					ssh.disconnect();
			}     
		}


		context.close();
	}
}
