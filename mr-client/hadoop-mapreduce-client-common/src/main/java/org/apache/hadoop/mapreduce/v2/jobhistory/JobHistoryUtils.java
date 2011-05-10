package org.apache.hadoop.mapreduce.v2.jobhistory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.mapreduce.TypeConverter;
import org.apache.hadoop.mapreduce.v2.YarnMRJobConfig;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.yarn.conf.YARNApplicationConstants;

public class JobHistoryUtils {
  
  private static final Log LOG = LogFactory.getLog(JobHistoryUtils.class);
  
  public static final FsPermission HISTORY_DIR_PERMISSION =
    FsPermission.createImmutable((short) 0750); // rwxr-x---
  
  public static final FsPermission HISTORY_FILE_PERMISSION =
    FsPermission.createImmutable((short) 0740); // rwxr-----
  
  /**
   * Suffix for configuration files.
   */
  public static final String CONF_FILE_NAME_SUFFIX = "_conf.xml";
  
  /**
   * Suffix for done files.
   */
  public static final String DONE_FILE_NAME_SUFFIX = ".done";
  
  /**
   * Job History File extension.
   */
  public static final String JOB_HISTORY_FILE_EXTENSION = ".jhist";
  
  public static final int VERSION = 4;

  public static final int SERIAL_NUMBER_DIRECTORY_DIGITS = 6;
  
  public static final String TIMESTAMP_DIR_REGEX = "\\d{4}" + "\\" + File.separator +  "\\d{2}" + "\\" + File.separator + "\\d{2}";
  public static final Pattern TIMESTAMP_DIR_PATTERN = Pattern.compile(TIMESTAMP_DIR_REGEX);
  private static final String TIMESTAMP_DIR_FORMAT = "%04d" + File.separator + "%02d" + File.separator + "%02d";
  
  /**
   * Version substring to use while storing history files.
   */
  public static final String LOG_VERSION_STRING = "version-" + VERSION;

  private static final PathFilter CONF_FILTER = new PathFilter() {
    @Override
    public boolean accept(Path path) {
      return path.getName().endsWith(CONF_FILE_NAME_SUFFIX);
    }
  };
  
  private static final PathFilter JOB_HISTORY_FILE_FILTER = new PathFilter() {
    @Override
    public boolean accept(Path path) {
      return path.getName().endsWith(JOB_HISTORY_FILE_EXTENSION);
    }
  };


  /**
   * Gets a PathFilter which would match configuration files.
   * @return
   */
  public static PathFilter getConfFileFilter() {
    return CONF_FILTER;
  }
  
  /**
   * Gets a PathFilter which would match job history file names.
   * @return
   */
  public static PathFilter getHistoryFileFilter() {
    return JOB_HISTORY_FILE_FILTER;
  }
  
  /**
   * Returns the current done directory.
   * @param doneDirPrefix the prefix for the done directory.
   * @return A string representation of the done directory.
   */
  public static String getCurrentDoneDir(String doneDirPrefix) {
    return doneDirPrefix + File.separator + LOG_VERSION_STRING + File.separator;
  }

  /**
   * Gets the configured directory prefix for In Progress history files.
   * @param conf
   * @return A string representation of the prefix.
   */
  public static String getConfiguredHistoryLogDirPrefix(Configuration conf) {
    String defaultLogDir = conf.get(
        YARNApplicationConstants.APPS_HISTORY_STAGING_DIR_KEY) + "/history/staging";
    String logDir = conf.get(YarnMRJobConfig.HISTORY_STAGING_DIR_KEY,
      defaultLogDir);
    return logDir;
  }
  
  /**
   * Gets the configured directory prefix for intermediate done history files.
   * @param conf
   * @return A string representation of the prefix.
   */
  public static String getConfiguredHistoryIntermediateDoneDirPrefix(Configuration conf) {
    String defaultDoneDir = conf.get(
        YARNApplicationConstants.APPS_HISTORY_STAGING_DIR_KEY) + "/history/done_intermediate";
    String  doneDirPrefix =
      conf.get(YarnMRJobConfig.HISTORY_INTERMEDIATE_DONE_DIR_KEY,
          defaultDoneDir);
    return doneDirPrefix;
  }
  
