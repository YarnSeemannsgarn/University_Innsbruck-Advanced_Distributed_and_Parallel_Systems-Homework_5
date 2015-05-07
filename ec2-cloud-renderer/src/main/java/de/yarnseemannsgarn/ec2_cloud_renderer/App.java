package de.yarnseemannsgarn.ec2_cloud_renderer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.io.Payload;
import org.jclouds.io.Payloads;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.collect.ImmutableSet;

public class App 
{
	// Args
	public static int PARAMETERS = 4;
	public static String INVALID_SYNTAX = "Invalid number of parameters. Syntax is: "
			+ "aws_access_key_id aws_secret_access_key instances frames";

	// Povray
	public static String POVRAY = "povray";
	public static String GM = "gm";
	public static String SCHERK = "scherk.pov";
	public static String RESULTS = "results";
	
	// Remote dirs
	public static String REMOTE_DIR = "/tmp/homework_5";
	public static String REMOTE_POVRAY = REMOTE_DIR + "/" + POVRAY;
	public static String REMOTE_SCHERK = REMOTE_DIR + "/" + SCHERK;
	public static String REMOTE_OUTPUT_DIR = REMOTE_DIR + "/" + RESULTS;
	public static String REMOTE_OUTPUT = REMOTE_OUTPUT_DIR + "/" + "scherk.png";
	
	// Local dirs and files
	public static Path CWD = Paths.get(System.getProperty("user.dir"));
	public static Path POVRAY_DIR = CWD.resolve(POVRAY);
	public static Path RESULT_DIR = CWD.resolve(RESULTS);
	public static Path RESULT_FILE = RESULT_DIR.resolve("result.gif");
	
