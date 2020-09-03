package com.snowflake.kafka.connector.internal;

import net.snowflake.client.core.OCSPMode;
import net.snowflake.client.core.SFStatement;
import net.snowflake.client.jdbc.SnowflakeConnectionV1;
import net.snowflake.client.jdbc.SnowflakeFileTransferAgent;
import net.snowflake.client.jdbc.SnowflakeFileTransferConfig;
import net.snowflake.client.jdbc.SnowflakeFileTransferMetadataV1;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class InternalStageTest {

  private final SnowflakeConnectionService service = TestUtils.getConnectionService();

  private final String stageName1 = TestUtils.randomStageName();
  private final String stageName2 = TestUtils.randomStageName();
  private final String stageName3 = TestUtils.randomStageName();
  private final String stageNameExpire= TestUtils.randomStageName();

  @After
  public void afterEach()
  {
    service.dropStage(stageName1);
    service.dropStage(stageName2);
    service.dropStage(stageName3);
    service.dropStage(stageNameExpire);
  }

  @Test
  public void testInternalStage() throws Exception
  {
    //create stage
    service.createStage(stageName1);
    service.createStage(stageName2);
    service.createStage(stageName3);

    SnowflakeInternalStage agent = new SnowflakeInternalStage((SnowflakeConnectionV1) service.getConnection());

    // PUT two files to stageName1
    long startTime = System.currentTimeMillis();
    agent.putWithCache(stageName1, "testCacheFileName1", "Any cache");
    agent.putWithCache(stageName1, "testCacheFileName2", "Any cache");
    List<String> files1 = service.listStage(stageName1, "testCache");
    assert files1.size() == 2;
    System.out.println(Logging.logMessage("Time: {} ms",
      (System.currentTimeMillis() - startTime)));

    // PUT 50 files to stageName2
    startTime = System.currentTimeMillis();
    int fileNumber = 50;
    for (int i = 0; i < fileNumber; i++)
    {
      agent.putWithCache(stageName2, "appName/tableName/partition/testCacheFileName" + i, "Any cache");
    }
    List<String> files2 = service.listStage(stageName2, "appName/tableName/partition/testCache");
    assert files2.size() == fileNumber;
    System.out.println(Logging.logMessage("Time: {} ms",
      (System.currentTimeMillis() - startTime)));

    // PUT 50 files to stageName3
    startTime = System.currentTimeMillis();
    for (int i = 0; i < fileNumber; i++)
    {
      service.put(stageName3, "appName/tableName/partition/testNoCacheFileName" + i, "Any cache");
    }
    List<String> files3 = service.listStage(stageName3, "appName/tableName/partition/testNoCache");
    assert files3.size() == fileNumber;
    System.out.println(Logging.logMessage("Time: {} ms",
      (System.currentTimeMillis() - startTime)));

  }

  @Ignore
  public void testCredentialExpire() throws Exception
  {
    service.createStage(stageNameExpire);
    SnowflakeConnectionV1 conn = (SnowflakeConnectionV1) service.getConnection();

    String fullFilePath = "testExpire1";
    String data = "Any cache";

    String command = SnowflakeInternalStage.dummyPutCommandTemplate + stageNameExpire;

    SnowflakeFileTransferAgent agent = new SnowflakeFileTransferAgent(
      command,
      conn.getSfSession(),
      new SFStatement(conn.getSfSession())
    );

    SnowflakeFileTransferMetadataV1 fileTransferMetadata =
      (SnowflakeFileTransferMetadataV1) agent.getFileTransferMetadatas().get(0);

    // Set filename to be uploaded
    fileTransferMetadata.setPresignedUrlFileName(fullFilePath);

    byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
    InputStream inStream = new ByteArrayInputStream(dataBytes);

    // Sleep until it expire
    Thread.sleep(2 * 60 * 60 * 1000);

    SnowflakeFileTransferAgent.uploadWithoutConnection(
      SnowflakeFileTransferConfig.Builder.newInstance()
        .setSnowflakeFileTransferMetadata(fileTransferMetadata)
        .setUploadStream(inStream)
        .setRequireCompress(true)
        .setOcspMode(OCSPMode.FAIL_OPEN)
        .build());

    List<String> filesExpire = service.listStage(stageNameExpire, "testExpire");
    assert filesExpire.size() == 1;
  }

}