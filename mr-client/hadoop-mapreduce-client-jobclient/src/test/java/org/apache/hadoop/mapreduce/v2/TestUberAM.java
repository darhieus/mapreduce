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

package org.apache.hadoop.mapreduce.v2;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobStatus;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskCompletionEvent;
import org.apache.hadoop.mapreduce.TaskID;
import org.apache.hadoop.mapreduce.TaskType;
import org.junit.BeforeClass;

public class TestUberAM extends TestMRJobs {

  private static final Log LOG = LogFactory.getLog(TestUberAM.class);

  @BeforeClass
  public static void setup() {
    TestMRJobs.setup();
    if (mrCluster != null) {
    	mrCluster.getConfig().setBoolean(MRJobConfig.JOB_UBERTASK_ENABLE, true);
    }
  }

  @Override
  public void testSleepJob()
  throws IOException, InterruptedException, ClassNotFoundException {
    if (mrCluster != null) {
    	mrCluster.getConfig().setInt("TestMRJobs.testSleepJob.reduces", 1);
    }
    super.testSleepJob();
  }

  @Override
  public void testRandomWriter()
  throws IOException, InterruptedException, ClassNotFoundException {
    super.testRandomWriter();
  }

  @Override
  public void testFailingMapper()
  throws IOException, InterruptedException, ClassNotFoundException {
    LOG.info("\n\n\nStarting uberized testFailingMapper().");

    if (!(new File(MiniMRYarnCluster.APPJAR)).exists()) {
      LOG.info("MRAppJar " + MiniMRYarnCluster.APPJAR
               + " not found. Not running test.");
      return;
    }

    Job job = runFailingMapperJob();

    // should be able to get diags for single task attempt...
    TaskID taskID = new TaskID(job.getJobID(), TaskType.MAP, 0);
    TaskAttemptID aId = new TaskAttemptID(taskID, 0);
    System.out.println("Diagnostics for " + aId + " :");
    for (String diag : job.getTaskDiagnostics(aId)) {
      System.out.println(diag);
    }
    // ...but not for second (shouldn't exist:  uber-AM overrode max attempts)
    boolean secondTaskAttemptExists = true;
    try {
      aId = new TaskAttemptID(taskID, 1);
      System.out.println("Diagnostics for " + aId + " :");
      for (String diag : job.getTaskDiagnostics(aId)) {
        System.out.println(diag);
      }
    } catch (Exception e) {
      secondTaskAttemptExists = false;
    }
    Assert.assertEquals(false, secondTaskAttemptExists);

    TaskCompletionEvent[] events = job.getTaskCompletionEvents(0, 2);
    Assert.assertEquals(1, events.length);
    Assert.assertEquals(TaskCompletionEvent.Status.FAILED,
        events[0].getStatus().FAILED);
    Assert.assertEquals(JobStatus.State.FAILED, job.getJobState());

    // TODO later:  add explicit "isUber()" checks of some sort
  }

//@Test  //FIXME:  if/when the corresponding TestMRJobs test gets enabled, do so here as well (potentially with mods for ubermode)
  public void testSleepJobWithSecurityOn()
  throws IOException, InterruptedException, ClassNotFoundException {
    super.testSleepJobWithSecurityOn();
  }

}
