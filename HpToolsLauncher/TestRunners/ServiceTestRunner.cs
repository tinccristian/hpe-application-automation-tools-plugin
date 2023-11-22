using System;
using System.Diagnostics;
using System.IO;
using System.Threading;

namespace HpToolsLauncher.TestRunners
{
    public class ServiceTestRunner
    {
        public string binPath = Environment.ExpandEnvironmentVariables("%LR_PATH%bin");
        public string[] processesToKill = new string[] { "wlrun", "lrun_svc" };
        public int _timeout;

        public bool RunServiceTest(string testPath, string resultsDirectory, int timeout)
        {
            _timeout = timeout * 1000;
            Cleanup(processesToKill);
            LogMessage("Cleanup complete.");

            //Start lrun_svc.exe and CmdServiceClient.exe
            StartLrunSvc();
            LogMessage("lrun_svc.exe is running");
            Process client = StartClient();
            LogMessage("CmdServiceClient.exe is running");

            //Set the load test data to the given .lrs
            string command = "setLoadTestData " + testPath;
            Write(client, command);
            LogMessage("Command given:",command);

            string result = Read(client);
            LogMessage("Client response:", result);
            if ((result.Contains("failed") || (result.Contains("empty")))) return false;

            //Set the results folder directory
            string command1 ="setResultsDirectory " + resultsDirectory;
            Write(client, command1);
            LogMessage("Command given:", command1);

            result = Read(client);
            LogMessage("Client response:",result);
            if ((result.Contains("failed")|| (result.Contains("empty"))))   return false;

            //Start the load test
            Write(client,"startLoadTest");
            LogMessage("Command given: startLoadTest");

            result = Read(client);
            LogMessage("Client response:",result);
            if (result.Contains("failed"))
            {
                return false;
            }
            LogMessage("The test has started, waiting for the test to end.");
            Stopwatch stopWatch = new Stopwatch();
            stopWatch.Start();
            var startTime= DateTime.Now;

            //Get the result
            result = "";
            while (!result.Contains("Ended"))               //wait for the test to end
            {
                Write(client, "getServiceState", 1000);     //ready, collating, running, ended
                result = Read(client);
                if ((result.Contains("failed") || (result.Contains("empty"))))
                {
                    LogMessage(result);
                    return false;
                }
                Thread.Sleep(1000);
            }

            stopWatch.Stop();
            TimeSpan ts = stopWatch.Elapsed;
            string elapsedTime = String.Format("{0:00}:{1:00}:{2:00}.{3:00}",
            ts.Hours, ts.Minutes, ts.Seconds,
            ts.Milliseconds / 10);
            LogMessage("Test completed in", elapsedTime);
            Dispose(client);
            Cleanup(processesToKill);
            return true;
        }

        #region Process utilities
        public void StartLrunSvc()
        {
            var startInfo = new System.Diagnostics.ProcessStartInfo
            {
                WorkingDirectory = binPath,
                FileName = "lrun_svc.exe",
            };
            var proc = new Process
            {
                StartInfo = startInfo
            };
            proc.Start();
            System.Threading.Thread.Sleep(_timeout);
        }

        public Process StartClient()
        {
            var startInfo = new System.Diagnostics.ProcessStartInfo
            {
                FileName = binPath + @"\CmdServiceClient.exe",
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                UseShellExecute = false
            };

            var proc = new Process
            {
                StartInfo = startInfo
            };
            proc.Start();
            System.Threading.Thread.Sleep(_timeout);
            return proc;
        }

        public void Cleanup(string[] processes)
        {
            foreach (string process in processes)
            {
                Process[] workers = Process.GetProcessesByName(process);
                foreach (Process worker in workers)
                {
                    try
                    {
                        worker.Kill();
                        worker.WaitForExit();
                        worker.Dispose();
                    }
                    catch (UnauthorizedAccessException)
                    {
                        continue;
                    }

                }
            }

        }

        public void LogMessage(string message)
        {
            Console.WriteLine("[{0}] {1}", DateTime.Now.ToString("h:mm:ss tt"), message);
        }

        public void LogMessage(string message, string extraInfo)
        {
            Console.WriteLine("[{0}] {1} {2}", DateTime.Now.ToString("h:mm:ss tt"), message, extraInfo);
        }
        #endregion

        #region Client communication
        public void Write(Process client, string command)
        {
            StreamWriter writer = client.StandardInput;
            writer.WriteLine(command);
            System.Threading.Thread.Sleep(_timeout);
            writer.Flush();
        }
        public void Write(Process client, string command, int timeout)
        {
            StreamWriter writer = client.StandardInput;
            writer.WriteLine(command);
            System.Threading.Thread.Sleep(timeout);
            writer.Flush();
        }
        public string Read(Process client)
        {
            StreamReader reader = client.StandardOutput;
            string result = "";
            do
            {
                int output = reader.Read();
                result += (char)output;
            }
            while (reader.Peek() > -1);
            reader.DiscardBufferedData();
            return result;
        }
        public void Dispose(Process client)
        {
            client.StandardInput.Close();
            client.StandardOutput.Close();
            client.Dispose();
        }
    }
    #endregion
}
