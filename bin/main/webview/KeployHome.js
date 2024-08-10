document.addEventListener('DOMContentLoaded', () => {
    const statusdiv = document.getElementById('statusdiv');
    const keploycommands = document.getElementById('keploycommands');
    const displayPreviousTestResults = document.getElementById('displayPreviousTestResults');
    const openConfig = document.getElementById('openConfig');
    const startRecordingButton = document.getElementById('startRecordingButton');
    const stopRecordingButton = document.getElementById('stopRecordingButton');
    const startTestingButton = document.getElementById('startTestingButton');
    const stopTestingButton = document.getElementById('stopTestingButton');
    const viewCompleteSummaryButton = document.getElementById('viewCompleteSummaryButton');
    const viewTestLogsButton = document.getElementById('viewTestLogsButton');
    const viewRecordLogsButton = document.getElementById('viewRecordLogsButton');
    const recordedTestCases = document.getElementById('recordedTestCases');
    const recordStatus = document.getElementById('recordStatus');
    const testStatus = document.getElementById('testStatus');
    const testResults = document.getElementById('testResults');
    const loader = document.getElementById('loader');
    const completeSummaryHr = document.getElementById('completeSummaryHr');
    const recordingSteps = document.getElementById('recordingSteps');
    const replayingSteps = document.getElementById('replayingSteps');
    const testSuiteName = document.getElementById('testSuiteName');
        window.displayPreviousTestResults = function (message) {
            document.getElementById('keploycommands').style.display = 'none';

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

        if (message.error === true) {

            if (lastTestResultsDiv) {
                const errorElement = document.createElement('p');
                errorElement.textContent = "No Test Runs Found";
                errorElement.classList.add("error");
                errorElement.id = "errorElement";
                lastTestResultsDiv.appendChild(errorElement);
            }
        } else {
            // Group tests by date
            const testsByDate = {};
            message.data.testResults.forEach(test => {
                const date = test.date;
                if (!testsByDate[date]) {
                    testsByDate[date] = [];
                }
                testsByDate[date].push(test);
            });


            const testCasesTotalElement = document.createElement('p');
            testCasesTotalElement.textContent = `Total Test Cases : ${message.data.total}`;
            if (totalTestCasesDiv) { totalTestCasesDiv.appendChild(testCasesTotalElement); }

            const testCasesPassedElement = document.createElement('p');
            testCasesPassedElement.textContent = `Test Cases Passed : ${message.data.success}`;
            if (testCasesPassedDiv) { testCasesPassedDiv.appendChild(testCasesPassedElement); }

            const testCasesFailedElement = document.createElement('p');
            testCasesFailedElement.textContent = `Test Cases Failed : ${message.data.failure}`;
            if (testCasesFailedDiv) { testCasesFailedDiv.appendChild(testCasesFailedElement); }

            // Create and append dropdown structure based on testsByDate
            const dropdownContainer = document.createElement('div');
            dropdownContainer.className = 'dropdown-container';

            for (const date in testsByDate) {
                if (testsByDate.hasOwnProperty(date)) {
                    const tests = testsByDate[date];

                    const dropdownHeader = document.createElement('div');
                    dropdownHeader.className = 'dropdown-header';

                    // Get current date
                    const currentDate = new Date();
                    const currentDateString = formatDate(currentDate);

                    // Get yesterday's date
                    const yesterday = new Date(currentDate);
                    yesterday.setDate(currentDate.getDate() - 1);
                    const yesterdayDateString = formatDate(yesterday);

                    if (currentDateString === date) {
                        dropdownHeader.textContent = `Today`;
                    } else if (yesterdayDateString === date) {
                        dropdownHeader.textContent = `Yesterday`;
                    } else {
                        dropdownHeader.textContent = `${date}`;
                    }

                    // Add dropdown icon
                    const dropdownIcon = document.createElement('span');
                    dropdownIcon.className = 'dropdown-icon';

                    dropdownHeader.appendChild(dropdownIcon);
                    dropdownHeader.onclick = () => {
                        const content = document.getElementById(`dropdown${date}`);
                        if (content) {
                            content.classList.toggle('show');
                            dropdownIcon.classList.toggle('open'); // Update icon based on dropdown state
                        }
                    };

                    const dropdownContent = document.createElement('div');
                    dropdownContent.id = `dropdown${date}`;
                    dropdownContent.className = 'dropdown-content';
                    tests.forEach((test, index) => {
                        // Append individual test details
                        const testMethod = document.createElement('div');
                        testMethod.textContent = `${test.method}`;
                        if (test.status === 'PASSED') {
                            testMethod.classList.add("testSuccess");
                        } else {
                            testMethod.classList.add("testError");
                        }
                        dropdownContent.appendChild(testMethod);

                        const testName = document.createElement('div');
                        testName.textContent = `${test.name}`;
                        testName.classList.add("testName");
                        dropdownContent.appendChild(testName);

                        testName.addEventListener('click', async () => {
                            vscode.postMessage({
                                type: "openTestFile",
                                value: test.testCasePath
                            });
                        });
                        testMethod.addEventListener('click', async () => {
                            vscode.postMessage({
                                type: "openTestFile",
                                value: test.testCasePath
                            });
                        });
                    });

                    dropdownContainer.appendChild(dropdownHeader);
                    dropdownContainer.appendChild(dropdownContent);
                }
            }

            if (lastTestResultsDiv) { lastTestResultsDiv.appendChild(dropdownContainer); }
        }
    }
    function show(element) {
        element.style.display = 'block';
    }

    function hide(element) {
        element.style.display = 'none';
    }

    function toggleSelectedButton(selectedButton) {
        document.querySelectorAll('.icon-button').forEach(button => {
            button.classList.remove('selected');
        });
        selectedButton.classList.add('selected');
    }

    function resetStatus() {
        hide(recordStatus);
        hide(recordedTestCases);
        hide(testStatus);
        hide(testResults);
        hide(viewCompleteSummaryButton);
        hide(viewTestLogsButton);
        hide(viewRecordLogsButton);
        hide(completeSummaryHr);
        hide(loader);
        hide(testSuiteName);
    }

    keploycommands.addEventListener('click', () => {
        toggleSelectedButton(keploycommands);
        resetStatus();
        hide(testSuiteName);
        show(statusdiv);
    });

    displayPreviousTestResults.addEventListener('click', () => {
        toggleSelectedButton(displayPreviousTestResults);
        hide(statusdiv);
        // Add functionality to display previous test results
    });

    openConfig.addEventListener('click', () => {
        toggleSelectedButton(openConfig);
        hide(statusdiv);
        // Add functionality to open the configuration
    });

    startRecordingButton.addEventListener('click', () => {
        // Add functionality to start recording
        hide(startRecordingButton);
        show(stopRecordingButton);
        show(recordingSteps);
    });

    stopRecordingButton.addEventListener('click', () => {
        // Add functionality to stop recording
        hide(stopRecordingButton);
        show(startRecordingButton);
        hide(recordingSteps);
    });

    startTestingButton.addEventListener('click', () => {
        // Add functionality to start testing
        hide(startTestingButton);
        show(stopTestingButton);
        show(replayingSteps);
    });

    stopTestingButton.addEventListener('click', () => {
        // Add functionality to stop testing
        hide(stopTestingButton);
        show(startTestingButton);
        hide(replayingSteps);
    });

    viewCompleteSummaryButton.addEventListener('click', () => {
        // Add functionality to view complete summary
    });

    viewTestLogsButton.addEventListener('click', () => {
        // Add functionality to view test logs
    });

    viewRecordLogsButton.addEventListener('click', () => {
        // Add functionality to view record logs
    });
});
