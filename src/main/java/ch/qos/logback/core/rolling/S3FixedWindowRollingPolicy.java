package ch.qos.logback.core.rolling;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class S3FixedWindowRollingPolicy extends FixedWindowRollingPolicy {

	ExecutorService executor = Executors.newFixedThreadPool(1);

	String clientRegion; // "*** Client region ***";
	String roleARN; // "*** Role ARN ***";
	String roleSessionName; // "logback-s3-rolling-policy";
	String s3BucketName;
	String s3FolderName;

	boolean rollingOnExit = true;

	AmazonS3Client s3Client;

	protected AmazonS3Client getS3Client() {

		AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
			.withCredentials(new ProfileCredentialsProvider())
			.withRegion(clientRegion)
			.build();

		AssumeRoleRequest roleRequest = new AssumeRoleRequest()
			.withRoleArn(roleARN)
			.withRoleSessionName(roleSessionName);

		AssumeRoleResult roleResponse = stsClient.assumeRole(roleRequest);

		Credentials sessionCredentials = roleResponse.getCredentials();

		BasicSessionCredentials awsCredentials = new BasicSessionCredentials(
			sessionCredentials.getAccessKeyId(),
			sessionCredentials.getSecretAccessKey(),
			sessionCredentials.getSessionToken());

		s3Client = (AmazonS3Client) AmazonS3ClientBuilder.standard()
			.withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
			.withRegion(clientRegion)
			.build();

		return s3Client;
	}

	@Override
	public void start() {
		super.start();
		// add a hook on JVM shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHookRunnable()));
	}

	@Override
	public void rollover() throws RolloverFailure {
		super.rollover();

		// upload the current log file into S3
		String rolledLogFileName = fileNamePattern.convertInt(getMinIndex());
		uploadFileToS3Async(rolledLogFileName);
	}

	protected void uploadFileToS3Async(String filename) {
		final File file = new File(filename);

		// if file does not exist or empty, do nothing
		if (!file.exists() || file.length() == 0) {
			return;
		}

		// add the S3 folder name in front if specified
		final StringBuffer s3ObjectName = new StringBuffer();
		if (getS3FolderName() != null) {
			s3ObjectName.append(getS3FolderName()).append("/");
		}
		s3ObjectName.append(file.getName());

		addInfo("Uploading " + filename);
		Runnable uploader = new Runnable() {
			@Override
			public void run() {
				try {
					getS3Client().putObject(getS3BucketName(), s3ObjectName.toString(), file);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
		executor.execute(uploader);
	}

	// On JVM exit, upload the current log
	class ShutdownHookRunnable implements Runnable {

		@Override
		public void run() {
			try {
				if (isRollingOnExit())
					// do rolling and upload the rolled file on exit
					rollover();
				else
					// upload the active log file without rolling
					uploadFileToS3Async(getActiveFileName());

				// wait until finishing the upload
				executor.shutdown();
				executor.awaitTermination(10, TimeUnit.MINUTES);
			} catch (Exception ex) {
				addError("Failed to upload a log in S3", ex);
				executor.shutdownNow();
			}
		}

	}


	/**
	 * s3BucketName
	 * @return
	 */
	public String getS3BucketName() {
		return s3BucketName;
	}

	/**
	 * setS3BucketName
	 * @param s3BucketName
	 */
	public void setS3BucketName(String s3BucketName) {
		this.s3BucketName = s3BucketName;
	}

	/**
	 * getS3FolderName
	 * @return
	 */
	public String getS3FolderName() {
		return s3FolderName;
	}

	/**
	 * setS3FolderName
	 * @param s3FolderName
	 */
	public void setS3FolderName(String s3FolderName) {
		this.s3FolderName = s3FolderName;
	}

	/**
	 * isRollingOnExit
	 * @return
	 */
	public boolean isRollingOnExit() {
		return rollingOnExit;
	}

	/**
	 * setRollingOnExit
	 * @param rollingOnExit
	 */
	public void setRollingOnExit(boolean rollingOnExit) {
		this.rollingOnExit = rollingOnExit;
	}

	/**
	 * @return the clientRegion
	 */
	public String getClientRegion() {
		return clientRegion;
	}

	/**
	 * @param clientRegion the clientRegion to set
	 */
	public void setClientRegion(String clientRegion) {
		this.clientRegion = clientRegion;
	}


	/**
	 * @return the roleARN
	 */
	public String getRoleARN() {
		return roleARN;
	}

	/**
	 * @param roleARN the roleARN to set
	 */
	public void setRoleARN(String roleARN) {
		this.roleARN = roleARN;
	}

	/**
	 * @return the roleSessionName
	 */
	public String getRoleSessionName() {
		return roleSessionName;
	}


	/**
	 * @param roleSessionName the roleSessionName to set
	 */
	public void setRoleSessionName(String roleSessionName) {
		this.roleSessionName = roleSessionName;
	}
}