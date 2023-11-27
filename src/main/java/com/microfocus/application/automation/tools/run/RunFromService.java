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

package com.microfocus.application.automation.tools.run;

import com.hp.octane.integrations.executor.TestsToRunConverter;
import com.microfocus.application.automation.tools.JenkinsUtils;
import com.microfocus.application.automation.tools.AlmToolsUtils;
import com.microfocus.application.automation.tools.EncryptionUtils;
import com.microfocus.application.automation.tools.Messages;
import com.microfocus.application.automation.tools.lr.model.ScriptRTSSetModel;
import com.microfocus.application.automation.tools.lr.model.SummaryDataLogModel;
import com.microfocus.application.automation.tools.model.*;
import com.microfocus.application.automation.tools.uft.model.SpecifyParametersModel;
import com.microfocus.application.automation.tools.uft.model.UftRunAsUser;
import com.microfocus.application.automation.tools.uft.model.UftSettingsModel;
import com.microfocus.application.automation.tools.uft.utils.UftToolUtils;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.Secret;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Describes a regular jenkins build step from UFT or LR
 */
public class RunFromService extends Builder implements SimpleBuildStep {

    private static final String LRANALYSIS_LAUNCHER_EXE = "LRAnalysisLauncher.exe";

    public static final String HP_TOOLS_LAUNCHER_EXE = "HpToolsLauncher.exe";
    public static final String HP_TOOLS_LAUNCHER_EXE_CFG = "HpToolsLauncher.exe.config";

    private String ResultFilename = "ApiResults.xml";

    private String ParamFileName = "ApiRun.txt";

    private RunFromServiceModel runFromServiceModel;

    private FileSystemTestSetModel fileSystemTestSetModel;
    private SpecifyParametersModel specifyParametersModel;
    private boolean isParallelRunnerEnabled;
    private boolean areParametersEnabled;
    private SummaryDataLogModel summaryDataLogModel;

    private ScriptRTSSetModel scriptRTSSetModel;

    private UftSettingsModel uftSettingsModel;

    private Map<Long, String> resultFileNames;


    // /**
    //  * Instantiates a new Run from file builder.
    //  *
    //  * @param fsTests the fs tests
    //  */
    // public RunFromService(String fsTests) {
    //     runFromServiceModel = new RunFromServiceModel(fsTests);
    // }

    // /**
    //  * Instantiates a new Run from file builder.
    //  *
    //  * @param runFromServiceModel the run from file model
    //  */
    // public RunFromService(RunFromServiceModel runFromServiceModel) {
    //     this.runFromServiceModel = runFromServiceModel;
    // }

    /**
     * @param fsTests                   the fs tests
     * @param fsTimeout                 the fs timeout
     * @param fsReportPath              the fs rerport path
     */
    @DataBoundConstructor
    public RunFromService(RunFromServiceModel runFromSm) {

    }

    public String getFsTimeout() {
        return runFromServiceModel.getFsTimeout();
    }

    /**
     * Sets fs timeout.
     *
     * @param fsTimeout the fs timeout
     */
    @DataBoundSetter
    public void setFsTimeout(String fsTimeout) {
        runFromServiceModel.setFsTimeout(fsTimeout);
    }

    public String getFsTests() {
        return runFromServiceModel.getFsTests();
    }

    public void setFsTests(String fsTests) {
        runFromServiceModel.setFsTests(fsTests);
    }

    /**
     * Get the fs report path.
     *
     * @return the filesystem report path
     */
    public String getFsReportPath() {
        return runFromServiceModel.getFsReportPath();
    }

    /**
     * Sets the report path
     *
     * @param fsReportPath the report path
     */
    @DataBoundSetter
    public void setFsReportPath(String fsReportPath) {
        runFromServiceModel.setFsReportPath(fsReportPath);
    }


    public Map<Long, String> getResultFileNames() {
        return resultFileNames;
    }

