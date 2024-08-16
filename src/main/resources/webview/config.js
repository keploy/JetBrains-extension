let showSettings = false;
let appCommand = '';
let path = './';
let passThroughPorts = '';

function handleSetupConfig() {
    showSettings = true;
    document.getElementById('container').classList.add('container-hide');
    document.getElementById('settingsContainer').classList.remove('container-hide');
}

document.getElementById('setupConfig').addEventListener('click', handleSetupConfig);

document.getElementById('initialiseConfigButton').addEventListener('click', function() {
    appCommand = document.getElementById('configCommand').value;
    path = document.getElementById('configPath').value;
    // You can add further actions here for saving the configuration
});
