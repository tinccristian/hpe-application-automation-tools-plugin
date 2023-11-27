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

package com.microfocus.application.automation.tools.model;

import com.microfocus.application.automation.tools.uft.utils.UftToolUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Node;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Properties;

/**
 * Holds the data for RunFromFile build type.
 */
public class RunFromServiceModel extends AbstractDescribableImpl<RunFromServiceModel> {

    private String fsTests;
    private String fsTimeout;
    private String fsReportPath;

    /**
     * Instantiates a new Run from file system model.
     *
     * @param fsTests                   the fs tests path
     * @param fsTimeout                 the fs timeout in minutes for tests in seconds
     * @param fsReportPath              the fs report path

     */
    @DataBoundConstructor
    public RunFromServiceModel(String fsTests, String fsTimeout, String fsReportPath) {
        this.setFsTests(fsTests);
        this.fsTimeout = fsTimeout;
        this.fsReportPath = fsReportPath;
    }


    /**
     * Sets fs tests.
     *
     * @param fsTests the fs tests
     */
    public void setFsTests(String fsTests) {
        this.fsTests = fsTests.trim();

        if (!this.fsTests.contains("\n")) {
            this.fsTests += "\n";
        }
    }

    /**
     * Sets fs timeout.
     *
     * @param fsTimeout the fs timeout
     */
    public void setFsTimeout(String fsTimeout) {
        this.fsTimeout = fsTimeout;
    }

    /**
     * Gets fs tests.
     *
     * @return the fs tests
     */
    public String getFsTests() {
        return fsTests;
    }

    /**
     * Gets fs timeout.
     *
     * @return the fs timeout
     */
    public String getFsTimeout() {
        return fsTimeout;
    }

    /**
     * Sets the report path for the given tests.
     */
    public void setFsReportPath(String fsReportPath) {
        this.fsReportPath = fsReportPath;
    }

    /**
     * Gets the test report path.
     */
    public String getFsReportPath() {
        return fsReportPath;
    }

    /**
     * Gets properties.
     *
     * @param envVars the env vars
     * @return the properties
     */
	@Nullable
	public Properties getProperties(EnvVars envVars, Node currNode) {
        return createProperties(envVars, currNode);
    }

    private Properties createProperties(EnvVars envVars, Node currNode) {
        Properties props = new Properties();

        addTestsToProps(envVars, props);
        
        return props;
    }

    
    private void addTestsToProps(EnvVars envVars, Properties props) {
        String fsTimeoutVal = StringUtils.isEmpty(fsTimeout) ? "-1" : envVars.expand(fsTimeout);
        props.put("fsTimeout", fsTimeoutVal);
    
        if (StringUtils.isNotBlank(fsReportPath)) {
            props.put("fsReportPath", fsReportPath);
        }
        if (!StringUtils.isEmpty(this.fsTests)) {
            String expandedFsTests = envVars.expand(fsTests);
            String[] testsArr;
            if (UftToolUtils.isMtbxContent(expandedFsTests)) {
                testsArr = new String[]{expandedFsTests};
            } else {
                testsArr = expandedFsTests.replaceAll("\r", "").split("\n");
            }

            int i = 1;

            for (String test : testsArr) {
                test = test.trim();
                props.put("Test" + i, test);
                i++;
            }
        } else {
            props.put("fsTests", "");
        }
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<RunFromServiceModel> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "LR Service File System Model";
        }
    }
}