    @DataBoundSetter
    public void setResultFileNames(Map<Long, String> results) {
        resultFileNames = results;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener)
            throws IOException {
        PrintStream out = listener.getLogger();

        UftOctaneUtils.setUFTRunnerTypeAsParameter(build, listener);

        EnvVars env = null;
        try {
            env = build.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            listener.error("Failed loading build environment: " + e.getMessage());
        }

        Node currNode = JenkinsUtils.getCurrentNode(workspace);
        if (currNode == null) {
            listener.error("Failed to get current executor node.");
            return;
        }

        // in case of mbt, since mbt can support uft and codeless at the same time, run only if there are uft tests
        ParametersAction parameterAction = build.getAction(ParametersAction.class);
        ParameterValue octaneFrameworkParam = parameterAction != null ? parameterAction.getParameter("octaneTestRunnerFramework") : null;
        if (octaneFrameworkParam != null && octaneFrameworkParam.getValue().equals("MBT")) {
            String testsToRunConverted = env == null ? null : env.get(TestsToRunConverter.DEFAULT_TESTS_TO_RUN_CONVERTED_PARAMETER);
            if (StringUtils.isEmpty(testsToRunConverted)) {
                out.println(RunFromService.class.getSimpleName() + " : No UFT tests were found");
                return;
            }
        }

        // this is an unproper replacement to the build.getVariableResolver since workflow run won't support the
        // getBuildEnvironment() as written here:
        // https://github.com/jenkinsci/pipeline-plugin/blob/893e3484a25289c59567c6724f7ce19e3d23c6ee/DEVGUIDE
        // .md#variable-substitutions

        JSONObject jobDetails;
        // now merge them into one list
        Properties mergedProperties = new Properties();


        if (env == null) {
            listener.fatalError("Environment not set");
            throw new IOException("Env Null - something went wrong with fetching jenkins build environment");
        }

        if (build instanceof AbstractBuild) {
            VariableResolver<String> varResolver = ((AbstractBuild) build).getBuildVariableResolver();
        }

        mergedProperties.putAll(Objects.requireNonNull(runFromServiceModel).getProperties(env, currNode));

        if (areParametersEnabled) {
            try {
                specifyParametersModel.addProperties(mergedProperties, "Test", currNode);
            } catch (Exception e) {
                listener.error("Error occurred while parsing parameter input, reverting back to empty array.");
            }
        }
        boolean isPrintTestParams = UftToolUtils.isPrintTestParams(build, listener);
        mergedProperties.put("printTestParams", isPrintTestParams ? "1" : "0");

        UftRunAsUser uftRunAsUser;
        try {
            uftRunAsUser = UftToolUtils.getRunAsUser(build, listener);
            if (uftRunAsUser != null) {
                mergedProperties.put("uftRunAsUserName", uftRunAsUser.getUsername());
                if (StringUtils.isNotBlank(uftRunAsUser.getEncodedPassword())) {
                    mergedProperties.put("uftRunAsUserEncodedPassword", uftRunAsUser.getEncodedPasswordAsEncrypted(currNode));
                } else if (uftRunAsUser.getPassword() != null) {
                    mergedProperties.put("uftRunAsUserPassword", uftRunAsUser.getPasswordAsEncrypted(currNode));
                }
            }
        } catch(IllegalArgumentException | EncryptionUtils.EncryptionException e) {
            build.setResult(Result.FAILURE);
            listener.fatalError(String.format("Build parameters check failed: %s.", e.getMessage()));
            return;
        }
        int idx = 0;
        for (Iterator<String> iterator = env.keySet().iterator(); iterator.hasNext(); ) {
            String key = iterator.next();
            idx++;
            mergedProperties.put("JenkinsEnv" + idx, key + ";" + env.get(key));
        }

        Date now = new Date();
        Format formatter = new SimpleDateFormat("ddMMyyyyHHmmssSSS");
        String time = formatter.format(now);

        // get a unique filename for the params file
        ParamFileName = "props" + time + ".txt";
        ResultFilename = String.format("Results%s_%d.xml", time, build.getNumber());

        long threadId = Thread.currentThread().getId();
        if (resultFileNames == null) {
            resultFileNames = new HashMap<Long, String>();
        }
        resultFileNames.put(threadId, ResultFilename);

        mergedProperties.put("runType", AlmRunTypes.RunType.LoadRunner.toString());

        if (summaryDataLogModel != null) {
            summaryDataLogModel.addToProps(mergedProperties);
        }

        if (scriptRTSSetModel != null) {
            scriptRTSSetModel.addScriptsToProps(mergedProperties, env);
        }

        mergedProperties.put("resultsFilename", ResultFilename);


        // cleanup report folders before running the build
        String selectedNode = env.get("NODE_NAME");
        if (selectedNode == null) {//if slave is given in the pipeline and not as part of build step
            try {
                selectedNode = launcher.getComputer().getName();
            } catch (Exception e) {
                listener.error("Failed to get selected node for UFT execution : " + e.getMessage());
            }
        }

        // clean cleanuptests' report folders
        int index = 1;
        while (mergedProperties.getProperty("CleanupTest" + index) != null) {
            String testPath = mergedProperties.getProperty("CleanupTest" + index);
            List<String> cleanupTests = UftToolUtils.getBuildTests(selectedNode, testPath);
            for (String test : cleanupTests) {
                UftToolUtils.deleteReportFoldersFromNode(selectedNode, test, listener);
            }

            index++;
        }

        // clean actual tests' report folders
        index = 1;
        while (mergedProperties.getProperty("Test" + index) != null) {
            String testPath = mergedProperties.getProperty(("Test" + index));
            List<String> buildTests = UftToolUtils.getBuildTests(selectedNode, testPath);
            for (String test : buildTests) {
                UftToolUtils.deleteReportFoldersFromNode(selectedNode, test, listener);
            }
            index++;
        }

        mergedProperties.setProperty("numOfTests", String.valueOf(index - 1));

        // get properties serialized into a stream
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            mergedProperties.store(stream, "");
        } catch (IOException e) {
            listener.error("Storing run variable failed: " + e);
            build.setResult(Result.FAILURE);
        }