  /**
   * Gets the configured directory prefix for Done history files.
   * @param conf
   * @return
   */
  public static String getConfiguredHistoryServerDoneDirPrefix(Configuration conf) {
    String defaultDoneDir = conf.get(
        YARNApplicationConstants.APPS_HISTORY_STAGING_DIR_KEY) + "/history/done";
    String  doneDirPrefix =
      conf.get(YarnMRJobConfig.HISTORY_DONE_DIR_KEY,
          defaultDoneDir);
    return doneDirPrefix; 
  }

  /**
   * Get the job history file path for non Done history files.
   */
  public static Path getStagingJobHistoryFile(Path dir, JobId jobId, int attempt) {
    return getStagingJobHistoryFile(dir, TypeConverter.fromYarn(jobId).toString(), attempt);
  }
  
  /**
   * Get the job history file path for non Done history files.
   */
  public static Path getStagingJobHistoryFile(Path dir, String jobId, int attempt) {
    return new Path(dir, jobId + "_" + 
        attempt + JOB_HISTORY_FILE_EXTENSION);
  }
  
  /**
   * Get the done configuration file name for a job.
   * @param jobId the jobId.
   * @return the conf file name.
   */
  public static String getIntermediateConfFileName(JobId jobId) {
    return TypeConverter.fromYarn(jobId).toString() + CONF_FILE_NAME_SUFFIX;
  }
  
  public static String getIntermediateDoneFileName(JobId jobId) {
    return TypeConverter.fromYarn(jobId).toString() + DONE_FILE_NAME_SUFFIX;
  }
  
  /**
   * Gets the conf file path for jobs in progress.
   * 
   * @param logDir the log directory prefix.
   * @param jobId the jobId.
   * @param attempt attempt number for this job.
   * @return
   */
  public static Path getStagingConfFile(Path logDir, JobId jobId, int attempt) {
    Path jobFilePath = null;
    if (logDir != null) {
      jobFilePath = new Path(logDir, TypeConverter.fromYarn(jobId).toString()
          + "_" + attempt + CONF_FILE_NAME_SUFFIX);
    }
    return jobFilePath;
  }
  
  /**
   * Gets the serial number part of the path based on the jobId and serialNumber format.
   * @param id
   * @param serialNumberFormat
   * @return
   */
  public static String serialNumberDirectoryComponent(JobId id, String serialNumberFormat) {
    return String.format(serialNumberFormat,
        Integer.valueOf(jobSerialNumber(id))).substring(0,
        SERIAL_NUMBER_DIRECTORY_DIGITS);
  }
  
  /**Extracts the timstamp component from the path.
   * @param path
   * @return
   */
  public static String getTimestampPartFromPath(String path) {
    Matcher matcher = TIMESTAMP_DIR_PATTERN.matcher(path);
    if (matcher.find()) {
      String matched = matcher.group();
      matched.intern();
      return matched;
    } else {
      return null;
    }
  }
  
  /**
   * Gets the history subdirectory based on the jobId, timestamp and serial number format.
   * @param id
   * @param timestampComponent
   * @param serialNumberFormat
   * @return
   */
  public static String historyLogSubdirectory(JobId id, String timestampComponent, String serialNumberFormat) {
    String result = LOG_VERSION_STRING;
    String serialNumberDirectory = serialNumberDirectoryComponent(id, serialNumberFormat);
    
    result = result 
      + File.separator + timestampComponent
      + File.separator + serialNumberDirectory
      + File.separator;
    
    return result;
  }
  
  /**
   * Gets the timestamp component based on millisecond time.
   * @param millisecondTime
   * @param debugMode
   * @return
   */
  public static String timestampDirectoryComponent(long millisecondTime, boolean debugMode) {
    Calendar timestamp = Calendar.getInstance();
    timestamp.setTimeInMillis(millisecondTime);
    String dateString = null;
    dateString = String.format(
        TIMESTAMP_DIR_FORMAT,
        timestamp.get(Calendar.YEAR),
        // months are 0-based in Calendar, but people will expect January
        // to be month #1.
        timestamp.get(debugMode ? Calendar.HOUR : Calendar.MONTH) + 1,
        timestamp.get(debugMode ? Calendar.MINUTE : Calendar.DAY_OF_MONTH));
    dateString = dateString.intern();
    return dateString;
  }
  
