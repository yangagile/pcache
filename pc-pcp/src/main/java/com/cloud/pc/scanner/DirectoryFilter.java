/*
 * Copyright (c) 2025 Yangagile. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloud.pc.scanner;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Getter
@Setter
public class DirectoryFilter extends SimpleFileVisitor<Path> {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryFilter.class);

    private Long timeSpan;
    private Long timeNow;
    private Long timeMaxSpan;
    private Long emptyDirCount;
    private Long errorCount;

    private FileStat[] statCounter;

    public DirectoryFilter(long timeSpan, long timeMaxSpan) {
        this.timeSpan = timeSpan;
        this.timeMaxSpan = timeMaxSpan;
        this.timeNow = System.currentTimeMillis();
        this.emptyDirCount = 0L;
        this.errorCount = 0L;
        this.statCounter = new FileStat[getCounterIndex(timeMaxSpan)+1];
        for(int i=0; i< statCounter.length; i++){
            statCounter[i] = new FileStat();
        }
    }
    @Override
    public String toString() {
        FileStat currentSize = new FileStat();
        FileStat deletedSize = statCounter[statCounter.length-1];
        for(int i=0; i< statCounter.length-1; i++){
            currentSize.add(statCounter[i]);
        }
        return "scanner result: "
                + " current size: " +  currentSize
                + " deleted size: " + deletedSize
                + " deleted dir count: " + emptyDirCount
                + " error count: " + errorCount;
    }


    int getCounterIndex(long span) {
        if (span > timeMaxSpan) {
            span = timeMaxSpan;
        }
        int i = 0;
        long curSpan = timeSpan;
        while(span > curSpan) {
            span -= curSpan;
            curSpan *= 2;
            i++;
        }
        return i;
    }

    public boolean isEmptyDirectory(Path path) {
        try {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
                return !dirStream.iterator().hasNext();
            }
        }catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (isEmptyDirectory(dir)) {
            dir.toFile().delete();
            LOG.info("delete dir: " + dir.getFileName());
            emptyDirCount++;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        long span = timeNow - attrs.lastAccessTime().toMillis();
        int i = getCounterIndex(span);
        statCounter[i].addSize(attrs.size());
        statCounter[i].addCount(1 );

        // 检查访问时间
        if (span > timeMaxSpan) {
            file.toFile().delete();
            LOG.info("delete file: " + file.getFileName() +" | size: " + attrs.size() + " bytes");
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        errorCount ++;
        return FileVisitResult.CONTINUE;
    }

    public FileStat getUsage() {
        FileStat currentSize = new FileStat();
        for(int i=0; i< statCounter.length-1; i++){
            currentSize.add(statCounter[i]);
        }
        return currentSize;
    }
}

