# logback-s3


```xml

<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>/var/log/myapp.log</file>
  <encoder>
    <pattern>%d\t%thread\t%level\t%logger\t%msg%n</pattern>
  </encoder>

 
  <rollingPolicy class="ch.qos.logback.core.rolling.S3FixedWindowRollingPolicy">
    <fileNamePattern>/var/log/myapp.log.%i.gz</fileNamePattern>
    <clientRegion>us-east-1</clientRegion>
    <roleARN>arn:aws:iam::user-key:role/roleName</roleARN>
    <roleSessionName>logback-s3-rolling-policy</roleSessionName>
    <s3BucketName>com.mybucket</s3BucketName>
    <s3FolderName>log</s3FolderName>

    <rollingOnExit>true</rollingOnExit>
  </rollingPolicy>

  <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
      <maxFileSize>10MB</maxFileSize>
  </triggeringPolicy>
</appender>

```