package com.Keploy

import com.fasterxml.jackson._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.jcef.{JBCefBrowser, JBCefClient, JBCefJSQuery}
import com.jediterm.terminal.ui.{TerminalWidget, TerminalWidgetListener}
import org.cef.CefApp
import org.cef.browser.{CefBrowser, CefFrame}
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.plugins.terminal.{ShellTerminalWidget, TerminalToolWindowFactory, TerminalToolWindowManager}
import org.yaml.snakeyaml.Yaml

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.{JComponent, SwingUtilities}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Properties, Success}

case class KeployWindow(project: Project) {

  private lazy val webView: JBCefBrowser = {
    val browser = new JBCefBrowser()
    registerAppSchemeHandler()
    val url = if (isKeployConfigPresent) "http://myapp/index.html" else "http://myapp/config.html"
    browser.loadURL(url)
    Disposer.register(project, browser)

    val client: JBCefClient = browser.getJBCefClient()
    val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryPrevTests: JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryConfig : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryRecordTests : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryReplayTests : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryCloseTerminal : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryOpenLog : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryRunConfig : JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryOpenYamlFile : JBCefJSQuery = JBCefJSQuery.create(browser)

    jsQueryOpenLog.addHandler((logPath: String) => {
      //copy it to a .log file
        val logFile = logPath.replace(".tmp", ".log")
        Files.copy(Paths.get(logPath), Paths.get(logFile))
      openDocumentInEditor(logFile)
      null
    })

    jsQueryOpenYamlFile.addHandler((filePath: String) => {
      openDocumentInEditor(filePath)
      null
    })

    jsQueryRunConfig.addHandler((_: String) => {
      println("Running keploy config --generate")
      if(isWindows){
        openTerminalAndExecuteCommand("wsl" , "" , toExit = false , isRecording = false , isReplaying= false)
        // Wait for a few seconds before executing the next command
        Thread.sleep(5000)
      }
      openTerminalAndExecuteCommand("keploy config --generate" , "" , toExit = true , isRecording = false , isReplaying= false)
      null
    })

    jsQueryRecordTests.addHandler((_: String) => {
      println("Recording test cases")
      val record_script  = getScriptPath("keploy_record_script")
      val record_log_file = getLogPath("record_mode.log")
      if (isWindows) {
        openTerminalAndExecuteCommand("wsl" , "" , toExit = false , isRecording = false , isReplaying= false)
        // Wait for a few seconds before executing the next command
        Thread.sleep(5000)
        openTerminalAndExecuteCommand(record_script , record_log_file , toExit = true , isRecording = true , isReplaying= false)
      } else {
        openTerminalAndExecuteCommand(record_script , record_log_file , toExit = true ,  isRecording = true , isReplaying= false)
      }
      null
    })
    jsQueryReplayTests.addHandler((_: String) => {
      println("Replaying test cases")
      val replay_script  = getScriptPath("keploy_test_script")
      val replay_log_file = getLogPath("test_mode.log")
      if (isWindows) {
        openTerminalAndExecuteCommand("wsl" , "" , toExit = false ,isRecording = false , isReplaying= false)
        // Wait for a few seconds before executing the next command
        Thread.sleep(5000)
        openTerminalAndExecuteCommand(replay_script , replay_log_file , toExit = true, isRecording = false , isReplaying= true)
      } else {
        openTerminalAndExecuteCommand(replay_script , replay_log_file , toExit = true , isRecording = false , isReplaying= true)
      }
      null
    })

    jsQueryCloseTerminal.addHandler((_: String) => {
      println("Closing terminal from Javascript")
        //get the current terminal widget
        val terminalView = TerminalToolWindowManager.getInstance(project)
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        if (toolWindow == null) {
          throw new IllegalStateException("Terminal tool window not found")
        }
        val contentManager = toolWindow.getContentManager
        val content = contentManager.findContent("Keploy")
        val shellTerminalWidget = if (content == null) {
          terminalView.createLocalShellWidget(project.getBasePath, "Keploy")
        } else {
          TerminalToolWindowManager.getWidgetByContent(content).asInstanceOf[ShellTerminalWidget]
        }
        shellTerminalWidget.close()
      null
    })

    jsQuery.addHandler((appCommandAndPath: String) => {
      val Array(appCommand, path) = appCommandAndPath.split("@@")
      initializeConfig(appCommand, path)
      null
    })
    jsQueryConfig.addHandler((_: String) => {
      openDocumentInEditor(Paths.get(project.getBasePath, "keploy.yml").toString)
      null
    })
    jsQueryPrevTests.addHandler((_: String) => {
      println("Displaying previous test results")
      val reportsFolderPath = Paths.get(project.getBasePath, "/keploy/reports")
      if (Files.exists(reportsFolderPath)) {
        val reportsFolder = new File(reportsFolderPath.toString)
        if (reportsFolder.listFiles().isEmpty) {
          previousTestResultsHandler(0, 0, 0, isError = true, "No test reports found.", null)
        } else {
          // Function to parse YAML
          val sortedReports = reportsFolder.listFiles().sortWith(_.lastModified() > _.lastModified())
          var totalSuccess = 0
          var totalFailure = 0
          var totalTests = 0
          val testResults = scala.collection.mutable.ListBuffer[Map[String, String]]()

          val yaml = new Yaml()
//          println("Reading test reports")
          sortedReports.foreach { testRunDir =>
            val testRunPath = Paths.get(reportsFolder.toString, testRunDir.getName)
            val testFiles = Files.list(testRunPath).iterator().asScala
              .filter(_.getFileName.toString.endsWith(".yaml"))
              .toList
            testFiles.foreach { testFile =>
              val testFilePath = testFile.toString
              val ios = Files.newInputStream(Paths.get(testFilePath))
//              println(s"Reading test file: $testFilePath")
//              println(fileContents)
              try {
                val mapper = new ObjectMapper().registerModules(DefaultScalaModule)
                val report = yaml.loadAs(ios, classOf[Any])
//                println(s"Parsed report: $report")
                val jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) // Formats YAML to a pretty printed JSON string - easy to read
                val jsonObj = mapper.readTree(jsonString)
//                println(s"JSON: $jsonObj")
//                println(s"Success: ${jsonObj.get("success")}," +
//                  s" Failure: ${jsonObj.get("failure")}," +
//                  s" Total: ${jsonObj.get("total")}")
                val success = jsonObj.get("success").asInt()
                val failure = jsonObj.get("failure").asInt()
                val total = jsonObj.get("total").asInt()
                totalSuccess += success
                totalFailure += failure
                totalTests += total
        val tests = jsonObj.get("tests")
                if (tests != null) {
                  tests.forEach { test =>
                    val req = test.get("req")
                    val timestamp = req.get("timestamp").asText().toLong
                    val date = new Date(timestamp)
                    testResults += Map(
                      "date" -> new SimpleDateFormat("dd/MM/yyyy").format(date),
                      "method" -> req.get("method").asText(),
                      "name" -> test.get("test_case_id").asText(),
                      "report" -> test.get("name").asText(),
                      "status" -> test.get("status").asText(),
                      "testCasePath" -> testFilePath
                    )
                  }
                }
              } catch {
                case e: Exception =>
                  println(s"Error parsing test file $testFilePath: ${e.getMessage}")
              }
            }
          }
//          println(s"Final Aggregated Results - Success: $totalSuccess, Failure: $totalFailure, Total: $totalTests")
          previousTestResultsHandler(totalSuccess, totalFailure, totalTests, isError = false, "Previous test results displayed.", testResults)

        }
      } else {
        previousTestResultsHandler(0, 0, 0, isError = true, "Run keploy test to generate test reports." , null)
      }
      null
    })

    client.addLoadHandler(new CefLoadHandlerAdapter {
      override def onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int): Unit = {
        if (frame.isMain) {

          browser.executeJavaScript(
            "window.runConfig = function() {" +
              jsQueryRunConfig.inject("runConfig") +
              "};",
            frame.getURL(), 0
          )


          browser.executeJavaScript(
            "window.initializeConfig = function(appCommandAndPath) {" +
              jsQuery.inject("appCommandAndPath") +
              "};",
            frame.getURL(), 0
          )
          browser.executeJavaScript(
            "window.historyPage = function() {" +
              jsQueryPrevTests.inject("historyPage") +
              "};",
            frame.getURL(), 0
          )
          browser.executeJavaScript(
            "window.openConfig = function() {" +
              jsQueryConfig.inject("openConfig") +
              "};",
            frame.getURL(), 0
          )
          browser.executeJavaScript(
            "window.recordTestCases = function() {" +
              jsQueryRecordTests.inject("recordTestCases") +
              "};",
            frame.getURL(), 0
          )



          browser.executeJavaScript(
            "window.replayTestCases = function() {" +
              jsQueryReplayTests.inject("replayTestCases") +
              "};",
            frame.getURL(), 0
          )

          browser.executeJavaScript(
            "window.openLogFile = function(logPath) {" +
              jsQueryOpenLog.inject("logPath") +
              "};",
            frame.getURL(), 0
          )

          browser.executeJavaScript(
            "window.openYamlFile = function(filePath) {" +
              jsQueryOpenYamlFile.inject("filePath") +
              "};",
            frame.getURL(), 0
          )
          browser.executeJavaScript(
            "window.closeTerminal = function() {" +
              jsQueryCloseTerminal.inject("closeTerminal") +
                "};",
            frame.getURL(), 0
          )

          val configInitializeJs =
            """
              document.getElementById('initialiseConfigButton').addEventListener('click', function() {
                var appCommand = document.getElementById('configCommand').value;
                var path = document.getElementById('configPath').value;
                window.initializeConfig(appCommand + "@@" + path);
              });
            """
          browser.executeJavaScript(configInitializeJs, frame.getURL, 0)



          val runConfigJs =
            """
              document.getElementById('setupConfig').addEventListener('click', function() {
                window.runConfig();
              });
            """
          browser.executeJavaScript(runConfigJs, frame.getURL, 0)

          val closeTerminalEvent =
            """
              document.getElementById('stopRecordingButton').addEventListener('click', function() {
                window.closeTerminal();
              });
              document.getElementById('stopTestingButton').addEventListener('click', function() {
                window.closeTerminal();
              });
            """
            browser.executeJavaScript(closeTerminalEvent, frame.getURL, 0)

          val historyPageJs =
            """
              document.getElementById('displayPreviousTestResults').addEventListener('click', function() {
                window.historyPage();
              });
            """
          browser.executeJavaScript(historyPageJs, frame.getURL, 0)
          val openConfigJs =
            """
              document.getElementById('openConfig').addEventListener('click', function() {
                window.openConfig();
              });
              """
            browser.executeJavaScript(openConfigJs, frame.getURL, 0)

          val recordTestCasesJs =
            """
              document.getElementById('startRecordingButton').addEventListener('click', function() {
              window.recordTestCases();
              });
            """
            browser.executeJavaScript(recordTestCasesJs, frame.getURL, 0)

          val replayTestCasesJs =
            """
              document.getElementById('startTestingButton').addEventListener('click', function() {
              window.replayTestCases();
              });
            """
            browser.executeJavaScript(replayTestCasesJs, frame.getURL, 0)
        }
      }
    }, browser.getCefBrowser)

    browser
  }

  def content: JComponent = webView.getComponent

  private def registerAppSchemeHandler(): Unit = {
    CefApp.getInstance().registerSchemeHandlerFactory(
      "http",
      "myapp",
      new CustomSchemeHandlerFactory
    )
  }

  import java.io.{File, FileNotFoundException, InputStream}
  import java.nio.file.{Files, Paths}

  private def getScriptPath(scriptName: String): String = {
    var resourceUrl = getClass.getResource(s"/scripts/bash/$scriptName.sh")
    if (isZsh) {
      resourceUrl = getClass.getResource(s"/scripts/zsh/$scriptName.zsh")
    }

    if (resourceUrl != null) {
      val filePath = if (resourceUrl.getProtocol == "jar") {
        // Resource is inside a JAR file, copy it to a temporary file
        val inputStream: InputStream = resourceUrl.openStream()
        val tempFile = Files.createTempFile(scriptName, ".sh")
        Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        inputStream.close()
        tempFile.toFile.setExecutable(true)  // Ensure the file is executable
        //check if the file is present
        if (tempFile.toFile.exists())
          println("File exists")
        tempFile.toAbsolutePath.toString
      } else {
        // Resource is not in a JAR, can use the path directly
        new File(resourceUrl.toURI).getAbsolutePath
      }

      // Normalize the path to ensure it's correctly formatted for the operating system
      val osNormalizedPath = Paths.get(filePath).toString
//      // If running on Windows, ensure the path is properly formatted for the shell
      if (Properties.isWin) {
        osNormalizedPath.replace("C:", "/mnt/c").replace("\\", "/")
      } else {
        osNormalizedPath
      }
//      osNormalizedPath

    } else {
      throw new FileNotFoundException(s"Script $scriptName not found in plugin resources")
    }
  }

  private def getLogPath(logName: String): String = {
    val resourceUrl = getClass.getResource(s"/scripts/logs/$logName")

    if (resourceUrl != null) {
      val filePath = if (resourceUrl.getProtocol == "jar") {
        // Resource is inside a JAR file, copy it to a temporary file
        val inputStream: InputStream = resourceUrl.openStream()
        val tempFile = Files.createTempFile(logName, null)
        Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        inputStream.close()
        tempFile.toAbsolutePath.toString
      } else {
        // Resource is not in a JAR, can use the path directly
        new File(resourceUrl.toURI).getAbsolutePath
      }

      // Normalize the path to ensure it's correctly formatted for the operating system
      val osNormalizedPath = Paths.get(filePath).toString

      //      // If running on Windows, ensure the path is properly formatted for the shell
      if (Properties.isWin) {
        osNormalizedPath.replace("C:", "/mnt/c").replace("\\", "/")
      } else {
        osNormalizedPath
      }
    } else {
      throw new FileNotFoundException(s"Log $logName not found in plugin resources")
    }
  }

  private def isZsh: Boolean = {
    val shell = System.getenv("SHELL")
    shell != null && shell.toLowerCase.contains("zsh")
  }
  private def openTerminalAndExecuteCommand(command: String , logFile : String , toExit : Boolean , isRecording : Boolean , isReplaying : Boolean): Unit = {
    SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        try {
          val terminalView = TerminalToolWindowManager.getInstance(project)
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
          if (toolWindow == null) {
            throw new IllegalStateException("Terminal tool window not found")
          }

          val contentManager = toolWindow.getContentManager
          val content = contentManager.findContent("Keploy")
          val shellTerminalWidget = if (content == null) {
            terminalView.createLocalShellWidget(project.getBasePath, "Keploy")
          } else {
            TerminalToolWindowManager.getWidgetByContent(content).asInstanceOf[ShellTerminalWidget]
          }
          var commandToExec = s"$command  $logFile "
          if (toExit) {
            commandToExec = s"$command  $logFile ; exit 0"
            if (isWindows) {
              commandToExec = s"$command  $logFile ; exit 0 ; exit 0"
            }
          }
//          val commandToExec = s"$command  $logFile ; exit 0"
          shellTerminalWidget.executeCommand(commandToExec)
          if(isRecording || isReplaying) {
            shellTerminalWidget.addListener(new TerminalWidgetListener {
              override def allSessionsClosed(terminalWidget: TerminalWidget) = {
                terminalWidget.removeListener(this)
                println("Closing terminal")
                var terminalLogFile = logFile
                if (isWindows) {
                  terminalLogFile = terminalLogFile.replace("/mnt/c", "C:")
                }
                if (isRecording) {
                  println("Recording completed")

                  //add event listener to viewRecordLogsButton
                  webView.getCefBrowser.executeJavaScript(
                    s"""
                         const viewRecordLogsButton = document.getElementById('viewRecordLogsButton');
                         viewRecordLogsButton.addEventListener('click', function() {
                         window.openLogFile("${terminalLogFile}");
                         });
                    """, webView.getCefBrowser.getURL, 0
                  )

                  val logData = Files.readAllLines(Paths.get(terminalLogFile)).toArray.mkString("\n")
                  val testSetName = extractTestSetName(logData)
                  val logLines = logData.split("\n")
                  val capturedTestLines = logLines.filter(line => line.contains("🟠 Keploy has captured test cases"))
                  if (capturedTestLines.length == 0) {
                    println("No test cases captured")
                    displayRecordedTestCases("No test cases captured", noTestCases = true, path = null, testSetName = null)
                  } else {
                    println("Test cases captured")
                    capturedTestLines.foreach { testLine =>
      //TODO : Check if this works on linux paths
                      if (testLine.contains("path") && testLine.contains("testcase name")) {
                        // Extracting testSetPath
                        val pathPart = testLine.split("path\":")(1).trim
                        var testSetPath = pathPart.split("\",")(0).replace("\"", "").trim

                        // Extracting testSetName which is the 2nd last part of the path
                        val testSetName = testSetPath.split("/").reverse(1)

                        // Extracting testCaseName
                        val testCasePart = testLine.split("testcase name\":")(1).trim
                        val testCaseName = testCasePart.split("}")(0).replace("\"", "").trim
                        testSetPath = testSetPath + "/" + testCaseName + ".yaml"
                        displayRecordedTestCases(testCaseName, noTestCases = false, path = testSetPath, testSetName = testSetName)
                      } else {
                        println("No valid path or testcase name found in the test line.")
                      }
                    }
                  }
                } else if (isReplaying) {
                  println("Replaying completed")
                  webView.getCefBrowser.executeJavaScript(
                    s"""
                         const viewTestLogsButton = document.getElementById('viewTestLogsButton');
                         viewTestLogsButton.addEventListener('click', function() {
                         window.openLogFile("${terminalLogFile}");
                         });
                    """, webView.getCefBrowser.getURL, 0
                  )

                  val logData = Files.readAllLines(Paths.get(terminalLogFile)).toArray.mkString("\n")
                  val logLines = logData.split("\n")
                  val startIndex = logLines.indexWhere(line => line.contains("COMPLETE TESTRUN SUMMARY."))
                  if (startIndex == -1) {
                    println("Start index not found")
                    displayTestResults("No test results found", isError = true, null)
                    //TODO : Find a way to break here
                  }
                  val endIndex = logLines.zipWithIndex.indexWhere { case (line, index) =>
                    index > startIndex && line.contains("<=========================================>")
                  }
                  if (endIndex == -1) {
                    println("End index not found")
                    displayTestResults("No test results found", isError = true, null)

                  }
                  val testResults = logLines.slice(startIndex, endIndex + 1)
                  //join the test results with \n
                  val testResultsString = testResults.mkString("\n")
                  if (testResultsString.isEmpty) {
                    println("No test results found")
                    displayTestResults("No test results found", isError = true, null)
                  } else {
                    println("Test results found")
//                    val ansiRegex: Regex = """[\u001B\u009B][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]""".r
                    val ansiRegex: Regex = """[\u001B\u009B][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]]""".r

                    val cleanSummary = ansiRegex.replaceAllIn(testResultsString, "")
                    println(cleanSummary)

                    val testSummaryList = cleanSummary.split('\n').toBuffer
                    println(testSummaryList)

                    // Remove last line of summary which is pattern
                    testSummaryList.remove(testSummaryList.length - 1)

                    // Remove first line of summary which is header
                    testSummaryList.remove(0)
                    println(testSummaryList)

//                    TODO: Implement Complete Summary
                    displayTestResults("Test results found", isError = false, testSummaryList)
                  }
                }
              }})
        }
        } catch {
          case e: IOException => e.printStackTrace()
          case e: Exception => e.printStackTrace()
        }
      }
    })
  }


  private def extractTestSetName(logContent: String): Option[String] = {
    // Define the regular expression pattern to find the test set name
    val regex = """Keploy has captured test cases for the user's application\.\s*\{"path": ".*\/(test-set-\d+)\/tests"""".r

    // Execute the regular expression on the log content
    val matcher = regex.findFirstMatchIn(logContent)

      // Check if a match was found and return the test set name, otherwise return None
    matcher.map(_.group(1))
  }
  private def isKeployConfigPresent: Boolean = {
    val configPath = Paths.get(project.getBasePath, "keploy.yml")
    Files.exists(configPath)
  }
  private def isWindows: Boolean = {
    System.getProperty("os.name").toLowerCase.contains("win")
  }
  private def initializeConfig(appCommand: String, path: String): Unit = {
    val folderPath = project.getBasePath
    val contentPath = if (path.isEmpty) "./" else path
    val configFilePath = Paths.get(folderPath, "keploy.yml")
    val content =
      s"""
         |path: "$contentPath"
         |appId: ""
         |command: "$appCommand"
         |port: 0
         |proxyPort: 16789
         |dnsPort: 26789
         |debug: false
         |disableANSI: false
         |disableTele: false
         |inDocker: false
         |generateGithubActions: true
         |containerName: ""
         |networkName: ""
         |buildDelay: 30
         |test:
         |  selectedTests: {}
         |  globalNoise:
         |    global: {}
         |    test-sets: {}
         |  delay: 5
         |  apiTimeout: 5
         |  coverage: false
         |  goCoverage: false
         |  coverageReportPath: ""
         |  ignoreOrdering: true
         |  mongoPassword: "default@123"
         |  language: ""
         |  removeUnusedMocks: false
         |record:
         |  recordTimer: 0s
         |  filters: []
         |configPath: ""
         |bypassRules: []
         |cmdType: "native"
         |enableTesting: false
         |fallbackOnMiss: false
         |keployContainer: "keploy-v2"
         |keployNetwork: "keploy-network"
         |
         |# This config file has been initialized
         |
         |# Visit https://keploy.io/docs/running-keploy/configuration-file/ to learn about using Keploy through the configuration file.
      """.stripMargin

    Future {
      val writer = Files.newBufferedWriter(configFilePath)
      writer.write(content)
      writer.close()
    }.onComplete {
      case Success(_) =>
        println("Keploy config file initialized successfully.")
        openDocumentInEditor(configFilePath.toString)
        webView.loadURL("http://myapp/index.html")
      case Failure(exception) =>
        println(s"Failed to initialize config file: ${exception.getMessage}")
    }
  }

  import com.fasterxml.jackson.databind.ObjectMapper
  import com.fasterxml.jackson.module.scala.DefaultScalaModule

  import scala.collection.mutable

  case class TestResult(
                         date: String,
                         method: String,
                         name: String,
                         report: String,
                         status: String,
                         testCasePath: String
                       )

  private def previousTestResultsHandler(
                                          success: Int,
                                          failure: Int,
                                          total: Int,
                                          isError: Boolean,
                                          message: String,
                                          testResults: Any
                                        ): Unit = {
    val data = Map(
      "type" -> "aggregatedTestResults",
      "value" -> Map(
        "total" -> total,
        "success" -> success,
        "failure" -> failure,
        "error" -> isError,
        "message" -> message,
        "testResults" -> testResults
      )
    )

    // Convert the Scala Map to JSON
    val mapper = new ObjectMapper().registerModules(DefaultScalaModule)
    val jsonData = mapper.writeValueAsString(data)
    webView.getCefBrowser.executeJavaScript(
      s"""
       const lastTestResultsDiv = document.getElementById('lastTestResults');
       const totalTestCasesDiv = document.getElementById('totalTestCases');
       const testSuiteNameDiv = document.getElementById('testSuiteName');
       const testCasesPassedDiv = document.getElementById('testCasesPassed');
       const testCasesFailedDiv = document.getElementById('testCasesFailed');

       // Clear previous content
       if (totalTestCasesDiv) { totalTestCasesDiv.innerHTML = ''; }
       if (testSuiteNameDiv) { testSuiteNameDiv.innerHTML = ''; }
       if (testCasesPassedDiv) { testCasesPassedDiv.innerHTML = ''; }
       if (testCasesFailedDiv) { testCasesFailedDiv.innerHTML = ''; }
    """, webView.getCefBrowser.getURL, 0
    )
    println("Cleared UI")

    if (isError) {
      webView.getCefBrowser.executeJavaScript(
        s"""
         if (lastTestResultsDiv) {
           const errorElement = document.createElement('p');
           errorElement.textContent = "No Test Runs Found";
           errorElement.classList.add("error");
           errorElement.id = "errorElement";
           lastTestResultsDiv.appendChild(errorElement);
         }
      """, webView.getCefBrowser.getURL, 0
      )
      println("No test reports found")
    } else {
      val groupedResults = mutable.Map[String, mutable.Map[String, mutable.ListBuffer[TestResult]]]()

      testResults.asInstanceOf[mutable.ListBuffer[Map[String, String]]].foreach { test =>
        val testResult = TestResult(
          date = test("date"),
          method = test("method"),
          name = test("name"),
          report = test("report"),
          status = test("status"),
          testCasePath = test("testCasePath")
        )

        groupedResults.getOrElseUpdate(testResult.date, mutable.Map.empty)
          .getOrElseUpdate(testResult.report, mutable.ListBuffer.empty) += testResult
      }

      val immutableGroupedResults = groupedResults.map { case (date, reportsMap) =>
        date -> reportsMap.map { case (report, tests) =>
          report -> tests.toList
        }.toMap
      }.toMap

      println(immutableGroupedResults)

      webView.getCefBrowser.executeJavaScript(
        s"""
         const dropdownContainer = document.createElement('div');
         dropdownContainer.className = 'dropdown-container';
      """, webView.getCefBrowser.getURL, 0
      )
      println("Created dropdown container")

      immutableGroupedResults.foreach { case (date, reportsMap) =>
        println("Creating dropdown content for date: " + date)
        webView.getCefBrowser.executeJavaScript(
          s"""
           const dropdownHeader = document.createElement('div');
           dropdownHeader.className = 'dropdown-header';
           const currentDate = new Date();
           const currentDateString = currentDate.toLocaleDateString();

           const yesterday = new Date(currentDate);
           yesterday.setDate(currentDate.getDate() - 1);
           const yesterdayDateString = yesterday.toLocaleDateString();

           if (currentDateString === "$date") {
             dropdownHeader.textContent = "Today";
           } else if (yesterdayDateString === "$date") {
             dropdownHeader.textContent = "Yesterday";
           } else {
             dropdownHeader.textContent = "$date";
           }

           const dropdownIcon = document.createElement('span');
           dropdownIcon.className = 'dropdown-icon';
           dropdownHeader.appendChild(dropdownIcon);
           dropdownHeader.onclick = () => {
             const content = document.getElementById('dropdown$date');
             if (content) {
               content.classList.toggle('show');
               dropdownIcon.classList.toggle('open');
             }
           };
           const dropdownContent = document.createElement('div');
           dropdownContent.id = 'dropdown$date';
           dropdownContent.className = 'dropdown-content';
        """, webView.getCefBrowser.getURL, 0
        )
        println("Created dropdown header")

        reportsMap.foreach { case (report, tests) =>
            println("Creating report dropdown content for report: " + report)
          webView.getCefBrowser.executeJavaScript(
            s"""
             const reportDropdownHeader = document.createElement('div');
             reportDropdownHeader.className = 'dropdown-header';
             reportDropdownHeader.textContent = "$report";
             const reportDropdownIcon = document.createElement('span');
             reportDropdownIcon.className = 'dropdown-icon';

             reportDropdownHeader.appendChild(reportDropdownIcon);
             reportDropdownHeader.onclick = () => {
               const content = document.getElementById('dropdown$date$report');
               if (content) {
                 content.classList.toggle('show');
                 reportDropdownIcon.classList.toggle('open');
               }
             };

             const reportDropdownContent = document.createElement('div');
             reportDropdownContent.id = 'dropdown$date$report';
             reportDropdownContent.className = 'report-dropdown-content';
          """, webView.getCefBrowser.getURL, 0
          )
          println("Created report dropdown header")

          tests.foreach { test =>
            println("Creating test content for test: " + test.name)
            webView.getCefBrowser.executeJavaScript(
              s"""
               const testMethod = document.createElement('div');
               testMethod.textContent = "${test.method}";
               if ("${test.status}" === 'PASSED') {
                 testMethod.classList.add("testSuccess");
               } else {
                 testMethod.classList.add("testError");
               }
               reportDropdownContent.appendChild(testMethod);

               const testName = document.createElement('div');
               testName.textContent = "${test.name}";
               testName.classList.add("testName");
               reportDropdownContent.appendChild(testName);
            """, webView.getCefBrowser.getURL, 0
            )
            println("Appended test content for test: " + test.name)
          }

          webView.getCefBrowser.executeJavaScript(
            s"""
             dropdownContent.appendChild(reportDropdownHeader);
             dropdownContent.appendChild(reportDropdownContent);
          """, webView.getCefBrowser.getURL, 0
          )
          println("Appended report dropdown content")
        }

        webView.getCefBrowser.executeJavaScript(
          s"""
           dropdownContainer.appendChild(dropdownHeader);
           dropdownContainer.appendChild(dropdownContent);
        """, webView.getCefBrowser.getURL, 0
        )
        println("Appended dropdown content")
      }

      webView.getCefBrowser.executeJavaScript(
        s"""
         if (lastTestResultsDiv) { lastTestResultsDiv.appendChild(dropdownContainer); }
      """, webView.getCefBrowser.getURL, 0
      )
      println("Appended dropdown container")
    }
  }
  private def displayRecordedTestCases(recordedTestCaseName: String, noTestCases: Boolean, path: String, testSetName: String): Unit = {
    println(s"Displaying recorded test cases for test set: $testSetName , path: $path , testCase: $recordedTestCaseName , noTestCases: $noTestCases")

    val jsTestSetName = testSetName.replace("\"", "\\\"") // Escape quotes for JS
    val jsRecordedTestCaseName = recordedTestCaseName.replace("\"", "\\\"")

    webView.getCefBrowser.executeJavaScript(
      s"""
         |const recordStatus = document.getElementById('recordStatus');
         |const recordedTestCasesDiv = document.getElementById('recordedTestCases');
         |recordStatus.style.display = "block";
         |recordedTestCasesDiv.style.display = "grid";
       """.stripMargin, webView.getCefBrowser.getURL, 0
    )

    if (noTestCases) {
      webView.getCefBrowser.executeJavaScript(
        s"""
           |recordStatus.textContent = "Failed To Record Test Cases";
           |recordStatus.classList.add("error");
           |const viewRecordLogsButton = document.getElementById('viewRecordLogsButton');
           |if (viewRecordLogsButton) {
           |  viewRecordLogsButton.style.display = "block";
           |}
         """.stripMargin, webView.getCefBrowser.getURL, 0
      )
    } else {
      webView.getCefBrowser.executeJavaScript(
        s"""
           |recordStatus.textContent = "Test Cases Recorded Successfully";
           |recordStatus.classList.add("success");

           |if (recordedTestCasesDiv) {
           |  let testSetDropdown = document.getElementById("$jsTestSetName");
           |  if (!testSetDropdown) {
           |    testSetDropdown = document.createElement('div');
           |    testSetDropdown.id = "$jsTestSetName";
           |    testSetDropdown.classList.add('dropdown-container');
           |    testSetDropdown.style.display = "block";
           |    const dropdownToggle = document.createElement('div');
           |    dropdownToggle.classList.add('dropdown-header');
           |    const toggleText = document.createElement('span');
           |    toggleText.textContent = "$jsTestSetName";
           |    const dropdownIcon = document.createElement('span');
           |    dropdownIcon.classList.add('dropdown-icon');
           |    dropdownToggle.appendChild(toggleText);
           |    dropdownToggle.appendChild(dropdownIcon);
           |    const testCaseContainer = document.createElement('div');
           |    testCaseContainer.classList.add('dropdown-content');
           |    testCaseContainer.style.display = 'none';
           |    dropdownToggle.addEventListener('click', () => {
           |    testCaseContainer.style.display = testCaseContainer.style.display === 'none' ? 'block' : 'none';
           |    dropdownIcon.classList.toggle('open');
           |    });
           |    testSetDropdown.appendChild(dropdownToggle);
           |    testSetDropdown.appendChild(testCaseContainer);
           |    recordedTestCasesDiv.appendChild(testSetDropdown);
           |  }
           |
           |  // Create the test case element
           |  const testCaseElement = document.createElement('button');
           |  testCaseElement.classList.add("recordedTestCase");
           |  testCaseElement.textContent = "$jsRecordedTestCaseName";
           |  testCaseElement.style.background = "#00163D";
           |  testSetDropdown.appendChild(testCaseElement);
           |
           |  testCaseElement.addEventListener('click', () => {
           |    window.openYamlFile("$path");
           |  });
           |
           |  const testCaseContainer = testSetDropdown.querySelector('.dropdown-content');
           |  testCaseContainer.appendChild(testCaseElement);
           |}
         """.stripMargin, webView.getCefBrowser.getURL, 0
      )
    }
  }



  private def displayTestResults(message: String, isError: Boolean, testResults: Any): Unit = {
      println("Displaying test results")
      println(testResults)
      if (isError) {
        webView.getCefBrowser.executeJavaScript(
          s"""
             |const testResultsDiv = document.getElementById('testResults');
             |testResultsDiv.style.display = "block";
             |testResultsDiv.textContent = "$message";
             |testResultsDiv.classList.add("error");
             |if(viewTestLogsButton) {
             |viewTestLogsButton.style.display = "block";
             |};
           """.stripMargin, webView.getCefBrowser.getURL, 0
        )
      } else {
        val testResultsList: Seq[String] = testResults match {
          case buffer: scala.collection.mutable.ArrayBuffer[String] => buffer.toSeq
          case seq: scala.collection.immutable.Seq[String] => seq
          case _ => throw new ClassCastException("Unsupported type")
        }
        val testResultsString = testResultsList.mkString("\n")
        val jsTestResultsString = testResultsString.replace("\"", "\\\"") // Escape quotes for JS
        webView.getCefBrowser.executeJavaScript(
          s"""
             |const testResultsDiv = document.getElementById('testResults');
             |testResultsDiv.style.display = "block";
             |testResultsDiv.textContent = "$testResultsString";
           """.stripMargin, webView.getCefBrowser.getURL, 0
        )
      }
    }
  private def openDocumentInEditor(filePath: String): Unit = {
    if(filePath.isEmpty) {
      println("File path is empty")
      return
    }
    var pathToOpen = filePath
    if(isWindows){
      pathToOpen = pathToOpen.replace("/mnt/c", "C:")
      pathToOpen = pathToOpen.replace("/", "\\")
    }
    println(s"Opening file: $pathToOpen")
    ApplicationManager.getApplication().invokeLater(new Runnable {
      override def run(): Unit = {
        val file: VirtualFile = LocalFileSystem.getInstance().findFileByPath(pathToOpen)
        if (file != null) {
          FileEditorManager.getInstance(project).openFile(file, true)
        } else {
          println(s"File not found: $pathToOpen")
        }
      }
    })
  }
}
