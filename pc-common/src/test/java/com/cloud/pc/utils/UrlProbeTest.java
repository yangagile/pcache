package com.cloud.pc.utils;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UrlProbeTest extends TestCase {
    static String urlActive = "http://active";
    static String urlSlow = "http://slow";
    static String urlDead = "http://dead";

    class TestPmsMgr implements  UrlProbe.IUrlProbeFunction{
        public List<String> getUrlList(String url) {
            try {
                if (url == urlActive) {
                    return new ArrayList<>(Arrays.asList(urlActive, urlSlow, urlDead));
                } else if (url == urlSlow) {
                    Thread.sleep(10);
                    return new ArrayList<>(Arrays.asList(urlActive, urlSlow, urlDead));

                } else if (url == urlDead) {
                    Thread.sleep(100);
                    return null;
                }
            } catch (Exception e) {
                //ignore
            }
            return null;
        }
    }

    @Test
    public void test_generateToken_parseToken() throws Exception {
        TestPmsMgr testPmsMgr = new TestPmsMgr();
        UrlProbe probe = new UrlProbe(urlActive, testPmsMgr);
        Assert.assertNotNull(probe);

        List<String> activeUrls = probe.GetActiveUrls();
        Assert.assertEquals("active url should be 3",3, activeUrls.size());

        // first time to get active url, will trigger probe function

        String url = probe.getUrl();
        Assert.assertEquals("should be active URL", urlActive, url);

        // report fail, the active url will be changed to slow
        probe.reportFail(urlActive);
        url = probe.getUrl();
        Assert.assertEquals("should be slow URL", urlSlow, url);

        // wait for probe function is over
        Thread.sleep(1000);

        // the failed active url will be back
        url = probe.getUrl();
        Assert.assertEquals("should be active URL", urlActive, url);

        // the dead url will be probed
        activeUrls = probe.GetActiveUrls();
        Assert.assertEquals("active url should be 2",2, activeUrls.size());
    }
}