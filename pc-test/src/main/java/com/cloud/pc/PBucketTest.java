package com.cloud.pc;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PBucketTest {
    private static final Logger LOG = LoggerFactory.getLogger(PBucket.class);

    String ops;
    String bucket;
    String fileKey;
    String localFile;

    void help() {
        System.out.println("put file: \n\t--ops put --local_file /tmp/localfile --bucket testBucket --file_key tmp/localfile");
        System.out.println("get file: \n\t--ops get --local_file /tmp/localfile --bucket testBucket --file_key tmp/localfile");
    }


    public void run(String[] args) {
        if (!parseArgs(args)) {
            help();
            return;
        }
        PBucket pbucket = new PBucket(bucket);
        System.out.println("PMS Url:" + pbucket.getPmsUrl());
        if (ops.equals("put")) {
            pbucket.putObject(fileKey, localFile);
        } else if (ops.equals("get")) {
            pbucket.getObject(fileKey, localFile);
        } else {
            help();
        }
        pbucket.close();
    }

    public static void main(String[] args) {
        PBucketTest pbTest = new PBucketTest();
        pbTest.run(args);
    }

    boolean parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ops":
                    ops = args[i + 1];
                    i++;
                    break;
                case "--bucket":
                    bucket = args[i + 1];
                    i++;
                    break;
                case "--file_key":
                    fileKey = args[i + 1];
                    i++;
                    break;
                case "--local_file":
                    localFile = args[i + 1];
                    i++;
                    break;
            }
        }

        if (StringUtils.isBlank(ops)) {
            return false;
        }
        return true;
    }
}