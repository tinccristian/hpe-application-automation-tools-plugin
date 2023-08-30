/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.tests;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

/**
 * Created by berkovir on 06/02/2017.
 */
@SuppressWarnings({"squid:S2698","squid:S2699","squid:S3658","squid:S2259","squid:S1872","squid:S2925"})
public class TestJenkinsDurationTest {

    @ClassRule
    public static final JenkinsRule rule = new JenkinsRule();


    @Test
    public void testDuration() throws Exception {

        FreeStyleProject p = rule.createFreeStyleProject("test-duration");

        assertEquals(0, p.getBuilds().toArray().length);
        long start = System.currentTimeMillis();
        AbstractBuild build = p.scheduleBuild2(0).get();


        while (build.isBuilding()){
            Thread.sleep(10);
        }

        long end = System.currentTimeMillis();
         long buildDurationWithoutPostProcessTime = build.getDuration();
        long buildDurationTotal = (end - start);
        long pluginPostProcessWorkTime = buildDurationTotal - buildDurationWithoutPostProcessTime;


        long buildDurationTotalExpected = 3000;
        long pluginPostProcessWorkTimeExpected = 2200;

        System.out.println(String.format("buildDurationTotal=%d, expected=%d", buildDurationTotal, buildDurationTotalExpected));
        System.out.println(String.format("pluginPostProcessWorkTime=%d, expected=%d", pluginPostProcessWorkTime, pluginPostProcessWorkTimeExpected));
        Assert.assertTrue(String.format("buildDurationTotal=%d, expected=%d", buildDurationTotal, buildDurationTotalExpected),buildDurationTotal < buildDurationTotalExpected);
        Assert.assertTrue(String.format("pluginPostProcessWorkTime=%d, expected=%d", pluginPostProcessWorkTime, pluginPostProcessWorkTimeExpected),pluginPostProcessWorkTime < pluginPostProcessWorkTimeExpected);

        int t;

    }
}