	public static void main( String[] args )
	{	
		if (args.length < PARAMETERS)
			throw new IllegalArgumentException(INVALID_SYNTAX);

		// Parse args
		String aws_access_key_id = args[0];
		String aws_secret_access_key = args[1];
		int instances = Integer.parseInt(args[2]);
		int frames = Integer.parseInt(args[3]);

		// Other params
		String instanceType = InstanceType.T1_MICRO;
		OsFamily osFamily = OsFamily.AMZN_LINUX;
		String location = "us-west-1";

		// Set properties due to https://jclouds.apache.org/guides/aws-ec2/
		Properties prop = new Properties();
		prop.setProperty("jclouds.relax-hostname", "true");
		prop.setProperty("jclouds.trust-all-certs", "true");

		// Modules
		Iterable<com.google.inject.Module> modules = 
				ImmutableSet.<com.google.inject.Module>of(new SshjSshClientModule());

		// Launch instances
		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(aws_access_key_id, aws_secret_access_key)
				.overrides(prop)
				.modules(modules)
				.buildView(ComputeServiceContext.class);
		ComputeService client = context.getComputeService(); 

		System.out.println( "Launch " + instances + " instance/s" );
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
		System.out.println();

		// Variables for frame rendering
		int processorPerNode = nodes.iterator().next().getHardware().getProcessors().size();
		int processors = processorPerNode * instances;
		System.out.println("Processors per Node: " + processorPerNode);
		System.out.println("Processors total: " + processors);
		System.out.println();
		
		int subsetStartFrame = 1;
		int subsetEndFrame = -1;
		int subsetPerProcessor = (int) ((double) frames / (double) processors); // Round down
		int modulo = frames % processors;
		
		Payload plPovray = Payloads.newFilePayload(POVRAY_DIR.resolve(POVRAY).toFile());
		Payload plsScherk = Payloads.newFilePayload(POVRAY_DIR.resolve(SCHERK).toFile());
			
		// SSH - copy files and submit jobs
		System.out.println("SSH to instances - copy files and render images");
		RESULT_DIR.toFile().mkdirs();
		int i = 0;
		Thread[] threadPool = new Thread[instances];
		long startTime = System.currentTimeMillis();
		for(NodeMetadata node : nodes) {
			subsetEndFrame = subsetStartFrame + subsetPerProcessor - 1;
			if((i+1) <= modulo)
				subsetEndFrame++;
			
			// Do copying and rendering in parallel
			Thread t = new Thread(new CopyAndRenderFilesOnNode(
					client, context, node, plPovray, plsScherk, subsetStartFrame, subsetEndFrame, frames, i));			
			t.start();
			threadPool[i] = t;
			
			subsetStartFrame = subsetEndFrame + 1;
			i++;
		}
		
		// Join threads
		for(i = 0; i < threadPool.length; i++) {
			try {
				threadPool[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Copy and rendering time: " + ((System.currentTimeMillis() - startTime)/1000) + "s");
		context.close();
		
		// Merge pictures to gif
		System.out.println();
		System.out.println("Merge all pictures to gif");
		try {		
			Runtime.getRuntime().exec(POVRAY_DIR + "/" + GM + " convert -loop 0 -delay 0 " + RESULT_DIR + "/*.png " + RESULT_FILE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static class CopyAndRenderFilesOnNode implements Runnable{
		private ComputeService client;
		private ComputeServiceContext context;
		private NodeMetadata node; 
		private Payload plPovray; 
		private Payload plsScherk; 
		private int subsetStartFrame;
		private int subsetEndFrame;
		private int frames;
		private int i;
		
		public CopyAndRenderFilesOnNode(ComputeService client, ComputeServiceContext context, NodeMetadata node, 
			Payload plPovray, Payload plsScherk, int subsetStartFrame, int subsetEndFrame, int frames, int i){
			this.client = client;
			this.context = context;
			this.node = node; 
			this.plPovray = plPovray; 
			this.plsScherk = plsScherk; 
			this.subsetStartFrame = subsetStartFrame;
			this.subsetEndFrame = subsetEndFrame;
			this.frames = frames;
			this.i = i;
		}

		public void run() {
			SshClient ssh = context.utils().sshForNode().apply(node);
			try {
				ssh.connect();
								
				// Copy Files
				System.out.println("Copy files to instance: " + node.getId());
				ssh.exec("mkdir -p " + REMOTE_DIR);
				ssh.put(REMOTE_POVRAY, plPovray); 
				ssh.put(REMOTE_SCHERK, plsScherk);
								
				// Render images
				System.out.println("Render frames " + subsetStartFrame + "-" + subsetEndFrame + " on instance: " + node.getId());
				String commands = "chmod +x " + REMOTE_POVRAY + "\n";
				commands += "mkdir -p " + REMOTE_OUTPUT_DIR + "\n";
				commands += REMOTE_POVRAY + " +I" + REMOTE_SCHERK + " +O" + REMOTE_OUTPUT + " +FN +W1024 +H768"
						+ " +KFI" + 1 + " +KFF" + frames + " +SF" + subsetStartFrame + " +EF" + subsetEndFrame 
						+ " -A0.1 +R2 +KI0 +KF1 +KC -P";
				ssh.exec(commands);
				
				// Create result archive
				String resultTarPath = REMOTE_DIR + "/results" + i + ".tar.gz";
				String command = "tar -czPf " + resultTarPath + " " + REMOTE_OUTPUT_DIR;
				ssh.exec(command);
				
				// Copy files to local machine
				System.out.println("Copy files from " + node.getId() + " to local machine");
				Payload renderedFrames = ssh.get(resultTarPath);
				
				// Extract rendered frames
				try {
					untarAllFilesToDirectory(renderedFrames.openStream(), RESULT_DIR);
				} catch (Exception e) {
					e.printStackTrace();
				}					
			} finally {
				if (ssh != null)
					ssh.disconnect();
			}
			
			// Destroy node
			System.out.println("Destroy node with id " + node.getId());
			client.destroyNode(node.getId());
		}		
	}
	
	// Copied (and adjusted) from: http://java-tweets.blogspot.co.at/2012/07/untar-targz-file-with-apache-commons.html
	private static void untarAllFilesToDirectory(InputStream is, Path destDir) 
			throws FileNotFoundException, IOException, ArchiveException{
		int BUFFER = 2048;

		/** create a TarArchiveInputStream object. **/
		BufferedInputStream in = new BufferedInputStream(is);
		GzipCompressorInputStream gzIn = new GzipCompressorInputStream(in);
		TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn);

		TarArchiveEntry entry = null;

		/** Read the tar entries using the getNextEntry method **/

		while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
			/** If the entry is a directory, create the directory. **/

			if (entry.isDirectory()) {
				// skip
			}
			/**
			 * If the entry is a file,write the decompressed file to the disk
			 * and close destination stream.
			 **/
			else {
				int count;
				byte data[] = new byte[BUFFER];

				String[] fileNameArray = entry.getName().split("/");
				String filename = fileNameArray[fileNameArray.length-1];
				File fo = new File(destDir + "/" + filename);
				
				FileOutputStream fos = new FileOutputStream(fo);
				BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
				while ((count = tarIn.read(data, 0, BUFFER)) != -1) {
					dest.write(data, 0, count);
				}
				dest.close();
			}
		}

		/** Close the input stream **/

		tarIn.close();
	}
}	
