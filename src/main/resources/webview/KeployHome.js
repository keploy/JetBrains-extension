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
