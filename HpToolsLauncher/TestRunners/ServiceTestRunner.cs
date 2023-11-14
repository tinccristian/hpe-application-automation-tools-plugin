using System;
using System.Diagnostics;
using System.IO;
using System.Threading;

namespace HpToolsLauncher.TestRunners
{
    public class ServiceTestRunner
    {
        public string binPath = Environment.ExpandEnvironmentVariables("%LR_PATH%bin");
        public string clientPath = @"C:\test_lrun_svc\client";
        public string[] processesToKill = new string[] { "wlrun", "lrun_svc", "CmdServiceClient" };

        public bool RunServiceTest(string testPath, string resultsPath, int timeout)
        {
            timeout *= 1000;
            Cleanup(processesToKill);
            Console.WriteLine("[{0}] Cleanup complete.", DateTime.Now.ToString("h:mm:ss tt"));

            StartLrunSvc(timeout);
            Console.WriteLine("[{0}] lrun_svc.exe is running", DateTime.Now.ToString("h:mm:ss tt"));
            Process client = StartClient(clientPath,timeout);
            Console.WriteLine("[{0}] The client is running.", DateTime.Now.ToString("h:mm:ss tt"));

            string command = "setLoadTestData " + testPath;
            Write(client, command, timeout);
            Console.WriteLine("[{0}] Command was:{1}", DateTime.Now.ToString("h:mm:ss tt"), command);
            string result = Read(client);
            Console.WriteLine("[{0}] Client response: {1}.", DateTime.Now.ToString("h:mm:ss tt"), result);
            if (result.Contains("failed"))
            {
                return false;
            }

            Write(client,"startLoadTest",timeout);

            Stopwatch stopWatch = new Stopwatch();
            stopWatch.Start();
            var startTime= DateTime.Now;

            result = Read(client);
            Console.WriteLine("[{0}] Client response: {1}.", DateTime.Now.ToString("h:mm:ss tt"), result);
            if (result.Contains("failed"))
            {
                return false;
            }
            Console.WriteLine("[{0}] The test has started, waiting for the test to end.", DateTime.Now.ToString("h:mm:ss tt"));

            result = "";
            while (!result.Contains("Ended"))
            {
                Write(client, "getServiceState", 1000);     //ready, collating, running, ended
                result = Read(client);
                Thread.Sleep(1000);
            }


            stopWatch.Stop();
            TimeSpan ts = stopWatch.Elapsed;
            string elapsedTime = String.Format("{0:00}:{1:00}:{2:00}.{3:00}",
            ts.Hours, ts.Minutes, ts.Seconds,
            ts.Milliseconds / 10);
            Console.WriteLine("[{0}] Test completed in {1}.", DateTime.Now.ToString("h:mm:ss tt"), elapsedTime);

            Dispose(client);
            Cleanup(processesToKill);
            return true;
        }

        #region Process utilities
        public void StartLrunSvc(int timeout)
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
            System.Threading.Thread.Sleep(timeout);
        }

        public Process StartClient(string clientPath, int timeout)
        {
            var startInfo = new System.Diagnostics.ProcessStartInfo
            {
                FileName = clientPath + @"\CmdServiceClient.exe",
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                UseShellExecute = false
            };

            var proc = new Process
            {
                StartInfo = startInfo
            };
            proc.Start();
            System.Threading.Thread.Sleep(timeout);
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
        #endregion

        #region Client communication
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
