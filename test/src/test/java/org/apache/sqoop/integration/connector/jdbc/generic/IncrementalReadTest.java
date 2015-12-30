/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.integration.connector.jdbc.generic;

import com.google.common.collect.Iterables;
import org.apache.hadoop.fs.Path;
import org.apache.sqoop.connector.hdfs.configuration.ToFormat;
import org.apache.sqoop.model.MConfigList;
import org.apache.sqoop.model.MJob;
import org.apache.sqoop.model.MLink;
import org.apache.sqoop.test.infrastructure.Infrastructure;
import org.apache.sqoop.test.infrastructure.SqoopTestCase;
import org.apache.sqoop.test.infrastructure.providers.DatabaseInfrastructureProvider;
import org.apache.sqoop.test.infrastructure.providers.HadoopInfrastructureProvider;
import org.apache.sqoop.test.infrastructure.providers.KdcInfrastructureProvider;
import org.apache.sqoop.test.infrastructure.providers.SqoopInfrastructureProvider;
import org.apache.sqoop.test.utils.ParametrizedUtils;
import org.testng.ITestContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Infrastructure(dependencies = {KdcInfrastructureProvider.class, HadoopInfrastructureProvider.class, SqoopInfrastructureProvider.class, DatabaseInfrastructureProvider.class})
public class IncrementalReadTest extends SqoopTestCase {

  public static Object[] COLUMNS = new Object [][] {
    //       column -                last value -               new max value
    {          "id",                         "9",                        "19"},
    {     "version",                      "8.10",                     "13.10"},
    {"release_date",     "2008-10-18 00:00:00.0",     "2013-10-17 00:00:00.0"},
  };

  private static String testName;

  private String checkColumn;
  private String lastValue;
  private String newMaxValue;

  @Factory(dataProvider="incremental-integration-test")
  public IncrementalReadTest(String checkColumn, String lastValue, String newMaxValue) {
    this.checkColumn = checkColumn;
    this.lastValue = lastValue;
    this.newMaxValue = newMaxValue;
  }

  @DataProvider(name="incremental-integration-test", parallel=true)
  public static Object[][] data(ITestContext context) {
    testName = context.getName();

    return Iterables.toArray(ParametrizedUtils.toArrayOfArrays(COLUMNS), Object[].class);
  }

  @Override
  public String getTestName() {
    if (methodName == null) {
      return testName;
    } else {
      return methodName + "[" + checkColumn + "]";
    }
  }

  @BeforeMethod
  public void cleanUpHDFS() throws Exception {
    hdfsClient.delete(new Path(getMapreduceDirectory()), true);
  }

  @Test
  public void testTable() throws Exception {
    createAndLoadTableUbuntuReleases();

    // RDBMS link
    MLink rdbmsLink = getClient().createLink("generic-jdbc-connector");
    fillRdbmsLinkConfig(rdbmsLink);
    saveLink(rdbmsLink);

    // HDFS link
    MLink hdfsLink = getClient().createLink("hdfs-connector");
    fillHdfsLink(hdfsLink);
    saveLink(hdfsLink);

    // Job creation
    MJob job = getClient().createJob(rdbmsLink.getName(), hdfsLink.getName());

    // Set the rdbms "FROM" config
    fillRdbmsFromConfig(job, "id");
    MConfigList fromConfig = job.getFromJobConfig();
    fromConfig.getStringInput("incrementalRead.checkColumn").setValue(checkColumn);
    fromConfig.getStringInput("incrementalRead.lastValue").setValue(lastValue);

    // Fill hdfs "TO" config
    fillHdfsToConfig(job, ToFormat.TEXT_FILE);

    saveJob(job);

    executeJob(job);

    // Assert correct output
    assertTo(
      "10,'Jaunty Jackalope',9.04,'2009-04-23 00:00:00.000'",
      "11,'Karmic Koala',9.10,'2009-10-29 00:00:00.000'",
      "12,'Lucid Lynx',10.04,'2010-04-29 00:00:00.000'",
      "13,'Maverick Meerkat',10.10,'2010-10-10 00:00:00.000'",
      "14,'Natty Narwhal',11.04,'2011-04-28 00:00:00.000'",
      "15,'Oneiric Ocelot',11.10,'2011-10-10 00:00:00.000'",
      "16,'Precise Pangolin',12.04,'2012-04-26 00:00:00.000'",
      "17,'Quantal Quetzal',12.10,'2012-10-18 00:00:00.000'",
      "18,'Raring Ringtail',13.04,'2013-04-25 00:00:00.000'",
      "19,'Saucy Salamander',13.10,'2013-10-17 00:00:00.000'"
    );

    // Verify new last value
    MJob updatedJob = getClient().getJob(job.getName());
    assertEquals(updatedJob.getFromJobConfig().getStringInput("incrementalRead.lastValue").getValue(), newMaxValue);

    // Clean up testing table
    dropTable();
  }

  @Test
  public void testQuery() throws Exception {
    createAndLoadTableUbuntuReleases();

    // RDBMS link
    MLink rdbmsLink = getClient().createLink("generic-jdbc-connector");
    fillRdbmsLinkConfig(rdbmsLink);
    saveLink(rdbmsLink);

    // HDFS link
    MLink hdfsLink = getClient().createLink("hdfs-connector");
    fillHdfsLink(hdfsLink);
    saveLink(hdfsLink);

    // Job creation
    MJob job = getClient().createJob(rdbmsLink.getName(), hdfsLink.getName());

    String query = "SELECT * FROM " + provider.escapeTableName(getTableName().getTableName()) + " WHERE ${CONDITIONS}";

    // Set the rdbms "FROM" config
    MConfigList fromConfig = job.getFromJobConfig();
    fromConfig.getStringInput("fromJobConfig.sql").setValue(query);
    fromConfig.getStringInput("fromJobConfig.partitionColumn").setValue("id");
    fromConfig.getStringInput("incrementalRead.checkColumn").setValue(checkColumn);
    fromConfig.getStringInput("incrementalRead.lastValue").setValue(lastValue);

    // Fill hdfs "TO" config
    fillHdfsToConfig(job, ToFormat.TEXT_FILE);

    saveJob(job);

    executeJob(job);

    // Assert correct output
    assertTo(
      "10,'Jaunty Jackalope',9.04,'2009-04-23 00:00:00.000'",
      "11,'Karmic Koala',9.10,'2009-10-29 00:00:00.000'",
      "12,'Lucid Lynx',10.04,'2010-04-29 00:00:00.000'",
      "13,'Maverick Meerkat',10.10,'2010-10-10 00:00:00.000'",
      "14,'Natty Narwhal',11.04,'2011-04-28 00:00:00.000'",
      "15,'Oneiric Ocelot',11.10,'2011-10-10 00:00:00.000'",
      "16,'Precise Pangolin',12.04,'2012-04-26 00:00:00.000'",
      "17,'Quantal Quetzal',12.10,'2012-10-18 00:00:00.000'",
      "18,'Raring Ringtail',13.04,'2013-04-25 00:00:00.000'",
      "19,'Saucy Salamander',13.10,'2013-10-17 00:00:00.000'"
    );

    // Verify new last value
    MJob updatedJob = getClient().getJob(job.getName());
    assertEquals(updatedJob.getFromJobConfig().getStringInput("incrementalRead.lastValue").getValue(), newMaxValue);

    // Clean up testing table
    dropTable();
  }
}