  public static String doneSubdirsBeforeSerialTail() {
    // Version Info
    String result = ("/" + LOG_VERSION_STRING);

    // date
    result = result + "/*/*/*"; // YYYY/MM/DD ;
    return result;
  }
  
  /**
   * Computes a serial number used as part of directory naming for the given jobId.
   * @param id the jobId.
   * @return
   */
  public static int jobSerialNumber(JobId id) {
    return id.getId();
  }
  
  public static List<FileStatus> localGlobber(FileContext fc, Path root, String tail)
      throws IOException {
    return localGlobber(fc, root, tail, null);
  }

  public static List<FileStatus> localGlobber(FileContext fc, Path root, String tail,
      PathFilter filter) throws IOException {
    return localGlobber(fc, root, tail, filter, null);
  }

  // hasMismatches is just used to return a second value if you want
  // one. I would have used MutableBoxedBoolean if such had been provided.
  public static List<FileStatus> localGlobber(FileContext fc, Path root, String tail,
      PathFilter filter, AtomicBoolean hasFlatFiles) throws IOException {
    if (tail.equals("")) {
      return (listFilteredStatus(fc, root, filter));
    }

    if (tail.startsWith("/*")) {
      Path[] subdirs = filteredStat2Paths(
          remoteIterToList(fc.listStatus(root)), true, hasFlatFiles);

      List<List<FileStatus>> subsubdirs = new LinkedList<List<FileStatus>>();

      int subsubdirCount = 0;

      if (subdirs.length == 0) {
        return new LinkedList<FileStatus>();
      }

      String newTail = tail.substring(2);

      for (int i = 0; i < subdirs.length; ++i) {
        subsubdirs.add(localGlobber(fc, subdirs[i], newTail, filter, null));
        // subsubdirs.set(i, localGlobber(fc, subdirs[i], newTail, filter,
        // null));
        subsubdirCount += subsubdirs.get(i).size();
      }

      List<FileStatus> result = new LinkedList<FileStatus>();

      for (int i = 0; i < subsubdirs.size(); ++i) {
        result.addAll(subsubdirs.get(i));
      }

      return result;
    }

    if (tail.startsWith("/")) {
      int split = tail.indexOf('/', 1);

      if (split < 0) {
        return listFilteredStatus(fc, new Path(root, tail.substring(1)), filter);
      } else {
        String thisSegment = tail.substring(1, split);
        String newTail = tail.substring(split);
        return localGlobber(fc, new Path(root, thisSegment), newTail, filter,
            hasFlatFiles);
      }
    }

    IOException e = new IOException("localGlobber: bad tail");

    throw e;
  }

  private static List<FileStatus> listFilteredStatus(FileContext fc, Path root,
      PathFilter filter) throws IOException {
    List<FileStatus> fsList = remoteIterToList(fc.listStatus(root));
    if (filter == null) {
      return fsList;
    } else {
      List<FileStatus> filteredList = new LinkedList<FileStatus>();
      for (FileStatus fs : fsList) {
        if (filter.accept(fs.getPath())) {
          filteredList.add(fs);
        }
      }
      return filteredList;
    }
  }

  private static List<FileStatus> remoteIterToList(
      RemoteIterator<FileStatus> rIter) throws IOException {
    List<FileStatus> fsList = new LinkedList<FileStatus>();
    if (rIter == null)
      return fsList;
    while (rIter.hasNext()) {
      fsList.add(rIter.next());
    }
    return fsList;
  }
  
  // hasMismatches is just used to return a second value if you want
  // one. I would have used MutableBoxedBoolean if such had been provided.
  private static Path[] filteredStat2Paths(List<FileStatus> stats, boolean dirs,
      AtomicBoolean hasMismatches) {
    int resultCount = 0;

    if (hasMismatches == null) {
      hasMismatches = new AtomicBoolean(false);
    }

    for (int i = 0; i < stats.size(); ++i) {
      if (stats.get(i).isDirectory() == dirs) {
        stats.set(resultCount++, stats.get(i));
      } else {
        hasMismatches.set(true);
      }
    }

    Path[] result = new Path[resultCount];
    for (int i = 0; i < resultCount; i++) {
      result[i] = stats.get(i).getPath();
    }

    return result;
  }
}