        String propsSerialization = stream.toString();
        
        FilePath CmdLineExe;
        try (InputStream propsStream = IOUtils.toInputStream(propsSerialization)) {
            // Get the URL to the Script used to run the test, which is bundled
            // in the plugin
            @SuppressWarnings("squid:S2259")
            URL cmdExeUrl = Jenkins.get().pluginManager.uberClassLoader.getResource(HP_TOOLS_LAUNCHER_EXE);
            if (cmdExeUrl == null) {
                listener.fatalError(HP_TOOLS_LAUNCHER_EXE + " not found in resources");
                return;
            }

            @SuppressWarnings("squid:S2259")
            URL cmdExeCfgUrl = Jenkins.get().pluginManager.uberClassLoader.getResource(HP_TOOLS_LAUNCHER_EXE_CFG);
            if (cmdExeCfgUrl == null) {
                listener.fatalError(HP_TOOLS_LAUNCHER_EXE_CFG + " not found in resources");
                return;
            }

            @SuppressWarnings("squid:S2259")
            URL cmdExe2Url = Jenkins.get().pluginManager.uberClassLoader.getResource(LRANALYSIS_LAUNCHER_EXE);
            if (cmdExe2Url == null) {
                listener.fatalError(LRANALYSIS_LAUNCHER_EXE + "not found in resources");
                return;
            }

            FilePath propsFileName = workspace.child(ParamFileName);
            CmdLineExe = workspace.child(HP_TOOLS_LAUNCHER_EXE);
            FilePath CmdLineExeCfg = workspace.child(HP_TOOLS_LAUNCHER_EXE_CFG);
            FilePath CmdLineExe2 = workspace.child(LRANALYSIS_LAUNCHER_EXE);

            try {
                // create a file for the properties file, and save the properties
                propsFileName.copyFrom(propsStream);
                // Copy the script to the project workspace
                CmdLineExe.copyFrom(cmdExeUrl);
                CmdLineExeCfg.copyFrom(cmdExeCfgUrl);
                CmdLineExe2.copyFrom(cmdExe2Url);
            } catch (IOException | InterruptedException e) {
                build.setResult(Result.FAILURE);
                listener.error("Copying executable files to executing node " + e);
            }
        }

        try {
            // Run the HpToolsLauncher.exe
            AlmToolsUtils.runOnBuildEnv(build, launcher, listener, CmdLineExe, ParamFileName, currNode);
            // Has the report been successfully generated?
        } catch (IOException ioe) {
            Util.displayIOException(ioe, listener);
            build.setResult(Result.FAILURE);
            listener.error("Failed running HpToolsLauncher " + ioe.getMessage());
        } catch (InterruptedException e) {
            build.setResult(Result.ABORTED);
            listener.error("Failed running HpToolsLauncher - build aborted " + StringUtils.defaultString(e.getMessage()));
            try {
                AlmToolsUtils.runHpToolsAborterOnBuildEnv(build, launcher, listener, ParamFileName, workspace);
            } catch (IOException e1) {
                Util.displayIOException(e1, listener);
                build.setResult(Result.FAILURE);
            } catch (InterruptedException e1) {
                listener.error("Failed running HpToolsAborter " + e1.getMessage());
            }
        }
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Gets run from file model.
     *
     * @return the run from file model
     */
    public RunFromServiceModel getrunFromServiceModel() {
        return runFromServiceModel;
    }

    /**
     * Gets run results file name.
     *
     * @return the run results file name
     */
    public String getRunResultsFileName() {
        synchronized (this) {
            long threadId = Thread.currentThread().getId();
            String fileName = resultFileNames.get(threadId);
            return fileName;
        }
    }


    /**
     * The type Descriptor.
     */
    @Symbol("runFromServiceBuilder")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * Instantiates a new Descriptor.
         */
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(
                @SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }


        @Override
        public String getDisplayName() {
            return Messages.RunFromFileBuilderStepName(Messages.LrunSvc());
        }

        /**
         * Do check fs tests form validation.
         *
         * @param value the value
         * @return the form validation
         */
        @SuppressWarnings("squid:S1172")
        public FormValidation doCheckFsTests(@QueryParameter String value) {
            //TODO
            return FormValidation.ok();
        }

        /**
         * Do check ignore error strings form validation.
         *
         * @param value the value
         * @return the form validation
         */
        @SuppressWarnings("squid:S1172")
        public FormValidation doCheckIgnoreErrorStrings(@QueryParameter String value) {

            return FormValidation.ok();
        }

        /**
         * Do check fs timeout form validation.
         *
         * @param value the value
         * @return the form validation
         */
        public FormValidation doCheckFsTimeout(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.ok();
            }

            String sanitizedValue = value.trim();
            if (sanitizedValue.length() > 0 && sanitizedValue.charAt(0) == '-') {
                sanitizedValue = sanitizedValue.substring(1);
            }

            if (!isParameterizedValue(sanitizedValue) && !StringUtils.isNumeric(sanitizedValue)) {
                return FormValidation.error("Timeout must be a parameter or a number, e.g.: 23, $Timeout or " +
                        "${Timeout}.");
            }

            return FormValidation.ok();
        }


        /**
         * Check if the value is parameterized.
         *
         * @param value the value
         * @return boolean
         */
        public boolean isParameterizedValue(String value) {
            //Parameter (with or without brackets)
            return value.matches("^\\$\\{[\\w-. ]*}$|^\\$[\\w-.]*$");
        }


        public List<String> getNodes() {
            return UftToolUtils.getNodesList();
        }

        // public List<String> getEncodings() { return RunFromServiceModel.encodings; }
    }

}